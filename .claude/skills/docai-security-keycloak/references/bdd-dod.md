## 11. Scénarios BDD Sécurité

```gherkin
Feature: Sécurité et isolation multi-tenant

  Scenario: Accès sans JWT — HTTP 401
    When la requête est envoyée sans header Authorization
    Then la réponse est HTTP 401 avec code "AUTH-001"

  Scenario: Isolation tenant — accès croisé impossible
    Given alice (acme-corp) a soumis le document "doc-001"
    When bob (beta-corp) tente GET /v1/documents/doc-001
    Then la réponse est HTTP 404 (pas HTTP 403 — ne pas révéler l'existence)

  Scenario: Rôle insuffisant — HTTP 403
    Given carol a le rôle VIEWER
    When carol tente POST /v1/documents
    Then la réponse est HTTP 403 avec code "AUTH-002"

  Scenario: Rate limit Starter dépassé
    Given le tenant "acme-corp" (plan Starter) a envoyé 100 requêtes en 1 min
    When il envoie une 101ème requête
    Then la réponse est HTTP 429 avec header Retry-After

  Scenario: Token impersonation expire après 2 heures
    Given un token d'impersonation a été approuvé il y a 2h01
    When l'agent support l'utilise
    Then la réponse est HTTP 401 (token expiré)

  Scenario: Keycloak down — JWT valide accepté (ADR-006)
    Given Keycloak est arrêté
    When une requête avec un JWT valide existant est envoyée
    Then la réponse est HTTP 200 (cache JWKS actif pendant 1h)
```

---

## 12. Definition of Done — Module 0 (complet)

- [ ] `TenantJwtFilter` avec `TenantContext.clear()` dans `finally`
- [ ] `KeycloakJwtAuthConverter` mappe `realm_access.roles` → `ROLE_X`
- [ ] Cache JWKS 1h configuré (ADR-006) — test Keycloak down validé
- [ ] `GlobalExceptionHandler` retourne `ProblemDetail` RFC 7807
- [ ] Security headers configurés : HSTS, X-Frame-Options, CSP
- [ ] `@PreAuthorize("hasRole('ANALYST')")` sur chaque endpoint protégé
- [ ] **Rate limiting Bucket4j + Valkey** testé aux limites Starter et Pro
- [ ] **`AuditMongoAdapter`** : chaque action sensible génère une entrée immuable
- [ ] **Impersonation support** : consentement, lecture seule, audit trail visible, expiration 2h
- [ ] **Keycloak** : realm `docai` importé, 5 utilisateurs de test créés, 5 rôles présents
- [ ] Isolation tenant testée : acme-corp ne voit pas les données de beta-corp → HTTP 404
- [ ] ArchUnit : `docai-domain` ne contient aucune import Spring Security
- [ ] `GET /actuator/health` → HTTP 200
- [ ] `GET /v1/documents` sans JWT → HTTP 401
- [ ] Token impersonation expire automatiquement après 2 heures

---

## Commons à Utiliser

- `commons-multitenancy` → `TenantContext`, `TenantJwtFilter`, `MongoTenantFilter`, `ValkeyTokenBlacklistAdapter`
- `commons-api` → `GlobalExceptionHandler`, `ProblemDetail`, catalogue erreurs AUTH-001/AUTH-002
- `commons-audit` → `@Audited` sur `LoginUseCase`, `LogoutUseCase`, `InviteUserUseCase`, `RevokeUserUseCase`
