# DocAI — Master Backend SpecKit
## Spécification Technique Complète · Production Ready · Backend

> **Stack :** Java 21 · Spring Boot 4.0.x · Kafka · Keycloak · Architecture Hexagonale · MongoDB · Valkey · Amazon S3  
> **Méthodologie :** DDD · Hexagonal · BDD · CQRS · Event-Driven · Craftsman Standards  
> **Version :** 15.0 — Mai 2026 — Organisé dans l ordre de développement · Production Ready · 20/20 · 100% Développable — Mai 2026 — Document de référence unique backend (SaaS complet + 11 ADR + 8 éléments manquants intégrés)  
> **Scope :** Spécification backend uniquement — le frontend fait l'objet d'un document dédié

---

## Sommaire

- [Description du Projet](#description-du-projet)
- [I — Architecture & Choix Techniques](#i--architecture--choix-techniques)
- [II — Fondations du Projet](#ii--fondations-du-projet)
  - [II.1 — Création du Projet (Setup)](#ii1--création-du-projet-setup)
  - [II.2 — CI/CD Pipeline](#ii2--cicd-pipeline)
- [III — Modules par Ordre de Création](#iii--modules-par-ordre-de-création)
  - [Module 0 — Sécurité & Multi-Tenancy (Fondation transversale)](#module-0--sécurité--multi-tenancy-fondation-transversale)
  - [Module 1 — Reconnaissance de Documents](#module-1--reconnaissance-de-documents)
  - [Module 2 — Extraction d'Informations](#module-2--extraction-dinformations)
  - [Module 3 — Détection de Fraude](#module-3--détection-de-fraude)
  - [Module 4 — Orchestration & Pipeline](#module-4--orchestration--pipeline)
  - [Module 5 — Dashboard & Reporting](#module-5--dashboard--reporting)
  - [Module 6 — Intégrations & API Publique](#module-6--intégrations--api-publique)
- [Annexes](#annexes)

---

---

## Ordre de Développement


> **Ce document est organisé dans l'ordre exact de développement.**
> Lire et implémenter dans l'ordre de haut en bas.

| Partie | Contenu | Durée | Prérequis |
|--------|---------|-------|-----------|
| **0 — Description & Architecture** | Comprendre le projet et les choix techniques | Lecture 2h | Aucun |
| **1 — Mise en place** | Créer le projet, CI/CD, standards | 1 semaine | Partie 0 lue |
| **2 — Commons** | 7 composants réutilisables | 2 semaines | Partie 1 validée |
| **3 — Fondations métier** | Sécurité, Login, RGPD | 4 semaines | Partie 2 terminée |
| **4 — Pipeline** | Modules 1 à 4 (traitement documentaire) | 14 semaines | Partie 3 validée |
| **5 — Produit** | Dashboard, API, Billing | 7 semaines | Partie 4 fonctionnelle |
| **Annexes** | ADR, Standards, Production Readiness | Référence permanente | — |


---

# PARTIE 0 — DESCRIPTION & VISION DU PROJET

> **Lire en premier.** Comprendre le problème résolu, les marchés cibles et les business values avant de toucher au code. Cette partie définit le POURQUOI du projet.

---

# Description du Projet

## Le problème résolu

Dans les entreprises, les documents (factures, contrats, CNI, ordonnances, bulletins de salaire) sont encore traités massivement à la main. Un comptable saisit manuellement les données d'une facture dans un ERP. Un gestionnaire vérifie visuellement si un RIB est cohérent. Un service fraude consulte chaque document suspect un par un. Ce traitement crée trois problèmes coûteux : erreurs humaines, délais de traitement et fraudes non détectées.

## La solution DocAI

DocAI est un **SaaS B2B** qui automatise la chaîne complète de traitement documentaire :

```
Document entrant (PDF, image)
        │
        ▼
  [Reconnaissance] → Identification automatique du type de document
        │
        ▼
  [Extraction]     → Parsing structuré des données clés (OCR + LLM)
        │
        ▼
  [Validation]     → Vérification règles métier + référentiels (INSEE, BAN, RPPS)
        │
        ▼
  [Fraude]         → Scoring de risque multi-signaux (métadonnées, données, visuel)
        │
        ▼
  [Livraison]      → JSON structuré → webhook / API / dashboard
```

## Marchés cibles

| Secteur | Cas d'usage principal |
|---------|----------------------|
| Comptabilité / Finance | Traitement automatique des factures fournisseurs |
| Assurance | Vérification des pièces justificatives (ordonnances, devis) |
| Banque / Fintech | KYC — vérification CNI, passeport, RIB |
| RH / Paie | Traitement des bulletins de salaire et contrats |
| Santé | Validation des ordonnances et comptes rendus médicaux |
| Immobilier | Dossiers locataires (justificatifs domicile, revenus) |

## Business Values — en chiffres

| KPI | Sans DocAI | Avec DocAI | Gain |
|-----|-----------|-----------|------|
| Temps traitement par document | 3–5 min | 15–30 sec | **×10** |
| Taux de détection fraude | ~40% | ≥ 85% | **+45 points** |
| Erreurs de saisie | 1–3% | < 0.5% | **-80%** |
| Documents perdus | Possible | Zéro (Outbox + Kafka) | **100% fiabilité** |
| Délai intégration client | Semaines | < 1 jour (API REST) | **×14** |
| Coût traitement 1 000 docs | ~€500 (humain) | ~€5–15 (LLM + infra) | **-97%** |

---

---

# I — Architecture & Choix Techniques

> **Lire cette section en entier avant d'écrire la première ligne de code.** Elle définit toutes les décisions techniques structurantes du projet.


## I.1 Architecture Hexagonale (Ports & Adapters)

### Pourquoi cette architecture ?

L'architecture hexagonale est choisie pour trois raisons fondamentales dans le contexte DocAI.

La première est le **swap de providers IA**. DocAI dépend de modèles LLM (OpenAI, Mistral) et de services OCR. Ces providers changent, évoluent et leurs prix varient. Avec l'hexagonale, l'adapter LLM peut être remplacé en une journée sans toucher au domaine métier.

La seconde est la **testabilité maximale**. Le domaine ne dépend d'aucune infrastructure. Les use cases sont testés avec des mocks purs, sans démarrer Spring, MongoDB ou Kafka. Les tests unitaires s'exécutent en < 1 seconde.

La troisième est la **conformité réglementaire**. Les règles métier (validation SIRET, scoring fraude, rotation des clés API) sont isolées dans le domaine, vérifiables par ArchUnit à chaque commit.

### Structure Maven Multi-Modules

```
docai-parent/                          ← POM parent (dependency management)
│
├── docai-domain/                      ← Java pur — AUCUNE dépendance framework
│   src/main/java/fr/docai/domain/
│   ├── model/                         Aggregates, Value Objects, Enums, Sealed Classes
│   ├── port/
│   │   ├── in/                        Interfaces Use Cases (Inbound Ports)
│   │   └── out/                       Interfaces Repositories, Storage, Events (Outbound Ports)
│   ├── event/                         Domain Events
│   ├── service/                       Domain Services (logique métier pure)
│   └── exception/                     Exceptions domaine typées
│
├── docai-application/                 ← Orchestre les use cases (dépend uniquement de domain)
│   ├── usecase/                       Implémentations des Inbound Ports
│   ├── command/                       Objets commande (CQRS write side)
│   └── query/                         Objets requête (CQRS read side)
│
├── docai-adapter-in-rest/             ← Adapter entrant REST (Spring MVC)
├── docai-adapter-in-kafka/            ← Adapter entrant Kafka Consumers
├── docai-adapter-out-mongodb/         ← Adapter sortant persistance
├── docai-adapter-out-kafka/           ← Adapter sortant Event Publisher
├── docai-adapter-out-valkey/          ← Adapter sortant Cache (Valkey/Redis)
├── docai-adapter-out-ai/              ← Adapter sortant OCR + LLM
├── docai-adapter-out-storage/         ← Adapter sortant Amazon S3
├── docai-adapter-out-external/        ← Adapter sortant APIs externes (INSEE, BAN, RPPS)
└── docai-bootstrap/                   ← Assemblage Spring Boot, config, main class
```

**Règle absolue vérifiée par ArchUnit en CI :** `docai-domain` ne contient aucune import `org.springframework`, `com.mongodb`, `org.apache.kafka` ou toute autre librairie infrastructure.


---

### **ArchUnit — Règles d'Architecture Exactes**

> **Où :** Créer `HexagonalArchitectureTest.java` dans `docai-domain/src/test/java/`.
> Ces tests s'exécutent en CI Phase 1 (Build) — une violation bloque immédiatement le pipeline.

**Comment implémenter `HexagonalArchitectureTest` :**

Ce test vérifie automatiquement que l'architecture hexagonale est respectée à chaque commit.

```java
@AnalyzeClasses(packages = "fr.docai")
public class HexagonalArchitectureTest {

    // ── Règle 1 : Le domaine est pur Java ─────────────────────────────
    // docai-domain ne doit importer AUCUN framework
    @ArchTest
    static final ArchRule domain_must_be_framework_free =
        noClasses()
            .that().resideInAPackage("fr.docai.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",     // Pas de Spring
                "com.mongodb..",             // Pas de MongoDB
                "org.apache.kafka..",        // Pas de Kafka
                "io.lettuce..",              // Pas de Valkey/Redis
                "software.amazon..",         // Pas de AWS SDK
                "jakarta.persistence..",     // Pas de JPA
                "com.stripe.."              // Pas de Stripe
            )
            .as("Le domaine ne doit pas dépendre d'un framework");

    // ── Règle 2 : Les adapters ne s'appellent pas entre eux ────────────
    @ArchTest
    static final ArchRule adapters_must_not_call_each_other =
        noClasses()
            .that().resideInAPackage("fr.docai.adapter..")
            .should().dependOnClassesThat()
            .resideInAPackage("fr.docai.adapter..")
            .as("Les adapters ne doivent pas s'appeler directement entre eux");

    // ── Règle 3 : Les use cases dépendent uniquement du domaine ────────
    @ArchTest
    static final ArchRule application_depends_only_on_domain =
        classes()
            .that().resideInAPackage("fr.docai.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "fr.docai.domain..",
                "fr.docai.application..",
                "java..",
                "javax..",
                "jakarta.validation.."        // Validation des commands uniquement
            )
            .as("Les use cases ne dépendent que du domaine");

    // ── Règle 4 : Les ports IN sont dans le domaine ────────────────────
    @ArchTest
    static final ArchRule inbound_ports_in_domain =
        classes()
            .that().haveNameMatching(".*UseCase")
            .and().areInterfaces()
            .should().resideInAPackage("fr.docai.domain.port.in..")
            .as("Les interfaces UseCase sont dans domain.port.in");

    // ── Règle 5 : Les ports OUT sont dans le domaine ───────────────────
    @ArchTest
    static final ArchRule outbound_ports_in_domain =
        classes()
            .that().haveNameMatching(".*Port")
            .and().areInterfaces()
            .should().resideInAPackage("fr.docai.domain.port.out..")
            .as("Les interfaces Port sont dans domain.port.out");

    // ── Règle 6 : Les aggregates sont dans le domaine ──────────────────
    @ArchTest
    static final ArchRule aggregates_in_domain =
        classes()
            .that().areAnnotatedWith(AggregateRoot.class)
            .should().resideInAPackage("fr.docai.domain.model..")
            .as("Les aggregates sont dans domain.model");

    // ── Règle 7 : Les controllers REST sont dans adapter.in.rest ───────
    @ArchTest
    static final ArchRule controllers_in_rest_adapter =
        classes()
            .that().haveNameMatching(".*Controller")
            .should().resideInAPackage("fr.docai.adapter.in.rest..")
            .as("Les controllers sont dans adapter.in.rest");

    // ── Règle 8 : Les consumers Kafka sont dans adapter.in.kafka ───────
    @ArchTest
    static final ArchRule consumers_in_kafka_adapter =
        classes()
            .that().haveNameMatching(".*KafkaConsumer")
            .should().resideInAPackage("fr.docai.adapter.in.kafka..")
            .as("Les consumers Kafka sont dans adapter.in.kafka");

    // ── Règle 9 : Les adapters MongoDB sont dans adapter.out.mongodb ────
    @ArchTest
    static final ArchRule mongo_adapters_in_mongodb_adapter =
        classes()
            .that().haveNameMatching(".*MongoAdapter")
            .should().resideInAPackage("fr.docai.adapter.out.mongodb..")
            .as("Les adapters MongoDB sont dans adapter.out.mongodb");

    // ── Règle 10 : Pas de logique métier dans les controllers ──────────
    @ArchTest
    static final ArchRule controllers_must_not_use_repositories =
        noClasses()
            .that().haveNameMatching(".*Controller")
            .should().dependOnClassesThat()
            .haveNameMatching(".*Repository|.*MongoAdapter|.*MongoTemplate")
            .as("Les controllers ne doivent pas accéder directement aux repositories");

    // ── Règle 11 : Les Domain Events sont immutables (records Java) ─────
    @ArchTest
    static final ArchRule domain_events_are_records =
        classes()
            .that().resideInAPackage("fr.docai.domain.event..")
            .should().be(record())
            .as("Les Domain Events sont des records Java (immutables)");

    // ── Règle 12 : Pas de @Autowired — uniquement injection constructeur ─
    @ArchTest
    static final ArchRule no_field_injection =
        noFields()
            .that().areDeclaredInClassesThat()
            .resideInAnyPackage("fr.docai.domain..", "fr.docai.application..")
            .should().beAnnotatedWith(Autowired.class)
            .as("Pas d'injection par champ — uniquement par constructeur");
}
```

**Structure des packages Java obligatoire :**

```
fr.docai/
├── domain/
│   ├── model/          ← Aggregates, Value Objects, Enums, Sealed Classes
│   ├── port/
│   │   ├── in/         ← Interfaces UseCase (Inbound Ports)
│   │   └── out/        ← Interfaces Repository, Storage, Events (Outbound Ports)
│   ├── event/          ← Domain Events (records Java)
│   ├── service/        ← Domain Services (logique métier pure)
│   └── exception/      ← Exceptions domaine typées
├── application/
│   ├── usecase/        ← Implémentations des Inbound Ports
│   ├── command/        ← Objets commande (CQRS write side)
│   └── query/          ← Objets requête (CQRS read side)
└── adapter/
    ├── in/
    │   ├── rest/        ← Controllers Spring MVC
    │   └── kafka/       ← Consumers Kafka
    └── out/
        ├── mongodb/     ← Adapters MongoDB
        ├── kafka/       ← Publishers Kafka (Outbox)
        ├── valkey/      ← Adapters cache Valkey
        ├── ai/          ← Adapters LLM + OCR
        ├── storage/     ← Adapter Amazon S3
        └── external/    ← Adapters APIs externes
```

**Configuration PIT Mutation Testing dans `pom.xml` du module domain :**

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.0</version>
    <configuration>
        <targetClasses>
            <param>fr.docai.domain.*</param>
        </targetClasses>
        <targetTests>
            <param>fr.docai.domain.*Test</param>
        </targetTests>
        <mutationThreshold>85</mutationThreshold>  <!-- Seuil 85% -->
        <coverageThreshold>90</coverageThreshold>  <!-- Couverture 90% -->
        <outputFormats>
            <outputFormat>HTML</outputFormat>
            <outputFormat>XML</outputFormat>
        </outputFormats>
    </configuration>
</plugin>
```

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-ARCH-001 | `HexagonalArchitectureTest` s'exécute en CI Phase 1 — violation = build KO | MUST |
| BR-ARCH-002 | Les 12 règles ArchUnit sont toutes actives — aucune désactivation sans validation Tech Lead | MUST |
| BR-ARCH-003 | Les packages Java respectent la structure définie ci-dessus | MUST |
| BR-ARCH-004 | PIT Mutation Testing ≥ 85% sur `docai-domain` — seuil bloquant en CI | MUST |
| BR-ARCH-005 | Toute nouvelle règle ArchUnit est discutée en équipe avant ajout | SHOULD |


## I.2 Principes SOLID appliqués

| Principe | Application concrète dans DocAI |
|----------|----------------------------------|
| **S** — Single Responsibility | Un Use Case = une responsabilité. Un Adapter = un système externe. |
| **O** — Open/Closed | Nouvelles règles de validation ajoutées par Strategy sans modifier la chaîne. |
| **L** — Liskov Substitution | `TesseractOcrAdapter` et fallback interchangeables via `OcrPort`. |
| **I** — Interface Segregation | `OcrPort` ≠ `LlmPort` ≠ `StoragePort` — ports fins et ciblés. |
| **D** — Dependency Inversion | Les Use Cases dépendent des interfaces Ports, jamais des implémentations Adapters. |

## I.3 Catalogue des Design Patterns

| Pattern | Modules concernés | Rôle |
|---------|-------------------|------|
| **Aggregate Root** | Reconnaissance, Fraude | Point d'entrée cohérent, garant des invariants domaine |
| **Value Object** | Tous | DocumentId, ConfidenceScore, FraudScore — immutables, auto-validants |
| **Domain Events** | Tous | Découplage inter-modules via Kafka |
| **CQRS** | Tous | Séparation commandes (write) / requêtes (read model) |
| **Outbox Pattern** | Orchestration | Atomicité publication Kafka + persistance MongoDB |
| **Strategy** | Extraction, Fraude | Algorithmes interchangeables sans modifier le code appelant |
| **Chain of Responsibility** | Extraction | Chaîne de validateurs ordonnés par sévérité |
| **Registry** | Fraude | Map typée `SignalType → FraudAnalyzerStrategy` |
| **State Machine** | Fraude, Orchestration | Transitions d'état contrôlées et auditées |
| **Anti-Corruption Layer** | Extraction, Fraude | Isole les APIs externes derrière des ports stables |
| **Idempotent Consumer** | Orchestration | Messages Kafka traités une seule fois (Valkey dedup) |
| **Saga (Choreography)** | Orchestration | Coordination sans orchestrateur central, compensation sur échec |
| **Cache-Aside** | Extraction, Dashboard | Valkey consulté avant traitement lourd, écrit après réponse |
| **Circuit Breaker** | Extraction, Fraude | Fail-fast Resilience4j sur appels LLM et APIs externes |
| **Bulkhead** | Extraction, Orchestration | Pools d'exécution isolés par type d'appel (Virtual Threads Java 21) |
| **Factory** | Reconnaissance | `DocumentFactory` crée l'aggregate selon type détecté |
| **Null Object / Fail-Safe** | Fraude | Analyse partielle si un analyseur est indisponible |
| **Test Data Builder** | Tests | Builders réutilisables pour les données de test |

## I.4 Stack Technique — Décisions détaillées

| Composant | Technologie | Version | Décision & Justification |
|-----------|-------------|---------|--------------------------|
| **Langage** | Java | 21 (LTS) | Virtual Threads (Loom), Records, Sealed Classes, Pattern Matching |
| **Framework** | Spring Boot | 4.0.x | Dernière version LTS stable (nov. 2025), native support Virtual Threads |
| **Sécurité** | Spring Security 6 + Keycloak | 26 | IAM externalisé, JWT, RBAC multi-tenant — pas de code auth custom |
| **Messagerie** | Apache Kafka | 3.7 (KRaft) | Pas de Zookeeper, mode KRaft moderne, replay natif des events |
| **Schema Registry** | Apicurio Registry | 2.6 | Apache 2.0 — remplace Confluent Community (licence restrictive) |
| **Cache** | Valkey | 8.x | Fork Linux Foundation de Redis BSD 3-Clause — pas de changement de licence |
| **Persistance** | MongoDB | 7.0 | Schéma flexible pour données documentaires (signaux fraude, extraction) |
| **Stockage fichiers** | Amazon S3 | SDK v2 | Natif cloud, scalabilité illimitée, SLA 99.999999999%, coût à l'usage |
| **Résilience** | Resilience4j | 2.x | CircuitBreaker, Retry, Bulkhead, RateLimiter — pas de dépendance Netflix |
| **Rate Limiting** | Bucket4j | 8.x + Valkey | Token bucket distribué, multi-instance |
| **OCR** | Tess4J | 5.x + PDFBox 3.x | Tess4J pour images, PDFBox pour PDF texte natif |
| **Métadonnées** | Apache Tika | 2.x | Détection falsifications, logiciels éditeurs (Photoshop, GIMP) |
| **LLM** | Spring AI + OpenAI/Mistral | — | Abstraction provider, swap sans changement de code métier |
| **Mapping** | MapStruct | 1.6 | Compile-time, zéro réflexion, pas de performance penalty |
| **Migrations DB** | Mongock | 5.x | Migrations MongoDB versionnées (équivalent Flyway pour Mongo) |
| **Métriques** | Micrometer + Prometheus | — | Standard Spring Boot, intégration Grafana dashboards |
| **Tracing** | OpenTelemetry + Grafana Tempo | — | Distributed tracing corrélé, `traceId` propagé dans les logs |
| **API Docs** | SpringDoc OpenAPI | 2.x | Génération auto Swagger UI, spec OpenAPI 3.1 |
| **Tests** | JUnit 5, TestContainers, ArchUnit, WireMock, Cucumber | — | Pyramide complète : unit → intégration → BDD → E2E |

### Pourquoi Amazon S3 et pas MinIO ?

MinIO est excellent pour le développement local mais son modèle de licence AGPL 3.0 crée des obligations contraignantes en production SaaS. Amazon S3 offre une API compatible (les adapters Spring ne changent pas), un SLA 99.999999999%, une durabilité native multi-AZ, et un coût à l'usage sans infrastructure à maintenir. En développement local, le SDK AWS S3 peut pointer vers un bucket de dev ou être mocké via WireMock/LocalStack dans les tests d'intégration.

## I.5 Topologie Kafka — Vue globale

| Topic | Producteur | Consommateur(s) | Rétention | Partitions |
|-------|-----------|-----------------|-----------|-----------|
| `docai.doc.uploaded` | document-service | classification-consumer | 7 jours | 6 |
| `docai.doc.classified` | classification-consumer | extraction-consumer | 7 jours | 6 |
| `docai.doc.extracted` | extraction-consumer | fraud-consumer, validation-consumer | 7 jours | 6 |
| `docai.doc.fraud.analyzed` | fraud-consumer | delivery-consumer | 7 jours | 6 |
| `docai.doc.completed` | delivery-consumer | notification-service | 30 jours | 3 |
| `docai.doc.failed` | tous services | alert-consumer | 30 jours | 3 |
| `docai.doc.dlq` | retry-service | monitoring-consumer | 90 jours | 3 |
| `docai.outbox.relay` | outbox-poller | kafka-relay-consumer | 1 jour | 3 |


---

### **Schémas Avro — Structure de chaque Event Kafka**

> **Où :** Ces schémas sont enregistrés dans Apicurio Registry au démarrage de l'application. Ils définissent le contrat entre les producteurs et consommateurs Kafka.

**Convention de nommage des schémas :**
- Namespace : `fr.docai.events`
- Nom : `{EventType}Event` (ex: `DocumentUploadedEvent`)
- Version : incrémentale (1, 2, 3...)

---

**DocumentUploadedEvent** (topic: `docai.doc.uploaded`)
```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "DocumentUploadedEvent",
  "fields": [
    {"name": "eventId",       "type": "string"},
    {"name": "documentId",    "type": "string"},
    {"name": "tenantId",      "type": "string"},
    {"name": "s3Key",         "type": "string"},
    {"name": "s3Bucket",      "type": "string"},
    {"name": "fileName",      "type": "string"},
    {"name": "mimeType",      "type": "string"},
    {"name": "sizeBytes",     "type": "long"},
    {"name": "contentHash",   "type": "string"},
    {"name": "uploadedBy",    "type": "string"},
    {"name": "occurredAt",    "type": "string"}
  ]
}
```

**DocumentClassifiedEvent** (topic: `docai.doc.classified`)
```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "DocumentClassifiedEvent",
  "fields": [
    {"name": "eventId",          "type": "string"},
    {"name": "documentId",       "type": "string"},
    {"name": "tenantId",         "type": "string"},
    {"name": "documentType",     "type": "string"},
    {"name": "confidenceScore",  "type": "double"},
    {"name": "modelVersion",     "type": "string"},
    {"name": "lowConfidence",    "type": "boolean"},
    {"name": "needsReview",      "type": "boolean"},
    {"name": "occurredAt",       "type": "string"}
  ]
}
```

**DocumentExtractedEvent** (topic: `docai.doc.extracted`)
```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "DocumentExtractedEvent",
  "fields": [
    {"name": "eventId",          "type": "string"},
    {"name": "documentId",       "type": "string"},
    {"name": "tenantId",         "type": "string"},
    {"name": "globalScore",      "type": "double"},
    {"name": "fieldsCount",      "type": "int"},
    {"name": "extractionMethod", "type": "string"},
    {"name": "rawOcrTextS3Key",  "type": ["null", "string"], "default": null},
    {"name": "occurredAt",       "type": "string"}
  ]
}
```

**FraudAnalyzedEvent** (topic: `docai.doc.fraud.analyzed`)
```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "FraudAnalyzedEvent",
  "fields": [
    {"name": "eventId",        "type": "string"},
    {"name": "documentId",     "type": "string"},
    {"name": "tenantId",       "type": "string"},
    {"name": "fraudScore",     "type": "int"},
    {"name": "riskLevel",      "type": "string"},
    {"name": "signalsCount",   "type": "int"},
    {"name": "approved",       "type": "boolean"},
    {"name": "needsReview",    "type": "boolean"},
    {"name": "occurredAt",     "type": "string"}
  ]
}
```

**DocumentCompletedEvent** (topic: `docai.doc.completed`)
```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "DocumentCompletedEvent",
  "fields": [
    {"name": "eventId",           "type": "string"},
    {"name": "documentId",        "type": "string"},
    {"name": "tenantId",          "type": "string"},
    {"name": "documentType",      "type": "string"},
    {"name": "fraudScore",        "type": "int"},
    {"name": "riskLevel",         "type": "string"},
    {"name": "extractionScore",   "type": "double"},
    {"name": "processingDurationMs", "type": "long"},
    {"name": "occurredAt",        "type": "string"}
  ]
}
```

**DocumentFailedEvent** (topic: `docai.doc.failed`)
```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "DocumentFailedEvent",
  "fields": [
    {"name": "eventId",       "type": "string"},
    {"name": "documentId",    "type": "string"},
    {"name": "tenantId",      "type": "string"},
    {"name": "failedStage",   "type": "string"},
    {"name": "errorCode",     "type": "string"},
    {"name": "errorMessage",  "type": "string"},
    {"name": "retryable",     "type": "boolean"},
    {"name": "occurredAt",    "type": "string"}
  ]
}
```

**Headers Kafka obligatoires sur chaque message :**

| Header | Valeur | Description |
|--------|--------|-------------|
| `tenant-id` | String | Tenant concerné (isolation) |
| `correlation-id` | UUID | Corrélation entre events d'un même document |
| `event-type` | String | Nom du type d'event |
| `trace-id` | String | OpenTelemetry traceId (distributed tracing) |
| `schema-version` | String | Version du schéma Avro (ex: "1") |


### **Kafka Consumer Group IDs — Convention de Nommage**

> **Où :** Ces group-ids sont déclarés dans chaque `@KafkaListener` des adapters consumers. Une convention cohérente est essentielle pour le monitoring du lag dans Kafka UI et Grafana.

**Convention de nommage :**
`docai.{module}.{consumer-name}.group`

**Tous les consumer group IDs :**

| Consumer | Class Java | Group ID | Topic consommé |
|----------|-----------|----------|----------------|
| Classification | `ClassificationKafkaConsumer` | `docai.recognition.classification.group` | `docai.doc.uploaded` |
| Extraction | `ExtractionKafkaConsumer` | `docai.extraction.extraction.group` | `docai.doc.classified` |
| Validation | `ValidationKafkaConsumer` | `docai.extraction.validation.group` | `docai.doc.extracted` |
| Fraude | `FraudKafkaConsumer` | `docai.fraud.analysis.group` | `docai.doc.extracted` |
| Pipeline completion | `CompletionKafkaConsumer` | `docai.pipeline.completion.group` | `docai.doc.fraud.analyzed` |
| Dashboard projection | `DashboardProjectionConsumer` | `docai.dashboard.projection.group` | Tous les topics |
| Webhook delivery | `WebhookDeliveryConsumer` | `docai.integration.webhook.group` | `docai.doc.completed` |
| Notification | `NotificationKafkaConsumer` | `docai.notification.alert.group` | `docai.doc.fraud.analyzed` |
| Outbox relay | `OutboxRelayConsumer` | `docai.outbox.relay.group` | `docai.outbox.relay` |
| DLQ monitor | `DlqMonitorConsumer` | `docai.pipeline.dlq.group` | `docai.doc.dlq` |

**Configuration dans `application.yml` — à ajouter par consumer :**

```yaml
# Exemple pour ClassificationKafkaConsumer
spring:
  kafka:
    consumer:
      group-id: docai.recognition.classification.group  # Défaut global
# Override par consumer dans @KafkaListener :
# @KafkaListener(topics = "docai.doc.uploaded",
#                groupId = "docai.recognition.classification.group")
```

**Monitoring du lag Kafka :**

Dans Grafana, les alertes de lag utilisent ces group IDs :
- `docai_kafka_consumer_lag{group="docai.recognition.classification.group"}` > 1000 → alerte
- `docai_kafka_consumer_lag{group="docai.extraction.extraction.group"}` > 500 → alerte

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-KGP-001 | Chaque consumer a un group-id unique suivant la convention `docai.{module}.{name}.group` | MUST |
| BR-KGP-002 | Le group-id est défini dans `application.yml` — jamais en dur dans le code Java | MUST |
| BR-KGP-003 | Chaque group-id a une alerte de lag configurée dans Grafana | MUST |


**Comment enregistrer un schéma dans Apicurio Registry :**
1. Créer le fichier `.avsc` dans `src/main/avro/`
2. Le plugin Maven `apicurio-registry-maven-plugin` enregistre automatiquement au build
3. Vérifier dans Apicurio UI (http://localhost:8081/ui) que le schéma est visible
4. Les consumers vérifient la compatibilité backward avant de démarrer


**Conventions Kafka :**

> ---
> ### ⚠️ ADR-002 — Clé de partition Kafka = documentId (RÈGLE ABSOLUE)
>
> **Pourquoi :** Avec `tenantId` comme clé, deux events du même document peuvent aller sur des partitions différentes et être traités dans le mauvais ordre → `ClassificationCorrected` traité avant `DocumentClassified` → état du document incohérent et irrécupérable.
>
> **Comment appliquer dans `OutboxKafkaProducer` :**
>
> Pour tous les topics du pipeline (`docai.doc.uploaded`, `docai.doc.classified`, `docai.doc.extracted`, `docai.doc.fraud.analyzed`, `docai.doc.completed`) :
> - Clé de partition = `documentId` (String UUID)
> - Kafka hash la clé → tous les events d'un même document vont sur la même partition → ordre FIFO garanti
>
> Exception autorisée :
> - Topics analytics et notifications → clé = `tenantId` (ordre par tenant, pas par document)
>
> **Comment vérifier :** Dans Kafka UI (`http://localhost:8090`), filtrer par `documentId` → tous les events de ce document doivent être sur la même partition, dans l'ordre chronologique.
>
> **Test obligatoire :** Publier `DocumentClassified` puis `ClassificationCorrected` pour le même document → vérifier que le consumer les traite dans cet ordre exact.
>
> **Référence complète :** Annexe E — ADR-002
> ---

- Clé de partition pipeline = `documentId` (garantit l'ordre des events par document — ADR-002)
- Clé de partition analytics/notifications = `tenantId` (ordre par tenant)
- Sérialisation : Avro + Apicurio Schema Registry
- Commit manuel (at-least-once delivery)
- Idempotence producer activée (`enable.idempotence=true`)
- Headers propagés : `tenant-id`, `correlation-id`, `event-type`, `traceId`

## I.6 Stratégies de Cache Valkey

| Données | Stratégie | TTL | Invalidation |
|---------|-----------|-----|-------------|
| Résultats extraction LLM | Cache-Aside | 24h ± 30min (jitter ADR-003) | Sur correction manuelle |
| Statuts documents | Write-Through | 5 min (pas de jitter) | Sur event Kafka |
| Profils tenant (quota, plan) | Cache-Aside | 15 min ± 2min (jitter ADR-003) | Sur modification TENANT_ADMIN |
| Validations SIRET / IBAN | Cache-Aside | 7 jours ± 6h (jitter ADR-003) | Manuelle (admin) |
| Clés d'idempotence | Write-Once | 24h (pas de jitter — précision requise) | Aucune (immutable) |
| Compteurs rate limiting | Write-Through | Sliding (pas de jitter) | Auto TTL |

## I.7 Résilience Transversale — Resilience4j

| Service externe | Circuit Breaker | Retry | Bulkhead | Timeout |
|----------------|-----------------|-------|----------|---------|
| LLM API | Seuil 50% / 10 calls | 3× backoff exponentiel | 20 threads max | 30s |
| OCR Engine | Seuil 50% / 5 calls | 2× backoff exponentiel | 10 threads max | 60s |
| API INSEE | Seuil 60% / 5 calls | 3× backoff exponentiel | 5 threads max | 5s |
| API BAN | Seuil 60% / 5 calls | 3× backoff exponentiel | 5 threads max | 5s |
| MongoDB | Seuil 40% / 10 calls | 2× backoff fixe | 50 threads max | 3s |

États Circuit Breaker : `CLOSED` → `OPEN` (fail-fast + fallback) → `HALF_OPEN` (test rétablissement)

## I.8 Observabilité Transversale

**Métriques Micrometer exposées Prometheus :**
- `docai_document_upload_total{tenant, type}` — compteur uploads
- `docai_document_processing_duration_seconds{module}` — histogram latences
- `docai_extraction_confidence_score{doc_type}` — histogram scores OCR/LLM
- `docai_fraud_score_distribution{risk_level}` — histogram distribution fraude
- `docai_circuit_breaker_state{service}` — gauge état circuit breakers
- `docai_cache_hit_ratio{region}` — gauge efficacité cache
- `docai_kafka_consumer_lag{topic, group}` — gauge lag consumers

**Distributed Tracing :** OpenTelemetry — `traceId` propagé dans logs JSON + headers Kafka + headers HTTP


### **Politique de Logs - Niveaux et Regles Obligatoires**

> **S applique a tous les modules.** Chaque log doit respecter ces regles - verifiees en code review.

**Comment choisir le bon niveau :**

| Niveau | Quand l utiliser | Exemples |
|--------|-----------------|---------|
| **ERROR** | Erreur necessite intervention humaine | Circuit Breaker OPEN, transaction echouee |
| **WARN** | Situation anormale mais recuperee | Retry reussi au 2eme essai, quota a 80% |
| **INFO** | Evenement metier important | Document soumis, classification terminee |
| **DEBUG** | Details techniques (desactive en prod) | Requete MongoDB, headers Kafka |

**Ce qui DOIT etre loggie (niveau INFO) :**
- Chaque soumission document (documentId, tenantId, mimeType, sizeBytes)
- Chaque changement de statut pipeline (documentId, fromStatus, toStatus, duration)
- Chaque decision fraude (documentId, score, riskLevel)
- Chaque action TENANT_ADMIN (invitation, revocation, changement plan)

**Ce qui NE DOIT JAMAIS etre loggie :**
- Mots de passe, tokens JWT, API Keys
- Numeros CNI, IBAN, RPPS, dates de naissance -> remplacer par [PII_MASKED]
- Contenu des fichiers PDF/images
- Cles AWS, secrets Stripe, credentials Keycloak

**Regles :**

| ID | Regle | Priorite |
|----|-------|---------|
| BR-LOG-001 | Toute donnee PII dans un log remplacee par [PII_MASKED] | MUST |
| BR-LOG-002 | Les secrets ne doivent jamais apparaitre dans les logs | MUST |
| BR-LOG-003 | Chaque log contient traceId et tenantId | MUST |
| BR-LOG-004 | Le niveau ERROR declenche une alerte Grafana | MUST |
| BR-LOG-005 | Le niveau DEBUG est desactive en staging et production | MUST |
| BR-LOG-006 | La revue de code verifie l absence de PII dans les nouveaux logs | MUST |

**Format log JSON obligatoire :**
```json
{
  "timestamp": "2026-05-14T10:00:00Z",
  "level": "INFO",
  "service": "docai-extraction",
  "traceId": "abc-123-xyz",
  "spanId": "def-456",
  "tenantId": "acme-corp",
  "userId": "usr-789",
  "message": "Extraction completed for document doc-001"
}
```

**Alertes Grafana :**
- Error rate > 1% sur 5 min → PagerDuty / Grafana OnCall
- P99 latence > 500ms → Slack
- Circuit Breaker OPEN → PagerDuty immédiat
- Kafka consumer lag > 1 000 messages → Slack
- Valkey cache hit ratio < 30% → Slack

## I.9 Persistance — Pourquoi MongoDB et pas SQL ?

MongoDB est choisi pour trois raisons structurelles liées au domaine DocAI.

**Schéma variable par type de document.** Une FACTURE a des champs `montantTTC`, `SIRET`, `lignes[]`. Une ORDONNANCE a des champs `médecin.RPPS`, `médicaments[].dosage`. En SQL, cela implique des dizaines de colonnes `NULL` ou des dizaines de tables de jointure. En MongoDB, chaque document porte exactement ce dont il a besoin.

**Signaux de fraude en nombre variable.** Un document peut avoir 0, 3 ou 12 signaux de fraude, chacun avec une structure `evidence` propre. Impossible à modéliser proprement en SQL sans des requêtes complexes. En MongoDB, `signals[]` est un tableau natif.

**Append-only pour l'audit.** Les `audit_entries` et `fraud_analyses` sont immuables après création. MongoDB TTL index gère l'archivage automatique. La règle est : MongoDB stocke les données métier durables, Valkey les données temporaires rapides, Kafka transporte les events, S3 stocke les fichiers binaires.


### **Regles de Migration Mongock — Nommage et Bonnes Pratiques**

> **Obligatoire avant la premiere migration.** Des migrations mal nommees peuvent bloquer un deploiement en production.

**Convention de nommage : V{numero}_{module}_{description}**

| Migration | Nom correct | Nom incorrect |
|-----------|-------------|---------------|
| Premiere migration | V001_setup_documents_collection | migration1 |
| Index documents | V002_documents_add_tenant_status_index | addIndex |
| Collection fraude | V003_fraud_analyses_create_collection | fraud |
| Ajout champ | V004_documents_add_processing_duration | addField |

**Regles absolues :**

| ID | Regle | Priorite |
|----|-------|---------|
| BR-MIG-001 | Chaque migration dans sa propre classe @ChangeUnit | MUST |
| BR-MIG-002 | Migrations backward-compatible — jamais supprimer un champ en une seule migration | MUST |
| BR-MIG-003 | auto-index-creation=false en production — uniquement via Mongock | MUST |
| BR-MIG-004 | Chaque migration a une methode rollback definie | MUST |
| BR-MIG-005 | Pas de logique metier dans une migration — uniquement DDL | MUST |
| BR-MIG-006 | Migrations testees en staging avant la production | MUST |
| BR-MIG-007 | Migration echouee en production = alerte immediate + blocage demarrage | MUST |

**Comment faire un changement backward-compatible (exemple ADR-004) :**
1. Migration V005 : ajouter rawOcrTextS3Key (garder rawOcrText)
2. Deploiement v1.1 : ecrire dans rawOcrTextS3Key, lire les deux
3. Migration V006 : migrer les donnees rawOcrText vers rawOcrTextS3Key
4. Deploiement v1.2 : lire uniquement rawOcrTextS3Key
5. Migration V007 : supprimer rawOcrText

**Procedure si migration echoue en production :**
1. Application refuse de demarrer (protection Mongock integree)
2. Alerte automatique Tech Lead
3. Consulter logs Mongock
4. Executer le rollback
5. Corriger et redeployer
6. Ne jamais modifier une migration deja executee


### Collections MongoDB



---

### **Stratégie de Pagination Globale — Standard Obligatoire**

> **Où :** Cette règle s'applique à TOUS les endpoints qui retournent une liste dans tous les modules. À définir maintenant pour éviter des formats incohérents entre modules.

**Comment paginer une réponse :**

Chaque endpoint retournant une liste utilise le format suivant via `commons-api` (`ApiResponse<T>`) :

**Paramètres de requête standards :**

| Paramètre | Type | Défaut | Maximum | Description |
|-----------|------|--------|---------|-------------|
| `page` | Integer | 0 | — | Numéro de page (commence à 0) |
| `size` | Integer | 20 | 100 | Nombre d'éléments par page |
| `sort` | String | `createdAt,desc` | — | Champ + direction (ex: `score,asc`) |

**Format de réponse paginée standard :**

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

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PAG-001 | Tous les endpoints liste utilisent les paramètres `page`, `size`, `sort` | MUST |
| BR-PAG-002 | La taille maximale est 100 éléments par page — au-delà HTTP 400 | MUST |
| BR-PAG-003 | La taille par défaut est 20 éléments par page | MUST |
| BR-PAG-004 | La réponse paginée contient toujours `totalElements` et `totalPages` | MUST |
| BR-PAG-005 | Si `size > 100` → HTTP 400 avec message "Maximum page size is 100" | MUST |
| BR-PAG-006 | Le tri par défaut est `createdAt,desc` sauf indication contraire | MUST |
| BR-PAG-007 | Les champs de tri autorisés sont documentés dans l'OpenAPI de chaque endpoint | MUST |
| BR-PAG-008 | La pagination est implémentée dans `commons-api` — ne jamais réimplémenter | MUST |

**Comment implémenter dans un nouveau endpoint liste :**

1. Ajouter les paramètres `@RequestParam(defaultValue="0") int page` et `@RequestParam(defaultValue="20") int size` dans le Controller
2. Valider que `size <= 100` — lever `ProblemDetail` HTTP 400 si dépassé
3. Passer `PageRequest.of(page, size, Sort.by(...))` au Use Case
4. Retourner `ApiResponse.paginated(data, pageMetadata)` depuis `commons-api`

**Exemples d'endpoints paginés dans DocAI :**

| Module | Endpoint | Tri par défaut | Filtres |
|--------|----------|---------------|---------|
| Module 1 | GET /v1/documents | createdAt,desc | status, type, dateFrom, dateTo |
| Module 3 | GET /v1/fraud/review-queue | score,desc | riskLevel, reviewer |
| Module 5 | GET /v1/dashboard/documents | createdAt,desc | status, type, riskLevel |
| Module 6 | GET /v1/api-keys | createdAt,desc | scope |
| Module 3 | GET /v1/notifications | createdAt,desc | read |

**Référence dans les modules :**
- Tous les modules avec des endpoints liste → référencer BR-PAG-001 à BR-PAG-008
- `commons-api` → `PageMetadata`, `ApiResponse.paginated()` à implémenter


### **Convention de Nommage des Collections MongoDB**

> **Obligatoire avant la premiere migration Mongock.** Impossible a renommer facilement apres le premier deploiement.

**Regle : snake_case pluriel**

| Correct | Incorrect |
|---------|-----------|
| documents | Document, DOCUMENTS |
| extraction_results | extractionResults |
| fraud_analyses | fraudAnalysis |
| audit_entries | auditEntry |
| outbox_events | outboxEvent |
| tenant_configs | tenantConfig |
| login_history | loginHistory |
| invitation_tokens | invitationToken |
| webhook_deliveries | webhookDelivery |
| api_keys | apiKey |
| notifications | Notification |
| subscriptions | Subscription |

**Regles pour les champs dans les documents :**
- Champs : camelCase (standard MongoDB)
- Identifiants : _id pour la cle primaire, documentId, tenantId pour les references
- Dates : toujours suffixees At (createdAt, updatedAt, processedAt, expiresAt)
- Booleens : prefixes is ou verbe (isRead, used, enabled)

| Collection | Rôle | Caractéristique clé |
|-----------|------|---------------------|
| `documents` | Aggregate racine du pipeline | Statut, metadata, S3 key |
| `extraction_results` | Résultats OCR + LLM | Schéma `fields` variable par type |
| `fraud_analyses` | Analyse fraude — **immuable** | Tableau `signals[]` variable |
| `audit_entries` | Journal immuable — append-only | TTL index 5 ans |
| `outbox_events` | Outbox Pattern — garantie Kafka | Statut PENDING/PUBLISHED/FAILED |
| `document_summary_views` | Read Model CQRS Dashboard | Agrégat dénormalisé pour lectures rapides |
| `webhook_deliveries` | Log de livraison webhooks | Tableau `attempts[]` |
| `api_keys` | Clés API clients | Hash SHA-256+sel, jamais en clair |
| `tenant_configs` | Configuration par tenant | Plan, quotas, webhooks configurés |

### Stratégie d'indexation MongoDB

Tous les index sont créés par **Mongock** (migrations versionnées). `auto-index-creation=false` en production.

> ---
> ### ⚠️ ADR-010 — Scalabilité des index MongoDB (OBLIGATOIRE ici)
>
> **Pourquoi :** Avec 1 million de documents par tenant, les index peuvent dépasser la RAM disponible → les requêtes passent en COLLSCAN → performances dégradées silencieusement de 100ms à 5s.
>
> **Règles concrètes à appliquer sur chaque index :**
>
> Avant chaque merge ajoutant une nouvelle requête MongoDB :
> 1. Exécuter `db.collection.explain("executionStats").find({...})` sur la requête
> 2. Vérifier que `winningPlan.stage` = `IXSCAN` (pas `COLLSCAN`)
> 3. Vérifier que `executionStats.totalDocsExamined` ≈ `executionStats.nReturned`
>
> Pour les collections dont le volume actif est < 20% du total (ex: documents COMPLETED représentent 90% du volume mais sont rarement consultés) :
> - Utiliser un **partial index** avec filtre sur les statuts actifs uniquement
> - Exemple : index sur `{tenantId, createdAt}` avec filtre `{status: {$in: ["PENDING","CLASSIFIED","EXTRACTED"]}}`
> - Résultat : index 10x plus petit, toujours en RAM
>
> Alerte Grafana à configurer :
> - Si `COLLSCAN` détecté dans les logs MongoDB → alerte immédiate Slack
> - Si taille index > 80% RAM disponible → alerte critique PagerDuty
>
> **Test obligatoire :** Insérer 100 000 documents de test → exécuter toutes les requêtes dashboard → vérifier IXSCAN sur 100% des requêtes.
>
> **Référence complète :** Annexe E — ADR-010
> ---

**Collection `documents` :**

| Index | Champs | Type | Usage |
|-------|--------|------|-------|
| idx_tenant_status_created | tenantId, status, createdAt | Compound | Liste filtrée par statut |
| idx_tenant_type_created | tenantId, type, createdAt | Compound | Liste filtrée par type |
| idx_idempotency | idempotencyKey | Unique | Détection doublons |

**Collection `fraud_analyses` :**

| Index | Champs | Type | Usage |
|-------|--------|------|-------|
| idx_document | documentId | Unique | Garantit l'immuabilité (1 analyse par doc) |
| idx_tenant_risk | tenantId, riskLevel, createdAt | Compound | Queue de révision |
| idx_tenant_score | tenantId, score | Compound | Dashboard distribution scores |

**Isolation multi-tenant :** `tenantId` est le **premier champ** de tous les index composites. Chaque requête MongoDB inclut automatiquement `{ tenantId: currentTenant }` injecté par l'adapter, pas par le use case.

---

---

## I.10 — Stratégie de Versioning de l API

> **Où :** Cette section s applique à tous les endpoints exposés dans le Module 6 et à toute évolution future de l API publique.

**Règle 1 — Quand rester en /v1/ :**
- Ajout d un nouveau endpoint (non cassant)
- Ajout d un champ optionnel dans une réponse JSON
- Ajout d un nouveau paramètre optionnel

**Règle 2 — Quand créer /v2/ :**
- Suppression ou renommage d un champ JSON
- Changement de type d un champ
- Modification du comportement d un endpoint existant
- Suppression d un endpoint

**Gestion de la coexistence /v1/ et /v2/ (minimum 6 mois) :**
- /v1/ maintenu et fonctionnel avec header Deprecation: true + Sunset: date suppression
- Email à tous les TENANT_ADMIN avec API Keys actives
- Documentation OpenAPI mise à jour avec deprecated: true

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-VER-001 | Tout endpoint public versionné sous /v1/ minimum | MUST |
| BR-VER-002 | Changement cassant = nouvelle version /v2/ obligatoire | MUST |
| BR-VER-003 | Ancienne version maintenue minimum 6 mois après annonce | MUST |
| BR-VER-004 | Tenants avec API Keys notifiés par email à chaque dépréciation | MUST |
| BR-VER-005 | Headers Deprecation et Sunset sur les endpoints dépréciés | MUST |


# PARTIE 1 — MISE EN PLACE (avant tout code)

> **Ordre obligatoire :**
> 1. **0.A — Créer le projet** (structure Maven, Docker, infrastructure locale)
> 2. **0.B — Configurer les standards** (Clean Code, CI/CD, Feature Flags, environnements)
> 3. **0.C — Valider le pipeline CI/CD** (premier build vert end-to-end)
>
> **Aucun module métier ne doit être démarré avant que ces 3 étapes soient validées.**

---

## 0.A — Création du Projet (Setup)

> **Première étape absolue.** Sans infrastructure locale fonctionnelle, impossible de démarrer.


### Prérequis système

| Prérequis | Version minimale | Vérification |
|-----------|-----------------|-------------|
| Java | 21 LTS | `java -version` → `21.x.x` |
| Maven | 3.9+ | `mvn -version` |
| Docker Engine | 24+ | `docker -v` |
| Docker Compose | v2+ | `docker compose version` |
| Git | — | `git --version` |
| RAM disponible | 8 GB | Pour tous les services en parallèle |
| Ports libres | Voir tableau ci-dessous | — |

**Ports requis :**

| Port | Service |
|------|---------|
| 6379 | Valkey (cache) |
| 8080 | Application Spring Boot |
| 8081 | Apicurio Registry |
| 8090 | Kafka UI |
| 8180 | Keycloak |
| 9090 | Prometheus |
| 9092, 9094 | Kafka (broker, controller) |
| 27017 | MongoDB |
| 3000 | Grafana |
| 3200 | Grafana Tempo |
| 4317 | OpenTelemetry Collector |

### Structure du projet — arborescence complète

```
docai-parent/
├── pom.xml                                   ← POM parent avec tout le dependency management
├── .env.example                              ← Template variables d'environnement (committer)
├── .env                                      ← Variables réelles (dans .gitignore — JAMAIS committer)
├── docker-compose.yml                        ← Infrastructure locale de développement
├── sonar-project.properties                  ← Configuration SonarCloud
├── Dockerfile                                ← Build multi-stage JRE 21 Alpine
├── .github/
│   ├── workflows/
│   │   ├── 01-ci.yml                        ← Build + Tests + SonarCloud
│   │   ├── 02-docker.yml                    ← Build Docker + Trivy + Push GHCR
│   │   ├── 03-deploy-staging.yml            ← Déploiement staging automatique
│   │   ├── 04-deploy-production.yml         ← Déploiement production avec approbation
│   │   └── 05-documentation.yml            ← Génération + publication docs
│   ├── pull_request_template.md             ← Checklist code review (voir Section III)
│   ├── CODEOWNERS                           ← Ownership par module
│   └── dependabot.yml                       ← Mises à jour auto dépendances
│
├── k8s/
│   ├── base/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── hpa.yaml
│   │   ├── configmap.yaml
│   │   └── ingress.yaml
│   ├── staging/
│   │   ├── kustomization.yaml               ← 1 replica, namespace docai-staging
│   │   └── configmap-patch.yaml
│   └── production/
│       ├── kustomization.yaml               ← 3 replicas, namespace docai-production
│       └── configmap-patch.yaml
│
├── docker/
│   └── keycloak/
│       └── realm-docai.json                 ← Realm Keycloak versionné
│
├── docai-domain/
├── docai-application/
├── docai-adapter-in-rest/
├── docai-adapter-in-kafka/
├── docai-adapter-out-mongodb/
├── docai-adapter-out-kafka/
├── docai-adapter-out-valkey/
├── docai-adapter-out-ai/
├── docai-adapter-out-storage/
├── docai-adapter-out-external/
└── docai-bootstrap/
    └── src/
        ├── main/resources/
        │   ├── application.yml              ← Config Spring Boot base
        │   ├── application-dev.yml          ← Profil développement
        │   └── application-prod.yml         ← Profil production (secrets via env vars)
        └── test/resources/features/         ← Fichiers .feature Cucumber
```

### docker-compose.yml — Infrastructure locale

```yaml
# DocAI — Infrastructure de développement locale
# Amazon S3 est utilisé directement (pas de MinIO)
# Configurez vos credentials AWS dans .env

version: "3.9"

networks:
  docai-net:
    driver: bridge

volumes:
  mongodb-data:
  kafka-data:
  valkey-data:
  prometheus-data:
  grafana-data:
  tempo-data:

services:

  # ─────────────────────────────────────────────
  # MongoDB 7 — Replica Set (requis pour transactions)
  # ─────────────────────────────────────────────
  mongodb:
    image: mongo:7.0
    container_name: docai-mongodb
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGODB_ROOT_USERNAME:-docai_root}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGODB_ROOT_PASSWORD:-docai_secret_local}
      MONGO_INITDB_DATABASE: docai
    command: ["--replSet", "rs0", "--bind_ip_all", "--keyFile", "/etc/mongo-keyfile"]
    ports:
      - "27017:27017"
    volumes:
      - mongodb-data:/data/db
      - ./docker/mongodb/mongo-keyfile:/etc/mongo-keyfile:ro
      - ./docker/mongodb/init-replica.js:/docker-entrypoint-initdb.d/init-replica.js:ro
    networks:
      - docai-net
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping').ok"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  # ─────────────────────────────────────────────
  # Apache Kafka 3.7 — Mode KRaft (sans Zookeeper)
  # ─────────────────────────────────────────────
  kafka:
    image: apache/kafka:3.7.0
    container_name: docai-kafka
    restart: unless-stopped
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka:9094"
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_LOG_DIRS: /var/lib/kafka/data
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_DEFAULT_REPLICATION_FACTOR: 1
      KAFKA_MIN_INSYNC_REPLICAS: 1
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_LOG_RETENTION_HOURS: 168
      CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
    ports:
      - "9092:9092"
      - "9094:9094"
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - docai-net
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s

  # ─────────────────────────────────────────────
  # Kafka UI — Interface de monitoring
  # Accessible : http://localhost:8090
  # ─────────────────────────────────────────────
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: docai-kafka-ui
    restart: unless-stopped
    depends_on:
      kafka:
        condition: service_healthy
      schema-registry:
        condition: service_healthy
    environment:
      KAFKA_CLUSTERS_0_NAME: docai-local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      KAFKA_CLUSTERS_0_SCHEMAREGISTRY: http://schema-registry:8081
    ports:
      - "8090:8080"
    networks:
      - docai-net

  # ─────────────────────────────────────────────
  # Apicurio Schema Registry — Avro Schemas
  # Accessible : http://localhost:8081/ui
  # ─────────────────────────────────────────────
  schema-registry:
    image: apicurio/apicurio-registry-mem:2.6.2.Final
    container_name: docai-schema-registry
    restart: unless-stopped
    ports:
      - "8081:8080"
    networks:
      - docai-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 10s
      timeout: 5s
      retries: 10

  # ─────────────────────────────────────────────
  # Valkey 8 — Cache (fork BSD de Redis)
  # ─────────────────────────────────────────────
  valkey:
    image: valkey/valkey:8-alpine
    container_name: docai-valkey
    restart: unless-stopped
    command: ["valkey-server", "--maxmemory", "512mb", "--maxmemory-policy", "allkeys-lru"]
    ports:
      - "6379:6379"
    volumes:
      - valkey-data:/data
    networks:
      - docai-net
    healthcheck:
      test: ["CMD", "valkey-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  # ─────────────────────────────────────────────
  # Keycloak 26 — IAM & SSO
  # Admin : http://localhost:8180 (admin / admin123)
  # ─────────────────────────────────────────────
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    container_name: docai-keycloak
    restart: unless-stopped
    command: ["start-dev", "--import-realm"]
    environment:
      KC_DB: dev-mem
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin123
      KC_HOSTNAME: localhost
      KC_HTTP_PORT: 8180
    ports:
      - "8180:8180"
    volumes:
      - ./docker/keycloak/realm-docai.json:/opt/keycloak/data/import/realm-docai.json:ro
    networks:
      - docai-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8180/health/ready"]
      interval: 15s
      timeout: 10s
      retries: 15
      start_period: 60s

  # ─────────────────────────────────────────────
  # Kafka Topics Init — Création des topics au démarrage
  # ─────────────────────────────────────────────
  kafka-init:
    image: apache/kafka:3.7.0
    container_name: docai-kafka-init
    depends_on:
      kafka:
        condition: service_healthy
    command: >
      bash -c "
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.uploaded --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.classified --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.extracted --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.fraud.analyzed --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.completed --partitions 3 --replication-factor 1 --config retention.ms=2592000000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.failed --partitions 3 --replication-factor 1 --config retention.ms=2592000000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.dlq --partitions 3 --replication-factor 1 --config retention.ms=7776000000 &&
        kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.outbox.relay --partitions 3 --replication-factor 1 --config retention.ms=86400000 &&
        echo '✅ Tous les topics Kafka créés.'
      "
    networks:
      - docai-net
    restart: "no"

  # ─────────────────────────────────────────────
  # Prometheus — Métriques
  # Accessible : http://localhost:9090
  # ─────────────────────────────────────────────
  prometheus:
    image: prom/prometheus:latest
    container_name: docai-prometheus
    restart: unless-stopped
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    ports:
      - "9090:9090"
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.retention.time=30d"
    networks:
      - docai-net
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:9090/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ─────────────────────────────────────────────
  # Grafana — Dashboards
  # Accessible : http://localhost:3000 (admin / admin123)
  # ─────────────────────────────────────────────
  grafana:
    image: grafana/grafana:latest
    container_name: docai-grafana
    restart: unless-stopped
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin123
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./docker/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./docker/grafana/dashboards:/var/lib/grafana/dashboards:ro
    ports:
      - "3000:3000"
    networks:
      - docai-net

  # ─────────────────────────────────────────────
  # Grafana Tempo — Distributed Tracing
  # ─────────────────────────────────────────────
  tempo:
    image: grafana/tempo:latest
    container_name: docai-tempo
    restart: unless-stopped
    command: ["-config.file=/etc/tempo.yaml"]
    volumes:
      - ./docker/tempo/tempo.yaml:/etc/tempo.yaml:ro
      - tempo-data:/tmp/tempo
    ports:
      - "3200:3200"
      - "4317:4317"
    networks:
      - docai-net

  # ─────────────────────────────────────────────
  # NOTE : Amazon S3
  # ─────────────────────────────────────────────
  # Aucun conteneur S3 en local — on utilise Amazon S3 directement.
  # Configurez dans .env :
  #   AWS_ACCESS_KEY_ID=...
  #   AWS_SECRET_ACCESS_KEY=...
  #   AWS_REGION=eu-west-3
  #   S3_BUCKET_NAME=docai-documents-dev
  #
  # Pour les tests d'intégration, utiliser TestContainers LocalStack :
  #   @Container static LocalStackContainer localstack =
  #       new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
  #           .withServices(S3);
```

### .env.example — Variables d'environnement

```bash
# ─── MongoDB ───────────────────────────────────────
MONGODB_ROOT_USERNAME=docai_root
MONGODB_ROOT_PASSWORD=CHANGE_ME_BEFORE_PRODUCTION
MONGODB_URI=mongodb://docai_root:CHANGE_ME@localhost:27017/docai?authSource=admin&replicaSet=rs0

# ─── Kafka ─────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SCHEMA_REGISTRY_URL=http://localhost:8081

# ─── Valkey (Cache) ────────────────────────────────
VALKEY_HOST=localhost
VALKEY_PORT=6379
VALKEY_PASSWORD=

# ─── Keycloak ──────────────────────────────────────
KEYCLOAK_URL=http://localhost:8180
KEYCLOAK_REALM=docai
KEYCLOAK_CLIENT_ID=docai-backend
KEYCLOAK_CLIENT_SECRET=CHANGE_ME_BEFORE_PRODUCTION

# ─── Amazon S3 ─────────────────────────────────────
AWS_ACCESS_KEY_ID=CHANGE_ME
AWS_SECRET_ACCESS_KEY=CHANGE_ME
AWS_REGION=eu-west-3
S3_BUCKET_NAME=docai-documents-dev

# ─── LLM API ───────────────────────────────────────
OPENAI_API_KEY=CHANGE_ME
MISTRAL_API_KEY=CHANGE_ME

# ─── APIs Externes ─────────────────────────────────
INSEE_CLIENT_ID=CHANGE_ME
INSEE_CLIENT_SECRET=CHANGE_ME

# ─── Application ───────────────────────────────────
SPRING_PROFILES_ACTIVE=dev
APP_BASE_URL=http://localhost:8080
```

**Règle impérative :** `.env` dans `.gitignore`. Vérifier avant chaque commit que les secrets ne sont pas dans Git.

### Commandes de démarrage

```bash
# 1. Cloner le projet
git clone https://github.com/votre-org/docai.git && cd docai

# 2. Configurer les variables d'environnement
cp .env.example .env
# Éditer .env et renseigner toutes les valeurs sensibles

# 3. Démarrer toute l'infrastructure locale
docker compose up -d

# 4. Vérifier l'état de tous les services
docker compose ps

# 5. Construire le projet (sans tests)
./mvnw clean install -DskipTests

# 6. Démarrer l'application en mode développement
./mvnw spring-boot:run -pl docai-bootstrap -Dspring-boot.run.profiles=dev

# ─── Commandes utiles ──────────────────────────────
# Tests unitaires seulement (rapides, pas de Docker)
./mvnw test -pl docai-domain,docai-application

# Tests intégration (TestContainers requis)
./mvnw verify -pl docai-adapter-out-mongodb

# Tests BDD Cucumber
./mvnw test -pl docai-bootstrap -Dtest=CucumberTestRunner

# Vérification architecture hexagonale
./mvnw test -pl docai-domain -Dtest=HexagonalArchitectureTest

# Rapport de couverture
./mvnw jacoco:report

# Mutation testing (domaine uniquement)
./mvnw org.pitest:pitest-maven:mutationCoverage -pl docai-domain

# Logs d'un service
docker compose logs -f kafka
docker compose logs -f keycloak
docker compose logs -f mongodb
```

### Vérification de l'installation

| Service | URL / Commande | Résultat attendu |
|---------|----------------|-----------------|
### Vérification de l installation

| Kafka UI | http://localhost:8090 | 8 topics DocAI visibles |
| Keycloak | http://localhost:8180/health/ready | `{"status":"UP"}` |
| Keycloak Admin | http://localhost:8180 | Realm `docai` visible |
| Apicurio Registry | http://localhost:8081/health/ready | HTTP 200 |
| Prometheus | http://localhost:9090/-/healthy | HTTP 200 |
| Grafana | http://localhost:3000 | Dashboard accessible |
| Valkey | `docker exec docai-valkey valkey-cli ping` | `PONG` |
| MongoDB RS | `docker exec -it docai-mongodb mongosh --eval "rs.status().ok"` | `1` |
| App | `GET /actuator/health` | `{"status":"UP"}` |
| Swagger UI | http://localhost:8080/swagger-ui.html | Interface OpenAPI |


---

### **Gestion des Environnements — DEV / STAGING / PRODUCTION**

> **Où :** Cette section est la référence unique pour toutes les différences de configuration entre environnements. Tout développeur qui configure un service doit s'y référer.

**Comment utiliser cette section :**
Avant de configurer un service (Keycloak, S3, Stripe, LLM), vérifier dans ce tableau quel mode utiliser selon l'environnement cible.

| Service | DEV (local) | STAGING | PRODUCTION |
|---------|------------|---------|-----------|
| **Keycloak** | Docker local (`localhost:8180`) | Instance cloud (1 replica) | Cluster 2 instances (ADR-006) |
| **MongoDB** | Docker local (1 node) | Atlas M10 (replica set 3 nodes) | Atlas M30 (replica set 3 nodes) |
| **Kafka** | Docker local (1 broker, KRaft) | Cloud (3 brokers, RF=3) | Cloud (3 brokers, RF=3) |
| **Amazon S3** | Bucket `docai-documents-dev` (région eu-west-3) | Bucket `docai-documents-staging` | Bucket `docai-documents-prod` (SSE-KMS activé) |
| **Valkey** | Docker local | Cloud managé | Cloud managé (cluster mode) |
| **Stripe** | Mode TEST (clé `sk_test_...`) | Mode TEST (clé `sk_test_...`) | Mode LIVE (clé `sk_live_...`) |
| **LLM (OpenAI)** | Mock WireMock (pas de vraie clé) | Vraie clé (quota limité) | Vraie clé (quota production) |
| **API INSEE** | WireMock stub | Vraie clé (sandbox) | Vraie clé (production) |
| **Emails (SES)** | Mode sandbox SES (emails bloqués) | Mode sandbox SES | Mode production SES |
| **BILLING_ENABLED** | `false` (gratuit) | `false` (gratuit) | `true` (facturation active) |
| **Logs** | JSON + console | JSON → CloudWatch | JSON → CloudWatch + alerte |
| **Tracing** | Tempo local | Tempo cloud | Grafana Cloud Tempo |
| **Secrets** | `.env` local | AWS Secrets Manager | AWS Secrets Manager |

**Règles absolues par environnement :**

**DEV :**
- Jamais de vraie clé LLM en dev — utiliser WireMock pour simuler les réponses
- Le fichier `.env` ne doit jamais être commité (`.gitignore` vérifié en CI)
- `BILLING_ENABLED=false` obligatoire — aucun appel Stripe en dev

**STAGING :**
- Données de staging jamais copiées depuis la production (RGPD)
- Stripe en mode TEST uniquement — utiliser les cartes de test Stripe
- Tests de charge k6 exécutés en staging avant chaque release production

**PRODUCTION :**
- Tous les secrets via AWS Secrets Manager — aucune variable d'environnement en dur
- `BILLING_ENABLED=true` — vérifier avant chaque déploiement
- Backup MongoDB automatique quotidien activé
- Chiffrement S3 SSE-KMS activé et vérifié (ADR-005)

---

---


---

### **Données de Test — Seeding DEV & STAGING**

> **Où :** À créer en Section 0.A juste après la vérification de l'installation. Sans données de test, chaque développeur crée les siennes manuellement et perd du temps.

**Pourquoi le seeding :**
Sans données de test préconfigurées, chaque développeur passe 30 minutes à créer un tenant, des utilisateurs, uploader des documents avant de pouvoir tester quoi que ce soit. Le seeding automatise ce bootstrapping.

**Comment organiser le seeding dans le projet :**

```
src/test/resources/seed/
├── dev/
│   ├── tenants.json          ← 3 tenants préconfigurés
│   ├── users.json            ← 10 utilisateurs (1 par rôle par tenant)
│   ├── documents/            ← Documents PDF exemples par type
│   │   ├── facture-exemple.pdf
│   │   ├── cni-exemple.pdf
│   │   └── rib-exemple.pdf
│   └── seed-config.yml       ← Configuration du seeding
└── staging/
    ├── tenants.json          ← 2 tenants de test performance
    └── users.json            ← Utilisateurs pour les tests k6
```

**Tenants préconfigurés pour le développement :**

| Tenant | tenant_id | Plan | Quota | Usage |
|--------|-----------|------|-------|-------|
| ACME Corp (test principal) | `acme-corp` | Pro | 5000 docs | Tests fonctionnels |
| Beta Assurances (test isolation) | `beta-assur` | Starter | 500 docs | Tests isolation tenant |
| Gamma RH (test quota dépassé) | `gamma-rh` | Starter | 500 docs (490 consommés) | Tests quota |

**Utilisateurs préconfigurés par tenant ACME Corp :**

| Email | Rôle | Mot de passe | Usage |
|-------|------|-------------|-------|
| admin@acme-corp.test | TENANT_ADMIN | Test1234! | Gestion tenant |
| analyst@acme-corp.test | ANALYST | Test1234! | Upload documents |
| viewer@acme-corp.test | VIEWER | Test1234! | Lecture seule |
| reviewer@acme-corp.test | FRAUD_REVIEWER | Test1234! | Queue révision |

**Comment exécuter le seeding :**

```bash
# Seeding DEV (à lancer une fois après docker compose up)
./mvnw spring-boot:run -pl docai-bootstrap   -Dspring-boot.run.profiles=dev,seed   -Dspring-boot.run.arguments="--seed.enabled=true --seed.env=dev"

# Vérification seeding réussi
curl http://localhost:8080/v1/public/auth/login   -d '{"email":"admin@acme-corp.test","password":"Test1234!"}'
# → Doit retourner un JWT valide

# Reset complet (supprimer toutes les données de test + re-seeder)
./mvnw spring-boot:run -pl docai-bootstrap   -Dspring-boot.run.profiles=dev,seed   -Dspring-boot.run.arguments="--seed.enabled=true --seed.reset=true"
```

**Comment implémenter le seeder :**

1. Créer `SeedingService` dans un module Spring Boot `docai-seed` (profil `seed` uniquement)
2. Le service lit les fichiers JSON depuis `src/test/resources/seed/{env}/`
3. Appelle l'API Keycloak Admin pour créer les utilisateurs et rôles
4. Insère les tenants dans MongoDB via `TenantMongoAdapter`
5. Upload les documents PDF exemples vers S3 via `AwsS3StorageAdapter`
6. **Jamais exécutable en production** : vérification `if (env == "production") throw`

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-SEED-001 | Le seeding est désactivé en production par défaut (profil seed inexistant en prod) | MUST |
| BR-SEED-002 | Les mots de passe de test ne doivent jamais être utilisés en production | MUST |
| BR-SEED-003 | Le seeding est idempotent : exécuté 2× = résultat identique | MUST |
| BR-SEED-004 | Le seeding DEV inclut des documents de chaque type supporté | MUST |
| BR-SEED-005 | Les tenants de test ont des tenant_id suffixés `.test` pour les distinguer | MUST |



---

### **Configuration Keycloak — realm-docai.json**

> **Où :** Fichier à créer dans `docker/keycloak/realm-docai.json`. Importé automatiquement au démarrage du conteneur Keycloak local.

**Structure du realm DocAI :**

Le fichier `realm-docai.json` configure automatiquement :

**1. Realm settings :**
- Nom du realm : `docai`
- Token access expiration : 900 secondes (15 minutes)
- Refresh token expiration : 28800 secondes (8 heures)
- Brute force protection : activé (5 tentatives → 15 min blocage)
- Email sender : `noreply@docai.fr`

**2. Clients configurés :**

| Client ID | Type | Usage |
|-----------|------|-------|
| `docai-backend` | confidential | Service Spring Boot (client_credentials + bearer-only) |
| `docai-frontend` | public | SPA React (authorization_code + PKCE) |
| `docai-admin` | confidential | Gestion admin (client_credentials, rôle SYSTEM) |

**3. Rôles de realm (5 rôles) :**

| Rôle | Description |
|------|-------------|
| `TENANT_ADMIN` | Administrateur du tenant — gère utilisateurs et abonnement |
| `ANALYST` | Upload et consultation documents |
| `VIEWER` | Lecture seule |
| `FRAUD_REVIEWER` | Queue de révision fraude |
| `SYSTEM` | Communication inter-services (jamais attribué à un humain) |

**4. Utilisateurs de test préconfigurés (environnement DEV uniquement) :**

| Email | Rôle | Tenant | Mot de passe |
|-------|------|--------|-------------|
| `admin@acme-corp.test` | TENANT_ADMIN | acme-corp | `Test1234!` |
| `analyst@acme-corp.test` | ANALYST | acme-corp | `Test1234!` |
| `viewer@acme-corp.test` | VIEWER | acme-corp | `Test1234!` |
| `reviewer@acme-corp.test` | FRAUD_REVIEWER | acme-corp | `Test1234!` |
| `admin@beta-assur.test` | TENANT_ADMIN | beta-assur | `Test1234!` |

**5. Claims JWT personnalisés (Protocol Mappers) :**

Le mapper `tenant-id-mapper` ajoute le claim `tenant_id` dans le JWT :
- Mapper type : `User Attribute`
- User attribute : `tenant_id`
- Token claim name : `tenant_id`
- Claim JSON type : `String`
- Add to access token : `true`
- Add to ID token : `false`

**6. Flows d'authentification :**
- Browser flow : Username/Password + OTP (2FA optionnel)
- Direct Access Grants : activé (pour les tests automatisés)
- Client Credentials : activé pour `docai-backend` et `docai-admin`

**Comment générer le fichier realm-docai.json :**

Si le fichier n'existe pas encore :
1. Démarrer Keycloak local sans import : `docker compose up keycloak`
2. Se connecter sur http://localhost:8180 (admin/admin123)
3. Créer manuellement le realm avec les settings ci-dessus
4. Exporter : `Admin Console → Realm Settings → Action → Export`
5. Sauvegarder dans `docker/keycloak/realm-docai.json`
6. Committer dans Git (c'est de la configuration, pas un secret)

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-KC-010 | Le fichier realm-docai.json est versionné dans Git | MUST |
| BR-KC-011 | Les mots de passe de test ne sont utilisés qu'en DEV (profil dev uniquement) | MUST |
| BR-KC-012 | Le mapper tenant_id est présent dans tous les JWT (vérifier en CI) | MUST |
| BR-KC-013 | En production, Keycloak est déployé depuis Terraform (pas d'import manuel) | MUST |



---

### **application.yml — Configuration Spring Boot Complète**

> **Où :** `docai-bootstrap/src/main/resources/`
> Ces fichiers sont la configuration de référence. Les valeurs sensibles sont toujours des variables d'environnement — jamais en dur.

---

#### `application.yml` — Configuration de base (tous les profils)

```yaml
spring:
  application:
    name: docai-backend

  # ── MongoDB ────────────────────────────────────────────────────
  data:
    mongodb:
      uri: ${MONGODB_URI}
      database: docai
      auto-index-creation: false          # Index créés uniquement via Mongock

  # ── Kafka ──────────────────────────────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      acks: all                           # At-least-once delivery
      enable-idempotence: true            # Idempotence producteur
      retries: 3
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        auto.register.schemas: false      # Schemas enregistrés via Apicurio
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false           # Commit manuel obligatoire
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        specific.avro.reader: true

  # ── Valkey (Redis compatible) ───────────────────────────────────
  data:
    redis:
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      password: ${VALKEY_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5

  # ── Security (Keycloak JWT) ────────────────────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/certs
          jwks-cache-ttl: 3600s           # Cache JWKS 1h (ADR-006)
          jwks-cache-refresh: 1800s       # Refresh toutes les 30 min

  # ── Amazon S3 ──────────────────────────────────────────────────
  cloud:
    aws:
      region:
        static: ${AWS_REGION:eu-west-3}
      credentials:
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}

  # ── Email (Amazon SES) ─────────────────────────────────────────
  mail:
    host: email-smtp.eu-west-3.amazonaws.com
    port: 587
    username: ${SES_SMTP_USERNAME}
    password: ${SES_SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

# ── Amazon S3 Bucket ───────────────────────────────────────────────
docai:
  s3:
    bucket: ${S3_BUCKET_NAME}
    presigned-url-expiry: 3600s           # URLs présignées valables 1h
  keycloak:
    admin-url: ${KEYCLOAK_URL}
    realm: ${KEYCLOAK_REALM:docai}
    client-id: ${KEYCLOAK_CLIENT_ID:docai-backend}
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
  billing:
    enabled: ${BILLING_ENABLED:false}     # Feature Flag — false par défaut
    stripe:
      secret-key: ${STRIPE_SECRET_KEY}
      webhook-secret: ${STRIPE_WEBHOOK_SECRET}

# ── Resilience4j ───────────────────────────────────────────────────
resilience4j:
  circuitbreaker:
    instances:
      llm:                                # LLM API (OpenAI, Mistral)
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
      ocr:                                # OCR Engine
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
      insee:                              # API INSEE SIRENE
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      ban:                                # API BAN Géoplateforme
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
  retry:
    instances:
      llm:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2  # 1s, 2s, 4s
      insee:
        max-attempts: 3
        wait-duration: 1s
      ban:
        max-attempts: 3
        wait-duration: 1s
  bulkhead:
    instances:
      llm:
        max-concurrent-calls: 20
        max-wait-duration: 0ms
      ocr:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
  timelimiter:
    instances:
      llm:
        timeout-duration: 30s
      ocr:
        timeout-duration: 60s
      insee:
        timeout-duration: 5s

# ── Outbox Poller ──────────────────────────────────────────────────
docai:
  outbox:
    poll-interval-ms: 500                 # Polling toutes les 500ms
    batch-size: 100                       # 100 events par batch
    max-retry-attempts: 5                 # 5 tentatives avant FAILED
    retry-delays-seconds: [1, 2, 4, 8, 16]  # Backoff exponentiel

# ── Rate Limiting (Bucket4j) ───────────────────────────────────────
  ratelimit:
    plans:
      starter:
        requests-per-minute: 100
        documents-per-month: 500
        overage-price-per-doc: 0.12
      pro:
        requests-per-minute: 1000
        documents-per-month: 10000
        overage-price-per-doc: 0.08
      enterprise:
        requests-per-minute: 10000
        documents-per-month: -1           # -1 = illimité

# ── Actuator / Observabilité ───────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    mongo:
      enabled: true
    kafka:
      enabled: true
    redis:
      enabled: true
    diskspace:
      enabled: true
      threshold: 10737418240              # Alerte si < 10GB
  metrics:
    tags:
      application: docai-backend
      environment: ${SPRING_PROFILES_ACTIVE:dev}
  tracing:
    sampling:
      probability: 1.0                    # 100% des traces en dev, 10% en prod

# ── OpenAPI / Swagger ──────────────────────────────────────────────
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method
  info:
    title: DocAI API
    version: v1
    description: API de traitement automatique de documents B2B
```

---

#### `application-dev.yml` — Profil développement local

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://docai_root:docai_secret_local@localhost:27017/docai?authSource=admin&replicaSet=rs0
  kafka:
    bootstrap-servers: localhost:9092
  data:
    redis:
      host: localhost
      port: 6379

docai:
  s3:
    bucket: docai-documents-dev
  keycloak:
    admin-url: http://localhost:8180
  billing:
    enabled: false                        # Gratuit en dev

management:
  endpoint:
    health:
      show-details: always                # Détails visibles en dev
  tracing:
    sampling:
      probability: 1.0                    # Toutes les traces en dev

logging:
  level:
    fr.docai: DEBUG
    org.springframework.security: DEBUG
    org.springframework.kafka: INFO

# LLM en mode mock via WireMock en dev
docai:
  llm:
    base-url: http://localhost:8099       # WireMock
    model: gpt-4o-mock
```

---

#### `application-prod.yml` — Profil production

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}                 # Via AWS Secrets Manager

management:
  endpoint:
    health:
      show-details: when-authorized       # Protégé en prod
  tracing:
    sampling:
      probability: 0.1                    # 10% des traces en prod (coût)

logging:
  level:
    fr.docai: INFO
    root: WARN
  pattern:
    console: >
      {"timestamp":"%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}",
       "level":"%level","service":"docai-backend",
       "traceId":"%X{traceId}","spanId":"%X{spanId}",
       "tenantId":"%X{tenantId}","message":"%msg"}%n

docai:
  billing:
    enabled: true                         # Facturation active en prod
  llm:
    base-url: https://api.openai.com
    model: gpt-4o
```


## 0.B — Standards, Qualité & Feature Flags

> **À configurer avant d'écrire la première ligne de code métier.**
> Ces standards s'appliquent à TOUS les modules. Les configurer maintenant évite de devoir corriger des centaines de fichiers plus tard.


**Nommage des classes :**
```
Use Case    : ClassifyDocumentUseCase       (verbe + objet + UseCase)
Value Object: ConfidenceScore               (nom métier explicite)
Aggregate   : Document, FraudAnalysis       (nom du concept domaine)
Adapter     : DocumentMongoAdapter          (objet + techno + Adapter)
Controller  : DocumentController            (objet + Controller)
Consumer    : ClassificationKafkaConsumer   (objet + techno + Consumer)
```

**Nommage des tests :**
```java
void should_return_classified_document_when_confidence_above_threshold()
void should_throw_quota_exceeded_exception_when_monthly_limit_reached()
void should_send_to_dlq_when_all_retry_attempts_exhausted()
```

**Seuils de qualité :**

| Règle | Seuil | Outil |
|-------|-------|-------|
| Longueur méthode | ≤ 20 lignes | Checkstyle |
| Longueur classe | ≤ 200 lignes | SonarCloud |
| Paramètres par méthode | ≤ 4 | Checkstyle |
| Complexité cyclomatique | ≤ 10 | SonarCloud |
| Couverture domaine | ≥ 90% | JaCoCo |
| Score mutation PIT domaine | ≥ 85% | PIT Maven |
| Code dupliqué | ≤ 3% | SonarCloud |

---

## 0.C — CI/CD Pipeline

> **À valider avec un premier build vert avant de démarrer le Module 0.**
> Le pipeline CI/CD est le filet de sécurité de tout le projet. Sans lui, impossible de garantir la qualité.


### Vue d'ensemble du pipeline

```
git push (any branch)
        │
        ▼
┌─────────────────────────────────────────┐
│  PHASE 1 — BUILD & COMPILATION          │
│  mvn clean compile + ArchUnit           │
│  ✅ Compile  ou  ❌ Arrêt pipeline      │
└────────────────────┬────────────────────┘
                     ▼
┌─────────────────────────────────────────┐
│  PHASE 2 — TESTS                        │
│  Unit + Intégration (TestContainers)    │
│  BDD Cucumber + JaCoCo coverage         │
│  ✅ Tous passent  ou  ❌ PR bloquée     │
└────────────────────┬────────────────────┘
                     ▼
┌─────────────────────────────────────────┐
│  PHASE 3 — SONARCLOUD                   │
│  Quality Gate : bugs, vulns, coverage   │
│  ✅ Gate passe  ou  ❌ Merge bloqué     │
└──────────── (develop/main/tags) ────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│  PHASE 4 — DOCKER BUILD & PUSH GHCR     │
│  Multi-stage JRE 21 Alpine              │
│  Scan Trivy (CRITICAL bloque)           │
│  Push ghcr.io/org/docai:{tag}           │
└──────────── (develop/main/tags) ────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│  PHASE 5 — DÉPLOIEMENT                  │
│  develop → Staging (auto)               │
│  tag v*.*.* → Production (approbation)  │
│  Health check + Rollback auto si KO     │
└──────────── (deploy + tag) ─────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│  PHASE 6 — DOCUMENTATION                │
│  OpenAPI + JavaDoc + JaCoCo + BDD HTML  │
│  Publication GitHub Pages               │
└──────────── (tags uniquement) ──────────┘
```

### Déclencheurs par branche

| Événement | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 | Phase 6 |
|-----------|---------|---------|---------|---------|---------|---------|
| PR → develop | ✅ | ✅ | ✅ (bloque PR) | ❌ | ❌ | ❌ |
| Push develop | ✅ | ✅ | ✅ | ✅ | ✅ Staging | ❌ |
| Push main | ✅ | ✅ | ✅ | ✅ | ✅ Prod (approbation) | ❌ |
| Tag v*.*.* | ✅ | ✅ | ✅ | ✅ | ✅ Prod | ✅ |

### Stratégie de branches — GitFlow adapté

| Branche | Protection | CI | Déploiement |
|---------|-----------|-----|------------|
| `main` | ✅ PR + 1 reviewer + Quality Gate | Toutes phases | Production (approbation) |
| `develop` | ✅ PR + Quality Gate | Phases 1–4 | Staging (auto) |
| `feature/UC-MOD-XXX-description` | ❌ Libre | Phases 1–3 | Aucun |
| `hotfix/v1.0.1-description` | ❌ Libre | Phases 1–3 | PR vers main + develop |
| `release/v1.X.0` | ❌ Libre | Toutes phases | PR vers main |

**Convention de commits obligatoire (Conventional Commits) :**

```bash
feat(recognition): add PDF classification support
fix(extraction): handle null LLM response gracefully
test(fraud): add BDD scenario for arithmetic signal
refactor(domain): extract ConfidenceScore value object
docs(api): update OpenAPI description for upload endpoint
ci: add Trivy Docker image scanning step
chore: upgrade Spring Boot to 4.0.1
perf(cache): increase Valkey TTL for LLM results
```

### Secrets & Variables GitHub

**Repository Secrets :**

| Secret | Description |
|--------|-------------|
| `SONAR_TOKEN` | Token API SonarCloud |
| `GHCR_TOKEN` | GitHub PAT (read:packages, write:packages) |
| `SLACK_WEBHOOK_URL` | Notifications Slack |
| `INSEE_CLIENT_ID` | API INSEE staging + prod |
| `INSEE_CLIENT_SECRET` | Secret API INSEE |
| `OPENAI_API_KEY` | Clé API LLM |
| `AWS_ACCESS_KEY_ID` | Credentials Amazon S3 |
| `AWS_SECRET_ACCESS_KEY` | Credentials Amazon S3 |

**Environment Secrets (par environnement) :**

| Secret | Staging | Production |
|--------|---------|-----------|
| `KUBECONFIG` | Config cluster staging | Config cluster production |
| `MONGODB_URI` | URI MongoDB staging | URI MongoDB production (Atlas) |
| `KEYCLOAK_CLIENT_SECRET` | Secret staging | Secret production |

> ---
> ### ⚠️ ADR-008 — Séparation des jobs CI et limite JVM (OBLIGATOIRE ici)
>
> **Pourquoi :** Les runners GitHub Actions ont 7GB de RAM. Spring Boot + TestContainers (MongoDB + Kafka + Valkey + LocalStack) en parallèle dépassent cette limite → OOM aléatoires et difficiles à diagnostiquer.
>
> **Comment configurer dans les workflows GitHub Actions :**
>
> Séparer en 3 jobs distincts dans `01-ci.yml` :
>
> Job 1 — `tests-unitaires` : modules `docai-domain` et `docai-application` uniquement. Pas de Docker. JVM `-Xmx512m`. Durée 2–4 min.
>
> Job 2 — `tests-integration` : modules adapters avec TestContainers. JVM `-Xmx512m`. Mode reuse TestContainers activé dans `AbstractIntegrationTest`. Durée 8–15 min.
>
> Job 3 — `tests-bdd` : Cucumber complet avec tous les conteneurs. JVM `-Xmx512m`. Mode reuse activé. Durée 10–20 min.
>
> Configuration Maven Surefire obligatoire en CI :
> `MAVEN_OPTS=-Xmx512m -Xms256m`
>
> Configuration TestContainers reuse dans `AbstractIntegrationTest` :
> Ajouter le fichier `~/.testcontainers.properties` avec `testcontainers.reuse.enable=true`
>
> **Test de validation :** Vérifier dans GitHub Actions que les 3 jobs s'exécutent en parallèle sans OOM sur 5 runs consécutifs.
>
> **Référence complète :** Annexe E — ADR-008
> ---

### Quality Gates — Seuils de blocage

| Phase | Condition | Conséquence |
|-------|-----------|-------------|
| Build | Code ne compile pas | ❌ Pipeline arrêté |
| Build | Violation ArchUnit | ❌ Pipeline arrêté |
| Tests | ≥ 1 test échoue | ❌ PR bloquée |
| Tests | Couverture globale < 80% | ❌ PR bloquée (SonarCloud) |
| Tests | Couverture `docai-domain` < 90% | ❌ PR bloquée |
| SonarCloud | ≥ 1 bug dans le nouveau code | ❌ Merge bloqué |
| SonarCloud | ≥ 1 vulnérabilité | ❌ Merge bloqué |
| SonarCloud | Duplication > 5% | ❌ Merge bloqué |
| Docker | Vulnérabilité CRITICAL | ❌ Image non publiée |
| Deploy Staging | Health check KO | ⚠️ Rollback automatique |
| Deploy Production | Approbation manuelle non reçue dans 1h | ⚠️ Timeout, annulé |
| Deploy Production | Health check KO | ⚠️ Rollback + alerte critique |

### Dockerfile multi-stage

```dockerfile
# ─── Stage 1 : Dépendances Maven (cache) ───────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS dependencies
WORKDIR /build
COPY pom.xml .
COPY */pom.xml* ./
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# ─── Stage 2 : Compilation & Tests ─────────────────────────────────────────
FROM dependencies AS build
COPY src ./src
COPY */src ./*
RUN ./mvnw clean package -DskipTests --no-transfer-progress \
    && java -Djarmode=layertools -jar docai-bootstrap/target/*.jar extract

# ─── Stage 3 : Image runtime légère (~200MB) ───────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Sécurité : utilisateur non-root
RUN addgroup -S docai && adduser -S docai -G docai
USER docai

# Layers Spring Boot pour cache Docker optimal
COPY --from=build /build/dependencies/ ./
COPY --from=build /build/snapshot-dependencies/ ./
COPY --from=build /build/spring-boot-loader/ ./
COPY --from=build /build/application/ ./

EXPOSE 8080

# Health check intégré
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
```


---

### **GitHub Actions — Fichiers de Workflow Complets**

> **Où :** Créer dans `.github/workflows/`. Ces fichiers définissent exactement les 6 phases du pipeline CI/CD.

---

#### `01-ci.yml` — Build, Tests & SonarCloud

```yaml
name: CI — Build & Tests

on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop, main]

jobs:
  # ── Job 1 : Tests unitaires (sans Docker) ────────────────────────
  unit-tests:
    name: Tests Unitaires
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Tests unitaires domaine
        env:
          MAVEN_OPTS: -Xmx512m -Xms256m    # ADR-008
        run: |
          ./mvnw test             -pl docai-domain,docai-application             --no-transfer-progress
      - name: Upload rapports tests
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-reports
          path: '**/target/surefire-reports/'

  # ── Job 2 : Tests intégration (TestContainers) ───────────────────
  integration-tests:
    name: Tests Intégration
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Tests intégration avec TestContainers
        env:
          MAVEN_OPTS: -Xmx512m -Xms256m    # ADR-008
          TESTCONTAINERS_REUSE_ENABLE: true # ADR-008
          AWS_ACCESS_KEY_ID: test
          AWS_SECRET_ACCESS_KEY: test
        run: |
          ./mvnw verify             -pl docai-adapter-out-mongodb,docai-adapter-out-kafka,docai-adapter-out-storage             -P integration-tests             --no-transfer-progress

  # ── Job 3 : Tests BDD Cucumber ───────────────────────────────────
  bdd-tests:
    name: Tests BDD Cucumber
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Tests BDD complets
        env:
          MAVEN_OPTS: -Xmx512m -Xms256m    # ADR-008
          TESTCONTAINERS_REUSE_ENABLE: true
          BILLING_ENABLED: "false"
        run: |
          ./mvnw test             -pl docai-bootstrap             -Dtest=CucumberTestRunner             --no-transfer-progress

  # ── Job 4 : Contract Tests (Spring Cloud Contract) ────────────────
  contract-tests:
    name: Contract Tests Frontend/Backend
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Vérification contrats API
        run: |
          ./mvnw spring-cloud-contract:generateTests             spring-cloud-contract:run             --no-transfer-progress
      - name: Publication stubs WireMock
        uses: actions/upload-artifact@v4
        with:
          name: wiremock-stubs
          path: '**/target/stubs/'

  # ── Job 5 : SonarCloud Quality Gate ──────────────────────────────
  sonarcloud:
    name: SonarCloud Analysis
    runs-on: ubuntu-latest
    needs: [unit-tests, integration-tests, bdd-tests]
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0                   # Full history pour SonarCloud
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Analyse SonarCloud
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          ./mvnw verify sonar:sonar             -Dsonar.projectKey=${{ vars.SONAR_PROJECT_KEY }}             -Dsonar.organization=${{ vars.SONAR_ORGANIZATION }}             -Dsonar.host.url=https://sonarcloud.io             --no-transfer-progress
```

---

#### `02-docker.yml` — Build Docker & Scan Sécurité

```yaml
name: Docker — Build, Scan & Push

on:
  push:
    branches: [develop, main]
    tags: ['v*.*.*']

jobs:
  docker:
    name: Build & Push Docker Image
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      security-events: write
    steps:
      - uses: actions/checkout@v4
      - name: Build application
        run: ./mvnw clean package -DskipTests --no-transfer-progress

      - name: Build Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: false
          tags: ghcr.io/${{ github.repository }}:${{ github.sha }}
          load: true

      - name: Scan Trivy (vulnérabilités)
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository }}:${{ github.sha }}
          format: sarif
          output: trivy-results.sarif
          severity: CRITICAL,HIGH
          exit-code: 1                     # CRITICAL bloque le pipeline

      - name: Login GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Push image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ghcr.io/${{ github.repository }}:${{ github.sha }}
            ghcr.io/${{ github.repository }}:latest
```

---

#### `03-deploy-staging.yml` — Déploiement Staging Automatique

```yaml
name: Deploy — Staging

on:
  push:
    branches: [develop]

jobs:
  deploy-staging:
    name: Déploiement Staging
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - uses: actions/checkout@v4

      - name: Configure kubectl (staging)
        uses: azure/k8s-set-context@v3
        with:
          kubeconfig: ${{ secrets.KUBECONFIG_STAGING }}

      - name: Déploiement Kubernetes staging
        run: |
          kubectl apply -k k8s/staging
          kubectl set image deployment/docai             docai=ghcr.io/${{ github.repository }}:${{ github.sha }}             -n docai-staging

      - name: Health check post-déploiement
        run: |
          kubectl rollout status deployment/docai -n docai-staging --timeout=300s
          # Vérifier que l'application répond
          sleep 30
          curl -f https://staging.docai.fr/actuator/health ||             (kubectl rollout undo deployment/docai -n docai-staging && exit 1)

      - name: Notification Slack
        if: always()
        uses: slackapi/slack-github-action@v1
        with:
          payload: |
            {"text": "Staging déployé : ${{ job.status }} — ${{ github.sha }}"}
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

---

#### `04-deploy-production.yml` — Déploiement Production avec Approbation

```yaml
name: Deploy — Production

on:
  push:
    tags: ['v*.*.*']

jobs:
  deploy-production:
    name: Déploiement Production
    runs-on: ubuntu-latest
    environment: production              # Requiert approbation manuelle
    steps:
      - uses: actions/checkout@v4

      - name: Configure kubectl (production)
        uses: azure/k8s-set-context@v3
        with:
          kubeconfig: ${{ secrets.KUBECONFIG_PRODUCTION }}

      - name: Déploiement Kubernetes production
        run: |
          kubectl apply -k k8s/production
          kubectl set image deployment/docai             docai=ghcr.io/${{ github.repository }}:${{ github.ref_name }}             -n docai-production

      - name: Health check production (zero-downtime)
        run: |
          # Attendre que le rolling update se termine
          kubectl rollout status deployment/docai -n docai-production --timeout=600s
          # Vérifier le SLA
          curl -f https://api.docai.fr/actuator/health ||             (kubectl rollout undo deployment/docai -n docai-production && exit 1)

      - name: Tag Git du déploiement
        run: |
          git tag deployed-production-$(date +%Y%m%d%H%M%S)
          git push origin --tags
```

---

#### `05-documentation.yml` — Publication OpenAPI & Docs

```yaml
name: Documentation — OpenAPI & JavaDoc

on:
  push:
    tags: ['v*.*.*']

jobs:
  publish-docs:
    name: Publier la documentation
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build & démarrer l'application
        run: ./mvnw spring-boot:start -pl docai-bootstrap -Dspring-boot.run.profiles=docs

      - name: Télécharger la spec OpenAPI
        run: |
          sleep 30
          curl http://localhost:8080/v3/api-docs -o openapi-spec.json

      - name: Valider la spec OpenAPI
        run: npx @apidevtools/swagger-cli validate openapi-spec.json

      - name: Arrêter l'application
        run: ./mvnw spring-boot:stop -pl docai-bootstrap

      - name: Publier sur GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./docs
          destination_dir: api/v1
```

---

#### `06-performance.yml` — Tests de Charge k6 (hebdomadaire + release)

```yaml
name: Performance — Tests de Charge k6

on:
  schedule:
    - cron: '0 2 * * 1'                   # Chaque lundi à 2h UTC
  workflow_dispatch:                       # Déclenchement manuel

jobs:
  load-tests:
    name: Tests de Charge Staging
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - uses: actions/checkout@v4

      - name: Installer k6
        run: |
          sudo gpg -k
          sudo gpg --no-default-keyring             --keyring /usr/share/keyrings/k6-archive-keyring.gpg             --keyserver hkp://keyserver.ubuntu.com:80             --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
          echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg]             https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
          sudo apt-get update && sudo apt-get install k6

      - name: Test charge nominale
        env:
          K6_STAGING_URL: ${{ vars.STAGING_URL }}
          K6_API_TOKEN: ${{ secrets.STAGING_API_TOKEN }}
        run: k6 run k6/nominal-load-test.js --out json=results-nominal.json

      - name: Test pointe soudaine
        run: k6 run k6/spike-load-test.js --out json=results-spike.json

      - name: Publier résultats Grafana
        run: |
          curl -X POST ${{ vars.GRAFANA_PUSH_URL }}             -H "Content-Type: application/json"             -d @results-nominal.json
```


### sonar-project.properties

```properties
sonar.projectKey=${SONAR_PROJECT_KEY}
sonar.organization=${SONAR_ORGANIZATION}
sonar.projectName=DocAI Backend
sonar.java.source=21
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml
sonar.junit.reportPaths=**/target/surefire-reports,**/target/failsafe-reports
sonar.exclusions=\
  **/target/**,\
  **/generated-sources/**,\
  **/*Application.java,\
  **/config/**,\
  **/dto/**,\
  **/entity/**
sonar.coverage.exclusions=\
  **/*Application.java,\
  **/config/**,\
  **/dto/**,\
  **/entity/**
```


---

### **Contract Testing Frontend/Backend — Stabilité des APIs**

> **Où :** À intégrer dans le pipeline CI/CD dès que le Module 6 (API Publique) est développé. Empêche le frontend de casser silencieusement quand le backend change.

**Pourquoi le Contract Testing :**
Sans contract testing, si un développeur backend renomme un champ JSON (`montantTTC` → `totalTTC`), le frontend plante silencieusement. Le bug est découvert par les utilisateurs, pas par le CI.

**Outil retenu : Spring Cloud Contract**
Spring Cloud Contract est retenu car il s'intègre nativement avec Spring Boot, génère des stubs WireMock automatiquement pour le frontend, et s'exécute dans le pipeline CI existant.

**Comment ça fonctionne :**

```
Backend définit les contrats (fichiers Groovy/YAML) :
  "Quand je reçois POST /v1/documents avec ce body,
   je retourne ce JSON avec ces champs"

Spring Cloud Contract génère automatiquement :
  1. Des tests côté backend (vérifient que le backend respecte le contrat)
  2. Des stubs WireMock côté frontend (simulent le backend pour les tests frontend)

Si le backend change et viole le contrat → test échoue en CI → PR bloquée
```

**Contrats à définir par module :**

| Module | Contrats prioritaires |
|--------|----------------------|
| Module 1 Upload | POST /v1/documents → 201 + documentId |
| Module 1 Upload | GET /v1/documents/{id} → statut + champs |
| Module 5 Dashboard | GET /v1/dashboard/summary → KPIs |
| Module 6 API | Tous les endpoints publics |
| Module 7 Billing | GET /v1/billing/usage → quota temps réel |

**Intégration dans le pipeline CI :**

Ajouter dans `01-ci.yml` un job dédié `contract-tests` :
- Exécuté après les tests unitaires
- Vérifie que tous les contrats définis sont respectés par le backend
- Publie les stubs WireMock dans un artifact GitHub pour le frontend
- Si un contrat est violé → PR bloquée (même règle que les tests unitaires)

**Comment créer un nouveau contrat :**
1. Créer le fichier de contrat dans `src/test/resources/contracts/`
2. Définir la requête et la réponse attendue
3. Lancer `./mvnw spring-cloud-contract:generateTests` — les tests sont générés automatiquement
4. Vérifier que les tests passent
5. Publier les stubs pour le frontend (artifact CI ou package Maven)

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-CT-001 | Tout endpoint public exposé au frontend a un contrat défini | MUST |
| BR-CT-002 | Un contrat violé bloque la PR exactement comme un test unitaire | MUST |
| BR-CT-003 | Les stubs WireMock sont publiés automatiquement après chaque build réussi | MUST |
| BR-CT-004 | Le frontend utilise les stubs WireMock générés — jamais de mocks manuels | SHOULD |

**Référence dans les modules :**
- Module 6.1 API Publique → créer les contrats pour tous les endpoints /v1/
- Module 5.1 Dashboard → créer les contrats pour les endpoints dashboard
- Module 7 Billing → créer les contrats pour les endpoints usage et plans


---

### **Infrastructure as Code — Terraform**

> **Où :** À créer en même temps que la Section 0.A (Création du projet). L'infrastructure doit être versionnée comme le code — jamais créée manuellement en production.

**Pourquoi Terraform :**
Sans IaC, l'infrastructure est créée manuellement → impossible à reproduire, pas de disaster recovery, configuration dérivant entre staging et production. Terraform garantit que staging et production sont identiques.

**Organisation des fichiers Terraform dans le projet :**

```
infra/
├── terraform/
│   ├── modules/                    ← Modules réutilisables
│   │   ├── s3-bucket/              ← Bucket S3 + versioning + KMS + Lifecycle Rule (ADR-007)
│   │   ├── mongodb-atlas/          ← Cluster Atlas + replica set + backup
│   │   ├── keycloak-realm/         ← Realm DocAI + clients + rôles
│   │   └── kafka-cloud/            ← Brokers + topics + partitions (ADR-002)
│   │
│   ├── environments/
│   │   ├── staging/
│   │   │   ├── main.tf             ← Appel des modules pour staging
│   │   │   ├── variables.tf        ← Variables staging (1 replica, M10)
│   │   │   └── terraform.tfvars   ← Valeurs staging (dans .gitignore)
│   │   │
│   │   └── production/
│   │       ├── main.tf             ← Appel des modules pour production
│   │       ├── variables.tf        ← Variables prod (3 replicas, M30)
│   │       └── terraform.tfvars   ← Valeurs prod (dans .gitignore — secrets via Vault)
│   │
│   └── backend.tf                  ← State Terraform stocké dans S3 (pas en local)
```

**Ressources Terraform à provisionner par module :**

**Module S3 (ADR-007 — obligatoire) :**
- Bucket S3 avec versioning activé
- Chiffrement SSE-KMS activé (clé KMS créée par Terraform — ADR-005)
- Lifecycle Rule : supprimer uploads multipart non finalisés après 24h
- Lifecycle Rule : archiver documents > rétention vers S3 Glacier (ADR-010)
- Réplication cross-region eu-west-3 → eu-central-1 (Disaster Recovery)
- Budget AWS : alerte si coût > 150% du mois précédent

**Module MongoDB Atlas :**
- Cluster M10 staging (1 replica set, 3 nodes)
- Cluster M30 production (1 replica set, 3 nodes, multi-AZ)
- Backup automatique quotidien activé
- Point-in-time recovery activé
- IP Whitelist : uniquement les IPs du cluster Kubernetes

**Module Keycloak :**
- Realm `docai` importé depuis `docker/keycloak/realm-docai.json`
- Clients configurés (docai-backend, docai-frontend)
- 5 rôles créés (TENANT_ADMIN, ANALYST, VIEWER, FRAUD_REVIEWER, SYSTEM)

**Module Kafka Cloud :**
- 3 brokers minimum en production (replication factor 3 — ADR-002)
- 8 topics créés avec partitions et rétention corrects
- Clé partition = documentId configurée (ADR-002)

**Comment utiliser Terraform :**

```
# Initialiser Terraform (première fois)
cd infra/terraform/environments/staging
terraform init

# Vérifier ce qui va être créé/modifié
terraform plan

# Appliquer les changements
terraform apply

# Ne jamais faire terraform apply en production manuellement
# → Passer par le pipeline CI/CD Phase 4 uniquement
```

**Intégration CI/CD :**
- Job `04-infra.yml` : `terraform plan` en PR (affiche ce qui change)
- Job `04-infra.yml` : `terraform apply` automatique sur merge vers main
- State Terraform stocké dans S3 (partagé entre tous les membres de l'équipe)
- Secrets Terraform via AWS Secrets Manager (jamais dans le code)


---

#### Contenu des fichiers Terraform — Modules principaux

**Module S3 (ADR-007 + ADR-005) :**
- Bucket name : docai-documents-{environment}
- Versioning : active
- Chiffrement SSE-KMS avec cle KMS du module KMS
- Public access : tout bloque (acces via presigned URLs uniquement)
- Lifecycle Rule 1 — Multipart cleanup (ADR-007) : AbortIncompleteMultipartUpload apres 1 jour
- Lifecycle Rule 2 — Archivage Glacier (ADR-010) : transition S3 Glacier apres 90 jours
- Replication cross-region eu-west-3 vers eu-central-1 (production uniquement)

**Module MongoDB Atlas :**
- Staging : M10, EU_WEST_3, 3 nodes, backup 2 jours
- Production : M30, EU_WEST_3, 3 nodes multi-AZ, auto-scaling CPU 75%, backup 7 jours

**Module KMS (ADR-005) :**
- Alias : alias/docai-pii-{environment}
- Rotation automatique annuelle activee
- Key policy : acces uniquement au role IAM application (pas aux developpeurs)
- CloudTrail logging de chaque utilisation

**Variables par environnement :**

| Variable | Staging | Production |
|----------|---------|-----------|
| mongodb_instance_size | M10 | M30 |
| enable_cross_region_replication | false | true |
| mongodb_backup_retention_days | 2 | 7 |
| kafka_broker_count | 1 | 3 |

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-TF-001 | Toute ressource cloud est créée via Terraform — jamais manuellement | MUST |
| BR-TF-002 | Le state Terraform est stocké dans S3 avec verrouillage DynamoDB | MUST |
| BR-TF-003 | `terraform plan` est exécuté en CI sur chaque PR touchant `infra/` | MUST |
| BR-TF-004 | `terraform apply` est exécuté uniquement via le pipeline CI/CD | MUST |
| BR-TF-005 | Les fichiers `.tfvars` contenant des secrets sont dans `.gitignore` | MUST |
| BR-TF-006 | Staging et production utilisent les mêmes modules — seules les variables diffèrent | MUST |
| BR-TF-007 | La Lifecycle Rule S3 (ADR-007) est configurée dans le module Terraform S3 | MUST |
| BR-TF-008 | Le chiffrement KMS S3 (ADR-005) est configuré dans le module Terraform S3 | MUST |

**Référence dans les modules :**
- ADR-007 → module Terraform S3 Lifecycle Rule
- ADR-005 → module Terraform S3 KMS encryption
- ADR-010 → module Terraform S3 Glacier archiving
- ADR-006 → module Terraform Keycloak 2 instances



---

### **Kubernetes — Contenu des Manifestes**

> **Où :** À créer en Section 0.C après le setup Terraform. Les manifestes Kubernetes définissent comment l'application tourne en staging et production.

**Rappel de la structure k8s/ dans le projet :**

```
k8s/
├── base/                           ← Manifestes communs (staging + prod)
│   ├── deployment.yaml             ← Déploiement application
│   ├── service.yaml                ← Exposition interne
│   ├── hpa.yaml                    ← Auto-scaling horizontal
│   ├── configmap.yaml              ← Configuration non sensible
│   └── ingress.yaml                ← Exposition externe HTTPS
├── staging/
│   ├── kustomization.yaml          ← 1 replica, namespace docai-staging
│   └── configmap-patch.yaml        ← Config spécifique staging
└── production/
    ├── kustomization.yaml          ← 3 replicas, namespace docai-production
    └── configmap-patch.yaml        ← Config spécifique production
```

**Contenu de chaque manifeste :**

**deployment.yaml — Déploiement de l'application :**

Ce fichier définit comment l'application Spring Boot est déployée.
Points clés à configurer :

- Image Docker : `ghcr.io/org/docai:{tag}` (tag = numéro de version Git)
- Resources limits : `memory: 512Mi, cpu: 500m` (base)
- Resources requests : `memory: 256Mi, cpu: 250m`
- Liveness probe : `GET /actuator/health/liveness` toutes les 30s (timeout 10s)
- Readiness probe : `GET /actuator/health/readiness` toutes les 10s (timeout 5s)
- Startup probe : `GET /actuator/health` toutes les 10s (failureThreshold: 30 = 5 min max)
- Variables d'environnement via ConfigMap (non sensibles) et Secrets (sensibles via AWS Secrets Manager)
- Stratégie de déploiement : **RollingUpdate** (zero-downtime)
  - maxUnavailable: 0 (jamais de pod indisponible pendant le déploiement)
  - maxSurge: 1 (1 pod supplémentaire pendant la transition)

**Comment fonctionne le zero-downtime avec RollingUpdate :**

```
État initial : 3 pods v1.0 en production

Déploiement v1.1 démarre :
  Step 1 → Démarre 1 pod v1.1 (maxSurge=1) → 4 pods total
  Step 2 → Attend que v1.1 soit Ready (readiness probe OK)
  Step 3 → Arrête 1 pod v1.0 (maxUnavailable=0)
  Step 4 → Répète jusqu'à 3 pods v1.1

Résultat : 0 interruption de service pendant le déploiement
```

**hpa.yaml — Auto-scaling horizontal :**

L'HPA ajuste automatiquement le nombre de replicas selon la charge.

- Minimum replicas : 2 (staging : 1)
- Maximum replicas : 10 (staging : 3)
- Scale-out déclenché si : CPU > 70% OU Kafka consumer lag > 1000 messages
- Scale-in : délai de stabilisation 5 minutes (évite le flapping)

**Métriques Kafka pour l'HPA :**
Le lag Kafka est exposé via Prometheus → KEDA (Kubernetes Event-Driven Autoscaling) lit cette métrique et ajuste les replicas des consumers automatiquement.

**configmap.yaml — Configuration non sensible :**

Variables d'environnement injectées depuis ConfigMap :
- `SPRING_PROFILES_ACTIVE=prod`
- `KAFKA_BOOTSTRAP_SERVERS=kafka-cluster:9092`
- `SCHEMA_REGISTRY_URL=http://schema-registry:8081`
- `MONGODB_DATABASE=docai`
- `BILLING_ENABLED=true` (production uniquement)

**Secrets Kubernetes — via AWS Secrets Manager :**

Les secrets (mots de passe, clés API) ne sont JAMAIS dans les manifestes Kubernetes.
Ils sont injectés via le **AWS Secrets Manager CSI Driver** :

```
AWS Secrets Manager
  └── secret/docai/production/mongodb-uri
  └── secret/docai/production/keycloak-secret
  └── secret/docai/production/openai-key
        ↓ (CSI Driver monte les secrets comme volumes)
Pod Spring Boot
  └── Lit les secrets depuis des variables d'environnement
      injectées par le CSI Driver
```

**ingress.yaml — Exposition externe HTTPS :**

- TLS 1.3 obligatoire (ADR-005 — sécurité)
- Certificat SSL géré par cert-manager (Let's Encrypt)
- Rate limiting au niveau Ingress : 1000 req/min par IP
- Headers sécurité injectés par Ingress : HSTS, X-Frame-Options, CSP

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-K8S-001 | Stratégie RollingUpdate avec maxUnavailable=0 en production | MUST |
| BR-K8S-002 | Liveness, Readiness et Startup probes configurées sur chaque pod | MUST |
| BR-K8S-003 | Secrets injectés via AWS Secrets Manager CSI Driver — jamais dans les manifestes | MUST |
| BR-K8S-004 | HPA configuré avec minimum 2 replicas en production | MUST |
| BR-K8S-005 | TLS 1.3 activé sur l'Ingress avec cert-manager | MUST |
| BR-K8S-006 | Resources limits et requests définies sur chaque container | MUST |
| BR-K8S-007 | Staging et production utilisent les mêmes manifestes base via Kustomize | MUST |
| BR-K8S-008 | KEDA configuré pour scale les consumers Kafka selon le lag | SHOULD |

**Intégration CI/CD :**
- Phase 5 du pipeline → `kubectl apply -k k8s/staging` (déploiement staging automatique)
- Phase 5 du pipeline → `kubectl apply -k k8s/production` (déploiement prod avec approbation)
- Health check post-déploiement : vérifier que tous les pods sont Ready dans les 5 minutes
- Rollback automatique si health check échoue : `kubectl rollout undo deployment/docai`

**Référence dans les modules :**
- ADR-006 → 2 replicas Keycloak dans les manifestes production
- ADR-008 → Resources limits JVM (-Xmx512m) dans les limites container
- Module 0 → Variables d'environnement Keycloak dans ConfigMap
- Module 7 → BILLING_ENABLED=true dans ConfigMap production uniquement



---

### **Health Checks Détaillés — Stratégie par Service**

> **Où :** À configurer en Section 0.C en même temps que les manifestes Kubernetes. Les health checks sont la base du zero-downtime et du monitoring en production.

**Pourquoi des health checks détaillés :**
Un `/actuator/health` qui retourne UP ne suffit pas. Si MongoDB est en lecture seule ou si Kafka ne répond plus, l'application semble UP mais ne traite plus les documents. Les health checks détaillés exposent l'état réel de chaque dépendance.

**3 endpoints Spring Boot Actuator à configurer :**

| Endpoint | Usage | Configuré dans |
|----------|-------|----------------|
| `/actuator/health/liveness` | Kubernetes Liveness Probe — pod à redémarrer ? | K8s deployment.yaml |
| `/actuator/health/readiness` | Kubernetes Readiness Probe — pod prêt à recevoir du trafic ? | K8s deployment.yaml |
| `/actuator/health` | Monitoring complet — état de toutes les dépendances | Grafana / alertes |

**Différence liveness vs readiness :**

```
Liveness Probe (défaillance = redémarrer le pod) :
  → Vérifie que l'application n'est pas bloquée (deadlock, OOM)
  → Ne vérifie PAS les dépendances externes (MongoDB, Kafka)
  → Si fail → Kubernetes tue et redémarre le pod
  → Seuil tolérant : échec 3× avant action

Readiness Probe (défaillance = arrêter d'envoyer du trafic) :
  → Vérifie que l'application peut traiter des requêtes
  → Vérifie les dépendances critiques (MongoDB, Kafka)
  → Si fail → Kubernetes retire le pod du load balancer (pas de redémarrage)
  → Seuil rapide : échec 1× → retrait immédiat du trafic
```

**Indicateurs de santé par service :**

| Service | Indicateur | Impact si DOWN | Liveness | Readiness |
|---------|-----------|---------------|----------|-----------|
| **MongoDB** | Ping replica set primary | Aucune persistance | ❌ Non | ✅ Oui |
| **Kafka** | Consumer group actif | Aucun event traité | ❌ Non | ✅ Oui |
| **Valkey** | PING command | Pas de cache/quota | ❌ Non | ✅ Oui |
| **Keycloak** | JWKS endpoint accessible | Pas d'auth (cache 1h) | ❌ Non | ❌ Non (cache JWKS) |
| **Amazon S3** | HeadBucket sur bucket doc | Pas d'upload | ❌ Non | ✅ Oui |
| **Disk space** | Espace disque > 10% | Application bloquée | ✅ Oui | ✅ Oui |
| **JVM Heap** | Heap < 90% utilisé | OOM imminent | ✅ Oui | ✅ Oui |

**Comment configurer dans `application.yml` :**

```yaml
management:
  endpoint:
    health:
      show-details: always        # En prod : when-authorized uniquement
      probes:
        enabled: true             # Active /liveness et /readiness
  health:
    mongo:
      enabled: true
    kafka:
      enabled: true
    redis:
      enabled: true               # Valkey utilise le health check Redis
    diskspace:
      enabled: true
      threshold: 10737418240      # Alerte si < 10GB disponibles
```

**Health check personnalisé Amazon S3 :**
Spring Boot n'a pas de health check S3 natif. Créer `S3HealthIndicator` qui implémente `HealthIndicator` :
1. Appeler `HeadBucket` sur le bucket de production
2. Si succès → `Health.up()`
3. Si échec → `Health.down().withDetail("bucket", bucketName).build()`
4. TTL cache : 30 secondes (éviter un appel S3 à chaque readiness probe)

**Format de réponse `/actuator/health` attendu :**

```json
{
  "status": "UP",
  "components": {
    "mongo":      { "status": "UP", "details": { "version": "7.0.x" } },
    "kafka":      { "status": "UP", "details": { "brokersAvailable": 3 } },
    "redis":      { "status": "UP" },
    "s3":         { "status": "UP", "details": { "bucket": "docai-prod" } },
    "diskSpace":  { "status": "UP", "details": { "free": 50000000000 } },
    "livenessState":  { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

**Alertes Grafana basées sur les health checks :**

| Alerte | Condition | Sévérité | Action |
|--------|-----------|---------|--------|
| MongoDB DOWN | health.mongo = DOWN | CRITICAL | PagerDuty immédiat |
| Kafka DOWN | health.kafka = DOWN | CRITICAL | PagerDuty immédiat |
| S3 DOWN | health.s3 = DOWN | HIGH | Slack + PagerDuty |
| Valkey DOWN | health.redis = DOWN | HIGH | Slack |
| Disk space critique | diskSpace < 5GB | HIGH | Slack |
| JVM Heap > 85% | heap > 85% | WARN | Slack |

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-HC-001 | Liveness et Readiness probes configurées dans tous les pods Kubernetes | MUST |
| BR-HC-002 | Le health check MongoDB vérifie le primary replica set (pas juste le ping) | MUST |
| BR-HC-003 | Le health check S3 personnalisé est implémenté avec TTL cache 30s | MUST |
| BR-HC-004 | `/actuator/health` est accessible sans authentification uniquement depuis le réseau interne | MUST |
| BR-HC-005 | En production `show-details: when-authorized` — jamais `always` | MUST |
| BR-HC-006 | Chaque composant DOWN déclenche une alerte Grafana avec runbook associé | MUST |


### Pull Request Template — `.github/pull_request_template.md`

```markdown
## Description
<!-- Décrivez les changements apportés -->

## Type de changement
- [ ] feat: Nouvelle fonctionnalité
- [ ] fix: Correction de bug
- [ ] refactor: Refactoring
- [ ] test: Ajout de tests
- [ ] docs: Documentation

## Checklist Architecture
- [ ] Le code respecte l'architecture hexagonale
- [ ] ArchUnit ne signale aucune violation (docai-domain sans imports Spring/Mongo/Kafka)
- [ ] Un seul niveau d'abstraction par méthode

## Checklist Tests
- [ ] Nouveaux cas couverts par des tests unitaires (nommage: should_X_when_Y)
- [ ] Scénarios Gherkin mis à jour si comportement métier modifié
- [ ] Couverture domaine ≥ 90% maintenue

## Checklist Sécurité
- [ ] Pas de données sensibles dans les logs
- [ ] Isolation tenant vérifiée (tenantId dans toutes les requêtes MongoDB)
- [ ] Endpoints sécurisés avec `@PreAuthorize`
- [ ] Pas de secrets dans le code

## Checklist Performance
- [ ] Pas de requêtes N+1
- [ ] Index MongoDB utilisé pour les nouvelles requêtes
- [ ] Cache Valkey utilisé si données consultées fréquemment

## Checklist Clean Code
- [ ] Noms explicites (pas d'abréviations obscures)
- [ ] Méthodes ≤ 20 lignes, classes ≤ 200 lignes
- [ ] Exceptions domaine levées (pas de RuntimeException générique)
- [ ] Pas de null retourné (Optional ou exception)
- [ ] Format de commit Conventional Commits respecté
```


---

### **Definition of Ready (DoR) — Critères obligatoires avant de commencer une User Story**

> **Où :** Cette section s'applique à TOUTES les User Stories de tous les modules.
> Un développeur NE DOIT PAS commencer à coder si l'un de ces critères n'est pas rempli.

**Comment utiliser la DoR :**
Avant chaque sprint planning, vérifier cette checklist pour chaque US prévue au sprint. Si un critère est manquant → l'US reste dans le backlog jusqu'à ce qu'il soit rempli.

**Checklist DoR — obligatoire pour chaque User Story :**

| # | Critère | Responsable | Vérifié par |
|---|---------|-------------|-------------|
| 1 | **La US est estimée en story points** | Équipe dev | Tech Lead |
| 2 | **Les critères d'acceptance sont écrits** en format BDD (Given/When/Then) | PO / Dev | Tech Lead |
| 3 | **Les scénarios Gherkin sont rédigés** et validés par le PO | Dev | PO |
| 4 | **L'ADR applicable est identifié** (ex: ADR-001 si quota concerné) | Dev | Tech Lead |
| 5 | **Les dépendances avec d'autres modules sont identifiées** et les interfaces sont stables | Dev | Tech Lead |
| 6 | **Les accès aux services externes sont disponibles** (clés INSEE, Stripe test, etc.) | DevOps | Dev |
| 7 | **Le schéma MongoDB est défini** si la US crée une nouvelle collection | Dev | Tech Lead |
| 8 | **Les endpoints OpenAPI sont esquissés** si la US expose une nouvelle API | Dev | PO |
| 9 | **L'impact sur le quota et le billing est évalué** si la US touche au pipeline | Dev | Tech Lead |
| 10 | **La US tient dans un sprint** (< 8 story points) — sinon découper | PO | Tech Lead |

**Exemple concret — US "Soumettre un document" :**
- ✅ Estimée : 5 points
- ✅ Critères d'acceptance : Gherkin rédigé (scénario succès, quota dépassé, format invalide)
- ✅ ADR identifié : ADR-001 (quota Lua), ADR-007 (AbortMultipart S3)
- ✅ Dépendances : Module 0 (TenantFilter) terminé et stable
- ✅ Accès S3 : bucket dev configuré, credentials dans .env
- ✅ Schéma MongoDB : collection `documents` définie dans Module 1


---


---

### **Templates Emails Amazon SES — Standard Obligatoire**

> **Où :** À définir avant le Module 0 car les premiers emails (bienvenue, invitation) sont envoyés dès la Phase 0.1 Inscription. Sans templates standardisés, chaque développeur code un email différemment.

**Organisation des templates dans le projet :**

```
src/main/resources/email-templates/
├── fr/                              ← Templates français (langue par défaut)
│   ├── welcome.html                 ← Bienvenue après inscription
│   ├── email-verification.html      ← Vérification email
│   ├── invitation.html              ← Invitation d'un collègue
│   ├── invitation-accepted.html     ← Confirmation activation compte
│   ├── password-reset.html          ← Réinitialisation mot de passe
│   ├── password-changed.html        ← Confirmation changement MDP
│   ├── account-revoked.html         ← Révocation d'accès
│   ├── quota-warning-80.html        ← Alerte 80% quota
│   ├── quota-warning-95.html        ← Alerte 95% quota
│   ├── subscription-activated.html  ← Abonnement activé
│   ├── invoice.html                 ← Facture mensuelle
│   ├── payment-failed.html          ← Paiement échoué
│   ├── subscription-canceled.html   ← Abonnement résilié
│   ├── trial-ending-7days.html      ← J-7 fin période gratuite
│   ├── trial-ending-3days.html      ← J-3 fin période gratuite
│   ├── trial-expired.html           ← Période gratuite expirée
│   ├── support-access-request.html  ← Demande accès support
│   ├── monthly-report.html          ← Rapport mensuel (backlog v2)
│   └── data-deletion-confirmed.html ← Confirmation effacement RGPD
└── layout/
    ├── base.html                    ← Layout HTML commun (header, footer, logo)
    └── base-text.txt                ← Version texte brut (fallback)
```

**Standard de contenu pour chaque template :**

Chaque email doit contenir :
- Logo DocAI en header
- Titre clair en H1
- Corps du message en texte lisible (pas de jargon technique)
- Un seul bouton d'action principal (CTA) si applicable
- Footer avec lien de désinscription (obligation légale)
- Footer avec adresse postale (obligation légale RGPD)
- Version texte brut en fallback (pour les clients email sans HTML)

**Catalogue complet des emails — déclencheur et contenu :**

| Template | Déclencheur | Destinataire | CTA | Module |
|----------|-------------|-------------|-----|--------|
| `welcome` | Inscription réussie | Souscripteur | Accéder au dashboard | 0.1 |
| `email-verification` | Inscription | Souscripteur | Vérifier mon email | 0.1 |
| `invitation` | TENANT_ADMIN invite | Invité | Activer mon compte | 0.1 |
| `password-reset` | Mot de passe oublié | Utilisateur | Réinitialiser | 0.2 |
| `password-changed` | MDP modifié | Utilisateur | Ce n'est pas moi | 0.4 |
| `quota-warning-80` | 80% quota atteint | TENANT_ADMIN | Upgrader mon plan | 7 |
| `quota-warning-95` | 95% quota atteint | TENANT_ADMIN | Upgrader maintenant | 7 |
| `subscription-activated` | Paiement Stripe OK | TENANT_ADMIN | Voir mon abonnement | 7 |
| `invoice` | 1er du mois | TENANT_ADMIN | Télécharger la facture | 7 |
| `payment-failed` | Stripe payment_failed | TENANT_ADMIN | Mettre à jour ma CB | 7 |
| `trial-ending-7days` | J-7 avant fin FREE | TENANT_ADMIN | Choisir un plan | 7 |
| `trial-expired` | Expiration FREE | TENANT_ADMIN | Choisir un plan | 7 |
| `support-access-request` | Agent support demande accès | TENANT_ADMIN | Autoriser / Refuser | 0 |

**Comment implémenter le service email :**

1. Créer `AmazonSesEmailAdapter` qui implémente `EmailNotificationPort`
2. Charger les templates via `Thymeleaf` (moteur de templates intégré à Spring Boot)
3. Passer les variables dynamiques (prénom, quota, lien, etc.) via un `Map<String, Object>`
4. Envoyer via `SesClient.sendEmail()` avec version HTML + version texte brut
5. Logguer chaque envoi : destinataire masqué `[PII_MASKED]`, template, statut

**Variables dynamiques communes à tous les templates :**

| Variable | Exemple | Description |
|----------|---------|-------------|
| `{{firstName}}` | Alice | Prénom de l'utilisateur |
| `{{tenantName}}` | ACME Corp | Nom de l'entreprise |
| `{{appUrl}}` | https://app.docai.fr | URL de l'application |
| `{{supportEmail}}` | support@docai.fr | Email du support |
| `{{year}}` | 2026 | Année courante (footer) |

**Configuration Amazon SES :**

- Mode sandbox SES en DEV et STAGING : les emails sont bloqués (non envoyés)
- Mode production SES en PRODUCTION uniquement : emails réellement envoyés
- Vérification domaine SES : `docai.fr` vérifié avec DKIM et SPF
- Quota SES : 50 000 emails/jour par défaut (suffisant pour le lancement)
- Bounce rate < 5% et complaint rate < 0.1% → surveiller dans SES Console

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-EMAIL-001 | Chaque template a une version HTML et une version texte brut | MUST |
| BR-EMAIL-002 | Les PII (email, prénom) ne sont jamais loggués — `[PII_MASKED]` | MUST |
| BR-EMAIL-003 | Chaque email contient un lien de désinscription (obligation légale) | MUST |
| BR-EMAIL-004 | SES en mode sandbox en DEV/STAGING — production uniquement en PROD | MUST |
| BR-EMAIL-005 | Le bounce rate SES est monitoré — alerte si > 3% | MUST |
| BR-EMAIL-006 | Les templates sont versionnés dans le dépôt Git comme le code | MUST |


### **Feature Flags — Stratégie de Déploiement Progressif**

> **Où :** Cette stratégie s'applique dès le Module 0.2 (BILLING_ENABLED) et s'étend à tous les nouveaux modules. À configurer avant le premier déploiement en production.

**Pourquoi les Feature Flags :**
Sans Feature Flags, chaque déploiement est tout-ou-rien. Avec les Feature Flags :
- Activer un module pour 10% des tenants → valider → 100%
- Désactiver un module défaillant sans redéploiement (kill switch)
- Tester une nouvelle version du scoring fraude sur un client pilote
- Lancer le billing sans impact sur les tenants existants

**Outil retenu : Unleash (open source)**
Unleash est retenu car il est open source (auto-hébergé), supporte les flags par tenant, et s'intègre nativement avec Spring Boot via le SDK Java officiel.

**Feature Flags définis dans DocAI :**

| Flag | Valeur par défaut | Description | Modules concernés |
|------|-----------------|-------------|------------------|
| `billing.enabled` | false | Active la facturation Stripe | Module 7 |
| `fraud.v2.enabled` | false | Active le nouveau scoring fraude v2 | Module 3 |
| `extraction.mistral.enabled` | false | Active Mistral comme provider LLM alternatif | Module 2 |
| `dashboard.search.enabled` | false | Active la recherche full-text | Module 5.4 |
| `notifications.inapp.enabled` | true | Active les notifications in-app | Module 5.3 |
| `maintenance.mode` | false | Kill switch global — bloque tous les uploads | Tous |

**Comment utiliser un Feature Flag dans un module :**

1. Définir le flag dans Unleash avec sa valeur par défaut
2. Dans le Use Case concerné, injecter le `FeatureFlagPort`
3. Vérifier le flag avant d'exécuter la logique
4. Si le flag est false → comportement par défaut (gratuit, version précédente)
5. Si le flag est true → nouveau comportement activé

**Comment activer progressivement un flag :**

```
Étape 1 : Activer pour les tenants internes DocAI uniquement (test réel)
Étape 2 : Activer pour 10% des tenants aléatoirement (canary release)
Étape 3 : Surveiller les métriques Grafana 48h
Étape 4 : Si pas d'anomalie → 50% des tenants
Étape 5 : Si pas d'anomalie → 100% des tenants
Étape 6 : Supprimer le flag du code (nettoyage technique)
```

**Kill switch — procédure d'urgence :**
Si un module cause des incidents en production :
1. Aller dans Unleash → désactiver le flag du module
2. Effet immédiat — aucun redéploiement nécessaire
3. Tous les tenants basculent vers le comportement par défaut
4. Ouvrir un incident dans #incidents-production Slack
5. Corriger le bug → réactiver progressivement

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-FF-001 | Chaque nouvelle fonctionnalité majeure est protégée par un Feature Flag | MUST |
| BR-FF-002 | Le flag `billing.enabled` est false en DEV et STAGING par défaut | MUST |
| BR-FF-003 | Un flag inutilisé depuis > 3 mois est supprimé du code (dette technique) | MUST |
| BR-FF-004 | Le kill switch `maintenance.mode` est testé avant chaque release production | MUST |
| BR-FF-005 | Unleash est déployé en haute disponibilité (2 instances minimum) | SHOULD |

**Intégration dans les modules :**
- Module 7 Billing → `billing.enabled` flag (déjà partiellement défini comme BILLING_ENABLED)
- Module 3 Fraude → `fraud.v2.enabled` pour tester le nouveau scoring
- Module 5.4 → `dashboard.search.enabled` pour la recherche full-text
- Tous les modules → `maintenance.mode` kill switch global

### Definition of Done — CI/CD (Setup initial)

- [ ] Dépôt GitHub créé avec protections branches (develop, main)
- [ ] SonarCloud configuré : organisation, projet importé, token généré
- [ ] Quality Gate DocAI configuré (seuils section précédente)
- [ ] Analyse automatique SonarCloud **désactivée** (GitHub Actions la lance)
- [ ] GitHub Container Registry activé sur le dépôt
- [ ] GitHub Environments créés : `staging` et `production` avec approbateurs
- [ ] Tous les secrets GitHub configurés
- [ ] `dependabot.yml` configuré (mises à jour Maven hebdomadaires)
- [ ] GitHub Pages activé (`gh-pages` branch)
- [ ] Manifestes Kubernetes créés et validés : deployment.yaml (RollingUpdate BR-K8S-001), hpa.yaml (BR-K8S-004), configmap.yaml, ingress.yaml (TLS BR-K8S-005)
- [ ] Zero-downtime validé : déploiement pendant charge simulée → 0 requête en erreur (maxUnavailable=0 vérifié)
- [ ] Health checks configurés : liveness, readiness, startup probes (BR-HC-001) — tester en arrêtant MongoDB
- [ ] S3HealthIndicator personnalisé implémenté et testé (BR-HC-003)
- [ ] Secrets AWS Secrets Manager CSI Driver configuré (BR-K8S-003)
- [ ] Terraform modules créés et `terraform plan` valide en staging (BR-TF-001)
- [ ] Test "happy path" pipeline de bout en bout réussi
- [ ] Seeding DEV exécuté : 3 tenants + 10 utilisateurs + documents exemples disponibles (BR-SEED-001)
- [ ] Connexion admin@acme-corp.test validée après seeding

---

---

# PARTIE 2 — COMMONS (avant tout module métier)

> **Ordre obligatoire :** Les 7 commons doivent être implémentés et testés avant de démarrer le Module 0.
>
> **Pourquoi en premier ?**
> - Module 0 utilise `commons-multitenancy`, `commons-api`, `commons-audit`
> - Module 1 utilise `commons-outbox`, `commons-quota`, `commons-testing`
> - Si les commons ne sont pas créés avant, chaque développeur réimplémente la même plomberie → code dupliqué dès le premier sprint
>
> **Les commons sont extraits de l'Annexe D et constituent une librairie Maven partagée `fr.docai:docai-commons`.**


## 0.D — Composants Réutilisables (Commons)

> **Référence complète :** Voir Annexe D pour les interfaces et spécifications de chaque commons.

**Ordre d'implémentation des commons :**

| # | Commons | Utilisé par | Durée |
|---|---------|-------------|-------|
| 1 | **commons-multitenancy** | Module 0, tous les adapters MongoDB | 2 jours |
| 2 | **commons-api** | Module 0, tous les controllers REST | 1 jour |
| 3 | **commons-audit** | Module 0, tous les use cases sensibles | 1 jour |
| 4 | **commons-outbox** | Module 1, tous les publishers Kafka | 2 jours |
| 5 | **commons-quota** | Module 1, Module 7 Billing | 1 jour |
| 6 | **commons-kafka** | Module 1, tous les consumers Kafka | 2 jours |
| 7 | **commons-testing** | Tous les tests d'intégration | 1 jour |

**Total commons : ~10 jours (2 semaines)**

**Comment implémenter chaque commons :**
1. Créer le module Maven `docai-commons-{nom}` dans le POM parent
2. Implémenter les interfaces et classes définies dans Annexe D
3. Écrire les tests unitaires (couverture ≥ 90% — domaine critique)
4. Publier dans le repository Maven local
5. Ajouter la dépendance dans les modules qui en ont besoin

**Référence ADR applicables aux commons :**


---

### **Interfaces Java des Commons — Signatures Obligatoires**

> **Où :** À implémenter dans `docai-commons` avant tout module métier. Ces interfaces sont le contrat entre les modules — elles ne changent jamais sans migration.

---

#### commons-multitenancy — Interfaces

```java
// TenantContext — Holder ThreadLocal du tenant courant
public final class TenantContext {
    public static void set(String tenantId);          // Appelé par TenantJwtFilter
    public static String get();                        // Lance TenantNotSetException si vide
    public static Optional<String> getOptional();      // Version safe (pas d'exception)
    public static void clear();                        // Appelé en finally dans le filtre
}

// TenantNotSetException — Exception domaine si tenant absent
public class TenantNotSetException extends RuntimeException {
    public TenantNotSetException(String endpoint);
}

// IdentityProviderPort — Abstraction Keycloak
public interface IdentityProviderPort {
    UserIdentity createUser(CreateUserCommand command);
    void assignRole(String userId, String tenantId, UserRole role);
    void revokeUser(String userId, String tenantId);
    void changePassword(String userId, String newPassword);
    TokenPair authenticate(String email, String password);
    TokenPair refreshToken(String refreshToken);
    void revokeRefreshToken(String refreshToken);
    void blacklistJwt(String jti, Instant expiresAt);
    boolean isJwtBlacklisted(String jti);
}

// TokenBlacklistPort — Valkey JWT blacklist
public interface TokenBlacklistPort {
    void blacklist(String jti, Duration ttl);
    boolean isBlacklisted(String jti);
}
```

---

#### commons-api — Interfaces

```java
// ApiResponse — Enveloppe standard de réponse
public record ApiResponse<T>(
    T data,
    PageMetadata page           // null si non paginé
) {
    public static <T> ApiResponse<T> of(T data);
    public static <T> ApiResponse<T> paginated(T data, PageMetadata page);
}

// PageMetadata — Métadonnées de pagination
public record PageMetadata(
    int number,                  // Page courante (commence à 0)
    int size,                    // Taille de la page
    long totalElements,          // Total des éléments
    int totalPages,              // Total des pages
    boolean first,               // Première page ?
    boolean last                 // Dernière page ?
) {}

// ProblemDetail — Réponse d'erreur RFC 7807
public record ProblemDetail(
    String type,                 // URL du type d'erreur
    String title,                // Titre court
    int status,                  // Code HTTP
    String detail,               // Message détaillé lisible
    String instance,             // Endpoint qui a levé l'erreur
    String traceId,              // Pour corrélation logs
    Instant timestamp            // Horodatage
) {}

// IdempotencyPort — Déduplication par clé
public interface IdempotencyPort {
    boolean tryAcquire(String idempotencyKey, Duration ttl);  // true si nouveau
    Optional<String> getCachedResponse(String idempotencyKey);
    void cacheResponse(String idempotencyKey, String response, Duration ttl);
}
```

---

#### commons-outbox — Interfaces

```java
// OutboxMessage — Event à publier via Kafka
public record OutboxMessage(
    UUID id,
    String aggregateType,        // "Document", "Tenant", "Subscription"
    String aggregateId,          // ID de l'aggregate
    String eventType,            // "DocumentUploaded", "DocumentClassified"
    String payload,              // JSON sérialisé de l'event
    String tenantId,
    String partitionKey,         // = documentId pour pipeline, tenantId sinon
    Instant createdAt,
    OutboxStatus status          // PENDING, PUBLISHED, FAILED
) {}

// OutboxStatus — Enum
public enum OutboxStatus { PENDING, PUBLISHED, FAILED }

// OutboxRepository — Persistance des outbox events
public interface OutboxRepository {
    void save(OutboxMessage message);
    List<OutboxMessage> findPending(int batchSize);        // Ordered by createdAt ASC
    void markPublished(UUID messageId);
    void markFailed(UUID messageId, String reason, int attempts);
    void deletePublishedOlderThan(Instant before);         // Nettoyage quotidien
}

// OutboxEventPublisher — Interface use case → outbox
public interface OutboxEventPublisher {
    void publish(String aggregateType, String aggregateId,
                 String eventType, Object payload, String tenantId,
                 String partitionKey);
}
```

---

#### commons-audit — Interfaces

```java
// AuditEvent — Event d'audit immuable
public record AuditEvent(
    UUID id,
    String tenantId,
    String userId,               // Masqué dans les logs mais stocké en clair
    String action,               // "DOCUMENT_UPLOADED", "FRAUD_DECISION_MADE"
    String resourceType,         // "Document", "Subscription"
    String resourceId,
    Map<String, Object> metadata,// Contexte additionnel (avant/après pour corrections)
    Instant occurredAt,
    String ipAddress,
    boolean isSupportAccess      // true si action de l'équipe support
) {}

// AuditPort — Enregistrement d'audit
public interface AuditPort {
    void record(AuditEvent event);                         // Async — ne bloque pas
    List<AuditEvent> findByTenant(String tenantId,
                                   Pageable pageable);
    List<AuditEvent> findByDocument(String documentId);
}

// @Audited — Annotation AOP
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();             // "DOCUMENT_UPLOADED"
    String resourceType();       // "Document"
    // resourceId extrait automatiquement du retour de méthode ou du premier paramètre
}
```

---

#### commons-quota — Interfaces

```java
// QuotaStatus — Résultat de la vérification quota
public enum QuotaStatus { ALLOWED, QUOTA_WARNING_80, QUOTA_WARNING_95, QUOTA_EXCEEDED }

// QuotaCheckResult — Résultat détaillé
public record QuotaCheckResult(
    QuotaStatus status,
    long currentUsage,           // Documents traités ce mois
    long limit,                  // Quota inclus dans le plan
    long remaining,              // Documents restants
    Instant resetAt              // Date de réinitialisation (1er du mois suivant)
) {}

// QuotaPort — Gestion des quotas
public interface QuotaPort {
    // Vérifie ET incrémente atomiquement (script Lua — ADR-001)
    QuotaCheckResult checkAndConsume(String tenantId, int amount);
    // Lecture seule sans incrément
    QuotaCheckResult getCurrentUsage(String tenantId);
    // Réinitialisation mensuelle (job planifié)
    void reset(String tenantId);
}

// @QuotaProtected — Annotation AOP
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuotaProtected {
    int amount() default 1;      // Nombre d'unités consommées par appel
    String resource() default "documents";
}
```

---

#### commons-kafka — Interfaces

```java
// ResilientKafkaConsumer — Consumer résilient (à étendre)
public abstract class ResilientKafkaConsumer<T> {
    // À implémenter dans chaque consumer
    public abstract void handle(T event, KafkaConsumerContext context);

    // Fourni par le commons — ne pas surcharger
    protected final void processWithIdempotence(ConsumerRecord<String, T> record);
    protected final void sendToDlq(ConsumerRecord<String, T> record, Exception cause);
    protected final boolean isAlreadyProcessed(String offsetKey);  // Vérifie Valkey
    protected final void markAsProcessed(String offsetKey);         // Écrit dans Valkey
}

// KafkaConsumerContext — Contexte du message Kafka
public record KafkaConsumerContext(
    String tenantId,             // Extrait des headers Kafka
    String correlationId,
    String traceId,
    int attempt                  // Numéro de tentative (1, 2, 3)
) {}

// KafkaEventPublisher — Producteur via Outbox (ADR-002)
public interface KafkaEventPublisher {
    // Clé partition = partitionKey (documentId pour pipeline)
    void publishViaOutbox(String topic, String partitionKey,
                          Object payload, String tenantId);
}

// JitterTtl — Utilitaire TTL avec jitter (ADR-003)
public final class JitterTtl {
    // TTL avec variation aléatoire ±10%
    public static Duration withJitter(Duration baseTtl);
    // TTL fixe sans jitter (pour rate limiting et idempotence)
    public static Duration fixed(Duration ttl);
}
```

---

#### commons-testing — Interfaces

```java
// AbstractIntegrationTest — Base pour tous les tests d'intégration
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {
    // Conteneurs partagés (mode reuse — ADR-008)
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0")
        .withReuse(true);
    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0")
        .withReuse(true);
    @Container
    static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:8")
        .withReuse(true);
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3"))
        .withServices(S3)
        .withReuse(true);

    // @DynamicPropertySource injecte automatiquement les URLs
}

// Builders — Pattern Test Data Builder
public class DocumentTestBuilder {
    public static DocumentTestBuilder aDocument();
    public DocumentTestBuilder withTenantId(String tenantId);
    public DocumentTestBuilder withStatus(DocumentStatus status);
    public DocumentTestBuilder withType(DocumentType type);
    public DocumentTestBuilder withFraudScore(int score);
    public Document build();
}

// ExternalApiStubs — WireMock pour les APIs externes
public class ExternalApiStubs {
    public static void stubInseeSuccess(WireMockServer server, String siret,
                                         String raisonSociale);
    public static void stubInseeNotFound(WireMockServer server, String siret);
    public static void stubInseeTimeout(WireMockServer server);
    public static void stubBanSuccess(WireMockServer server, String address,
                                       double lat, double lon);
    public static void stubLlmSuccess(WireMockServer server, String response);
    public static void stubLlmRateLimit(WireMockServer server);
    public static void stubLlmTimeout(WireMockServer server);
}
```


- ADR-001 → commons-quota (script Lua atomique)
- ADR-002 → commons-kafka (clé partition = documentId)
- ADR-003 → commons-kafka (withJitter TTL)
- ADR-006 → commons-multitenancy (cache JWKS)
- ADR-008 → commons-testing (reuse TestContainers)


Ces composants peuvent être extraits dans un artifact Maven partagé `fr.docai:docai-commons` et réutilisés dans n'importe quel futur SaaS Java.

| Module | Contenu | Réutilisabilité |
|--------|---------|-----------------|
| `commons-multitenancy` | `TenantContext`, `TenantJwtFilter`, `MongoTenantFilter` | Tout SaaS multi-tenant Spring Boot |
| `commons-outbox` | `OutboxMessage`, `OutboxRepository`, `OutboxRelay` | Tout service Kafka avec garantie de livraison |
| `commons-api` | `ApiResponse<T>`, `ProblemDetail` RFC 7807, `GlobalExceptionHandler`, `IdempotencyFilter` | Toute API REST Spring Boot |
| `commons-audit` | `AuditEvent`, `AuditPort`, annotation `@Audited` AOP | Tout SaaS finance, santé, légal |
| `commons-quota` | `QuotaPort`, `QuotaStatus`, annotation `@QuotaProtected` AOP | Tout SaaS avec plans et quotas |
| `commons-kafka` | `ResilientKafkaConsumer`, `OutboxKafkaProducer` avec retry + DLQ + tracing | Tout service event-driven |
| `commons-testing` | Test Data Builders, `AbstractIntegrationTest`, `ExternalApiStubs` WireMock | Tous les projets |

---

---

# PARTIE 3 — FONDATIONS MÉTIER

> **Ordre obligatoire :**
> 1. **Module 0 — Sécurité & Multi-Tenancy** (avec Login, Inscription, Gestion équipe, RGPD)
>
> **Le Module 0 doit être 100% terminé et validé avant de démarrer le Module 1.**
> Tous les endpoints des modules suivants dépendent du TenantJwtFilter et du GlobalExceptionHandler définis ici.

---

## Module 0 — Sécurité & Multi-Tenancy (Fondation transversale)

> **À implémenter EN PREMIER, avant tout endpoint métier.**  
> Si vous codez 3 modules sans cette fondation, vous devrez tout reprendre : endpoints, requêtes MongoDB, tests, gestion des erreurs.

**Durée estimée :** 2 semaines

### Pourquoi en premier ?

Chaque endpoint est automatiquement protégé dès sa création. Chaque requête MongoDB est automatiquement filtrée par tenant. Les use cases reçoivent déjà le contexte utilisateur et tenant. En l'ajoutant après, c'est le double du travail.

### Configuration Keycloak

**Realm DocAI :**
- Realm : `docai`
- Clients : `docai-backend` (confidential, client_credentials) · `docai-frontend` (public, PKCE)
- Durée JWT : 15 minutes · Refresh token : 8 heures
- PKCE activé pour le client frontend

**Rôles RBAC :**

| Rôle | Permissions |
|------|------------|
| `TENANT_ADMIN` | Gestion totale du tenant, API keys, webhooks, quotas |
| `ANALYST` | Upload, consultation, correction manuelle extractions |
| `VIEWER` | Lecture seule sur tous les documents du tenant |
| `FRAUD_REVIEWER` | Queue de révision fraude, décisions APPROVED/REJECTED |
| `SYSTEM` | Communication inter-services (client_credentials flow) |

**Claims JWT obligatoires :**
```json
{
  "sub": "usr-123",
  "email": "alice@acme.com",
  "tenant_id": "acme-corp",
  "roles": ["ANALYST"],
  "exp": 1748000000
}
```

### Business Rules Sécurité

| ID | Règle |
|----|-------|
| BR-SEC-001 | Tout endpoint requiert un JWT valide avec claim `tenant_id` |
| BR-SEC-002 | Isolation totale des données par `tenant_id` (filtre MongoDB systématique) |
| BR-SEC-003 | API Keys hashées SHA-256 + sel — jamais stockées en clair |
| BR-SEC-004 | Tout accès est audité : userId, tenantId, action, IP, timestamp |
| BR-SEC-005 | Inputs validés et sanitisés via Jakarta Validation avant traitement |
| BR-SEC-006 | Security headers : CSP, HSTS, X-Frame-Options, X-Content-Type-Options |
| BR-SEC-007 | Aucun secret dans les logs — PII masqués (`****`) |
| BR-SEC-008 | `.env` dans `.gitignore` — vérification automatique en CI (git-secrets) |

### Architecture Hexagonale — Module 0

> ---
> ### ⚠️ ADR-006 — Cache JWKS Keycloak local (OBLIGATOIRE ici)
>
> **Pourquoi :** Si Keycloak est indisponible et que les clés publiques ne sont pas en cache, Spring Security ne peut plus valider les JWT → tous les utilisateurs connectés sont bloqués après 15 min (durée de vie du JWT).
>
> **Comment configurer dans `SecurityConfig` Spring :**
>
> Spring Security OAuth2 Resource Server supporte nativement le cache JWKS.
>
> Configuration à appliquer :
> - `jwk-set-uri` : URL du endpoint JWKS Keycloak
> - Cache TTL : 1 heure
> - Refresh automatique : toutes les 30 minutes (avant expiration du cache)
> - En cas d'échec refresh : conserver le cache existant (fail-open sur le cache)
>
> En production uniquement :
> - Keycloak déployé en 2 instances derrière un load balancer (manifeste Kubernetes)
> - Alerte Grafana si Keycloak indisponible > 30 secondes
>
> **Test obligatoire :** Arrêter Keycloak en local (`docker compose stop keycloak`) → les requêtes avec un JWT valide existant doivent continuer à fonctionner pendant au moins 1 heure.
>
> **Référence complète :** Annexe E — ADR-006
> ---

**TenantContext — Holder ThreadLocal :**
```
TenantJwtFilter (Adapter IN REST)
  └─ Extrait tenant_id du JWT
  └─ Appelle TenantContext.set(tenantId)
  └─ Execute la chaîne de filtres
  └─ TenantContext.clear() en finally (évite les fuites entre requêtes)

DocumentMongoAdapter (Adapter OUT MongoDB)
  └─ Lit TenantContext.get()
  └─ Injecte automatiquement { tenantId: currentTenant } dans CHAQUE requête
```

**Flux de sécurité complet :**
```
Client → [JWT Header] → TenantJwtFilter → Spring Security → TenantContext.set()
      → Controller → Use Case → DocumentMongoAdapter → MongoDB (filtre tenantId auto)
```

**Outbound Ports :**
- `PORT-OUT-SEC-001` — `AuditPort` — enregistrement immuable de chaque action

**Adapters :**
- `TenantJwtFilter` — extraction `tenant_id` du claim JWT, alimentation TenantContext
- `KeycloakJwtAuthConverter` — mapping rôles Keycloak → `GrantedAuthority` Spring
- `MongoTenantFilter` — intercepteur Spring Data, injecte `tenantId` automatiquement
- `AuditMongoAdapter` — écriture dans collection `audit_entries` (append-only)

### Rate Limiting — Bucket4j + Valkey

| Niveau | Limite | Fenêtre | Réponse |
|--------|--------|---------|---------|
| Par tenant (plan Starter) | 100 req | 1 min | HTTP 429 + Retry-After |
| Par tenant (plan Pro) | 1 000 req | 1 min | HTTP 429 + Retry-After |
| Par IP (anti-abus global) | 30 req | 1 min | HTTP 429 |
| Quota mensuel Starter | 500 docs | 30 jours | HTTP 429 + message quota |
| Quota mensuel Pro | 10 000 docs | 30 jours | HTTP 429 + message quota |

### Format d'erreur API — RFC 7807 Problem Details

**Toutes les erreurs de l'API respectent ce format :**
```json
{
  "type": "https://api.docai.fr/errors/quota-exceeded",
  "title": "Quota Exceeded",
  "status": 429,
  "detail": "Your plan allows 500 documents/month. Current usage: 500.",
  "instance": "/v1/documents",
  "traceId": "abc-123-xyz",
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Catalogue des codes erreur :**

| Code erreur | HTTP | Situation |
|-------------|------|-----------|
| `DOC-001` | 400 | Document invalide (type non supporté, taille dépassée) |
| `DOC-002` | 409 | Document déjà soumis (idempotency key utilisée) |
| `DOC-003` | 404 | Document non trouvé pour ce tenant |
| `EXT-001` | 422 | Extraction échouée (score confiance insuffisant) |
| `FRD-001` | 200 | Document rejeté (score fraude critique) |
| `QUOTA-001` | 429 | Quota mensuel dépassé |
| `RATE-001` | 429 | Rate limit dépassé |
| `AUTH-001` | 401 | JWT absent ou invalide |
| `AUTH-002` | 403 | Rôle insuffisant pour cette action |

**`GlobalExceptionHandler` Spring — `@RestControllerAdvice` :**
- `DomainException` → ProblemDetail avec code erreur métier
- `ConstraintViolationException` → HTTP 400
- `AccessDeniedException` → HTTP 403
- Toute autre exception → HTTP 500 (sans stack trace exposée en production)

### Scénarios BDD — Sécurité

```gherkin
Feature: Sécurité et isolation multi-tenant

  Scenario: Accès sans JWT — HTTP 401
    Given un endpoint protégé "/v1/documents"
    When la requête est envoyée sans header Authorization
    Then la réponse est HTTP 401
    And le corps contient "type": "AUTH-001"

  Scenario: Isolation tenant — accès croisé impossible
    Given l'utilisateur alice du tenant "acme-corp" a soumis le document "doc-001"
    And l'utilisateur bob du tenant "beta-corp" est authentifié
    When bob tente d'accéder à "GET /v1/documents/doc-001"
    Then la réponse est HTTP 404
    And aucune information sur le document de acme-corp n'est révélée

  Scenario: Rôle insuffisant — HTTP 403
    Given l'utilisateur carol a le rôle VIEWER
    When carol tente de soumettre "POST /v1/documents"
    Then la réponse est HTTP 403
    And le corps contient "type": "AUTH-002"

  Scenario: Quota dépassé — HTTP 429
    Given le tenant "acme-corp" a un plan Starter (500 docs/mois)
    And "acme-corp" a déjà traité 500 documents ce mois
    When "acme-corp" tente de soumettre un nouveau document
    Then la réponse est HTTP 429
    And le corps contient la date de réinitialisation du quota
```

---

### Phase 0.1 — Inscription Tenant (création du compte entreprise)

**Objectif :** Permettre à une nouvelle entreprise de s'inscrire seule, obtenir automatiquement son accès `TENANT_ADMIN`, puis inviter et gérer son équipe — sans aucune intervention manuelle de l'équipe DocAI.

**Durée estimée :** 1 semaine

#### Flow 1 — Inscription d'un nouveau tenant (automatique)

Quand une entreprise remplit le formulaire d'inscription, le système crée tout automatiquement. Personne n'intervient manuellement.

```
Formulaire d'inscription
  ├── Nom entreprise  : ACME Corp
  ├── Email           : alice@acme.com
  ├── Mot de passe    : ********
  └── Plan choisi     : Starter
          │
          ▼
Le système fait automatiquement :
  1. Génère tenant_id = "acme-corp" (slug unique depuis le nom)
  2. Crée l'utilisateur alice dans Keycloak
  3. Attribue le rôle TENANT_ADMIN à alice automatiquement
  4. Initialise la configuration tenant en MongoDB (plan, quota)
  5. Crée le préfixe S3 : acme-corp/
  6. Envoie l'email de bienvenue à alice
          │
          ▼
Alice peut se connecter immédiatement
```

#### Business Rules — Inscription

| ID | Règle | Priorité |
|----|-------|---------|
| BR-ONB-001 | L'email doit être unique dans tout le système | MUST |
| BR-ONB-002 | Le nom d'entreprise génère un `tenant_id` slug unique (ex: "ACME Corp" → "acme-corp") | MUST |
| BR-ONB-003 | Si le slug existe déjà, un suffixe numérique est ajouté ("acme-corp-2") | MUST |
| BR-ONB-004 | Le rôle `TENANT_ADMIN` est attribué automatiquement au souscripteur | MUST |
| BR-ONB-005 | Un email de bienvenue est envoyé dans les 60 secondes après l'inscription | MUST |
| BR-ONB-006 | L'email de bienvenue contient un lien de vérification valable 24h | MUST |
| BR-ONB-007 | Tant que l'email n'est pas vérifié, l'accès est limité (pas d'upload) | SHOULD |
| BR-ONB-008 | L'inscription est accessible sans JWT — endpoint public | MUST |

#### Use Case — UC-ONB-001 — S'inscrire

| Étape | Description |
|-------|-------------|
| 1 | Réception des données d'inscription (email, mot de passe, nom entreprise, plan) |
| 2 | Validation format email et robustesse mot de passe |
| 3 | Vérification unicité email dans Keycloak |
| 4 | Génération `tenant_id` slug unique |
| 5 | Création utilisateur dans Keycloak + attribution rôle `TENANT_ADMIN` |
| 6 | Création configuration tenant dans MongoDB (plan, quota initial) |
| 7 | Initialisation préfixe Amazon S3 |
| 8 | Publication event `TenantCreated` via Outbox |
| 9 | Envoi email de bienvenue avec lien de vérification |
| 10 | Retour HTTP 201 avec `tenantId` et instructions de connexion |

#### Scénarios BDD — Inscription

```gherkin
Feature: Inscription d'un nouveau tenant

  Scenario: Inscription réussie
    Given un formulaire avec email "alice@acme.com", nom "ACME Corp", plan Starter
    When alice soumet le formulaire d'inscription
    Then le tenant "acme-corp" est créé
    And alice reçoit le rôle TENANT_ADMIN automatiquement
    And un email de bienvenue est envoyé à "alice@acme.com"
    And la réponse est HTTP 201 avec le tenantId "acme-corp"

  Scenario: Email déjà utilisé
    Given l'email "alice@acme.com" est déjà enregistré
    When une nouvelle inscription arrive avec ce même email
    Then la réponse est HTTP 409
    And le message indique que l'email est déjà utilisé
    And aucun tenant n'est créé

  Scenario: Nom d'entreprise déjà pris — suffixe automatique
    Given le tenant "acme-corp" existe déjà
    When une nouvelle entreprise "ACME Corp" s'inscrit
    Then le tenant_id généré est "acme-corp-2"
    And l'inscription réussit normalement
```

#### Endpoints — Inscription

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/v1/public/signup` | ❌ Public | Créer un compte tenant |
| GET | `/v1/public/verify-email?token=xxx` | ❌ Public | Vérifier l'adresse email |
| POST | `/v1/public/resend-verification` | ❌ Public | Renvoyer l'email de vérification |

---


---

### Phase 0.2 — Login, Logout & Gestion de Session

**Objectif :** Permettre à tout utilisateur d'un tenant (TENANT_ADMIN, ANALYST, VIEWER, FRAUD_REVIEWER) de se connecter, obtenir son JWT, le renouveler automatiquement et se déconnecter de façon sécurisée.

**Durée estimée :** 3 jours (inclus dans la semaine du Module 0)

> **Important :** Le login/logout concerne **tous les utilisateurs** du tenant, pas seulement le TENANT_ADMIN. C'est un flow quotidien utilisé par toute l'équipe.

---

#### Flow Login

```
alice entre email + mot de passe
        │
        ▼
POST /v1/public/auth/login
  body: { email, password }
        │
        ▼
DocAI appelle Keycloak (authentification)
        │
        ├── Credentials valides
        │       ▼
        │   Keycloak retourne :
        │     ├── access_token  (JWT valide 15 min)
        │     ├── refresh_token (valide 8 heures)
        │     └── expires_in
        │
        └── Credentials invalides
                ▼
            HTTP 401 — "Email ou mot de passe incorrect"
            (message volontairement vague — sécurité)
```

#### Flow Logout

```
alice clique "Se déconnecter"
        │
        ▼
POST /v1/auth/logout
  header: Authorization: Bearer {JWT}
  body: { refresh_token }
        │
        ▼
DocAI invalide le refresh_token dans Keycloak
DocAI ajoute le JWT en liste noire Valkey (TTL = durée restante du JWT)
        │
        ▼
alice ne peut plus utiliser ni le JWT ni le refresh_token
Toute requête avec l'ancien JWT → HTTP 401
```

#### Flow Refresh Token

```
JWT expiré (15 min écoulées)
        │
        ▼
POST /v1/auth/refresh
  body: { refresh_token }
        │
        ├── Refresh token valide
        │       ▼
        │   Nouveau JWT retourné (15 min)
        │   Nouveau refresh_token retourné (rotation)
        │
        └── Refresh token invalide ou expiré
                ▼
            HTTP 401 — alice doit se re-connecter
```

#### Flow Mot de passe oublié

```
alice clique "Mot de passe oublié"
        │
        ▼
POST /v1/public/auth/forgot-password
  body: { email }
        │
        ▼
Email envoyé avec lien de réinitialisation (token UUID, TTL 1 heure)
HTTP 200 toujours retourné (même si email inexistant — sécurité)
        │
        ▼
alice clique le lien → choisit un nouveau mot de passe
POST /v1/public/auth/reset-password
  body: { token, newPassword }
        │
        ├── Token valide → mot de passe mis à jour dans Keycloak
        │                   Email de confirmation envoyé
        └── Token expiré ou invalide → HTTP 410 Gone
```

#### Business Rules — Login / Logout

| ID | Règle | Priorité |
|----|-------|---------|
| BR-AUTH-001 | Le login retourne un JWT (15 min) + refresh token (8h) | MUST |
| BR-AUTH-002 | Le logout invalide le refresh token dans Keycloak ET blackliste le JWT dans Valkey | MUST |
| BR-AUTH-003 | Un JWT blacklisté est rejeté même s'il n'est pas encore expiré | MUST |
| BR-AUTH-004 | Le refresh token est à usage unique — rotation à chaque renouvellement | MUST |
| BR-AUTH-005 | Après 5 tentatives de login échouées, le compte est bloqué 15 min | MUST |
| BR-AUTH-006 | Le message d'erreur login est identique pour email inconnu et mot de passe incorrect | MUST |
| BR-AUTH-007 | Le lien de réinitialisation mot de passe est valable 1 heure | MUST |
| BR-AUTH-008 | Un lien de réinitialisation ne peut être utilisé qu'une seule fois | MUST |
| BR-AUTH-009 | L'email de réinitialisation est envoyé même si l'email n'existe pas (sécurité anti-énumération) | MUST |
| BR-AUTH-010 | Le nouveau mot de passe doit contenir au minimum 8 caractères, 1 majuscule, 1 chiffre | MUST |

#### Scénarios BDD — Login / Logout

```gherkin
Feature: Login, Logout et gestion de session

  Scenario: Login réussi
    Given alice@acme.com est un utilisateur actif avec mot de passe valide
    When alice envoie ses credentials sur "POST /v1/public/auth/login"
    Then la réponse est HTTP 200
    And un access_token JWT valide 15 min est retourné
    And un refresh_token valide 8h est retourné
    And le JWT contient les claims : tenant_id, roles, sub, exp

  Scenario: Login échoué — mauvais mot de passe
    Given alice@acme.com existe dans le système
    When alice envoie un mauvais mot de passe
    Then la réponse est HTTP 401
    And le message est "Email ou mot de passe incorrect"
    And aucun token n'est retourné

  Scenario: Blocage après 5 tentatives échouées
    Given alice a échoué 4 fois consécutives
    When alice échoue une 5ème fois
    Then le compte est bloqué 15 minutes
    And la réponse est HTTP 429 avec "Compte temporairement bloqué"

  Scenario: Logout — JWT invalidé immédiatement
    Given alice est connectée avec un JWT valide
    When alice appelle "POST /v1/auth/logout"
    Then la réponse est HTTP 200
    And le refresh_token est révoqué dans Keycloak
    And le JWT est ajouté en liste noire Valkey
    And toute requête suivante avec ce JWT retourne HTTP 401

  Scenario: Refresh token — renouvellement automatique
    Given le JWT d'alice a expiré (15 min écoulées)
    And alice possède un refresh_token valide
    When alice appelle "POST /v1/auth/refresh" avec le refresh_token
    Then un nouveau JWT est retourné (15 min)
    And un nouveau refresh_token est retourné (rotation)
    And l'ancien refresh_token est invalidé

  Scenario: Mot de passe oublié
    Given alice a oublié son mot de passe
    When alice soumet "POST /v1/public/auth/forgot-password" avec son email
    Then la réponse est HTTP 200 (même si email inexistant)
    And un email de réinitialisation est envoyé (TTL 1 heure)
    And alice clique le lien et choisit un nouveau mot de passe
    And alice peut se reconnecter avec le nouveau mot de passe
```

#### Architecture Hexagonale — Phase 0.2

**Domain Model :**
- `JwtBlacklist` — Value Object (tokenId, expiresAt) — stocké Valkey
- `PasswordResetToken` — Value Object (token UUID, email, expiresAt, used)
- `UserLoggedIn`, `UserLoggedOut`, `PasswordResetRequested` — Domain Events

**Inbound Ports :**
- `PORT-IN-AUTH-001` — `LoginUseCase`
- `PORT-IN-AUTH-002` — `LogoutUseCase`
- `PORT-IN-AUTH-003` — `RefreshTokenUseCase`
- `PORT-IN-AUTH-004` — `ForgotPasswordUseCase`
- `PORT-IN-AUTH-005` — `ResetPasswordUseCase`

**Outbound Ports :**
- `PORT-OUT-AUTH-001` — `IdentityProviderPort` → **commons-multitenancy** `KeycloakIdentityAdapter`
- `PORT-OUT-AUTH-002` — `TokenBlacklistPort` → **commons-multitenancy** `ValkeyTokenBlacklistAdapter`
- `PORT-OUT-AUTH-003` — `EmailNotificationPort` → `AmazonSesEmailAdapter`
- `PORT-OUT-AUTH-004` — `PasswordResetTokenRepositoryPort` → MongoDB

**Endpoints :**

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/v1/public/auth/login` | ❌ Public | Connexion email + mot de passe |
| POST | `/v1/auth/logout` | ✅ JWT | Déconnexion + invalidation tokens |
| POST | `/v1/auth/refresh` | ❌ Public | Renouveler le JWT |
| POST | `/v1/public/auth/forgot-password` | ❌ Public | Demander réinitialisation |
| POST | `/v1/public/auth/reset-password` | ❌ Public | Réinitialiser le mot de passe |

**Commons utilisés :**
- `commons-multitenancy` → `KeycloakIdentityAdapter`, `ValkeyTokenBlacklistAdapter`
- `commons-api` → `GlobalExceptionHandler` pour les erreurs 401/429
- `commons-audit` → `@Audited` sur LoginUseCase et LogoutUseCase

**Emails transactionnels — Phase 0.2 :**

| Déclencheur | Destinataire | Contenu |
|-------------|-------------|---------|
| Mot de passe oublié | Utilisateur | Lien de réinitialisation (1 heure) |
| Mot de passe modifié | Utilisateur | Confirmation changement + date/heure/IP |
| Compte bloqué (5 tentatives) | Utilisateur | Notification + durée blocage |

#### Definition of Done — Phase 0.2

- [ ] Login testé : credentials valides → JWT + refresh token retournés
- [ ] Login testé : credentials invalides → HTTP 401, message générique
- [ ] Blocage compte après 5 tentatives testé (TTL 15 min Valkey)
- [ ] Logout testé : JWT blacklisté dans Valkey → HTTP 401 sur requête suivante
- [ ] Refresh token testé : rotation à chaque renouvellement
- [ ] Refresh token expiré testé → HTTP 401
- [ ] Flow mot de passe oublié testé de bout en bout
- [ ] Token réinitialisation usage unique testé (2ème utilisation → HTTP 410)
- [ ] Email envoyé même si email inconnu (anti-énumération)
- [ ] AuditEntry créé pour chaque login et logout (userId, IP, timestamp)

### Phase 0.3 — Gestion Équipe (par le TENANT_ADMIN)

Une fois connectée, Alice peut inviter ses collègues et leur attribuer les bons rôles. C'est elle qui gère son équipe, sans passer par DocAI.

```
Alice (TENANT_ADMIN) connectée
  │
  ├── Invite bob@acme.com   → choisit le rôle ANALYST
  │       └── Bob reçoit un email d'invitation avec lien d'activation
  │
  ├── Invite carol@acme.com → choisit le rôle VIEWER
  │       └── Carol reçoit un email d'invitation
  │
  └── Révoque l'accès de dave@acme.com
          └── Dave ne peut plus se connecter immédiatement
```

#### Business Rules — Gestion équipe

| ID | Règle | Priorité |
|----|-------|---------|
| BR-ONB-010 | Seul un `TENANT_ADMIN` peut inviter des utilisateurs dans son tenant | MUST |
| BR-ONB-011 | Un `TENANT_ADMIN` ne peut inviter que dans son propre tenant (isolation) | MUST |
| BR-ONB-012 | Les rôles assignables par le TENANT_ADMIN : ANALYST, VIEWER, FRAUD_REVIEWER | MUST |
| BR-ONB-013 | Le rôle TENANT_ADMIN ne peut être attribué que par l'équipe DocAI (rôle SYSTEM) | MUST |
| BR-ONB-014 | L'invitation est valable 7 jours — après expiration, renvoyer une nouvelle invitation | MUST |
| BR-ONB-015 | Un utilisateur peut avoir un seul rôle actif par tenant | MUST |
| BR-ONB-016 | La révocation d'accès est effective immédiatement (invalidation JWT Keycloak) | MUST |
| BR-ONB-017 | Le TENANT_ADMIN reçoit un email de confirmation à chaque invitation envoyée | SHOULD |

#### Use Case — UC-ONB-002 — Inviter un utilisateur

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN saisit l'email et le rôle du futur utilisateur |
| 2 | Vérification que l'email n'existe pas déjà dans ce tenant |
| 3 | Création de l'utilisateur dans Keycloak (sans mot de passe) |
| 4 | Attribution du rôle choisi dans Keycloak |
| 5 | Génération d'un token d'invitation (UUID, TTL 7 jours, stocké dans MongoDB) |
| 6 | Envoi email d'invitation avec lien d'activation contenant le token |
| 7 | Création AuditEntry : qui a invité qui, quel rôle, quand |

#### Use Case — UC-ONB-003 — Activer son compte (côté invité)

| Étape | Description |
|-------|-------------|
| 1 | Bob clique sur le lien d'invitation reçu par email |
| 2 | Vérification validité du token (non expiré, non déjà utilisé) |
| 3 | Bob choisit son mot de passe |
| 4 | Activation du compte dans Keycloak |
| 5 | Token d'invitation marqué comme utilisé (non réutilisable) |
| 6 | Bob peut se connecter avec son email + mot de passe |

#### Scénarios BDD — Gestion équipe

```gherkin
Feature: Gestion de l'équipe par le TENANT_ADMIN

  Scenario: Invitation réussie d'un collègue
    Given alice est connectée avec le rôle TENANT_ADMIN du tenant "acme-corp"
    When alice invite "bob@acme.com" avec le rôle ANALYST
    Then un email d'invitation est envoyé à "bob@acme.com"
    And le lien d'invitation est valable 7 jours
    And un AuditEntry est créé (alice a invité bob, rôle ANALYST)

  Scenario: Bob active son compte via le lien d'invitation
    Given bob a reçu un email d'invitation avec un token valide
    When bob clique sur le lien et choisit un mot de passe
    Then le compte de bob est activé dans Keycloak
    And bob peut se connecter avec le rôle ANALYST
    And le token d'invitation est marqué comme utilisé

  Scenario: Lien d'invitation expiré
    Given un token d'invitation créé il y a 8 jours
    When bob tente d'activer son compte avec ce token
    Then la réponse est HTTP 410 (Gone)
    And le message indique que l'invitation a expiré
    And bob doit demander une nouvelle invitation

  Scenario: Révocation d'accès immédiate
    Given bob est connecté avec un JWT valide
    When alice révoque l'accès de bob depuis le dashboard
    Then bob ne peut plus appeler aucun endpoint protégé
    And la réponse est HTTP 401 sur la prochaine requête de bob
    And un AuditEntry est créé (alice a révoqué bob)
```

#### Endpoints — Gestion équipe

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| POST | `/v1/team/invite` | `TENANT_ADMIN` | Inviter un utilisateur |
| GET | `/v1/team/users` | `TENANT_ADMIN` | Lister les utilisateurs du tenant |
| PUT | `/v1/team/users/{userId}/role` | `TENANT_ADMIN` | Changer le rôle d'un utilisateur |
| DELETE | `/v1/team/users/{userId}` | `TENANT_ADMIN` | Révoquer l'accès |
| GET | `/v1/public/accept-invitation?token=xxx` | ❌ Public | Activer son compte via invitation |

#### Emails transactionnels — Module 0

| Déclencheur | Destinataire | Contenu |
|-------------|-------------|---------|
| Inscription réussie | Souscripteur | Bienvenue + lien de vérification email + guide démarrage rapide |
| Email vérifié | Souscripteur | Confirmation + accès complet activé |
| Invitation envoyée | Invité | Lien d'activation (7 jours) + nom du tenant + rôle attribué |
| Invitation acceptée | TENANT_ADMIN | Confirmation que l'invité a activé son compte |
| Révocation d'accès | Utilisateur révoqué | Notification que son accès a été retiré |

**Provider email recommandé :** Amazon SES (déjà dans l'écosystème AWS utilisé pour S3)

#### Architecture Hexagonale — Phase 0.1

**Domain Model :**
- `Tenant` — Aggregate (tenantId, companyName, plan, status, createdAt)
- `TenantUser` — Aggregate (userId, tenantId, email, role, status)
- `InvitationToken` — Value Object (token UUID, expiresAt, used)
- `TenantCreated`, `UserInvited`, `UserActivated`, `UserRevoked` — Domain Events

**Inbound Ports :**
- `PORT-IN-ONB-001` — `SignupTenantUseCase`
- `PORT-IN-ONB-002` — `InviteUserUseCase`
- `PORT-IN-ONB-003` — `ActivateUserUseCase`
- `PORT-IN-ONB-004` — `RevokeUserUseCase`
- `PORT-IN-ONB-005` — `VerifyEmailUseCase`

**Outbound Ports :**
- `PORT-OUT-ONB-001` — `IdentityProviderPort` (Keycloak)
- `PORT-OUT-ONB-002` — `EmailNotificationPort` (Amazon SES)
- `PORT-OUT-ONB-003` — `TenantRepositoryPort` (MongoDB)
- `PORT-OUT-ONB-004` — `InvitationTokenRepositoryPort` (MongoDB)

**Adapters :**
- `KeycloakIdentityAdapter` — création utilisateurs + attribution rôles via API Admin Keycloak
- `AmazonSesEmailAdapter` — envoi emails transactionnels via Amazon SES
- `TenantMongoAdapter` — persistance tenant + configurations
- `InvitationTokenMongoAdapter` — persistance tokens d'invitation avec TTL index 7 jours

**Collection MongoDB `invitation_tokens` :**

| Champ | Type | Description |
|-------|------|-------------|
| `_id` | UUID | Token unique |
| `tenantId` | String | Tenant concerné |
| `invitedEmail` | String | Email de l'invité |
| `role` | String | Rôle attribué |
| `invitedBy` | String | UserId de l'invitant |
| `expiresAt` | DateTime | TTL index MongoDB 7 jours |
| `used` | Boolean | Token déjà utilisé |
| `usedAt` | DateTime | Date d'utilisation |

#### Definition of Done — Phase 0.1

- [ ] Inscription complète testée de bout en bout (signup → email → vérification → connexion)
- [ ] Template `welcome.html` rendu correctement en HTML et texte brut (BR-EMAIL-001)
- [ ] Template `email-verification.html` envoyé en mode sandbox SES (BR-EMAIL-004)
- [ ] `tenant_id` slug unique généré et testé (collision → suffixe numérique)
- [ ] Rôle `TENANT_ADMIN` attribué automatiquement via Keycloak Admin API
- [ ] Email de bienvenue envoyé via Amazon SES (testé avec WireMock)
- [ ] Flow invitation testé : invite → email → activation → connexion
- [ ] Révocation effective immédiatement testée (JWT précédent invalide)
- [ ] Tokens d'invitation expirés rejetés (TTL MongoDB vérifié)
- [ ] Isolation tenant : un TENANT_ADMIN ne peut pas gérer un autre tenant
- [ ] AuditEntry créé pour chaque action d'invitation ou révocation

---


---

### Phase 0.4 — Profil Utilisateur & Sécurité du Compte

**Objectif :** Permettre à chaque utilisateur de gérer son propre profil, changer son mot de passe, activer le 2FA et consulter son historique de connexion.

**Durée estimée :** 3 jours

---

#### 1. Changement de mot de passe (utilisateur connecté)

Différent du "mot de passe oublié" — ici l'utilisateur est **déjà connecté** et veut changer son mot de passe depuis son profil.

```
alice est connectée
  │
  ▼
PUT /v1/profile/password
  body: { currentPassword, newPassword, confirmPassword }
  │
  ├── currentPassword correct → mot de passe mis à jour dans Keycloak
  │     → Tous les refresh tokens existants invalidés (sécurité)
  │     → alice doit se re-connecter sur tous ses appareils
  │     → Email de confirmation envoyé
  │
  └── currentPassword incorrect → HTTP 401
```

#### Business Rules — Changement mot de passe

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PRF-001 | L'utilisateur doit fournir son mot de passe actuel pour en changer | MUST |
| BR-PRF-002 | Le nouveau mot de passe doit être différent de l'ancien | MUST |
| BR-PRF-003 | Après changement, tous les refresh tokens existants sont invalidés | MUST |
| BR-PRF-004 | Un email de confirmation est envoyé après le changement | MUST |
| BR-PRF-005 | Le nouveau mot de passe doit respecter les règles de complexité (8 car, 1 maj, 1 chiffre) | MUST |

---

#### 2. Profil utilisateur (consultation & modification)

Chaque utilisateur peut consulter et modifier son propre profil.

```
GET /v1/profile
  → Retourne : nom, prénom, email, rôle, tenant, avatar URL, dateInscription

PUT /v1/profile
  body: { firstName, lastName }
  → Mise à jour dans Keycloak
  → Seuls nom et prénom sont modifiables directement
  → L'email suit un flow séparé (vérification obligatoire)
```

#### Business Rules — Profil

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PRF-010 | Chaque utilisateur ne voit et ne modifie que son propre profil | MUST |
| BR-PRF-011 | Le rôle et le tenant_id sont en lecture seule (non modifiables par l'utilisateur) | MUST |
| BR-PRF-012 | L'avatar est stocké dans Amazon S3 (max 2MB, formats PNG/JPEG uniquement) | SHOULD |

---

#### 3. Changement d'email (avec vérification obligatoire)

Le changement d'email est un flow sécurisé — l'ancien email reste actif jusqu'à confirmation du nouveau.

```
alice veut changer son email
  │
  ▼
PUT /v1/profile/email
  body: { newEmail, currentPassword }
  │
  ├── currentPassword correct
  │     → Email de vérification envoyé sur le NOUVEL email
  │     → L'ANCIEN email reste actif (alice peut toujours se connecter)
  │     → Lien de confirmation valable 24h
  │
  └── currentPassword incorrect → HTTP 401

alice clique le lien de confirmation sur le nouvel email
  → Email mis à jour dans Keycloak
  → L'ancien email ne fonctionne plus
  → Email de notification envoyé sur l'ANCIEN email
    "Votre email a été modifié. Si ce n'est pas vous, contactez-nous."
```

#### Business Rules — Changement email

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PRF-020 | Le mot de passe actuel est requis pour changer l'email | MUST |
| BR-PRF-021 | L'ancien email reste actif jusqu'à confirmation du nouveau | MUST |
| BR-PRF-022 | Le lien de confirmation est valable 24h | MUST |
| BR-PRF-023 | Un email de notification est envoyé sur l'ancien email après changement | MUST |
| BR-PRF-024 | Le nouvel email ne doit pas déjà exister dans le système | MUST |

---

#### 4. Historique de connexion

Chaque utilisateur peut consulter ses dernières connexions pour détecter un accès suspect.

```
GET /v1/profile/login-history
  → Retourne les 20 dernières connexions :
    ├── Date et heure
    ├── Adresse IP
    ├── Appareil / navigateur (User-Agent)
    └── Statut : SUCCÈS ou ÉCHEC
```

#### Business Rules — Historique connexion

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PRF-030 | Les 20 dernières connexions (succès et échecs) sont conservées | MUST |
| BR-PRF-031 | L'IP et le User-Agent sont enregistrés à chaque tentative de login | MUST |
| BR-PRF-032 | L'historique n'est visible que par l'utilisateur lui-même | MUST |
| BR-PRF-033 | Les entrées d'historique sont conservées 90 jours | SHOULD |

**Collection MongoDB `login_history` :**

| Champ | Type | Description |
|-------|------|-------------|
| `_id` | UUID | Identifiant de la connexion |
| `userId` | String | Utilisateur concerné |
| `tenantId` | String | Tenant concerné |
| `ipAddress` | String | Adresse IP |
| `userAgent` | String | Navigateur / appareil |
| `status` | String | SUCCESS ou FAILED |
| `occurredAt` | DateTime | Horodatage (TTL index 90 jours) |

---

#### 5. Double Authentification — 2FA

Keycloak supporte le 2FA nativement (TOTP — Google Authenticator, Authy). Il faut le configurer et le rendre **obligatoire pour le plan Enterprise**.

```
alice active le 2FA depuis son profil
  │
  ▼
POST /v1/profile/2fa/enable
  → Keycloak génère un QR Code TOTP
  → alice scanne avec Google Authenticator
  → alice entre le premier code pour confirmer
  → 2FA activé

À chaque login suivant :
  email + mot de passe → code TOTP demandé → JWT retourné
```

#### Business Rules — 2FA

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PRF-040 | Le 2FA est optionnel pour les plans Starter et Pro | SHOULD |
| BR-PRF-041 | Le 2FA est **obligatoire** pour le plan Enterprise | MUST |
| BR-PRF-042 | Le TENANT_ADMIN peut rendre le 2FA obligatoire pour tous ses utilisateurs | SHOULD |
| BR-PRF-043 | Des codes de récupération (backup codes) sont générés lors de l'activation | MUST |
| BR-PRF-044 | La désactivation du 2FA requiert le mot de passe actuel | MUST |

---

#### Scénarios BDD — Profil & Sécurité

```gherkin
Feature: Profil utilisateur et sécurité du compte

  Scenario: Changement de mot de passe réussi
    Given alice est connectée avec un JWT valide
    When alice envoie PUT /v1/profile/password avec son mot de passe actuel correct
    And un nouveau mot de passe valide
    Then le mot de passe est mis à jour dans Keycloak
    And tous les refresh tokens d'alice sont invalidés
    And un email de confirmation est envoyé à alice

  Scenario: Changement email — vérification obligatoire
    Given alice veut changer son email vers "alice-new@acme.com"
    When alice envoie PUT /v1/profile/email avec son mot de passe correct
    Then un email de vérification est envoyé à "alice-new@acme.com"
    And l'ancien email "alice@acme.com" reste fonctionnel
    When alice clique le lien de confirmation
    Then le nouvel email est activé dans Keycloak
    And un email de notification est envoyé à "alice@acme.com"

  Scenario: Historique connexion — détection accès suspect
    Given alice consulte GET /v1/profile/login-history
    Then les 20 dernières connexions sont retournées
    And chaque entrée contient date, IP, appareil, statut

  Scenario: Activation 2FA
    Given alice active le 2FA depuis son profil
    When alice scanne le QR Code et entre le premier code TOTP valide
    Then le 2FA est activé sur son compte
    And des codes de récupération sont générés et affichés une seule fois
    When alice se reconnecte
    Then email + mot de passe + code TOTP sont requis
```

#### Endpoints — Profil & Sécurité

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/v1/profile` | ✅ JWT | Consulter son profil |
| PUT | `/v1/profile` | ✅ JWT | Modifier nom et prénom |
| PUT | `/v1/profile/password` | ✅ JWT | Changer son mot de passe |
| PUT | `/v1/profile/email` | ✅ JWT | Demander changement email |
| GET | `/v1/profile/login-history` | ✅ JWT | Historique connexions |
| POST | `/v1/profile/2fa/enable` | ✅ JWT | Activer le 2FA |
| POST | `/v1/profile/2fa/disable` | ✅ JWT | Désactiver le 2FA |
| GET | `/v1/profile/2fa/backup-codes` | ✅ JWT | Récupérer les codes backup |

**Commons utilisés :**
- `commons-multitenancy` → isolation tenant sur toutes les requêtes profil
- `commons-audit` → `@Audited` sur changement mot de passe, email, 2FA
- `commons-api` → `ProblemDetail` pour erreurs profil

#### Definition of Done — Phase 0.4

- [ ] Changement mot de passe testé (mot de passe actuel requis, refresh tokens invalidés)
- [ ] Changement email testé (vérification nouveau email, ancien reste actif jusqu'à confirmation)
- [ ] Historique connexion persisté à chaque login (IP, User-Agent, statut)
- [ ] 2FA activable via TOTP (QR Code Keycloak)
- [ ] 2FA obligatoire pour plan Enterprise testé
- [ ] Codes de récupération 2FA générés et fonctionnels
- [ ] Email de notification envoyé sur changement mot de passe et email


> ---
> ### Références Annexes — Module 0 — Sécurité & Multi-Tenancy
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `TenantJwtFilter (Adapter IN — filtre Spring Security)`
> - `KeycloakIdentityAdapter (Adapter OUT — implémente IdentityProviderPort)`
> - `AuditMongoAdapter (Adapter OUT — implémente AuditPort)`
> - `SignupTenantUseCase (Application — implémente SignupTenantPort)`
> - `TenantId (Value Object — validation constructeur, UUID)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Keycloak déployé en 2 instances minimum (ADR-006) — vérifier manifeste Kubernetes
> - Cache JWKS Spring Security configuré TTL 1h refresh 30 min (ADR-006)
> - TLS 1.3 activé sur tous les endpoints — vérifier certificat
> - Headers sécurité HTTP configurés : CSP, HSTS, X-Frame-Options
> - Rate limiting testé aux limites Starter et Pro — vérifier Bucket4j + Valkey
> - Audit trail immuable — tenter une modification et vérifier le rejet
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-multitenancy → TenantContext, TenantJwtFilter, MongoTenantFilter, ValkeyTokenBlacklistAdapter**
> - **commons-api → GlobalExceptionHandler (RFC 7807), ProblemDetail, catalogue erreurs AUTH-001/AUTH-002**
> - **commons-audit → @Audited sur LoginUseCase, LogoutUseCase, InviteUserUseCase, RevokeUserUseCase**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Chaque login réussi : userId, tenantId, IP, userAgent
> - WARN — Tentative login échouée : userId masqué, IP, tentative N/5
> - ERROR — JWT invalide ou expiré : traceId, endpoint tenté
> - INFO — Invitation envoyée : invitedBy, role, tenantId
> - INFO — Révocation accès : revokedBy, revokedUserId, tenantId
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Module 0 doit être 100% terminé et validé avant de démarrer tout autre module.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---


---

### **Accès Support Client aux Données Tenant (RGPD)**

> **Où :** Cette section définit comment l'équipe support DocAI peut accéder aux données d'un tenant pour reproduire un bug ou résoudre un incident, sans violer l'isolation des données ni le RGPD.

**Le problème :**
L'isolation multi-tenant est totale — un tenant ne voit pas les données d'un autre. Mais si un client appelle le support avec un bug, l'équipe support a besoin d'accéder à ses données pour le reproduire. Comment faire sans violer l'architecture ?

**Décision retenue : Impersonation auditée avec consentement**

L'équipe support ne peut accéder aux données d'un tenant que si les 3 conditions suivantes sont remplies :
1. **Consentement explicite** du TENANT_ADMIN (email ou ticket de support)
2. **Durée limitée** : accès valable maximum 2 heures
3. **Audit complet** : chaque action de l'équipe support est loggée et visible par le tenant

**Comment fonctionne l'impersonation :**

```
1. Client ouvre un ticket support avec problème
2. Agent support demande l'autorisation au TENANT_ADMIN
   → Email automatique : "L'agent [nom] demande un accès à vos données
     pendant 2 heures pour résoudre le ticket #12345. Acceptez-vous ?"
3. TENANT_ADMIN clique "Accepter"
4. Token d'impersonation généré (UUID, TTL 2h, scope READ_ONLY)
5. Agent support utilise le token → accède aux données en lecture seule
6. Chaque action loggée dans audit_entries avec flag support=true
7. TENANT_ADMIN peut voir toutes les actions de l'agent dans son dashboard
8. Token expiré → accès automatiquement révoqué
```

**Business Rules — Support Client :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-SUP-001 | Aucun accès support sans consentement explicite du TENANT_ADMIN | MUST |
| BR-SUP-002 | L'accès support est en lecture seule — jamais de modification possible | MUST |
| BR-SUP-003 | Le token d'impersonation expire automatiquement après 2 heures | MUST |
| BR-SUP-004 | Chaque action de l'agent support est loggée avec flag support=true | MUST |
| BR-SUP-005 | Le TENANT_ADMIN peut voir l'historique complet des accès support | MUST |
| BR-SUP-006 | Le TENANT_ADMIN peut révoquer l'accès support à tout moment | MUST |
| BR-SUP-007 | L'équipe support ne peut pas s'impersonner sans ticket de support ouvert | MUST |

**Endpoints Support :**

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| POST | `/v1/support/impersonation-request` | SYSTEM | Demander l'autorisation au TENANT_ADMIN |
| POST | `/v1/support/impersonation-approve?token=xxx` | TENANT_ADMIN | Approuver l'accès support |
| GET | `/v1/support/audit-trail` | TENANT_ADMIN | Historique des accès support |
| DELETE | `/v1/support/impersonation/{id}` | TENANT_ADMIN | Révoquer l'accès immédiatement |

**Logs obligatoires pour l'accès support :**
- INFO — Accès support demandé : tenantId, agentId, ticketId
- INFO — Accès support approuvé : tenantId, agentId, expiresAt
- INFO — Action support : tenantId, agentId, action, resourceId, support=true
- INFO — Accès support révoqué/expiré : tenantId, agentId, reason

**Impact RGPD :**
- Chaque accès support est une donnée personnelle → conservé 5 ans dans audit_entries
- Le client peut demander l'historique complet des accès support (droit d'accès RGPD)
- Mentionné dans le DPA (Data Processing Agreement) fourni aux clients

### Definition of Done — Module 0 (complet)

- [ ] `TenantJwtFilter` implémenté et testé (JWT valide, JWT invalide, JWT sans tenant_id)
- [ ] Isolation tenant testée : lecture croisée entre 2 tenants → HTTP 404
- [ ] `GlobalExceptionHandler` retourne RFC 7807 pour toutes les exceptions connues
- [ ] Rate limiting testé aux limites de chaque plan (Starter, Pro)
- [ ] `AuditMongoAdapter` : chaque action sensible génère une entrée immuable
- [ ] ArchUnit : `docai-domain` ne contient aucune import Spring Security
- [ ] Keycloak : realm `docai` importé, 5 utilisateurs de test créés, 5 rôles présents
- [ ] Inscription tenant automatique testée de bout en bout
- [ ] Gestion équipe testée (invitation, activation, révocation)
- [ ] Emails transactionnels testés (bienvenue, invitation, révocation)
- [ ] `GET /actuator/health` → HTTP 200
- [ ] `GET /v1/documents` sans JWT → HTTP 401
- [ ] `GET /v1/documents` avec JWT alice → HTTP 200 (liste vide)
- [ ] Accès support : impersonation avec consentement, lecture seule, audit trail visible
- [ ] Token impersonation expire automatiquement après 2 heures

---

---

## Module 0.3 — RGPD & Privacy

> **Bounded Context :** Garantir la conformité légale au Règlement Général sur la Protection des Données pour tous les tenants européens.

**Durée estimée :** 1 semaine

> **Pourquoi c'est obligatoire ?**  
> DocAI traite des données personnelles sensibles : CNI, passeports, ordonnances médicales, bulletins de salaire. En tant que SaaS B2B européen, DocAI est **responsable de traitement** (ou sous-traitant selon les cas). La non-conformité expose à des amendes CNIL jusqu'à **4% du CA mondial**.

---

### Principes RGPD appliqués à DocAI

| Principe RGPD | Application dans DocAI |
|--------------|----------------------|
| **Minimisation des données** | Ne stocker que les champs extraits nécessaires, pas le document brut au-delà de la durée utile |
| **Limitation de la conservation** | Durée de rétention configurable par tenant (défaut 90 jours) |
| **Droit à l'effacement** | Endpoint de suppression complète des données d'un tenant |
| **Portabilité** | Export JSON de toutes les données d'un tenant |
| **Transparence** | Audit trail consultable de tous les accès aux données |
| **Intégrité & Confidentialité** | Chiffrement des données PII au repos |

---

### Business Rules — RGPD

| ID | Règle | Priorité |
|----|-------|---------|
| BR-RGP-001 | Les documents sont supprimés de S3 automatiquement après la durée de rétention configurée | MUST |
| BR-RGP-002 | La durée de rétention par défaut est 90 jours (configurable par tenant entre 30 et 365 jours) | MUST |
| BR-RGP-003 | Le droit à l'effacement supprime : document S3, extraction, analyse fraude, audit trail SAUF les entrées de traçabilité légale (factures, décisions fraude) | MUST |
| BR-RGP-004 | Une demande d'effacement génère un rapport de suppression consultable | MUST |
| BR-RGP-005 | Les données PII dans les extractions sont chiffrées au repos (MongoDB Field Level Encryption) | MUST |
| BR-RGP-006 | Toutes les données sont stockées en Europe (région AWS eu-west-3 Paris) | MUST |
| BR-RGP-007 | Le tenant peut exporter toutes ses données au format JSON (droit à la portabilité) | MUST |
| BR-RGP-008 | L'audit trail RGPD est immuable et conservé 5 ans (obligation légale) | MUST |
| BR-RGP-009 | En cas de résiliation, les données sont supprimées dans les 90 jours | MUST |
| BR-RGP-010 | Un DPA (Data Processing Agreement) est disponible et signable en ligne | SHOULD |

---

### Données PII identifiées dans DocAI

| Donnée | Collection | Niveau sensibilité | Protection |
|--------|-----------|-------------------|-----------|
| Nom, prénom | `extraction_results` | Élevé | Chiffrement Field Level |
| Date de naissance | `extraction_results` | Élevé | Chiffrement Field Level |
| Numéro CNI / Passeport | `extraction_results` | Très élevé | Chiffrement Field Level |
| IBAN | `extraction_results` | Élevé | Chiffrement Field Level |
| Adresse | `extraction_results` | Modéré | Chiffrement Field Level |
| Numéro RPPS médecin | `extraction_results` | Modéré | Chiffrement Field Level |
| Email (invitations) | `invitation_tokens` | Modéré | Hashé après activation |
| Fichier PDF/image original | Amazon S3 | Très élevé | Chiffrement S3 SSE-KMS |

---

### Durée de rétention

```
Document soumis
    │
    ▼
Traitement pipeline (classification, extraction, fraude)
    │
    ▼
Résultats disponibles dans le dashboard
    │
    ▼ (après durée de rétention configurée : défaut 90 jours)
    │
    ├── Fichier S3 supprimé (objet S3 supprimé définitivement)
    ├── ExtractionResult supprimé de MongoDB
    ├── Document metadata anonymisé (conservé pour statistiques)
    └── FraudAnalysis conservée anonymisée (traçabilité légale)
```

**Politique de rétention différenciée :**

| Type de donnée | Durée de rétention | Justification |
|---------------|-------------------|---------------|
| Fichiers PDF/images (S3) | Configurable 30–365 jours (défaut 90j) | Minimisation données |
| Résultats extraction | Même durée que le fichier | Cohérence |
| Analyse fraude | 5 ans anonymisée | Obligation légale, traçabilité |
| Audit trail | 5 ans | Obligation légale RGPD |
| Factures | 10 ans | Obligation comptable |

---

### Use Cases — RGPD

**UC-RGP-001 — Configurer la durée de rétention**

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN définit la durée de rétention (30 à 365 jours) |
| 2 | La configuration est persistée dans `tenant_configs` |
| 3 | Un job planifié quotidien supprime les documents dépassant cette durée |
| 4 | Chaque suppression génère un AuditEntry RGPD |

**UC-RGP-002 — Droit à l'effacement**

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN demande l'effacement via `DELETE /v1/rgpd/data` |
| 2 | Vérification que le tenant a le droit (pas de litige ou obligation légale en cours) |
| 3 | Suppression asynchrone : fichiers S3, extractions MongoDB, métadonnées document |
| 4 | Conservation des entrées d'audit anonymisées (obligation légale) |
| 5 | Rapport de suppression généré et envoyé par email |
| 6 | Réponse HTTP 202 (traitement asynchrone) |

**UC-RGP-003 — Export des données (portabilité)**

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN demande l'export via `POST /v1/rgpd/export` |
| 2 | Génération asynchrone d'un fichier JSON contenant toutes les données du tenant |
| 3 | Fichier déposé dans S3 avec lien de téléchargement signé (valable 24h) |
| 4 | Email envoyé au TENANT_ADMIN avec le lien de téléchargement |

---

### Scénarios BDD — RGPD

```gherkin
Feature: Conformité RGPD

  Scenario: Suppression automatique des documents expirés
    Given le tenant "acme-corp" a une durée de rétention de 90 jours
    And un document a été soumis il y a 91 jours
    When le job de rétention quotidien s'exécute
    Then le fichier PDF est supprimé d'Amazon S3
    And l'ExtractionResult est supprimé de MongoDB
    And un AuditEntry RGPD est créé "DOCUMENT_RETENTION_EXPIRED"
    And les statistiques du tenant ne montrent qu'un documentId anonymisé

  Scenario: Droit à l'effacement — suppression complète
    Given le tenant "beta-corp" demande l'effacement de ses données
    When "DELETE /v1/rgpd/data" est appelé par le TENANT_ADMIN
    Then la réponse est HTTP 202 (traitement asynchrone)
    And tous les fichiers S3 de "beta-corp" sont supprimés
    And tous les ExtractionResults de "beta-corp" sont supprimés
    And les AuditEntries sont anonymisées (userId masqué, contenu effacé)
    And un email de confirmation de suppression est envoyé

  Scenario: Export des données — portabilité
    Given le TENANT_ADMIN de "gamma-corp" demande un export
    When "POST /v1/rgpd/export" est appelé
    Then la réponse est HTTP 202
    And un fichier JSON est généré de façon asynchrone
    And un email avec un lien de téléchargement signé (24h) est envoyé
    And le fichier contient toutes les données de "gamma-corp" au format JSON

  Scenario: Données PII chiffrées au repos
    Given un document avec un numéro CNI "123456789012"
    When l'extraction est persistée dans MongoDB
    Then le champ "numeroDocument" est stocké chiffré (Field Level Encryption)
    And une lecture directe en base ne révèle pas la valeur en clair
    And seule l'application avec la clé de déchiffrement peut lire la valeur
```

---

### Architecture Hexagonale — Module 0.3

**Domain Model :**
- `RetentionPolicy` — Value Object (tenantId, retentionDays, effectiveFrom)
- `DeletionReport` — Aggregate (tenantId, requestedAt, status, itemsDeleted, completedAt)
- `DataExport` — Aggregate (tenantId, requestedAt, s3Key, expiresAt, status)
- `DocumentRetentionExpired`, `DataErasureRequested`, `DataExportReady` — Domain Events

**Inbound Ports :**
- `PORT-IN-RGP-001` — `ConfigureRetentionPolicyUseCase`
- `PORT-IN-RGP-002` — `RequestDataErasureUseCase`
- `PORT-IN-RGP-003` — `RequestDataExportUseCase`
- `PORT-IN-RGP-004` — `RunRetentionCleanupUseCase` (job planifié)

**Outbound Ports :**
- `PORT-OUT-RGP-001` — `DataErasurePort` (suppression S3 + MongoDB)
- `PORT-OUT-RGP-002` — `DataExportPort` (génération JSON + S3)
- `PORT-OUT-RGP-003` — `RetentionPolicyRepositoryPort`
- `PORT-OUT-RGP-004` — `RgpdAuditPort`

**Adapters :**
- `S3DataErasureAdapter` — suppression objets S3 (deleteObject)
- `MongoDataErasureAdapter` — suppression + anonymisation MongoDB
- `DataExportGeneratorAdapter` — génération JSON export, upload S3 signé
- `RetentionCleanupScheduler` — job planifié quotidien à 2h00 UTC

**Endpoints :**

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| GET | `/v1/rgpd/retention-policy` | `TENANT_ADMIN` | Consulter la politique de rétention |
| PUT | `/v1/rgpd/retention-policy` | `TENANT_ADMIN` | Configurer la durée de rétention |
| DELETE | `/v1/rgpd/data` | `TENANT_ADMIN` | Demander l'effacement complet |
| GET | `/v1/rgpd/deletion-reports` | `TENANT_ADMIN` | Historique des suppressions |
| POST | `/v1/rgpd/export` | `TENANT_ADMIN` | Demander un export de données |
| GET | `/v1/rgpd/exports` | `TENANT_ADMIN` | Historique des exports |

**NFR — RGPD :**

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-RGP-001 | Job de rétention : 100% des documents expirés supprimés dans les 24h | 100% |
| NFR-RGP-002 | Droit à l'effacement traité en moins de 72h (exigence RGPD) | 100% |
| NFR-RGP-003 | Export données disponible en moins de 24h | 100% |
| NFR-RGP-004 | Données stockées exclusivement en région EU (eu-west-3) | 100% |
| NFR-RGP-005 | Chiffrement S3 SSE-KMS activé sur le bucket de production | 100% |


---

### Suppression de compte utilisateur individuel (RGPD — droit à l'effacement individuel)

Différent de l'effacement du tenant complet — ici un **utilisateur individuel** demande la suppression de son propre compte, sans supprimer les données des autres utilisateurs du tenant.

```
bob veut supprimer son compte
  │
  ▼
DELETE /v1/profile/account
  body: { currentPassword, confirmationText: "SUPPRIMER MON COMPTE" }
  │
  ▼
Vérification mot de passe
  │
  ▼
Suppression asynchrone :
  → Compte désactivé immédiatement dans Keycloak
  → JWT invalidé
  → Données personnelles anonymisées dans MongoDB (nom, email → "Utilisateur supprimé")
  → Les documents traités par bob restent (appartiennent au tenant, pas à bob)
  → AuditEntries de bob anonymisées
  → Email de confirmation envoyé à l'ancien email de bob
```

#### Business Rules — Suppression compte individuel

| ID | Règle | Priorité |
|----|-------|---------|
| BR-RGP-020 | Un utilisateur peut supprimer son propre compte à tout moment | MUST |
| BR-RGP-021 | Le mot de passe actuel + confirmation textuelle sont requis | MUST |
| BR-RGP-022 | Le compte est désactivé immédiatement — le JWT est invalidé | MUST |
| BR-RGP-023 | Les données personnelles sont anonymisées (pas supprimées) pour conserver la traçabilité | MUST |
| BR-RGP-024 | Un TENANT_ADMIN ne peut pas supprimer son compte s'il est le seul admin du tenant | MUST |
| BR-RGP-025 | Le TENANT_ADMIN reçoit une notification quand un membre de son équipe supprime son compte | SHOULD |

**Endpoint :**

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| DELETE | `/v1/profile/account` | ✅ JWT | Supprimer son propre compte |

> ---
> ### ⚠️ ADR-005 — Chiffrement PII via AWS KMS (OBLIGATOIRE ici)
>
> **Pourquoi :** Sans rotation des clés, une compromission expose toutes les données PII depuis le début du projet. Les développeurs ne doivent jamais avoir accès aux clés de chiffrement.
>
> **Comment configurer dans ce module :**
>
> Champs PII à chiffrer obligatoirement dans `extraction_results` (MongoDB Field Level Encryption) :
> - `fields.nom`, `fields.prenom`
> - `fields.dateNaissance`
> - `fields.numeroDocument` (CNI, Passeport)
> - `fields.IBAN`
>
> Configuration obligatoire :
> 1. La clé maître est gérée dans AWS KMS (jamais en dur dans le code)
> 2. L'application accède à KMS via IAM Role — aucune clé dans les variables d'environnement
> 3. La rotation automatique annuelle est activée sur la clé KMS en production
> 4. CloudTrail audite chaque opération de chiffrement et déchiffrement
>
> **Ce qui est interdit :**
> - Stocker la clé dans `.env`, `application.yml` ou secrets GitHub
> - Accorder l'accès KMS à un utilisateur IAM humain (uniquement IAM Role pour l'application)
>
> **Test obligatoire :** Lire directement un document MongoDB sans passer par l'application → les champs PII doivent apparaître chiffrés (illisibles).
>
> **Référence complète :** Annexe E — ADR-005
> ---


> ---
> ### Références Annexes — Module 0.3 — RGPD & Privacy
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `RequestDataErasureUseCase (Application — implémente RequestDataErasurePort)`
> - `RunRetentionCleanupUseCase (Application — job planifié quotidien 2h UTC)`
> - `S3DataErasureAdapter (Adapter OUT — implémente DataErasurePort)`
> - `MongoDataErasureAdapter (Adapter OUT — anonymisation PII)`
> - `RetentionPolicy (Value Object — retentionDays, 30 à 365)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Chiffrement Field Level Encryption MongoDB activé sur tous les champs PII (ADR-005)
> - AWS KMS rotation automatique annuelle activée — vérifier dans la console AWS
> - Chiffrement S3 SSE-KMS activé sur le bucket de production
> - Données stockées exclusivement en eu-west-3 — vérifier la région S3 et MongoDB Atlas
> - Job de rétention testé avec des documents expirés réels
> - Test de restauration MongoDB effectué ce mois (voir Annexe F.1)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-audit → @Audited sur RequestDataErasureUseCase, RunRetentionCleanupUseCase (audit trail RGPD immuable 5 ans)**
> - **commons-multitenancy → isolation tenant sur toutes les suppressions et exports**
> - **commons-api → ProblemDetail pour erreurs RGPD**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Document supprimé (rétention expirée) : documentId, tenantId, s3Key — PAS le contenu
> - INFO — Demande effacement reçue : tenantId, requestedBy, scope
> - INFO — Export données généré : tenantId, s3ExportKey, expiresAt
> - ERROR — Échec suppression S3 : documentId, s3Key, raison
> - WARN — Document PII déchiffré : userId, documentId, action (audit trail déchiffrement)
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Vérifier que le chiffrement KMS est configuré AVANT de stocker le premier document PII en production.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Module 0.3

- [ ] Job de rétention quotidien testé (documents expirés supprimés de S3 + MongoDB)
- [ ] Droit à l'effacement testé de bout en bout (suppression asynchrone + email confirmation)
- [ ] Export données testé (JSON généré + lien S3 signé envoyé par email)
- [ ] Chiffrement Field Level Encryption MongoDB activé sur les champs PII identifiés
- [ ] Chiffrement S3 SSE-KMS activé sur le bucket (vérifié via AWS Console)
- [ ] Données stockées en eu-west-3 (vérifié en configuration)
- [ ] AuditEntries RGPD conservées 5 ans (TTL index MongoDB vérifié)
- [ ] Rapport de suppression généré et consultable
- [ ] Anonymisation des AuditEntries testée (PII masquées après effacement)

---

---

# PARTIE 4 — PIPELINE DE TRAITEMENT

> **Ordre obligatoire de développement :**
> 1. **Module 1 — Reconnaissance** (Upload + Classification)
> 2. **Module 2 — Extraction** (OCR + LLM + Validation)
> 3. **Module 3 — Détection Fraude** (Scoring + Révision)
> 4. **Module 4 — Orchestration & Résilience** (Pipeline Kafka + DLQ + Saga)
>
> **Chaque module dépend du précédent.** Un document doit pouvoir être uploadé et classifié avant de tester l'extraction.

---

## Module 1 — Reconnaissance de Documents

> **Bounded Context :** Réception et identification de tout document entrant dans le système.

### Phase 1.1 — Upload & Validation

**Objectif :** Permettre à un utilisateur authentifié de soumettre un document de façon fiable, idempotente, avec isolation par tenant et confirmation immédiate.

**Durée estimée :** 2 semaines

#### Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-REC-001 | Formats acceptés : PDF, PNG, JPEG, TIFF, WEBP | MUST |
| BR-REC-002 | Taille maximale : 20 MB par fichier | MUST |
| BR-REC-003 | Chaque soumission génère un `documentId` UUID v4 unique | MUST |
| BR-REC-004 | L'endpoint est idempotent via header `X-Idempotency-Key` (24h) | MUST |
| BR-REC-005 | Le fichier est stocké dans Amazon S3 avant tout traitement | MUST |
| BR-REC-006 | Le quota mensuel du tenant est vérifié avant stockage S3 | MUST |
| BR-REC-007 | Une réponse HTTP 201 avec `documentId` est renvoyée dès confirmation S3 | MUST |
| BR-REC-008 | Le traitement est asynchrone — l'utilisateur n'attend pas la fin | MUST |
| BR-REC-009 | L'event `DocumentUploaded` est publié via l'Outbox Pattern | MUST |

#### Use Cases

**UC-REC-001 — Soumettre un document** `MUST`

| Étape | Description |
|-------|-------------|
| 1 | Réception HTTP multipart/form-data |
| 2 | Vérification idempotence (Redis SETNX `X-Idempotency-Key`) |
| 3 | Validation format et taille fichier (Jakarta Validation) |
| 4 | Vérification quota mensuel tenant (Bucket4j + Valkey) |
| 5 | Calcul hash SHA-256 du contenu (détection doublons) |
| 6 | Upload Amazon S3 : `{tenantId}/{year}/{month}/{documentId}/{filename}` |
| 7 | Création aggregate `Document` (état `PENDING`) |
| 8 | Persistance Document + OutboxEvent dans la même transaction MongoDB |
| 9 | Retour HTTP 201 avec `documentId` et `status: PENDING` |

**UC-REC-002 — Consulter le statut d'un document** `MUST`
- `GET /v1/documents/{id}` → Document avec statut courant
- Vérification isolation tenant avant retour

**UC-REC-003 — Lister les documents du tenant** `MUST`
- `GET /v1/documents?status=CLASSIFIED&page=0&size=20`
- Read Model CQRS dédié (collection `document_summary_views`)
- Filtres : `status`, `type`, `riskLevel`, `dateFrom`, `dateTo`

#### Scénarios BDD

```gherkin
Feature: Upload et validation de documents

  Scenario: Soumission réussie d'une facture PDF
    Given un utilisateur ANALYST du tenant "acme-corp" authentifié
    And le quota mensuel de "acme-corp" n'est pas atteint
    When il soumet un fichier "facture-oct-2026.pdf" (2MB, PDF valide)
    Then la réponse est HTTP 201
    And un documentId UUID est retourné
    And le document est en état PENDING
    And le fichier est stocké dans Amazon S3
    And l'event "DocumentUploaded" est publié sur "docai.doc.uploaded"

  Scenario: Idempotence — double soumission avec même clé
    Given un document soumis avec X-Idempotency-Key "key-abc-123"
    When le même document est soumis à nouveau avec la même clé
    Then la réponse est HTTP 200 (pas 201)
    And le même documentId est retourné
    And aucun document supplémentaire n'est créé

  Scenario: Quota mensuel dépassé
    Given le tenant "beta-corp" a atteint sa limite de 500 documents
    When un utilisateur tente de soumettre un nouveau document
    Then la réponse est HTTP 429
    And le message indique la date de renouvellement du quota

  Scenario: Format non supporté
    Given un utilisateur soumet un fichier "document.docx"
    When la validation de format s'exécute
    Then la réponse est HTTP 400
    And le code erreur est "DOC-001"
```

#### Architecture Hexagonale

**Domain Model :**
- `Document` — Aggregate Root (documentId, tenantId, fileName, mimeType, s3Key, status, contentHash, idempotencyKey, uploadedAt, events[])
- `DocumentId` — Value Object (UUID, validation constructeur)
- `ContentHash` — Value Object (SHA-256, validation format)
- `DocumentStatus` — Enum (PENDING, CLASSIFIED, EXTRACTED, ANALYZED, COMPLETED, FAILED, NEEDS_REVIEW)
- `DocumentUploaded` — Domain Event

**Inbound Ports :**
- `PORT-IN-REC-001` — `SubmitDocumentUseCase`
- `PORT-IN-REC-002` — `GetDocumentStatusUseCase`
- `PORT-IN-REC-003` — `ListDocumentsUseCase`

**Outbound Ports :**
- `PORT-OUT-REC-001` — `DocumentRepositoryPort`
- `PORT-OUT-REC-002` — `StoragePort` (Amazon S3)
- `PORT-OUT-REC-003` — `EventPublisherPort` (Outbox)
- `PORT-OUT-REC-004` — `QuotaPort`
- `PORT-OUT-REC-005` — `IdempotencyPort`

**Adapters :**
- `DocumentController` — Spring MVC REST, multipart upload
- `DocumentMongoAdapter` — persistence + filtre tenant → **commons-multitenancy** (`MongoTenantFilter`)
- `AwsS3StorageAdapter` — AWS SDK v2, upload multipart, presigned URLs

> ---
> ### ⚠️ ADR-007 — Upload S3 multipart : AbortMultipartUpload (OBLIGATOIRE ici)
>
> **Pourquoi :** Si la connexion est coupée à 80% de l'upload, les parties déjà envoyées restent dans S3 — invisibles mais facturées indéfiniment.
>
> **Comment implémenter dans `AwsS3StorageAdapter.upload()` :**
>
> Structure obligatoire de la méthode upload :
> 1. Démarrer le multipart upload → obtenir l'`uploadId`
> 2. Dans un bloc `try` → uploader chaque partie séquentiellement
> 3. Dans le bloc `finally` → si une exception a été levée, appeler `AbortMultipartUpload(uploadId)` pour annuler immédiatement
> 4. Seulement si toutes les parties sont uploadées avec succès → appeler `CompleteMultipartUpload`
>
> La Lifecycle Rule S3 (configurée via Terraform) supprime automatiquement les uploads non finalisés après 24h comme filet de sécurité supplémentaire.
>
> **Test obligatoire :** Simuler une coupure réseau à mi-upload → vérifier qu'`AbortMultipartUpload` est appelé et qu'aucun objet partiel ne reste dans S3.
>
> **Référence complète :** Annexe E — ADR-007
> ---
- `OutboxEventPublisher` — écrit dans `outbox_events` MongoDB → **commons-outbox** (`OutboxRepository`)
- `ValkeyQuotaAdapter` — compteurs Lua atomiques → **commons-quota** (`QuotaPort`)

> ---
> ### ⚠️ ADR-001 — Quota atomique Valkey (OBLIGATOIRE ici)
>
> **Pourquoi :** Sans atomicité, 50 uploads simultanés peuvent lire le compteur avant qu'il soit incrémenté → le tenant dépasse son quota sans être bloqué.
>
> **Comment implémenter dans `ValkeyQuotaAdapter.checkAndConsume()` :**
>
> Le script Lua s'exécute en une seule opération atomique sur Valkey — aucun autre thread ne peut l'interrompre entre la lecture et l'écriture.
>
> Logique du script Lua (pseudocode) :
> 1. Lire la valeur courante du compteur `quota:{tenantId}:{yearMonth}`
> 2. Si valeur >= quota du plan → retourner `QUOTA_EXCEEDED` sans incrémenter
> 3. Sinon → incrémenter de 1 et retourner `ALLOWED` avec la nouvelle valeur
>
> Le TTL du compteur est fixé au 1er du mois suivant à minuit UTC (réinitialisation automatique).
>
> **Test obligatoire :** Lancer 100 threads simultanés sur un quota de 100 — le compteur final doit être exactement 100, jamais 101 ou plus.
>
> **Référence complète :** Annexe E — ADR-001
> ---
- `ValkeyIdempotencyAdapter` — SETNX + TTL 24h → **commons-api** (`IdempotencyFilter`)

**Commons utilisés dans ce module :**
- `commons-multitenancy` → isolation tenant automatique sur MongoDB
- `commons-outbox` → garantie publication Kafka via Outbox Pattern
- `commons-quota` → vérification et décrémentation quota mensuel
- `commons-api` → `ApiResponse<T>`, `ProblemDetail` RFC 7807, idempotence
- `commons-audit` → `@Audited` sur SubmitDocumentUseCase
- `commons-testing` → `DocumentTestBuilder`, `AbstractIntegrationTest`

**Kafka Events :**

| Event | Topic | Payload clés |
|-------|-------|-------------|
| `DocumentUploaded` | `docai.doc.uploaded` | documentId, tenantId, s3Key, mimeType, contentHash, uploadedAt |

**Endpoints exposés :**

| Méthode | Endpoint | Rôles | Description |
|---------|----------|-------|-------------|
| POST | `/v1/documents` | `ANALYST`, `TENANT_ADMIN` | Soumettre un document |
| GET | `/v1/documents/{id}` | `ANALYST`, `VIEWER`, `TENANT_ADMIN` | Statut + résultats |
| GET | `/v1/documents` | `ANALYST`, `VIEWER`, `TENANT_ADMIN` | Liste paginée (BR-PAG-001 — max 100 éléments, défaut 20) |

#### NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-REC-001 | Latence upload (API → S3 confirmé) | < 2s (P95) |
| NFR-REC-002 | Disponibilité endpoint upload | ≥ 99.9% |
| NFR-REC-003 | Zéro perte de document après HTTP 201 | 100% |
| NFR-REC-004 | Idempotence garantie | 24h |
| NFR-REC-005 | Uploads simultanés supportés par tenant | 50 req/s |


> ---
> ### Références Annexes — Module 1.1 — Upload & Validation
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `SubmitDocumentUseCase (Application — implémente SubmitDocumentPort)`
> - `DocumentController (Adapter IN REST — mapping DTO vers Command uniquement)`
> - `DocumentMongoAdapter (Adapter OUT — implémente DocumentRepositoryPort)`
> - `AwsS3StorageAdapter (Adapter OUT — implémente StoragePort, AbortMultipartUpload ADR-007)`
> - `Document (Aggregate Root — documentId, tenantId, status, contentHash)`
> - `DocumentId (Value Object — UUID v4, validation constructeur)`
> - `ContentHash (Value Object — SHA-256, validation format)`
> - `DocumentStatus (Enum — PENDING, CLASSIFIED, EXTRACTED, ANALYZED, COMPLETED, FAILED)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Bucket S3 de production configuré avec SSE-KMS et Lifecycle Rule multipart 24h (ADR-007)
> - Idempotence testée : double soumission avec même clé → HTTP 200, 1 seul document créé
> - Outbox Pattern testé : panne Kafka simulée → document publié à la reprise
> - Rate limiting testé à 100 req/min (Starter) et 1000 req/min (Pro)
> - Métrique docai_document_upload_total exposée dans Prometheus
> - Upload fichier 20MB testé — vérifier que AbortMultipartUpload est appelé en cas d'échec
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-multitenancy → MongoTenantFilter injecte tenantId automatiquement dans DocumentMongoAdapter**
> - **commons-outbox → OutboxRepository + OutboxRelay pour publication Kafka garantie (zéro perte)**
> - **commons-quota → @QuotaProtected + script Lua atomique Valkey — ADR-001 obligatoire ici**
> - **commons-api → IdempotencyFilter (header X-Idempotency-Key, TTL 24h Valkey)**
> - **commons-audit → @Audited sur SubmitDocumentUseCase**
> - **commons-testing → DocumentTestBuilder, AbstractIntegrationTest (reuse TestContainers — ADR-008)**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Document soumis : documentId, tenantId, mimeType, sizeBytes, s3Key
> - WARN — Quota 80% atteint lors de l'upload : tenantId, docsProcessed/docsIncluded
> - ERROR — Upload S3 échoué : documentId, tenantId, s3Key, raison (pas le contenu du fichier)
> - INFO — Outbox event publié : documentId, topic, partitionKey=documentId
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** ADR-001 (quota Lua) et ADR-007 (AbortMultipart) identifiés et compris avant de commencer.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

> ⚠️ **Migrations Mongock — première migration ici** — Respecter la convention `V{numero}_{module}_{description}`. Créer `V001_setup_documents_collection` avant tout code. Voir Section I.9 — Règles de Migration Mongock.

#### Definition of Done — Phase 1.1

- [ ] Tous les scénarios BDD passent (Cucumber + TestContainers)
- [ ] ArchUnit : aucune dépendance Spring/Mongo dans `docai-domain`
- [ ] Tests unitaires domaine ≥ 90% coverage
- [ ] Idempotence validée par test automatisé (double soumission)
- [ ] Outbox Pattern testé (panne Kafka simulée → event publié à la reprise)
- [ ] Rate limiting testé aux limites de chaque plan
- [ ] Upload Amazon S3 testé avec LocalStack TestContainers
- [ ] Budget AWS S3 configuré avec alerte dépassement 150% (Annexe C — Monitoring Coûts AWS)
- [ ] Module Terraform S3 appliqué en staging : Lifecycle Rule 24h (BR-TF-007), KMS (BR-TF-008), versioning activé
- [ ] Métrique `docai_document_upload_total` exposée dans Prometheus
- [ ] Documentation OpenAPI générée et validée pour les 3 endpoints

---

### Phase 1.2 — Classification Automatique par IA

**Objectif :** Identifier automatiquement le type de document via un modèle de vision, avec score de confiance, routing adapté et résilience si le modèle est indisponible.

**Durée estimée :** 3 semaines

#### Business Rules

| ID | Règle |
|----|-------|
| BR-REC-010 | La classification est déclenchée automatiquement à réception de `DocumentUploaded` |
| BR-REC-011 | Le score de confiance est un flottant entre 0.0 et 1.0 |
| BR-REC-012 | Score ≥ 0.85 → classification automatique acceptée |
| BR-REC-013 | Score 0.70–0.84 → classification acceptée + flag `LOW_CONFIDENCE` |
| BR-REC-014 | Score < 0.70 → état `NEEDS_REVIEW` + queue révision manuelle |
| BR-REC-015 | L'appel au modèle de vision est protégé par Circuit Breaker Resilience4j |
| BR-REC-016 | En cas d'échec après retry, document en `NEEDS_REVIEW` (fail-safe, jamais bloqué) |
| BR-REC-017 | L'event `DocumentClassified` inclut le type, le score et la version du modèle |

**Types de documents reconnus :**
`FACTURE`, `CNI`, `PASSEPORT`, `RIB`, `ORDONNANCE`, `BULLETIN_SALAIRE`, `CONTRAT`, `JUSTIFICATIF_DOMICILE`, `AUTRE`

#### Scénarios BDD

```gherkin
Feature: Classification automatique de documents

  Scenario: Classification réussie avec haute confiance
    Given un document en état PENDING sur le topic "docai.doc.uploaded"
    When le consumer reçoit l'event et appelle le modèle de vision
    And le modèle retourne type=FACTURE avec score=0.95
    Then le document passe en état CLASSIFIED
    And l'event "DocumentClassified" est publié sur "docai.doc.classified"
    And le pipeline d'extraction est déclenché

  Scenario: Classification avec faible confiance
    Given le modèle retourne score=0.55
    Then le document passe en état NEEDS_REVIEW
    And une entrée est créée dans la queue de révision manuelle
    And aucun pipeline d'extraction n'est déclenché

  Scenario: Circuit Breaker ouvert — fallback fail-safe
    Given le Circuit Breaker du ClassificationModel est en état OPEN
    When le consumer tente la classification
    Then aucun appel au modèle n'est effectué (fail-fast)
    And le document passe en état NEEDS_REVIEW
    And le motif "CIRCUIT_BREAKER_OPEN" est enregistré

  Scenario: Correction manuelle de classification
    Given un document en état NEEDS_REVIEW
    And l'utilisateur a le rôle ANALYST
    When il corrige le type vers "FACTURE" avec justification
    Then le type est mis à jour
    And un AuditEntry immuable est créé (userId, avant, après, timestamp)
    And l'event "ClassificationCorrected" est publié pour reprendre le pipeline
```

#### Architecture Hexagonale (ajouts)

**Domain Model :**
- `ConfidenceScore` — Value Object (Double 0.0–1.0, exception si hors bornes)
- `ClassificationResult` — Value Object (documentType, confidenceScore, modelVersion, classifiedAt)
- `DocumentClassified`, `DocumentNeedsReview`, `ClassificationCorrected` — Domain Events

**Inbound Ports :**
- `PORT-IN-REC-004` — `ClassifyDocumentUseCase`
- `PORT-IN-REC-005` — `CorrectClassificationUseCase`

**Outbound Ports :**
- `PORT-OUT-REC-006` — `ClassificationModelPort`

**Adapters :**
- `ClassificationKafkaConsumer` — consomme `docai.doc.uploaded`, commit manuel
- `VisionModelAdapter` — appel HTTP modèle vision IA, Resilience4j intégré
- `FallbackRuleBasedClassifier` — heuristiques si modèle indisponible

**Design Patterns :**
- **Strategy** — `VisionClassifierStrategy` vs `RuleBasedClassifierStrategy` (fallback)
- **Circuit Breaker** — Resilience4j seuil 50% / 10 calls
- **Retry** — 3 tentatives backoff exponentiel (1s, 2s, 4s)
- **Null Object** — `FallbackClassificationResult` si modèle totalement indisponible

#### NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-REC-006 | Latence classification (P95) | < 5s |
| NFR-REC-007 | Précision sur jeu de test labellisé | ≥ 92% |
| NFR-REC-008 | Disponibilité avec fallback actif | ≥ 99.5% |
| NFR-REC-009 | Taux documents routés en révision manuelle | < 8% |


> ---
> ### Références Annexes — Module 1.2 — Classification Automatique
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `ClassifyDocumentUseCase (Application — implémente ClassifyDocumentPort)`
> - `ClassificationKafkaConsumer (Adapter IN Kafka — étend ResilientKafkaConsumer)`
> - `VisionModelAdapter (Adapter OUT — implémente ClassificationModelPort + Resilience4j)`
> - `FallbackRuleBasedClassifier (Adapter OUT — Null Object Pattern si modèle indisponible)`
> - `ConfidenceScore (Value Object — Double 0.0-1.0, exception si hors bornes)`
> - `ClassificationResult (Value Object — documentType, confidenceScore, modelVersion)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Circuit Breaker VisionModel testé : CLOSED → OPEN → HALF_OPEN → CLOSED
> - Fallback testé : modèle indisponible → document en NEEDS_REVIEW (pas de blocage pipeline)
> - Consumer Kafka idempotent : même offset traité 2× → second ignoré silencieusement
> - Clé partition Kafka = documentId vérifiée dans Kafka UI (ADR-002)
> - Correction manuelle auditée : AuditEntry immuable avec userId, avant, après, timestamp
> - Métriques : docai_classification_duration_seconds, docai_classification_confidence_score
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer (retry exponentiel, DLQ auto, idempotence Valkey, tracing OpenTelemetry)**
> - **commons-outbox → OutboxKafkaProducer avec clé partition = documentId (ADR-002 obligatoire)**
> - **commons-multitenancy → filtre tenant automatique sur DocumentMongoAdapter**
> - **commons-audit → @Audited sur CorrectClassificationUseCase (correction manuelle traçable)**
> - **commons-testing → DocumentTestBuilder, AbstractIntegrationTest, stubs WireMock modèle vision**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Classification réussie : documentId, tenantId, type, score, modelVersion, durationMs
> - WARN — Score faible (< 0.85) : documentId, tenantId, score, action=NEEDS_REVIEW
> - WARN — Circuit Breaker OPEN : service=VisionModel, documentId, action=FALLBACK
> - INFO — Correction manuelle : documentId, tenantId, oldType, newType, correctedBy=[PII_MASKED]
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** ADR-002 (clé partition documentId) identifié. Module 1.1 terminé et stable.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---


> ---
> ### Prompts LLM — Classification (OBLIGATOIRE pour implémenter VisionModelAdapter)
>
> **Modèle retenu :** `gpt-4o` (OpenAI Vision) — supporte les images et PDF page par page.
> Alternative : `mistral-pixtral-large` si OpenAI indisponible (Circuit Breaker ADR-001).
>
> **Prompt système (System Message) :**
> ```
> Tu es un expert en classification de documents administratifs français.
> Tu reçois une image ou une page de document et tu dois identifier son type.
> Réponds UNIQUEMENT en JSON valide, sans texte avant ou après.
> Ne fais jamais de suppositions — si tu n'es pas sûr, utilise AUTRE.
> ```
>
> **Prompt utilisateur (User Message) :**
> ```
> Analyse ce document et retourne UNIQUEMENT ce JSON :
> {
>   "type": "FACTURE|CNI|PASSEPORT|RIB|ORDONNANCE|BULLETIN_SALAIRE|CONTRAT|JUSTIFICATIF_DOMICILE|AUTRE",
>   "confidence": 0.0 à 1.0,
>   "reasoning": "Explication courte (max 50 mots)",
>   "indicators": ["indice1", "indice2"]
> }
> ```
>
> **Paramètres API OpenAI :**
> - model : `gpt-4o`
> - max_tokens : 200 (réponse courte — JSON uniquement)
> - temperature : 0.1 (déterministe — pas de créativité)
> - response_format : `{ type: "json_object" }`
>
> **Comment envoyer l'image :**
> Pour une image : encoder en base64 et envoyer comme `image_url` (data URI).
> Pour un PDF : extraire chaque page en image PNG via PDFBox, envoyer la première page.
> Si le PDF a du texte natif : extraire le texte avec PDFBox et l'envoyer comme texte (moins cher).
>
> **Gestion des réponses invalides :**
> Si le JSON retourné ne respecte pas le schéma → retry 1× avec le même prompt.
> Si le 2ème essai échoue → type=AUTRE, confidence=0.0, passer en NEEDS_REVIEW.
>
> **Coût estimé par classification :**
> - Image PNG (1 page) : ~1 500 tokens input + 200 tokens output ≈ $0.006
> - PDF texte natif : ~500 tokens input + 200 tokens output ≈ $0.002
> ---

#### Definition of Done — Phase 1.2

- [ ] Consumer Kafka idempotent testé (même offset traité 2× → second ignoré)
- [ ] Circuit Breaker testé : CLOSED → OPEN → HALF_OPEN → CLOSED
- [ ] Fallback testé : modèle indisponible → document en NEEDS_REVIEW
- [ ] Correction manuelle testée + audit entry validée
- [ ] Métriques : `docai_classification_duration_seconds`, `docai_classification_confidence_score`
- [ ] Tests TestContainers avec Kafka + MongoDB + S3 (LocalStack) réels

---

---

## Module 2 — Extraction d'Informations

> **Bounded Context :** Extraire les données structurées d'un document classifié, les valider et les enrichir via les référentiels externes.

### Phase 2.1 — Pipeline OCR & Extraction LLM

**Objectif :** Extraire les champs clés de chaque document classifié via OCR + LLM, avec résultat JSON structuré par type.

**Durée estimée :** 3 semaines

#### Business Rules

| ID | Règle |
|----|-------|
| BR-EXT-001 | L'extraction est déclenchée par réception de `DocumentClassified` |
| BR-EXT-002 | Le pipeline suit l'ordre : Prétraitement → OCR → LLM → Parsing → Cache → Persistance |
| BR-EXT-003 | Chaque champ extrait porte un score de confiance individuel (0.0–1.0) |
| BR-EXT-004 | Le score global est la moyenne pondérée des champs obligatoires |
| BR-EXT-005 | Le schéma d'extraction est défini par type de document |
| BR-EXT-006 | Les appels LLM sont protégés par Circuit Breaker + Retry + Bulkhead |
| BR-EXT-007 | En cas d'échec LLM (CB ouvert), un fallback OCR basique est utilisé |
| BR-EXT-008 | Le cache Valkey est consulté avant tout appel LLM (clé = SHA-256 contenu) |

**Schémas d'extraction par type :**

| Type | Champs obligatoires |
|------|---------------------|
| `FACTURE` | émetteur.siret, émetteur.raisonSociale, numéroFacture, dateEmission, montantHT, tauxTVA, montantTVA, montantTTC |
| `CNI` | nom, prénom, dateNaissance, numéroDocument, dateExpiration |
| `PASSEPORT` | nom, prénom, nationalité, numéroPasseport, dateExpiration, MRZ |
| `RIB` | titulaire, IBAN, BIC |
| `ORDONNANCE` | médecin.nom, médecin.RPPS, patient.nom, patient.dateNaissance, datePrescription, médicaments[] |
| `BULLETIN_SALAIRE` | employé.nom, employeur.siret, période, salaireNet, salaireBrut |

#### Scénarios BDD

```gherkin
Feature: Extraction automatique d'informations

  Scenario: Extraction réussie d'une facture
    Given un document FACTURE en état CLASSIFIED
    And le cache Valkey ne contient pas de résultat pour ce contenu
    When le pipeline d'extraction s'exécute
    Then l'OCR produit un texte brut non vide
    And le LLM extrait tous les champs selon le schéma FACTURE
    And le champ "montantTTC" a un score de confiance ≥ 0.90
    And le résultat est mis en cache Valkey (TTL 24h)
    And le document passe en état EXTRACTED
    And l'event "ExtractionCompleted" est publié

  Scenario: Cache hit — résultat LLM réutilisé
    Given un document au contenu identique à un document déjà extrait
    When le pipeline d'extraction s'exécute
    Then aucun appel LLM n'est effectué
    And le résultat est retourné en moins de 500ms
    And la métrique "docai_cache_hit_total" est incrémentée

  Scenario: Fallback OCR — Circuit Breaker LLM ouvert
    Given le Circuit Breaker du service LLM est en état OPEN
    When le pipeline tente l'extraction
    Then un fallback OCR basique extrait le texte brut sans structure
    And le document passe en état NEEDS_REVIEW avec flag "PARTIAL_EXTRACTION"
```

#### Architecture Hexagonale

**Domain Model :**
- `ExtractionResult` — Aggregate (documentId, tenantId, fields Map\<String,ExtractedField\>, globalScore, status, **rawOcrTextS3Key** (pas rawOcrText), corrections[])

> ---
> ### ⚠️ ADR-004 — Texte OCR brut → Amazon S3, jamais MongoDB (OBLIGATOIRE ici)
>
> **Pourquoi :** MongoDB limite les transactions à 4MB. Un PDF de 200 pages produit un texte OCR de 4-5MB. Stocker le texte brut dans MongoDB fait échouer la transaction silencieusement.
>
> **Comment implémenter dans `ExtractDocumentUseCase` et `ExtractionResult` :**
>
> Flux obligatoire :
> 1. OCR produit le texte brut → uploader dans S3 : `{tenantId}/{year}/{documentId}/ocr.txt`
> 2. Récupérer la clé S3 retournée
> 3. Créer `ExtractionResult` avec `rawOcrTextS3Key = "acme/2026/doc-001/ocr.txt"` (pas `rawOcrText`)
> 4. Persister `ExtractionResult` dans MongoDB (transaction légère, sans le texte brut)
>
> Pour relire le texte OCR plus tard (ex: retraitement manuel) :
> - Lire `rawOcrTextS3Key` depuis MongoDB
> - Télécharger le fichier S3 correspondant
>
> **Champ interdit dans MongoDB `extraction_results` :** `rawOcrText` — ce champ ne doit jamais exister dans la collection.
>
> **Test obligatoire :** Soumettre un PDF de 200 pages — vérifier que la transaction MongoDB réussit et que le texte OCR est bien dans S3.
>
> **Référence complète :** Annexe E — ADR-004
> ---
- `ExtractedField` — Value Object (value, confidenceScore, fieldName)
- `ExtractionCompleted`, `ExtractionFailed` — Domain Events

**Inbound Ports :**
- `PORT-IN-EXT-001` — `ExtractDocumentUseCase`

**Outbound Ports :**
- `PORT-OUT-EXT-001` — `ExtractionResultRepositoryPort`
- `PORT-OUT-EXT-002` — `OcrPort`
- `PORT-OUT-EXT-003` — `LlmPort`
- `PORT-OUT-EXT-004` — `ExtractionCachePort`

**Adapters :**
- `ExtractionKafkaConsumer` — consomme `docai.doc.classified` → **commons-kafka** (`ResilientKafkaConsumer`)
- `Tess4JOcrAdapter` — OCR images avec Tesseract
- `PdfBoxOcrAdapter` — extraction texte natif PDF (sans OCR si PDF texte)
- `OpenAiLlmAdapter` — appel OpenAI avec prompt structuré + Resilience4j
- `MistralLlmAdapter` — alternative provider LLM
- `ValkeyExtractionCacheAdapter` — cache SHA-256 → résultat

> ---
> ### ⚠️ ADR-003 — Jitter sur TTL cache Valkey (OBLIGATOIRE ici)
>
> **Pourquoi :** Si 1 000 documents sont mis en cache avec TTL fixe de 24h, ils expirent tous au même instant → 1 000 appels LLM simultanés → Circuit Breaker ouvert → 1 000 documents en NEEDS_REVIEW.
>
> **Comment implémenter dans `ValkeyExtractionCacheAdapter.set()` :**
>
> Ne jamais écrire : `TTL = 86400` (24h fixe)
>
> Toujours écrire : `TTL = 86400 + random(-1800, +1800)` (24h ± 30 min aléatoire)
>
> La méthode utilitaire `withJitter(baseTtlSeconds)` est disponible dans `commons-kafka` — l'utiliser systématiquement pour tout TTL > 1 heure.
>
> TTL effectifs dans ce module :
> - Cache résultat LLM : 24h ± 30 min
> - Cache validation SIRET : 7 jours ± 6h
> - Cache validation adresse BAN : 7 jours ± 6h
>
> **Test obligatoire :** Vérifier que 100 mises en cache consécutives produisent 100 TTL différents (aucune valeur identique).
>
> **Référence complète :** Annexe E — ADR-003
> ---

**Commons utilisés dans ce module :**
- `commons-multitenancy` → filtre tenant automatique MongoDB
- `commons-kafka` → `ResilientKafkaConsumer` (retry, DLQ, idempotence, tracing)
- `commons-outbox` → publication events ExtractionCompleted
- `commons-audit` → `@Audited` sur corrections manuelles
- `commons-testing` → `ExtractionResultTestBuilder`, stubs WireMock LLM

**Kafka Events :**

| Event | Topic | Payload clés |
|-------|-------|-------------|
| `ExtractionCompleted` | `docai.doc.extracted` | documentId, tenantId, fields, globalScore, modelVersion |
| `ExtractionFailed` | `docai.doc.failed` | documentId, reason, stage=EXTRACTION |

#### NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-EXT-001 | Latence extraction PDF natif (P95) | < 10s |
| NFR-EXT-002 | Latence extraction image scannée (P95) | < 25s |
| NFR-EXT-003 | Précision champs obligatoires | ≥ 95% |
| NFR-EXT-004 | Cache hit ratio après rodage | ≥ 40% |
| NFR-EXT-005 | Disponibilité avec fallback OCR | ≥ 99.5% |


> ---
> ### Références Annexes — Module 2.1 — Extraction OCR & LLM
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `ExtractDocumentUseCase (Application — implémente ExtractDocumentPort)`
> - `ExtractionKafkaConsumer (Adapter IN — étend ResilientKafkaConsumer)`
> - `OpenAiLlmAdapter (Adapter OUT — implémente LlmPort + Resilience4j CircuitBreaker)`
> - `Tess4JOcrAdapter (Adapter OUT — implémente OcrPort)`
> - `ValkeyExtractionCacheAdapter (Adapter OUT — TTL avec jitter ADR-003)`
> - `ExtractionResult (Aggregate — rawOcrTextS3Key pas rawOcrText — ADR-004)`
> - `ExtractedField (Value Object — fieldName, value, confidenceScore 0.0-1.0)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - TTL cache LLM avec jitter ±30 min vérifié — pas de TTL fixe (ADR-003)
> - rawOcrText absent de MongoDB — uniquement rawOcrTextS3Key (ADR-004)
> - Circuit Breaker LLM testé : OPEN → fallback OCR → NEEDS_REVIEW
> - Test avec PDF 200 pages : transaction MongoDB < 4MB (ADR-004)
> - Cache hit ratio > 40% après rodage — vérifier métrique docai_cache_hit_total
> - EXPLAIN PLAN sur toutes les requêtes ExtractionResult (ADR-010)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer sur ExtractionKafkaConsumer (retry, DLQ, idempotence)**
> - **commons-outbox → OutboxKafkaProducer clé partition = documentId (ADR-002)**
> - **commons-multitenancy → filtre tenant automatique sur ExtractionResultMongoAdapter**
> - **commons-audit → @Audited sur corrections manuelles extraction**
> - **commons-testing → ExtractionResultTestBuilder, stubs WireMock LLM (OpenAI, Mistral)**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Extraction démarrée : documentId, tenantId, type, cacheHit=true/false
> - INFO — Extraction terminée : documentId, tenantId, globalScore, fieldsCount, durationMs
> - INFO — OCR exécuté : documentId, tenantId, textLengthChars, ocrDurationMs (pas le texte lui-même)
> - WARN — Score confiance faible : documentId, fieldName, score (pas la valeur du champ PII)
> - WARN — Circuit Breaker LLM OPEN : documentId, fallback=OCR_BASIC
> - ERROR — Extraction échouée : documentId, tenantId, stage, raison
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** ADR-003 (jitter TTL) et ADR-004 (OCR→S3) identifiés. Module 1.2 terminé et stable.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---


> ---
> ### Prompts LLM — Extraction par type de document (OBLIGATOIRE pour OpenAiLlmAdapter)
>
> **Modèle retenu :** `gpt-4o` pour l'extraction (meilleure précision sur les documents français).
> **Temperature :** 0.0 (extraction factuelle — 0 créativité tolérée).
> **response_format :** `{ type: "json_object" }` — garantit un JSON valide en retour.
>
> **Prompt système commun à tous les types :**
> ```
> Tu es un expert en extraction d'informations de documents administratifs français.
> Le texte fourni est extrait par OCR — il peut contenir des erreurs de reconnaissance.
> Extrait uniquement les informations demandées. Si un champ est absent ou illisible,
> utilise null. Ne jamais inventer une valeur. Réponds UNIQUEMENT en JSON valide.
> ```
>
> **Prompt FACTURE :**
> ```
> Extrait les informations de cette facture française et retourne ce JSON :
> {
>   "emetteur": {
>     "raisonSociale": "string|null",
>     "siret": "string 14 chiffres|null",
>     "adresse": "string|null"
>   },
>   "numeroFacture": "string|null",
>   "dateEmission": "YYYY-MM-DD|null",
>   "dateEcheance": "YYYY-MM-DD|null",
>   "montantHT": "number|null",
>   "tauxTVA": "number (ex: 20.0)|null",
>   "montantTVA": "number|null",
>   "montantTTC": "number|null",
>   "modePaiement": "VIREMENT|CHEQUE|CB|PRELEVEMENT|null"
> }
> Texte OCR : {{ocrText}}
> ```
>
> **Prompt RIB :**
> ```
> Extrait les informations de ce RIB et retourne ce JSON :
> {
>   "titulaire": "string|null",
>   "banque": "string|null",
>   "iban": "string (format FRXX XXXX...)|null",
>   "bic": "string|null",
>   "domiciliation": "string|null"
> }
> Texte OCR : {{ocrText}}
> ```
>
> **Prompt CNI :**
> ```
> Extrait les informations de cette carte nationale d'identité française et retourne ce JSON :
> {
>   "nom": "string|null",
>   "prenom": "string|null",
>   "dateNaissance": "YYYY-MM-DD|null",
>   "lieuNaissance": "string|null",
>   "nationalite": "string|null",
>   "numeroDocument": "string|null",
>   "dateExpiration": "YYYY-MM-DD|null",
>   "sexe": "M|F|null"
> }
> Texte OCR : {{ocrText}}
> ```
>
> **Prompt ORDONNANCE :**
> ```
> Extrait les informations de cette ordonnance médicale française et retourne ce JSON :
> {
>   "medecin": {
>     "nom": "string|null",
>     "prenom": "string|null",
>     "rpps": "string 11 chiffres|null",
>     "specialite": "string|null",
>     "adresseCabinet": "string|null"
>   },
>   "patient": {
>     "nom": "string|null",
>     "prenom": "string|null",
>     "dateNaissance": "YYYY-MM-DD|null"
>   },
>   "datePrescription": "YYYY-MM-DD|null",
>   "medicaments": [
>     {
>       "nom": "string",
>       "dosage": "string|null",
>       "posologie": "string|null",
>       "duree": "string|null"
>     }
>   ]
> }
> Texte OCR : {{ocrText}}
> ```
>
> **Prompt BULLETIN_SALAIRE :**
> ```
> Extrait les informations de ce bulletin de salaire français et retourne ce JSON :
> {
>   "employe": {
>     "nom": "string|null",
>     "prenom": "string|null",
>     "matricule": "string|null"
>   },
>   "employeur": {
>     "raisonSociale": "string|null",
>     "siret": "string|null"
>   },
>   "periode": "YYYY-MM|null",
>   "salaireBrut": "number|null",
>   "totalCotisations": "number|null",
>   "salaireNet": "number|null",
>   "salaireNetImposable": "number|null",
>   "heuresTravaillees": "number|null"
> }
> Texte OCR : {{ocrText}}
> ```
>
> **Scores de confiance par champ :**
> Après extraction, calculer un score de confiance par champ :
> - 1.0 : valeur extraite et validée algorithmiquement (SIRET Luhn OK, IBAN modulo 97 OK)
> - 0.9 : valeur extraite avec format correct (date valide, montant positif)
> - 0.7 : valeur extraite mais format non vérifié
> - 0.5 : valeur extraite avec doute (OCR flou, caractères ambigus)
> - 0.0 : valeur null (champ absent ou illisible)
>
> **Score global :** Moyenne pondérée des champs obligatoires uniquement.
> ---

#### Definition of Done — Phase 2.1

- [ ] Consumer Kafka idempotent testé
- [ ] Cache Valkey testé : hit → pas d'appel LLM
- [ ] Circuit Breaker LLM testé : OPEN → fallback OCR → NEEDS_REVIEW
- [ ] Tous les schémas d'extraction testés avec documents réels (ou mocks)
- [ ] Métriques : `docai_extraction_duration_seconds`, `docai_cache_hit_total`
- [ ] Tests WireMock : simulation OpenAI timeout → retry → fallback

---

### Phase 2.2 — Validation Métier & APIs Externes

**Objectif :** Valider les données extraites contre les règles métier et les référentiels officiels (INSEE, BAN, RPPS).

**Durée estimée :** 2 semaines

#### APIs Externes — Synthèse décisionnelle

| Validation | API | Coût | Rate Limit | Stratégie |
|-----------|-----|------|-----------|-----------|
| SIRET format | Algorithme Luhn (local) | Gratuit | Aucun | Validation locale d'abord |
| SIRET actif | API SIRENE INSEE | Gratuit | 30 req/min | + cache Valkey 7 jours |
| IBAN format | Algorithme modulo 97 (local) | Gratuit | Aucun | Validation locale d'abord |
| Adresse française | API BAN Géoplateforme IGN | Gratuit | 50 req/s | + cache Valkey 7 jours |
| RPPS médecin | API FHIR ANS | Gratuit | Non documenté | Fichier local + API |

**Règle fondamentale :** Les validations algorithmiques (Luhn, modulo 97) sont exécutées EN PREMIER. Si elles échouent, aucun appel API n'est effectué → économie de quota et signal de fraude fort immédiat.

#### Business Rules

| ID | Règle | Sévérité |
|----|-------|---------|
| BR-EXT-010 | SIRET doit avoir 14 chiffres et passer Luhn | BLOQUANT |
| BR-EXT-011 | IBAN doit passer modulo 97 (ISO 13616) | BLOQUANT |
| BR-EXT-012 | SIRET valide Luhn → vérification activité INSEE | AVERTISSEMENT |
| BR-EXT-013 | Date émission ne doit pas être dans le futur | BLOQUANT |
| BR-EXT-014 | Montant TTC = Montant HT + TVA (±0.02€) | BLOQUANT |
| BR-EXT-015 | Si API externe indisponible mais cache disponible → fail-open (validation continue) | MUST |
| BR-EXT-016 | Si API externe indisponible ET cache vide → avertissement dans le rapport | MUST |

#### Anti-Corruption Layer — APIs Externes

Chaque API externe est isolée derrière un port stable. Les changements d'API INSEE, BAN ou RPPS ne touchent que l'adapter, jamais le domaine.

```
ValidateExtractionUseCase (Application)
  → SiretValidatorPort (Domain Interface)
       → InseeApiAdapter (Infrastructure)
            → InseeApiClient (HTTP) → Cache Valkey (7j) → Resilience4j
```

**Adapters :**
- `InseeApiAdapter` — API SIRENE v3, OAuth2 client_credentials, cache Valkey 7j
- `BanApiAdapter` — Géoplateforme IGN, cache Valkey 7j
- `RppsFileAdapter` — fichier RPPS local (mise à jour hebdomadaire) + API FHIR ANS en fallback
- `SiretLuhnValidator` — validation algorithmique locale
- `IbanModulo97Validator` — validation algorithmique locale

#### Scénarios BDD

```gherkin
Feature: Validation des données extraites

  Scenario: SIRET invalide — détecté localement sans appel INSEE
    Given un document FACTURE avec SIRET "12345678901234" (Luhn invalide)
    When la validation s'exécute
    Then la validation Luhn échoue
    And aucun appel à l'API INSEE n'est effectué
    And le signal "DATA_SIRET_INVALID" est ajouté (poids 40)
    And l'extraction est marquée PARTIALLY_INVALID

  Scenario: API INSEE indisponible — fail-open avec cache
    Given le SIRET "81969482600017" est en cache Valkey (validation: ACTIF)
    And l'API INSEE est indisponible
    When la validation SIRET s'exécute
    Then le résultat en cache est utilisé
    And la validation se termine sans erreur

  Scenario: IBAN invalide — modulo 97 échoue
    Given un document RIB avec IBAN "FR76INVALID"
    When la validation IBAN s'exécute
    Then le calcul modulo 97 échoue
    And le signal "DATA_IBAN_INVALID" est ajouté (poids 40)
```


> ---
> ### Références Annexes — Module 2.2 — Validation Métier & APIs Externes
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `ValidateExtractionUseCase (Application — orchestre la chaîne de validateurs)`
> - `SiretLuhnValidator (Domain Service — algorithme local, pas d'appel API)`
> - `IbanModulo97Validator (Domain Service — algorithme local ISO 13616)`
> - `InseeApiAdapter (Adapter OUT — Anti-Corruption Layer, cache Valkey 7j ADR-003)`
> - `BanApiAdapter (Adapter OUT — Anti-Corruption Layer, cache Valkey 7j ADR-003)`
> - `ValidationResult (Value Object — field, status VALID/INVALID/UNVERIFIED, source)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Validations locales (Luhn, modulo 97) exécutées AVANT tout appel API externe
> - Cache Valkey 7j avec jitter ±6h sur SIRET et adresses (ADR-003)
> - Fail-open testé : API INSEE down + cache disponible → validation continue
> - Fail-open testé : API INSEE down + cache vide → avertissement dans rapport
> - Métriques : docai_validation_api_call_total{api, status} exposées
> - WireMock stubs pour tous les scénarios INSEE, BAN, RPPS (timeout, 404, 500)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer avec Circuit Breaker sur chaque API externe**
> - **commons-multitenancy → isolation tenant sur ValidationResult**
> - **commons-audit → @Audited sur résultats de validation**
> - **commons-testing → ExternalApiStubs WireMock (INSEE, BAN, RPPS simulées)**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Validation SIRET locale : documentId, siret=[PARTIAL_MASK], result=VALID/INVALID
> - INFO — Appel API INSEE : documentId, durationMs, status=HIT/MISS/ERROR
> - WARN — API INSEE indisponible, cache utilisé : documentId, cacheAge
> - WARN — API INSEE indisponible, cache vide : documentId, action=UNVERIFIED
> - ERROR — Validation IBAN échouée : documentId, iban=[PII_MASKED], raison
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Vérifier les accès API INSEE (credentials sandbox) avant de commencer.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 2.2

- [ ] Algorithmes Luhn et modulo 97 testés unitairement (cas valides, invalides, limites)
- [ ] Anti-Corruption Layer testé avec WireMock (INSEE, BAN, RPPS simulées)
- [ ] Fail-open validé : API down + cache disponible → validation continue
- [ ] Fail-open validé : API down + cache vide → avertissement dans rapport
- [ ] Métriques : `docai_validation_api_call_total{api, status}`
- [ ] Cache Valkey 7j pour validations SIRET et adresses

---

### Phase 2.3 — Correction Manuelle & Audit

**Objectif :** Permettre la correction des champs extraits avec traçabilité complète et revalidation automatique.

**Durée estimée :** 1 semaine

#### Business Rules

| ID | Règle |
|----|-------|
| BR-EXT-020 | Un champ peut être corrigé manuellement par un ANALYST |
| BR-EXT-021 | Chaque correction génère un AuditEntry immuable (userId, fieldName, avant, après, timestamp) |
| BR-EXT-022 | Après correction, la validation complète est relancée automatiquement |
| BR-EXT-023 | La correction invalide le cache Valkey associé au document |
| BR-EXT-024 | Un historique complet des corrections est accessible via `GET /v1/documents/{id}/audit` |


> ---
> ### Références Annexes — Module 2.3 — Correction Manuelle & Audit
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `CorrectExtractionFieldUseCase (Application — crée AuditEntry immuable)`
> - `ExtractionCorrectionController (Adapter IN REST — ANALYST uniquement)`
> - `AuditEntry (Value Object immuable — userId, fieldName, oldValue, newValue, timestamp)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Correction manuelle crée AuditEntry immuable — tenter une modification → exception
> - Cache Valkey invalidé après correction — vérifier qu'un nouvel appel LLM est déclenché
> - Revalidation automatique déclenchée après correction — tester le flow complet
> - Endpoint GET /v1/documents/{id}/audit protégé par JWT et isolation tenant
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-audit → @Audited sur CorrectExtractionFieldUseCase (immuable, userId, avant, après)**
> - **commons-multitenancy → isolation tenant sur corrections**
> - **commons-api → ProblemDetail pour erreurs de correction (champ inexistant, rôle insuffisant)**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Correction champ : documentId, tenantId, fieldName, correctedBy=[PII_MASKED]
> - INFO — Cache invalidé après correction : documentId, cacheKey
> - INFO — Revalidation déclenchée : documentId, tenantId
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Module 2.1 et 2.2 terminés. Schéma AuditEntry validé par le Tech Lead.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 2.3

- [ ] Correction manuelle auditée avec AuditEntry immuable en MongoDB
- [ ] Invalidation cache Valkey testée après correction
- [ ] Revalidation automatique déclenchée après correction
- [ ] Historique corrections accessible via endpoint audit
- [ ] Endpoint protégé : seul `ANALYST` ou `TENANT_ADMIN` peut corriger

---

---

## Module 3 — Détection de Fraude

> **Bounded Context :** Analyser chaque document extrait pour détecter des indices de fraude, scorer le risque et gérer le workflow de décision.

### Phase 3.1 — Scoring de Base & Signaux Données

**Objectif :** Calculer un score de fraude (0–100) basé sur la cohérence des données extraites et déclencher le routing automatique selon les seuils.

**Durée estimée :** 2 semaines

#### Business Rules

| ID | Règle |
|----|-------|
| BR-FRD-001 | Tout document en état EXTRACTED fait l'objet d'une analyse fraude automatique |
| BR-FRD-002 | Le score fraude est un entier 0–100 (somme des poids des signaux, cap à 100) |
| BR-FRD-003 | Chaque signal a un poids défini et fixe |
| BR-FRD-004 | Score 0–25 → `DocumentApproved` automatiquement |
| BR-FRD-005 | Score 26–50 → `DocumentFlaggedForReview` (révision optionnelle) |
| BR-FRD-006 | Score 51–75 → `DocumentBlocked` + queue révision obligatoire |
| BR-FRD-007 | Score 76–100 → `DocumentRejected` immédiatement + alerte temps réel |
| BR-FRD-008 | L'analyse est fail-safe : un analyseur défaillant est ignoré sans bloquer le pipeline |
| BR-FRD-009 | La `FraudAnalysis` est immuable après création |

**Catalogue complet des signaux :**

| Signal | Poids | Catégorie | Description |
|--------|-------|-----------|-------------|
| `DATA_ARITHMETIC_ERROR` | 35 | Données | Calculs TVA/totaux incorrects |
| `DATA_SIRET_INVALID` | 40 | Données | SIRET invalide algorithmiquement (Luhn) |
| `DATA_SIRET_UNKNOWN` | 20 | Données | SIRET valide mais inconnu INSEE |
| `DATA_IBAN_INVALID` | 40 | Données | IBAN invalide (modulo 97) |
| `DATA_DATE_FUTURE` | 30 | Données | Date d'émission dans le futur |
| `DATA_DUPLICATE` | 50 | Données | Document identique avec données différentes |
| `DATA_RPPS_INVALID` | 35 | Données | Numéro RPPS médecin invalide ou inconnu |
| `META_EDITOR_SUSPICIOUS` | 25 | Métadonnées | Logiciel d'édition image (Photoshop, GIMP) |
| `META_DATE_INCONSISTENCY` | 20 | Métadonnées | Date création fichier incohérente avec contenu |
| `META_HIDDEN_LAYERS` | 30 | Métadonnées | Couches cachées dans PDF |
| `META_UPSCALE_ARTIFACTS` | 15 | Métadonnées | Image artificiellement agrandie |
| `VISUAL_FONT_INCONSISTENCY` | 15 | Visuel | Polices multiples dans un même champ |
| `VISUAL_TEXT_OVERLAY` | 35 | Visuel | Texte superposé ou collé |
| `VISUAL_LOGO_DEGRADED` | 10 | Visuel | Logo de résolution inférieure |
| `VISUAL_ALIGNMENT_BROKEN` | 10 | Visuel | Alignement de colonnes incohérent |

#### Scénarios BDD

```gherkin
Feature: Détection de fraude — scoring automatique

  Scenario: Document légitime — approbation automatique
    Given un document FACTURE sans anomalie détectée
    When l'analyse fraude s'exécute
    Then aucun signal n'est détecté
    And le score fraude est 0 et le RiskLevel est FAIBLE
    And l'event "DocumentApproved" est publié
    And aucune action manuelle n'est requise

  Scenario: Détection calcul arithmétique incorrect
    Given un document FACTURE : montantHT=1000€, montantTVA=200€, montantTTC=1500€
    When l'analyseur de cohérence s'exécute
    Then le signal "DATA_ARITHMETIC_ERROR" est détecté avec poids 35
    And l'evidence contient "expected_ttc=1200, found_ttc=1500"

  Scenario: Score critique — rejet immédiat
    Given signaux : DATA_SIRET_INVALID(40) + DATA_ARITHMETIC_ERROR(35) + DATA_DATE_FUTURE(30)
    When l'analyse agrège les signaux
    Then le score fraude est plafonné à 100
    And le RiskLevel est CRITIQUE et le document est rejeté immédiatement
    And une alerte temps réel est envoyée via SSE

  Scenario: Fail-safe — analyseur défaillant ignoré
    Given l'analyseur "DataCoherenceAnalyzer" lève une RuntimeException
    When l'analyse s'exécute avec CompositeFraudAnalyzer
    Then l'analyseur défaillant est ignoré (catch + log + métrique)
    And les autres analyseurs s'exécutent normalement
    And le rapport contient un avertissement "PARTIAL_ANALYSIS"
    And aucune exception n'est propagée au pipeline
```

#### Architecture Hexagonale

**Domain Model :**
- `FraudAnalysis` — Aggregate immuable (documentId, tenantId, score, signals[], riskLevel, createdAt)
- `FraudScore` — Value Object (Integer 0–100, immutable)
- `FraudSignal` — Value Object (type, weight, description, evidence: Map\<String,Object\>)
- `RiskLevel` — Enum (FAIBLE, MODERE, ELEVE, CRITIQUE)
- `FraudDetected`, `DocumentApproved`, `DocumentRejected` — Domain Events

**Inbound Ports :**
- `PORT-IN-FRD-001` — `AnalyzeFraudUseCase`
- `PORT-IN-FRD-002` — `GetFraudAnalysisUseCase`

**Outbound Ports :**
- `PORT-OUT-FRD-001` — `FraudAnalysisRepositoryPort`
- `PORT-OUT-FRD-002` — `FraudAnalyzerPort` (interface implémentée par chaque analyseur)
- `PORT-OUT-FRD-003` — `NotificationPort`
- `PORT-OUT-FRD-004` — `MetadataAnalyzerPort` (Apache Tika)
- `PORT-OUT-FRD-005` — `VisualAnalyzerPort` (OpenCV)

**Design Patterns :**
- **Strategy** — chaque analyseur implémente `FraudAnalyzerStrategy`
- **Registry** — `FraudAnalyzerRegistry` : Map\<SignalType, FraudAnalyzerStrategy\>
- **Composite** — `CompositeFraudAnalyzer` agrège tous les analyseurs
- **Null Object** — `NoOpFraudAnalyzer` substituable si un analyseur est en erreur

**Commons utilisés dans ce module :**
- `commons-multitenancy` → filtre tenant sur toutes les requêtes MongoDB
- `commons-kafka` → `ResilientKafkaConsumer` sur le consumer fraud
- `commons-outbox` → publication events DocumentApproved / DocumentRejected
- `commons-audit` → `@Audited` sur chaque décision de révision (immuable)
- `commons-api` → `ProblemDetail` pour les erreurs de workflow révision
- `commons-testing` → `FraudAnalysisTestBuilder`, simulation analyseurs défaillants

#### NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-FRD-001 | Latence analyse complète (P95) | < 10s |
| NFR-FRD-002 | Taux faux positifs | < 5% |
| NFR-FRD-003 | Taux détection fraudes réelles (recall) | ≥ 85% |
| NFR-FRD-004 | Disponibilité avec fail-safe | ≥ 99.5% |
| NFR-FRD-005 | Immuabilité FraudAnalysis | 100% |


> ---
> ### Références Annexes — Module 3.1 — Scoring de Fraude
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `AnalyzeFraudUseCase (Application — orchestre le CompositeFraudAnalyzer)`
> - `CompositeFraudAnalyzer (Domain Service — agrège tous les analyseurs, fail-safe)`
> - `FraudAnalyzerRegistry (Domain Service — Map<SignalType, FraudAnalyzerStrategy>)`
> - `DataCoherenceAnalyzer (Domain Service — implémente FraudAnalyzerStrategy)`
> - `FraudAnalysis (Aggregate immuable — score, signals[], riskLevel, createdAt)`
> - `FraudScore (Value Object — Integer 0-100, cap à 100, immutable)`
> - `FraudSignal (Value Object — type, weight, description, evidence Map<String,Object>)`
> - `NoOpFraudAnalyzer (Null Object — substituable si analyseur en erreur)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - FraudAnalysis immuable : tentative de modification → exception domaine levée
> - Fail-safe testé : chaque analyseur peut lever une exception sans bloquer le pipeline
> - Score plafonné à 100 même si somme des poids > 100 — tester avec 3 signaux lourds
> - Tests mutation PIT sur FraudScore ≥ 85% (cœur métier critique)
> - Métriques : docai_fraud_score_distribution, docai_fraud_signal_detected{signal_type}
> - EXPLAIN PLAN sur collection fraud_analyses (ADR-010)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer sur FraudKafkaConsumer**
> - **commons-outbox → OutboxKafkaProducer clé partition = documentId (ADR-002)**
> - **commons-multitenancy → isolation tenant sur FraudAnalysisMongoAdapter**
> - **commons-audit → @Audited sur chaque décision de révision (immuable)**
> - **commons-testing → FraudAnalysisTestBuilder, simulation analyseurs défaillants**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Analyse fraude démarrée : documentId, tenantId
> - INFO — Analyse fraude terminée : documentId, tenantId, score, riskLevel, signalsCount, durationMs
> - WARN — Signal détecté : documentId, tenantId, signalType, weight (pas les données PII)
> - WARN — Analyseur défaillant ignoré : documentId, analyzerName, exception
> - ERROR — Analyse fraude échouée totalement : documentId, tenantId, raison
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Module 2.1 et 2.2 terminés. Catalogue des signaux validé par le métier.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 3.1

- [ ] Tous les analyseurs de données testés unitairement (cas valide, invalide, limite)
- [ ] Fail-safe validé : chaque analyseur peut lever une exception sans casser le pipeline
- [ ] Tests mutation PIT sur calcul de score FraudScore (≥ 85%)
- [ ] Immuabilité FraudAnalysis testée (tentative de modification → exception domaine)
- [ ] Métriques : `docai_fraud_score_distribution`, `docai_fraud_signal_detected{signal_type}`

---


### Phase 3.2 — Analyseurs Avancés : Apache Tika + Analyse Visuelle

**Objectif :** Détecter les falsifications via les métadonnées du fichier (Apache Tika) et l'analyse visuelle du contenu (algorithmes d'image processing).

**Durée estimée :** 2 semaines

---

#### Analyseur 1 — Apache Tika (Métadonnées fichier)

**Bibliothèque :** `org.apache.tika:tika-core:2.x`

Apache Tika extrait les métadonnées embarquées dans le fichier PDF ou image. Ces métadonnées révèlent comment le document a été créé et potentiellement modifié.

**Métadonnées extraites et signaux générés :**

| Métadonnée Tika | Clé Tika | Signal si suspect | Poids |
|----------------|----------|-------------------|-------|
| Logiciel créateur | `xmp:CreatorTool` | Si contient "Photoshop", "GIMP", "Inkscape", "Paint" → `META_EDITOR_SUSPICIOUS` | 25 |
| Date création fichier | `meta:creation-date` | Si > date émission document de plus de 30 jours → `META_DATE_INCONSISTENCY` | 20 |
| Date modification | `meta:save-date` | Si modification après la date de signature → `META_DATE_INCONSISTENCY` | 20 |
| Nombre de révisions | `cp:revision` | Si > 5 révisions sur une facture simple → `META_HIGH_REVISION_COUNT` | 10 |
| Couches PDF | `pdf:hasXFA` ou couches cachées | Si couches masquées détectées → `META_HIDDEN_LAYERS` | 30 |
| Résolution image | `tiff:XResolution` | Si résolution < 72 DPI (upscalé) → `META_UPSCALE_ARTIFACTS` | 15 |
| Producteur PDF | `pdf:PDFVersion` + `producer` | Si modifié par un éditeur non professionnel → `META_EDITOR_SUSPICIOUS` | 25 |

**Comment implémenter `ApacheTikaMetadataAdapter` :**

1. Télécharger le fichier depuis S3 (stream — pas de fichier temporaire sur disque)
2. Appeler `Tika.detect(stream)` pour confirmer le type MIME
3. Appeler `parser.parse(stream, handler, metadata, context)`
4. Parcourir les métadonnées extraites et comparer avec les règles ci-dessus
5. Générer les signaux fraude correspondants avec les evidences (valeur trouvée vs valeur attendue)
6. Retourner la liste de `FraudSignal` détectés

**Exemple d'evidence pour META_EDITOR_SUSPICIOUS :**
```json
{
  "signal": "META_EDITOR_SUSPICIOUS",
  "weight": 25,
  "evidence": {
    "tool": "Adobe Photoshop CC 2023",
    "expected": "professional PDF generator",
    "found": "image editing software"
  }
}
```

---

#### Analyseur 2 — Analyse Visuelle (Détection de falsifications)

**Bibliothèque :** `org.bytedeco:opencv:4.9.0` (JavaCV — binding Java d'OpenCV)
**Alternative légère :** API Vision OpenAI pour la détection visuelle (si OpenCV trop complexe)

**Algorithmes de détection et signaux générés :**

**Détection de texte superposé (VISUAL_TEXT_OVERLAY) — Poids 35 :**

Algorithme : Clone Stamp Detection
1. Convertir le document en image PNG (PDFBox rasterization à 300 DPI)
2. Appliquer un filtre de détection de bords (Canny Edge Detection)
3. Analyser les zones de texte : chercher des rectangles de pixels uniformes sous le texte
4. Si une zone de texte repose sur un fond de couleur uniforme non-blanc → superposition détectée
5. Seuil de déclenchement : zone > 50 pixels × 20 pixels

**Détection de polices incohérentes (VISUAL_FONT_INCONSISTENCY) — Poids 15 :**

Algorithme : Analyse des caractéristiques typographiques
1. Extraire le texte avec positions (PDFBox `TextPositionExtractor`)
2. Regrouper les caractères par zone visuelle (même ligne)
3. Calculer la hauteur moyenne des caractères par zone
4. Si écart de hauteur > 15% dans un même champ → polices différentes → signal
5. Cas typique : montant "modifié" a une police légèrement différente du reste de la facture

**Détection de logo dégradé (VISUAL_LOGO_DEGRADED) — Poids 10 :**

Algorithme : Comparaison de résolution par zone
1. Détecter les zones d'image dans le PDF (PDFBox `PDFStreamEngine`)
2. Pour chaque image extraite, calculer sa résolution effective (DPI)
3. Si résolution logo < 72 DPI alors que le reste du document est > 200 DPI → logo importé d'une source basse qualité
4. Signal indique que le logo a été récupéré depuis une source web et non depuis un document officiel

**Détection d'alignement cassé (VISUAL_ALIGNMENT_BROKEN) — Poids 10 :**

Algorithme : Analyse des lignes de base
1. Extraire les lignes de texte avec leurs coordonnées Y (PDFBox)
2. Calculer l'espacement moyen entre les lignes d'un tableau
3. Si un écart d'espacement > 20% de la moyenne → ligne ajoutée ou supprimée dans le tableau
4. Cas typique : ligne de montant ajoutée manuellement dans une facture modifiée

**Fail-safe obligatoire (ADR-001 pour les analyseurs) :**

Chaque analyseur visuel doit être enveloppé dans un try-catch :
- Si OpenCV lève une exception → logger WARN + ignorer ce signal + continuer
- Si l'analyse prend > 30 secondes → timeout + ignorer ce signal
- Le `CompositeFraudAnalyzer` ne doit jamais propager les exceptions des analyseurs

**Performance :**
- Analyse Tika : < 1 seconde (lecture des métadonnées uniquement)
- Analyse visuelle complète : 3-8 secondes selon la taille du document
- Timeout global analyseur visuel : 15 secondes

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-VIS-001 | L'analyse Tika est exécutée sur tous les documents sans exception | MUST |
| BR-VIS-002 | L'analyse visuelle est exécutée uniquement sur les documents FACTURE, CNI, RIB | MUST |
| BR-VIS-003 | Chaque analyseur visuel a un timeout de 15 secondes | MUST |
| BR-VIS-004 | L'échec d'un analyseur visuel n'arrête jamais le pipeline (fail-safe) | MUST |
| BR-VIS-005 | L'evidence de chaque signal contient la valeur trouvée et la valeur attendue | MUST |

#### Definition of Done — Phase 3.2

- [ ] ApacheTikaMetadataAdapter testé avec un PDF créé par Photoshop → signal META_EDITOR_SUSPICIOUS
- [ ] ApacheTikaMetadataAdapter testé avec un PDF dont la date de création est dans le futur → META_DATE_INCONSISTENCY
- [ ] VisualAnalyzerAdapter testé avec une facture dont le montant a été modifié visuellement → VISUAL_TEXT_OVERLAY
- [ ] Fail-safe testé : chaque analyseur peut lever une RuntimeException sans bloquer le pipeline
- [ ] Timeout 15s respecté : analyse d'un PDF de 50 pages terminée ou abandonnée dans les délais
- [ ] Tous les analyseurs enregistrés dans FraudAnalyzerRegistry



---

### Phase 3.3 — Workflow de Révision Humaine

**Objectif :** Permettre aux `FRAUD_REVIEWER` de statuer sur les documents suspects avec State Machine auditée et notifications temps réel.

**Durée estimée :** 2 semaines

#### Business Rules

| ID | Règle |
|----|-------|
| BR-FRD-020 | Un document bloqué (score 51–75) est mis dans la queue de révision |
| BR-FRD-021 | Seul un `FRAUD_REVIEWER` peut statuer sur un document en révision |
| BR-FRD-022 | Une décision (APPROVED/REJECTED/ESCALATED) est définitive et immuable |
| BR-FRD-023 | Chaque décision est signée (userId, timestamp, justification optionnelle) |
| BR-FRD-024 | Une décision ESCALATED requiert un second reviewer |
| BR-FRD-025 | Les alertes fraude critique (score > 75) arrivent en temps réel via SSE |

**State Machine de révision :**
```
PENDING_REVIEW → [reviewer prend en charge] → REVIEWING
  → [APPROVED]   → APPROVED  → event DocumentApproved
  → [REJECTED]   → REJECTED  → event DocumentRejected
  → [ESCALATED]  → ESCALATED → [second reviewer] → REVIEWING (second cycle)
                                    → APPROVED / REJECTED
```

**Domain Model (ajouts) :**
- `ReviewDecision` — Value Object immuable (decision, reviewerId, timestamp, notes)
- `ReviewDecisionMade` — Domain Event

**Endpoints :**

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| GET | `/v1/fraud/review-queue` | `FRAUD_REVIEWER` | Queue de révision paginée |
| POST | `/v1/fraud/{id}/decision` | `FRAUD_REVIEWER` | Statuer sur un document |
| GET | `/v1/fraud/{id}/analysis` | `ANALYST`, `FRAUD_REVIEWER` | Détail analyse fraude |
| GET | `/v1/fraud/stream` | Tous rôles | SSE alertes temps réel |


> ---
> ### Références Annexes — Module 3.3 — Workflow de Révision Humaine
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `MakeReviewDecisionUseCase (Application — transition State Machine + AuditEntry immuable)`
> - `FraudReviewController (Adapter IN REST — FRAUD_REVIEWER uniquement)`
> - `SseNotificationAdapter (Adapter OUT — Spring SseEmitter, map tenantId → List<SseEmitter>)`
> - `ReviewDecision (Value Object immuable — decision, reviewerId, timestamp, notes)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Toutes les transitions State Machine testées y compris les transitions invalides
> - Immuabilité des décisions testée : tentative modification → exception domaine
> - SSE testé : event Kafka → alerte reçue par client SSE < 2 secondes
> - Isolation SSE tenant : client A ne reçoit pas les alertes du client B
> - Cleanup SseEmitters morts (keepalive) testé
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-audit → @Audited sur MakeReviewDecisionUseCase (décision immuable signée)**
> - **commons-multitenancy → isolation tenant sur SSE et queue de révision**
> - **commons-api → ProblemDetail pour transitions invalides de la State Machine**
> - **commons-kafka → consumer SSE écoute docai.doc.fraud.analyzed**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Décision révision : documentId, tenantId, decision, reviewerId=[PII_MASKED]
> - INFO — Alerte SSE envoyée : documentId, tenantId, score, riskLevel, emittersCount
> - WARN — SseEmitter mort détecté et nettoyé : tenantId
> - ERROR — Transition State Machine invalide : documentId, fromStatus, toStatus
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Modules 3.1 et 3.2 terminés. State Machine validée par le Tech Lead.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 3.3

- [ ] Toutes les transitions State Machine testées (y compris transitions invalides)
- [ ] Immuabilité des décisions testée
- [ ] SSE testé : event Kafka → alerte reçue par client SSE < 2s
- [ ] Isolation tenant validée sur SSE (client A ne reçoit pas alertes client B)
- [ ] Queue de révision testée avec pagination et filtres (riskLevel, date, reviewer)

---

---

## Module 4 — Orchestration & Pipeline

> **Bounded Context :** Garantir que le pipeline de traitement s'exécute de façon fiable, résiliente et traçable, avec reprise sur échec et zéro perte de message.

### Phase 4.1 — Pipeline Kafka & Idempotence

**Objectif :** Mettre en place le pipeline asynchrone complet avec at-least-once delivery, idempotence des consumers et traçabilité des étapes.

**Durée estimée :** 2 semaines

#### Business Rules

| ID | Règle |
|----|-------|
| BR-ORC-001 | Chaque étape est déclenchée par un event Kafka dédié |
| BR-ORC-002 | Tous les consumers utilisent le commit manuel (at-least-once delivery) |
| BR-ORC-003 | Chaque consumer est idempotent (même message traité N fois = même résultat) |
| BR-ORC-004 | L'idempotence est garantie par clé Valkey : `topic:partition:offset` (TTL 24h) |
| BR-ORC-005 | Le statut du pipeline est consultable à tout moment |
| BR-ORC-006 | Chaque changement d'état de document est horodaté et traçable |

#### Outbox Pattern — Fonctionnement détaillé

1. Le Use Case écrit l'OutboxEvent dans la collection `outbox_events` MongoDB dans la **même transaction** que la mutation du document
2. Le `OutboxPollerAdapter` poll la collection toutes les 500ms (`@Scheduled`)
3. Pour chaque OutboxEvent en statut `PENDING`, il produit sur Kafka
4. Après confirmation Kafka (ack), l'OutboxEvent est marqué `PUBLISHED`
5. En cas d'échec Kafka, retry avec backoff exponentiel (5 tentatives, délais : 1s, 2s, 4s, 8s, 16s)
6. Après 5 échecs, statut `FAILED` + alerte monitoring

**Garantie fournie :** Zéro perte de document même en cas de panne Kafka ou redémarrage de l'application.

**Commons utilisés dans ce module :**
- `commons-outbox` → `OutboxMessage`, `OutboxRepository`, `OutboxRelay` — cœur du pattern
- `commons-kafka` → `ResilientKafkaConsumer` sur tous les consumers du pipeline
- `commons-kafka` → `OutboxKafkaProducer` pour la publication via Outbox
- `commons-multitenancy` → propagation tenant-id dans les headers Kafka
- `commons-audit` → `@Audited` sur chaque compensation Saga


> ---
> ### Références Annexes — Module 4.1 — Pipeline Kafka & Idempotence
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `OutboxPollerAdapter (Adapter — @Scheduled 500ms, publie les events PENDING)`
> - `PipelineStatusUseCase (Application — statut temps réel du pipeline)`
> - `OutboxEvent (Aggregate — id, aggregateType, eventType, payload, status PENDING/PUBLISHED/FAILED)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Clé partition Kafka = documentId sur tous les topics pipeline (ADR-002) — vérifier Kafka UI
> - Outbox Pattern testé : panne Kafka simulée → event publié à la reprise (zéro perte)
> - Idempotence consumer : même offset traité 2× → second ignoré silencieusement
> - Monitoring lag Kafka : alerte Grafana si > 1000 messages — vérifier configuration
> - Outbox relay monitoré : alerte si délai > 30 secondes
> - Topics créés avec partitions et rétention corrects (voir docker/kafka-init)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-outbox → OutboxMessage, OutboxRepository, OutboxRelay (cœur du pattern — zéro perte garantie)**
> - **commons-kafka → ResilientKafkaConsumer sur tous les consumers + OutboxKafkaProducer**
> - **commons-multitenancy → tenant-id propagé dans headers Kafka sur chaque message**
> - **commons-audit → @Audited sur chaque compensation Saga**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Outbox event publié : eventId, topic, documentId, partitionKey=documentId
> - WARN — Outbox event retry : eventId, attempt N/5, raison
> - ERROR — Outbox event FAILED après 5 tentatives : eventId, documentId, raison
> - INFO — Consumer idempotent : offset déjà traité ignoré : topic, partition, offset
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** ADR-002 (clé partition = documentId) compris et validé. Tous les topics Kafka créés.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 4.1

- [ ] Idempotence testée : même offset Kafka traité 2× → second ignoré silencieusement
- [ ] Outbox Pattern testé : panne Kafka simulée → message publié à la reprise
- [ ] Statut pipeline temps réel testé
- [ ] Chaque topic Kafka créé avec bonne configuration (partitions, rétention, compaction)

---

### Phase 4.2 — Retry, DLQ & Reprise sur Échec

**Objectif :** Gérer les échecs transitoires et permanents du pipeline avec politiques de retry et Dead Letter Queue.

**Durée estimée :** 1 semaine

#### Business Rules

| ID | Règle |
|----|-------|
| BR-ORC-010 | Les erreurs transitoires sont retentées 3× avec backoff exponentiel (1s, 2s, 4s) |
| BR-ORC-011 | Après 3 échecs, le message est envoyé en DLQ (`docai.doc.dlq`) |
| BR-ORC-012 | La DLQ est monitorée — alerte si > 10 messages |
| BR-ORC-013 | Les messages en DLQ peuvent être rejoués manuellement via API admin |
| BR-ORC-014 | Chaque message DLQ contient : raison, topic source, nombre de tentatives, timestamp |
| BR-ORC-015 | La DLQ est retenue 90 jours (vs 7 jours pour les topics normaux) |

**Endpoint DLQ :**

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| GET | `/v1/admin/dlq` | `TENANT_ADMIN` | Lister les messages en DLQ |
| POST | `/v1/admin/dlq/{id}/replay` | `TENANT_ADMIN` | Rejouer un message DLQ |
| DELETE | `/v1/admin/dlq/{id}` | `TENANT_ADMIN` | Supprimer un message DLQ |


> ---
> ### Références Annexes — Module 4.2 — Retry, DLQ & Reprise sur Échec
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `ReplayDlqMessageUseCase (Application — rejoue un message DLQ)`
> - `DlqAdminController (Adapter IN REST — TENANT_ADMIN uniquement)`
> - `DlqMessage (Value Object — topic source, attempts, reason, payload, failedAt)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - DLQ monitorée : alerte Grafana si > 10 messages — vérifier la configuration
> - Retry 3× avec backoff testé (erreur transitoire → succès au 3ème essai)
> - Replay DLQ testé : message rejoué → traitement normal
> - Rétention DLQ 90 jours configurée (vs 7 jours topics normaux)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer gère le retry exponentiel et l'envoi DLQ automatiquement**
> - **commons-audit → @Audited sur ReplayDlqMessageUseCase**
> - **commons-api → endpoints DLQ admin protégés TENANT_ADMIN**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - WARN — Retry message : topic, partition, offset, attempt N/3, raison
> - ERROR — Message envoyé en DLQ : topic, offset, documentId, raison, attempts=3
> - INFO — Replay DLQ déclenché : dlqMessageId, replayedBy=[PII_MASKED]
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Module 4.1 terminé. Politique retry (délais, nombre de tentatives) validée par le Tech Lead.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 4.2

- [ ] Retry 3× avec backoff testé (erreur transitoire → succès au 3e essai)
- [ ] DLQ alimentée testé (3 échecs consécutifs → message en DLQ)
- [ ] Replay DLQ testé (message DLQ rejoué → traitement normal)
- [ ] Alerte Grafana configurée : lag DLQ > 10 → notification Slack

---


### Phase 4.3 — Saga & Compensation

**Objectif :** Garantir la cohérence du pipeline en cas d'échec partiel, avec compensation automatique et auditée pour chaque scénario d'échec possible.

**Durée estimée :** 1 semaine

**Pattern utilisé : Saga Choreography (sans orchestrateur central)**
Chaque service écoute les events de succès et d'échec, et décide de sa compensation de façon autonome.

---

#### Scénarios de compensation — tous les cas couverts

**Scénario 1 — Échec de classification après upload**

```
Document uploadé → S3 OK → MongoDB OK → Event DocumentUploaded publié
→ ClassificationKafkaConsumer échoue après 3 retries
→ Event DocumentFailed publié (failedStage: CLASSIFICATION)
→ Compensation : document passe en NEEDS_REVIEW (pas de suppression S3 — fichier conservé)
→ AuditEntry : "Classification failed after 3 retries, moved to NEEDS_REVIEW"
→ Notification tenant : "Un document nécessite une révision manuelle"
```

**Scénario 2 — Échec d'extraction après classification**

```
Document classifié → Event DocumentClassified publié
→ ExtractionKafkaConsumer échoue après 3 retries (LLM indisponible, timeout)
→ Compensation partielle :
  - Si OCR a réussi → ExtractionResult partiel sauvegardé (champs = vide, rawOcrTextS3Key présent)
  - Si OCR a aussi échoué → ExtractionResult absent
→ Document passe en NEEDS_REVIEW avec flag EXTRACTION_FAILED
→ AuditEntry : "Extraction failed, OCR partial result preserved"
→ Le reviewer peut corriger manuellement les champs manquants
```

**Scénario 3 — Échec d'analyse fraude après extraction**

```
Document extrait → Event DocumentExtracted publié
→ FraudKafkaConsumer échoue après 3 retries
→ Compensation : analyse fraude partielle (signaux partiels disponibles)
  - Si au moins 1 analyseur a répondu → FraudAnalysis créée avec flag PARTIAL_ANALYSIS
  - Si 0 analyseur a répondu → FraudAnalysis absente
→ Document passe en NEEDS_REVIEW (sécurité — mieux vaut révision humaine)
→ Score fraude = -1 (indique une analyse incomplète, différent de 0)
→ AuditEntry : "Fraud analysis partial, manual review required"
```

**Scénario 4 — Échec de livraison webhook après complétion**

```
Document complété → Event DocumentCompleted publié
→ WebhookDeliveryConsumer échoue après 5 retries (endpoint client non disponible)
→ Compensation : PAS de rollback du document (il est bien traité)
→ Action : webhook marqué PERMANENTLY_FAILED dans webhook_deliveries
→ Notification dashboard : "Webhook non livré — voir /v1/webhooks/{id}/deliveries"
→ Le client peut déclencher un replay manuel via /v1/admin/dlq/{id}/replay
→ AuditEntry : "Webhook delivery failed after 5 attempts, manual replay available"
```

**Scénario 5 — Panne pendant l'upload S3 (avant persistance MongoDB)**

```
Upload S3 démarre → connexion coupée à mi-chemin
→ AbortMultipartUpload appelé automatiquement (ADR-007)
→ Aucun document créé en MongoDB (transaction pas encore exécutée)
→ Aucun event Outbox publié
→ Compensation : rien à compenser — l'idempotency-key reste valide 24h
→ Le client peut re-soumettre avec la même clé → traité comme premier envoi
→ Pas d'AuditEntry (aucune action métier n'a eu lieu)
```

**Scénario 6 — Panne MongoDB après upload S3 (Outbox non créé)**

```
Upload S3 OK → Transaction MongoDB échoue (MongoDB primary failover)
→ Le fichier S3 est uploadé mais aucun document MongoDB n'existe
→ Compensation automatique :
  - La Lifecycle Rule S3 supprime l'objet après la durée de rétention si jamais référencé
  - Le client reçoit HTTP 503 → peut re-soumettre avec la même idempotency-key
→ Si le client ne re-soumet pas : le fichier S3 orphelin est supprimé après 90 jours
→ AuditEntry : impossible (MongoDB en panne) — log ERROR enregistré dans CloudWatch
```

**Scénario 7 — Panne entre Outbox sauvegardé et publication Kafka**

```
Outbox event PENDING sauvegardé → application redémarre avant publication Kafka
→ Au redémarrage, OutboxRelay repollute les events PENDING
→ L'event est publié sur Kafka (at-least-once delivery garantie)
→ Kafka consumer est idempotent → si le document était déjà en traitement, le doublon est ignoré
→ Aucune compensation nécessaire — le pattern Outbox protège ce scénario nativement
```

---

#### State Machine globale du pipeline avec compensations

```
PENDING
  │ Upload OK + S3 + Outbox publiés
  ▼
UPLOADED
  │ Classification OK (confidence ≥ 0.70)
  ▼                    │ confidence < 0.70 ou échec classification
CLASSIFIED             ▼
  │           NEEDS_REVIEW ←────── Toute compensation d'échec arrive ici
  │ Extraction OK                  │ Révision manuelle ANALYST
  ▼                               │
EXTRACTED                          │
  │ Analyse fraude OK             ▼
  ▼                           REVIEWING
FRAUD_ANALYZED                     │
  │ score ≤ 25 → Approved         │ Décision FRAUD_REVIEWER
  │ score 26-75 → Flagged    APPROVED / REJECTED
  │ score > 75 → Rejected
  ▼
COMPLETED ──── Webhook livré ──── Fin
  │
  └── Webhook non livré → COMPLETED (état inchangé, webhook en DLQ)
```

**Business Rules Saga :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-SAGA-001 | Tout document échoué passe en NEEDS_REVIEW — jamais en état bloquant | MUST |
| BR-SAGA-002 | La compensation ne supprime jamais le fichier S3 (sauf AbortMultipart) | MUST |
| BR-SAGA-003 | Chaque compensation génère un AuditEntry avec la raison et l'étape | MUST |
| BR-SAGA-004 | Le score fraude -1 indique une analyse incomplète (distinct de 0 = aucun risque) | MUST |
| BR-SAGA-005 | Un webhook non livré ne bloque jamais l'état COMPLETED du document | MUST |
| BR-SAGA-006 | L'idempotency-key reste valide 24h même en cas d'échec (permet le re-soumission) | MUST |

#### Definition of Done — Phase 4.3

- [ ] Scénario 1 testé : classification échoue 3× → document en NEEDS_REVIEW + AuditEntry
- [ ] Scénario 2 testé : LLM down → extraction partielle + NEEDS_REVIEW
- [ ] Scénario 3 testé : fraude partielle → score=-1 + NEEDS_REVIEW
- [ ] Scénario 4 testé : webhook échoue 5× → document reste COMPLETED + DLQ
- [ ] Scénario 5 testé : upload interrompu → AbortMultipartUpload + re-soumission possible
- [ ] Scénario 7 testé : OutboxRelay publie les events PENDING après redémarrage
- [ ] State Machine complète testée : toutes les transitions valides et invalides



---

---

# PARTIE 5 — PRODUIT & MONÉTISATION

> **Ordre obligatoire :**
> 1. **Module 5 — Dashboard & Reporting** (Read Model, SSE, Notifications)
> 2. **Module 6 — API Publique & Intégrations** (API Keys, Webhooks, Rate Limiting)
> 3. **Module 7 — Billing & Abonnements** (Plans, Stripe, Feature Flag)
>
> **Le Billing est en dernier** car il dépend du pipeline complet (quota = nombre de documents traités).
> Le Feature Flag `BILLING_ENABLED = false` permet de tout tester gratuitement pendant les Parties 3 et 4.

---

## Module 5 — Dashboard & Reporting

> **Bounded Context :** Fournir une interface temps réel sur l'activité de traitement, les alertes fraude et les KPIs du tenant.

**Note :** Ce module définit les endpoints REST et SSE consommés par le frontend (spécifié dans un document dédié `FRONTEND_SPECKIT.md`).

### Phase 5.1 — Read Model CQRS & Analytics

**Objectif :** Fournir les données du dashboard en < 100ms via un Read Model dédié, mis à jour par les Domain Events Kafka.

**Durée estimée :** 1 semaine

#### Business Rules

| ID | Règle |
|----|-------|
| BR-DSH-001 | Le Read Model est mis à jour par consommation des events Kafka (CQRS) |
| BR-DSH-002 | Les requêtes dashboard ne touchent jamais les collections write-side |
| BR-DSH-003 | Les KPIs sont agrégés et mis à jour de façon incrémentale |
| BR-DSH-004 | La pagination est obligatoire sur toutes les listes (max 100 par page) |
| BR-DSH-005 | Les filtres disponibles : status, type, riskLevel, dateFrom, dateTo |

**Collection `document_summary_views` — Read Model :**
Cette collection est une projection dénormalisée, mise à jour par le `DashboardProjectionConsumer` qui écoute tous les events Kafka du pipeline.

**Endpoints Dashboard :**

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/v1/dashboard/summary` | KPIs du tenant (total, par status, par type, par risque) |
| GET | `/v1/dashboard/documents` | Liste paginée du Read Model avec filtres |
| GET | `/v1/dashboard/documents/{id}` | Détail complet d'un document (read model) |
| GET | `/v1/analytics/usage` | Usage quota temps réel |
| GET | `/v1/analytics/fraud-trends` | Tendances fraude sur une période |

**Index Read Model (optimisés pour les requêtes dashboard) :**

| Index | Champs | Usage |
|-------|--------|-------|
| idx_tenant_status | tenantId, status, createdAt | Liste par statut |
| idx_tenant_risk | tenantId, riskLevel, createdAt | Liste par risque |
| idx_tenant_type | tenantId, type, createdAt | Liste par type |

#### NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-DSH-001 | Latence requêtes dashboard (P95) | < 100ms |
| NFR-DSH-002 | Délai mise à jour Read Model après event Kafka | < 2s |
| NFR-DSH-003 | Pagination : réponse pour 100 docs | < 200ms |

**Commons utilisés dans ce module :**
- `commons-multitenancy` → filtre tenant sur toutes les requêtes Read Model
- `commons-kafka` → `ResilientKafkaConsumer` sur le `DashboardProjectionConsumer`
- `commons-api` → `ApiResponse<T>` pour tous les endpoints dashboard
- `commons-testing` → `DocumentSummaryViewTestBuilder`

> ---
> ### ⚠️ ADR-011 — Cohérence du Read Model CQRS (OBLIGATOIRE ici)
>
> **Pourquoi :** Si un event Kafka est perdu ou traité en erreur, le dashboard affiche un statut obsolète indéfiniment sans alerte. L'utilisateur voit un document "en cours" alors qu'il est traité depuis 2 heures.
>
> **Ce qui doit être implémenté dans ce module :**
>
> 1. Champ `lastSyncedAt` dans `document_summary_views` — mis à jour à chaque event Kafka traité par le `DashboardProjectionConsumer`
>
> 2. Job `ReadModelReconciliationScheduler` (toutes les 5 minutes) :
>    - Sélectionner les documents dont `updatedAt` (write-side) > `lastSyncedAt` + 30 secondes
>    - Pour chaque divergence détectée → resynchroniser le Read Model depuis la write-side
>    - Incrémenter `docai_read_model_desync_total` et logguer avec documentId + cause
>
> 3. Alerte Grafana : si `docai_read_model_desync_total` > 10 en 5 minutes → alerte Slack
>
> 4. Endpoint admin `POST /v1/admin/read-model/rebuild` pour reconstruction complète sans downtime (rejouer les events Kafka des 7 derniers jours dans une collection temporaire puis swap atomique)
>
> **Test obligatoire :** Arrêter le `DashboardProjectionConsumer` 10 minutes → reprendre → vérifier que le job de réconciliation rattrape le retard et met à jour tous les documents désynchronisés.
>
> **Référence complète :** Annexe E — ADR-011
> ---


> ---
> ### Références Annexes — Module 5.1 — Read Model CQRS & Analytics
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `DashboardProjectionConsumer (Adapter IN Kafka — met à jour document_summary_views + lastSyncedAt)`
> - `ReadModelReconciliationScheduler (Adapter — @Scheduled 5 min, détecte désynchronisations ADR-011)`
> - `GetDashboardSummaryUseCase (Application — lit uniquement le Read Model)`
> - `DocumentSummaryView (Read Model — dénormalisé, lastSyncedAt obligatoire ADR-011)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Champ lastSyncedAt présent dans toutes les entrées Read Model (ADR-011)
> - Job réconciliation testé : consumer arrêté 10 min → rattrapé à la reprise
> - Requêtes dashboard < 100ms avec 100 000 documents — EXPLAIN PLAN validé (ADR-010)
> - Partial index sur statuts actifs uniquement (ADR-010)
> - Alerte Grafana si > 10 désynchronisations en 5 min (ADR-011)
> - Métriques Read Model exposées : docai_readmodel_lag_seconds, docai_readmodel_desync_total
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer sur DashboardProjectionConsumer**
> - **commons-multitenancy → filtre tenant sur toutes les requêtes Read Model**
> - **commons-api → ApiResponse<T> pour tous les endpoints dashboard paginés**
> - **commons-testing → DocumentSummaryViewTestBuilder**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Read Model mis à jour : documentId, tenantId, fromStatus, toStatus, lastSyncedAt
> - WARN — Désynchronisation détectée : documentId, writeSideUpdatedAt, readModelLastSyncedAt, lag
> - INFO — Resynchronisation effectuée : documentId, tenantId, cause
> - ERROR — Échec mise à jour Read Model : documentId, raison
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** ADR-010 (index) et ADR-011 (lastSyncedAt + réconciliation) identifiés et compris.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

> ⚠️ **Pagination obligatoire ici** — Tous les endpoints liste du Dashboard utilisent le format paginé standard (BR-PAG-001 à BR-PAG-008). Voir Section 0.B — Stratégie de Pagination Globale.

#### Definition of Done — Phase 5.1

- [ ] Read Model mis à jour par tous les events du pipeline (testé TestContainers)
- [ ] Requêtes dashboard < 100ms avec 100 000 documents en base
- [ ] EXPLAIN PLAN MongoDB passé sur toutes les requêtes dashboard
- [ ] Endpoints protégés par JWT et isolation tenant

---

### Phase 5.2 — Alertes Temps Réel (SSE)

**Objectif :** Envoyer les alertes fraude aux clients frontend en < 2s via Server-Sent Events.

**Durée estimée :** 1 semaine

#### Business Rules

| ID | Règle |
|----|-------|
| BR-DSH-010 | Les alertes fraude (score > 50) sont envoyées en SSE < 2s après publication |
| BR-DSH-011 | Un client SSE ne reçoit que les alertes de son `tenant_id` |
| BR-DSH-012 | La connexion SSE est ré-établie automatiquement (Last-Event-ID) |
| BR-DSH-013 | L'endpoint SSE est protégé par JWT |

**Endpoint SSE :**
- `GET /v1/dashboard/stream` — flux SSE des alertes temps réel
- Header `Authorization: Bearer {JWT}` → isolation tenant automatique
- Header `Last-Event-ID` → reconnexion avec reprise depuis le dernier event reçu

**Adapters :**
- `SseNotificationAdapter` — Spring SseEmitter, map `tenant_id → List<SseEmitter>` actifs
- `AlertKafkaConsumer` — consomme `docai.doc.fraud.analyzed` → push SSE si score > 50

#### Definition of Done — Phase 5.2

- [ ] SSE testé : event Kafka → alerte reçue par client SSE < 2s
- [ ] Isolation tenant validée sur SSE (client A ne reçoit pas alertes client B)
- [ ] Reconnexion automatique testée avec Last-Event-ID
- [ ] Cleanup automatique des SseEmitters morts (keepalive)

---


---

### Phase 5.3 — Centre de Notifications In-App

**Objectif :** Fournir un historique persistant des notifications (alertes fraude, fin de quota, etc.) que l'utilisateur peut consulter et marquer comme lues.

**Durée estimée :** 3 jours

> **Différence avec SSE :** Le SSE envoie les alertes en temps réel mais ne les conserve pas. Le centre de notifications conserve **l'historique complet** des alertes même si l'utilisateur n'était pas connecté au moment de l'alerte.

#### Business Rules — Notifications

| ID | Règle | Priorité |
|----|-------|---------|
| BR-NOT-001 | Chaque alerte fraude (score > 50) génère une notification persistante | MUST |
| BR-NOT-002 | Les alertes quota (80%, 95%, dépassement) génèrent des notifications | MUST |
| BR-NOT-003 | Une notification peut être marquée lue / non lue | MUST |
| BR-NOT-004 | Le nombre de notifications non lues est accessible en temps réel (SSE) | MUST |
| BR-NOT-005 | Les notifications sont isolées par tenant et par utilisateur | MUST |
| BR-NOT-006 | Les notifications sont conservées 90 jours | SHOULD |
| BR-NOT-007 | Le TENANT_ADMIN peut voir les notifications de toute son équipe | SHOULD |

**Collection MongoDB `notifications` :**

| Champ | Type | Description |
|-------|------|-------------|
| `_id` | UUID | Identifiant notification |
| `tenantId` | String | Isolation tenant |
| `userId` | String | Destinataire (null = tous les utilisateurs du tenant) |
| `type` | String | FRAUD_ALERT, QUOTA_WARNING, QUOTA_EXCEEDED, PAYMENT_FAILED |
| `title` | String | Titre court de la notification |
| `message` | String | Message détaillé |
| `resourceId` | String | ID du document ou ressource concernée |
| `read` | Boolean | Lue ou non lue |
| `readAt` | DateTime | Date de lecture |
| `createdAt` | DateTime | Date de création (TTL index 90 jours) |

**Endpoints :**

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/v1/notifications` | ✅ JWT | Lister les notifications (paginées) |
| PUT | `/v1/notifications/{id}/read` | ✅ JWT | Marquer une notification comme lue |
| PUT | `/v1/notifications/read-all` | ✅ JWT | Marquer toutes comme lues |
| GET | `/v1/notifications/unread-count` | ✅ JWT | Nombre de notifications non lues |

**Commons utilisés :**
- `commons-multitenancy` → isolation tenant + userId sur chaque requête
- `commons-kafka` → consumer `NotificationKafkaConsumer` écoute tous les events pertinents
- `commons-api` → `ApiResponse<T>` pour la liste paginée

#### Definition of Done — Phase 5.3

- [ ] Notification créée automatiquement à chaque alerte fraude (score > 50)
- [ ] Notification créée à 80%, 95% et dépassement quota
- [ ] Marquage lu/non lu fonctionnel
- [ ] Compteur non lues accessible en temps réel (SSE)
- [ ] Isolation tenant et userId testée
- [ ] TTL 90 jours configuré sur la collection MongoDB

---

## Module 6 — Intégrations & API Publique

> **Bounded Context :** Exposer les fonctionnalités DocAI aux systèmes externes via une API publique sécurisée, des webhooks fiables et un système de quotas par plan.

### Phase 6.1 — API Publique & API Keys

**Objectif :** Exposer une API REST versionnée, documentée OpenAPI, consommable par les clients B2B avec authentification par API Key.

**Durée estimée :** 2 semaines

#### Business Rules

| ID | Règle |
|----|-------|
| BR-INT-001 | L'API publique est versionnée : `/v1/`, `/v2/` |
| BR-INT-002 | Les API Keys sont générées par le `TENANT_ADMIN` |
| BR-INT-003 | Une API Key est hashée SHA-256 + sel en MongoDB — jamais exposée après création |
| BR-INT-004 | Chaque API Key a un scope défini (READ, WRITE, ADMIN) |
| BR-INT-005 | Une API Key peut être révoquée à tout moment sans préavis |
| BR-INT-006 | Le rate limiting s'applique par API Key (mêmes règles que par tenant) |
| BR-INT-007 | La documentation OpenAPI 3.1 est générée automatiquement (SpringDoc) |

**Endpoints API Publique :**

| Méthode | Endpoint | Scope | Description |
|---------|----------|-------|-------------|
| POST | `/v1/documents` | WRITE | Soumettre un document |
| GET | `/v1/documents/{id}` | READ | Statut et résultats |
| GET | `/v1/documents` | READ | Liste paginée avec filtres |
| GET | `/v1/documents/{id}/extraction` | READ | Résultat extraction structuré |
| GET | `/v1/documents/{id}/fraud` | READ | Score et signaux fraude |
| POST | `/v1/documents/{id}/reprocess` | WRITE | Relancer le traitement |
| GET | `/v1/analytics` | READ | KPIs tenant |
| POST | `/v1/api-keys` | ADMIN | Créer une API Key |
| DELETE | `/v1/api-keys/{id}` | ADMIN | Révoquer une API Key |
| GET | `/v1/api-keys` | ADMIN | Lister les API Keys du tenant |

> ⚠️ **Contract Testing obligatoire** — Chaque endpoint public doit avoir un contrat Spring Cloud Contract. Voir Section CI/CD — Contract Testing.

**Commons utilisés dans ce module :**
- `commons-api` → `ApiResponse<T>`, `ProblemDetail`, versioning `/v1/`
- `commons-quota` → `@QuotaProtected` sur chaque endpoint de soumission
- `commons-multitenancy` → isolation tenant sur API Keys et webhooks
- `commons-audit` → `@Audited` sur création et révocation API Keys
- `commons-kafka` → `ResilientKafkaConsumer` sur le consumer webhooks
- `commons-testing` → `ApiKeyTestBuilder`, stubs WireMock pour endpoints externes


> ---
> ### Références Annexes — Module 6.1 — API Publique & API Keys
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `ApiKeyController (Adapter IN REST — TENANT_ADMIN uniquement)`
> - `CreateApiKeyUseCase (Application — hash SHA-256 + sel, jamais en clair)`
> - `RevokeApiKeyUseCase (Application — invalide cache Valkey immédiatement)`
> - `ApiKey (Aggregate — id, tenantId, hashedKey, scope, createdAt, lastUsedAt)`
> - `ApiKeyScope (Enum — READ, WRITE, ADMIN)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - API Keys hashées SHA-256 + sel — lecture directe en base ne révèle pas la valeur
> - Révocation effective immédiatement : cache Valkey invalidé, prochaine requête → HTTP 401
> - Rate limiting par API Key testé aux limites du plan
> - Documentation OpenAPI 3.1 générée et accessible (/swagger-ui.html)
> - Versioning API : header Deprecation + Sunset configurés si /v1/ déprécié (voir I.10)
> - Time-to-first-call < 1h validé avec un développeur externe (test réel)
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-api → ApiResponse<T>, ProblemDetail RFC 7807, versioning /v1/ (voir I.10)**
> - **commons-quota → @QuotaProtected sur chaque endpoint de soumission (ADR-001)**
> - **commons-multitenancy → isolation tenant sur API Keys**
> - **commons-audit → @Audited sur création et révocation API Keys**
> - **commons-kafka → consumer webhooks livraisons**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — API Key créée : tenantId, keyId, scope, createdBy=[PII_MASKED]
> - INFO — API Key révoquée : tenantId, keyId, revokedBy=[PII_MASKED]
> - WARN — API Key expirée utilisée : tenantId, keyId (jamais la valeur de la clé)
> - INFO — Appel API authentifié : tenantId, keyId, endpoint, durationMs
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Module 0 terminé. Stratégie versioning API (Section I.10) validée par le Tech Lead.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 6.1

- [ ] API Keys générées + hashées SHA-256 + sel (jamais en clair en base)
- [ ] Révocation API Key effective immédiatement (cache Valkey invalidé)
- [ ] Rate limiting par API Key testé
- [ ] Documentation OpenAPI 3.1 générée et accessible (`/swagger-ui.html`)
- [ ] Spec OpenAPI publiée sur GitHub Pages via job 05-documentation.yml (BR-OAS-004)
- [ ] Tous les endpoints ont descriptions + exemples réponse + codes erreur (BR-OAS-002)
- [ ] Time-to-first-call < 1h avec la documentation seule (test avec développeur externe)

---

### Phase 6.2 — Webhooks Fiables

**Objectif :** Notifier les systèmes externes des événements DocAI via webhooks avec garanties de livraison.

**Durée estimée :** 1 semaine

#### Business Rules

| ID | Règle |
|----|-------|
| BR-INT-010 | Chaque webhook est signé HMAC-SHA256 (header `X-DocAI-Signature`) |
| BR-INT-011 | Retry 5× avec backoff exponentiel (30s, 1min, 5min, 15min, 1h) |
| BR-INT-012 | Webhook non délivré après 5 tentatives → alerte dashboard |
| BR-INT-013 | Chaque livraison (tentative + résultat) est auditée en MongoDB |
| BR-INT-014 | Events supportés : `DOCUMENT_COMPLETED`, `FRAUD_DETECTED`, `REVIEW_REQUIRED` |

**Payload Webhook (exemple `DOCUMENT_COMPLETED`) :**
```json
{
  "eventId": "evt-uuid",
  "eventType": "DOCUMENT_COMPLETED",
  "tenantId": "acme-corp",
  "documentId": "doc-uuid",
  "occurredAt": "2026-05-14T10:00:00Z",
  "data": {
    "status": "COMPLETED",
    "type": "FACTURE",
    "fraudScore": 0,
    "riskLevel": "FAIBLE",
    "extractionScore": 0.97
  }
}
```

**Signature HMAC :** `X-DocAI-Signature: sha256=HMAC(secret, body)`


> ---
> ### Références Annexes — Module 6.2 — Webhooks Fiables
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `DeliverWebhookUseCase (Application — retry exponentiel, signature HMAC-SHA256)`
> - `WebhookDeliveryConsumer (Adapter IN Kafka — déclenche la livraison)`
> - `WebhookHttpAdapter (Adapter OUT — appel HTTP endpoint client + vérification réponse)`
> - `WebhookDelivery (Aggregate — tenantId, attempts[], status, hmacSignature)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - Signature HMAC-SHA256 vérifiée côté client (test WireMock)
> - Retry 5× testé avec backoff (30s, 1min, 5min, 15min, 1h)
> - Circuit Breaker sur endpoint webhook client testé
> - Log de livraison accessible via GET /v1/webhooks/{id}/deliveries
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-kafka → ResilientKafkaConsumer sur WebhookDeliveryConsumer**
> - **commons-audit → @Audited sur chaque tentative de livraison webhook**
> - **commons-multitenancy → isolation tenant sur WebhookDeliveryMongoAdapter**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Webhook livré : tenantId, webhookId, endpoint (sans payload complet), durationMs
> - WARN — Webhook retry : tenantId, webhookId, attempt N/5, httpStatus
> - ERROR — Webhook non livré après 5 tentatives : tenantId, webhookId, endpoint
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Module 6.1 terminé. URL de test webhook disponible (RequestBin ou équivalent).
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Phase 6.2

- [ ] Signature HMAC validée côté client (test WireMock)
- [ ] Retry 5× testé avec backoff (endpoint cible simulé en erreur avec WireMock)
- [ ] Log de livraison accessible via `GET /v1/webhooks/{id}/deliveries`
- [ ] Circuit Breaker sur webhook endpoint testé

---

### Phase 6.3 — Rate Limiting Avancé & Quotas

**Objectif :** Gestion complète des quotas par tenant et par plan, avec métriques d'usage et alertes de dépassement.

**Durée estimée :** 1 semaine

#### Business Rules

| ID | Règle |
|----|-------|
| BR-INT-020 | Le quota mensuel est réinitialisé le 1er de chaque mois à minuit UTC |
| BR-INT-021 | Notification tenant à 80% et 95% de consommation du quota |
| BR-INT-022 | L'usage temps réel est consultable via `/v1/analytics/usage` |
| BR-INT-023 | Le dépassement de quota retourne HTTP 429 avec date de réinitialisation |

**Plans disponibles :**

| Plan | Quota mensuel | Rate limit | Prix |
|------|--------------|-----------|------|
| Starter | 500 docs/mois | 100 req/min | Freemium |
| Pro | 10 000 docs/mois | 1 000 req/min | À définir |
| Enterprise | Illimité | Sur devis | À définir |

#### Definition of Done — Phase 6.3

- [ ] Réinitialisation quota mensuelle testée (scheduled job simulé)
- [ ] Notifications 80% et 95% testées
- [ ] Endpoint usage temps réel testé
- [ ] Rate limiting par API Key testé (distinct du rate limiting par tenant)

---


---

### Phase 5.4 — Fonctionnalités Avancées (Backlog v2)

> Ces fonctionnalités sont prévues pour la **version 2** de DocAI. Elles sont spécifiées ici pour anticiper les décisions d'architecture.

---

> ⚠️ **Tests de charge obligatoires** — Chaque endpoint de recherche doit avoir un test k6. Voir Annexe G.1.

#### 8. Recherche Full-Text sur les Documents

**Objectif :** Permettre au tenant de chercher dans le contenu extrait de ses documents.

```
Exemples de recherches :
  "toutes les factures ACME avec montantTTC > 1000€"
  "ordonnances du Dr Martin en octobre 2026"
  "documents avec SIRET 81969482600017"
```

**Business Rules :**

| ID | Règle |
|----|-------|
| BR-SRCH-001 | La recherche porte uniquement sur les données extraites (pas le PDF brut) |
| BR-SRCH-002 | La recherche est isolée par tenant (un tenant ne voit que ses résultats) |
| BR-SRCH-003 | Les résultats sont paginés (max 50 par page) |
| BR-SRCH-004 | Les champs PII chiffrés (CNI, IBAN) ne sont pas indexés pour la recherche |

**Choix technique :** MongoDB Atlas Search (index full-text natif) ou Elasticsearch selon le volume.

**Endpoint :**

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/v1/documents/search` | ✅ JWT | Recherche full-text avec filtres |

---

#### 9. Tags & Catégories sur les Documents

**Objectif :** Permettre au tenant d'organiser ses documents avec des tags personnalisés.

```
POST /v1/documents/{id}/tags
  body: { tags: ["urgent", "client-acme", "à-vérifier"] }

GET /v1/documents?tags=urgent,client-acme
  → Documents filtrés par tags
```

**Business Rules :**

| ID | Règle |
|----|-------|
| BR-TAG-001 | Un document peut avoir jusqu'à 10 tags |
| BR-TAG-002 | Un tag est une chaîne de 1 à 50 caractères, sans espaces (slugs) |
| BR-TAG-003 | Les tags sont propres à chaque tenant |
| BR-TAG-004 | La liste des tags utilisés par le tenant est accessible pour l'autocomplétion |

**Endpoints :**

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| PUT | `/v1/documents/{id}/tags` | ✅ ANALYST | Ajouter/modifier les tags |
| GET | `/v1/tags` | ✅ JWT | Liste des tags du tenant (autocomplétion) |

---

#### 10. Rapport Mensuel Automatique

**Objectif :** Envoyer un email mensuel au TENANT_ADMIN avec le résumé du mois.

**Contenu du rapport :**
```
Rapport DocAI — Octobre 2026 — ACME Corp

📄 Documents traités     : 423 / 500 (85%)
✅ Documents approuvés   : 398 (94%)
🚨 Fraudes détectées     : 12 (2.8%)
⏱️ Temps moyen traitement: 18 secondes
💰 Coût estimé ce mois  : 49€ (inclus dans votre plan)
📈 vs mois précédent    : +15% de documents
```

**Business Rules :**

| ID | Règle |
|----|-------|
| BR-RPT-001 | Le rapport est envoyé le 1er de chaque mois à 8h00 UTC |
| BR-RPT-002 | Le rapport est envoyé uniquement au(x) TENANT_ADMIN |
| BR-RPT-003 | Le tenant peut désactiver le rapport mensuel depuis ses préférences |
| BR-RPT-004 | Le rapport est également accessible via `GET /v1/analytics/monthly-report/{year}/{month}` |

---

#### 11. Période d'Essai Prolongeable

**Objectif :** Permettre à l'équipe DocAI de prolonger la période FREE d'un tenant (prospect chaud, partenaire, etc.) sans redéploiement.

```
L'équipe DocAI (rôle SYSTEM) appelle :
POST /v1/admin/tenants/{tenantId}/extend-trial
  body: { additionalDays: 30, reason: "Prospect stratégique" }
  → La période FREE est prolongée de 30 jours
  → Email envoyé au TENANT_ADMIN : "Bonne nouvelle ! Votre période d'essai a été prolongée"
  → AuditEntry créé (qui a prolongé, combien de jours, pourquoi)
```

**Business Rules :**

| ID | Règle |
|----|-------|
| BR-TRL-001 | Seul le rôle SYSTEM (équipe DocAI) peut prolonger une période d'essai |
| BR-TRL-002 | La prolongation maximale est de 90 jours supplémentaires |
| BR-TRL-003 | Une raison est obligatoire pour chaque prolongation (traçabilité) |
| BR-TRL-004 | Un AuditEntry est créé pour chaque prolongation |
| BR-TRL-005 | Le TENANT_ADMIN reçoit un email de notification avec la nouvelle date d'expiration |

**Endpoint :**

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/v1/admin/tenants/{id}/extend-trial` | ✅ SYSTEM | Prolonger la période FREE |
| GET | `/v1/admin/tenants/{id}/trial-history` | ✅ SYSTEM | Historique des prolongations |

---

---

## Module 7 — Billing & Abonnements

> **Bounded Context :** Gérer les plans d'abonnement, la facturation à l'usage et le cycle de vie commercial de chaque tenant.

> **Note :** Le Billing est développé en dernier (Partie 5) car il dépend du pipeline complet. Le Feature Flag `BILLING_ENABLED = false` permet de développer et tester toutes les Parties 3 et 4 gratuitement.

**Durée estimée :** 2 semaines


### Flow — Proposition de plan au TENANT_ADMIN (fin période FREE)

Le TENANT_ADMIN est la **seule personne** qui reçoit les propositions de plan et qui peut souscrire. C'est lui qui gère l'abonnement de son entreprise.

```
J-7 avant fin FREE
  → Email automatique à alice (TENANT_ADMIN)
     "Votre période gratuite se termine dans 7 jours"
     Bouton : "Voir les plans disponibles"

J-3 avant fin FREE
  → 2ème email de rappel à alice
     "Plus que 3 jours — continuez à utiliser DocAI"
     Bouton : "Choisir mon plan maintenant"

J-0 — Expiration FREE
  → Email à alice : "Votre période gratuite est terminée"
  → Compte passe en lecture seule (EXPIRED)
  → Dashboard affiche bannière :
     "⚠️ Votre accès gratuit a expiré. Choisissez un plan pour continuer."
     Bouton : "Voir les plans"

Alice clique "Voir les plans"
  → Page comparaison des 3 plans :

  ┌──────────────┬────────────────┬──────────────────┐
  │   STARTER    │      PRO       │   ENTERPRISE     │
  │   49€/mois   │  199€/mois     │   Sur devis      │
  │  500 docs    │  5 000 docs    │   Illimité       │
  │ +0.12€/doc   │  +0.08€/doc    │   Tarif négocié  │
  │  sup.        │  sup.          │                  │
  └──────────────┴────────────────┴──────────────────┘

Alice clique "Choisir Starter"
  → Redirigée vers Stripe Checkout
  → Entre sa carte bancaire
  → Paiement confirmé par Stripe
  → Compte passe en ACTIVE immédiatement
  → Email de confirmation d'abonnement envoyé à alice
  → Bannière dashboard disparaît
  → Upload de documents de nouveau disponible
```

> ---
> ### ⚠️ ADR-009 — Downgrade plan : conservation des données (OBLIGATOIRE ici)
>
> **Pourquoi :** Sans règle explicite, un développeur pourrait supprimer ou restreindre l'accès aux données au moment du downgrade → perte de données client et risque juridique.
>
> **Règles concrètes à implémenter dans `ChangeSubscriptionPlanUseCase` :**
>
> Lors d'un downgrade (ex: Pro → Starter) :
> 1. Ne JAMAIS supprimer ou masquer des documents existants
> 2. Le nouveau quota (500 docs/mois) s'applique uniquement à partir du 1er du mois suivant
> 3. L'overage du mois en cours est facturé au tarif du plan actuel (Pro) jusqu'à la fin de la période
> 4. Envoyer un email au TENANT_ADMIN expliquant l'impact **avant** de confirmer le downgrade
>
> Si la rétention change avec le downgrade :
> - Les données entre l'ancienne et la nouvelle durée de rétention → conservées 90 jours supplémentaires
> - Email de rappel envoyé 30 jours avant suppression définitive
>
> **Test obligatoire :** Créer un tenant Pro avec 4 200 documents → downgrader vers Starter → vérifier que les 4 200 documents sont tous accessibles en lecture le lendemain.
>
> **Référence complète :** Annexe E — ADR-009
> ---

**Règles importantes :**
- Seul le **TENANT_ADMIN** voit la page des plans et peut souscrire
- Les autres rôles (ANALYST, VIEWER) voient uniquement un message "Compte suspendu — contactez votre administrateur"
- Le TENANT_ADMIN peut changer de plan à tout moment depuis son dashboard
- Le downgrade (ex: Pro → Starter) est effectif en fin de période mensuelle
- L'upgrade (ex: Starter → Pro) est effectif immédiatement

> ⚠️ **Feature Flags** — Le flag `billing.enabled` est le premier flag à configurer dans Unleash. Voir Section 0.B — Feature Flags pour la procédure de déploiement progressif et le kill switch.

### Principe fondamental — Billing Feature Flag

**Le billing est contrôlé par un Feature Flag global activable/désactivable sans redéploiement.**

```
BILLING_ENABLED = false   → Phase de test, tout le monde est gratuit
BILLING_ENABLED = true    → Facturation active, plans appliqués
```

Cela permet de lancer DocAI en mode **totalement gratuit** pendant la phase de test, puis d'activer la facturation d'un seul changement de configuration, sans toucher au code.

**Comportement selon le flag :**

| Situation | BILLING_ENABLED = false | BILLING_ENABLED = true |
|-----------|------------------------|----------------------|
| Quota mensuel | Illimité pour tous | Appliqué selon le plan |
| Stripe | Aucun appel | Actif |
| Plans | Ignorés | Appliqués |
| Dépassement quota | Impossible | HTTP 429 |
| Email facturation | Non envoyé | Envoyé |

---

### Plans & Tarification — Modèle Hybride

Le modèle hybride combine un **abonnement mensuel fixe** (revenus prévisibles) et une **facturation à l'usage** au-delà du quota inclus (croissance avec le client).

| Plan | Prix mensuel | Documents inclus | Prix par doc supplémentaire | Usage |
|------|-------------|-----------------|----------------------------|-------|
| **FREE** | 0€ | 50 docs/mois | Non disponible | Phase de test / Découverte |
| **Starter** | 49€/mois | 500 docs/mois | 0.12€/doc | PME, comptables |
| **Pro** | 199€/mois | 5 000 docs/mois | 0.08€/doc | ETI, cabinets comptables |
| **Enterprise** | Sur devis | Illimité | Tarif négocié | Grands comptes |

**Règle du plan FREE :**
- Disponible uniquement pendant `BILLING_ENABLED = false` OU comme plan d'essai 30 jours
- Après 30 jours en FREE, le tenant est invité à choisir un plan payant
- Si aucun plan choisi après 30 jours, accès limité à la lecture seule

---

### Business Rules — Billing

| ID | Règle | Priorité |
|----|-------|---------|
| BR-BIL-001 | Si `BILLING_ENABLED = false`, aucune règle de quota ni de facturation ne s'applique | MUST |
| BR-BIL-002 | Si `BILLING_ENABLED = true`, chaque upload vérifie le quota et le plan | MUST |
| BR-BIL-003 | Le quota mensuel est réinitialisé le 1er de chaque mois à minuit UTC | MUST |
| BR-BIL-004 | Notification email au tenant à 80% de consommation du quota | MUST |
| BR-BIL-005 | Notification email au tenant à 95% de consommation du quota | MUST |
| BR-BIL-006 | Au dépassement du quota inclus, les documents supplémentaires sont facturés au tarif overage | MUST |
| BR-BIL-007 | Le dépassement est autorisé (pas de blocage) — il est facturé en fin de mois | MUST |
| BR-BIL-008 | Une facture Stripe est générée automatiquement le 1er de chaque mois | MUST |
| BR-BIL-009 | En cas de paiement échoué, le tenant reçoit 3 relances sur 7 jours | MUST |
| BR-BIL-010 | Après 3 relances sans paiement, le compte passe en `PAST_DUE` (lecture seule) | MUST |
| BR-BIL-011 | Le changement de plan est effectif immédiatement (upgrade) ou en fin de mois (downgrade) | MUST |
| BR-BIL-012 | La résiliation conserve les données 90 jours avant suppression définitive | MUST |

---

### Cycle de vie d'un abonnement

```
Inscription
    │
    ▼
FREE (30 jours d'essai)
    │
    ├── Choisit un plan payant
    │       ▼
    │   ACTIVE ──────────────────────────────────────────────┐
    │       │                                                 │
    │       ├── Paiement échoue                              │
    │       │       ▼                                         │
    │       │   PAST_DUE (lecture seule, 3 relances / 7j)   │
    │       │       │                                         │
    │       │       ├── Paiement régularisé → ACTIVE ────────┘
    │       │       └── Aucun paiement → SUSPENDED
    │       │                   ▼
    │       │               CANCELED (données 90j puis suppression)
    │       │
    │       └── Résiliation volontaire
    │               ▼
    │           CANCELED
    │
    └── Aucun plan choisi après 30 jours
            ▼
        EXPIRED (lecture seule)
```

---

### Intégration Stripe

**Pourquoi Stripe ?**
- Standard mondial du paiement SaaS
- Gestion native des abonnements récurrents
- Customer Portal self-service (le client gère sa CB lui-même)
- Webhooks fiables pour les événements de paiement

**Events Stripe écoutés par DocAI :**

| Event Stripe | Action DocAI |
|-------------|-------------|
| `checkout.session.completed` | Activer le plan choisi, démarrer l'abonnement |
| `invoice.paid` | Confirmer le paiement, réinitialiser le compteur overage |
| `invoice.payment_failed` | Passer en `PAST_DUE`, envoyer email relance |
| `customer.subscription.updated` | Mettre à jour le plan (upgrade/downgrade) |
| `customer.subscription.deleted` | Passer en `CANCELED`, programmer la suppression des données |

**Sécurité Stripe Webhooks :** Chaque event Stripe est vérifié par signature `Stripe-Signature` avant traitement. Un event non signé est rejeté avec HTTP 400.

---

### Calcul de la facture mensuelle

```
Exemple tenant Pro — mois d'octobre :

Abonnement Pro          : 199.00€
Documents inclus        : 5 000
Documents traités       : 6 200

Overage                 : 6 200 - 5 000 = 1 200 docs
Coût overage            : 1 200 × 0.08€ = 96.00€

─────────────────────────────────────────
Total facturé           : 199.00€ + 96.00€ = 295.00€
```

**Le compteur de documents traités est incrémenté à chaque `DocumentUploaded` event Kafka confirmé.**

---

### Use Cases — Billing

**UC-BIL-001 — Choisir un plan (après période FREE)**

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN clique sur "Choisir un plan" dans le dashboard |
| 2 | Redirection vers Stripe Checkout avec les paramètres du plan |
| 3 | TENANT_ADMIN entre ses informations de paiement sur la page Stripe |
| 4 | Stripe confirme → event `checkout.session.completed` envoyé à DocAI |
| 5 | DocAI active le plan, initialise le quota mensuel |
| 6 | Email de confirmation d'abonnement envoyé au TENANT_ADMIN |

**UC-BIL-002 — Consulter l'usage en temps réel**

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN consulte `GET /v1/billing/usage` |
| 2 | Retour : documents traités ce mois, quota inclus, overage estimé, coût estimé |

**UC-BIL-003 — Gérer sa carte bancaire (self-service)**

| Étape | Description |
|-------|-------------|
| 1 | TENANT_ADMIN clique sur "Gérer mon abonnement" |
| 2 | Redirection vers Stripe Customer Portal (géré par Stripe) |
| 3 | Le client met à jour sa CB, change de plan, télécharge ses factures |
| 4 | Stripe notifie DocAI des changements via webhooks |

---

### Scénarios BDD — Billing

```gherkin
Feature: Billing et abonnements — modèle hybride

  Scenario: Billing désactivé — aucune restriction
    Given BILLING_ENABLED = false
    And le tenant "acme-corp" a traité 10 000 documents ce mois
    When "acme-corp" soumet un nouveau document
    Then la soumission réussit sans restriction
    And aucun appel Stripe n'est effectué

  Scenario: Alerte quota 80%
    Given BILLING_ENABLED = true
    And le tenant "beta-corp" a un plan Starter (500 docs/mois)
    And "beta-corp" vient de traiter son 400ème document (80%)
    Then un email d'alerte est envoyé au TENANT_ADMIN
    And le message indique "400/500 documents utilisés ce mois"

  Scenario: Dépassement quota — overage autorisé et facturé
    Given BILLING_ENABLED = true
    And le tenant "gamma-corp" a un plan Pro (5000 docs/mois inclus)
    And "gamma-corp" a traité 5001 documents
    When "gamma-corp" soumet un nouveau document (le 5002ème)
    Then la soumission réussit (pas de blocage)
    And le compteur overage est incrémenté
    And ce document sera facturé 0.08€ en fin de mois

  Scenario: Paiement échoué — passage en PAST_DUE
    Given Stripe envoie l'event "invoice.payment_failed" pour "delta-corp"
    When DocAI reçoit cet event (signature vérifiée)
    Then le statut de "delta-corp" passe en PAST_DUE
    And "delta-corp" peut toujours lire ses documents (lecture seule)
    And "delta-corp" ne peut plus soumettre de nouveaux documents
    And un email de relance est envoyé au TENANT_ADMIN

  Scenario: Régularisation paiement
    Given le tenant "delta-corp" est en PAST_DUE
    And Stripe envoie l'event "invoice.paid"
    When DocAI reçoit cet event
    Then le statut de "delta-corp" repasse en ACTIVE
    And "delta-corp" peut à nouveau soumettre des documents
    And un email de confirmation est envoyé
```

---

### Emails transactionnels — Billing

| Déclencheur | Destinataire | Contenu |
|-------------|-------------|---------|
| Abonnement activé | TENANT_ADMIN | Confirmation plan + quota inclus + date renouvellement |
| Quota 80% atteint | TENANT_ADMIN | Alerte usage + lien pour upgrader |
| Quota 95% atteint | TENANT_ADMIN | Alerte urgente usage + coût overage estimé |
| Facture générée | TENANT_ADMIN | Facture du mois + détail overage |
| Paiement échoué | TENANT_ADMIN | Relance + lien Customer Portal Stripe |
| Abonnement résilié | TENANT_ADMIN | Confirmation + rappel conservation données 90 jours |

---

### Architecture Hexagonale — Module 0.2

**Domain Model :**
- `Subscription` — Aggregate (tenantId, plan, status, stripeCustomerId, stripeSubscriptionId, currentPeriodStart, currentPeriodEnd)
- `UsageCounter` — Value Object (docsProcessed, docsIncluded, overageCount, periodStart)
- `Plan` — Enum (FREE, STARTER, PRO, ENTERPRISE)
- `SubscriptionStatus` — Enum (TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELED, EXPIRED)
- `SubscriptionActivated`, `PaymentFailed`, `QuotaThresholdReached` — Domain Events

**Inbound Ports :**
- `PORT-IN-BIL-001` — `ActivateSubscriptionUseCase`
- `PORT-IN-BIL-002` — `GetUsageUseCase`
- `PORT-IN-BIL-003` — `HandleStripeWebhookUseCase`
- `PORT-IN-BIL-004` — `ChangeSubscriptionPlanUseCase`

**Outbound Ports :**
- `PORT-OUT-BIL-001` — `PaymentGatewayPort` (Stripe)
- `PORT-OUT-BIL-002` — `SubscriptionRepositoryPort`
- `PORT-OUT-BIL-003` — `UsageCounterPort` (Valkey)
- `PORT-OUT-BIL-004` — `EmailNotificationPort`
- `PORT-OUT-BIL-005` — `BillingFeatureFlagPort`

**Adapters :**
- `StripePaymentAdapter` — Stripe SDK, Checkout Sessions, Customer Portal, webhooks
- `SubscriptionMongoAdapter` — persistance abonnements
- `ValkeyUsageCounterAdapter` — compteurs atomiques documents traités par mois
- `BillingFeatureFlagAdapter` — lecture `BILLING_ENABLED` depuis configuration Spring

**Collection MongoDB `subscriptions` :**

| Champ | Type | Description |
|-------|------|-------------|
| `_id` | UUID | Identifiant abonnement |
| `tenantId` | String | Tenant concerné |
| `plan` | String | FREE, STARTER, PRO, ENTERPRISE |
| `status` | String | TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELED |
| `stripeCustomerId` | String | ID client Stripe |
| `stripeSubscriptionId` | String | ID abonnement Stripe |
| `trialEndsAt` | DateTime | Fin de période d'essai (30 jours) |
| `currentPeriodStart` | DateTime | Début période de facturation courante |
| `currentPeriodEnd` | DateTime | Fin période de facturation courante |
| `billingEnabled` | Boolean | Snapshot du flag au moment de l'abonnement |

**Endpoints :**

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| GET | `/v1/billing/plans` | ❌ Public | Lister les plans disponibles |
| POST | `/v1/billing/checkout` | `TENANT_ADMIN` | Démarrer Stripe Checkout |
| GET | `/v1/billing/portal` | `TENANT_ADMIN` | Accéder au Customer Portal Stripe |
| GET | `/v1/billing/usage` | `TENANT_ADMIN` | Usage temps réel du mois courant |
| GET | `/v1/billing/subscription` | `TENANT_ADMIN` | Détail abonnement courant |
| POST | `/v1/billing/webhooks/stripe` | ❌ Public (signé Stripe) | Recevoir les events Stripe |

**NFR — Billing :**

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-BIL-001 | Signature Stripe webhook vérifiée en < 50ms | 100% |
| NFR-BIL-002 | Compteur usage mis à jour après chaque document traité | Temps réel |
| NFR-BIL-003 | Latence endpoint usage temps réel | < 100ms |
| NFR-BIL-004 | Idempotence webhooks Stripe (même event reçu 2× → traité 1× seulement) | 100% |


> ---
> ### Références Annexes — Module 0.2 — Billing & Abonnements
>
> #### Annexe B — Clean Code & Nommage (appliquer ici)
> Nommage obligatoire dans ce module :
> - `ActivateSubscriptionUseCase (Application — implémente ActivateSubscriptionPort)`
> - `HandleStripeWebhookUseCase (Application — implémente HandleStripeWebhookPort)`
> - `StripePaymentAdapter (Adapter OUT — implémente PaymentGatewayPort)`
> - `SubscriptionMongoAdapter (Adapter OUT — implémente SubscriptionRepositoryPort)`
> - `Plan (Enum domaine : FREE, STARTER, PRO, ENTERPRISE)`
> - `SubscriptionStatus (Enum domaine : TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELED)`
> Règles : méthodes ≤ 20 lignes, classes ≤ 200 lignes, complexité ≤ 10, couverture domaine ≥ 90%.
> Tests nommés : `should_<résultat>_when_<contexte>()`.
>
> #### Annexe C — Production Readiness (vérifier avant déploiement)
> - BILLING_ENABLED configuré selon l'environnement (false en DEV/STAGING, true en PROD)
> - Clé Stripe TEST en staging, clé Stripe LIVE en production uniquement
> - Webhooks Stripe : signature vérifiée sur 100% des events — vérifier le filtre
> - Idempotence webhooks Stripe testée : même event reçu 2× → traité 1× seulement
> - Compteurs quota Valkey : script Lua atomique utilisé (ADR-001)
> - Alertes quota 80% et 95% testées en staging avant release prod
>
> #### Annexe D — Commons à utiliser (ne pas réimplémenter)
> - **commons-quota → QuotaPort, @QuotaProtected AOP, ValkeyQuotaAdapter (script Lua atomique ADR-001)**
> - **commons-audit → @Audited sur ActivateSubscriptionUseCase, HandleStripeWebhookUseCase**
> - **commons-api → ProblemDetail pour erreurs QUOTA-001, RATE-001**
> - **commons-multitenancy → isolation tenant sur toutes les requêtes Stripe et abonnements**
>
> #### Annexe F.1 — Logs obligatoires dans ce module
> - INFO — Abonnement activé : tenantId, plan, stripeSubscriptionId
> - WARN — Quota 80% atteint : tenantId, docsProcessed, docsIncluded
> - ERROR — Paiement échoué : tenantId, stripeInvoiceId (jamais les données CB)
> - INFO — Overage facturé : tenantId, extraDocs, overageCost
> - ERROR — Webhook Stripe signature invalide : IP source, event type
> Jamais de PII dans les logs → remplacer par `[PII_MASKED]`.
> Toujours inclure `traceId` et `tenantId` dans chaque log.
>
> #### Annexe F.2 — Definition of Ready avant de commencer
> Vérifier les 10 critères DoR avant de démarrer une US de ce module.
> **Spécifique à ce module :** Vérifier que BILLING_ENABLED = false avant de démarrer les tests. Stripe en mode TEST obligatoire.
> Voir Section II.1 — Definition of Ready pour la checklist complète.
> ---

#### Definition of Done — Module 0.2

- [ ] Feature Flag `BILLING_ENABLED` testé : false → aucune restriction, true → plans appliqués
- [ ] Plan FREE fonctionnel avec limite 50 docs/mois et expiration 30 jours
- [ ] Stripe Checkout testé (mode test Stripe)
- [ ] Tous les webhooks Stripe testés avec WireMock (signature vérifiée)
- [ ] Idempotence webhooks Stripe testée (event reçu 2× → traité 1× seulement)
- [ ] Alertes quota 80% et 95% testées et emails envoyés
- [ ] Overage calculé correctement et persisté
- [ ] Cycle de vie complet testé : TRIAL → ACTIVE → PAST_DUE → ACTIVE (régularisation)
- [ ] Cycle de vie complet testé : ACTIVE → CANCELED → données 90j puis suppression
- [ ] Customer Portal Stripe accessible
- [ ] Endpoint usage temps réel testé avec données Valkey

---

---

# Annexes — Référence Technique

> **Les annexes sont des documents de référence.** Elles ne contiennent pas de code à développer mais des standards, décisions et checklists à consulter pendant le développement.

---

## Annexe A — Roadmap Globale Backend

| Phase | Module | Objectif | Durée | Semaines |
|-------|--------|----------|-------|---------|
| 0 | Sécurité & Multi-Tenancy | Fondations, Keycloak, TenantFilter, Error Handling | 2 sem. | 1–2 |
| 0.1 | Inscription & Équipe | Signup automatique, invitations, emails Amazon SES | 1 sem. | 3 |
| 0.2 | Login / Logout / 2FA | Sessions, refresh token, mot de passe oublié | 3 jours | 3 |
| 0.3 | Profil utilisateur | Changement MDP, email, historique connexion, 2FA | 3 jours | 4 |
| 0.4 | Billing & Abonnements | Feature Flag, plans hybrides, Stripe, quotas | 2 sem. | 5–6 |
| 0.5 | RGPD & Privacy | Rétention, effacement, export, chiffrement PII | 1 sem. | 7 |
| 1.1 | Reconnaissance | Upload, S3, Outbox, Idempotence | 2 sem. | 7–8 |
| 1.2 | Reconnaissance | Classification IA + résilience | 3 sem. | 9–11 |
| 2.1 | Extraction | OCR + LLM + cache Valkey | 3 sem. | 12–14 |
| 2.2 | Extraction | Validation métier + APIs externes | 2 sem. | 15–16 |
| 2.3 | Extraction | Correction manuelle + audit | 1 sem. | 17 |
| 3.1 | Fraude | Scoring + signaux données | 2 sem. | 18–19 |
| 3.2 | Fraude | Analyseurs avancés (Tika + visuel) | 2 sem. | 20–21 |
| 3.3 | Fraude | Workflow révision + SSE alertes | 2 sem. | 22–23 |
| 4.1 | Orchestration | Pipeline Kafka + idempotence | 2 sem. | 24–25 |
| 4.2 | Orchestration | Retry + DLQ | 1 sem. | 26 |
| 4.3 | Orchestration | Saga + compensation | 1 sem. | 27 |
| 5.1 | Dashboard | Read Model CQRS + Analytics | 1 sem. | 28 |
| 5.2 | Dashboard | SSE temps réel | 1 sem. | 29 |
| 6.1 | Intégrations | API publique + API Keys | 2 sem. | 30–31 |
| 6.2 | Intégrations | Webhooks fiables | 1 sem. | 32 |
| 6.3 | Intégrations | Rate limiting avancé + quotas | 1 sem. | 33 |

**Durée totale : 35 semaines (~9 mois)**  
Avec une équipe de 2 développeurs backend seniors, les phases peuvent être parallélisées à partir de la semaine 12.

> **Note :** Les semaines 1 à 6 (Modules 0, 0.1, 0.2, 0.3) sont les **fondations SaaS non négociables** — elles doivent être terminées et validées avant de démarrer le Module 1.

---

## Annexe C — Production Readiness Checklist

**Sécurité :**
- [ ] Secrets managés via AWS Secrets Manager ou HashiCorp Vault (pas de `.env` en production)
- [ ] WAF (Web Application Firewall) devant le load balancer
- [ ] TLS 1.3 minimum sur tous les endpoints publics
- [ ] OWASP Top 10 passé : scan OWASP ZAP ou Snyk en CI
- [ ] Headers sécurité HTTP : CSP, HSTS, X-Frame-Options, X-Content-Type-Options
- [ ] Audit log immuable (pas de suppression possible)
- [ ] Pentest externe avant le lancement public

**Fiabilité :**
- [ ] MongoDB : 3 nodes replica set en production (1 primary + 2 secondary)
- [ ] Kafka : 3 brokers minimum, replication factor 3
- [ ] Load balancer avec health checks sur `/actuator/health`
- [ ] Circuit Breaker testé : que se passe-t-il si OpenAI est down ?
- [ ] DLQ monitorée : alerte Grafana si > 10 messages en DLQ
- [ ] Outbox relay monitoré : alerte si délai > 30 secondes
- [ ] Backup MongoDB : snapshot quotidien, test de restauration mensuel
- [ ] Amazon S3 : versioning activé sur le bucket de production
- [ ] Rotation des secrets applicatifs planifiée : dates d'expiration configurées dans AWS Secrets Manager (BR-ROT-002)

**Observabilité :**
- [ ] Dashboards Grafana : pipeline, fraude, API, JVM heap, Kafka lag
- [ ] Alertes configurées : latence P95, taux erreur, lag Kafka, heap JVM
- [ ] Logs structurés JSON avec traceId, tenantId, userId sur tous les services
- [ ] Grafana OnCall configuré (escalade selon sévérité)
- [ ] SLA défini et publié : 99.9% uptime, < 30s traitement P95
- [ ] Status page publique (Instatus ou Statuspage.io)

**Scalabilité :**
- [ ] HPA Kubernetes : scale-out basé sur CPU et lag Kafka
- [ ] Stress test passé : 2× la charge maximale attendue (Annexe G.1)
- [ ] EXPLAIN PLAN MongoDB sur toutes les requêtes dashboard (ADR-010)

**Monitoring des Coûts AWS :**

> **Pourquoi critique :** Une boucle infinie d'OCR ou un upload malveillant peut générer des milliers d'euros en une nuit sans alerte de coût.

- [ ] **AWS Budget configuré** : alerte si dépenses S3 > 150% du mois précédent
- [ ] **AWS Cost Explorer** : dashboard mensuel des coûts par service (S3, KMS, SES, Secrets Manager)
- [ ] **Alerte coût S3** : si coût S3 dépasse le budget mensuel défini → alerte Slack immédiate
- [ ] **Alerte coût KMS** : surveillance des appels déchiffrement (pic anormal = compromission possible)
- [ ] **Lifecycle Rule S3** configurée (ADR-007) : objets multipart non finalisés supprimés après 24h
- [ ] **S3 Glacier** : documents archivés > rétention → Glacier (10× moins cher que S3 Standard)
- [ ] **Rapport mensuel coûts** : envoyé automatiquement au Tech Lead le 1er du mois

**Comment configurer les alertes de coût AWS :**
1. AWS Console → Billing → Budgets → Créer un budget mensuel par service
2. Configurer le seuil d'alerte à 80% et 100% du budget mensuel
3. Connecter l'alerte à un topic SNS → Lambda → Slack webhook
4. Tester en dépassant artificiellement le seuil en staging

**Pentest — Planning & Scope :**

> **Obligatoire avant le lancement public.** Sans pentest, des vulnérabilités critiques peuvent rester non détectées malgré le scan OWASP automatique.

- [ ] **Pentest interne** : scan OWASP ZAP automatisé en CI sur chaque release (déjà prévu)
- [ ] **Pentest externe** : cabinet spécialisé, avant le lancement public (une seule fois)
- [ ] **Scope pentest externe** :
  - API publique REST (/v1/*)
  - Endpoints Stripe webhooks
  - Endpoints authentification (login, inscription, refresh)
  - Isolation tenant (tentatives d'accès croisé)
  - Upload de fichiers malveillants (PDF avec scripts, images corrompues)
- [ ] **Fréquence pentest externe** : annuel ou avant chaque release majeure (v2, v3)
- [ ] **Rapport pentest** : archivé dans le wiki, vulnérabilités critiques corrigées avant release
- [ ] **Bug bounty** : à envisager en v2 (HackerOne ou Bugcrowd) pour les grands comptes Enterprise

---


---

### **Rotation des Secrets Applicatifs**

> **Où :** À planifier avant le lancement. La rotation des secrets applicatifs est distincte de la rotation des clés KMS (ADR-005) — elle concerne les credentials d'accès aux APIs externes.

**Secrets applicatifs à rotation régulière :**

| Secret | Emplacement | Fréquence rotation | Responsable |
|--------|------------|-------------------|-------------|
| Clé API OpenAI / Mistral | AWS Secrets Manager | Tous les 90 jours | Tech Lead |
| Credentials API INSEE | AWS Secrets Manager | Tous les 6 mois | Tech Lead |
| Clé secrète Stripe | AWS Secrets Manager | En cas de compromission | Tech Lead |
| Clé secrète Keycloak client | AWS Secrets Manager | Tous les 90 jours | Tech Lead |
| Webhook secret Stripe | AWS Secrets Manager | Tous les 90 jours | Tech Lead |
| HMAC secret webhooks DocAI | AWS Secrets Manager | Tous les 180 jours | Tech Lead |

**Comment faire une rotation sans interruption de service :**

```
Procédure rotation clé API LLM (OpenAI) :
  1. Générer une nouvelle clé API sur le portail OpenAI
  2. Mettre à jour le secret dans AWS Secrets Manager
  3. Forcer le rechargement du secret dans l'application (sans redéploiement) :
     → Spring Cloud AWS lit automatiquement la nouvelle valeur au prochain refresh
     → Ou : POST /actuator/refresh (Spring Cloud Config)
  4. Vérifier dans les logs que les appels LLM réussissent avec la nouvelle clé
  5. Révoquer l'ancienne clé sur le portail OpenAI
  6. Ajouter une entrée dans le journal de rotation (Google Sheet ou wiki)
```

**Alerte rotation manquée :**
Configurer une alerte dans AWS Secrets Manager si un secret n'a pas été rotaté depuis > 90 jours.

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-ROT-001 | Tous les secrets applicatifs sont dans AWS Secrets Manager | MUST |
| BR-ROT-002 | Chaque secret a une date d'expiration configurée dans AWS | MUST |
| BR-ROT-003 | La rotation est documentée dans un journal de rotation | MUST |
| BR-ROT-004 | La rotation ne nécessite jamais un redéploiement complet | MUST |

---

### **Chaos Engineering — Scénarios de Panne Planifiés**

> **Où :** À intégrer dans le planning de release. Tester les pannes avant qu'elles arrivent en production.

**Pourquoi le Chaos Engineering :**
Les ADR définissent comment gérer les pannes (Circuit Breaker, fallback, DLQ). Le Chaos Engineering vérifie que ces mécanismes fonctionnent réellement en conditions quasi-réelles.

**Scénarios de chaos à exécuter en staging avant chaque release majeure :**

| # | Scénario | Ce qui est testé | Résultat attendu |
|---|----------|-----------------|-----------------|
| 1 | Arrêter Keycloak pendant 20 min | Cache JWKS (ADR-006) | Utilisateurs connectés non bloqués pendant 1h |
| 2 | Arrêter Kafka pendant 5 min | Outbox Pattern | Zéro perte de documents, publication à la reprise |
| 3 | Saturer le LLM (renvoyer 429) | Circuit Breaker + fallback | Documents en NEEDS_REVIEW, pas de crash |
| 4 | Remplir le disque MongoDB à 95% | Health check diskspace | Alerte Grafana + pod retiré du trafic |
| 5 | Arrêter 1 pod sur 3 en production | RollingUpdate zero-downtime | 0 erreur HTTP pendant la panne |
| 6 | Dépasser le quota LLM | Fallback OCR | Extraction partielle, pas de blocage pipeline |
| 7 | Flood de documents (×10 normal) | HPA + Kafka consumer lag | Scale-out automatique, lag résorbé en < 5 min |

**Comment exécuter le Chaos Engineering :**

En staging, utiliser des commandes simples sans outil dédié :
- `docker stop keycloak` → tester pendant 20 min → `docker start keycloak`
- `docker stop kafka` → vérifier l'Outbox → `docker start kafka`
- WireMock stub LLM retournant 503 → vérifier Circuit Breaker

En production (avec précaution) : uniquement les scénarios #5 (tuer un pod) et #7 (load test).

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-CHAOS-001 | Les 7 scénarios de chaos sont exécutés en staging avant chaque release majeure | MUST |
| BR-CHAOS-002 | Les résultats de chaque test chaos sont documentés dans le wiki | MUST |
| BR-CHAOS-003 | Un scénario chaos en production requiert l'approbation du Tech Lead | MUST |
| BR-CHAOS-004 | Chaque ADR est validé par un test chaos correspondant | SHOULD |

---

### **Publication OpenAPI — Portail Développeur**

> **Où :** À configurer dans le workflow CI/CD Phase 6 (Documentation). Permet aux clients B2B d'intégrer l'API sans aide manuelle.

**Pourquoi un portail développeur :**
Un client qui veut intégrer DocAI ne devrait pas avoir besoin de contacter le support. La spec OpenAPI publiée automatiquement lui donne tout ce dont il a besoin : endpoints, schemas, exemples, codes d'erreur.

**Comment automatiser la publication de la spec OpenAPI :**

**Étape 1 — Générer la spec OpenAPI en CI :**
```
Job 05-documentation.yml :
  1. Builder le projet
  2. Démarrer l'application en mode test
  3. Appeler GET /v3/api-docs → télécharger la spec JSON
  4. Valider la spec avec openapi-generator-cli (format valide ?)
  5. Publier dans GitHub Releases : docai-api-spec-v1.2.0.json
```

**Étape 2 — Publier sur GitHub Pages :**
La spec est publiée sur GitHub Pages sous `docs.docai.fr/api` :
- Swagger UI hébergé statiquement (aucun serveur requis)
- URL stable : `https://docs.docai.fr/api/v1`
- Version archivée : `https://docs.docai.fr/api/v1/archive/1.0.0`

**Étape 3 — Générer les SDK clients :**
Optionnel mais recommandé pour les clients Enterprise :
- SDK JavaScript/TypeScript : `openapi-generator-cli generate -g typescript-axios`
- SDK Python : `openapi-generator-cli generate -g python`
- Publiés dans GitHub Releases avec chaque release de l'API

**Contenu obligatoire de la spec OpenAPI :**

Chaque endpoint doit avoir dans la spec :
- Description claire du comportement
- Tous les codes de réponse possibles (200, 201, 400, 401, 403, 404, 429, 500)
- Schéma JSON de chaque réponse avec exemples
- Headers requis (Authorization, X-Idempotency-Key)
- Description des erreurs RFC 7807

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-OAS-001 | La spec OpenAPI est générée automatiquement en CI sur chaque tag de release | MUST |
| BR-OAS-002 | Chaque endpoint a une description et des exemples de réponse | MUST |
| BR-OAS-003 | Les erreurs RFC 7807 sont documentées dans la spec | MUST |
| BR-OAS-004 | La spec est publiée sur GitHub Pages avec Swagger UI | MUST |
| BR-OAS-005 | Les anciennes versions de spec sont archivées (pas supprimées) | MUST |
| BR-OAS-006 | Un SDK client est généré et publié pour les releases majeures | SHOULD |


## Annexe E — Décisions Architecturales Critiques (ADR)

> **ADR = Architecture Decision Record**  
> Ces 4 problèmes ont été identifiés comme des **bugs architecturaux garantis en production** s'ils ne sont pas traités avant le démarrage du développement.

---

### ADR-001 — Concurrence sur les compteurs de quota

**Problème :**
Si 50 utilisateurs d'un même tenant uploadent simultanément, le compteur Valkey peut être lu avant d'être incrémenté par un autre thread. Résultat : le tenant dépasse son quota sans que le système le détecte.

```
Scénario problématique :
  Thread 1 lit quota = 499/500 → autorisé
  Thread 2 lit quota = 499/500 → autorisé (avant incrément Thread 1)
  Thread 1 incrémente → 500/500
  Thread 2 incrémente → 501/500 ← DÉPASSEMENT non détecté
```

**Décision retenue : Script Lua atomique Valkey**

Valkey exécute les scripts Lua de façon atomique — aucun autre thread ne peut interrompre l'exécution. Le check et l'incrément se font en une seule opération indivisible.

```
Logique du script (sans code) :
  1. Lire la valeur courante du compteur
  2. Si valeur >= quota → retourner QUOTA_EXCEEDED sans incrémenter
  3. Sinon → incrémenter et retourner ALLOWED
  Ces 3 étapes sont atomiques — impossible d'être interrompues
```

**Règles à ajouter dans BR-INT-020 :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-CONC-001 | Toute vérification + incrément de quota utilise un script Lua atomique Valkey | MUST |
| BR-CONC-002 | Aucune lecture-modification-écriture séparée sur les compteurs de quota | MUST |
| BR-CONC-003 | Le script Lua est testé avec 100 threads simultanés — le compteur ne doit jamais dépasser le quota | MUST |

**Impact sur les modules :**
- `commons-quota` → `ValkeyQuotaAdapter` doit utiliser un script Lua pour `checkAndConsume()`
- Module 1 (Upload) → la vérification quota passe par ce script atomique
- Module 6.3 (Rate Limiting) → même logique pour les compteurs de rate limiting

---

### ADR-002 — Ordering des events Kafka par document

**Problème :**
Avec 6 partitions sur les topics principaux et une clé de partition = `tenantId`, plusieurs documents du même tenant sont répartis sur toutes les partitions. Deux events pour le **même document** peuvent atterrir sur des partitions différentes et être traités dans le mauvais ordre.

```
Scénario problématique :
  DocumentClassified     → partition 3
  ClassificationCorrected → partition 1  (tenantId hash différent)

  Consumer partition 1 traite ClassificationCorrected EN PREMIER
  Consumer partition 3 traite DocumentClassified ENSUITE
  → Le document est écrasé avec l'état ancien
  → État incohérent
```

**Décision retenue : Clé de partition = `documentId` (pas `tenantId`)**

Tous les events concernant le même document utilisent `documentId` comme clé de partition. Kafka garantit que tous les events avec la même clé vont sur la même partition, dans l'ordre d'arrivée.

```
Avant (incorrect) :
  Clé partition = tenantId
  → Events d'un même document sur des partitions différentes
  → Ordre non garanti

Après (correct) :
  Clé partition = documentId
  → Tous les events d'un même document sur la même partition
  → Ordre FIFO garanti pour chaque document
```

**Conséquence sur l'isolation tenant :**
L'isolation tenant n'est plus garantie par la partition mais par le filtre `tenantId` dans MongoDB (déjà en place). La partition sert uniquement à garantir l'ordre des events par document.

**Règles à ajouter dans BR-ORC :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-ORD-001 | La clé de partition Kafka est le `documentId` pour tous les topics du pipeline | MUST |
| BR-ORD-002 | Les topics analytics et notifications utilisent `tenantId` comme clé (ordre par tenant) | MUST |
| BR-ORD-003 | Le consumer vérifie que l'event reçu correspond bien au statut attendu du document avant traitement | MUST |
| BR-ORD-004 | Un event reçu dans le mauvais ordre (statut document incompatible) est envoyé en DLQ avec motif ORDER_VIOLATION | MUST |

**Impact sur les modules :**
- Module 4.1 (Pipeline Kafka) → modifier la clé de partition sur tous les topics pipeline
- `commons-kafka` → `OutboxKafkaProducer` utilise `documentId` comme clé
- Topologie Kafka dans la section I.5 → mettre à jour

---

### ADR-003 — Thundering Herd sur le cache Valkey

**Problème :**
Si 1000 documents ont été mis en cache Valkey avec le même TTL de 24h, ils expirent tous au même instant. Tous les threads appellent le LLM simultanément, le surchargent, le circuit breaker s'ouvre, et 1000 documents passent en NEEDS_REVIEW.

```
Scénario problématique :
  09h00 : 1000 documents mis en cache (TTL = 24h exactement)
  09h00 le lendemain : 1000 expirations simultanées
  09h00 : 1000 appels LLM simultanés
  09h00 : LLM surchargé → timeout → Circuit Breaker OPEN
  09h00 : 1000 documents en NEEDS_REVIEW
```

**Décision retenue : TTL avec jitter (variation aléatoire)**

Au lieu d'un TTL fixe de 24h, on ajoute une variation aléatoire entre -30 et +30 minutes. Les expirations sont ainsi réparties sur une fenêtre d'1 heure au lieu d'être simultanées.

```
Sans jitter : TTL = 86400 secondes (24h exactement)
Avec jitter  : TTL = 86400 + random(-1800, +1800) secondes
               → entre 23h30 et 24h30

Résultat : au lieu de 1000 expirations simultanées,
           on a ~28 expirations par minute sur 1 heure
           → charge LLM lissée, pas de pic
```

**Règles à ajouter dans la stratégie cache :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-CACHE-001 | Tout TTL Valkey supérieur à 1 heure applique un jitter aléatoire de ±10% | MUST |
| BR-CACHE-002 | Le jitter est appliqué côté adapter, transparent pour le use case | MUST |
| BR-CACHE-003 | Les TTL de rate limiting (< 1 min) n'appliquent pas de jitter | MUST |

**TTL avec jitter par type de données :**

| Données | TTL de base | Jitter | TTL effectif |
|---------|------------|--------|-------------|
| Résultats LLM | 24h | ±30 min | 23h30 – 24h30 |
| Validations SIRET | 7 jours | ±6h | 6j18h – 7j6h |
| Validations adresses BAN | 7 jours | ±6h | 6j18h – 7j6h |
| Profils tenant | 15 min | ±2 min | 13 – 17 min |
| Statuts documents | 5 min | aucun | 5 min exactement |

**Impact sur les modules :**
- `commons-kafka` → méthode utilitaire `withJitter(baseTtl)` dans le cache adapter
- Module 2.1 (Extraction) → `ValkeyExtractionCacheAdapter` applique le jitter
- Module 2.2 (Validation) → `InseeApiAdapter`, `BanApiAdapter` appliquent le jitter
- Section I.6 (Stratégies Cache) → mettre à jour le tableau des TTL

---

### ADR-004 — Limite de transaction MongoDB à 4MB

**Problème :**
MongoDB limite les transactions à 4MB de données écrites en une seule transaction. Si un document PDF de 100 pages produit un texte OCR très long, l'écriture atomique `ExtractionResult + OutboxEvent` dans la même transaction peut dépasser cette limite et échouer silencieusement.

```
Scénario problématique :
  PDF 100 pages → OCR produit 500 000 caractères (~500KB)
  ExtractionResult avec rawOcrText = 500KB
  + champs extraits = 50KB
  + OutboxEvent = 5KB
  Total transaction = ~555KB → OK

  Mais PDF scanné haute résolution 200 pages
  → rawOcrText = 4.5MB → TRANSACTION ÉCHOUE
  → Document reste en PENDING sans erreur explicite
```

**Décision retenue : Ne pas stocker le texte OCR brut dans MongoDB**

Le texte OCR brut (`rawOcrText`) est volumineux et peu utile après l'extraction LLM. On le stocke dans **Amazon S3** (pas dans MongoDB), et on ne garde dans MongoDB que les champs extraits structurés.

```
Avant (problématique) :
  ExtractionResult MongoDB :
    fields: { montantTTC: ..., siret: ... }  ← utile
    rawOcrText: "500 000 caractères..."       ← problématique

Après (correct) :
  ExtractionResult MongoDB :
    fields: { montantTTC: ..., siret: ... }  ← stocké MongoDB
    rawOcrTextS3Key: "acme/2026/doc-001/ocr.txt" ← référence S3

  Fichier S3 acme/2026/doc-001/ocr.txt :
    "500 000 caractères..."                  ← stocké S3
```

**Règles à ajouter :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-TXN-001 | Le texte OCR brut est stocké dans Amazon S3, pas dans MongoDB | MUST |
| BR-TXN-002 | MongoDB `ExtractionResult` contient uniquement une référence S3 vers le texte OCR | MUST |
| BR-TXN-003 | La taille d'un document MongoDB ne doit pas dépasser 1MB (alerte SonarCloud si dépassé) | MUST |
| BR-TXN-004 | Les transactions MongoDB sont monitorées — alerte si durée > 2 secondes | MUST |
| BR-TXN-005 | Les tests d'intégration incluent un document PDF de 200 pages pour valider la transaction | MUST |

**Impact sur les modules :**
- Module 2.1 (Extraction) → `ExtractionResult` modifié : `rawOcrTextS3Key` à la place de `rawOcrText`
- `AwsS3StorageAdapter` → nouvelle méthode `storeOcrText(tenantId, documentId, text)`
- Collection MongoDB `extraction_results` → supprimer le champ `rawOcrText`, ajouter `rawOcrTextS3Key`
- `commons-outbox` → vérification taille OutboxEvent avant insertion (alerte si > 500KB)

---

### Résumé des 4 ADR

| ADR | Problème | Solution | Modules impactés |
|-----|----------|----------|-----------------|
| ADR-001 | Concurrence quota | Script Lua atomique Valkey | commons-quota, Module 1, Module 6.3 |
| ADR-002 | Ordering Kafka | Clé partition = documentId | commons-kafka, Module 4.1, tous topics |
| ADR-003 | Thundering Herd | TTL avec jitter ±10% | commons-kafka, Module 2.1, Module 2.2 |
| ADR-004 | Transaction 4MB | OCR brut → S3, référence dans MongoDB | Module 2.1, commons-outbox, S3 adapter |

---

### ADR-005 — Rotation des clés de chiffrement PII

**Problème :** Sans rotation des clés KMS, si la clé maître est compromise, toutes les données PII de tous les tenants depuis le début sont exposées.

**Décision retenue : Rotation annuelle automatique via AWS KMS**

AWS KMS gère la rotation automatique. Les données chiffrées avec l'ancienne clé restent lisibles — KMS conserve toutes les versions. Seules les nouvelles écritures utilisent la nouvelle clé.

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-KEY-001 | Les clés de chiffrement PII sont gérées exclusivement par AWS KMS | MUST |
| BR-KEY-002 | La rotation automatique annuelle est activée sur toutes les clés KMS | MUST |
| BR-KEY-003 | Aucun développeur n'a accès direct aux clés — uniquement via IAM roles | MUST |
| BR-KEY-004 | La clé maître KMS est différente entre staging et production | MUST |
| BR-KEY-005 | Un audit CloudTrail de chaque utilisation de clé est activé | MUST |

**Impact :** Module 0.3 RGPD → FLE via AWS KMS. CI/CD → clés injectées via IAM Role, jamais via secrets GitHub.

---

### ADR-006 — Fallback Keycloak indisponible

**Problème :** Si Keycloak tombe, les JWT existants restent valides 15 minutes puis tous les utilisateurs sont bloqués.

**Décision retenue : 3 mesures complémentaires**

**Mesure 1 — Cache local clés publiques Keycloak (JWKS)**

Spring Security met en cache les clés publiques Keycloak (TTL 1h, refresh 30 min). Si Keycloak est down, la vérification des JWT existants continue depuis le cache local.

**Mesure 2 — Keycloak en cluster en production**

2 instances Keycloak minimum derrière un load balancer. Panne d'une instance = service maintenu.

**Mesure 3 — Alerte monitoring immédiate**

Alerte Grafana si Keycloak indisponible > 30 secondes, avant que les JWT expirent.

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-KC-001 | Les clés publiques Keycloak (JWKS) sont mises en cache local (TTL 1h, refresh 30 min) | MUST |
| BR-KC-002 | Keycloak est déployé en 2 instances minimum en production | MUST |
| BR-KC-003 | Alerte Grafana si Keycloak indisponible > 30 secondes | MUST |
| BR-KC-004 | Procédure de redémarrage Keycloak documentée dans le RUNBOOK | MUST |

**Impact :** Module 0 → configuration cache JWKS Spring Security. CI/CD → 2 replicas Keycloak en production.

---

### ADR-007 — Nettoyage des uploads S3 multipart non finalisés

**Problème :** Si une connexion est coupée pendant un upload multipart, les parties S3 restent — invisibles mais facturées indéfiniment.

**Décision retenue : 2 mécanismes combinés**

**Mécanisme 1 — S3 Lifecycle Rule** : suppression automatique des uploads non finalisés après 24h via Terraform.

**Mécanisme 2 — AbortMultipartUpload** : en cas d'exception, l'application annule immédiatement l'upload en cours.

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-S3-001 | Lifecycle Rule S3 supprime les uploads multipart non finalisés après 24h | MUST |
| BR-S3-002 | Cette règle est configurée à la création du bucket via Terraform | MUST |
| BR-S3-003 | En cas d'exception, l'application appelle AbortMultipartUpload immédiatement | MUST |
| BR-S3-004 | Coût S3 monitoré — alerte si hausse > 20% mensuel | SHOULD |

**Impact :** Module 1 Upload → `AwsS3StorageAdapter` appelle AbortMultipartUpload dans le bloc finally. Terraform → Lifecycle Rule sur tous les buckets.

---

### ADR-008 — Mémoire JVM et TestContainers sur GitHub Actions

**Problème :** Runners GitHub Actions = 7GB RAM. Spring Boot + TestContainers (MongoDB + Kafka + Valkey + LocalStack) dépassent cette limite en parallèle → OOM aléatoires.

**Décision retenue : 4 ajustements combinés**

**Ajustement 1 — 3 jobs CI distincts**

| Job CI | Docker | RAM estimée | Durée |
|--------|--------|------------|-------|
| tests-unitaires | Non | ~512MB | 2–4 min |
| tests-integration | Oui | ~3GB | 8–15 min |
| tests-bdd | Oui (tous) | ~4GB | 10–20 min |

**Ajustement 2 — Limite JVM : -Xmx512m** en CI. Sans limite la JVM prend 75% de la RAM.

**Ajustement 3 — TestContainers mode reuse** : 1 seul conteneur partagé par suite de tests. -60% mémoire, -40% durée.

**Ajustement 4 — Runner 16GB** si OOM persiste malgré les 3 ajustements.

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-CI-020 | Tests unitaires, intégration et BDD dans des jobs CI séparés | MUST |
| BR-CI-021 | JVM limitée à 512MB maximum en CI (-Xmx512m) | MUST |
| BR-CI-022 | TestContainers configuré en mode reuse | MUST |
| BR-CI-023 | Si OOM → runner 16GB sans bloquer le pipeline | SHOULD |

**Impact :** CI/CD → 3 jobs séparés. `commons-testing` → `AbstractIntegrationTest` active le reuse.

---

### Résumé complet des 8 ADR

| ADR | Priorité | Problème | Solution | Statut |
|-----|----------|----------|----------|--------|
| ADR-001 | 🔴 Critique | Concurrence quota | Script Lua atomique Valkey | ✅ Résolu |
| ADR-002 | 🔴 Critique | Ordering Kafka | Clé partition = documentId | ✅ Résolu |
| ADR-003 | 🔴 Critique | Thundering Herd cache | TTL avec jitter ±10% | ✅ Résolu |
| ADR-004 | 🔴 Critique | Transaction MongoDB 4MB | OCR brut → S3 | ✅ Résolu |
| ADR-005 | 🟠 Important | Rotation clés PII | AWS KMS rotation annuelle | ✅ Résolu |
| ADR-006 | 🟠 Important | Keycloak down | Cache JWKS + cluster 2 instances | ✅ Résolu |
| ADR-007 | 🟠 Important | S3 multipart non finalisé | Lifecycle Rule + AbortMultipart | ✅ Résolu |
| ADR-008 | 🟠 Important | JVM OOM en CI | Jobs séparés + Xmx512m + reuse | ✅ Résolu |


---

### ADR-009 — Downgrade de plan et données orphelines

**Problème :**
Quand un tenant passe de Pro (5 000 docs/mois) à Starter (500 docs/mois), il a potentiellement des milliers de documents traités pendant sa période Pro. Que devient cet historique ? Si rien n'est défini, l'expérience client est dégradée et le support est surchargé de questions.

```
Scénario sans règle définie :
  Tenant Pro depuis 6 mois → 3 000 documents dans le dashboard
  Downgrade vers Starter
  Question sans réponse : les 3 000 documents sont-ils toujours visibles ?
  → Confusion client, tickets support, risque de churn
```

**Décision retenue : Conservation complète en lecture seule**

Les données existantes ne sont jamais supprimées lors d'un downgrade. L'historique complet reste accessible en lecture seule. Seul le quota mensuel change pour les nouveaux documents.

```
Règle downgrade :
  Documents existants → toujours visibles, toujours téléchargeables
  Nouvelles soumissions → limitées au nouveau quota (500 docs/mois)
  Dashboard → affiche un bandeau informatif sur le changement de plan
  Export → toujours disponible sur tout l'historique
```

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-DWN-001 | Un downgrade de plan ne supprime aucune donnée existante | MUST |
| BR-DWN-002 | L'historique complet reste accessible en lecture seule après downgrade | MUST |
| BR-DWN-003 | Le nouveau quota s'applique uniquement aux nouvelles soumissions | MUST |
| BR-DWN-004 | Un email expliquant les conséquences du downgrade est envoyé au TENANT_ADMIN avant effectivité | MUST |
| BR-DWN-005 | Le dashboard affiche un bandeau informatif les 7 premiers jours après downgrade | SHOULD |
| BR-DWN-006 | Le downgrade est effectif en fin de période mensuelle (pas immédiatement) | MUST |
| BR-DWN-007 | En cas de suspension (PAST_DUE), les données restent accessibles 90 jours | MUST |

**Email envoyé au TENANT_ADMIN avant le downgrade :**

| Contenu | Détail |
|---------|--------|
| Date d'effectivité | Fin de la période mensuelle courante |
| Nouveau quota | 500 documents/mois |
| Historique | Conservé intégralement en lecture seule |
| Conséquence | Nouveaux uploads limités au nouveau quota |
| Action possible | Rester sur Pro, annuler le downgrade |

**Impact sur les modules :**
- Module 0.2 Billing → webhook Stripe `customer.subscription.updated` déclenche l'email informatif
- Module 5.1 Dashboard → bandeau informatif 7 jours après downgrade
- Module 0.3 RGPD → politique de rétention inchangée lors d'un downgrade

---

### ADR-010 — Scalabilité des index MongoDB sur grandes collections

**Problème :**
Les index MongoDB sont définis pour les volumes initiaux. Avec 1 million de documents par tenant et 100 tenants, la collection `documents` peut atteindre 100 millions d'entrées. Les index en RAM deviennent insuffisants, les requêtes ralentissent progressivement et sans monitoring proactif, le problème est détecté trop tard (quand les clients se plaignent).

```
Évolution de la collection documents :
  Mois 1   : 10 000 documents   → index en RAM OK
  Mois 6   : 500 000 documents  → début de ralentissement discret
  Mois 12  : 2 000 000 documents → requêtes dashboard > 1 seconde
  Mois 18  : 10 000 000 documents → timeout sur certaines requêtes
  
  Sans monitoring proactif → problème détecté par les clients, pas par l'équipe
```

**Décision retenue : 3 niveaux de réponse progressifs**

**Niveau 1 — Monitoring proactif dès le début (semaine 1)**

Un job planifié hebdomadaire analyse l'utilisation des index et alerte si un index commence à sortir de la RAM ou si une requête commence à dépasser les seuils.

```
Métriques surveillées :
  → Taille des index vs RAM disponible
  → Nombre de COLLSCAN détectés (requête sans index)
  → Latence P95 des requêtes par collection
  → Alerte si P95 > 200ms sur une requête dashboard
```

**Niveau 2 — Archivage automatique des données anciennes**

Les documents de plus de 2 ans sont archivés dans une collection `documents_archive` avec les mêmes index mais une politique de stockage moins coûteuse. La collection principale reste légère et performante.

```
Collection documents      → données < 2 ans  → accès fréquent → index en RAM
Collection documents_archive → données > 2 ans → accès rare    → index sur disque
```

**Niveau 3 — Sharding MongoDB si nécessaire**

Si le volume dépasse les capacités d'un replica set (> 500GB par collection), MongoDB sharding est activé avec `tenantId` comme shard key. Chaque tenant est naturellement isolé sur son shard.

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-IDX-001 | Un job hebdomadaire analyse l'utilisation des index et alerte si taille index > 80% de la RAM | MUST |
| BR-IDX-002 | Alerte si une requête dashboard dépasse P95 > 200ms (vs cible < 100ms) | MUST |
| BR-IDX-003 | Les documents de plus de 2 ans sont archivés automatiquement dans `documents_archive` | SHOULD |
| BR-IDX-004 | Tout nouvel index est justifié par un EXPLAIN PLAN documenté dans la PR | MUST |
| BR-IDX-005 | Les index partiels (filteredIndex) sont utilisés pour les statuts peu fréquents | SHOULD |
| BR-IDX-006 | Le sharding MongoDB est préparé (shard key = tenantId) mais activé uniquement si nécessaire | COULD |

**Seuils d'alerte et actions :**

| Seuil atteint | Alerte | Action |
|--------------|--------|--------|
| Index > 80% RAM | Slack warning | Analyse et optimisation sous 1 semaine |
| P95 dashboard > 200ms | Slack warning | EXPLAIN PLAN + index review sous 3 jours |
| P95 dashboard > 500ms | PagerDuty | Intervention immédiate |
| Collection > 100GB | Slack warning | Planifier archivage ou sharding |

**Impact sur les modules :**
- Module 5.1 Dashboard → Read Model CQRS soulage la collection principale
- Module 0.3 RGPD → politique de rétention 90 jours naturellement limite la taille
- Annexe C Production Readiness → vérification monitoring index activé

---

### ADR-011 — Cohérence et resynchronisation du Read Model CQRS

**Problème :**
Le Read Model Dashboard est mis à jour par les events Kafka. Si un event est perdu, retardé ou traité en erreur, le dashboard affiche des données obsolètes sans que ni l'utilisateur ni l'équipe ne le sache. Le désynchronisme est silencieux.

```
Scénario problématique :
  Document traité avec succès → status = COMPLETED dans la collection write-side
  Event Kafka "DocumentCompleted" → perdu (rebalance consumer au mauvais moment)
  Read Model → status = EXTRACTED (ancien état, jamais mis à jour)
  
  Utilisateur voit : document en cours de traitement
  Réalité : document traité depuis 2 heures
  Aucune alerte → désynchronisme silencieux indéfini
```

**Décision retenue : 3 mécanismes de détection et correction**

**Mécanisme 1 — Timestamp de dernière mise à jour dans le Read Model**

Chaque entrée du Read Model porte un champ `lastSyncedAt` mis à jour à chaque event Kafka traité. Un job planifié toutes les 5 minutes compare `lastSyncedAt` avec le `updatedAt` de la collection write-side.

```
Job de vérification (toutes les 5 minutes) :
  Pour chaque document dont updatedAt > lastSyncedAt + 30 secondes
    → Détecter la désynchronisation
    → Déclencher une resynchronisation ciblée
    → Incrémenter la métrique docai_read_model_sync_lag
```

**Mécanisme 2 — Resynchronisation ciblée sans interruption de service**

Quand une désynchronisation est détectée sur un document spécifique, le Read Model de ce document est reconstruit depuis la collection write-side sans toucher aux autres documents.

```
Resynchronisation ciblée :
  Document doc-001 désynchronisé détecté
    → Lire l'état actuel depuis write-side (documents + extraction_results + fraud_analyses)
    → Reconstruire document_summary_views[doc-001]
    → Pas d'interruption pour les autres documents
    → AuditEntry créé : "READ_MODEL_RESYNCED", documentId, cause, timestamp
```

**Mécanisme 3 — Reconstruction complète planifiable (sans coupure)**

En cas de désynchronisme massif (migration, incident Kafka), un endpoint admin permet de reconstruire tout le Read Model depuis les events Kafka rejoués depuis le début (event sourcing).

```
POST /v1/admin/read-model/rebuild
  → Reconstruction asynchrone depuis les events Kafka
  → Le Read Model existant reste lisible pendant la reconstruction
  → Les nouvelles entrées écrasent progressivement les anciennes
  → Email envoyé à l'équipe DocAI à la fin de la reconstruction
```

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-SYNC-001 | Chaque entrée du Read Model porte un champ `lastSyncedAt` mis à jour à chaque event | MUST |
| BR-SYNC-002 | Un job planifié toutes les 5 minutes détecte les désynchronisations (lag > 30 secondes) | MUST |
| BR-SYNC-003 | Alerte Grafana si > 10 documents désynchronisés détectés | MUST |
| BR-SYNC-004 | La resynchronisation ciblée s'exécute sans interruption de service | MUST |
| BR-SYNC-005 | La reconstruction complète du Read Model est possible sans coupure | MUST |
| BR-SYNC-006 | Chaque resynchronisation génère un AuditEntry | MUST |
| BR-SYNC-007 | La métrique `docai_read_model_sync_lag_seconds` est exposée dans Prometheus | MUST |

**Métriques Prometheus ajoutées :**

| Métrique | Type | Description |
|----------|------|-------------|
| `docai_read_model_sync_lag_seconds` | Histogram | Délai entre write-side et Read Model |
| `docai_read_model_desync_total` | Counter | Nombre de désynchronisations détectées |
| `docai_read_model_resync_total` | Counter | Nombre de resynchronisations effectuées |

**Impact sur les modules :**
- Module 5.1 Dashboard → ajouter `lastSyncedAt` dans `document_summary_views`
- Module 4.1 Orchestration → `DashboardProjectionConsumer` met à jour `lastSyncedAt`
- Section I.8 Observabilité → ajouter les 3 métriques Read Model

---

### Résumé complet des 11 ADR

| ADR | Priorité | Problème | Solution | Statut |
|-----|----------|----------|----------|--------|
| ADR-001 | 🔴 Critique | Concurrence quota | Script Lua atomique Valkey | ✅ Résolu |
| ADR-002 | 🔴 Critique | Ordering Kafka | Clé partition = documentId | ✅ Résolu |
| ADR-003 | 🔴 Critique | Thundering Herd cache | TTL avec jitter ±10% | ✅ Résolu |
| ADR-004 | 🔴 Critique | Transaction MongoDB 4MB | OCR brut → S3 | ✅ Résolu |
| ADR-005 | 🟠 Important | Rotation clés PII | AWS KMS rotation annuelle | ✅ Résolu |
| ADR-006 | 🟠 Important | Keycloak down | Cache JWKS + cluster 2 instances | ✅ Résolu |
| ADR-007 | 🟠 Important | S3 multipart non finalisé | Lifecycle Rule + AbortMultipart | ✅ Résolu |
| ADR-008 | 🟠 Important | JVM OOM en CI | Jobs séparés + Xmx512m + reuse | ✅ Résolu |
| ADR-009 | 🟡 Confort | Downgrade plan | Conservation données lecture seule | ✅ Résolu |
| ADR-010 | 🟡 Confort | Index scaling | Monitoring + archivage + sharding préparé | ✅ Résolu |
| ADR-011 | 🟡 Confort | Read Model désynchronisé | lastSyncedAt + job détection + resync | ✅ Résolu |





---

## Annexe F — Contacts, Responsabilités & Standards Opérationnels

---

### **F.1 — Stratégie de Sauvegarde et Disaster Recovery**

> **Où :** À configurer avant le premier déploiement production. Vérifier à chaque release.

**Comment configurer les sauvegardes :**

**MongoDB Atlas (production) :**
- Snapshots automatiques quotidiens (rétention 7 jours)
- Snapshots hebdomadaires conservés 4 semaines
- Point-in-time recovery activé (restauration à la minute près)
- Test de restauration obligatoire chaque mois en staging

**Amazon S3 (fichiers documents) :**
- Versioning activé sur le bucket de production
- Réplication cross-region : eu-west-3 vers eu-central-1
- Lifecycle Rule : documents > rétention archivés dans S3 Glacier

**Objectifs RTO / RPO :**

| Scénario | RTO (rétablissement) | RPO (perte max) |
|----------|---------------------|----------------|
| Panne service Spring Boot | < 5 min (auto-restart Kubernetes) | 0 |
| Panne MongoDB primary | < 30 sec (élection replica) | 0 |
| Panne Kafka complète | < 15 min | 0 (Outbox Pattern) |
| Panne région AWS complète | < 4 heures | < 1 heure |

**Procédure de restauration MongoDB (tester chaque mois) :**
1. Identifier le point de restauration dans Atlas
2. Restaurer dans un cluster temporaire Atlas
3. Vérifier l'intégrité des données
4. Faire pointer l'application vers le cluster restauré
5. Documenter le résultat dans le RUNBOOK

---

### **F.2 — Politique de Dépendances et Mises à Jour**

> **Où :** S'applique à tous les modules. À vérifier chaque semaine avec Dependabot.

| Type de mise à jour | Délai max | Qui valide | Test requis |
|---------------------|----------|-----------|------------|
| **Patch sécurité critique** (CVE CRITICAL) | 24h | Tech Lead | CI complet |
| **Patch sécurité** (CVE HIGH) | 72h | Tech Lead | CI complet |
| **Patch version** (x.y.Z) | 1 semaine | Dev | CI complet |
| **Minor version** (x.Y.0) | 2 semaines | Tech Lead | CI + test manuel |
| **Major version** (X.0.0) | Sprint dédié | Équipe | CI + non-régression |

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-DEP-001 | Les CVE CRITICAL bloquent le déploiement production jusqu'à correction | MUST |
| BR-DEP-002 | Dependabot configuré pour les updates Maven hebdomadaires | MUST |
| BR-DEP-003 | Aucune dépendance sans licence compatible (Apache 2.0, MIT, BSD) | MUST |
| BR-DEP-004 | Spring Boot et Java LTS mis à jour dans les 3 mois de release | SHOULD |

---

### **F.3 — Politique de Branches pour les Hotfixes**

> **Où :** Procédure en cas de bug critique en production. À connaître par toute l'équipe.

**Procédure hotfix :**

```
1. Créer branche depuis main :
   git checkout main && git pull
   git checkout -b hotfix/v1.0.1-description-du-bug

2. Corriger le bug + écrire le test qui le reproduit

3. PR vers main (1 reviewer minimum)
   Label : hotfix, priority-critical

4. Merger dans main après validation CI
   Tag : git tag v1.0.1 && git push --tags

5. Surveiller les métriques Grafana 30 min après déploiement

6. Merger le hotfix dans develop :
   git checkout develop && git merge hotfix/v1.0.1-description
```

---

### **F.4 — SLA Documenté et Publié**

> **Où :** À publier sur la status page avant le lancement public.

| Métrique | Engagement | Mesure Prometheus |
|----------|-----------|-----------------|
| **Disponibilité** | 99.9% mensuel | Uptime endpoint principal |
| **Latence upload** | P95 < 2 secondes | docai_document_upload_duration |
| **Latence traitement** | P95 < 30 secondes | docai_document_processing_duration |
| **Latence dashboard** | P95 < 100ms | docai_dashboard_query_duration |

**Ce qui compte comme downtime :**
- POST /v1/documents retourne > 5% d'erreurs 5xx sur 5 minutes consécutives
- GET /v1/documents/{id} inaccessible > 1 minute

**Ce qui ne compte pas comme downtime :**
- Dégradation performances mais service accessible
- Maintenance planifiée annoncée 48h à l'avance (max 4h/mois)
- Pannes causées par des services tiers (AWS, Stripe, LLM)

**Compensation (plans Pro et Enterprise) :**

| Disponibilité mensuelle | Crédit |
|------------------------|--------|
| 99.0% – 99.9% | 10% du mois facturé |
| 95.0% – 99.0% | 25% du mois facturé |
| < 95.0% | 50% du mois facturé |

---

### **F.5 — Glossaire Métier**

> **À lire en priorité par tout nouveau développeur avant de toucher au code.**

| Terme | Définition |
|-------|-----------|
| **Tenant** | Une entreprise cliente de DocAI. Toutes les données sont isolées par tenant. |
| **TENANT_ADMIN** | Premier utilisateur d'un tenant, créé automatiquement à l'inscription. Gère les utilisateurs et l'abonnement. |
| **Document** | Tout fichier (PDF, image) soumis au pipeline DocAI pour traitement. |
| **Pipeline** | La chaîne de traitement : Upload → Classification → Extraction → Fraude → Livraison. |
| **ExtractionResult** | Résultat structuré du parsing d'un document (champs extraits avec scores de confiance). |
| **FraudAnalysis** | Analyse de risque d'un document — **immuable** après création. Score 0-100 + signaux détectés. |
| **FraudScore** | Entier 0-100 représentant le risque de fraude. 0 = aucun risque, 100 = fraude certaine. |
| **RiskLevel** | FAIBLE (0-25), MODÉRÉ (26-50), ÉLEVÉ (51-75), CRITIQUE (76-100). |
| **Signal** | Un indice de fraude détecté (ex: DATA_SIRET_INVALID). Chaque signal a un poids. |
| **Outbox Pattern** | Mécanisme garantissant la publication Kafka même en cas de panne. Event sauvegardé MongoDB PUIS publié Kafka. |
| **Read Model** | Projection dénormalisée pour la lecture (Dashboard). Mise à jour par les events Kafka. |
| **Quota** | Nombre maximum de documents traitables par mois selon le plan. |
| **Overage** | Documents traités au-delà du quota inclus, facturés séparément. |
| **DLQ** | Dead Letter Queue — file d'attente Kafka pour les messages ayant échoué après tous les retries. |
| **Circuit Breaker** | Mécanisme Resilience4j qui coupe les appels vers un service défaillant. |
| **Idempotence** | Propriété d'une opération qui peut être exécutée plusieurs fois avec le même résultat. |
| **BILLING_ENABLED** | Feature flag global. false = tout le monde gratuit (phase de test). true = facturation active. |
| **ADR** | Architecture Decision Record — décision architecturale documentée avec problème, solution et justification. |

---

### **F.6 — Guide d'Onboarding Développeur**

> **Objectif : environnement fonctionnel en moins d'une heure.**

**Étape 1 — Prérequis (15 min)**
1. Installer Java 21 LTS (Eclipse Temurin recommandé)
2. Installer Maven 3.9+
3. Installer Docker Engine 24+ et Docker Compose v2
4. Cloner le dépôt

**Étape 2 — Configuration (10 min)**
1. Copier `.env.example` vers `.env`
2. Remplir les valeurs AWS (bucket dev, credentials IAM dev)
3. Les autres valeurs pointent vers Docker local par défaut

**Étape 3 — Démarrage infrastructure (10 min)**
1. `docker compose up -d`
2. Vérifier : `docker compose ps` → tous les services healthy
3. Keycloak : http://localhost:8180 (admin/admin123)
4. Kafka UI : http://localhost:8090 (8 topics visibles)

**Étape 4 — Build et tests (15 min)**
1. `./mvnw clean install -DskipTests`
2. `./mvnw test -pl docai-domain` (tests domaine < 30 secondes)
3. `./mvnw spring-boot:run -pl docai-bootstrap`
4. Vérifier : http://localhost:8080/actuator/health → UP

**Étape 5 — Lecture obligatoire (10 min)**
1. Sections I.1 à I.10 (architecture et choix techniques)
2. Module 0 (sécurité — fondation de tout)
3. Annexe E (11 ADR — décisions critiques à ne pas violer)
4. Annexe F.5 (Glossaire — termes du domaine)

**Étape 6 — Premier commit**
- Configurer Conventional Commits dans son IDE
- Format obligatoire : `type(scope): description`
- Exemples : `feat(recognition): add PDF classification`, `fix(extraction): handle null LLM response`

---

### **F.7 — Contacts et Responsabilités**

| Rôle | Responsabilité | Contact urgence |
|------|---------------|----------------|
| **Tech Lead** | Décisions architecturales, validation ADR, merge main | PagerDuty 24/7 |
| **Responsable Sécurité** | Revue sécurité, clés KMS, pentest | Slack #security |
| **DPO** | Conformité RGPD, demandes d'effacement, audits CNIL | Email DPO |
| **DevOps** | Infrastructure, CI/CD, Kubernetes, AWS | PagerDuty incidents infra |
| **On-call rotation** | Astreinte production (rotation hebdomadaire) | PagerDuty automatique |

**En cas d'incident production :**
1. Consulter le Runbook dans le wiki
2. Contacter l'on-call via PagerDuty
3. Ouvrir un incident dans #incidents-production Slack
4. Ne jamais agir seul sur la base de données de production


---

## Annexe G — Tests de Performance & Internationalisation (Backlog v2)

---

### G.1 — Stratégie de Tests de Charge

> **Où intégrer dans le dev :** Les tests de charge s'exécutent en staging avant chaque release production. Ils doivent être créés en même temps que chaque module exposant un endpoint public.

**Outil retenu : k6**
k6 est retenu car il s'intègre nativement dans GitHub Actions, génère des rapports Grafana, et utilise JavaScript pour les scripts — accessible à toute l'équipe.

**Quand lancer les tests de charge :**

| Déclencheur | Fréquence | Environnement |
|------------|-----------|--------------|
| Avant chaque release production | Obligatoire | Staging |
| Chaque semaine (job CI planifié) | Hebdomadaire | Staging |
| Après un changement architecture majeur | À la demande | Staging |
| Jamais en production | Interdit | Production |

**3 Scénarios obligatoires :**

**Scénario 1 — Charge nominale (valider le SLA quotidien)**

Simule 50 utilisateurs simultanés pendant 5 minutes.
Représente une journée de travail normale pour un tenant Pro.

Seuils de blocage (si dépassés → pipeline CI échoue) :
- P95 latence upload < 2 secondes
- P95 latence traitement < 30 secondes
- P95 latence dashboard < 100ms
- Taux d'erreur < 1%

**Scénario 2 — Pointe soudaine (valider la résilience)**

Simule une montée de 10 à 500 utilisateurs en 30 secondes puis retour à 10.
Représente un pic inattendu (fin de mois comptable, lancement campagne).

Seuils de blocage :
- Taux d'erreur < 5% pendant la pointe
- Retour à P95 normal dans les 2 minutes après la pointe
- Aucun Circuit Breaker resté OPEN après le pic

**Scénario 3 — Stress (trouver la limite)**

Augmentation progressive jusqu'à 1000 utilisateurs simultanés.
Objectif : identifier le point de rupture AVANT la production.

Seuils informatifs (pas de blocage CI mais alerte Slack) :
- Documenter la charge maximale supportée
- Identifier le premier composant qui sature (CPU, Kafka lag, MongoDB)
- Vérifier que la dégradation est gracieuse (pas de crash brutal)

**Résultats attendus par module :**

| Module | Endpoint | P95 cible | Charge testée |
|--------|----------|-----------|--------------|
| Module 1 Upload | POST /v1/documents | < 2s | 50 uploads simultanés |
| Module 2 Extraction | GET /v1/documents/{id} | < 500ms | 200 req/s |
| Module 5 Dashboard | GET /v1/dashboard/summary | < 100ms | 500 req/s |
| Module 6 API | POST /v1/documents (API Key) | < 2s | 100 req/s |

**Intégration CI/CD :**

Les tests de charge s'exécutent dans un job CI dédié `06-performance.yml` :
- Déclenché manuellement ou avant chaque tag de release
- Exécuté uniquement en staging (jamais en production)
- Résultats publiés dans Grafana (dashboard dédié)
- Si un seuil de blocage est dépassé → pipeline arrêté, release bloquée

**Comment créer un test de charge pour un nouveau module :**
1. Créer le fichier `k6/module-X-load-test.js` dans le dépôt
2. Définir les 3 scénarios (nominal, pointe, stress)
3. Configurer les seuils dans `thresholds`
4. Ajouter le test dans le job CI `06-performance.yml`
5. Exécuter une première fois en staging et documenter les résultats de référence

**Règles :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-PERF-001 | Les tests de charge s'exécutent en staging uniquement | MUST |
| BR-PERF-002 | Chaque nouveau endpoint public a un test k6 associé | MUST |
| BR-PERF-003 | Un dépassement de seuil bloque la release production | MUST |
| BR-PERF-004 | Les résultats sont publiés dans Grafana et conservés 90 jours | MUST |
| BR-PERF-005 | Le test stress est exécuté avant chaque release majeure | MUST |

---

### G.2 — Internationalisation (i18n) — Backlog v2

> **Statut :** Non prioritaire pour le lancement. À planifier pour la v2 lors de l'expansion internationale.

**Ce qui doit être préparé dès maintenant (sans implémenter) :**

**Messages d'erreur RFC 7807 :**
Les messages d'erreur sont actuellement en français. Pour l'internationalisation :
- Le champ `detail` dans ProblemDetail doit être externalisé dans des fichiers de messages
- La langue est déterminée par le header `Accept-Language` de la requête
- Langues cibles v2 : anglais (EN), espagnol (ES), allemand (DE)

**Emails transactionnels :**
- Templates d'email séparés par langue
- Langue du tenant configurée dans `tenant_configs`
- Par défaut : français (FR)

**Validation de documents internationaux :**
- TVA européenne : format différent par pays (FR, DE, IT, ES)
- Numéros d'identification fiscale par pays
- Formats d'adresse différents par pays (pas seulement API BAN France)

**Devise et facturation :**
- EUR par défaut pour l'Europe
- Stripe supporte nativement les devises multiples
- Prix affichés dans la devise du tenant

**Règles pour préparer l'i18n dès maintenant :**

| ID | Règle | Priorité |
|----|-------|---------|
| BR-I18N-001 | Les messages d'erreur ne doivent jamais être des chaînes codées en dur — utiliser des clés de messages | SHOULD |
| BR-I18N-002 | Les dates et montants utilisent des formats standards ISO 8601 et ISO 4217 dans les APIs | MUST |
| BR-I18N-003 | La langue du tenant est un champ configurable dans tenant_configs | SHOULD |


