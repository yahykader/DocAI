---
name: docai-architecture-adr
description: "Référence d'architecture DocAI — Architecture Hexagonale (Ports & Adapters), 12 règles ArchUnit exactes, principes SOLID, catalogue Design Patterns, et les 11 ADR (décisions architecturales). Utiliser OBLIGATOIREMENT avant d'implémenter n'importe quel composant DocAI : domain model, use case, adapter, kafka event, test. Ces règles s'appliquent à TOUS les modules sans exception. Utiliser aussi quand on demande la structure Maven, les packages Java, les conventions de nommage, les règles hexagonales, ou quand un ADR est mentionné (ADR-001 à ADR-011)."
---

# DocAI — Architecture & ADR
## Référence transversale obligatoire · Lire avant tout autre skill

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 1 (Architecture) + Partie 7 (Annexes ADR)
> **S'applique à :** TOUS les modules, TOUS les adapters, TOUS les composants

---

## 1. Architecture Hexagonale — Structure Maven

```
docai-parent/                          ← POM parent (dependency management)
├── docai-domain/                      ← Java pur — AUCUNE dépendance framework
│   ├── model/                         Aggregates, Value Objects, Enums
│   ├── port/in/                       Interfaces Use Cases (Inbound Ports)
│   ├── port/out/                      Interfaces Repositories, Storage, Events
│   ├── event/                         Domain Events
│   ├── service/                       Domain Services (logique métier pure)
│   └── exception/                     Exceptions domaine typées
├── docai-application/                 ← Orchestre les use cases (dépend uniquement de domain)
│   ├── usecase/                       Implémentations des Inbound Ports
│   ├── command/                       Objets commande (CQRS write side)
│   └── query/                         Objets requête (CQRS read side)
├── docai-adapter-in-rest/             ← Spring MVC Controllers
├── docai-adapter-in-kafka/            ← Kafka Consumers
├── docai-adapter-out-mongodb/         ← Persistance MongoDB
├── docai-adapter-out-kafka/           ← Event Publisher (Outbox)
├── docai-adapter-out-valkey/          ← Cache Valkey/Redis
├── docai-adapter-out-ai/              ← OCR + LLM + Vision
├── docai-adapter-out-storage/         ← Amazon S3
├── docai-adapter-out-external/        ← APIs INSEE, BAN, RPPS
└── docai-bootstrap/                   ← Assemblage Spring Boot final
```

**Package racine :** `fr.docai`

**Règle absolue :** `docai-domain` ne contient AUCUN import `org.springframework`, `com.mongodb`, `org.apache.kafka`, `io.lettuce`, `software.amazon`, `com.stripe`.

---

## 2. Conventions de Nommage

| Préfixe | Type | Exemple |
|---------|------|---------|
| `BR-MOD-NNN` | Business Rule | `BR-REC-001`, `BR-FRD-005` |
| `UC-MOD-NNN` | Use Case | `UC-ONB-001` |
| `NFR-MOD-NNN` | Non-Functional Requirement | `NFR-EXT-001` |
| `PORT-IN-MOD-NNN` | Port entrant (interface use case) | `PORT-IN-REC-001` |
| `PORT-OUT-MOD-NNN` | Port sortant (interface adapter) | `PORT-OUT-REC-002` |
| `ADR-NNN` | Architecture Decision Record | `ADR-001` |

**Modules :** REC (Reconnaissance), EXT (Extraction), FRD (Fraude), PPL (Pipeline), DSH (Dashboard), INT (Intégrations), BIL (Billing), SEC (Sécurité), ONB (Onboarding), AUTH (Auth), PRF (Profil)

**Niveaux de priorité :** MUST (bloquant), SHOULD (important), COULD (optionnel)

---

## 3. Les 12 Règles ArchUnit

