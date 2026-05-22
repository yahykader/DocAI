---
name: docai-module1-upload
description: "Implémente le Module 1.1 DocAI (Upload & Validation : POST /v1/documents multipart, aggregate Document, idempotence X-Idempotency-Key Valkey 24h, quota atomique Lua ADR-001, upload S3 multipart avec AbortMultipartUpload ADR-007, hash SHA-256 streaming déduplication, Outbox Pattern transaction atomique MongoDB, état PENDING, event DocumentUploaded, Mongock V001). Utiliser quand on demande d'implémenter l'upload de documents, la soumission de fichiers, l'idempotence d'upload, le stockage S3 initial, l'Outbox sur upload, ou le point d'entrée du pipeline documentaire DocAI. Prérequis : Module 0 (Auth + Keycloak + Commons) terminé."
---

# Module 1.1 — Upload & Validation de Documents

> **Prérequis :** Module 0 (Auth + Multi-Tenancy + Commons) terminé. ADR-001 et ADR-007 lus.  
> **Durée estimée :** 2 semaines  
> **C'est le point d'entrée de tout le pipeline — aucun autre module ne peut commencer sans lui.**

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/document/
├── Document.java             // Aggregate
├── DocumentId.java           // Value Object (UUID v4)
├── ContentHash.java          // Value Object (SHA-256, détection doublons)
├── DocumentStatus.java       // Enum (PENDING, CLASSIFIED, EXTRACTED, ANALYZED, COMPLETED, FAILED, NEEDS_REVIEW)
├── DocumentType.java         // Enum (FACTURE, RIB, CNI, ORDONNANCE, BULLETIN_SALAIRE, AUTRE)
└── events/
    └── DocumentUploaded.java // Domain Event
```

**State Machine obligatoire :**
```
PENDING → CLASSIFIED → EXTRACTED → ANALYZED → COMPLETED
   │                                              │
   └─────── FAILED ←──────────────────────────────┘
   └─────── NEEDS_REVIEW (faible confiance ou erreur)
```

### Aggregate Document
```java
// Champs obligatoires
String documentId;          // UUID v4 — généré côté serveur (jamais fourni par le client)
String tenantId;            // Isolation tenant
String fileName;
String mimeType;
String s3Key;               // {tenantId}/documents/{documentId}/{filename}
DocumentStatus status;      // Initialisé à PENDING
ContentHash contentHash;    // SHA-256 du fichier (déduplication)
String idempotencyKey;      // Valeur du header X-Idempotency-Key
Instant uploadedAt;
```

### Ports
```
Inbound:
  PORT-IN-REC-001 → SubmitDocumentUseCase
  PORT-IN-REC-002 → GetDocumentUseCase
  PORT-IN-REC-003 → ListDocumentsUseCase

Outbound:
  PORT-OUT-REC-001 → StoragePort          (upload S3)
  PORT-OUT-REC-002 → DocumentRepositoryPort
  PORT-OUT-REC-003 → OutboxEventPublisher  (commons-outbox)
  PORT-OUT-REC-004 → QuotaPort            (commons-quota)
```

### Adapters
```
docai-adapter-in-rest/
└── DocumentUploadController.java    // POST /v1/documents (multipart/form-data)

docai-adapter-out-s3/
└── AwsS3StorageAdapter.java         // Upload multipart + AbortMultipartUpload (ADR-007)

docai-adapter-out-mongodb/
└── DocumentMongoAdapter.java        // Collection documents (Mongock V001)

docai-adapter-out-kafka/
└── OutboxKafkaProducer.java         // commons-outbox — publication garantie
```

---

## SubmitDocumentUseCase — Flux Complet

```
Réception POST /v1/documents (multipart/form-data)
  │
  ├─ 1. Vérification JWT + extraction tenantId (TenantJwtFilter)
  ├─ 2. Vérification idempotence : SETNX Valkey "idempotency:{key}" TTL 24h
  │       Si clé existe → retourner le résultat existant (HTTP 200)
  ├─ 3. Validation format : PDF, PNG, JPEG, TIFF, WEBP uniquement
  ├─ 4. Validation taille : ≤ 20 MB
  ├─ 5. Vérification quota mensuel (@QuotaProtected — script Lua atomique ADR-001)
  │       Plan FREE : blocage si dépassé → HTTP 429
  │       Plan STARTER/PRO : overage autorisé
  ├─ 6. Calcul hash SHA-256 en streaming (pendant lecture, pas de double buffer)
  ├─ 7. Upload S3 : clé = {tenantId}/documents/{documentId}/{filename}
  │       AbortMultipartUpload si échec (ADR-007)
  ├─ 8. Transaction atomique MongoDB :
  │       - Créer aggregate Document (status=PENDING)
  │       - Créer OutboxMessage (event DocumentUploaded, partitionKey=documentId)
  │       (même ClientSession MongoDB — atomique)
  └─ 9. Retour HTTP 201 : { documentId, status: "PENDING", createdAt }
