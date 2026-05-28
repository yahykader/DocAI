# Tasks: Module C — Persistance & Standards

**Input**: `specs/005-persistance-standards/plan.md`  
**Branch**: `005-persistance-standards` | **Date**: 2026-05-28  
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Checklist**: [checklists/persistance-observability.md](checklists/persistance-observability.md)

**Prérequis**: Module 1.A (Setup Projet) terminé — Mongock, docai-adapter-out-mongodb, docai-bootstrap/resources en place  
**Étapes**: 4 (1.5j total) | **Modules**: `docai-bootstrap`, `docai-adapter-out-mongodb`, `docai-commons` (NOUVEAU), `docai-adapter-in-rest`  
**Test-First**: Obligatoire — écrire les tests AVANT l'implémentation (Constitution Check ⚠️ REQUIRED)  
**ADR**: ADR-008 (TestContainers.withReuse(true)), ADR-010 (tenantId-first), ADR-011 (lastSyncedAt)

---

## Organisation

Les 4 étapes sont des pré-requis transversaux pour tous les modules consommateurs.  
**C-01** et **C-02** peuvent démarrer en parallèle.  
**C-03** nécessite la création du pom.xml avant les tests.  
**C-04** nécessite que `docai-commons` compile (dépendance Maven de C-03).

---

## ÉTAPE 1 — Logs JSON structurés + MDC (C-01 · 0.25j)

**Module**: `docai-bootstrap` + `docai-adapter-in-rest`  
**PR**: `feat(observability): add JSON logs MDC tenantId traceId PII masking`  
**Critère**: Log contient `traceId` + `tenantId` en JSON · `user@example.com` → `[PII_MASKED]`

### Tests C-01 — Écrire en PREMIER ⚠️

> Vérifier que ces tests **ÉCHOUENT** (NoClassDefFoundError ou AssertionError) avant d'écrire l'implémentation

- [X] T001 [P] [C-01] Écrire `LogbackJsonConfigTest` — profil `staging` : JSON valide, champs `traceId`/`tenantId`/`level`/`timestamp` présents, aucun log DEBUG émis (FR-OBS-004)  
  → `docai-bootstrap/src/test/java/fr/docai/bootstrap/logging/LogbackJsonConfigTest.java`

- [X] T002 [P] [C-01] Écrire `PiiMaskingConverterTest` — `user@example.com` → `[PII_MASKED]`, SIRET `12345678901234` → `[PARTIAL_MASK]`, masquage récursif sur `{ "address": { "email": "..." } }`  
  → `docai-bootstrap/src/test/java/fr/docai/bootstrap/logging/PiiMaskingConverterTest.java`

### Implémentation C-01

- [X] T003 [C-01] Ajouter dépendance `logstash-logback-encoder` dans `docai-bootstrap/pom.xml` (version gérée par BOM Spring Boot 4)

- [X] T004 [C-01] Créer `docai-bootstrap/src/main/resources/logback-spring.xml`  
  — Profil `local` : ConsoleAppender pattern texte `%d{HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}][%X{tenantId}] %logger{36} - %msg%n`, niveau DEBUG  
  — Profils `staging,prod` : LogstashEncoder avec `includeMdcKeyName` traceId + tenantId + `customFields {"service":"docai"}`, niveau INFO

- [X] T005 [C-01] Créer `PiiMaskingConverter.java` — Logback `PatternConverter` avec 4 regex (email → `[PII_MASKED]`, SIRET → `[PARTIAL_MASK]`, IBAN → `[PII_MASKED]`, téléphone → `[PII_MASKED]`); application récursive sur valeurs imbriquées; enregistrer dans `logback-spring.xml`  
  → `docai-bootstrap/src/main/java/fr/docai/bootstrap/logging/PiiMaskingConverter.java`

- [X] T006 [C-01] Créer `TenantMdcFilter.java` — `OncePerRequestFilter`, extraire `tenant_id` du JWT claim, injecter dans MDC; fallback SEC-002 : valeur `"UNAUTHENTICATED"` si requête pré-auth (OPTIONS, health, token invalide), **jamais `null`**; `traceId` géré automatiquement par OpenTelemetry MDC bridge  
  → `docai-adapter-in-rest/src/main/java/fr/docai/adapter/in/rest/filter/TenantMdcFilter.java`

