package fr.docai.adapter.out.mongodb.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * V001 — Initial MongoDB schema: documents, document_summary_views, users.
 *
 * <p>ADR-010: tenantId FIRST in every composite index.
 * ADR-011: document_summary_views created here; lastSyncedAt populated in Partie 5.
 * BR-MIG-003: no @Indexed/@CompoundIndex in Java code — indexes created here only.
 * BR-MIG-004: {@code @RollbackExecution} required.
 * SEC-004: rollback drops only when collection is empty (fresh environment guard).
 */
@ChangeUnit(
    id = "V001_setup_documents_collection",
    order = "001",
    author = "docai-team",
    transactional = false
)
public class V001SetupDocumentsCollection {

    private static final Logger log = LoggerFactory.getLogger(V001SetupDocumentsCollection.class);

    /** Creates all collections with ADR-010 indexes. */
    @Execution
    public void execute(MongoDatabase db) {
        createDocumentsIndexes(db.getCollection("documents"));
        createSummaryViewsIndexes(db.getCollection("document_summary_views"));
        createUsersIndexes(db.getCollection("users"));
    }

    /** Drops collections only when empty — SEC-004 guard (safe in fresh environments only). */
    @RollbackExecution
    public void rollback(MongoDatabase db) {
        dropIfEmpty(db, "users");
        dropIfEmpty(db, "document_summary_views");
        dropIfEmpty(db, "documents");
    }

    private void createDocumentsIndexes(MongoCollection<Document> col) {
        // ADR-010: tenantId FIRST — filtered list by status (primary query pattern)
        createIndex(col,
            new Document("tenantId", 1).append("status", 1).append("createdAt", -1),
            new IndexOptions().name("idx_tenantId_status_createdAt"));
        // ADR-010: tenantId FIRST — filtered list by document type
        createIndex(col,
            new Document("tenantId", 1).append("type", 1).append("createdAt", -1),
            new IndexOptions().name("idx_tenantId_type_createdAt"));
        // ADR-010: tenantId FIRST — chronological pagination
        createIndex(col,
            new Document("tenantId", 1).append("createdAt", -1),
            new IndexOptions().name("idx_tenantId_createdAt"));
        // ADR-010: tenantId FIRST — per-tenant SHA-256 deduplication on upload
        createIndex(col,
            new Document("tenantId", 1).append("contentHash", 1),
            new IndexOptions().unique(true).name("idx_tenantId_contentHash_unique"));
        // Single-field sparse unique — X-Idempotency-Key header enforcement
        createIndex(col,
            new Document("idempotencyKey", 1),
            new IndexOptions().unique(true).sparse(true).name("idx_idempotencyKey_unique"));
    }

    private void createSummaryViewsIndexes(MongoCollection<Document> col) {
        // ADR-010: tenantId FIRST — unique read-model lookup per tenant+document (ADR-011)
        createIndex(col,
            new Document("tenantId", 1).append("documentId", 1),
            new IndexOptions().unique(true).name("idx_tenantId_documentId_unique"));
    }

    private void createUsersIndexes(MongoCollection<Document> col) {
        // ADR-010: tenantId FIRST — unique email per tenant (prevents cross-tenant collision)
        createIndex(col,
            new Document("tenantId", 1).append("email", 1),
            new IndexOptions().unique(true).name("idx_tenantId_email_unique"));
    }

    private void createIndex(MongoCollection<Document> col, Document keys, IndexOptions options) {
        String collName = col.getNamespace().getCollectionName();
        try {
            col.createIndex(keys, options);
            log.info("V001 index created: {} on {}", options.getName(), collName);
        } catch (com.mongodb.MongoCommandException e) {
            // Idempotent: index already exists (common in re-applied migrations)
            if (e.getErrorMessage() != null && e.getErrorMessage().contains("index with key pattern")) {
                log.debug("V001 index already exists: {} on {} (idempotent)", options.getName(), collName);
            } else {
                log.error("V001 failed to create index '{}' on {}: {}", options.getName(), collName, e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            log.error("V001 failed to create index '{}' on {}: {}", options.getName(), collName, e.getMessage());
            throw e;
        }
    }

    private void dropIfEmpty(MongoDatabase db, String collection) {
        var col = db.getCollection(collection);
        long docCount = col.countDocuments();
        if (docCount == 0) {
            col.drop();
            log.info("V001 rollback: dropped empty collection '{}'", collection);
        } else {
            log.warn("V001 rollback: skipped drop of non-empty collection '{}' ({} documents present) — SEC-004 guard active",
                collection, docCount);
        }
    }
}
