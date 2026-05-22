---
name: docai-security-keycloak
description: "Implémente la sécurité du Module 0 DocAI (TenantJwtFilter, KeycloakJwtAuthConverter, MongoTenantFilter, SecurityConfig, GlobalExceptionHandler RFC 7807, Rate Limiting Bucket4j+Valkey, AuditMongoAdapter append-only, Impersonation Support Client consentement TENANT_ADMIN, configuration realm Keycloak). Utiliser quand on demande la sécurité, le filtre JWT, l'isolation tenant, les rôles RBAC, le rate limiting, l'audit trail, l'accès support, ou le gestionnaire d'erreurs global. A implémenter EN PREMIER avant tout endpoint métier."
---

# DocAI — Module 0 Sécurité & Multi-Tenancy

## ⚠️ IMPLÉMENTER EN PREMIER — Avant tout endpoint métier

Sans cette fondation, chaque module devra être repris.

---

## 1. Configuration Keycloak

> **Stack :** Spring Boot **4.0.x** · Keycloak **26** · Spring Security 6

**Realm DocAI :**
- Realm : `docai`
- Client `docai-backend` : confidential, `client_credentials` flow (inter-services)
- Client `docai-frontend` : public, PKCE activé
- Durée JWT : **15 minutes** · Refresh token : **8 heures**

**Rôles RBAC :**

| Rôle | Permissions |
|------|------------|
| `TENANT_ADMIN` | Gestion totale du tenant, API keys, webhooks, quotas |
| `ANALYST` | Upload, consultation, correction manuelle extractions |
| `VIEWER` | Lecture seule sur tous les documents du tenant |
| `FRAUD_REVIEWER` | Queue de révision fraude, décisions APPROVED/REJECTED |
| `SYSTEM` | Communication inter-services (client_credentials flow) |

**Claims JWT obligatoires :**
```json
{
  "sub": "usr-123",
  "email": "alice@acme.com",
  "tenant_id": "acme-corp",
  "roles": ["ANALYST"],
  "exp": 1748000000
}
```

**Utilisateurs de test à créer dans Keycloak :**
- `admin@acme-corp.test / Test1234!` → rôle TENANT_ADMIN, tenant acme-corp
- `alice@acme-corp.test / Test1234!` → rôle ANALYST, tenant acme-corp
- `viewer@acme-corp.test / Test1234!` → rôle VIEWER, tenant acme-corp
- `fraud@acme-corp.test / Test1234!` → rôle FRAUD_REVIEWER, tenant acme-corp
- `admin@beta-corp.test / Test1234!` → rôle TENANT_ADMIN, tenant beta-corp (test isolation)

---

## 2. Business Rules Sécurité

| ID | Règle |
|----|-------|
| BR-SEC-001 | Tout endpoint requiert un JWT valide avec claim `tenant_id` |
| BR-SEC-002 | Isolation totale des données par `tenant_id` (filtre MongoDB systématique) |
| BR-SEC-003 | API Keys hashées SHA-256 + sel — jamais stockées en clair |
| BR-SEC-004 | Tout accès est audité : userId, tenantId, action, IP, timestamp |
| BR-SEC-005 | Inputs validés et sanitisés via Jakarta Validation avant traitement |
| BR-SEC-006 | Security headers : CSP, HSTS, X-Frame-Options, X-Content-Type-Options |
| BR-SEC-007 | Aucun secret dans les logs — PII masqués (`[PII_MASKED]`) |
| BR-SEC-008 | `.env` dans `.gitignore` — vérification automatique en CI (git-secrets) |

---

## 3. TenantJwtFilter — Extraction tenant du JWT

```java
@Component
@Order(1)
public class TenantJwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                String tenantId = jwtAuth.getToken().getClaimAsString("tenant_id");
                if (tenantId == null || tenantId.isBlank()) {
                    throw new TenantNotSetException(request.getRequestURI());
                }
                TenantContext.set(tenantId);
                MDC.put("tenantId", tenantId);
                MDC.put("userId", jwtAuth.getToken().getSubject());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();   // OBLIGATOIRE — évite les fuites entre requêtes
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }
}
```

**Flux de sécurité complet :**
```
Client → [JWT Header] → TenantJwtFilter → Spring Security → TenantContext.set()
      → Controller → Use Case → DocumentMongoAdapter → MongoDB (filtre tenantId auto)
```

---

## 4. KeycloakJwtAuthConverter — Mapping rôles

```java
@Component
public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractRoles(jwt).stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<String> extractRoles(Jwt jwt) {
        // Keycloak stocke les rôles dans realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }
}
```

---