- [X] T007 [C-01] Modifier `docai-bootstrap/src/main/resources/application.yml` — supprimer section `logging.pattern.console`, passer `fr.docai: INFO` hors profil local (remplacé par `logback-spring.xml`)

**Checkpoint C-01** ✅  
`LogbackJsonConfigTest` passe · `PiiMaskingConverterTest` passe  
Log de démarrage Spring Boot contient `"traceId"` et `"tenantId"` en JSON sous profil `staging`

---

## ÉTAPE 2 — Mongock V001 + Index ADR-010 (C-02 · 0.5j)

**Module**: `docai-adapter-out-mongodb`  
**PR**: `feat(mongodb): add Mongock V001 documents collection ADR-010 indexes`  
**Critère**: Mongock s'exécute sans erreur au démarrage · EXPLAIN PLAN = IXSCAN

### Tests C-02 — Écrire en PREMIER ⚠️

> Vérifier que ces tests **ÉCHOUENT** avant d'écrire l'implémentation

- [X] T008 [P] [C-02] Écrire `V001SetupDocumentsCollectionIT` avec `TestContainers.withReuse(true)` (ADR-008) :  
  — Collections `documents` et `document_summary_views` existent après exécution  
  — Index `{tenantId:1, status:1, createdAt:-1}` présent (ADR-010 tenantId-first)  
  — `find({tenantId, status})` → `winningPlan.stage = "IXSCAN"` (EXPLAIN PLAN)  
  — `rollback()` supprime les collections sans erreur (SEC-004 guard)  
  → `docai-adapter-out-mongodb/src/test/java/fr/docai/adapter/out/mongodb/migration/V001SetupDocumentsCollectionIT.java`

- [X] T009 [P] [C-02] Écrire `MongockStartupIT` — `ApplicationContext` charge sans exception, Mongock loggue succès du changeset  
  → `docai-adapter-out-mongodb/src/test/java/fr/docai/adapter/out/mongodb/migration/MongockStartupIT.java`

### Implémentation C-02

- [X] T010 [C-02] Créer `V001SetupDocumentsCollection.java`  
  ```
  @ChangeUnit(id = "V001_setup_documents_collection", order = "001", author = "docai-team")
  ```  
  `@Execution` : créer `documents` + index `{tenantId:1, status:1, createdAt:-1}` → `idx_tenantId_status_createdAt`; créer `document_summary_views` + index unique `{tenantId:1, documentId:1}` → `idx_tenantId_documentId`  
  `@RollbackExecution` : guard SEC-004 — `estimatedDocumentCount() == 0` avant tout `drop()` (rollback sûr uniquement en environnement frais)  
  → `docai-adapter-out-mongodb/src/main/java/fr/docai/adapter/out/mongodb/migration/V001SetupDocumentsCollection.java`

- [X] T011 [C-02] Vérifier (sans modifier) que `auto-index-creation: false` est présent ligne 10 de `docai-bootstrap/src/main/resources/application.yml` (BR-MIG-003)

- [X] T012 [C-02] Capturer et joindre l'EXPLAIN PLAN dans la PR :  
  `db.documents.find({"tenantId": "test", "status": "PENDING"}).explain("executionStats")`  
  → vérifier `winningPlan.stage = "IXSCAN"` (obligatoire, bloquant PR — ADR-010)

**Checkpoint C-02** ✅  
`V001SetupDocumentsCollectionIT` passe · `MongockStartupIT` passe  
Application démarre sans exception · Mongock log : `Successfully applied changeset 'V001_setup_documents_collection'`

---

## ÉTAPE 3 — Commons pagination BR-PAG (C-03 · 0.5j)

**Module**: `docai-commons` (NOUVEAU — 12e module Maven)  
**PR**: `feat(commons): add pagination ApiResponse BR-PAG-001-008 max-100`  
**Critère**: `GET /api/v1/documents?size=101` → HTTP 400 · réponse contient `totalElements` + `totalPages`

### Pré-requis Maven — À faire EN PREMIER

- [X] T013 [C-03] Créer `backend/docai-commons/pom.xml` — parent `docai-parent`, dépendance `spring-boot-starter-web`, groupId `fr.docai`, artifactId `docai-commons`

- [X] T014 [C-03] Ajouter `<module>docai-commons</module>` dans `backend/pom.xml` **avant** `docai-adapter-in-rest` (ordre de compilation requis par BR-PAG-008)

