# Implementation Plan: Stack Technique & Intégrations DocAI (Module B)

**Branch**: `004-stack-technique` | **Date**: 2026-05-25 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/003-stack-technique/spec.md`

---

## Summary

Configurer les 4 blocs transversaux du Module B — aucune logique métier, pure configuration et utilitaires :

1. **BOM parent** — corriger `apicurio.version` (2.4→2.6.5.Final) + configurer `maven-avro-plugin`
2. **Topologie Kafka** — déjà conforme ADR-002 dans docker-compose ; ajouter Consumer Group IDs dans `application.yml`
3. **Cache Valkey** — créer `JitterTtl` utilitaire + documenter les 9 stratégies dans `application.yml`
4. **Resilience4j** — déclarer 8 instances (llm, tika, opencv, insee, ban, rpps, s3, plus kafka N/A) avec seuils exacts Constitution Annex A

Prérequis : Module 1.A (Setup Projet) terminé, Docker Compose opérationnel.

---

## Technical Context

**Language/Version**: Java 21 LTS + Maven 3.9+  
**Primary Dependencies**:

| Dépendance | Version | Rôle |
|-----------|---------|------|
| Spring Boot | 4.0.0 | Framework principal |
| Resilience4j | 2.3.0 | Circuit Breaker, Retry, Bulkhead, TimeLimiter |
| Bucket4j | 8.10.1 | Rate limiting distribué (Valkey) |
| Apache Avro | 1.11.4 | Sérialisation Kafka |
| Apicurio Registry | **2.6.5.Final** | Schema Registry (≠ Confluent) |
| Tess4J | 5.13.0 | OCR texte scanné |
| PDFBox | 3.0.3 | Extraction texte PDF natif |
| Apache Tika | 2.9.2 | Analyse métadonnées fichiers |
| JavaCV | 1.5.11 | Vision documentaire (wraps OpenCV) |
| AWS SDK v2 | 2.25.70 | Stockage S3 |

**Storage**: MongoDB 7.0 (Replica Set) + Valkey 8.x (Redis-compatible)  
**Testing**: JUnit 5 + ArchUnit + Spring Boot Test  
**Target Platform**: JVM 21, Docker Compose (dev local)  
**Project Type**: Configuration/Infrastructure — aucun code domaine (constitution Principe I)  
**Performance Goals**: `./mvnw clean compile` < 3 min | startup sans erreur < 30s | Valkey PING → PONG  
**Constraints**: ADR-002, ADR-003, ADR-006 non-négociables | `JitterTtl` dans `docai-adapter-out-valkey` | groupId Kafka **jamais** dans `@KafkaListener`  
**Scale/Scope**: 11 modules Maven | 8 topics Kafka | 9 stratégies cache | 8 configs Resilience4j

---

## Constitution Check

### Gate 1 — Hexagonal Architecture ✅ PASS

Ce module ne crée aucune entité domaine. `JitterTtl` reste dans `docai-adapter-out-valkey` (pas dans `docai-domain`). La configuration Resilience4j est dans `docai-bootstrap/application.yml`, pas dans les ports. Les adaptateurs sortants utilisent les annotations Resilience4j ; le domaine n'en a pas connaissance.

### Gate 2 — DDD Bounded Contexts ✅ PASS

Module purement transversal. Aucun bounded context créé ou modifié. Aucune entité domaine touchée.

### Gate 3 — Test-First ✅ PASS (avec obligation)

- `JitterTtl` doit avoir des tests unitaires **avant** la configuration cache.
- Test de démarrage Spring Boot (`@SpringBootTest`) vérifiant le chargement Resilience4j.
- Test ArchUnit vérifiant l'absence de `groupId` littéral dans `@KafkaListener`.

### Gate 4 — SOLID ✅ PASS

- `JitterTtl` : classe finale utilitaire, responsabilité unique
- `KafkaTopicConfig` (si @Bean Spring) : une responsabilité par bean
- `ValkeyCacheConfig` : une responsabilité par stratégie de cache

### Gate 5 — ADR Compliance

| ADR | Status | Action requise |
|-----|--------|---------------|
| ADR-001 | ⚠️ À faire | Lua script atomique pour quota — implémenter lors Module Upload |
| ADR-002 | ✅ OK | docker-compose kafka-init conforme ; Consumer Group IDs dans application.yml |
| ADR-003 | ⚠️ À créer | `JitterTtl.withJitter()` — créer classe + tests |
| ADR-004 | N/A | Module OCR/Extraction |
| ADR-005 | N/A | Module RGPD |
| ADR-006 | ⚠️ À configurer | `jwks-cache-ttl: 1h` absent de application.yml actuel |
| ADR-007 | N/A | Module Upload S3 |
| ADR-008 | ✅ OK | `-Xmx512m` déjà dans profiles Maven |
| ADR-009 | N/A | Module Billing |
| ADR-010 | N/A | Module MongoDB |
| ADR-011 | N/A | Module Dashboard |

### Gate 6 — Observability ✅ PASS

Les instances Resilience4j sont exposées via `/actuator/health` (déjà configuré dans application.yml). La connexion Valkey est exposée via `components.redis`.

---

## Project Structure

### Documentation (cette feature)

```text
specs/003-stack-technique/
├── spec.md              ← Spécification complète (27 FR)
├── plan.md              ← Ce fichier
├── research.md          ← 8 décisions documentées (D1-D8)
├── data-model.md        ← 4 entités configuration (Topic, CacheStrategy, ResilienceConfig, JitterTtl)
├── quickstart.md        ← Guide 4 étapes avec commandes de vérification
├── contracts/
│   └── application-yml.md ← Blocs YAML requis (5 blocs)
└── tasks.md             ← Généré par /speckit-tasks (à faire)
```

### Source Code — fichiers à créer ou modifier

```text
backend/pom.xml
  └── [MODIFIER] apicurio.version: 2.4.15.Final → 2.6.5.Final

