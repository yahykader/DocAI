# Data Model: Module C — Persistance & Standards

**Branch**: `005-persistance-standards` | **Date**: 2026-05-28

## MongoDB Collections (Migration V001)

### Collection: `documents`

| Field | Type | Index | Notes |
|-------|------|-------|-------|
| `_id` | ObjectId | PRIMARY | Auto-generated |
| `tenantId` | String | COMPOUND[1] first | FIRST dans l'index composite (ADR-010) |
| `status` | String (enum) | COMPOUND[2] | PENDING, PROCESSING, COMPLETED, FAILED |
| `createdAt` | Date | COMPOUND[3] desc | Horodatage de création |
| `updatedAt` | Date | — | Mise à jour automatique |

**Index composite V001**:
```json
{ "tenantId": 1, "status": 1, "createdAt": -1 }
```
EXPLAIN PLAN cible: `IXSCAN` (validé par `V001SetupDocumentsCollectionIT`)

---

### Collection: `document_summary_views`

| Field | Type | Notes |
|-------|------|-------|
| `_id` | ObjectId | Auto-generated |
| `tenantId` | String | FIRST dans tous les index composites (ADR-010) |
| `documentId` | String | Référence logique vers `documents._id` |
| `status` | String | Dénormalisé pour lecture rapide (CQRS) |
| `lastSyncedAt` | Date | **MANDATORY (ADR-011)** — null jusqu'à Partie 5 |

> **Note ADR-011**: Structure et champ `lastSyncedAt` créés en Partie 1 via V001.
> La valeur est renseignée en Partie 5 par `DashboardProjectionConsumer` (Kafka).
> Un `ReconciliationScheduler` toutes les 5 minutes alertera si lag > 30s.

---

## Java Records (docai-commons)

### `ApiResponse<T>`

```java
// fr.docai.commons.pagination.ApiResponse
public record ApiResponse<T>(
    List<T> data,
    PageInfo page
) {}
```

**Sérialisation JSON**:
```json
{
  "data": [ "..." ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

---

### `PageInfo`

```java
// fr.docai.commons.pagination.PageInfo
public record PageInfo(
    int number,
    int size,
    long totalElements,
    int totalPages
) {
    public static PageInfo from(Page<?> springPage) {
        return new PageInfo(
            springPage.getNumber(),
            springPage.getSize(),
            springPage.getTotalElements(),
            springPage.getTotalPages()
        );
    }
}
```

---

### `PaginationParams`

```java
// fr.docai.commons.pagination.PaginationParams
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

Violation `size > 100` → HTTP 400 automatique via `@Valid` sur le paramètre controller (BR-PAG-005).

---

## Entity Relationships

```
documents (1) ──────────────────────────── (1) document_summary_views
   _id                                         documentId  (ref. logique)
   tenantId ──────────────────────────────→   tenantId
   status                                      status      (dénormalisé)
   createdAt                                   lastSyncedAt (ADR-011)
   updatedAt
```

---

## Document Status Lifecycle

```
         ┌─────────────────────┐
 upload  │                     │
─────────► PENDING ──► PROCESSING ──► COMPLETED
                          │
                          └──► FAILED ──► (retry) ──► PROCESSING
```

Défini ici pour référence d'indexation ; implémenté dans les modules fonctionnels (Partie 4).