```java
@AnalyzeClasses(packages = "fr.docai")
public class HexagonalArchitectureTest {

    // Règle 1 — Domaine pur Java (pas de framework)
    @ArchTest
    static final ArchRule domain_must_be_framework_free =
        noClasses().that().resideInAPackage("fr.docai.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "com.mongodb..", "org.apache.kafka..",
                "io.lettuce..", "software.amazon..", "jakarta.persistence..", "com.stripe..");

    // Règle 2 — Adapters ne s'appellent pas entre eux
    @ArchTest
    static final ArchRule adapters_must_not_call_each_other =
        noClasses().that().resideInAPackage("fr.docai.adapter..")
            .should().dependOnClassesThat().resideInAPackage("fr.docai.adapter..");

    // Règle 3 — Application dépend uniquement du domaine
    @ArchTest
    static final ArchRule application_depends_only_on_domain =
        classes().that().resideInAPackage("fr.docai.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("fr.docai.domain..", "fr.docai.application..",
                                "java..", "javax..", "jakarta.validation..");

    // Règle 4 — Ports IN dans le domaine (interfaces UseCase)
    @ArchTest
    static final ArchRule inbound_ports_in_domain =
        classes().that().haveNameMatching(".*UseCase").and().areInterfaces()
            .should().resideInAPackage("fr.docai.domain.port.in..");

    // Règle 5 — Ports OUT dans le domaine
    @ArchTest
    static final ArchRule outbound_ports_in_domain =
        classes().that().haveNameMatching(".*Port").and().areInterfaces()
            .should().resideInAPackage("fr.docai.domain.port.out..");

    // Règle 6 — Adapters implémentent les ports du domaine
    @ArchTest
    static final ArchRule adapters_implement_domain_ports =
        classes().that().haveNameMatching(".*Adapter").and().areNotInterfaces()
            .should().resideInAPackage("fr.docai.adapter..");

    // Règle 7 — Pas d'accès direct à MongoDB depuis le domaine
    @ArchTest
    static final ArchRule no_direct_mongodb_in_domain =
        noClasses().that().resideInAPackage("fr.docai.domain..")
            .should().accessClassesThat().resideInAPackage("org.springframework.data.mongodb..");

    // Règle 8 — Pas d'accès direct à Kafka depuis le domaine
    @ArchTest
    static final ArchRule no_direct_kafka_in_domain =
        noClasses().that().resideInAPackage("fr.docai.domain..")
            .should().accessClassesThat().resideInAPackage("org.springframework.kafka..");

    // Règle 9 — Controllers dans adapter-in-rest uniquement
    @ArchTest
    static final ArchRule controllers_in_rest_adapter =
        classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("fr.docai.adapter.in.rest..");

    // Règle 10 — Listeners Kafka dans adapter-in-kafka uniquement
    @ArchTest
    static final ArchRule kafka_listeners_in_kafka_adapter =
        classes().that().areAnnotatedWith(KafkaListener.class)
            .should().resideInAPackage("fr.docai.adapter.in.kafka..");

    // Règle 11 — Documents MongoDB dans adapter-out-mongodb uniquement
    @ArchTest
    static final ArchRule mongo_documents_in_mongo_adapter =
        classes().that().areAnnotatedWith(Document.class)
            .should().resideInAPackage("fr.docai.adapter.out.mongodb..");

    // Règle 12 — Pas de @Transactional dans le domaine
    @ArchTest
    static final ArchRule no_transactional_in_domain =
        noClasses().that().resideInAPackage("fr.docai.domain..")
            .should().beAnnotatedWith(Transactional.class);
}
```

**Règles ArchUnit :**

| ID | Règle |
|----|-------|
| BR-ARCH-001 | `HexagonalArchitectureTest` s'exécute en CI Phase 1 — violation = build KO |
| BR-ARCH-002 | Les 12 règles sont toutes actives — aucune désactivation sans validation Tech Lead |
| BR-ARCH-003 | PIT Mutation Testing ≥ 85% sur `docai-domain` — seuil bloquant en CI |
| BR-ARCH-004 | Couverture JaCoCo ≥ 90% sur `docai-domain` |

---

## 4. Principes SOLID appliqués

| Principe | Application DocAI |
|----------|-------------------|
| **S** — Single Responsibility | Un Use Case = une responsabilité. Un Adapter = un système externe. |
| **O** — Open/Closed | Nouvelles règles de validation ajoutées par Strategy sans modifier la chaîne. |
| **L** — Liskov Substitution | `TesseractOcrAdapter` et fallback OCR interchangeables via `OcrPort`. |
| **I** — Interface Segregation | `OcrPort` ≠ `LlmPort` ≠ `StoragePort` — ports fins et ciblés. |
| **D** — Dependency Inversion | Use Cases dépendent des interfaces Ports, jamais des Adapters. |