```

---

## ADR Obligatoires

### ADR-001 — Quota atomique Lua + Valkey
```
Le compteur de quota doit être incrémenté de façon atomique.
Ne jamais utiliser GET + SET séparément (race condition sous charge).
Utiliser le script Lua via commons-quota (@QuotaProtected).
Test obligatoire : 100 threads simultanés → compteur exact.
```

### ADR-007 — AbortMultipartUpload S3
```
Si l'upload multipart S3 échoue après avoir commencé,
appeler AbortMultipartUpload pour éviter les fichiers orphelins.
Le bucket de production doit avoir une Lifecycle Rule "abort incomplete multipart
uploads after 24h" en filet de sécurité.
```

---

## Endpoints

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| POST | `/v1/documents` | ANALYST, TENANT_ADMIN | Soumettre un document |
| GET | `/v1/documents/{id}` | Tous rôles | Statut et résultats |
| GET | `/v1/documents` | Tous rôles | Liste paginée (BR-PAG-001) |

**Réponse HTTP 201 :**
```json
{
  "data": {
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "fileName": "facture_oct.pdf",
    "mimeType": "application/pdf",
    "createdAt": "2026-05-21T10:00:00Z"
  }
}
```

---

## Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-REC-001 | Formats acceptés : PDF, PNG, JPEG, TIFF, WEBP | MUST |
| BR-REC-002 | Taille maximale : 20 MB par fichier | MUST |
| BR-REC-003 | `documentId` UUID v4 généré côté serveur — jamais fourni par le client | MUST |
| BR-REC-004 | Idempotence via `X-Idempotency-Key` (TTL 24h Valkey) | MUST |
| BR-REC-005 | Fichier stocké en S3 avant retour HTTP 201 | MUST |
| BR-REC-006 | Quota mensuel vérifié avant stockage (script Lua atomique) | MUST |
| BR-REC-007 | HTTP 201 + documentId dès confirmation S3 (traitement asynchrone) | MUST |
| BR-REC-008 | `DocumentUploaded` publié via Outbox Pattern (atomique avec persistance) | MUST |
| BR-REC-009 | Hash SHA-256 calculé en streaming (pas de double lecture) | MUST |

**Flux d'erreurs :**

| Cas | Réponse |
|-----|---------|
| Format non supporté | HTTP 422 — `UNSUPPORTED_FORMAT` |
| Fichier > 20 MB | HTTP 413 — `FILE_TOO_LARGE` |
| Quota dépassé (FREE) | HTTP 429 — `QUOTA_EXCEEDED` + date reset |
| Double soumission (même X-Idempotency-Key) | HTTP 200 — résultat existant |
| S3 indisponible | HTTP 503 — `STORAGE_UNAVAILABLE` |

---

## Migration Mongock — V001

```java
// V001_setup_documents_collection.java
@ChangeUnit(id = "V001_setup_documents_collection", order = "001")
public class V001SetupDocumentsCollection {

