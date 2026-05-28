---
document_type: security-review
review_type: plan
assessment_date: 2026-05-28
codebase_analyzed: DocAI / specs/005-persistance-standards
total_files_analyzed: 5
total_findings: 7
overall_risk: HIGH
critical_count: 0
high_count: 1
medium_count: 3
low_count: 2
informational_count: 1
owasp_categories: [A02, A04, A05, A09]
cwe_ids: [CWE-117, CWE-200, CWE-311, CWE-20, CWE-400]
field_summaries:
  document_type: "Always 'security-review'. Allows indexers to skip non-review documents."
  review_type: "Which command generated this document: audit, branch, staged, plan, tasks, or followup."
  assessment_date: "ISO 8601 date the review was performed (YYYY-MM-DD)."
  overall_risk: "Highest severity tier with active findings (CRITICAL, HIGH, MODERATE, LOW, INFORMATIONAL)."
  critical_count: "Number of Critical findings (CVSS 9.0-10.0)."
  high_count: "Number of High findings (CVSS 7.0-8.9)."
  medium_count: "Number of Medium findings (CVSS 4.0-6.9)."
  low_count: "Number of Low findings (CVSS 0.1-3.9)."
  informational_count: "Number of Informational findings."
  owasp_categories: "OWASP Top 10 2025 categories (A01-A10) that have at least one finding."
  cwe_ids: "CWE identifiers referenced in this document."
  finding_id: "Unique finding identifier (SEC-NNN) for cross-referencing and task linkage."
  location: "File path and line number of the vulnerable code (path/to/file.ext:line)."
  owasp_category: "OWASP Top 10 2025 category for this finding (AXX:2025-Name)."
  cwe: "Common Weakness Enumeration identifier with short name (CWE-NNN: Name)."
  cvss_score: "CVSS v3.1 base score (0.0-10.0). 9.0+=Critical, 7.0-8.9=High, 4.0-6.9=Medium, 0.1-3.9=Low."
  spec_kit_task: "Spec-Kit task ID for backlog tracking and remediation follow-up (TASK-SEC-NNN)."
---

# Security Review — Plan: Module C — Persistance & Standards

**Branch**: `005-persistance-standards` | **Date**: 2026-05-28 | **Reviewer**: speckit-security-review-plan

---

## Executive Summary

Le plan de Module C est globalement bien conçu du point de vue sécurité : `auto-index-creation: false`, MDC via OpenTelemetry, pagination bornée, versioning structurel, Actuator sur port séparé. Ces patterns constituent une base solide.

Cependant, **un finding HIGH est identifié** : la stratégie de masquage PII via regex Logback ne couvre pas les champs structurés (`StructuredArguments.kv()`) ni les stack traces contenant des objets PII. Sans guardrails sur les objets de domaine, la fuite de données personnelles dans les logs structurés est probable dès l'implémentation des modules métier. Ce risque doit être adressé dans la phase de design avant implémentation.

3 findings MEDIUM concernent : l'absence de valeur par défaut pour `tenantId` sur les requêtes non-authentifiées, l'enforcement non-garanti de `@Valid` sur les controllers consommateurs de `PaginationParams`, et le risque de perte de données sur rollback de migration en base non-vide.

**Verdict**: Plan implementable avec les 4 corrections identifiées. Le finding HIGH (SEC-001) doit être résolu avant la tâche ÉTAPE 1.

---

## Artefacts examinés

| Fichier | Rôle |
|---------|------|
| `specs/005-persistance-standards/plan.md` | Plan d'implémentation principal |
| `specs/005-persistance-standards/spec.md` | Spécification fonctionnelle |
| `specs/005-persistance-standards/research.md` | Décisions techniques (6) |
| `specs/005-persistance-standards/data-model.md` | Modèle de données MongoDB + Java |
| `specs/005-persistance-standards/contracts/pagination-api.md` | Contrat REST pagination |
| `.specify/memory/constitution.md` | Constitution DocAI v1.1.0 |

---

## Findings

### SEC-001 — PII Masking : les champs structurés Logback ne sont pas couverts par le regex converter

**Sévérité**: HIGH | **CVSS**: 7.2 | **OWASP**: A02:2025-Cryptographic Failures | **CWE**: CWE-311 (Missing Encryption of Sensitive Data)
**Spec-Kit Task**: TASK-SEC-001

