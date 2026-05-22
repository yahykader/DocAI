---
name: docai-module0-auth
description: Implémente le Module 0 Phase 0.2 DocAI (Login, Logout, Refresh token, JWT blacklist Valkey, mot de passe oublié). Utiliser quand on demande d'implémenter le login, le logout, le refresh token, la liste noire JWT, ou le flow mot de passe oublié.
---

# DocAI — Module 0 Auth (Phase 0.2)

## Business Rules

| ID | Règle |
|----|-------|
| BR-AUTH-001 | Login retourne JWT (15 min) + refresh token (8h) |
| BR-AUTH-002 | Logout invalide le refresh token Keycloak ET blackliste le JWT dans Valkey |
| BR-AUTH-003 | JWT blacklisté rejeté même s'il n'est pas encore expiré |
| BR-AUTH-004 | Refresh token à usage unique — rotation à chaque renouvellement |
| BR-AUTH-005 | Compte bloqué 15 min après 5 tentatives de login échouées |
| BR-AUTH-006 | Message d'erreur identique pour email inconnu et mot de passe incorrect (anti-énumération) |
| BR-AUTH-007 | Lien de réinitialisation valable 1 heure, usage unique |
| BR-AUTH-009 | Email de réinitialisation envoyé même si email inexistant (sécurité) |

## Domain Model

```java
// JwtBlacklist — stocké dans Valkey
public record JwtBlacklist(String jti, Instant expiresAt) {}

// PasswordResetToken — stocké dans MongoDB
public record PasswordResetToken(
    UUID token,
    String email,
    String tenantId,
    Instant expiresAt,   // TTL 1 heure
    boolean used
) {}

// Domain Events
public record UserLoggedIn(String userId, String tenantId, String ipAddress, Instant occurredAt) {}
public record UserLoggedOut(String userId, String tenantId, Instant occurredAt) {}
public record PasswordResetRequested(String email, String tenantId, Instant occurredAt) {}
```

## LoginUseCase

```java
@Component
public class LoginUseCaseImpl implements LoginUseCase {

    private final IdentityProviderPort identityProvider;
    private final ValkeyRateLimitAdapter rateLimiter;

    @Override
    @Audited(action = "USER_LOGGED_IN", resourceType = "User")
    public LoginResult execute(LoginCommand command) {
        // Vérifier blocage compte (5 tentatives — TTL 15 min Valkey)
        String lockKey = "login:lock:" + command.email();
        int attempts = getFailedAttempts(command.email());
        if (attempts >= 5) {
            throw new AccountLockedException("Compte temporairement bloqué"); // HTTP 429
        }

        try {
            // Authentifier via Keycloak
            TokenPair tokens = identityProvider.authenticate(command.email(), command.password());

            // Réinitialiser le compteur d'échecs
            resetFailedAttempts(command.email());

            log.info("User logged in tenantId={} userId=[PII_MASKED] ip=[PII_MASKED]",
                extractTenantId(tokens.accessToken()));

            return new LoginResult(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());

        } catch (AuthenticationException e) {
            // Incrémenter le compteur d'échecs
            incrementFailedAttempts(command.email());

            // Message générique — ne pas révéler si l'email existe
            throw new InvalidCredentialsException("Email ou mot de passe incorrect"); // HTTP 401
        }
    }
}
```

## LogoutUseCase — JWT Blacklist

```java
@Component
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final IdentityProviderPort identityProvider;
    private final TokenBlacklistPort blacklist;

    @Override
    @Audited(action = "USER_LOGGED_OUT", resourceType = "User")
    public void execute(LogoutCommand command) {
        // 1. Invalider le refresh token dans Keycloak
        identityProvider.revokeRefreshToken(command.refreshToken());

        // 2. Blacklister le JWT dans Valkey (TTL = durée restante du JWT)
        String jti = extractJti(command.accessToken());
        Instant jwtExpiry = extractExpiry(command.accessToken());
        Duration ttl = Duration.between(Instant.now(), jwtExpiry);

        if (ttl.isPositive()) {
            blacklist.blacklist(jti, ttl);
        }

        log.info("User logged out tenantId={}", TenantContext.get());
    }
}
```

