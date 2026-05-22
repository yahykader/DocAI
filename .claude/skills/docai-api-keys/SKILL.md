---
name: docai-api-keys
description: "Implémente le Module 6.1 DocAI (API publique versionnée /v1/, API Keys hashées SHA-256 + sel, scopes READ/WRITE/ADMIN, révocation immédiate avec invalidation cache Valkey, SpringDoc OpenAPI 3.1, contract testing obligatoire sur tous les endpoints publics, rate limiting par API Key, Time-to-first-call ( 1h). Utiliser quand on demande d'implémenter les API Keys, la création ou révocation d'une clé API, l'authentification par clé, la documentation OpenAPI, l'API publique B2B DocAI, ou le versioning d'API. Prérequis : Module 0 (Auth + Keycloak) terminé."
---

# Module 6.1 — API Publique & API Keys

> **Prérequis :** Module 0 (Auth + Multi-Tenancy) terminé.  
> **Durée estimée :** 2 semaines  
> **Objectif :** Client externe intègre l'API en < 1h avec la documentation seule.

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/integration/
├── ApiKey.java           // Aggregate (id, tenantId, name, hashedKey, salt, scope, createdAt, lastUsedAt, revokedAt)
├── ApiKeyScope.java      // Enum (READ, WRITE, ADMIN)
└── events/
    ├── ApiKeyCreated.java
    └── ApiKeyRevoked.java
```

**Règle critique :** La valeur de l'API Key en clair n'est **jamais** stockée ni loggée.  
Elle est générée une fois, hashée SHA-256 + sel, et retournée uniquement à la création.

### Ports
```
Inbound:
  PORT-IN-INT-001 → CreateApiKeyUseCase
  PORT-IN-INT-002 → RevokeApiKeyUseCase
  PORT-IN-INT-003 → ListApiKeysUseCase
  PORT-IN-INT-004 → AuthenticateWithApiKeyUseCase  (filtre d'authentification)

Outbound:
  PORT-OUT-INT-001 → ApiKeyRepositoryPort
  PORT-OUT-INT-002 → ApiKeyCachePort              (cache Valkey invalidation immédiate)
```

### Adapters
```
docai-adapter-in-rest/
├── ApiKeyController.java          // CRUD API Keys (TENANT_ADMIN uniquement)
└── ApiKeyAuthFilter.java          // Filtre Spring Security : header X-API-Key → résolution tenant

docai-adapter-out-mongodb/
└── MongoApiKeyAdapter.java        // Collection api_keys

docai-adapter-out-valkey/
└── ValkeyApiKeyCacheAdapter.java  // Cache hashedKey → tenantId (TTL 5min, invalidation immédiate sur révocation)
```

---

## Génération & Hash d'une API Key

```java
// CreateApiKeyUseCase.java
public ApiKeyCreationResult create(CreateApiKeyCommand cmd) {
    // 1. Générer la clé en clair (unique, non stockée)
    String plainTextKey = "sk-docai-" + UUID.randomUUID().toString().replace("-", "");

    // 2. Générer un sel aléatoire
    String salt = generateSecureRandomSalt();

    // 3. Hash SHA-256 + sel
    String hashedKey = hashSha256WithSalt(plainTextKey, salt);

    // 4. Persister uniquement le hash + sel
    ApiKey apiKey = ApiKey.create(cmd.tenantId(), cmd.name(), hashedKey, salt, cmd.scope());
    apiKeyRepository.save(apiKey);

    // 5. Mettre en cache pour auth rapide
    apiKeyCache.put(hashedKey, apiKey.tenantId(), Duration.ofMinutes(5));

    // 6. Retourner la clé en clair UNE SEULE FOIS
    return new ApiKeyCreationResult(apiKey.id(), plainTextKey);  // plainTextKey jamais reloggué
}
```

---

## Authentification par API Key

```java
// ApiKeyAuthFilter.java — exécuté avant les autres filtres Spring Security
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String rawKey = request.getHeader("X-API-Key");
        if (rawKey != null) {
            // 1. Vérifier Valkey cache (fast path)
            // 2. Si miss → chercher en MongoDB par hash
            // 3. Vérifier non révoquée
            // 4. Mettre à jour lastUsedAt (asynchrone, non bloquant)
            // 5. Injecter tenantId dans TenantContext
            // 6. Créer Authentication Spring Security avec scopes ApiKey
        }
        filterChain.doFilter(request, response);
    }
}
```

---

## Endpoints API Keys (TENANT_ADMIN)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/v1/api-keys` | Créer une API Key (retourne plainText une seule fois) |
| DELETE | `/v1/api-keys/{id}` | Révoquer (invalidation Valkey immédiate) |
| GET | `/v1/api-keys` | Lister les API Keys du tenant (préfixe uniquement, jamais la valeur) |

---

## Endpoints API Publique

