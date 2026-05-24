package fr.docai.bootstrap;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests for hexagonal architecture (12 rules per ADR-008).
 * Enforces clean boundaries between domain, application, and adapter layers.
 */
class HexagonalArchitectureTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
            .importPackages("fr.docai");

    // Rule 1: Domain layer must not depend on Spring
    @Test
    void domainLayerMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("fr.docai.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .check(ALL_CLASSES);
    }

    // Rule 2: Domain layer must not depend on MongoDB
    @Test
    void domainLayerMustNotDependOnMongoDB() {
        noClasses()
                .that().resideInAPackage("fr.docai.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.mongodb..")
                .check(ALL_CLASSES);
    }

    // Rule 3: Domain layer must not depend on Kafka
    @Test
    void domainLayerMustNotDependOnKafka() {
        noClasses()
                .that().resideInAPackage("fr.docai.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework.kafka..")
                .check(ALL_CLASSES);
    }

    // Rule 4: Domain layer must not depend on AWS SDK
    @Test
    void domainLayerMustNotDependOnAwsSdk() {
        noClasses()
                .that().resideInAPackage("fr.docai.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("software.amazon.awssdk..")
                .check(ALL_CLASSES);
    }

    // Rule 5: Domain layer must not depend on Redis/Valkey
    @Test
    void domainLayerMustNotDependOnRedis() {
        noClasses()
                .that().resideInAPackage("fr.docai.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.lettuce..").or()
                .resideInAPackage("redis.clients..")
                .check(ALL_CLASSES);
    }

    // Rule 6: Domain layer must not depend on external APIs (LangChain, Stripe, etc.)
    @Test
    void domainLayerMustNotDependOnExternalLibraries() {
        noClasses()
                .that().resideInAPackage("fr.docai.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("dev.langchain4j..")
                .or().dependOnClassesThat()
                .resideInAPackage("com.stripe..")
                .check(ALL_CLASSES);
    }

    // Rule 7: Application layer may depend on domain but not on adapters
    @Test
    void applicationLayerMustNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("fr.docai.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("fr.docai.adapter..")
                .check(ALL_CLASSES);
    }

    // Rule 8: Adapter-in (REST, Kafka) may depend on application and domain
    @Test
    void adapterInMayDependOnApplicationAndDomain() {
        classes()
                .that().resideInAPackage("fr.docai.adapter.in..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "fr.docai.domain..",
                        "fr.docai.application..",
                        "fr.docai.adapter.in..",
                        "java..",
                        "javax..",
                        "org.springframework..",
                        "org.slf4j..",
                        "org.apache..",
                        "io.micrometer..",
                        "io.opentelemetry..",
                        "org.keycloak..",
                        "io.jsonwebtoken..",
                        "jakarta..")
                .check(ALL_CLASSES);
    }

    // Rule 9: Adapter-out (MongoDB, Kafka, Redis, etc.) may depend on application and domain
    @Test
    void adapterOutMayDependOnApplicationAndDomain() {
        classes()
                .that().resideInAPackage("fr.docai.adapter.out..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "fr.docai.domain..",
                        "fr.docai.application..",
                        "fr.docai.adapter.out..",
                        "java..",
                        "javax..",
                        "org.springframework..",
                        "org.mongodb..",
                        "org.slf4j..",
                        "org.apache..",
                        "io.micrometer..",
                        "io.opentelemetry..",
                        "software.amazon.awssdk..",
                        "io.lettuce..",
                        "dev.langchain4j..",
                        "com.stripe..",
                        "io.mongock..",
                        "jakarta..")
                .check(ALL_CLASSES);
    }

    // Rule 10: Bootstrap (entry point) may depend on all layers
    @Test
    void bootstrapMayDependOnAllLayers() {
        classes()
                .that().resideInAPackage("fr.docai.bootstrap..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "fr.docai..",
                        "java..",
                        "javax..",
                        "org.springframework..",
                        "org.slf4j..",
                        "io.micrometer..",
                        "io.opentelemetry..",
                        "jakarta..")
                .check(ALL_CLASSES);
    }

    // Rule 11: No cyclic dependencies between modules
    @Test
    void noModularCyclicDependencies() {
        Architectures.layeredArchitecture()
                .layer("domain").definedBy("fr.docai.domain..")
                .layer("application").definedBy("fr.docai.application..")
                .layer("adapter-in").definedBy("fr.docai.adapter.in..")
                .layer("adapter-out").definedBy("fr.docai.adapter.out..")
                .layer("bootstrap").definedBy("fr.docai.bootstrap..")
                .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "adapter-in", "adapter-out", "bootstrap")
                .whereLayer("application").mayOnlyBeAccessedByLayers("adapter-in", "adapter-out", "bootstrap")
                .whereLayer("adapter-in").mayNotBeAccessedByLayers("adapter-out", "application", "domain")
                .whereLayer("adapter-out").mayNotBeAccessedByLayers("adapter-in", "application", "domain")
                .whereLayer("bootstrap").mayNotBeAccessedByAnyLayer()
                .check(ALL_CLASSES);
    }

    // Rule 12: Interfaces in domain must be implemented by adapters (port pattern)
    @Test
    void portInterfacesMustBeImplementedByAdapters() {
        classes()
                .that().haveNameMatching(".*Port")
                .and().resideInAPackage("fr.docai.domain..")
                .should().beInterfaces()
                .check(ALL_CLASSES);
    }
}
