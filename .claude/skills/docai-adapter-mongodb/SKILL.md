---
name: docai-adapter-mongodb
description: "Crée un Adapter MongoDB dans docai-adapter-out-mongodb. Utiliser pour implémenter un repository, un document MongoDB, ou une migration Mongock. Applique les conventions DocAI : isolation tenant obligatoire, index strategy, Mongock migrations versionnées, pas de requête sans filtre tenantId."
---

# DocAI — Créer un Adapter MongoDB

## Localisation

Module : `docai-adapter-out-mongodb`
Package : `fr.docai.adapter.out.mongodb`

## Règles absolues

- **TOUTE requête MongoDB doit filtrer par `tenantId`** — violation = fuite de données entre tenants
- Collections en **`snake_case` pluriel** : `documents`, `extraction_results`, `fraud_analyses`, `audit_entries`
- Les migrations de schéma passent par **Mongock** — jamais de modification manuelle en prod
- **`auto-index-creation: false`** en production — index créés uniquement via Mongock
- **JAMAIS `@Indexed` dans `@Document`** — les index se créent uniquement via Mongock migrations
- Les documents MongoDB finissent par `Document` (ex: `DocumentMongoDocument`)
- Les adapters MongoDB finissent par `MongoAdapter`
- **ADR-010 :** `EXPLAIN PLAN` obligatoire avant chaque merge ajoutant une requête MongoDB

## Structure type — Document MongoDB

```java
// Convention : snake_case pluriel pour le nom de la collection
@Document(collection = "documents")
public class DocumentMongoDocument {

    @Id
    private String id;

    @Field("tenant_id")
    // PAS de @Indexed ici — les index sont créés uniquement via Mongock (ADR-010)
    private String tenantId;

    @Field("document_id")
    private String documentId;

    @Field("status")
    private String status;

    @Field("created_at")
    private Instant createdAt;  // Toujours Instant, jamais String ou Date

    @Field("updated_at")
    private Instant updatedAt;

    // Constructeur, getters — pas de setters publics sur les champs critiques
}
```

## Structure type — MongoAdapter

```java
@Component
public class ProcessedDocumentMongoAdapter implements ProcessedDocumentRepository {

    private final ProcessedDocumentMongoRepository mongoRepository;

    public ProcessedDocumentMongoAdapter(ProcessedDocumentMongoRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    @Override
    public Optional<ProcessedDocument> findById(DocumentId id, TenantId tenantId) {
        // TOUJOURS filtrer par tenantId
        return mongoRepository
            .findByDocumentIdAndTenantId(id.value(), tenantId.value())
            .map(this::toDomain);
    }

    @Override
    public void save(ProcessedDocument aggregate) {
        ProcessedDocumentDocument doc = toDocument(aggregate);
        mongoRepository.save(doc);
    }

    // Mapping domaine ↔ document MongoDB (jamais exposer le document MongoDB au domaine)
    private ProcessedDocument toDomain(ProcessedDocumentDocument doc) { ... }
    private ProcessedDocumentDocument toDocument(ProcessedDocument domain) { ... }
}
```

## Repository Spring Data

```java
// Interface Spring Data — requêtes avec filtre tenantId OBLIGATOIRE
public interface ProcessedDocumentMongoRepository
    extends MongoRepository<ProcessedDocumentDocument, String> {

    // Toujours inclure tenantId dans les requêtes
    Optional<ProcessedDocumentDocument> findByDocumentIdAndTenantId(
        String documentId, String tenantId
    );

    List<ProcessedDocumentDocument> findByTenantIdAndStatus(
        String tenantId, String status, Pageable pageable
    );

    // Jamais : findByDocumentId(String documentId) — risque cross-tenant
}
```

## Migration Mongock — Convention V{NNN}_{module}_{description}

```java
// Convention OBLIGATOIRE : V{numero}_{module}_{description}
// Exemples : V001_setup_documents_collection, V002_documents_add_tenant_status_index
@ChangeUnit(id = "V001_setup_documents_collection", order = "001", author = "docai-team")
public class V001SetupDocumentsCollection {

    @Execution
    public void execute(MongoDatabase db) {
        // Index composé tenantId + status (toujours tenantId EN PREMIER — ADR-010)
        db.getCollection("documents").createIndex(
            Indexes.ascending("tenant_id", "status", "created_at"),
            new IndexOptions().name("idx_tenant_status_created")
        );

        // Index unique documentId + tenantId
        db.getCollection("documents").createIndex(
            Indexes.ascending("document_id", "tenant_id"),
            new IndexOptions().unique(true).name("idx_document_tenant_unique")
        );
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        db.getCollection("documents").dropIndex("idx_tenant_status_created");
        db.getCollection("documents").dropIndex("idx_document_tenant_unique");
    }
}
```

## Index strategy obligatoire par collection

| Champ | Type d'index | Raison |
|-------|-------------|--------|
| `tenant_id` | Simple | Isolation tenant — toutes les requêtes |
| `document_id + tenant_id` | Composé unique | Éviter les doublons cross-tenant |
| `status + tenant_id` | Composé | Requêtes par statut par tenant |
| `created_at` | Simple TTL si applicable | Nettoyage automatique |

## Checklist

- [ ] Chaque méthode du repository filtre par `tenantId`
- [ ] Collection en `snake_case` pluriel (`documents`, pas `Document` ni `processed_documents`)
- [ ] `@Document` sans `@Indexed` — index créés uniquement via Mongock
- [ ] Migration nommée `V{NNN}_{module}_{description}` (ex: `V001_setup_documents_collection`)
- [ ] `tenantId` **en premier** dans tous les index composites (ADR-010)
- [ ] Migration avec `@RollbackExecution` obligatoire
- [ ] Mapping domaine ↔ MongoDB dans l'adapter (jamais exposer le Document au domaine)
- [ ] **ADR-010 :** `EXPLAIN PLAN` vérifié avant merge sur toutes les requêtes ajoutées
- [ ] Test d'intégration avec TestContainers MongoDB (via `AbstractIntegrationTest`)