**Description**:
Le plan décrit un `PiiMaskingConverter` Logback basé sur des regex (`email → [PII_MASKED]`). Ce converter s'applique au champ `message` du log. Il ne couvre **pas** :

1. **Les `StructuredArguments`** de `logstash-logback-encoder` :
   ```java
   log.info("Processing document", StructuredArguments.kv("email", user.getEmail()));
   ```
   Le champ `email` est écrit directement dans le JSON en dehors du champ `message` → le converter regex ne s'applique pas.

2. **Les stack traces** : une `NullPointerException` sur un objet contenant un email produira la valeur en clair dans `stack_trace`.

3. **Les `toString()` des agrégats** : `log.info("User entity: {}", userEntity)` si `UserEntity.toString()` inclut le champ email.

**Impact**: Les modules métier (Parties 3–4) qui logueront des entités avec PII contourneront le masquage. Violation directe de FR-OBS-003 et FR-OBS-007.

**Correction requise** (avant ÉTAPE 1):
- Ajouter une **convention de code** documentée : `@ToString.Exclude` (Lombok) obligatoire sur tous les champs PII des entités de domaine.
- Interdire `StructuredArguments.kv()` pour les champs PII — utiliser à la place `StructuredArguments.kv("email", "[PII_MASKED]")` ou une factory method `PiiLogger.safeKv()`.
- Ajouter ces règles dans la Constitution (section VI) ou dans un ADR dédié.
- Test supplémentaire : `PiiMaskingConverterTest#structuredArgumentWithEmailShouldBeMasked`.

---

### SEC-002 — TenantMdcFilter : aucun fallback pour les requêtes non-authentifiées

**Sévérité**: MEDIUM | **CVSS**: 5.3 | **OWASP**: A09:2025-Security Logging and Monitoring Failures | **CWE**: CWE-117 (Improper Output Neutralization for Logs)
**Spec-Kit Task**: TASK-SEC-002

**Description**:
Le plan décrit `TenantMdcFilter` qui injecte `tenantId` depuis le JWT claim `tenant_id`. Les filtres Servlet s'exécutent avant l'authentification Spring Security. Sur les requêtes pré-auth (OPTIONS CORS, `/actuator/health` sur port 8080 si exposé, tokens invalides), le claim est absent → `MDC.put("tenantId", null)` → le champ JSON sera `"tenantId": null`.

Conséquence :
- Les logs des requêtes non-autorisées ne sont pas distinguables par tenantId → impossibilité d'alerter sur des scans ou tentatives de force-brute par tenant.
- Le spec définit (Acceptance Scenario 1) : "chaque ligne de log produite contient les champs `traceId`, `tenantId`" → `null` viole ce critère.
- L'Edge Case de la spec couvre `traceId = "STARTUP"` mais pas `tenantId` sur requêtes non-auth.

**Correction requise** (dans ÉTAPE 1):
```java
String tenantId = extractTenantIdFromJwt(request);
MDC.put("tenantId", tenantId != null ? tenantId : "UNAUTHENTICATED");
```
Ajouter test : `TenantMdcFilterTest#unauthenticatedRequestShouldHaveFallbackTenantId`.

---

### SEC-003 — PaginationParams : l'enforcement de `@Valid` n'est pas garanti structurellement

**Sévérité**: MEDIUM | **CVSS**: 5.0 | **OWASP**: A04:2025-Insecure Design | **CWE**: CWE-20 (Improper Input Validation)
**Spec-Kit Task**: TASK-SEC-003

**Description**:
Le plan définit `PaginationParams` avec annotations Bean Validation (`@Max(100) @Min(1) int size`) et mentionne "`@Valid @ModelAttribute`" comme usage attendu. Cependant :

1. L'enforcement de `@Valid` repose sur la **discipline du développeur** dans chaque controller consommateur. Si un controller oublie `@Valid`, la validation `size > 100` est silencieusement ignorée — un consommateur peut alors demander `size=10000`, provoquant un scan complet de la collection (DoS potentiel, violation BR-PAG-005).

