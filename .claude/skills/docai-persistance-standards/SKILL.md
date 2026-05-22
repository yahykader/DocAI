---
name: docai-persistance-standards
description: "Standards MongoDB DocAI — collections (nommage snake_case), index strategy (ADR-010), migrations Mongock (conventions V001→V008), pagination globale (BR-PAG-001 à 008), EXPLAIN PLAN obligatoire, API versioning /v1/, logs structurés JSON, métriques Micrometer, Clean Code standards (méthodes ≤ 20 lignes, classes ≤ 200 lignes). Utiliser avant d'implémenter tout adapter MongoDB, toute migration Mongock, tout endpoint paginé, ou quand on demande les conventions de nommage des collections, les index, la pagination, le versioning API, ou les standards de qualité de code DocAI."
---

# DocAI — Persistance & Standards
## MongoDB · Mongock · Pagination · Versioning API · Clean Code

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 1 (Module 1.C)

---

## 1. Collections MongoDB — Nommage et Rôles

**Convention : `snake_case` pluriel**

| Collection | Rôle | Caractéristique clé |
|-----------|------|---------------------|
| `documents` | Aggregate racine du pipeline | Statut, metadata, S3 key |
| `extraction_results` | Résultats OCR + LLM | Schéma `fields[]` variable par type |
| `fraud_analyses` | Analyse fraude — **immuable après création** | Tableau `signals[]` variable |
| `audit_entries` | Journal immuable — **append-only** | TTL index 5 ans |
| `outbox_events` | Outbox Pattern | Statut PENDING/PUBLISHED/FAILED |
| `document_summary_views` | Read Model CQRS Dashboard | Agrégat dénormalisé |
| `webhook_deliveries` | Log livraisons webhooks | Tableau `attempts[]` |
| `api_keys` | Clés API clients | Hash SHA-256+sel, jamais en clair |
| `tenant_configs` | Configuration par tenant | Plan, quotas |
| `subscriptions` | Abonnements Stripe | Cycle de vie billing |
| `login_history` | Historique connexions | TTL index 90 jours |
| `invitation_tokens` | Tokens d'invitation | TTL index 7 jours |
| `password_reset_tokens` | Tokens reset MDP | TTL index 1h |
| `notifications` | Notifications in-app | TTL index 90 jours |
| `dlq_messages` | Messages DLQ archivés | Rétention 90 jours |

**Règles pour les champs :**
- Champs : `camelCase` (standard MongoDB)
- Dates : suffixe `At` (`createdAt`, `updatedAt`, `expiresAt`, `processedAt`)
- Booléens : préfixe `is` ou verbe (`isRead`, `used`, `enabled`)
- Identifiants : `_id` pour la clé primaire, `documentId`, `tenantId` pour références

---

## 2. Stratégie d'Indexation (ADR-010)

**Règle absolue :** `auto-index-creation=false` en production. Tous les index via Mongock uniquement.

**Règle ADR-010 :** `tenantId` est le **premier champ** de tous les index composites.

### Index par collection

**`documents` :**
```
{tenantId, status, createdAt}      — Compound — Liste filtrée par statut
{tenantId, type, createdAt}        — Compound — Liste filtrée par type
{tenantId, createdAt}              — Compound — Pagination chronologique
{contentHash, tenantId}            — Unique — Déduplication upload
{idempotencyKey}                   — Unique — Idempotence X-Idempotency-Key
```

**`extraction_results` :**
```
{documentId}                       — Unique — Lookup par documentId
{tenantId, status}                 — Compound — Dashboard filtré
```

**`fraud_analyses` :**
```
{documentId}                       — Unique — Immuabilité (1 analyse par doc)
{tenantId, riskLevel, createdAt}   — Compound — Queue révision
{tenantId, score}                  — Compound — Distribution scores
```

**`audit_entries` :**
```
{tenantId, action, occurredAt}     — Compound — Audit log filtré
{resourceType, resourceId}         — Compound — Historique ressource
{occurredAt}                       — TTL 5 ans — Archivage automatique
```

**`outbox_events` :**
```
{status, createdAt}                — Compound — Poller récupère PENDING par ordre
```

**`document_summary_views` :**
```
{tenantId, status, riskLevel}      — Compound — Dashboard filtres combinés
{tenantId, createdAt}              — Compound — Tri chronologique
{tenantId, type}                   — Compound — Filtre par type
{tenantId, lastSyncedAt}           — Compound — Réconciliation ADR-011
```

**`notifications` :**
```
{tenantId, userId, read, createdAt} — Compound — Listing non lues
{createdAt}                         — TTL 90 jours
```

### EXPLAIN PLAN obligatoire

Avant chaque merge ajoutant une requête MongoDB :

```javascript
// Vérifier IXSCAN (bon) vs COLLSCAN (bloquant)
db.documents.find({tenantId: "acme", status: "COMPLETED"})
            .explain("executionStats")
// winningPlan.stage doit être "IXSCAN"
// totalDocsExamined ≈ nReturned
```

**Partial index (si actif < 20% du volume) :**
```javascript
// Exemple : index uniquement sur les documents actifs
db.documents.createIndex(
  {tenantId: 1, createdAt: -1},
  {partialFilterExpression: {status: {$in: ["PENDING","CLASSIFIED","EXTRACTED"]}}}
)
```

---

## 3. Migrations Mongock — Convention V001→VNNN

**Convention :** `V{numero}_{module}_{description}`

