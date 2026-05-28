package fr.docai.adapter.out.mongodb.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * IT — V001 : collections + indexes ADR-010 + EXPLAIN PLAN + rollback SEC-004.
 * TestContainers.withReuse(true) — ADR-008.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V001SetupDocumentsCollectionIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7"))
        .withReuse(true); // ADR-008: container reuse for CI memory efficiency

    static MongoDatabase db;
    static V001SetupDocumentsCollection migration;

    @BeforeAll
    static void setUp() {
        MongoClient client = MongoClients.create(MONGO.getConnectionString());
        db = client.getDatabase("docai_test_v001");
        db.drop(); // clean slate — withReuse(true) can retain state between runs
        migration = new V001SetupDocumentsCollection();
    }

    @Test
    @Order(1)
    void executionCreatesAllCollections() {
        migration.execute(db);

        var names = new ArrayList<String>();
        db.listCollectionNames().into(names);

        assertTrue(names.contains("documents"),
            "documents collection must exist after V001 execution");
        assertTrue(names.contains("document_summary_views"),
            "document_summary_views collection must exist — ADR-011 (lastSyncedAt)");
        assertTrue(names.contains("users"),
            "users collection must exist — index {tenantId,email} created by V001 (BR-MIG-003)");
    }

    @Test
    @Order(2)
    void documentsHasTenantIdFirstCompositeIndex() {
        // ADR-010: tenantId must be position-0 in every composite index
        var indexes = new ArrayList<Document>();
        db.getCollection("documents").listIndexes().into(indexes);

        boolean found = indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            if (key == null) return false;
            List<String> fields = new ArrayList<>(key.keySet());
            return fields.size() >= 2
                && "tenantId".equals(fields.get(0))
                && fields.contains("status");
        });

        assertTrue(found,
            "Composite index {tenantId,status,createdAt} with tenantId first must exist (ADR-010)");
    }

    @Test
    @Order(3)
    void documentSummaryViewsHasUniqueTenantDocumentIndex() {
        var indexes = new ArrayList<Document>();
        db.getCollection("document_summary_views").listIndexes().into(indexes);

        boolean found = indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            if (key == null) return false;
            List<String> fields = new ArrayList<>(key.keySet());
            boolean isUnique = Boolean.TRUE.equals(idx.getBoolean("unique"));
            return isUnique
                && fields.size() == 2
                && "tenantId".equals(fields.get(0))
                && "documentId".equals(fields.get(1));
        });

        assertTrue(found,
            "Unique index {tenantId,documentId} must exist in document_summary_views (ADR-010 + ADR-011)");
    }

    @Test
    @Order(6)
    void explainPlanUsesIxscanNotCollscan() {
        // ADR-010: COLLSCAN is forbidden — every query must use an index
        db.getCollection("documents").insertOne(
            new Document("tenantId", "acme")
                .append("status", "PENDING")
                .append("createdAt", Instant.now()));

        Document explain = db.getCollection("documents")
            .find(Filters.and(
                Filters.eq("tenantId", "acme"),
                Filters.eq("status", "PENDING")))
            .explain();

        assertFalse(containsCollscan(explain),
            "COLLSCAN detected — winningPlan.stage must be IXSCAN (ADR-010). "
            + "Run: db.documents.find({tenantId:'acme',status:'PENDING'}).explain('executionStats')");

        String stage = extractInnermostStage(explain);
        assertEquals("IXSCAN", stage,
            "winningPlan must use IXSCAN for {tenantId, status} query (ADR-010)");
    }

    @Test
    @Order(4)
    void usersHasUniqueTenantEmailIndex() {
        var indexes = new ArrayList<Document>();
        db.getCollection("users").listIndexes().into(indexes);

        boolean found = indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            if (key == null) return false;
            List<String> fields = new ArrayList<>(key.keySet());
            boolean isUnique = Boolean.TRUE.equals(idx.getBoolean("unique"));
            return isUnique
                && fields.size() == 2
                && "tenantId".equals(fields.get(0))
                && "email".equals(fields.get(1));
        });

        assertTrue(found,
            "Unique index {tenantId,email} with tenantId first must exist in users (ADR-010 + BR-MIG-003)");
    }

    @Test
    @Order(5)
    void idempotencyKeyIndexIsSparseAndUnique() {
        var indexes = new ArrayList<Document>();
        db.getCollection("documents").listIndexes().into(indexes);

        boolean found = indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            if (key == null) return false;
            List<String> fields = new ArrayList<>(key.keySet());
            boolean isUnique = Boolean.TRUE.equals(idx.getBoolean("unique"));
            boolean isSparse = Boolean.TRUE.equals(idx.getBoolean("sparse"));
            return isUnique && isSparse
                && fields.size() == 1
                && "idempotencyKey".equals(fields.get(0));
        });

        assertTrue(found,
            "idx_idempotencyKey_unique must be both unique and sparse (partial-index pattern)");
    }

    @Test
    @Order(7)
    void rollbackDropsEmptyCollections() {
        // SEC-004: drop only if empty — safe rollback in fresh environments only
        db.getCollection("documents").deleteMany(new Document());

        migration.rollback(db);

        var names = new ArrayList<String>();
        db.listCollectionNames().into(names);

        assertFalse(names.contains("documents"),
            "rollback() must drop documents when empty (SEC-004)");
        assertFalse(names.contains("document_summary_views"),
            "rollback() must drop document_summary_views when empty (SEC-004)");
        assertFalse(names.contains("users"),
            "rollback() must drop users when empty (SEC-004)");
    }

    // --- helpers ---

    private boolean containsCollscan(Document node) {
        if (node == null) return false;
        for (String key : node.keySet()) {
            Object val = node.get(key);
            if ("stage".equals(key) && "COLLSCAN".equals(val)) return true;
            if (val instanceof Document nested && containsCollscan(nested)) return true;
        }
        return false;
    }

    private String extractInnermostStage(Document explain) {
        try {
            Document queryPlanner = explain.get("queryPlanner", Document.class);
            Document winningPlan = queryPlanner.get("winningPlan", Document.class);
            return resolveLeafStage(winningPlan);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String resolveLeafStage(Document plan) {
        if (plan == null) return "UNKNOWN";
        Document inputStage = plan.get("inputStage", Document.class);
        if (inputStage != null) return resolveLeafStage(inputStage);
        return plan.getString("stage");
    }
}