2. Le plan ne définit pas de `ControllerAdvice` global ni de `HandlerMethodArgumentResolver` personnalisé qui validerait automatiquement `PaginationParams` sans `@Valid` explicite.

**Correction requise** (dans ÉTAPE 3):
- Créer un `PaginationParamsHandlerMethodArgumentResolver` dans `docai-commons` qui applique la validation automatiquement — les controllers n'ont plus besoin de `@Valid`.
- OU documenter explicitement BR-PAG-008 dans les guidelines de PR review : "tout endpoint liste DOIT avoir `@Valid` sur `PaginationParams`".
- Test supplémentaire : `PaginationParamsIntegrationTest#missingValidAnnotationShouldStillValidate`.

---

### SEC-004 — Mongock V001 : le rollback `drop()` est destructif sur base non-vide

**Sévérité**: MEDIUM | **CVSS**: 5.5 | **OWASP**: A05:2025-Security Misconfiguration | **CWE**: CWE-200 (Exposure of Sensitive Information — data loss)
**Spec-Kit Task**: TASK-SEC-004

**Description**:
Le `@RollbackExecution` de V001 exécute :
```java
db.getCollection("documents").drop();
db.getCollection("document_summary_views").drop();
```

Si une défaillance partielle se produit lors d'une migration ultérieure (V002+) et que Mongock rejoue V001 en rollback sur une base de production avec des documents existants, le `drop()` supprime toutes les données irréversiblement.

Mongock empêche normalement la ré-exécution via le `mongockChangeLog`, mais :
- Une corruption du changelog ou une intervention manuelle peut contourner ce garde-fou
- Le plan ne documente pas ce risque et ne prévoit pas de vérification préalable

**Correction requise** (dans ÉTAPE 2):
```java
@RollbackExecution
public void rollback(MongoDatabase db) {
    // Sécurité : ne drop que si la collection est vide (rollback sûr uniquement en environnement frais)
    if (db.getCollection("documents").estimatedDocumentCount() == 0) {
        db.getCollection("documents").drop();
    }
    if (db.getCollection("document_summary_views").estimatedDocumentCount() == 0) {
        db.getCollection("document_summary_views").drop();
    }
}
```
Documenter dans les notes de migration : "rollback destructif — applicable uniquement en environnement sans données".

---

### SEC-005 — EXPLAIN PLAN : index manquant sur `lastSyncedAt` prépare un COLLSCAN futur

**Sévérité**: LOW | **CVSS**: 3.1 | **OWASP**: A05:2025-Security Misconfiguration | **CWE**: CWE-400 (Uncontrolled Resource Consumption)
**Spec-Kit Task**: TASK-SEC-005

**Description**:
V001 crée `document_summary_views` avec un index `{tenantId, documentId}`. Le `ReconciliationScheduler` de Partie 5 (ADR-011) devra requêter les documents où `lastSyncedAt < now - 30s`. Sans index sur `{tenantId, lastSyncedAt}`, cette requête produira un `COLLSCAN` à l'échelle → violation ADR-010.

Ce problème n'affecte pas Partie 1 mais crée une dette ADR-010 prévisible. La spécification V001 devrait inclure cet index dès le départ pour éviter une migration corrective ultérieure.

**Correction recommandée** (optionnelle en ÉTAPE 2):
```java
db.getCollection("document_summary_views").createIndex(
    new Document("tenantId", 1).append("lastSyncedAt", 1),
    new IndexOptions().name("idx_tenantId_lastSyncedAt"));
```

---

### SEC-006 — Trailing slash normalization non adressée pour Spring Boot 4

**Sévérité**: LOW | **CVSS**: 2.6 | **OWASP**: A05:2025-Security Misconfiguration | **CWE**: CWE-20 (Improper Input Validation)
**Spec-Kit Task**: TASK-SEC-006

**Description**:
Spring Boot 4 (Spring MVC 6.1+) a modifié le comportement par défaut : les trailing slashes ne sont **plus** matchées automatiquement. Une requête `GET /api/v1/documents/` retournera HTTP 404 alors que `GET /api/v1/documents` retourne 200.

Ce changement affecte les clients API qui ajoutent un slash de fin (courant dans certains frameworks clients). Le plan ne mentionne pas ce changement ni la stratégie choisie (accepter ou rejeter les trailing slashes).

