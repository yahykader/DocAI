package fr.docai.adapter.out.mongodb.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * IT — Mongock Spring Boot integration: ApplicationContext loads + V001 applied at startup.
 * BR-MIG-007: throw-exception-if-cannot-obtain-lock ensures startup blocks on failure.
 * TestContainers.withReuse(true) — ADR-008.
 */
@SpringBootTest(
    classes = MongockStartupIT.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "mongock.migration-scan-package=fr.docai.adapter.out.mongodb.migration",
        "mongock.throw-exception-if-cannot-obtain-lock=true",
        "mongock.transaction-enabled=false",
        "spring.data.mongodb.auto-index-creation=false"
    }
)
@Testcontainers
class MongockStartupIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7"))
        .withReuse(true); // ADR-008: container reuse for CI memory efficiency

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
            () -> MONGO.getConnectionString() + "/docai_mongock_test");
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    MongoTemplate mongoTemplate;

    @Test
    void contextLoadsWithoutException() {
        assertNotNull(context,
            "ApplicationContext must load without exception (BR-MIG-007: startup blocks on failure)");
    }

    @Test
    void mongockAppliedV001OnStartup() {
        assertTrue(mongoTemplate.collectionExists("documents"),
            "Mongock must create documents collection during startup: "
            + "V001_setup_documents_collection applied successfully");
        assertTrue(mongoTemplate.collectionExists("document_summary_views"),
            "Mongock must create document_summary_views collection: ADR-011 (lastSyncedAt)");
    }

    /**
     * Minimal Spring Boot application for Mongock integration testing.
     * Scans only the migration package to avoid loading tenant/user adapters
     * which require additional domain beans.
     */
    @SpringBootApplication(scanBasePackages = "fr.docai.adapter.out.mongodb.migration")
    static class TestApp {}
}