- [X] T015 [C-03] Ajouter dépendance `docai-commons` dans `docai-adapter-in-rest/pom.xml`

### Tests C-03 — Écrire en PREMIER ⚠️

> Dépendent de T013-T015 (module doit exister). Vérifier que les tests **ÉCHOUENT** avant implémentation.

- [X] T016 [P] [C-03] Écrire `PaginationParamsTest` :  
  `#size101ShouldFail` → `@Validated` sur `size=101` lève `ConstraintViolationException`  
  `#size100ShouldPass` → `size=100` valide  
  → `docai-commons/src/test/java/fr/docai/commons/pagination/PaginationParamsTest.java`

- [X] T017 [P] [C-03] Écrire `ApiResponseTest#serializationMatchesContract` — JSON sérialisé contient `data` (tableau non-null) + `page.number/size/totalElements/totalPages` (sérialisation Jackson avec Java 21 records)  
  → `docai-commons/src/test/java/fr/docai/commons/pagination/ApiResponseTest.java`

- [X] T018 [P] [C-03] Écrire `PageInfoTest#fromSpringPage` — `PageInfo.from(mockPage)` mappe `number/size/totalElements/totalPages` correctement depuis un `Page<?>` Spring  
  → `docai-commons/src/test/java/fr/docai/commons/pagination/PageInfoTest.java`

### Implémentation C-03

- [X] T019 [P] [C-03] Créer `ApiResponse.java` : `public record ApiResponse<T>(List<T> data, PageInfo page) {}`  
  `data` jamais null (tableau vide si aucun résultat — contrat pagination-api.md)  
  → `docai-commons/src/main/java/fr/docai/commons/pagination/ApiResponse.java`

- [X] T020 [P] [C-03] Créer `PageInfo.java` avec `from(Page<?> springPage)` factory method  
  Formule : `totalPages = ceil(totalElements / size)` (déléguée à `springPage.getTotalPages()`)  
  → `docai-commons/src/main/java/fr/docai/commons/pagination/PageInfo.java`

- [X] T021 [P] [C-03] Créer `PaginationParams.java` avec `@Min(0) int page`, `@Max(100) @Min(1) int size`, `String sort`; constantes `DEFAULT_SIZE = 20`, `MAX_SIZE = 100`, `DEFAULT_SORT = "createdAt,desc"` (BR-PAG-003, BR-PAG-006)  
  → `docai-commons/src/main/java/fr/docai/commons/pagination/PaginationParams.java`

- [X] T022 [C-03] Créer `PaginationParamsHandlerMethodArgumentResolver` (SEC-003) — enforcement structurel de `@Valid`, enregistrer dans Spring MVC via `WebMvcConfigurer.addArgumentResolvers()` dans `docai-commons`; les controllers consommateurs n'ont pas besoin d'`@Valid` explicite  
  → `docai-commons/src/main/java/fr/docai/commons/pagination/PaginationParamsHandlerMethodArgumentResolver.java`

**Checkpoint C-03** ✅  
`PaginationParamsTest`, `ApiResponseTest`, `PageInfoTest` passent  
`mvn clean package -pl docai-commons` réussit · `GET /api/v1/documents?size=101` → HTTP 400 avec message `"Page size must not exceed 100 elements"`

---

## ÉTAPE 4 — Versioning API /v1/ (C-04 · 0.25j)

**Module**: `docai-adapter-in-rest`  
**PR**: `feat(api): add /v1 versioning prefix OpenAPI SpringDoc`  
**Critère**: `GET /api/v1/documents` → HTTP 200 · `GET /api/documents` → HTTP 404

### Tests C-04 — Écrire en PREMIER ⚠️

> Vérifier que le test **ÉCHOUE** avant d'écrire l'implémentation

- [ ] T023 [C-04] Écrire `ApiVersioningConfigTest` :  
  `#v1PrefixApplied` → `GET /api/v1/documents` HTTP 200  
  `#withoutPrefixReturns404` → `GET /api/documents` HTTP 404  
  `#actuatorUnaffected` → Actuator sur port 9091 répond sans préfixe `/v1`  
  → `docai-adapter-in-rest/src/test/java/fr/docai/adapter/in/rest/config/ApiVersioningConfigTest.java`

### Implémentation C-04

