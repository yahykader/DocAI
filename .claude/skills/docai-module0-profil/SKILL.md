---
name: docai-module0-profil
description: Implémente le Module 0 Phase 0.4 DocAI (profil utilisateur, changement mot de passe, changement email avec vérification, 2FA TOTP, historique de connexion). Utiliser quand on demande d'implémenter le profil, le changement de MDP connecté, la gestion du 2FA, ou l'historique de connexion.
---

# DocAI — Module 0 Profil Utilisateur & Sécurité (Phase 0.4)

## Business Rules

| ID | Règle |
|----|-------|
| BR-PRF-001 | Mot de passe actuel requis pour en changer |
| BR-PRF-003 | Après changement MDP, tous les refresh tokens existants invalidés |
| BR-PRF-011 | Rôle et tenant_id sont en lecture seule (non modifiables par l'utilisateur) |
| BR-PRF-020 | Mot de passe actuel requis pour changer l'email |
| BR-PRF-021 | Ancien email reste actif jusqu'à confirmation du nouveau |
| BR-PRF-030 | 20 dernières connexions (succès + échecs) conservées |
| BR-PRF-040 | 2FA optionnel Starter/Pro, **obligatoire** Enterprise |
| BR-PRF-043 | Codes de récupération générés lors de l'activation 2FA |

## Use Cases

```java
// 1. Changement mot de passe (utilisateur connecté)
@Component
public class ChangePasswordUseCaseImpl implements ChangePasswordUseCase {

    @Override
    @Audited(action = "PASSWORD_CHANGED", resourceType = "User")
    public void execute(ChangePasswordCommand command) {
        String userId = TenantContext.getCurrentUserId();
        String tenantId = TenantContext.get();

        // Vérifier le mot de passe actuel
        try {
            identityProvider.authenticate(command.email(), command.currentPassword());
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException("Mot de passe actuel incorrect"); // HTTP 401
        }

        // Nouveau MDP différent de l'ancien
        if (command.currentPassword().equals(command.newPassword())) {
            throw new SamePasswordException("Le nouveau MDP doit être différent de l'ancien");
        }

        // Mettre à jour dans Keycloak
        identityProvider.changePassword(userId, command.newPassword());

        // Invalider TOUS les refresh tokens existants (BR-PRF-003)
        identityProvider.revokeAllSessions(userId);

        // Email de confirmation
        emailPort.send(EmailMessage.of(command.email(), "password-changed",
            Map.of("ipAddress", "[PII_MASKED]", "timestamp", Instant.now().toString()),
            tenantId));

        log.info("Password changed userId=[PII_MASKED] tenantId={}", tenantId);
    }
}

// 2. Changement email — avec vérification obligatoire
@Component
public class ChangeEmailUseCaseImpl implements ChangeEmailUseCase {

    @Override
    @Audited(action = "EMAIL_CHANGE_REQUESTED", resourceType = "User")
    public void execute(ChangeEmailCommand command) {
        // Vérifier MDP actuel
        identityProvider.authenticate(command.currentEmail(), command.currentPassword());

        // Nouveau email non déjà utilisé
        if (identityProvider.emailExists(command.newEmail())) {
            throw new EmailAlreadyExistsException(command.newEmail());
        }

        // Token de confirmation (UUID, TTL 24h)
        EmailChangeToken token = new EmailChangeToken(
            UUID.randomUUID(), command.currentEmail(), command.newEmail(),
            TenantContext.get(), Instant.now().plus(24, ChronoUnit.HOURS)
        );
        emailChangeTokenRepository.save(token);

        // Email de vérification sur le NOUVEL email
        emailPort.send(EmailMessage.of(command.newEmail(), "email-verification", Map.of(
            "confirmationLink", buildEmailChangeLink(token.token())
        ), TenantContext.get()));

        // L'ANCIEN email reste actif jusqu'à confirmation (BR-PRF-021)
        log.info("Email change requested tenantId={}", TenantContext.get());
    }
}

// 3. Confirmation changement email
@Component
public class ConfirmEmailChangeUseCaseImpl implements ConfirmEmailChangeUseCase {

    @Override
    public void execute(ConfirmEmailChangeCommand command) {
        EmailChangeToken token = emailChangeTokenRepository
            .findByToken(command.token())
            .orElseThrow(() -> new InvalidTokenException());

        if (token.expiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException(); // HTTP 410
        }

        // Mettre à jour l'email dans Keycloak
        identityProvider.updateEmail(token.userId(), command.newEmail());

        // Email de notification sur l'ANCIEN email (sécurité)
        emailPort.send(EmailMessage.of(token.currentEmail(), "email-changed-notification",
            Map.of(), token.tenantId()));

        emailChangeTokenRepository.delete(token.token());
    }
}
```

## Historique de connexion

```java
// Collection MongoDB : login_history (TTL index 90 jours)
@Document(collection = "login_history")
public class LoginHistoryEntry {
    @Id private String id;
    @Field("user_id") private String userId;
    @Field("tenant_id") private String tenantId;  // Index via Mongock (pas @Indexed ici)
    @Field("ip_address") private String ipAddress;      // Stocké mais jamais loggué
    @Field("user_agent") private String userAgent;
    @Field("status") private String status;             // SUCCESS | FAILED
    @Field("occurred_at") private Instant occurredAt;   // TTL index 90 jours
}

// Enregistrement dans LoginUseCase
@Component
public class LoginHistoryRecorder {
    public void record(String userId, String tenantId, String ipAddress,
                       String userAgent, LoginStatus status) {
        LoginHistoryEntry entry = new LoginHistoryEntry(
            userId, tenantId, ipAddress, userAgent, status.name(), Instant.now()
        );
        loginHistoryRepository.save(entry);
    }
}

// Endpoint
// GET /v1/profile/login-history → 20 dernières connexions
```

## 2FA TOTP — Keycloak natif

```java
@RestController
@RequestMapping("/v1/profile/2fa")
public class TwoFactorController {

    // Activer le 2FA → retourne QR Code TOTP
    @PostMapping("/enable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TotpSetupResponse> enable2FA() {
        String userId = getCurrentUserId();
        TotpSetup setup = identityProvider.initiate2FA(userId);
        // setup.qrCodeUri() → à scanner avec Google Authenticator
        // setup.backupCodes() → afficher UNE SEULE FOIS (BR-PRF-043)
        return ResponseEntity.ok(TotpSetupResponse.from(setup));
    }

    // Confirmer avec premier code TOTP
    @PostMapping("/verify")
    public ResponseEntity<Void> verify2FA(@RequestBody VerifyTotpRequest request) {
        identityProvider.confirm2FA(getCurrentUserId(), request.totpCode());
        return ResponseEntity.ok().build();
    }

    // Désactiver (MDP requis)
    @PostMapping("/disable")
    public ResponseEntity<Void> disable2FA(@RequestBody DisableTotpRequest request) {
        identityProvider.authenticate(getCurrentUserEmail(), request.currentPassword());
        identityProvider.disable2FA(getCurrentUserId());
        return ResponseEntity.ok().build();
    }
}
```

## Endpoints complets

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/v1/profile` | ✅ JWT | Consulter son profil |
| PUT | `/v1/profile` | ✅ JWT | Modifier nom et prénom |
| PUT | `/v1/profile/password` | ✅ JWT | Changer son mot de passe |
| PUT | `/v1/profile/email` | ✅ JWT | Demander changement email |
| GET | `/v1/profile/login-history` | ✅ JWT | 20 dernières connexions |
| POST | `/v1/profile/2fa/enable` | ✅ JWT | Activer le 2FA |
| POST | `/v1/profile/2fa/disable` | ✅ JWT | Désactiver le 2FA (MDP requis) |
| GET | `/v1/profile/2fa/backup-codes` | ✅ JWT | Codes de récupération |

## Scénarios BDD clés

```gherkin
Scenario: Changement MDP — tous les sessions invalidées
  Given alice est connectée sur 2 appareils
  When alice change son MDP via PUT /v1/profile/password
  Then tous les refresh tokens sont invalidés
  And alice doit se reconnecter sur tous ses appareils

Scenario: Changement email — ancien actif jusqu'à confirmation
  Given alice veut changer vers "alice-new@acme.com"
  When alice soumet le changement
  Then email de vérification envoyé sur alice-new@acme.com
  And alice@acme.com reste fonctionnel
  When alice confirme via le lien
  Then alice-new@acme.com est activé
  And notification envoyée sur alice@acme.com

Scenario: 2FA obligatoire Enterprise
  Given le plan du tenant est Enterprise
  When alice tente de se connecter sans 2FA activé
  Then Keycloak redirige vers l'activation 2FA obligatoire
```

## Checklist

- [ ] Changement MDP : MDP actuel vérifié + tous sessions invalidées (BR-PRF-003)
- [ ] Changement email : ancien actif jusqu'à confirmation (BR-PRF-021)
- [ ] TTL index MongoDB 90 jours sur `login_history.occurredAt`
- [ ] `login_history` : IP et User-Agent enregistrés mais jamais loggués
- [ ] 2FA TOTP via Keycloak natif + backup codes générés une seule fois
- [ ] 2FA obligatoire pour plan Enterprise configuré dans Keycloak
- [ ] `@Audited` sur changement MDP, changement email, activation/désactivation 2FA
- [ ] Profil : rôle et tenantId en lecture seule (non modifiables)