## 5. SecurityConfig — ADR-006 (Cache JWKS obligatoire)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            KeycloakJwtAuthConverter converter,
                                            TenantJwtFilter tenantFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/v1/public/**").permitAll()   // signup, login, forgot-password
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
            )
            .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
            .headers(h -> h
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000))
            )
            .build();
    }
}
```

```yaml
# application.yml — ADR-006 : Cache JWKS 1h (Keycloak peut être down)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/certs
          jwks-cache-ttl: 3600s       # Cache 1h — si Keycloak down, auth continue
          jwks-cache-refresh: 1800s   # Refresh 30 min avant expiration
```

> **Test ADR-006 obligatoire :** `docker compose stop keycloak` → les JWT existants doivent continuer à fonctionner pendant 1h.

---

## 6. MongoTenantFilter — Isolation tenant automatique

```java
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        MongoTemplate template = new MongoTemplate(factory);
        // Intercepteur : injecte { tenantId: TenantContext.get() } dans CHAQUE requête
        template.setEntityCallbacks(ReactiveBeforeConvertCallback.class);
        return template;
    }
}
```

**Règle absolue :** HTTP 404 (pas 403) si l'ID existe mais appartient à un autre tenant — ne jamais révéler l'existence d'une ressource.

---

## 7. AuditMongoAdapter — Audit trail immuable ← NOUVEAU

**PORT-OUT-SEC-001** — `AuditPort` — enregistrement immuable de chaque action sensible.

```java
// docai-domain/port/out/AuditPort.java
public interface AuditPort {
    void record(AuditEntry entry);
}

// docai-adapter-out-mongodb/AuditMongoAdapter.java
@Component
public class AuditMongoAdapter implements AuditPort {

    @Override
    public void record(AuditEntry entry) {
        // Append-only — jamais de update ni delete sur audit_entries
        mongoTemplate.insert(AuditEntryDocument.from(entry), "audit_entries");
    }
}
```

**Collection `audit_entries` :**

| Champ | Type | Description |
|-------|------|-------------|
| `_id` | UUID | Identifiant entrée |
| `tenantId` | String | Tenant concerné |
| `userId` | String | Utilisateur ayant agi |
| `action` | String | DOCUMENT_UPLOADED, LOGIN, LOGOUT, API_KEY_CREATED... |
| `resourceType` | String | DOCUMENT, EXTRACTION, API_KEY... |
| `resourceId` | String | ID de la ressource |
| `details` | Object | Données contextuelles (avant/après correction...) |
| `ipAddress` | String | IP de l'appelant |
| `userAgent` | String | Navigateur / client API |
| `occurredAt` | DateTime | Horodatage précis |

**Usage dans les Use Cases :**
```java
// Annoter les use cases sensibles avec @Audited (commons-audit)
@Audited(action = "DOCUMENT_UPLOADED")
public class SubmitDocumentUseCase implements SubmitDocumentPort { ... }

@Audited(action = "LOGIN")
public class LoginUseCase implements LoginPort { ... }
```

**TTL :** Index MongoDB TTL sur `occurredAt` → conservation **5 ans** (obligation légale RGPD).

---

## 8. Rate Limiting — Bucket4j + Valkey ← NOUVEAU

| Niveau | Limite | Fenêtre | Réponse |
|--------|--------|---------|---------|
| Par tenant (plan Starter) | 100 req | 1 min | HTTP 429 + `Retry-After` |
| Par tenant (plan Pro) | 1 000 req | 1 min | HTTP 429 + `Retry-After` |
| Par IP (anti-abus global) | 30 req | 1 min | HTTP 429 |
| Quota mensuel Starter | 500 docs | 30 jours | HTTP 429 + date reset |
| Quota mensuel Pro | 10 000 docs | 30 jours | HTTP 429 + date reset |

```java
// RateLimitingFilter.java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String tenantId = TenantContext.getTenantId();
        String ip = request.getRemoteAddr();

        // 1. Rate limit par IP (anti-abus global)
        if (!ipRateLimiter.tryConsume(ip)) {
            writeRateLimitError(response, "RATE-001", "IP rate limit exceeded");
            return;
        }

        // 2. Rate limit par tenant (plan-aware)
        if (tenantId != null && !tenantRateLimiter.tryConsume(tenantId)) {
            String retryAfter = tenantRateLimiter.getRetryAfterSeconds(tenantId).toString();
            response.setHeader("Retry-After", retryAfter);
            writeRateLimitError(response, "RATE-001", "Tenant rate limit exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

```yaml
# application.yml
docai:
  rate-limiting:
    by-ip:
      capacity: 30
      refill-tokens: 30
      refill-period: 1m
    by-tenant:
      starter:
        capacity: 100
        refill-tokens: 100
        refill-period: 1m
      pro:
        capacity: 1000
        refill-tokens: 1000
        refill-period: 1m
```

**Headers HTTP 429 obligatoires :**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 42
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1748736000
```

---

## 9. Impersonation Support Client ← NOUVEAU

> **Problème :** L'isolation multi-tenant est totale. Comment l'équipe support accède aux données d'un tenant pour résoudre un bug sans violer l'architecture ?

**Décision : Impersonation auditée avec consentement explicite**

3 conditions obligatoires :
1. **Consentement** du TENANT_ADMIN (email avec lien d'approbation)
2. **Durée limitée** : token TTL 2 heures, scope READ_ONLY
3. **Audit complet** : chaque action loggée avec `support=true`, visible par le tenant

**Flow complet :**
```
1. Client ouvre un ticket support
2. Agent support → POST /v1/support/impersonation-request
   → Email automatique au TENANT_ADMIN :
     "L'agent [nom] demande un accès 2h pour résoudre le ticket #12345. Acceptez-vous ?"
3. TENANT_ADMIN → POST /v1/support/impersonation-approve?token=xxx
4. Token UUID généré (TTL 2h, scope READ_ONLY, stocké Valkey)
5. Agent utilise le token → accès données en lecture seule uniquement
6. Chaque action → AuditEntry avec flag support=true
7. TENANT_ADMIN peut voir toutes les actions dans son dashboard
8. À expiration → accès automatiquement révoqué
```

**Business Rules :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-SUP-001 | Aucun accès support sans consentement explicite du TENANT_ADMIN | MUST |
| BR-SUP-002 | Accès support en lecture seule — jamais de modification possible | MUST |
| BR-SUP-003 | Token impersonation expire automatiquement après 2 heures | MUST |
| BR-SUP-004 | Chaque action de l'agent loggée avec `support=true` | MUST |
| BR-SUP-005 | Le TENANT_ADMIN peut voir l'historique complet des accès support | MUST |
| BR-SUP-006 | Le TENANT_ADMIN peut révoquer l'accès à tout moment | MUST |
| BR-SUP-007 | Pas d'impersonation sans ticket de support ouvert | MUST |

**Endpoints :**

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| POST | `/v1/support/impersonation-request` | SYSTEM | Demander l'autorisation |
| POST | `/v1/support/impersonation-approve?token=xxx` | TENANT_ADMIN | Approuver l'accès |
| GET | `/v1/support/audit-trail` | TENANT_ADMIN | Historique accès support |
| DELETE | `/v1/support/impersonation/{id}` | TENANT_ADMIN | Révoquer immédiatement |

**Logs obligatoires :**
```
INFO — Accès support demandé  : tenantId, agentId=[PII_MASKED], ticketId
INFO — Accès support approuvé : tenantId, agentId=[PII_MASKED], expiresAt
INFO — Action support         : tenantId, agentId=[PII_MASKED], action, resourceId, support=true
INFO — Accès support révoqué  : tenantId, agentId=[PII_MASKED], reason
```

> **Impact RGPD :** Chaque accès support est conservé 5 ans dans `audit_entries`. Mentionné dans le DPA.

---

## 10. GlobalExceptionHandler — Erreurs RFC 7807

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest req) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            ex.getHttpStatus(), ex.getMessage());
        problem.setType(URI.create("https://api.docai.fr/errors/" + ex.getErrorCode()));
        problem.setTitle(ex.getTitle());
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setInstance(URI.create(req.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return buildProblem(HttpStatus.FORBIDDEN, "AUTH-002", "Rôle insuffisant", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-001",
            "Erreur interne", "Une erreur inattendue s'est produite");
    }
}
```

**Catalogue des codes erreur :**

| Code | HTTP | Situation |
|------|------|-----------|
| `DOC-001` | 400 | Document invalide (type non supporté, taille dépassée) |
| `DOC-002` | 409 | Document déjà soumis (idempotency key utilisée) |
| `DOC-003` | 404 | Document non trouvé pour ce tenant |
| `EXT-001` | 422 | Extraction échouée (score confiance insuffisant) |
| `FRD-001` | 200 | Document rejeté (score fraude critique) |
| `QUOTA-001` | 429 | Quota mensuel dépassé |
| `RATE-001` | 429 | Rate limit dépassé |
| `AUTH-001` | 401 | JWT absent ou invalide |
| `AUTH-002` | 403 | Rôle insuffisant pour cette action |

---

---

## 11. Scénarios BDD Sécurité & Definition of Done
> Lire **references/bdd-dod.md** pour les scénarios BDD complets et la Definition of Done.