---

## 5. Catalogue Design Patterns par Module

| Pattern | Module | Description |
|---------|--------|-------------|
| **Outbox Pattern** | Upload, tous publishers | Transaction atomique MongoDB + event → publication Kafka garantie |
| **Strategy** | Classification, Fraude, Extraction | Algorithme interchangeable par type de document |
| **Registry** | Fraude | `FraudAnalyzerRegistry` : auto-enregistrement des analyseurs Spring |
| **Composite** | Fraude | `CompositeFraudAnalyzer` agrège tous les analyseurs |
| **Null Object / Fail-Safe** | Fraude | Analyseur défaillant → liste vide, pipeline continue |
| **Cache-Aside** | Extraction, Validation | Valkey consulté avant appel LLM/API externe, écrit après succès |
| **Anti-Corruption Layer** | Validation externe | InseeApiAdapter, BanApiAdapter isolent les APIs externes du domaine |
| **Circuit Breaker** | LLM, OCR, APIs externes | Resilience4j — fallback si service indisponible |
| **Chain of Responsibility** | Validation | Chaîne ordonnée : Arithmétique → SIRET → IBAN → Adresse |
| **CQRS** | Dashboard | Read Model séparé, projection depuis events Kafka |
| **Saga** | Pipeline | Compensations sur chaque scénario d'échec |
| **Factory** | Classification | `DocumentFactory` crée l'aggregate selon type détecté |
| **Bulkhead** | LLM, OCR | Virtual Threads Java 21 isolés par type d'appel |

---

## 6. Les 11 ADR — Résumé obligatoire

> **Lire les détails complets dans `references/adr-details.md`**

| ADR | Priorité | Règle à retenir | Modules impactés |
|-----|----------|-----------------|-----------------|
| **ADR-001** | 🔴 Critique | Quota : script Lua ATOMIQUE Valkey (jamais `check` puis `increment` séparés) | Module 1, Module 7 |
| **ADR-002** | 🔴 Critique | Kafka : clé partition = `documentId` (jamais `tenantId` pour le pipeline) | TOUS les modules |
| **ADR-003** | 🔴 Critique | Cache : TTL avec jitter `±10%` si TTL > 1h (jamais TTL fixe) | Module 2, Module 3 |
| **ADR-004** | 🔴 Critique | OCR brut → S3 uniquement (jamais dans MongoDB — limite 4MB transaction) | Module 2 |
| **ADR-005** | 🟠 Important | PII chiffrés via AWS KMS, rotation automatique annuelle | Module 0.5 RGPD |
| **ADR-006** | 🟠 Important | JWKS Keycloak en cache local TTL 1h (sinon Keycloak down = tous bloqués) | Module 0 |
| **ADR-007** | 🟠 Important | Upload S3 : `AbortMultipartUpload` en cas d'erreur + Lifecycle Rule 24h via Terraform | Module 1 |
| **ADR-008** | 🟠 Important | CI : 3 jobs séparés + JVM `-Xmx512m` + TestContainers `reuse=true` | CI/CD |
| **ADR-009** | 🟡 Confort | Downgrade plan : données historiques conservées en lecture seule, quota reset mois suivant | Module 7 |
| **ADR-010** | 🟡 Confort | MongoDB : `EXPLAIN PLAN` avant chaque merge + partial index si actif < 20% | TOUS les modules |
| **ADR-011** | 🟡 Confort | Read Model : `lastSyncedAt` + job réconciliation toutes les 5 min | Module 5 |

---

## 7. Checklist avant chaque implémentation

Avant d'écrire la moindre ligne de code sur un composant DocAI, vérifier :

- [ ] Le composant est dans le bon module Maven (`docai-domain`, `docai-application`, `docai-adapter-*`)
- [ ] `docai-domain` : zéro import framework (Spring, MongoDB, Kafka, AWS)
- [ ] Les 12 règles ArchUnit ne sont pas violées
- [ ] Les ADR applicables sont respectés (voir tableau ci-dessus)
- [ ] Les conventions de nommage sont respectées (`BR-XXX-NNN`, `PORT-IN-XXX-NNN`)
- [ ] PIT Mutation ≥ 85% sur le domaine, JaCoCo ≥ 90%

> **Pour les détails complets de chaque ADR, lire `references/adr-details.md`**
