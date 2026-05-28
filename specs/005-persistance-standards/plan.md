# Implementation Plan: Module C — Persistance & Standards

**Branch**: `005-persistance-standards` | **Date**: 2026-05-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-persistance-standards/spec.md`

## Summary

Module C établit les fondations transversales de la plateforme DocAI en 4 étapes (1.5 jours total) : logs JSON structurés avec MDC `traceId`/`tenantId` et masquage PII récursif, première migration Mongock créant les collections MongoDB initiales selon ADR-010, pagination centralisée dans un nouveau module `docai-commons` (BR-PAG-008), et versioning `/v1/` appliqué structurellement via `WebMvcConfigurer`. Ces 4 étapes sont pré-requis pour tous les modules consommateurs.

## Technical Context

**Language/Version**: Java 21
**Framework**: Spring Boot 4.0.x
**Build**: Maven 3.9+ (parent POM 11 modules — `docai-commons` à créer en 12e module)
**Storage**: MongoDB 7 (Replica Set, `auto-index-creation: false` déjà configuré)
**Migrations**: Mongock 5.x
**Observability**: SLF4J + Logback + `logstash-logback-encoder` 8.x, Micrometer → Prometheus
**Testing**: JUnit 5, Mockito, TestContainers (`withReuse(true)` — ADR-008)
**Target Platform**: Linux server (Docker Compose local, Kubernetes prod)
**Project Type**: Backend hexagonal (11+1 modules Maven)
**Performance Goals**: P95 dashboard < 100ms, P95 processing < 30s (Constitution Annex D)
**Constraints**: `server.servlet.context-path: /api` déjà en place — versioning `/v1/` via `configurePathMatch` (URLs: `/api/v1/{resource}`)
**Scale/Scope**: Multi-tenant, ≥ 1M documents/tenant (index partiel si < 20% actifs — ADR-010)

## Constitution Check

### Gates pré-design

| Gate | Statut | Evidence |
|------|--------|----------|
| Hexagonal Architecture | ✅ PASS | Logback → `docai-bootstrap`, Mongock → `docai-adapter-out-mongodb`, pagination → `docai-commons`, versioning → `docai-adapter-in-rest`. Zéro modification à `docai-domain`. |
| Domain Purity | ✅ PASS | Aucune modification à `docai-domain` requise |
| ADR-010 (tenantId-first + EXPLAIN PLAN) | ✅ PASS | V001 place `tenantId` en premier dans tous les index composites ; EXPLAIN PLAN documenté dans research.md et validé par `V001SetupDocumentsCollectionIT` |
| ADR-011 (lastSyncedAt) | ✅ PASS | Champ `lastSyncedAt` créé dans `document_summary_views` en V001 ; valeur renseignée en Partie 5 |
| Annex B (NEVER `@Indexed` en `@Document`) | ✅ PASS | Tous les index créés via Mongock uniquement |
| `auto-index-creation: false` | ✅ PASS | Déjà présent dans `application.yml` (ligne 10) |
| Test-First Development | ⚠️ REQUIRED | Tests unitaires + intégration requis pour chaque étape (cf. section par étape) |
| BR-PAG-008 (zéro duplication pagination) | ✅ PASS | Pagination dans `docai-commons` uniquement ; tout doublon = blocage PR |
| FR-PAG-005 (versioning global structurel) | ✅ PASS | `WebMvcConfigurer.configurePathMatch()` — conformité structurelle |
| ADR-008 (CI mémoire) | ✅ PASS | `TestContainers.withReuse(true)` dans tous les IT |

### Violations et justifications

Aucune violation de la Constitution détectée.

## Project Structure

### Documentation (this feature)

```text
specs/005-persistance-standards/
├── plan.md              ← Ce fichier
├── research.md          ← Phase 0 (résolutions techniques — 6 décisions)
├── data-model.md        ← Phase 1 (collections MongoDB + types Java)
├── contracts/
│   └── pagination-api.md ← Phase 1 (contrat REST pagination + versioning)
└── tasks.md             ← Phase 2 (généré par /speckit-tasks)
```

### Source Code

```text
backend/
├── pom.xml                                                  ← ajouter module docai-commons
│
├── docai-commons/                                           ← NOUVEAU MODULE
│   ├── pom.xml
│   └── src/
│       ├── main/java/fr/docai/commons/
│       │   └── pagination/
│       │       ├── ApiResponse.java
│       │       ├── PageInfo.java
│       │       └── PaginationParams.java
│       └── test/java/fr/docai/commons/
│           └── pagination/
│               ├── ApiResponseTest.java
│               └── PaginationParamsTest.java
│
├── docai-bootstrap/
│   └── src/main/resources/
│       ├── application.yml                                  ← modifier section logging
│       └── logback-spring.xml                               ← NOUVEAU
│
├── docai-adapter-out-mongodb/
│   └── src/main/java/fr/docai/adapter/out/mongodb/
│       └── migration/
│           └── V001SetupDocumentsCollection.java            ← NOUVEAU
│
└── docai-adapter-in-rest/
    └── src/main/java/fr/docai/adapter/in/rest/
        └── config/
            └── ApiVersioningConfig.java                     ← NOUVEAU