    @Execution
    public void execute(MongoDatabase db) {
        // Créer collection documents
        db.createCollection("documents");

        // Index principal : listing par tenant
        db.getCollection("documents").createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("tenantId"),
                Indexes.descending("createdAt"),
                Indexes.ascending("status")));

        // Index déduplication par hash + tenant
        db.getCollection("documents").createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("contentHash"),
                Indexes.ascending("tenantId")));
    }
}
```

---

## Clé S3 — Convention

```
Documents originaux  : {tenantId}/documents/{documentId}/{filename}
Exemple : acme-corp/documents/550e8400-e29b-41d4-a716-446655440000/facture_oct.pdf
```

---

## Scénarios BDD Obligatoires

```gherkin
Feature: Upload de documents

  Scenario: Upload réussi d'une facture PDF
    Given un tenant "acme-corp" avec plan Pro (450/10000 docs ce mois)
    And un utilisateur "alice" authentifié avec rôle ANALYST
    When alice soumet "facture_oct.pdf" (1.5MB) avec X-Idempotency-Key "idem-001"
    Then la réponse est HTTP 201
    And le documentId est un UUID v4 valide
    And le statut est "PENDING"
    And le fichier est présent dans S3 à la clé correcte
    And l'event DocumentUploaded est publié sur Kafka

  Scenario: Idempotence — double soumission
    Given alice a soumis "facture_oct.pdf" avec X-Idempotency-Key "idem-001"
    When alice soumet à nouveau le même fichier avec la même clé
    Then la réponse est HTTP 200 (pas HTTP 201)
    And un seul document existe en base
    And aucun doublon S3

  Scenario: Format non supporté
    When alice soumet "document.exe" (format EXE)
    Then la réponse est HTTP 422
    And le message est "UNSUPPORTED_FORMAT"

  Scenario: Quota dépassé — plan FREE
    Given un tenant avec plan FREE et 10/10 documents ce mois
    When il soumet un nouveau document
    Then la réponse est HTTP 429 "QUOTA_EXCEEDED"
    And le corps contient la date de réinitialisation du quota

  Scenario: Panne Kafka — document quand même persisté
    Given le broker Kafka est indisponible
    When alice soumet un document valide
    Then la réponse est HTTP 201 (traitement asynchrone garanti)
    And le document est persisté en MongoDB avec status PENDING
    And l'OutboxMessage est en statut PENDING (sera publié à la reprise Kafka)
```

---

## NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-REC-001 | Upload fichier 20MB | < 2s (P95) |
| NFR-REC-002 | Réponse GET /v1/documents (liste) | < 100ms (P95) |
| NFR-REC-003 | Idempotence : 100 threads simultanés avec même clé | 1 seul document créé |
| NFR-REC-004 | Quota atomique : 100 threads simultanés | Compteur exact, 0 race condition |

---

## Commons à Utiliser (ne pas réimplémenter)

- `commons-multitenancy` → `MongoTenantFilter` injecte tenantId dans `DocumentMongoAdapter`
- `commons-outbox` → `OutboxRepository` + `OutboxRelay` pour publication Kafka garantie
- `commons-quota` → `@QuotaProtected` + script Lua atomique (ADR-001)
- `commons-api` → `IdempotencyFilter` (header X-Idempotency-Key, TTL 24h Valkey)
- `commons-audit` → `@Audited` sur `SubmitDocumentUseCase`
- `commons-testing` → `DocumentTestBuilder`, `AbstractIntegrationTest`

---

## Definition of Done

- [ ] Bucket S3 production configuré : SSE-KMS + Lifecycle Rule multipart 24h (ADR-007)
- [ ] Idempotence testée : double soumission même clé → HTTP 200, 1 seul document
- [ ] Outbox Pattern testé : panne Kafka simulée → document publié à la reprise
- [ ] Rate limiting testé : 100 req/min (Starter), 1000 req/min (Pro)
- [ ] Upload fichier 20MB testé — AbortMultipartUpload vérifié en cas d'échec
- [ ] Migration Mongock V001 appliquée — EXPLAIN PLAN validé sur toutes les requêtes
- [ ] Métrique `docai_document_upload_total` exposée Prometheus
- [ ] Hash SHA-256 calculé en streaming (pas de double lecture mémoire)
- [ ] Couverture domaine ≥ 90%

---

## Logs Obligatoires

```
INFO  — Document soumis : documentId, tenantId, mimeType, sizeBytes, s3Key
WARN  — Quota 80% atteint lors de l'upload : tenantId, docsUsed/docsLimit
ERROR — Upload S3 échoué : documentId, tenantId, s3Key, raison
INFO  — Outbox event publié : documentId, topic, partitionKey
```
> Jamais de PII dans les logs → `[PII_MASKED]`. Toujours `traceId` + `tenantId`.