| Migration | Nom correct |
|-----------|-------------|
| V001 | `V001_setup_documents_collection` |
| V002 | `V002_outbox_events_create_collection` |
| V003 | `V003_extraction_results_create_collection` |
| V004 | `V004_fraud_analyses_create_collection` |
| V005 | `V005_audit_entries_create_ttl_collection` |
| V006 | `V006_dashboard_summary_views_create_collection` |
| V007 | `V007_webhook_deliveries_create_collection` |
| V008 | `V008_api_keys_create_collection` |
| V009 | `V009_classification_add_model_version_index` |
| ... | Continuer la séquence par module |

```java
// Structure obligatoire d'une migration Mongock
@ChangeUnit(id = "V001_setup_documents_collection",
            order = "001",
            author = "docai-team")
public class V001SetupDocumentsCollection {

    @Execution
    public void execute(MongoDatabase db) {
        // Créer collection + index
        db.getCollection("documents").createIndex(
            Indexes.ascending("tenantId", "status", "createdAt"),
            new IndexOptions().name("idx_tenant_status_created")
        );
        // Index unique idempotence
        db.getCollection("documents").createIndex(
            Indexes.ascending("idempotencyKey"),
            new IndexOptions().unique(true).name("idx_idempotency")
        );
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        // Toujours définir un rollback
        db.getCollection("documents").drop();
    }
}
```

**Règles Mongock :**

| ID | Règle |
|----|-------|
| BR-MIG-001 | Chaque migration dans sa propre classe `@ChangeUnit` |
| BR-MIG-002 | Migrations backward-compatible — jamais supprimer un champ en 1 migration |
| BR-MIG-003 | `auto-index-creation=false` en production — uniquement via Mongock |
| BR-MIG-004 | Chaque migration a une méthode `@RollbackExecution` |
| BR-MIG-005 | Pas de logique métier dans une migration — uniquement DDL |
| BR-MIG-006 | Migrations testées en staging avant la production |
| BR-MIG-007 | Migration échouée = application refuse de démarrer + alerte Tech Lead |

---

## 4. Pagination Globale — Standard obligatoire

**S'applique à TOUS les endpoints liste de TOUS les modules.**

### Paramètres de requête

| Paramètre | Type | Défaut | Maximum |
|-----------|------|--------|---------|
| `page` | Integer | `0` | — |
| `size` | Integer | `20` | **100** |
| `sort` | String | `createdAt,desc` | — |

### Format de réponse paginée

```json
{
  "data": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1250,
    "totalPages": 63,
    "first": true,
    "last": false
  }
}
```

### Règles

| ID | Règle |
|----|-------|
| BR-PAG-001 | Tous les endpoints liste utilisent `page`, `size`, `sort` |
| BR-PAG-002 | Taille maximale **100** — au-delà HTTP 400 |
| BR-PAG-003 | Taille par défaut **20** |
| BR-PAG-004 | Réponse toujours avec `totalElements` et `totalPages` |
| BR-PAG-005 | `size > 100` → HTTP 400 "Maximum page size is 100" |
| BR-PAG-006 | Tri par défaut `createdAt,desc` sauf indication contraire |
| BR-PAG-007 | Champs de tri autorisés documentés dans OpenAPI |
| BR-PAG-008 | Implémenté dans `commons-api` — ne jamais réimplémenter |

### Endpoints paginés par module

| Module | Endpoint | Tri défaut | Filtres |
|--------|----------|-----------|---------|
| 1 | GET /v1/documents | createdAt,desc | status, type, dateFrom, dateTo |
| 3 | GET /v1/fraud/review-queue | score,desc | riskLevel, reviewer |
| 5 | GET /v1/dashboard/documents | createdAt,desc | status, type, riskLevel |
| 5 | GET /v1/notifications | createdAt,desc | read, type |
| 6 | GET /v1/api-keys | createdAt,desc | scope |

---

## 5. Versioning API

**Toutes les routes :** préfixe `/v1/`

**Stratégie d'évolution :**
- Breaking change → nouvelle version `/v2/`
- `/v1/` maintenu 6 mois minimum après sortie de `/v2/`
- Dépréciation annoncée via header `Deprecation: date` + `Sunset: date`
- OpenAPI spec générée automatiquement en CI et publiée sur GitHub Pages

---

## 6. Clean Code — Standards obligatoires

| Règle | Seuil | Outil |
|-------|-------|-------|
| Longueur méthode | ≤ 20 lignes | Checkstyle |
| Longueur classe | ≤ 200 lignes | SonarCloud |
| Paramètres par méthode | ≤ 4 | Checkstyle |
| Complexité cyclomatique | ≤ 10 | SonarCloud |
| Couverture tests domaine | ≥ 90% | JaCoCo |
| Score mutation testing (PIT) | ≥ 85% | PIT Maven |
| Code dupliqué | ≤ 3% | SonarCloud |
| Technical Debt ratio | ≤ 5% | SonarCloud |

**Quality Gate SonarCloud — bloquant en CI :**
- 0 bug, 0 vulnérabilité, 0 security hotspot non résolu
- Couverture ≥ 90% sur nouveau code
- Duplication ≤ 3%

**Conventional Commits (obligatoire) :**
```
feat(recognition): add confidence threshold validation
fix(fraud): correct SIRET checksum algorithm
refactor(pipeline): extract retry logic to RetryPolicy
test(extraction): add BDD scenarios for corrupted PDF
docs(api): update OpenAPI spec for /v1/documents
chore(deps): upgrade Spring Boot to 4.0.1
perf(dashboard): add compound index on tenantId + createdAt
```