```

---

## Phase 1 — ÉTAPE 1 : Logs JSON structurés (0.25j)

**Module**: `docai-bootstrap`
**Objectif**: Remplacer le pattern console textuel par des logs JSON structurés en staging/prod, avec injection MDC automatique et masquage PII récursif.

### Fichiers à créer

**`docai-bootstrap/src/main/resources/logback-spring.xml`**
```xml
<configuration>
  <!-- Profil local : texte lisible -->
  <springProfile name="local">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}][%X{tenantId}] %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG"><appender-ref ref="CONSOLE"/></root>
  </springProfile>

  <!-- Profils staging + prod : JSON structuré -->
  <springProfile name="staging,prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>tenantId</includeMdcKeyName>
        <customFields>{"service":"docai"}</customFields>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
    <!-- DEBUG désactivé en staging/prod (FR-OBS-004) -->
    <logger name="fr.docai" level="INFO"/>
  </springProfile>
</configuration>
```

### Fichiers à modifier

**`application.yml`** — supprimer la section `logging.pattern.console` (remplacée par `logback-spring.xml`) et passer `fr.docai: INFO` hors profil local.

### Composants additionnels

**`TenantMdcFilter`** (dans `docai-adapter-in-rest` ou `docai-bootstrap`) : `OncePerRequestFilter` qui injecte `tenantId` depuis le JWT claim `tenant_id` dans le MDC à chaque requête. `traceId` est géré automatiquement par OpenTelemetry MDC bridge.
- Fallback obligatoire (SEC-002) : `tenantId = "UNAUTHENTICATED"` sur les requêtes pré-auth (OPTIONS, health, tokens invalides) — jamais `null`.

**`PiiMaskingConverter`** : Logback `PatternConverter` custom qui applique les 4 regex de masquage (email, SIRET, IBAN, téléphone) récursivement sur les valeurs de message. Enregistré dans `logback-spring.xml`.

**PII Logging Convention (SEC-001 — bloquant)** : La regex Logback ne couvre pas les champs structurés ni les stack traces. Règles obligatoires pour tous les modules consommateurs :
- `@ToString.Exclude` sur tous les champs PII des entités (email, SIRET, IBAN, numéro de téléphone)
- Interdire `StructuredArguments.kv("email", value)` — utiliser à la place `PiiLogger.safeKv("email", value)` (factory method qui masque automatiquement)
- Ces règles doivent être documentées dans la Constitution (section VI) avant que les modules métier soient implémentés

### Tests

| Test | Classe | Assertion |
|------|--------|-----------|
| Log JSON valide en profil staging | `LogbackJsonConfigTest` | Output est du JSON valide avec champs `traceId`, `tenantId`, `level`, `timestamp` |
| Masquage email | `PiiMaskingConverterTest` | `user@example.com` → `[PII_MASKED]` |
| Masquage SIRET | `PiiMaskingConverterTest` | `12345678901234` → `[PARTIAL_MASK]` |
| Masquage objet imbriqué | `PiiMaskingConverterTest` | `{ "address": { "email": "..." } }` → masqué |
| DEBUG absent en staging | `LogbackJsonConfigTest` | Aucun log DEBUG émis sous profil `staging` |

---

## Phase 2 — ÉTAPE 2 : Mongock V001 (0.5j)

**Module**: `docai-adapter-out-mongodb`
**Objectif**: Créer les collections MongoDB initiales avec indexation ADR-010.

### Fichier à créer

**`migration/V001SetupDocumentsCollection.java`**
```java
@ChangeUnit(id = "V001_setup_documents_collection", order = "001", author = "docai-team")
public class V001SetupDocumentsCollection {