backend/docai-adapter-out-kafka/
├── pom.xml
│   └── [MODIFIER] Ajouter maven-avro-plugin:1.11.4
└── src/main/avro/
    ├── [CRÉER] DocumentUploadedEvent.avsc
    ├── [CRÉER] DocumentClassifiedEvent.avsc
    ├── [CRÉER] DocumentExtractedEvent.avsc
    ├── [CRÉER] DocumentValidatedEvent.avsc
    ├── [CRÉER] DocumentFraudDetectedEvent.avsc
    ├── [CRÉER] DocumentCompletedEvent.avsc
    ├── [CRÉER] DocumentFailedEvent.avsc
    └── [CRÉER] OutboxRelayEvent.avsc

backend/docai-adapter-out-valkey/
└── src/main/java/fr/docai/adapter/out/valkey/util/
    ├── [CRÉER] JitterTtl.java
    └── src/test/java/.../
        └── [CRÉER] JitterTtlTest.java

backend/docai-bootstrap/src/main/resources/
└── application.yml
    ├── [MODIFIER] Supprimer spring.kafka.consumer.group-id: docai-group
    ├── [AJOUTER] kafka.groups.* (Consumer Group IDs par module)
    ├── [AJOUTER] spring.kafka.producer/consumer SerDe Apicurio
    ├── [AJOUTER] resilience4j.* (8 instances CB + Retry + Bulkhead + TimeLimiter)
    ├── [AJOUTER] docai.cache.* (TTL references documentaires)
    └── [AJOUTER] spring.security.oauth2.resourceserver.jwt.jwks-cache-ttl: 1h

backend/docai-bootstrap/src/test/java/fr/docai/bootstrap/arch/
└── [CRÉER ou MODIFIER] HexagonalArchitectureTest.java
    └── Ajouter règle: @KafkaListener sans groupId littéral
```

---

## Complexity Tracking

Aucune violation de Constitution identifiée pour ce module. Tableau vide.

---

## Phases

### Phase 0 — Research ✅ Complète

Voir [research.md](research.md) — 8 décisions documentées :

| ID | Décision | Statut |
|----|----------|--------|
| D1 | Apicurio 2.4 → 2.6.5.Final | Résolu |
| D2 | maven-avro-plugin (pas manuel) | Résolu |
| D3 | Consumer Group IDs dans application.yml | Résolu |
| D4 | JitterTtl dans docai-adapter-out-valkey | Résolu |
| D5 | Resilience4j YAML — 8 services, seuils Constitution Annex A | Résolu |
| D6 | Valkey : spring.data.redis (compatible) + commentaire | Résolu |
| D7 | Topologie Kafka existante conforme ADR-002 | Résolu |
| D8 | ADR-006 : jwks-cache-ttl: 1h à ajouter | Résolu |

### Phase 1 — Design & Contracts ✅ Complète

Artefacts générés :

| Artefact | Chemin | Contenu |
|---------|--------|---------|
| Data Model | [data-model.md](data-model.md) | 4 entités configuration + tableaux de référence |
| Contract YAML | [contracts/application-yml.md](contracts/application-yml.md) | 5 blocs YAML complets (copy-paste ready) |
| Quickstart | [quickstart.md](quickstart.md) | 4 étapes avec commandes de vérification |

---

## Implémentation — Séquence recommandée

```
Étape 1 (30 min) : POM parent + maven-avro-plugin
  → Vérifier : ./mvnw clean compile → BUILD SUCCESS

Étape 2 (20 min) : Vérification topologie Kafka + Consumer Group IDs dans application.yml
  → Vérifier : 8 topics dans Kafka UI (http://localhost:8090)

Étape 3 (60 min) : JitterTtl (code + test) + application.yml cache config
  → Vérifier : tests JitterTtl passent + Valkey PING → PONG

Étape 4 (60 min) : Resilience4j dans application.yml + ADR-006 JWKS TTL
  → Vérifier : /actuator/health → toutes instances Resilience4j CLOSED
```

**Total estimé**: 3h à 4h (1/2 journée).

---

## Prochaine commande

```
/speckit-tasks
```

Génèrera `tasks.md` avec les micro-tâches ≤ 1 jour chacune, alignées sur les 4 étapes ci-dessus.