| Méthode | Endpoint | Scope | Description |
|---------|----------|-------|-------------|
| POST | `/v1/documents` | WRITE | Soumettre un document |
| GET | `/v1/documents/{id}` | READ | Statut et résultats |
| GET | `/v1/documents` | READ | Liste paginée avec filtres |
| GET | `/v1/documents/{id}/extraction` | READ | Résultat extraction structuré |
| GET | `/v1/documents/{id}/fraud` | READ | Score et signaux fraude |
| POST | `/v1/documents/{id}/reprocess` | WRITE | Relancer le traitement |
| GET | `/v1/analytics` | READ | KPIs tenant |

> ⚠️ **Contract Testing obligatoire** — Chaque endpoint public doit avoir un contrat Spring Cloud Contract. Utiliser le skill `docai-contract-testing`.

---

## Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-INT-001 | API versionnée `/v1/`, `/v2/` | MUST |
| BR-INT-002 | API Keys générées par `TENANT_ADMIN` uniquement | MUST |
| BR-INT-003 | API Key hashée SHA-256 + sel — jamais exposée après création | MUST |
| BR-INT-004 | Chaque API Key a un scope (READ, WRITE, ADMIN) | MUST |
| BR-INT-005 | Révocation effective immédiatement (cache Valkey invalidé) | MUST |
| BR-INT-006 | Rate limiting par API Key (même règles que par tenant) | MUST |
| BR-INT-007 | Documentation OpenAPI 3.1 générée automatiquement (SpringDoc) | MUST |
| BR-OAS-002 | Tous les endpoints ont descriptions + exemples + codes erreur | MUST |
| BR-OAS-004 | Spec OpenAPI publiée sur GitHub Pages (job 05-documentation.yml) | MUST |

---

## Configuration SpringDoc

```yaml
# application.yml
springdoc:
  api-docs:
    path: /v1/api-docs
  swagger-ui:
    path: /swagger-ui.html
    try-it-out-enabled: true
  info:
    title: DocAI API
    version: 1.0.0
    description: API publique DocAI — Traitement documentaire B2B
```

```java
// Annotations obligatoires sur chaque Controller public
@Operation(summary = "Soumettre un document", description = "...")
@ApiResponse(responseCode = "202", description = "Document accepté pour traitement")
@ApiResponse(responseCode = "429", description = "Quota dépassé")
@SecurityRequirement(name = "ApiKey")
```

---

## Nommage Obligatoire (Annexe B)

| Classe | Module |
|--------|--------|
| `ApiKeyController` | Adapter IN REST — TENANT_ADMIN uniquement |
| `CreateApiKeyUseCase` | Application — hash SHA-256 + sel |
| `RevokeApiKeyUseCase` | Application — invalide cache Valkey immédiatement |
| `ApiKey` | Aggregate (id, tenantId, hashedKey, scope, createdAt, lastUsedAt) |
| `ApiKeyScope` | Enum (READ, WRITE, ADMIN) |
| `ApiKeyAuthFilter` | Filtre Spring Security — résolution tenant depuis X-API-Key |

---

## Commons à Utiliser

- `commons-api` → `ApiResponse<T>`, `ProblemDetail` RFC 7807, versioning `/v1/`
- `commons-quota` → `@QuotaProtected` sur `POST /v1/documents`
- `commons-multitenancy` → isolation tenant sur API Keys
- `commons-audit` → `@Audited` sur création et révocation
- `commons-testing` → `ApiKeyTestBuilder`

---

## Scénarios BDD

```gherkin
Feature: API Keys

  Scenario: Création API Key — hash obligatoire
    Given un TENANT_ADMIN de "acme-corp"
    When il crée une API Key avec scope READ
    Then la réponse contient la clé en clair (une seule fois)
    And la lecture directe en MongoDB ne révèle pas la valeur

  Scenario: Révocation — effective immédiatement
    Given une API Key active "sk-docai-xyz"
    When le TENANT_ADMIN la révoque
    Then la prochaine requête avec cette clé retourne HTTP 401
    And le cache Valkey est invalidé immédiatement

  Scenario: Scope insuffisant
    Given une API Key avec scope READ
    When elle tente POST /v1/documents
    Then la réponse est HTTP 403 (scope WRITE requis)
```

---

## Definition of Done

- [ ] API Keys générées + hashées SHA-256 + sel (jamais en clair en base)
- [ ] Révocation effective immédiatement (cache Valkey invalidé)
- [ ] Rate limiting par API Key testé (distinct du rate limiting par tenant)
- [ ] Documentation OpenAPI 3.1 générée et accessible (`/swagger-ui.html`)
- [ ] Spec OpenAPI publiée sur GitHub Pages (BR-OAS-004)
- [ ] Tous les endpoints : descriptions + exemples + codes erreur (BR-OAS-002)
- [ ] Contrats Spring Cloud Contract sur tous les endpoints publics
- [ ] Time-to-first-call < 1h validé (test développeur externe)
- [ ] Couverture domaine ≥ 90%

---

## Logs Obligatoires

```
INFO  — API Key créée : tenantId, keyId, scope, createdBy=[PII_MASKED]
INFO  — API Key révoquée : tenantId, keyId, revokedBy=[PII_MASKED]
WARN  — API Key expirée utilisée : tenantId, keyId (jamais la valeur de la clé)
INFO  — Appel API authentifié : tenantId, keyId, endpoint, durationMs
```