## TokenBlacklistFilter — Vérification à chaque requête

```java
@Component
@Order(2) // Après TenantJwtFilter
public class TokenBlacklistFilter extends OncePerRequestFilter {

    private final TokenBlacklistPort blacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(req);
        if (token != null) {
            String jti = extractJti(token);
            if (blacklist.isBlacklisted(jti)) {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.getWriter().write("{\"status\":401,\"title\":\"Token révoqué\",\"errorCode\":\"AUTH-001\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}

// ValkeyTokenBlacklistAdapter
@Component
public class ValkeyTokenBlacklistAdapter implements TokenBlacklistPort {
    private static final String PREFIX = "jwt:blacklist:";

    @Override
    public void blacklist(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + jti, "REVOKED", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
```

## RefreshTokenUseCase — Rotation obligatoire

```java
@Component
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    @Override
    public LoginResult execute(RefreshTokenCommand command) {
        try {
            // Keycloak invalide l'ancien refresh token et retourne un nouveau couple
            TokenPair tokens = identityProvider.refreshToken(command.refreshToken());
            return new LoginResult(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
        } catch (InvalidRefreshTokenException e) {
            throw new UnauthorizedException("Refresh token invalide ou expiré"); // HTTP 401
        }
    }
}
```

## ForgotPasswordUseCase — Anti-énumération

```java
@Component
public class ForgotPasswordUseCaseImpl implements ForgotPasswordUseCase {

    @Override
    public void execute(ForgotPasswordCommand command) {
        // Toujours HTTP 200 — même si email inexistant (BR-AUTH-009)
        Optional<String> userExists = identityProvider.findUserByEmail(command.email());

        if (userExists.isPresent()) {
            PasswordResetToken token = new PasswordResetToken(
                UUID.randomUUID(), command.email(), command.tenantId(),
                Instant.now().plus(1, ChronoUnit.HOURS), false
            );
            resetTokenRepository.save(token);

            emailPort.send(EmailMessage.of(command.email(), "password-reset", Map.of(
                "resetLink", buildResetLink(token.token())
            ), command.tenantId()));
        }

        // Même réponse si email inexistant — ne pas révéler l'existence
        log.info("Password reset requested email=[PII_MASKED] tenantId={}",
            command.tenantId());
    }
}
```

## Endpoints

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/v1/public/auth/login` | ❌ Public | Connexion |
| POST | `/v1/auth/logout` | ✅ JWT | Déconnexion + invalidation |
| POST | `/v1/auth/refresh` | ❌ Public | Renouveler le JWT |
| POST | `/v1/public/auth/forgot-password` | ❌ Public | Demander réinitialisation |
| POST | `/v1/public/auth/reset-password` | ❌ Public | Réinitialiser le MDP |

## Scénarios BDD clés

```gherkin
Scenario: Login réussi
  Given alice@acme.com est active avec bon MDP
  When alice se connecte
  Then JWT 15min + refresh token 8h retournés

Scenario: Blocage après 5 tentatives
  Given alice a échoué 4 fois
  When alice échoue une 5ème fois
  Then HTTP 429 "Compte temporairement bloqué" (15 min)

Scenario: Logout — JWT immédiatement invalide
  Given alice est connectée avec JWT valide
  When alice se déconnecte
  Then toute requête suivante avec ce JWT → HTTP 401

Scenario: Refresh — rotation obligatoire
  Given JWT expiré, refresh token valide
  When alice rafraîchit son token
  Then nouveau JWT + nouveau refresh token retournés
  And l'ancien refresh token est invalidé
```

## Checklist

- [ ] JWT blacklist dans Valkey (TTL = durée restante du JWT)
- [ ] `TokenBlacklistFilter` vérifie le JTI à chaque requête
- [ ] Rotation refresh token à chaque renouvellement
- [ ] Blocage compte après 5 tentatives (Valkey TTL 15 min)
- [ ] Message d'erreur générique login (anti-énumération)
- [ ] `ForgotPassword` retourne toujours HTTP 200
- [ ] Reset token usage unique + TTL 1h MongoDB
- [ ] `@Audited` sur login et logout (userId, IP, timestamp)
- [ ] Test : logout → JWT blacklisté → HTTP 401 immédiat
