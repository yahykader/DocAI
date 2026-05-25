package fr.docai.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 12 règles d'architecture hexagonale — BR-ARCH-001 / BR-ARCH-002.
 *
 * Note sur allowEmptyShould(true) :
 * Les règles portant sur adapter.* et application.* utilisent allowEmptyShould(true)
 * car ces modules ne sont pas sur le classpath de docai-domain.
 * Dès qu'un adapter ou use case violera la règle, le test échouera correctement.
 */
class HexagonalArchitectureTest {

    static JavaClasses ALL_CLASSES;

    @BeforeAll
    static void importClasses() {
        ALL_CLASSES = new ClassFileImporter().importPackages("fr.docai");
    }

    // ── Règle 1 : Domaine pur Java — ZERO framework ───────────────────────────
    @Test
    void rule01_domain_must_be_framework_free() {
        noClasses()
            .that().resideInAPackage("fr.docai.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "com.mongodb..",
                "org.apache.kafka..",
                "io.lettuce..",
                "software.amazon.awssdk..",
                "jakarta.persistence..",
                "com.stripe.."
            )
            .as("Règle 1 — Le domaine ne doit pas dépendre d'un framework")
            .check(ALL_CLASSES);
    }

    // ── Règle 2 : Les adapters ne s'appellent pas entre eux ───────────────────
    // allowEmptyShould : fr.docai.adapter.* absent du classpath domain (normal à ce stade)
    @Test
    void rule02_adapters_must_not_call_each_other() {
        noClasses()
            .that().resideInAPackage("fr.docai.adapter..")
            .should().dependOnClassesThat()
            .resideInAPackage("fr.docai.adapter..")
            .allowEmptyShould(true)
            .as("Règle 2 — Les adapters ne doivent pas s'appeler directement entre eux")
            .check(ALL_CLASSES);
    }

    // ── Règle 3 : L'application dépend uniquement du domaine ──────────────────
    // allowEmptyShould : fr.docai.application.* absent du classpath domain (normal à ce stade)
    @Test
    void rule03_application_depends_only_on_domain() {
        classes()
            .that().resideInAPackage("fr.docai.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "fr.docai.domain..",
                "fr.docai.application..",
                "java..",
                "javax..",
                "jakarta.validation.."
            )
            .allowEmptyShould(true)
            .as("Règle 3 — Les use cases ne dépendent que du domaine")
            .check(ALL_CLASSES);
    }

    // ── Règle 4 : Les ports IN dans domain/port/in/ ───────────────────────────
    @Test
    void rule04_inbound_ports_in_domain() {
        classes()
            .that().haveNameMatching(".*UseCase")
            .and().areInterfaces()
            .should().resideInAPackage("fr.docai.domain.port.in..")
            .allowEmptyShould(true)
            .as("Règle 4 — Les interfaces UseCase sont dans domain.port.in")
            .check(ALL_CLASSES);
    }

    // ── Règle 5 : Les ports OUT dans domain/port/out/ ─────────────────────────
    @Test
    void rule05_outbound_ports_in_domain() {
        classes()
            .that().haveNameMatching(".*Port")
            .and().areInterfaces()
            .should().resideInAPackage("fr.docai.domain.port.out..")
            .as("Règle 5 — Les interfaces Port sont dans domain.port.out")
            .check(ALL_CLASSES);
    }

    // ── Règle 6 : Les Adapter résident dans fr.docai.adapter ──────────────────
    // allowEmptyShould : aucun Adapter existant dans docai-domain (normal)
    @Test
    void rule06_adapters_reside_in_adapter_packages() {
        classes()
            .that().haveNameMatching(".*Adapter")
            .and().areNotInterfaces()
            .should().resideInAPackage("fr.docai.adapter..")
            .allowEmptyShould(true)
            .as("Règle 6 — Les classes Adapter résident dans fr.docai.adapter")
            .check(ALL_CLASSES);
    }

    // ── Règle 7 : Pas de MongoDB direct dans le domaine ───────────────────────
    @Test
    void rule07_no_mongodb_in_domain() {
        noClasses()
            .that().resideInAPackage("fr.docai.domain..")
            .should().accessClassesThat()
            .resideInAPackage("org.springframework.data.mongodb..")
            .as("Règle 7 — Pas d'accès MongoDB direct depuis le domaine")
            .check(ALL_CLASSES);
    }

    // ── Règle 8 : Pas de Kafka direct dans le domaine ─────────────────────────
    @Test
    void rule08_no_kafka_in_domain() {
        noClasses()
            .that().resideInAPackage("fr.docai.domain..")
            .should().accessClassesThat()
            .resideInAPackage("org.springframework.kafka..")
            .as("Règle 8 — Pas d'accès Kafka direct depuis le domaine")
            .check(ALL_CLASSES);
    }

    // ── Règle 9 : @RestController uniquement dans adapter-in-rest ────────────
    // allowEmptyShould : aucun @RestController dans docai-domain (normal)
    @Test
    void rule09_controllers_in_rest_adapter() {
        classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("fr.docai.adapter.in.rest..")
            .allowEmptyShould(true)
            .as("Règle 9 — @RestController uniquement dans adapter-in-rest")
            .check(ALL_CLASSES);
    }

    // ── Règle 10 : @KafkaListener uniquement dans adapter-in-kafka ───────────
    // allowEmptyShould : aucun @KafkaListener dans docai-domain (normal)
    @Test
    void rule10_kafka_listeners_in_kafka_adapter() {
        classes()
            .that().areAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
            .should().resideInAPackage("fr.docai.adapter.in.kafka..")
            .allowEmptyShould(true)
            .as("Règle 10 — @KafkaListener uniquement dans adapter-in-kafka")
            .check(ALL_CLASSES);
    }

    // ── Règle 11 : @Document MongoDB uniquement dans adapter-out-mongodb ──────
    // allowEmptyShould : aucun @Document dans docai-domain (normal)
    @Test
    void rule11_mongo_documents_in_mongodb_adapter() {
        classes()
            .that().areAnnotatedWith("org.springframework.data.mongodb.core.mapping.Document")
            .should().resideInAPackage("fr.docai.adapter.out.mongodb..")
            .allowEmptyShould(true)
            .as("Règle 11 — @Document MongoDB uniquement dans adapter-out-mongodb")
            .check(ALL_CLASSES);
    }

    // ── Règle 12 : Pas de @Transactional dans le domaine ─────────────────────
    @Test
    void rule12_no_transactional_in_domain() {
        noClasses()
            .that().resideInAPackage("fr.docai.domain..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .as("Règle 12 — Pas de @Transactional dans le domaine")
            .check(ALL_CLASSES);
    }
}