**Correction recommandée** (dans ÉTAPE 4):
Documenter la décision dans `ApiVersioningConfig` :
```java
// Spring Boot 4 : trailing slashes non matchées par défaut (changement vs Spring Boot 3)
// Décision : rejeter les trailing slashes → retourne 404 (comportement strict)
// Alternative : activer useTrailingSlashMatch() si compatibilité nécessaire
```

---

### SEC-007 — Champ `path` dans les réponses d'erreur HTTP 400 expose le routing interne

**Sévérité**: INFORMATIONAL | **CVSS**: 0.0 | **OWASP**: A02:2025-Cryptographic Failures | **CWE**: CWE-200 (Exposure of Sensitive Information)
**Spec-Kit Task**: TASK-SEC-007

**Description**:
Le contrat `pagination-api.md` définit une réponse d'erreur HTTP 400 avec un champ `path: "/api/v1/documents"`. Ce champ expose le routing interne de l'application dans les réponses d'erreur.

Impact minimal (chemin prévisible et public), mais contrevient au principe de minimal information disclosure.

**Correction recommandée**: Omettre `path` des réponses d'erreur en production, ou utiliser le `problem+json` standard (RFC 7807) qui omet `path` par défaut.

---

## Patterns sécurisés confirmés

| Pattern | Détail | Référence |
|---------|--------|-----------|
| `auto-index-creation: false` | Déjà configuré, prévient la création d'index non contrôlés | `application.yml:10` |
| traceId via OpenTelemetry | Pas de génération UUID manuelle — corrélation via OTel bridge | `research.md Decision 2` |
| Pagination bornée `@Max(100)` | Prévient les requêtes DoS par taille de page excessive (si @Valid appliqué) | `data-model.md` |
| `tenantId` FIRST dans tous les index | Garantit que chaque requête filtre par tenant avant tout autre critère | `plan.md ÉTAPE 2` |
| Actuator sur port 9091 séparé | Non affecté par `/v1/` prefix, non exposé publiquement | `application.yml:64` |
| `WebMvcConfigurer.configurePathMatch` | Enforcement structurel du versioning — pas disciplinaire | `plan.md ÉTAPE 4` |
| `@RollbackExecution` sur V001 | Conformité BR-MIG-004 — rollback documenté pour chaque migration | `plan.md ÉTAPE 2` |
| DEBUG désactivé en staging/prod | Prévient la verbosité PII en production | `logback-spring.xml` profil |
| JWT JWKS cache 1h | ADR-006 déjà en place — Keycloak outage n'impacte pas les sessions actives | `application.yml:50` |

---

## Tableau de synthèse

| ID | Sévérité | CVSS | Étape impactée | Correction avant impl. ? |
|----|----------|------|----------------|--------------------------|
| SEC-001 | HIGH | 7.2 | ÉTAPE 1 | **OUI — bloquant** |
| SEC-002 | MEDIUM | 5.3 | ÉTAPE 1 | Recommandé |
| SEC-003 | MEDIUM | 5.0 | ÉTAPE 3 | Recommandé |
| SEC-004 | MEDIUM | 5.5 | ÉTAPE 2 | Recommandé |
| SEC-005 | LOW | 3.1 | ÉTAPE 2 | Optionnel |
| SEC-006 | LOW | 2.6 | ÉTAPE 4 | Optionnel |
| SEC-007 | INFO | 0.0 | Contrat | Non |

---

## Recommandations avant `/speckit-tasks`

1. **SEC-001 (HIGH)** — Ajouter une section "PII Logging Convention" dans le plan ÉTAPE 1 : convention `@ToString.Exclude`, interdiction `StructuredArguments.kv()` pour PII, factory method `PiiLogger.safeKv()`.
2. **SEC-002 (MEDIUM)** — Mettre à jour la description de `TenantMdcFilter` pour inclure le fallback `"UNAUTHENTICATED"`.
3. **SEC-003 (MEDIUM)** — Choisir entre `HandlerMethodArgumentResolver` automatique ou règle PR review documentée. Documenter la décision dans le plan.
4. **SEC-004 (MEDIUM)** — Ajouter la vérification `estimatedDocumentCount() == 0` dans le template `@RollbackExecution` du plan.