- [ ] T024 [C-04] Créer `ApiVersioningConfig.java` — `@Configuration implements WebMvcConfigurer`, override `configurePathMatch()` avec `HandlerTypePredicate.forBasePackage("fr.docai.adapter.in.rest")` → préfixe `/v1`  
  Résultat URL : `http://host:8080/api/v1/{resource}` (context-path `/api` + MVC prefix `/v1`)  
  Exclusions : Actuator (port 9091), Swagger (`/swagger-ui`, `/v3/api-docs`), filtres Security  
  → `docai-adapter-in-rest/src/main/java/fr/docai/adapter/in/rest/config/ApiVersioningConfig.java`

**Checkpoint C-04** ✅  
`ApiVersioningConfigTest` passe (3 cas : 200 / 404 / actuator)  
`curl http://localhost:8080/api/v1/documents` → HTTP 200  
`curl http://localhost:8080/api/documents` → HTTP 404

---

## Phase finale — Validation intégration

- [ ] T025 [P] Exécuter `mvn clean test -P unit-tests` depuis `backend/` — 0 failure, 0 error
- [ ] T026 [P] Exécuter `mvn clean verify -P integration-tests` depuis `backend/` — 0 failure (TestContainers requis)
- [ ] T027 Exécuter `mvn checkstyle:check` depuis `backend/` — 0 violation
- [ ] T028 Vérifier que `docai-commons` est listé avant `docai-adapter-in-rest` dans `backend/pom.xml` (ordre de compilation)

---

## Dépendances & Ordre d'exécution

### Dépendances entre étapes

| Étape | Dépend de | Parallélisable avec |
|-------|-----------|---------------------|
| C-01 (Logs JSON) | Module 1.A terminé | C-02 |
| C-02 (Mongock V001) | Module 1.A terminé | C-01, C-03 (partiellement) |
| C-03 (Commons pagination) | T013-T015 (pom.xml créés) | C-01, C-02 |
| C-04 (Versioning /v1/) | T013-T015 + `docai-commons` compilé | — |

### Dépendances internes (chaque étape)

```
[P] Écrire tests → vérifier qu'ils ÉCHOUENT
[P] Écrire implémentation → vérifier que les tests PASSENT
→ Commit + push PR
→ Joindre EXPLAIN PLAN (C-02 uniquement)
```

### Opportunités parallèles

- **C-01 + C-02** : modules entièrement indépendants, peuvent démarrer simultanément
- **Au sein de C-03** : T019 (ApiResponse), T020 (PageInfo), T021 (PaginationParams) peuvent s'écrire en parallèle [P]
- **C-03 + C-04** : C-04 peut démarrer dès que `mvn package -pl docai-commons` réussit

---

## Récapitulatif des livrables

| ID | Étape | Fichier(s) créé(s) | Test(s) associés |
|----|-------|---------------------|------------------|
| C-01 | Logs JSON + MDC | `logback-spring.xml`, `PiiMaskingConverter.java`, `TenantMdcFilter.java`, `application.yml` (modifié) | `LogbackJsonConfigTest`, `PiiMaskingConverterTest` |
| C-02 | Mongock V001 | `V001SetupDocumentsCollection.java` | `V001SetupDocumentsCollectionIT`, `MongockStartupIT` |
| C-03 | Commons pagination | `docai-commons/pom.xml`, `ApiResponse.java`, `PageInfo.java`, `PaginationParams.java`, `PaginationParamsHandlerMethodArgumentResolver.java` | `ApiResponseTest`, `PaginationParamsTest`, `PageInfoTest` |
| C-04 | Versioning /v1/ | `ApiVersioningConfig.java` | `ApiVersioningConfigTest` |

**Total** : 4 PRs · ~12 fichiers créés · 8 classes de tests

---

## Règles de validation PR (blocage si non respectées)

1. **Tests verts** : `mvn clean test -P unit-tests` → 0 failure, 0 error
2. **IT verts** (C-02 uniquement) : `mvn clean verify -P integration-tests` → 0 failure
3. **EXPLAIN PLAN joint** (C-02 uniquement) : screenshot `winningPlan.stage = "IXSCAN"` obligatoire
4. **Checkstyle propre** : 0 violation
5. **TestContainers.withReuse(true)** sur tous les IT — ADR-008
6. **Zéro `@Indexed`** dans les classes `@Document` — BR-MIG-003 / Annex B
7. **Zéro réimplémentation pagination** dans d'autres modules — BR-PAG-008 (bloquant)