    @Execution
    public void execute(MongoDatabase db) {
        // Collection 1 : documents
        db.createCollection("documents");
        db.getCollection("documents").createIndex(
            new Document("tenantId", 1)
                .append("status", 1)
                .append("createdAt", -1),
            new IndexOptions().name("idx_tenantId_status_createdAt"));

        // Collection 2 : document_summary_views (ADR-011)
        // lastSyncedAt : structure créée ici, valeur renseignée en Partie 5
        db.createCollection("document_summary_views");
        db.getCollection("document_summary_views").createIndex(
            new Document("tenantId", 1).append("documentId", 1),
            new IndexOptions().name("idx_tenantId_documentId").unique(true));
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        // SEC-004 : ne drop que si vide — rollback sûr uniquement en environnement frais
        if (db.getCollection("documents").estimatedDocumentCount() == 0) {
            db.getCollection("documents").drop();
        }
        if (db.getCollection("document_summary_views").estimatedDocumentCount() == 0) {
            db.getCollection("document_summary_views").drop();
        }
    }
}
```

### Points de conformité ADR-010

- `tenantId` est le **premier** champ de chaque index composite
- `auto-index-creation: false` déjà en place (application.yml ligne 10)
- `EXPLAIN PLAN` à joindre en PR : `winningPlan.stage = IXSCAN` obligatoire

### Tests

| Test | Classe | Type | Assertion |
|------|--------|------|-----------|
| Migration crée les collections | `V001SetupDocumentsCollectionIT` | IT (TestContainers) | Collections `documents` et `document_summary_views` existent après exécution |
| Index respecte tenantId-first | `V001SetupDocumentsCollectionIT` | IT | Index `{tenantId, status, createdAt}` présent |
| EXPLAIN PLAN = IXSCAN | `V001SetupDocumentsCollectionIT` | IT | `find({tenantId, status})` → `winningPlan.stage = "IXSCAN"` |
| Rollback idempotent | `V001SetupDocumentsCollectionIT` | IT | `rollback()` supprime les collections sans erreur |
| Mongock démarre sans erreur | `MongockStartupIT` | IT | `ApplicationContext` charge sans exception |

---

## Phase 3 — ÉTAPE 3 : Commons pagination (0.5j)

**Module**: `docai-commons` (NOUVEAU)
**Objectif**: Créer le module partagé avec la logique de pagination centralisée (BR-PAG-008).

### Actions préalables

1. Créer `backend/docai-commons/pom.xml` (parent → `docai-parent`, dépendance `spring-boot-starter-web`)
2. Ajouter `<module>docai-commons</module>` dans `backend/pom.xml` (avant `docai-adapter-in-rest`)
3. Ajouter la dépendance `docai-commons` dans `docai-adapter-in-rest/pom.xml`

### Fichiers à créer

**`ApiResponse.java`**
```java
public record ApiResponse<T>(List<T> data, PageInfo page) {}
```

**`PageInfo.java`**
```java
public record PageInfo(int number, int size, long totalElements, int totalPages) {
    public static PageInfo from(Page<?> springPage) {
        return new PageInfo(
            springPage.getNumber(), springPage.getSize(),
            springPage.getTotalElements(), springPage.getTotalPages());
    }
}
```

**`PaginationParams.java`**
```java
public record PaginationParams(
    @Min(0) int page,
    @Max(100) @Min(1) int size,
    String sort
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT = "createdAt,desc";
}
```

**Enforcement structurel (SEC-003)** : Créer un `PaginationParamsHandlerMethodArgumentResolver` dans `docai-commons` qui applique la validation automatiquement — les controllers consommateurs n'ont pas besoin d'`@Valid` explicite, éliminant le risque d'oubli silencieux.

### Tests

| Test | Assertion |
|------|-----------|
| `PaginationParamsTest#size101ShouldFail` | `@Validated` sur `size=101` → `ConstraintViolationException` |
| `PaginationParamsTest#size100ShouldPass` | `size=100` valide |
| `ApiResponseTest#serializationMatchesContract` | JSON sérialisé contient `data` + `page.number/size/totalElements/totalPages` |
| `PageInfoTest#fromSpringPage` | `PageInfo.from(mockPage)` mappe correctement |

---

## Phase 4 — ÉTAPE 4 : Versioning API /v1/ (0.25j)

**Module**: `docai-adapter-in-rest`
**Objectif**: Appliquer le préfixe `/v1/` à tous les controllers via configuration globale structurelle (FR-PAG-005).

### Fichier à créer

**`config/ApiVersioningConfig.java`**
```java
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/v1",
            HandlerTypePredicate.forBasePackage("fr.docai.adapter.in.rest"));
    }
}
```

Résultat : URLs complètes `http://host:8080/api/v1/{resource}` (`/api` context-path + `/v1` prefix MVC).

### Tests

| Test | Assertion |
|------|-----------|
| `ApiVersioningConfigTest#v1PrefixApplied` | `GET /api/v1/documents` → HTTP 200 |
| `ApiVersioningConfigTest#withoutPrefixReturns404` | `GET /api/documents` → HTTP 404 |
| `ApiVersioningConfigTest#actuatorUnaffected` | Actuator sur port 9091 sans préfixe `/v1` |

---

## Résumé des livrables

| Étape | Durée | Modules | Fichiers créés |
|-------|-------|---------|----------------|
| 1 — Logs JSON | 0.25j | `docai-bootstrap` | `logback-spring.xml`, `TenantMdcFilter.java`, `PiiMaskingConverter.java` |
| 2 — Mongock V001 | 0.5j | `docai-adapter-out-mongodb` | `V001SetupDocumentsCollection.java` + IT |
| 3 — Commons pagination | 0.5j | `docai-commons` (NOUVEAU) | `ApiResponse.java`, `PageInfo.java`, `PaginationParams.java` + pom.xml |
| 4 — Versioning /v1/ | 0.25j | `docai-adapter-in-rest` | `ApiVersioningConfig.java` |
| **Total** | **1.5j** | **4+1 modules** | **~10 fichiers** |

## Complexity Tracking

Aucune violation de Constitution. Aucune justification requise.

---

*Prêt pour : **`/speckit-tasks`***
