# DocAI — SpecKit Partie 1 : Architecture + Setup & CI/CD
## MASTER Partie 1 + Partie 2 · Semaine 1 · 6 Modules · 9 Skills

> **Objectif :** Référence transversale complète + projet fonctionnel + CI/CD vert.
> **Règle absolue :** L'ordre d'implémentation ci-dessous est NON-NÉGOCIABLE — chaque étape bloque la suivante.
> **Prérequis :** Java 21 LTS, Maven 3.9+, Docker Engine 24+, Docker Compose v2+, RAM 8GB min.

---

## ⚠️ Ordre d'implémentation obligatoire

> **Section 1 (Modules A, B, C) = RÉFÉRENCE À LIRE** — pas à implémenter en premier.
> **Section 2 (Modules 1.A, 1.B, 1.C) = IMPLÉMENTER EN PREMIER** — le projet doit exister avant tout.
> Tous les composants de Section 1 dépendent des modules Maven créés en Section 2 Module 1.A.

```
┌─────────────────────────────────────────────────────────────┐
│  ORDRE D'IMPLÉMENTATION SEMAINE 1                           │
├─────┬────────────────────────────┬────────────────────────┤
│  1  │ Section 2 — Module 1.A    │ POM + 11 modules Maven  │
│     │                            │ Docker Compose          │
│     │                            │ Keycloak + Seeding      │
├─────┼────────────────────────────┼────────────────────────┤
│  2  │ Section 1 — Module A      │ HexagonalArchitectureTest│
│     │                            │ Ports IN/OUT du domaine │
├─────┼────────────────────────────┼────────────────────────┤
│  3  │ Section 1 — Module B      │ Avro schemas            │
│     │                            │ Consumer Group IDs      │
│     │                            │ Resilience4j config     │
│     │                            │ JitterTtl Valkey        │
├─────┼────────────────────────────┼────────────────────────┤
│  4  │ Section 1 — Module C      │ Mongock V001            │
│     │                            │ Pagination BR-PAG       │
│     │                            │ Logs JSON structurés    │
│     │                            │ Versioning /v1/         │
├─────┼────────────────────────────┼────────────────────────┤
│  5  │ Section 2 — Module 1.B    │ GitHub Actions CI/CD    │
│     │                            │ Dockerfile              │
│     │                            │ Kubernetes manifestes   │
├─────┼────────────────────────────┼────────────────────────┤
│  6  │ Section 2 — Module 1.C    │ Feature Flags Unleash   │
│     │                            │ Templates Emails SES    │
│     │                            │ DoR + PR Template + DoD │
└─────┴────────────────────────────┴────────────────────────┘
```

> **Pourquoi cet ordre ?**
> - HexagonalArchitectureTest (Module A) analyse des classes compilées → Maven doit exister (Module 1.A)
> - Schémas Avro (Module B) vivent dans docai-adapter-out-kafka → module doit exister (Module 1.A)
> - Mongock V001 (Module C) cible docai-adapter-out-mongodb → module doit exister (Module 1.A)
> - GitHub Actions (Module 1.B) valide un build Maven → projet doit compiler (Module 1.A)
> - FeatureFlagPort (Module 1.C) vit dans docai-domain → module doit exister (Module 1.A)

---

## Skills associés — Partie 1 complète

| Skill | Rôle |
|-------|------|
| `docai-architecture-adr` | 12 règles ArchUnit, 11 ADR, SOLID, Design Patterns — **LIRE EN PREMIER** |
| `docai-stack-technique` | Stack V15, Kafka 8 topics + Consumer Groups, Valkey TTL, Resilience4j seuils |
| `docai-persistance-standards` | MongoDB collections, Mongock V001→, BR-PAG-001→008, versioning /v1/ |
| `docai-setup-projet` | POM parent, Docker Compose 11 services, Keycloak realm, .env.example |
| `docai-cicd-pipeline` | GitHub Actions 01/02/03, Dockerfile, K8s, Quality Gates, DoD CI/CD |
| `docai-seeding` | SeedingService DEV, 3 tenants, 10 utilisateurs, idempotence |
| `docai-feature-flag` | Unleash, 6 flags, FeatureFlagPort, déploiement progressif |
| `docai-email-ses` | 19 templates Thymeleaf SES, BR-EMAIL-001 à 004, sandbox DEV |
| `docai-observability` | Micrometer, logs JSON structurés, OpenTelemetry |
| `docai-annexes-standards` | Annex B MongoDB · Annex C Secrets · GitFlow + Dependabot |

---

## Référence ADR — Les 11 décisions architecturales (NON-NÉGOCIABLES)

> ⚠️ Ces 11 ADR s'appliquent à TOUT le projet dès la Partie 1.
> Une violation = bug critique à corriger en < 24h.

| ADR | Priorité | Règle obligatoire | Applicable Partie 1 |
|-----|----------|-------------------|---------------------|
| **ADR-001** | 🔴 Critique | Quota : script Lua **ATOMIQUE** Valkey — jamais `GET` puis `INCR` séparés | ⚠️ Préparer commons-quota |
| **ADR-002** | 🔴 Critique | Kafka : clé partition = `documentId` — jamais `tenantId` pour le pipeline | ✅ Config kafka-init |
| **ADR-003** | 🔴 Critique | Cache : TTL avec jitter `±10%` sur tout TTL > 1h — jamais TTL fixe | ⚠️ Préparer commons-kafka |
| **ADR-004** | 🔴 Critique | OCR brut → S3 uniquement — jamais dans MongoDB (limite 4MB) | ⚠️ Préparer S3 adapter |
| **ADR-005** | 🟠 Important | PII chiffrés via AWS KMS — rotation automatique annuelle | ⚠️ Module 0.5 |
| **ADR-006** | 🟠 Important | JWKS Keycloak en cache local TTL 1h — sinon Keycloak down = tous bloqués | ✅ Configurer maintenant |
| **ADR-007** | 🟠 Important | Upload S3 : `AbortMultipartUpload` + Lifecycle Rule 24h Terraform | ⚠️ S3 adapter |
| **ADR-008** | 🟠 Important | CI : 3 jobs séparés + JVM `-Xmx512m` + TestContainers `reuse=true` | ✅ Appliquer maintenant |
| **ADR-009** | 🟡 Confort | Downgrade plan : données conservées lecture seule, quota reset mois suivant | ➡️ Module 7 |
| **ADR-010** | 🟡 Confort | MongoDB : `EXPLAIN PLAN` avant chaque merge + partial index si actif < 20% | ✅ Règle dès maintenant |
| **ADR-011** | 🟡 Confort | Read Model : `lastSyncedAt` + réconciliation toutes les 5 min | ➡️ Module 5 |

---

## Références Annexes — Applicables dès Partie 1

### Annex B — Standards MongoDB (ADR-010)
| Règle | Détail |
|-------|--------|
| Nommage collections | `snake_case` pluriel : `documents`, `extraction_results`, `fraud_analyses` |
| Nommage champs | `camelCase`, dates suffixées `At` : `createdAt`, `updatedAt` |
| JAMAIS `@Indexed` | Tous les index via **Mongock migrations uniquement** |
| `auto-index-creation: false` | Obligatoire en production |
| `tenantId` EN PREMIER | Dans tous les index composés |
| EXPLAIN PLAN obligatoire | `winningPlan.stage` = `IXSCAN`, jamais `COLLSCAN` — avant chaque merge |
| Nommage migrations | `V{NNN}_{module}_{description}` ex: `V001_setup_documents_collection` |
| `@RollbackExecution` | Chaque migration Mongock a sa méthode de rollback |

### Annex C — Rotation des Secrets
| Secret | Stockage | Fréquence |
|--------|----------|-----------|
| OpenAI / Mistral API Key | AWS Secrets Manager | **90 jours** |
| Keycloak Client Secret | AWS Secrets Manager | **90 jours** |
| Stripe Webhook Secret | AWS Secrets Manager | **90 jours** |
| INSEE OAuth2 | AWS Secrets Manager | 6 mois |
| MongoDB Credentials | AWS Secrets Manager | 180 jours |
| Clé KMS PII | AWS KMS | **Annuelle auto** — ADR-005 |

### GitFlow + Dependabot
| Branche | Protection | Déploiement |
|---------|-----------|-------------|
| `main` | ✅ PR + 1 reviewer + Quality Gate | Production (approbation) |
| `develop` | ✅ PR + Quality Gate | Staging (automatique) |
| `feature/UC-MOD-XXX-description` | ❌ Libre | Aucun |
| `hotfix/v1.0.1-description` | ❌ Libre | PR vers main + develop |

---

## ✅ Critère de passage → Partie 2

- [ ] `./mvnw clean compile` → BUILD SUCCESS (11 modules)
- [ ] `docker compose ps` → tous les services `healthy`
- [ ] MongoDB Replica Set initialisé (`rs.status().ok === 1`)
- [ ] 8 topics Kafka visibles avec clé partition `documentId` (ADR-002)
- [ ] Login `admin@acme-corp.test` → JWT valide avec claim `tenant_id: acme-corp`
- [ ] Cache JWKS Keycloak configuré TTL 1h dans application.yml (ADR-006)
- [ ] `GET /actuator/health` → `{"status":"UP"}`
- [ ] `GET /v1/documents` sans JWT → HTTP 401
- [ ] `./mvnw test -Dtest=HexagonalArchitectureTest` → 12 règles passent
- [ ] `./mvnw checkstyle:check` → 0 violation
- [ ] `.env` présent dans `.gitignore` — secrets non commitués
- [ ] Premier build GitHub Actions `01-ci.yml` vert sur `develop` (ADR-008)
- [ ] Trivy scan : 0 vulnérabilité CRITICAL dans l'image Docker
- [ ] `billing.enabled = false` confirmé en DEV (Unleash)
- [ ] `dependabot.yml` configuré pour updates Maven hebdomadaires (BR-DEP-002)
- [ ] Tous les secrets dans AWS Secrets Manager avec date d'expiration (BR-ROT-001/002)
- [ ] Stratégie GitFlow configurée (main + develop protégées)

---
---


---
---

# ═══════════════════════════════════════════════
# ÉTAPE 1 — SETUP PROJET (MASTER Partie 2 Module 2.A)
# ✅ IMPLÉMENTER EN PREMIER — le projet Maven doit exister avant tout
# ═══════════════════════════════════════════════

---

# MODULE 1.A — Structure Maven + Docker Compose

> **Contenu :** POM parent 11 modules, Docker Compose 11 services, Keycloak Realm, Seeding DEV
> **Durée estimée :** 3-4 jours
> **Skills :** `docai-setup-projet` · `docai-architecture-adr` · `docai-seeding`
> **ADR applicables :** ADR-002 (topics Kafka) · ADR-006 (cache JWKS) · ADR-008 (structure CI) · ADR-010 (EXPLAIN PLAN dès le 1er index)

---

## 🔵 speckit-specify — Module 1.A

```
speckit-specify

Module  : Module 1.A — Structure Maven + Docker Compose
Partie  : Partie 1 — Setup & CI/CD (Semaine 1)
Skills  : docai-setup-projet, docai-architecture-adr, docai-seeding

Objectif :
  - POM parent Maven multi-modules (11 modules)
  - Docker Compose avec 11 services locaux tous healthy
  - Configuration Keycloak realm-docai.json (5 rôles, 5 utilisateurs de test)
  - Protocol Mapper tenant_id → claim JWT tenant_id
  - Fichier .env.example documenté et versionné
  - SeedingService DEV (3 tenants, 10 utilisateurs, idempotent)

Stack :
  Java 21 LTS · Spring Boot 4.0.x · MongoDB 7 (Replica Set rs0)
  Kafka 3.7 KRaft · Valkey 8 · Keycloak 26 · Amazon S3 (direct, pas de MinIO)
  Prometheus · Grafana · Grafana Tempo (ports 3200 + 4317 OTLP)

11 modules Maven (package racine fr.docai) :
  docai-domain           ← Java pur — ZERO Spring/MongoDB/Kafka/AWS
  docai-application      ← Use cases (dépend uniquement de domain)
  docai-adapter-in-rest
  docai-adapter-in-kafka
  docai-adapter-out-mongodb
  docai-adapter-out-kafka
  docai-adapter-out-valkey
  docai-adapter-out-ai
  docai-adapter-out-storage
  docai-adapter-out-external
  docai-bootstrap        ← Assemblage Spring Boot final

ADR applicables à ce module :
  ADR-002 : 8 topics Kafka avec clé partition documentId
  ADR-006 : Cache JWKS Keycloak TTL 1h dans application.yml
  ADR-008 : Préparer structure pour 3 jobs CI séparés avec Xmx512m
  ADR-010 : EXPLAIN PLAN obligatoire avant tout index MongoDB
```

---

## 🟢 speckit-clarify — Module 1.A

```
speckit-clarify

Clarifie les points suivants du Module 1.A — skill docai-setup-projet :

1. Le package racine Java est-il fr.docai pour tous les modules ?
2. MinIO en local DEV ou vrai bucket AWS S3 dès le départ ?
3. Le SeedingService s'exécute-t-il uniquement avec le profil Spring "seed"
   ou aussi au démarrage en mode DEV ?
4. Le realm-docai.json Keycloak est-il versionné dans docker/keycloak/
   et importé automatiquement au démarrage du conteneur ?
5. Les 8 topics Kafka sont-ils créés via kafka-init dans docker-compose
   ou via application.yml (auto-create) ?
   → ADR-002 : clé partition = documentId sur tous les topics pipeline
6. L'attribut Keycloak tenant_id est-il un "User Attribute" mappé
   vers le claim JWT tenant_id via Protocol Mapper ?
   → ADR-006 : cache JWKS TTL 1h à configurer dans application.yml
7. EXPLAIN PLAN ADR-010 : quel outil utiliser en DEV ?
   (mongosh explain / MongoDB Compass / log slow queries ?)
```

---

## 🟡 speckit-plan — Module 1.A

```
speckit-plan

Génère le plan d'implémentation du Module 1.A — skill docai-setup-projet.
Respecte les ADR-002, ADR-006, ADR-008, ADR-010.

Ordre obligatoire :

ÉTAPE 1 — Structure Maven (0.5j)
  Créer POM parent + 11 modules vides avec packages fr.docai
  Règle : docai-domain = ZERO import Spring/MongoDB/Kafka/AWS
  Vérifier : ./mvnw clean compile → BUILD SUCCESS

ÉTAPE 2 — ArchUnit 12 règles (0.5j)
  Créer HexagonalArchitectureTest dans docai-domain
  Vérifier : ./mvnw test -Dtest=HexagonalArchitectureTest → toutes passent

ÉTAPE 3 — Docker Compose 11 services (1j)
  MongoDB 7 Replica Set + mongodb-init (rs.initiate)
  Kafka 3.7 KRaft + kafka-init (8 topics — ADR-002 : documentId partition key)
  Kafka UI, Apicurio 2.6, Valkey 8, Keycloak 26
  Prometheus, Grafana, Grafana Tempo (ports 3200 + 4317 OTLP)
  Amazon S3 directement (pas de conteneur MinIO — MASTER SpecKit)
  Vérifier : docker compose ps → tous healthy

ÉTAPE 4 — Keycloak Realm (0.5j)
  realm-docai.json : 5 rôles, 5 utilisateurs de test
  Protocol Mapper : tenant_id User Attribute → claim JWT tenant_id
  application.yml : cache JWKS TTL 1h (ADR-006)
  Vérifier : login admin@acme-corp.test → JWT contient tenant_id: acme-corp

ÉTAPE 5 — .env.example + application.yml (0.5j)
  Toutes les variables documentées avec valeurs CHANGE_ME
  Profils DEV / PROD séparés
  ADR-010 : activer slow query log MongoDB dès le DEV
  Vérifier : .env dans .gitignore, variables complètes

ÉTAPE 6 — SeedingService DEV (0.5j)
  @Profile("seed") uniquement — jamais en prod
  3 tenants : acme-corp PRO, beta-assur STARTER, gamma-rh STARTER
  10 utilisateurs avec rôles corrects
  Idempotent : 2 exécutions = même résultat, zéro doublon
  Vérifier : GET /actuator/health → UP, GET /v1/documents sans JWT → 401
```

---

## 🟠 speckit-checklist — Module 1.A

```
speckit-checklist

Génère la checklist complète du Module 1.A — skill docai-setup-projet.
ADR vérifiés : ADR-002, ADR-006, ADR-008, ADR-010.

STRUCTURE MAVEN :
  - [ ] POM parent avec 11 modules déclarés
  - [ ] Package racine fr.docai respecté dans tous les modules
  - [ ] docai-domain : ZERO dépendance Spring/MongoDB/Kafka/AWS/Stripe
  - [ ] ./mvnw clean compile → BUILD SUCCESS en < 2 min
  - [ ] ./mvnw test -Dtest=HexagonalArchitectureTest → 12 règles passent

DOCKER COMPOSE :
  - [ ] MongoDB 7.0 en Replica Set (rs0) — requis pour transactions atomiques
  - [ ] mongodb-init : rs.initiate() exécuté automatiquement au démarrage
  - [ ] Kafka 3.7 KRaft (sans Zookeeper)
  - [ ] kafka-init : 8 topics créés automatiquement
  - [ ] ADR-002 ✅ : topics pipeline configurés pour clé partition = documentId
  - [ ] 8 topics exacts :
        docai.doc.uploaded (6 partitions, rétention 7j)
        docai.doc.classified (6 partitions, rétention 7j)
        docai.doc.extracted (6 partitions, rétention 7j)
        docai.doc.fraud.analyzed (6 partitions, rétention 7j)
        docai.doc.completed (3 partitions, rétention 30j)
        docai.doc.failed (3 partitions, rétention 30j)
        docai.doc.dlq (3 partitions, rétention 90j)
        docai.outbox.relay (3 partitions, rétention 1j)
  - [ ] Apicurio Registry 2.6 accessible (http://localhost:8081)
  - [ ] Valkey 8 avec healthcheck valkey-cli ping → PONG
  - [ ] Keycloak 26 avec import realm automatique au démarrage
  - [ ] Amazon S3 utilisé directement (pas de conteneur MinIO local — credentials dans .env)
  - [ ] Prometheus (9090), Grafana (3000), Grafana Tempo (3200 + 4317 OTLP intégré)
  - [ ] docker compose ps → TOUS les services healthy

KEYCLOAK — ADR-006 :
  - [ ] Realm "docai" importé depuis docker/keycloak/realm-docai.json
  - [ ] 5 rôles : TENANT_ADMIN, ANALYST, VIEWER, FRAUD_REVIEWER, SYSTEM
  - [ ] 5 utilisateurs de test avec MDP Test1234!
  - [ ] Protocol Mapper : User Attribute tenant_id → claim JWT tenant_id
  - [ ] Access Token TTL : 15 minutes
  - [ ] Refresh Token TTL : 8 heures
  - [ ] realm-docai.json versionné dans Git (pas un secret — c'est une config)
  - [ ] ADR-006 ✅ : application.yml configure cache JWKS TTL 1h
        spring.security.oauth2.resourceserver.jwt.jwk-set-uri + cache PT1H

VARIABLES D'ENVIRONNEMENT :
  - [ ] .env.example versionné avec toutes les variables documentées
  - [ ] .env dans .gitignore — JAMAIS commité dans Git
  - [ ] Toutes les valeurs sensibles marquées CHANGE_ME
  - [ ] Variables présentes : MongoDB, Valkey, Kafka, Keycloak, S3/MinIO,
        LLM (OpenAI), APIs externes (INSEE), Application

MONGODB — ADR-010 + ANNEX B :
  - [ ] Slow query log activé en DEV (operations > 100ms loggées)
  - [ ] ADR-010 ✅ : EXPLAIN PLAN obligatoire avant tout index ajouté
  - [ ] Pas d'index @Indexed dans le code Java — uniquement via Mongock (Annex B)
  - [ ] Collections nommées en snake_case pluriel : documents, tenants (Annex B)
  - [ ] auto-index-creation: false dans application-prod.yml (Annex B)
  - [ ] tenantId EN PREMIER dans tous les index composés (Annex B + ADR-010)

SEEDING DEV :
  - [ ] @Profile("seed") uniquement — jamais en prod ni en staging
  - [ ] 3 tenants : acme-corp (PRO), beta-assur (STARTER), gamma-rh (STARTER)
  - [ ] 10 utilisateurs avec rôles corrects par tenant
  - [ ] Idempotent : 2 exécutions → 0 erreur, 0 doublon

DEFINITION OF DONE :
  - [ ] GET /actuator/health → {"status":"UP"}
  - [ ] GET /v1/documents sans JWT → HTTP 401
  - [ ] Swagger : http://localhost:8080/swagger-ui.html accessible
  - [ ] 8 topics Kafka visibles dans Kafka UI (http://localhost:8090)
  - [ ] Login admin@acme-corp.test → JWT avec tenant_id: acme-corp
  - [ ] Login admin@beta-assur.test → JWT avec tenant_id: beta-assur
    (test isolation multi-tenant)
```

---

## 🔴 speckit-tasks — Module 1.A

```
speckit-tasks

Découpe le Module 1.A en micro-tâches de 1 jour max — skill docai-setup-projet.
Chaque tâche = 1 PR + 1 critère de done mesurable.
Convention commit : feat/fix/test/refactor/ci/perf(scope): message

TÂCHE 1.A-01 — Structure Maven + ArchUnit (1j)
  Action  : POM parent + 11 modules vides (fr.docai)
            + HexagonalArchitectureTest 12 règles dans docai-domain
  PR      : feat(setup): init Maven multi-modules 11 modules + ArchUnit 12 rules
  Critère : ./mvnw clean compile → SUCCESS
            ./mvnw test -Dtest=HexagonalArchitectureTest → 12 règles vertes

TÂCHE 1.A-02 — Docker Compose + Kafka topics (1j)
  Action  : docker-compose.yml 11 services avec healthchecks
            mongodb-init (Replica Set rs0)
            kafka-init (8 topics — ADR-002 documentId partition)
  PR      : feat(infra): add docker-compose 11 services kafka 8 topics ADR-002
  Critère : docker compose ps → tous healthy
            8 topics visibles Kafka UI, rs.status().ok === 1

TÂCHE 1.A-03 — Keycloak Realm + ADR-006 (0.5j)
  Action  : realm-docai.json (5 rôles, 5 users, Protocol Mapper tenant_id)
            application.yml : cache JWKS TTL 1h (ADR-006)
  PR      : feat(security): add Keycloak realm ADR-006 JWKS cache 1h
  Critère : login admin@acme-corp.test → JWT avec tenant_id: acme-corp
            cache JWKS configuré PT1H dans application.yml

TÂCHE 1.A-04 — .env.example + application profiles (0.5j)
  Action  : .env.example documenté (MongoDB, Valkey, Kafka, Keycloak, S3, LLM, INSEE)
            application.yml + application-dev.yml + application-prod.yml
            Slow query MongoDB activé DEV (ADR-010)
  PR      : feat(config): add env example application profiles ADR-010 slow query
  Critère : .env dans .gitignore confirmé
            Slow query log MongoDB actif en DEV (operations > 100ms)

TÂCHE 1.A-05 — SeedingService DEV (0.5j)
  Action  : SeedingService @Profile("seed"), 3 tenants, 10 users, idempotent
  PR      : feat(seeding): add DEV SeedingService idempotent 3 tenants 10 users
  Critère : run 2x → 0 erreur, 0 doublon
            GET /actuator/health → UP
            GET /v1/documents sans JWT → HTTP 401
```

---

## ⚫ speckit-analyse — Module 1.A

```
speckit-analyse

Analyse ce code par rapport aux règles des skills docai-setup-projet
et docai-architecture-adr (ADR-002, ADR-006, ADR-008, ADR-010) :

Points à vérifier obligatoirement :

ARCHITECTURE :
  1. docai-domain contient-il un import org.springframework.* ?
     → Violation ArchUnit Règle 1 — bloquant CI
  2. docai-domain contient-il un import com.mongodb.* ?
     → Violation ArchUnit Règle 7 — bloquant CI
  3. Un adapter appelle-t-il directement un autre adapter ?
     → Violation ArchUnit Règle 2 — bloquant CI

ADR :
  4. ADR-002 : les topics Kafka ont-ils documentId comme clé de partition ?
     → Jamais tenantId comme clé de partition sur les topics pipeline
  5. ADR-006 : le cache JWKS Keycloak est-il configuré TTL 1h ?
     → spring.security.oauth2.resourceserver.jwt → cache PT1H
  6. ADR-008 : les jobs CI ont-ils MAVEN_OPTS=-Xmx512m ?
     → Obligation sur unit-tests, integration-tests, bdd-tests
  7. ADR-010 : un index MongoDB a-t-il été ajouté sans EXPLAIN PLAN ?
     → Vérifier que le EXPLAIN PLAN est documenté dans la PR

SETUP :
  8. Le SeedingService a-t-il @Profile("seed") ?
     → Ne doit JAMAIS s'exécuter en prod ou staging
  9. Les 8 topics Kafka ont-ils les bons noms exacts du SpecKit ?
     → docai.doc.uploaded, docai.doc.classified, docai.doc.extracted,
        docai.doc.fraud.analyzed, docai.doc.completed, docai.doc.failed,
        docai.doc.dlq, docai.outbox.relay
 10. Le .env est-il dans .gitignore ?
     → Secrets jamais commitués

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 1.A

### Composant 1 — POM Parent Maven
```
speckit-implement

Implémente le POM parent Maven multi-modules DocAI — skill docai-setup-projet.
ADR-008 : structure prête pour 3 jobs CI séparés avec MAVEN_OPTS=-Xmx512m.

11 modules :
  docai-domain, docai-application,
  docai-adapter-in-rest, docai-adapter-in-kafka,
  docai-adapter-out-mongodb, docai-adapter-out-kafka,
  docai-adapter-out-valkey, docai-adapter-out-ai,
  docai-adapter-out-storage, docai-adapter-out-external,
  docai-bootstrap

Stack exacte dans BOM :
  Java 21 · Spring Boot 4.0.x
  resilience4j 2.3.0 · bucket4j 8.10.1 · mapstruct 1.6.3
  lombok 1.18.36 · avro 1.11.4 · apicurio 2.6.5.Final
  mongock 5.4.4 · tess4j 5.13.0 · pdfbox 3.0.3 · tika 2.9.2
  javacv 1.5.11 · aws-sdk 2.25.70 · archunit 1.3.0
  wiremock 3.9.1 · cucumber 7.20.1 · testcontainers 1.20.4
  springdoc 2.8.6

Règle absolue :
  docai-domain → ZERO dépendance Spring/MongoDB/Kafka/AWS/Stripe
  Vérifié par ArchUnit 12 règles à chaque commit CI (ADR-008)
```

### Composant 2 — Docker Compose + Kafka Topics (ADR-002)
```
speckit-implement

Crée le docker-compose.yml DocAI — skill docai-setup-projet.
ADR-002 : 8 topics Kafka avec documentId comme clé de partition.
ADR-006 : Keycloak 26 avec import realm automatique.

11 services avec healthcheck obligatoire sur chacun :
  mongodb:7.0      → Replica Set rs0, port 27017
  mongodb-init     → rs.initiate() automatique
  kafka:3.7.0      → KRaft (ports 9092, 9094)
  kafka-init       → 8 topics (voir noms exacts ci-dessous)
  kafka-ui         → provectuslabs, port 8090
  apicurio         → 2.6.0.Final, port 8081
  valkey:8         → port 6379
  keycloak:26.0    → port 8180, import realm-docai.json auto
  prometheus       → port 9090
  grafana          → port 3000
  tempo            → grafana/tempo, port 3200 (HTTP) + port 4317 (OTLP intégré)
                     PAS de conteneur OTEL Collector séparé — Tempo gère les deux ports

Note MASTER SpecKit : Amazon S3 utilisé directement en local et en prod.
  Pas de MinIO. Configurer AWS credentials dans .env :
  AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION, S3_BUCKET_NAME
  Pour les tests intégration → TestContainers LocalStack S3

8 topics exacts dans kafka-init (ADR-002 — documentId partition key) :
  docai.doc.uploaded        6 partitions  rétention 7j
  docai.doc.classified      6 partitions  rétention 7j
  docai.doc.extracted       6 partitions  rétention 7j
  docai.doc.fraud.analyzed  6 partitions  rétention 7j
  docai.doc.completed       3 partitions  rétention 30j
  docai.doc.failed          3 partitions  rétention 30j
  docai.doc.dlq             3 partitions  rétention 90j
  docai.outbox.relay        3 partitions  rétention 1j
```

### Composant 3 — SeedingService DEV
```
speckit-implement

Implémente SeedingService DEV — skill docai-setup-projet.

@Profile("seed") obligatoire — JAMAIS en prod ni en staging.

3 tenants à créer :
  acme-corp   → plan PRO
  beta-assur  → plan STARTER
  gamma-rh    → plan STARTER

10 utilisateurs (MDP : Test1234!) :
  admin@acme-corp.test       → TENANT_ADMIN / acme-corp
  analyst@acme-corp.test     → ANALYST / acme-corp
  viewer@acme-corp.test      → VIEWER / acme-corp
  reviewer@acme-corp.test    → FRAUD_REVIEWER / acme-corp
  admin@beta-assur.test      → TENANT_ADMIN / beta-assur
  analyst@beta-assur.test    → ANALYST / beta-assur
  viewer@beta-assur.test     → VIEWER / beta-assur
  admin@gamma-rh.test        → TENANT_ADMIN / gamma-rh
  analyst@gamma-rh.test      → ANALYST / gamma-rh
  viewer@gamma-rh.test       → VIEWER / gamma-rh

Note : admin@acme-corp.test + admin@beta-assur.test
       servent à tester l'isolation multi-tenant (2 tenants différents).

Idempotence obligatoire :
  Vérifier existence avant insertion → 0 erreur, 0 doublon sur 2 exécutions.
```

---
---


---
---

# ═══════════════════════════════════════════════
# ÉTAPE 2 — ARCHITECTURE & PRINCIPES (MASTER Partie 1 Module 1.A)
# ⚠️ PRÉREQUIS : Module 1.A terminé — projet Maven compilé
# ═══════════════════════════════════════════════

---

# MODULE A — Architecture & Principes

> **Source :** MASTER SpecKit Partie 1 Module 1.A
> **Contenu :** Architecture Hexagonale, SOLID, Design Patterns, ArchUnit 12 règles
> **Skills :** `docai-architecture-adr`
> **ADR :** ADR-002 · ADR-010
> **S'applique à :** TOUS les modules sans exception

---

## 🔵 speckit-specify — Module A

```
speckit-specify

Module  : Module A — Architecture & Principes (Référence Transversale)
Source  : MASTER SpecKit Partie 1 Module 1.A
Skill   : docai-architecture-adr

Objectif — 3 blocs fondamentaux :

BLOC 1 — Architecture Hexagonale (Ports & Adapters)
  Structure Maven 11 modules avec séparation stricte :
    docai-domain     → Java pur — ZERO import framework
    docai-application → Use cases (dépend uniquement de domain)
    docai-adapter-*  → Adapters entrants et sortants
    docai-bootstrap  → Assemblage Spring Boot final
  12 règles ArchUnit vérifiées en CI à chaque commit
  Package racine : fr.docai

BLOC 2 — Principes SOLID appliqués à DocAI
  S — Single Responsibility : 1 Use Case = 1 responsabilité
  O — Open/Closed : Strategy Pattern pour validation (pas de if/else)
  L — Liskov : TesseractOcrAdapter et fallback interchangeables via OcrPort
  I — Interface Segregation : OcrPort ≠ LlmPort ≠ StoragePort — ports fins
  D — Dependency Inversion : Use Cases dépendent des Ports, jamais des Adapters

BLOC 3 — Catalogue Design Patterns par module
  Outbox Pattern      → Upload, tous publishers Kafka
  Strategy            → Classification, Fraude, Extraction
  Registry            → Fraude (FraudAnalyzerRegistry auto-enregistrement)
  Composite           → Fraude (CompositeFraudAnalyzer)
  Fail-Safe/Null Object → Fraude (analyseur défaillant → pipeline continue)
  Cache-Aside         → Extraction, Validation (Valkey avant appel LLM/API)
  Anti-Corruption Layer → Validation externe (INSEE, BAN, RPPS)
  Circuit Breaker     → LLM, OCR, APIs externes (Resilience4j)
  Chain of Responsibility → Validation (Arithmétique → SIRET → IBAN → Adresse)
  CQRS                → Dashboard (Read Model séparé, projection Kafka)
  Saga                → Pipeline (compensations sur chaque échec)
  Factory             → Classification (DocumentFactory par type détecté)
  Bulkhead            → LLM, OCR (Virtual Threads Java 21 isolés)

ADR applicables :
  ADR-002 : clé partition = documentId (jamais tenantId) sur topics pipeline
  ADR-010 : EXPLAIN PLAN MongoDB obligatoire avant chaque merge
```

---

## 🟢 speckit-clarify — Module A

```
speckit-clarify

Clarifie les points suivants du Module A — skill docai-architecture-adr :

1. Le package racine est-il fr.docai sur tous les 11 modules ?
   → Vérifier que ArchUnit utilise packages = "fr.docai"

2. Les 12 règles ArchUnit sont-elles toutes actives ?
   → Aucune désactivation sans validation Tech Lead (BR-ARCH-002)

3. Pour le pattern Strategy en Fraude :
   Les analyseurs sont-ils auto-découverts via Spring DI (@Component)
   ou enregistrés manuellement dans FraudAnalyzerRegistry ?

4. Pour le pattern CQRS Dashboard :
   Le Read Model est-il une collection MongoDB séparée
   ou une vue calculée à la volée ?
   → ADR-011 : lastSyncedAt obligatoire dans le Read Model

5. Pour le Bulkhead avec Virtual Threads Java 21 :
   Utilise-t-on @Async Spring ou Resilience4j Bulkhead directement ?
```

---

## 🟡 speckit-plan — Module A

```
speckit-plan

Génère le plan d'implémentation du Module A — skill docai-architecture-adr.

⚠️ PRÉREQUIS : Module 1.A (Setup Projet) doit être terminé avant cette étape.
   HexagonalArchitectureTest nécessite les 11 modules Maven compilés.

Ordre obligatoire :

ÉTAPE 1 — Structure Maven hexagonale (inclus dans Module 1.A)
  11 modules avec dépendances correctes
  docai-domain → ZERO import framework
  Vérifier : ./mvnw clean compile → BUILD SUCCESS

ÉTAPE 2 — HexagonalArchitectureTest (12 règles ArchUnit)
  Classe dans docai-domain
  12 règles toutes actives (BR-ARCH-001/002)
  Coverage domaine ≥ 90% (BR-ARCH-004)
  PIT ≥ 85% (BR-ARCH-003)
  Vérifier : ./mvnw test -Dtest=HexagonalArchitectureTest → 12 règles vertes

ÉTAPE 3 — Design Patterns de base (structures vides)
  Ports IN (interfaces UseCase) dans docai-domain/port/in/
  Ports OUT (interfaces Repository/Storage/Event) dans docai-domain/port/out/
  Domain Events dans docai-domain/event/
  Domain Exceptions dans docai-domain/exception/
  Vérifier : structure conforme à ArchUnit Règles 4 + 5

ÉTAPE 4 — SOLID vérification dans Checkstyle
  Méthodes ≤ 20 lignes, paramètres ≤ 4, complexité ≤ 10
  Vérifier : ./mvnw checkstyle:check → 0 violation
```

---

## 🟠 speckit-checklist — Module A

```
speckit-checklist

Génère la checklist complète du Module A — skill docai-architecture-adr.
ADR vérifiés : ADR-002 · ADR-010.

ARCHITECTURE HEXAGONALE :
  - [ ] docai-domain : ZERO import org.springframework.* / com.mongodb.* / org.apache.kafka.*
  - [ ] docai-application : dépend UNIQUEMENT de docai-domain
  - [ ] Adapters ne s'appellent pas entre eux (Règle 2 ArchUnit)
  - [ ] Controllers UNIQUEMENT dans docai-adapter-in-rest (Règle 9)
  - [ ] Listeners Kafka UNIQUEMENT dans docai-adapter-in-kafka (Règle 10)
  - [ ] @Document MongoDB UNIQUEMENT dans docai-adapter-out-mongodb (Règle 11)
  - [ ] Pas de @Transactional dans le domaine (Règle 12)

ARCHUNIT — 12 RÈGLES :
  - [ ] Règle 1 : domaine pur Java (pas de framework)
  - [ ] Règle 2 : adapters ne s'appellent pas entre eux
  - [ ] Règle 3 : application dépend uniquement du domaine
  - [ ] Règle 4 : ports IN dans docai-domain/port/in/ (interfaces UseCase)
  - [ ] Règle 5 : ports OUT dans docai-domain/port/out/
  - [ ] Règle 6 : adapters implémentent les ports du domaine
  - [ ] Règle 7 : pas d'accès MongoDB direct depuis le domaine
  - [ ] Règle 8 : pas d'accès Kafka direct depuis le domaine
  - [ ] Règle 9 : controllers dans adapter-in-rest uniquement
  - [ ] Règle 10 : listeners Kafka dans adapter-in-kafka uniquement
  - [ ] Règle 11 : @Document MongoDB dans adapter-out-mongodb uniquement
  - [ ] Règle 12 : pas de @Transactional dans le domaine
  - [ ] BR-ARCH-001 : HexagonalArchitectureTest s'exécute en CI Phase 1
  - [ ] BR-ARCH-002 : 12 règles toutes actives — aucune désactivation
  - [ ] BR-ARCH-003 : PIT ≥ 85% sur docai-domain
  - [ ] BR-ARCH-004 : JaCoCo ≥ 90% sur docai-domain

SOLID :
  - [ ] S : chaque Use Case a une seule responsabilité
  - [ ] O : Strategy Pattern utilisé pour extension (pas de if/else sur types)
  - [ ] L : adapters interchangeables via leur Port (ex: OcrPort)
  - [ ] I : ports fins et ciblés (OcrPort ≠ LlmPort ≠ StoragePort)
  - [ ] D : Use Cases dépendent uniquement des interfaces Ports

DESIGN PATTERNS — ADR-002 :
  - [ ] ADR-002 ✅ : Outbox Pattern — clé partition Kafka = documentId
  - [ ] FraudAnalyzerRegistry : auto-enregistrement via @Component Spring DI
  - [ ] Cache-Aside : Valkey consulté AVANT tout appel LLM/API externe
  - [ ] Anti-Corruption Layer : InseeApiAdapter, BanApiAdapter, RppsApiAdapter
  - [ ] CQRS : Read Model dans collection séparée (ADR-011 : lastSyncedAt)
```

---

## 🔴 speckit-tasks — Module A

```
speckit-tasks

Découpe le Module A en micro-tâches — skill docai-architecture-adr.
Chaque tâche = 1 PR + 1 critère mesurable.

TÂCHE A-01 — HexagonalArchitectureTest 12 règles (0.5j)
  Action  : Créer HexagonalArchitectureTest dans docai-domain
            12 règles toutes actives, @AnalyzeClasses(packages = "fr.docai")
  PR      : test(arch): add HexagonalArchitectureTest 12 rules all active
  Critère : ./mvnw test -Dtest=HexagonalArchitectureTest → 12 règles vertes
            Violation intentionnelle dans docai-domain → CI rouge

TÂCHE A-02 — Structure Ports (0.5j)
  Action  : Créer interfaces Ports IN (UseCase) et Ports OUT (Repository/Storage)
            Domain Events, Domain Exceptions dans les bons packages
  PR      : feat(domain): add port interfaces domain events exceptions structure
  Critère : Règles ArchUnit 4 + 5 passent (ports dans bons packages)

TÂCHE A-03 — Vérification SOLID + Checkstyle (0.25j)
  Action  : Vérifier Checkstyle (méthodes ≤ 20 lignes, complexité ≤ 10)
            Documenter les Design Patterns dans wiki équipe
  PR      : docs: add Design Patterns catalogue SOLID principles wiki
  Critère : ./mvnw checkstyle:check → 0 violation
```

---

## ⚫ speckit-analyse — Module A

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-architecture-adr :

HEXAGONALE (12 règles ArchUnit) :
  1.  docai-domain importe-t-il org.springframework.* ? → Règle 1 — violation
  2.  Un adapter appelle-t-il directement un autre adapter ? → Règle 2 — violation
  3.  docai-application importe-t-il un adapter ? → Règle 3 — violation
  4.  Une interface UseCase est-elle hors de port/in/ ? → Règle 4 — violation
  5.  Une interface Port est-elle hors de port/out/ ? → Règle 5 — violation
  6.  Un @RestController est-il hors de adapter-in-rest ? → Règle 9 — violation
  7.  Un @KafkaListener est-il hors de adapter-in-kafka ? → Règle 10 — violation
  8.  Un @Document MongoDB est-il hors de adapter-out-mongodb ? → Règle 11 — violation
  9.  Une classe du domaine a-t-elle @Transactional ? → Règle 12 — violation

SOLID :
 10. Un Use Case a-t-il plus d'une responsabilité ?
     → Signe : méthode > 20 lignes ou classe > 200 lignes
 11. Un if/else sur type de document remplace-t-il un Strategy Pattern ?
     → Refactorer en Strategy + Registry

DESIGN PATTERNS :
 12. ADR-002 : un ProducerRecord utilise-t-il tenantId comme clé partition ?
     → INTERDIT sur topics pipeline — uniquement documentId

[coller le code ici]
```

---

## 🟣 speckit-implement — Module A

### Composant 1 — HexagonalArchitectureTest (12 règles exactes)
```
speckit-implement

Implémente HexagonalArchitectureTest — skill docai-architecture-adr.
Package : fr.docai.architecture dans docai-domain/test/

@AnalyzeClasses(packages = "fr.docai")
public class HexagonalArchitectureTest {

  Règle 1 — domaine pur Java (ZERO framework) :
    noClasses().that().resideInAPackage("fr.docai.domain..")
      .should().dependOnClassesThat().resideInAnyPackage(
        "org.springframework..", "com.mongodb..", "org.apache.kafka..",
        "io.lettuce..", "software.amazon..", "jakarta.persistence..", "com.stripe..")

  Règle 2 — adapters ne s'appellent pas entre eux :
    noClasses().that().resideInAPackage("fr.docai.adapter..")
      .should().dependOnClassesThat().resideInAPackage("fr.docai.adapter..")

  Règle 3 — application dépend uniquement du domaine :
    classes().that().resideInAPackage("fr.docai.application..")
      .should().onlyDependOnClassesThat()
      .resideInAnyPackage("fr.docai.domain..", "fr.docai.application..",
                          "java..", "javax..", "jakarta.validation..")

  Règle 4 — ports IN dans domain/port/in/ :
    classes().that().haveNameMatching(".*UseCase").and().areInterfaces()
      .should().resideInAPackage("fr.docai.domain.port.in..")

  Règle 5 — ports OUT dans domain/port/out/ :
    classes().that().haveNameMatching(".*Port").and().areInterfaces()
      .should().resideInAPackage("fr.docai.domain.port.out..")

  Règle 6 — adapters implémentent ports du domaine :
    classes().that().haveNameMatching(".*Adapter").and().areNotInterfaces()
      .should().resideInAPackage("fr.docai.adapter..")

  Règle 7 — pas de MongoDB direct dans le domaine :
    noClasses().that().resideInAPackage("fr.docai.domain..")
      .should().accessClassesThat().resideInAPackage("org.springframework.data.mongodb..")

  Règle 8 — pas de Kafka direct dans le domaine :
    noClasses().that().resideInAPackage("fr.docai.domain..")
      .should().accessClassesThat().resideInAPackage("org.springframework.kafka..")

  Règle 9 — controllers dans adapter-in-rest :
    classes().that().areAnnotatedWith(RestController.class)
      .should().resideInAPackage("fr.docai.adapter.in.rest..")

  Règle 10 — listeners Kafka dans adapter-in-kafka :
    classes().that().areAnnotatedWith(KafkaListener.class)
      .should().resideInAPackage("fr.docai.adapter.in.kafka..")

  Règle 11 — @Document MongoDB dans adapter-out-mongodb :
    classes().that().areAnnotatedWith(Document.class)
      .should().resideInAPackage("fr.docai.adapter.out.mongodb..")

  Règle 12 — pas de @Transactional dans le domaine :
    noClasses().that().resideInAPackage("fr.docai.domain..")
      .should().beAnnotatedWith(Transactional.class)
}
```

### Composant 2 — Structure Ports IN/OUT du domaine
```
speckit-implement

Crée la structure des Ports du domaine DocAI — skill docai-architecture-adr.

docai-domain/src/main/java/fr/docai/domain/
├── port/
│   ├── in/          ← Interfaces Use Cases (Inbound Ports)
│   │   Nommage : *UseCase (ex: SubmitDocumentUseCase, GetDocumentStatusUseCase)
│   └── out/         ← Interfaces Repositories/Storage/Events (Outbound Ports)
│       Nommage : *Port (ex: DocumentRepositoryPort, StoragePort, EventPublisherPort)
├── model/           ← Aggregates, Value Objects, Enums, Sealed Classes
├── event/           ← Domain Events (ex: DocumentUploaded, FraudDetected)
├── service/         ← Domain Services (logique métier pure)
└── exception/       ← Exceptions domaine typées (ex: DocumentNotFoundException)

Exemples Ports IN obligatoires :
  SubmitDocumentUseCase, GetDocumentStatusUseCase, ListDocumentsUseCase
  ClassifyDocumentUseCase, ExtractDocumentUseCase
  AnalyzeFraudUseCase, ReviewFraudDecisionUseCase

Exemples Ports OUT obligatoires :
  DocumentRepositoryPort, StoragePort, EventPublisherPort
  QuotaPort, IdempotencyPort, EmailNotificationPort, FeatureFlagPort

Règle absolue : ZERO import Spring/MongoDB/Kafka/AWS dans ce répertoire.
```

---
---


---
---

# ═══════════════════════════════════════════════
# ÉTAPE 3 — STACK & INTÉGRATIONS (MASTER Partie 1 Module 1.B)
# ⚠️ PRÉREQUIS : Module 1.A terminé — modules adapter-out-kafka/valkey disponibles
# ═══════════════════════════════════════════════

---

# MODULE B — Stack & Intégrations

> **Source :** MASTER SpecKit Partie 1 Module 1.B
> **Contenu :** Stack technique, Kafka 8 topics + Consumer Groups + Avro, Valkey strategies, Resilience4j seuils
> **Skills :** `docai-stack-technique`
> **ADR :** ADR-002 (Kafka partition) · ADR-003 (TTL jitter) · ADR-006 (JWKS cache)
> **S'applique à :** Tous les adapters Kafka, Valkey, LLM, OCR, APIs externes

---

## 🔵 speckit-specify — Module B

```
speckit-specify

Module  : Module B — Stack & Intégrations (Référence Transversale)
Source  : MASTER SpecKit Partie 1 Module 1.B
Skill   : docai-stack-technique

Objectif — 4 blocs fondamentaux :

BLOC 1 — Stack Technique (décisions + versions exactes)
  Java 21 LTS · Spring Boot 4.0.x (pas 3.x) · Spring Security 6
  Kafka 3.7 KRaft (pas Zookeeper) · Apicurio Registry 2.6 (pas Confluent)
  Valkey 8.x (pas Redis — changement licence mai 2025)
  MongoDB 7.0 · Amazon S3 SDK v2 · Resilience4j 2.x
  OCR : Tess4J 5.x + PDFBox 3.x · Vision : OpenAI GPT-4o
  Visuel : JavaCV 4.9.0 (pas org.opencv direct)

BLOC 2 — Topologie Kafka (8 topics + Consumer Groups)
  ADR-002 : clé partition = documentId sur tous les topics pipeline
  Exception : docai.doc.failed + docai.doc.dlq = tenantId
  Convention Consumer Group : docai.{module}.{name}.group

BLOC 3 — Stratégies Cache Valkey (ADR-003)
  Jitter ±10% obligatoire sur tout TTL > 1h
  Exception fixe : idempotence (topic:partition:offset) + JWT blacklist
  9 stratégies de cache définies (extraction, INSEE, BAN, RPPS, quota...)

BLOC 4 — Résilience Transversale Resilience4j
  8 services avec seuils exacts (Circuit Breaker, Retry, Bulkhead, Timeout)
  LLM : 50%/10calls · 3×exp 1s · 20 threads · 30s timeout
  Tika/OpenCV : 50%/5calls · timeout 15s (BR-VIS-003)

ADR applicables :
  ADR-002 : documentId comme clé partition Kafka (jamais tenantId pipeline)
  ADR-003 : JitterTtl.withJitter() — jamais TTL fixe > 1h
  ADR-006 : JWKS Keycloak cache TTL 1h en application.yml
```

---

## 🟢 speckit-clarify — Module B

```
speckit-clarify

Clarifie les points suivants du Module B — skill docai-stack-technique :

1. Les schémas Avro sont-ils générés via maven-avro-plugin
   ou écrits manuellement ?
   → Impact sur la configuration pom.xml docai-adapter-out-kafka

2. Les Consumer Group IDs sont-ils définis dans application.yml
   ou directement dans @KafkaListener(groupId = "...") ?
   → Règle : JAMAIS en dur dans le code Java — toujours application.yml

3. ADR-003 — exception TTL fixe :
   Les clés d'idempotence (topic:partition:offset) ont-elles un TTL fixe 24h
   ou avec jitter ?
   → Fixe obligatoire (précision requise pour idempotence)

4. Resilience4j — pour Tika et OpenCV :
   Timeout 15s est-il configuré via @TimeLimiter ou via Bulkhead ?
   → BR-VIS-003 : timeout 15s obligatoire, fail-safe pipeline continue

5. Valkey — le quota mensuel (ADR-001) :
   Le TTL de la clé quota est-il fixe (reset 1er du mois) ?
   → Oui — exception TTL fixe car précision reset requise
```

---

## 🟡 speckit-plan — Module B

```
speckit-plan

Génère le plan d'implémentation du Module B — skill docai-stack-technique.

⚠️ PRÉREQUIS : Module 1.A (Setup Projet) terminé.
   Les schémas Avro, application.yml et JitterTtl nécessitent
   les modules Maven docai-adapter-out-kafka, docai-bootstrap,
   docai-adapter-out-valkey compilés.

Respecte ADR-002, ADR-003, ADR-006.

ÉTAPE 1 — POM parent avec toutes les versions (inclus Module 1.A)
  Versions exactes dans BOM :
    resilience4j 2.3.0, bucket4j 8.10.1, avro 1.11.4
    apicurio 2.6.5.Final, tess4j 5.13.0, pdfbox 3.0.3
    tika 2.9.2, javacv 1.5.11, aws-sdk 2.25.70
  Vérifier : ./mvnw clean compile → BUILD SUCCESS

ÉTAPE 2 — Topologie Kafka dans kafka-init docker-compose
  8 topics avec noms exacts, partitions, rétentions
  ADR-002 : documentId comme clé partition (documentation dans YAML)
  Consumer Group IDs dans application.yml (jamais en dur)
  Vérifier : 8 topics visibles Kafka UI (http://localhost:8090)

ÉTAPE 3 — Configuration Valkey dans application.yml
  9 stratégies de cache documentées
  ADR-003 : JitterTtl.withJitter() — classe utilitaire dans commons-kafka
  Exception : clés idempotence + JWT blacklist = TTL fixe
  Vérifier : connexion Valkey ping → PONG

ÉTAPE 4 — Configuration Resilience4j dans application.yml
  8 services avec seuils exacts (Circuit Breaker + Retry + Bulkhead + Timeout)
  BR-VIS-003 : Tika + OpenCV timeout 15s obligatoire
  Vérifier : configuration chargée au démarrage sans erreur
```

---

## 🟠 speckit-checklist — Module B

```
speckit-checklist

Génère la checklist complète du Module B — skill docai-stack-technique.
ADR vérifiés : ADR-002 · ADR-003 · ADR-006.

STACK TECHNIQUE — VERSIONS EXACTES :
  - [ ] Java 21 LTS (pas Java 17)
  - [ ] Spring Boot 4.0.x (pas 3.x — vérifier pom.xml)
  - [ ] Apicurio Registry 2.6 (pas Confluent Schema Registry)
  - [ ] Valkey 8.x (pas Redis — vérifier docker-compose.yml)
  - [ ] JavaCV 4.9.0 (pas org.opencv direct)
  - [ ] PDFBox 3.x + Tess4J 5.x (pas d'autre librairie OCR)

KAFKA — ADR-002 :
  - [ ] ADR-002 ✅ : 8 topics avec documentId comme clé partition (pipeline)
  - [ ] Exception ADR-002 : docai.doc.failed + docai.doc.dlq = tenantId
  - [ ] Consumer Group IDs définis dans application.yml (JAMAIS en dur)
  - [ ] Convention groupe respectée : docai.{module}.{name}.group
  - [ ] 10 consumer groups exacts :
        docai.recognition.classification.group
        docai.extraction.extraction.group
        docai.extraction.validation.group
        docai.fraud.analysis.group
        docai.pipeline.completion.group
        docai.dashboard.projection.group
        docai.integration.webhook.group
        docai.notification.alert.group
        docai.notification.sse.group
        docai.pipeline.dlq.group
  - [ ] Alerte Grafana lag configurée pour chaque consumer group

VALKEY — ADR-003 :
  - [ ] ADR-003 ✅ : JitterTtl.withJitter() sur tout TTL > 1h (classe utilitaire)
  - [ ] 9 stratégies de cache documentées dans application.yml :
        extraction LLM : 24h jitter · INSEE SIRET : 7j jitter
        BAN adresse : 30j jitter · RPPS médecin : 7j jitter
        JWT blacklist : durée restante JWT FIXE
        Idempotence topic:partition:offset : 24h FIXE
        Idempotence upload X-Idempotency-Key : 24h FIXE
        Quota mensuel : TTL reset 1er du mois FIXE
        Classification SHA-256 : 1h jitter
  - [ ] Exception ADR-003 documentée : idempotence + JWT = TTL fixe

RESILIENCE4J — SEUILS EXACTS :
  - [ ] LLM (OpenAI/Mistral) : CB 50%/10calls · Retry 3× exp 1s · Bulkhead 20 · Timeout 30s
  - [ ] OCR Tess4J : CB 50%/5calls · Retry 3× 2s · Bulkhead 10 · Timeout 60s
  - [ ] API INSEE : CB 60%/5calls · Retry 2× 2s · Bulkhead 5 · Timeout 5s
  - [ ] API BAN : CB 60%/5calls · Retry 2× 2s · Bulkhead 5 · Timeout 5s
  - [ ] API RPPS : CB 60%/8calls · Retry 2× 3s · Bulkhead 5 · Timeout 5s
  - [ ] Apache Tika : CB 50%/5calls · Retry 2× 1s · Bulkhead 5 · Timeout 15s (BR-VIS-003)
  - [ ] OpenCV/JavaCV : CB 50%/5calls · Retry 1× · Bulkhead 5 · Timeout 15s (BR-VIS-003)
  - [ ] Amazon S3 : CB 50%/10calls · Retry 3× exp 1s · Bulkhead 20 · Timeout 30s
  - [ ] Wait duration open state : 30s (LLM, S3) / 60s (OCR)
  - [ ] Transition HALF_OPEN après 3 appels autorisés

MÉTRIQUES MICROMETER :
  - [ ] docai_document_upload_total{tenant, type}
  - [ ] docai_document_processing_duration_seconds{module}
  - [ ] docai_circuit_breaker_state{service}
  - [ ] docai_cache_hit_ratio{region}
  - [ ] docai_kafka_consumer_lag{topic, group}
  - [ ] Alertes Grafana : error rate > 1%, Circuit Breaker OPEN, lag > 1000
```

---

## 🔴 speckit-tasks — Module B

```
speckit-tasks

Découpe le Module B en micro-tâches — skill docai-stack-technique.
Chaque tâche = 1 PR + 1 critère mesurable.

TÂCHE B-01 — Consumer Group IDs dans application.yml (0.5j)
  Action  : Définir les 10 consumer group IDs dans application.yml
            Convention docai.{module}.{name}.group (jamais en dur)
            ADR-002 : documentId clé partition documenté
  PR      : feat(kafka): add 10 consumer group IDs application.yml ADR-002
  Critère : Aucun groupId hardcodé dans le code Java
            10 groupIds visibles dans application.yml

TÂCHE B-02 — Stratégies Cache Valkey + JitterTtl (0.5j)
  Action  : 9 stratégies de cache documentées dans application.yml
            JitterTtl utilitaire (ADR-003) dans commons-kafka
            Exceptions TTL fixe documentées (idempotence, JWT)
  PR      : feat(cache): add 9 Valkey strategies JitterTtl ADR-003 exceptions
  Critère : JitterTtl.withJitter(Duration.ofHours(24)) retourne valeur entre 21.6h et 26.4h
            Clés idempotence = TTL fixe 24h (pas de jitter)

TÂCHE B-03 — Configuration Resilience4j seuils exacts (0.5j)
  Action  : application.yml avec seuils exacts pour 8 services
            BR-VIS-003 : Tika + OpenCV timeout 15s configuré
            Wait duration + HALF_OPEN transitions
  PR      : feat(resilience): add Resilience4j exact thresholds 8 services BR-VIS-003
  Critère : Configuration chargée au démarrage sans erreur
            LLM CB : 50%/10calls confirmé dans /actuator/circuitbreakers
```

---

## ⚫ speckit-analyse — Module B

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-stack-technique
(ADR-002, ADR-003, BR-VIS-003) :

KAFKA — ADR-002 :
  1. Un ProducerRecord utilise-t-il tenantId comme clé partition
     sur un topic pipeline (uploaded, classified, extracted, fraud.analyzed) ?
     → INTERDIT — uniquement documentId
  2. Un @KafkaListener contient-il un groupId hardcodé ?
     → INTERDIT — doit référencer application.yml

VALKEY — ADR-003 :
  3. Un appel Valkey utilise-t-il Duration.ofHours(X) directement ?
     → Si TTL > 1h → VIOLATION ADR-003 — utiliser JitterTtl.withJitter()
  4. Les clés d'idempotence (topic:partition:offset) ont-elles un jitter ?
     → INTERDIT — TTL fixe 24h obligatoire pour idempotence

RESILIENCE4J :
  5. Le timeout Tika est-il supérieur à 15s ?
     → VIOLATION BR-VIS-003 — timeout 15s MAX
  6. Le timeout OpenCV est-il supérieur à 15s ?
     → VIOLATION BR-VIS-003 — timeout 15s MAX
  7. Le seuil Circuit Breaker LLM est-il différent de 50%/10 calls ?
     → Vérifier vs seuils exacts du SpecKit

STACK :
  8. Une librairie utilise-t-elle org.opencv (pas JavaCV) ?
     → VIOLATION — utiliser JavaCV 4.9.0 uniquement
  9. Une version Spring Boot est-elle 3.x (pas 4.0.x) ?
     → VIOLATION — Spring Boot 4.0.x obligatoire

[coller le code ici]
```

---

## 🟣 speckit-implement — Module B

### Composant 1 — Schémas Avro des 8 events Kafka
```
speckit-implement

Crée les schémas Avro des 8 events Kafka — skill docai-stack-technique.
ADR-002 : documentId comme champ de clé partition dans chaque schéma.

Localisation : docai-adapter-out-kafka/src/main/avro/

8 schémas à créer :
  DocumentUploadedEvent.avsc
    fields : documentId (string), tenantId (string), fileName (string),
             mimeType (string), sizeBytes (long), s3Key (string),
             contentHash (string), uploadedAt (long — epoch ms),
             occurredAt (long — epoch ms)

  DocumentClassifiedEvent.avsc
    fields : documentId, tenantId, documentType (enum),
             confidenceScore (float 0.0-1.0), modelVersion (string),
             occurredAt (long)

  DocumentExtractedEvent.avsc
    fields : documentId, tenantId, documentType (enum),
             extractedFields (map<string, string>), globalScore (float),
             rawOcrTextS3Key (string — ADR-004), occurredAt (long)

  DocumentFraudAnalyzedEvent.avsc
    fields : documentId, tenantId, fraudScore (int 0-100),
             riskLevel (enum FAIBLE/MODERE/ELEVE/CRITIQUE),
             signals (array<FraudSignal>), occurredAt (long)

  DocumentCompletedEvent.avsc
    fields : documentId, tenantId, finalStatus (enum), occurredAt (long)

  DocumentFailedEvent.avsc
    fields : documentId, tenantId, failureStage (string),
             errorCode (string), occurredAt (long)

  OutboxRelayEvent.avsc
    fields : outboxId (string), aggregateId (string), eventType (string),
             payload (bytes), occurredAt (long)

Convention Apicurio Registry :
  Namespace : fr.docai.kafka.events
  Enregistrement automatique au démarrage via maven-avro-plugin
```

### Composant 2 — application.yml Resilience4j seuils exacts
```
speckit-implement

Configure Resilience4j dans application.yml — skill docai-stack-technique.
Seuils exacts pour les 8 services. BR-VIS-003 : Tika + OpenCV timeout 15s.

resilience4j:
  circuitbreaker:
    instances:
      llm:
        failureRateThreshold: 50
        minimumNumberOfCalls: 10
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
      ocr:
        failureRateThreshold: 50
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 3
      insee:
        failureRateThreshold: 60
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 30s
      s3:
        failureRateThreshold: 50
        minimumNumberOfCalls: 10
        waitDurationInOpenState: 30s
  timelimiter:
    instances:
      llm:      { timeoutDuration: 30s }
      ocr:      { timeoutDuration: 60s }
      tika:     { timeoutDuration: 15s }  # BR-VIS-003 — JAMAIS modifier
      opencv:   { timeoutDuration: 15s }  # BR-VIS-003 — JAMAIS modifier
      insee:    { timeoutDuration: 5s }
      ban:      { timeoutDuration: 5s }
      rpps:     { timeoutDuration: 5s }
      s3:       { timeoutDuration: 30s }
  retry:
    instances:
      llm:   { maxAttempts: 3, waitDuration: 1s, enableExponentialBackoff: true }
      ocr:   { maxAttempts: 3, waitDuration: 2s }
      insee: { maxAttempts: 2, waitDuration: 2s }
      s3:    { maxAttempts: 3, waitDuration: 1s, enableExponentialBackoff: true }
  bulkhead:
    instances:
      llm:   { maxConcurrentCalls: 20 }
      ocr:   { maxConcurrentCalls: 10 }
      insee: { maxConcurrentCalls: 5 }
      tika:  { maxConcurrentCalls: 5 }
      s3:    { maxConcurrentCalls: 20 }
```

### Composant 3 — JitterTtl + Stratégies Valkey (ADR-003)
```
speckit-implement

Implémente JitterTtl et les stratégies cache Valkey — skill docai-stack-technique.
ADR-003 : jitter ±10% obligatoire sur tout TTL > 1h.

Classe utilitaire JitterTtl dans docai-adapter-out-valkey/ :
  public static Duration withJitter(Duration base) {
    double jitter = 0.9 + (Math.random() * 0.2); // 0.9 à 1.1 (±10%)
    return Duration.ofMillis((long)(base.toMillis() * jitter));
  }

9 stratégies de cache dans application.yml :
  docai.cache.extraction-llm.ttl: 24h   # → JitterTtl.withJitter()
  docai.cache.insee-siret.ttl: 7d       # → JitterTtl.withJitter()
  docai.cache.ban-address.ttl: 30d      # → JitterTtl.withJitter()
  docai.cache.rpps.ttl: 7d             # → JitterTtl.withJitter()
  docai.cache.classification.ttl: 1h   # → JitterTtl.withJitter()
  docai.cache.jwt-blacklist.ttl: fixed  # TTL fixe = durée restante JWT
  docai.cache.idempotency.ttl: 24h     # TTL fixe — précision idempotence
  docai.cache.quota.ttl: monthly-reset # TTL fixe — reset 1er du mois
  docai.cache.upload-idempotency.ttl: 24h # TTL fixe

Clés Valkey exactes :
  extraction:{sha256}              → TTL 24h jitter
  insee:siret:{siret}              → TTL 7j jitter
  ban:address:{hash}               → TTL 30j jitter
  rpps:{numero}                    → TTL 7j jitter
  jwt:blacklist:{jti}              → TTL fixe (durée restante)
  idempotent:{topic}:{partition}:{offset} → TTL 24h fixe
  idempotency:{X-Idempotency-Key}  → TTL 24h fixe
  quota:{tenantId}:{year}-{month}  → TTL reset 1er du mois
  classification:{sha256}          → TTL 1h jitter
```

---
---


---
---

# ═══════════════════════════════════════════════
# ÉTAPE 4 — PERSISTANCE & STANDARDS (MASTER Partie 1 Module 1.C)
# ⚠️ PRÉREQUIS : Module 1.A terminé — adapter-out-mongodb + bootstrap disponibles
# ═══════════════════════════════════════════════

---

# MODULE C — Persistance & Standards

> **Source :** MASTER SpecKit Partie 1 Module 1.C
> **Contenu :** Observabilité logs, MongoDB collections, Mongock V001→V008, Pagination BR-PAG-001→008, Versioning API /v1/
> **Skills :** `docai-persistance-standards` · `docai-observability`
> **ADR :** ADR-010 (EXPLAIN PLAN) · ADR-011 (lastSyncedAt Read Model)
> **Annex B :** Standards MongoDB complets
> **S'applique à :** Tous les adapters MongoDB, tous les endpoints paginés

---

## 🔵 speckit-specify — Module C

```
speckit-specify

Module  : Module C — Persistance & Standards (Référence Transversale)
Source  : MASTER SpecKit Partie 1 Module 1.C
Skills  : docai-persistance-standards, docai-observability

Objectif — 3 blocs fondamentaux :

BLOC 1 — Observabilité Transversale
  Logs JSON structurés (jamais texte brut en staging/prod)
  traceId + tenantId dans CHAQUE ligne de log
  PII → [PII_MASKED] obligatoire (email, nom, SIRET, IBAN)
  Niveaux : ERROR (non récupérable) / WARN (dégradé récupéré)
             INFO (flux nominal) / DEBUG (dev local uniquement)
  14 métriques Micrometer documentées
  6 alertes Grafana configurées

BLOC 2 — MongoDB Standards (Annex B complète)
  15 collections définies avec nommage snake_case pluriel
  Stratégie indexation : tenantId EN PREMIER (ADR-010)
  EXPLAIN PLAN obligatoire avant chaque merge (winningPlan.stage = IXSCAN)
  Mongock V001→V008 : convention V{NNN}_{module}_{description}
  BR-MIG-001 à 007 : règles migrations strictes
  auto-index-creation: false en production

BLOC 3 — Pagination + Versioning API
  BR-PAG-001 à 008 : standard paginé pour TOUS les endpoints liste
  Format réponse : { data: [...], page: { number, size, totalElements... } }
  Maximum 100 éléments par page (BR-PAG-002) → HTTP 400 sinon
  Implémenté une seule fois dans commons-api (BR-PAG-008)
  Versioning : préfixe /v1/ obligatoire sur toutes les routes
  Breaking change → /v2/ avec /v1/ maintenu 6 mois minimum

ADR applicables :
  ADR-010 : EXPLAIN PLAN avant chaque merge + partial index si actif < 20%
  ADR-011 : lastSyncedAt obligatoire dans Read Model (document_summary_views)
```

---

## 🟢 speckit-clarify — Module C

```
speckit-clarify

Clarifie les points suivants du Module C — skill docai-persistance-standards :

1. La pagination est-elle implémentée dans commons-api une seule fois
   et réutilisée dans tous les modules (BR-PAG-008) ?
   → Aucun module ne doit réimplémenter sa propre pagination

2. ADR-010 : l'EXPLAIN PLAN est-il vérifié automatiquement en CI
   ou manuellement en PR review ?
   → Actuellement : checklist PR manuelle (Tâche 1.B-02)

3. Les migrations Mongock sont-elles toutes backward-compatible (BR-MIG-002) ?
   → Jamais supprimer un champ en 1 seule migration

4. Le champ lastSyncedAt (ADR-011) est-il présent dans
   document_summary_views dès la Partie 1
   ou uniquement en Partie 5 (Dashboard) ?
   → Structure définie maintenant, remplie en Partie 5

5. Le versioning /v1/ est-il configuré globalement dans Spring
   (@RequestMapping("/v1") sur tous les controllers)
   ou via application.yml (server.servlet.context-path = /v1) ?
```

---

## 🟡 speckit-plan — Module C

```
speckit-plan

Génère le plan d'implémentation du Module C
— skills docai-persistance-standards + docai-observability.

⚠️ PRÉREQUIS : Module 1.A (Setup Projet) terminé.
   Mongock V001 nécessite docai-adapter-out-mongodb.
   commons-api pagination nécessite un module commons.
   Logs JSON nécessitent docai-bootstrap/resources.

Respecte ADR-010, ADR-011 + Annex B.

ÉTAPE 1 — Configuration logs JSON structurés (0.25j)
  logback-spring.xml avec Logstash Logback Encoder
  MDC : traceId + tenantId dans chaque thread
  PII masqués : email → [PII_MASKED], SIRET → [PARTIAL_MASK]
  DEBUG désactivé en staging/prod
  Vérifier : log de test visible avec traceId + tenantId

ÉTAPE 2 — Mongock V001 (première migration) (0.5j)
  V001_setup_documents_collection
  Collections : documents + index (tenantId, status, createdAt)
  ADR-010 : tenantId EN PREMIER dans tous les index composés
  auto-index-creation: false dans application.yml
  @RollbackExecution obligatoire (BR-MIG-004)
  Vérifier : Mongock s'exécute au démarrage sans erreur

ÉTAPE 3 — commons-api pagination globale BR-PAG (0.5j)
  ApiResponse<T> record avec champ page
  Paramètres : page=0, size=20 (max 100), sort=createdAt,desc
  HTTP 400 si size > 100 (BR-PAG-005)
  EXPLAIN PLAN MongoDB validé pour requête paginée
  Vérifier : GET /v1/documents?size=101 → HTTP 400

ÉTAPE 4 — Versioning API /v1/ (0.25j)
  @RequestMapping("/v1") sur tous les controllers
  ou server.servlet.context-path: /v1 dans application.yml
  Header Deprecation/Sunset pour futures versions
  Vérifier : GET /v1/documents → HTTP 200 (pas /documents)
```

---

## 🟠 speckit-checklist — Module C

```
speckit-checklist

Génère la checklist complète du Module C
— skills docai-persistance-standards + docai-observability.
ADR vérifiés : ADR-010 · ADR-011. Annexes : Annex B.

OBSERVABILITÉ :
  - [ ] Logs JSON structurés (logback-spring.xml + Logstash Encoder)
  - [ ] traceId et tenantId dans CHAQUE ligne de log (MDC obligatoire)
  - [ ] PII masqués : email, IBAN, SIRET, nom → [PII_MASKED]
  - [ ] Niveaux respectés : ERROR / WARN / INFO / DEBUG (DEV uniquement)
  - [ ] 14 métriques Micrometer exposées sur /actuator/prometheus
  - [ ] 6 alertes Grafana configurées :
        error rate > 1% · Circuit Breaker OPEN · Kafka lag > 1000
        Valkey hit ratio < 30% · DLQ > 10 · P99 > 500ms

MONGODB — ADR-010 + ANNEX B :
  - [ ] 15 collections avec nommage snake_case pluriel (Annex B)
  - [ ] Champs camelCase, dates suffixées At (Annex B)
  - [ ] auto-index-creation: false dans application.yml (BR-MIG-003)
  - [ ] ADR-010 ✅ : tenantId EN PREMIER dans tous les index composés
  - [ ] EXPLAIN PLAN avant chaque merge (winningPlan.stage = IXSCAN)
  - [ ] Partial index si sélectivité actif < 20%
  - [ ] ADR-011 ✅ : champ lastSyncedAt dans document_summary_views

MONGOCK — BR-MIG-001 à 007 :
  - [ ] BR-MIG-001 : chaque migration dans sa propre classe @ChangeUnit
  - [ ] BR-MIG-002 : migrations backward-compatible (pas de drop de champ)
  - [ ] BR-MIG-003 : uniquement via Mongock — pas d'index @Indexed dans le code
  - [ ] BR-MIG-004 : @RollbackExecution présente dans chaque migration
  - [ ] BR-MIG-005 : uniquement DDL — pas de logique métier
  - [ ] BR-MIG-006 : testées en staging avant production
  - [ ] BR-MIG-007 : migration échouée → application refuse de démarrer
  - [ ] V001_setup_documents_collection créée (première migration)
  - [ ] Convention V{NNN}_{module}_{description} respectée

PAGINATION — BR-PAG-001 à 008 :
  - [ ] BR-PAG-001 : paramètres page, size, sort sur tous les endpoints liste
  - [ ] BR-PAG-002 : size maximum 100 — sinon HTTP 400
  - [ ] BR-PAG-003 : size par défaut 20
  - [ ] BR-PAG-004 : réponse avec totalElements + totalPages
  - [ ] BR-PAG-005 : size > 100 → HTTP 400 "Maximum page size is 100"
  - [ ] BR-PAG-006 : tri par défaut createdAt,desc
  - [ ] BR-PAG-007 : champs de tri documentés dans OpenAPI
  - [ ] BR-PAG-008 : implémenté dans commons-api — jamais réimplémenté
  - [ ] Format réponse : { data: [...], page: { number, size, totalElements, totalPages } }

VERSIONING API :
  - [ ] Préfixe /v1/ sur toutes les routes REST
  - [ ] Breaking change → /v2/ avec /v1/ maintenu 6 mois minimum
  - [ ] Header Deprecation + Sunset sur routes dépréciées
  - [ ] OpenAPI spec générée automatiquement en CI (SpringDoc)
```

---

## 🔴 speckit-tasks — Module C

```
speckit-tasks

Découpe le Module C en micro-tâches — skill docai-persistance-standards.
Chaque tâche = 1 PR + 1 critère mesurable.

TÂCHE C-01 — Logs JSON structurés + MDC (0.25j)
  Action  : logback-spring.xml avec Logstash Encoder
            MDC tenantId + traceId dans chaque thread
            Masquage PII configuré
  PR      : feat(observability): add JSON logs MDC tenantId traceId PII masking
  Critère : Log de test contient traceId + tenantId en JSON
            Email dans un log → [PII_MASKED] visible

TÂCHE C-02 — Mongock V001 + auto-index-creation false (0.5j)
  Action  : V001_setup_documents_collection avec index (ADR-010)
            auto-index-creation: false dans application.yml
            EXPLAIN PLAN documenté dans PR
  PR      : feat(mongodb): add Mongock V001 documents collection ADR-010 indexes
  Critère : Mongock s'exécute au démarrage sans erreur
            COLLSCAN absent → IXSCAN confirmé (EXPLAIN PLAN)

TÂCHE C-03 — commons-api pagination BR-PAG (0.5j)
  Action  : ApiResponse<T> + PageRequest dans commons-api
            Validation size ≤ 100 (HTTP 400 sinon)
            EXPLAIN PLAN sur requête paginée
  PR      : feat(commons): add pagination ApiResponse BR-PAG-001-008 max-100
  Critère : GET /v1/documents?size=101 → HTTP 400
            GET /v1/documents → réponse avec totalElements + totalPages

TÂCHE C-04 — Versioning /v1/ (0.25j)
  Action  : Configurer préfixe /v1/ globalement
            OpenAPI spec exposée à /v1/api-docs
            Swagger UI à /v1/swagger-ui.html
  PR      : feat(api): add /v1 versioning prefix OpenAPI SpringDoc
  Critère : GET /v1/documents → HTTP 200
            GET /documents → HTTP 404 (pas de route sans /v1/)
```

---

## ⚫ speckit-analyse — Module C

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-persistance-standards
(ADR-010, ADR-011, BR-PAG, BR-MIG, Annex B) :

LOGS :
  1. Un log contient-il un email, IBAN ou SIRET en clair ?
     → VIOLATION — masquer avec [PII_MASKED]
  2. Un log ne contient-il pas traceId ou tenantId ?
     → VIOLATION — MDC obligatoire dans chaque thread

MONGODB — ADR-010 + Annex B :
  3. Un @Document MongoDB a-t-il @Indexed ?
     → VIOLATION BR-MIG-003 — uniquement via Mongock
  4. Un index composite n'a-t-il pas tenantId en premier champ ?
     → VIOLATION ADR-010 — tenantId toujours en position 1
  5. Une requête MongoDB a-t-elle été ajoutée sans EXPLAIN PLAN documenté ?
     → VIOLATION ADR-010 — IXSCAN obligatoire
  6. La collection document_summary_views a-t-elle le champ lastSyncedAt ?
     → ADR-011 — obligatoire pour le Read Model

MONGOCK :
  7. Une migration n'a-t-elle pas de @RollbackExecution ?
     → VIOLATION BR-MIG-004
  8. Une migration supprime-t-elle un champ en 1 étape ?
     → VIOLATION BR-MIG-002 — backward-compatible obligatoire

PAGINATION — BR-PAG :
  9. Un endpoint liste n'utilise-t-il pas ApiResponse<T> de commons-api ?
     → VIOLATION BR-PAG-008 — réimplémentation interdite
 10. Un endpoint liste accepte-t-il size > 100 sans HTTP 400 ?
     → VIOLATION BR-PAG-005

API :
 11. Un endpoint n'a-t-il pas le préfixe /v1/ ?
     → VIOLATION versioning — toutes les routes sont /v1/*

[coller le code ici]
```

---

## 🟣 speckit-implement — Module C

### Composant 1 — Mongock V001 (première migration)
```
speckit-implement

Implémente la première migration Mongock DocAI — skill docai-persistance-standards.
ADR-010 : tenantId EN PREMIER dans tous les index composés.
BR-MIG-001 à 007 : règles obligatoires.

@ChangeUnit(id = "V001_setup_documents_collection",
            order = "001", author = "docai-team")
public class V001SetupDocumentsCollection {

  @Execution
  public void execute(MongoDatabase db) {
    MongoCollection<Document> col = db.getCollection("documents");

    // Index 1 : tenantId EN PREMIER (ADR-010) — liste filtrée statut
    col.createIndex(Indexes.ascending("tenantId", "status", "createdAt"),
      new IndexOptions().name("idx_tenant_status_created"));

    // Index 2 : tenantId EN PREMIER — liste filtrée type
    col.createIndex(Indexes.ascending("tenantId", "type", "createdAt"),
      new IndexOptions().name("idx_tenant_type_created"));

    // Index 3 : tenantId EN PREMIER — pagination chronologique
    col.createIndex(Indexes.ascending("tenantId", "createdAt"),
      new IndexOptions().name("idx_tenant_created"));

    // Index 4 : Unique — déduplication upload (SHA-256)
    col.createIndex(Indexes.ascending("contentHash", "tenantId"),
      new IndexOptions().unique(true).name("idx_content_hash_unique"));

    // Index 5 : Unique — idempotence X-Idempotency-Key
    col.createIndex(Indexes.ascending("idempotencyKey"),
      new IndexOptions().unique(true).sparse(true).name("idx_idempotency_unique"));
  }

  @RollbackExecution  // BR-MIG-004 : toujours présent
  public void rollback(MongoDatabase db) {
    db.getCollection("documents").drop();
  }
}

EXPLAIN PLAN à documenter dans la PR :
  db.documents.find({tenantId:"acme", status:"PENDING"}).explain("executionStats")
  → winningPlan.stage doit être "IXSCAN"
```

### Composant 2 — Pagination commons-api (BR-PAG-001 à 008)
```
speckit-implement

Implémente la pagination globale dans commons-api — skill docai-persistance-standards.
BR-PAG-001 à 008 — implémenté UNE SEULE FOIS, jamais réimplémenté.

record ApiResponse<T>(List<T> data, PageInfo page) {
  record PageInfo(int number, int size, long totalElements,
                  int totalPages, boolean first, boolean last) {}
}

Validation dans chaque Controller :
  @GetMapping
  public ApiResponse<DocumentSummary> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,        // BR-PAG-003
      @RequestParam(defaultValue = "createdAt,desc") String sort) {

    if (size > 100) {                                     // BR-PAG-002 + BR-PAG-005
      throw new ValidationException("Maximum page size is 100");
    }
    // ... logique paginée
  }

Format réponse exact (BR-PAG-004) :
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

EXPLAIN PLAN à valider avant merge :
  db.documents.find({tenantId:"acme"})
    .sort({createdAt: -1}).skip(0).limit(20).explain("executionStats")
  → winningPlan.stage = IXSCAN obligatoire (ADR-010)
```

### Composant 3 — Logs JSON structurés + MDC
```
speckit-implement

Configure les logs JSON structurés — skill docai-observability.
traceId + tenantId dans CHAQUE log. PII masqués. Jamais de texte brut en prod.

logback-spring.xml dans docai-bootstrap/src/main/resources/ :
  Profils dev  → console texte (lisible en développement)
  Profils staging/prod → JSON (Logstash Logback Encoder)

MDC enrichment dans TenantJwtFilter (Partie 3) :
  MDC.put("tenantId", tenantId);
  MDC.put("traceId", traceId);
  // Dans finally : MDC.remove("tenantId"); MDC.remove("traceId");

Format JSON obligatoire :
  {
    "timestamp": "2026-05-22T10:00:00.000Z",
    "level": "INFO",
    "service": "docai-backend",
    "traceId": "abc123",
    "spanId": "def456",
    "tenantId": "acme-corp",
    "message": "Document submitted"
  }

Masquage PII (jamais de vraies valeurs) :
  email    → [PII_MASKED]
  SIRET    → [PARTIAL_MASK]
  IBAN     → [PII_MASKED]
  nom/prénom → [PII_MASKED]

Niveaux obligatoires :
  ERROR : exceptions non récupérables (pipeline arrêté)
  WARN  : état dégradé mais récupéré (fallback activé, cache miss)
  INFO  : flux nominal (document soumis, classifié, extrait)
  DEBUG : développement local UNIQUEMENT (désactivé staging/prod)
```

---


---
---

# ═══════════════════════════════════════════════
# ÉTAPE 5 — CI/CD PIPELINE (MASTER Partie 2 Module 2.C)
# ⚠️ PRÉREQUIS : Modules 1.A + A + B + C terminés — build Maven vert requis
# ═══════════════════════════════════════════════

---

# MODULE 1.B — Standards & CI/CD

> **Contenu :** Checkstyle, ArchUnit en CI, SonarCloud, Dockerfile, K8s, Feature Flags, Observabilité
> **Durée estimée :** 2-3 jours
> **Skills :** `docai-cicd-pipeline` · `docai-architecture-adr` · `docai-feature-flag` · `docai-observability`
> **ADR applicables :** ADR-008 (CI 3 jobs + Xmx512m + reuse) · ADR-010 (EXPLAIN PLAN MongoDB)

---

## 🔵 speckit-specify — Module 1.B

```
speckit-specify

Module  : Module 1.B — Standards & CI/CD
Partie  : Partie 1 — Setup & CI/CD (Semaine 1)
Skills  : docai-cicd-pipeline, docai-architecture-adr,
          docai-feature-flag, docai-observability

Objectif :
  - Pipeline CI GitHub Actions (01-ci.yml, 02-docker.yml, 03-deploy-staging.yml)
  - Dockerfile multi-stage JRE 21 Alpine (utilisateur non-root "docai")
  - Manifestes Kubernetes (deployment.yaml, service.yaml, hpa.yaml)
  - Quality Gates SonarCloud bloquants
  - Checkstyle (méthodes ≤ 20 lignes, paramètres ≤ 4, complexité ≤ 10)
  - 6 Feature Flags Unleash configurés
  - Observabilité : logs JSON structurés (tenantId + traceId), métriques Micrometer, OpenTelemetry

ADR applicables :
  ADR-008 ✅ : 3 jobs CI séparés + MAVEN_OPTS=-Xmx512m + TESTCONTAINERS_REUSE_ENABLE=true
               (éviter OOM GitHub Runner 7GB)
  ADR-010 ✅ : Slow query log MongoDB activé en DEV + EXPLAIN PLAN gate en PR

Quality Gates bloquants :
  Violation ArchUnit (12 règles)     → pipeline arrêté immédiatement
  Coverage docai-domain < 90%        → PR bloquée
  Coverage global < 80%              → PR bloquée
  ≥ 1 bug SonarCloud nouveau code    → merge bloqué
  Vulnérabilité CRITICAL Trivy       → image Docker non publiée
```

---

## 🟢 speckit-clarify — Module 1.B

```
speckit-clarify

Clarifie les points suivants du Module 1.B — skill docai-cicd-pipeline :

1. Registry Docker : GitHub Container Registry (ghcr.io) ou Docker Hub ?
2. Le déploiement production (04-deploy-production.yml) nécessite-t-il
   une approbation manuelle via GitHub Environment protection ?
3. Unleash est-il self-hosted (dans docker-compose.yml)
   ou SaaS cloud (app.unleash.io) ?
4. Les logs JSON structurés : logback-spring.xml personnalisé
   ou configuration Spring Boot + logstash-logback-encoder ?
5. OpenTelemetry : OTEL Java Agent (javaagent jar)
   ou Spring Boot Actuator micrometer-tracing + OTLP ?
6. ADR-008 : y a-t-il un runner GitHub self-hosted disponible
   ou on reste sur ubuntu-latest 7GB ?
7. ADR-010 : le EXPLAIN PLAN est-il vérifié manuellement en PR review
   ou via un script automatisé dans le CI ?
```

---

## 🟡 speckit-plan — Module 1.B

```
speckit-plan

Génère le plan d'implémentation du Module 1.B — skill docai-cicd-pipeline.
Respecte ADR-008 (3 jobs CI séparés) et ADR-010 (EXPLAIN PLAN gate).

Ordre obligatoire :

ÉTAPE 1 — Checkstyle (0.5j)
  maven-checkstyle-plugin : méthodes ≤ 20 lignes, paramètres ≤ 4,
  complexité cyclomatique ≤ 10, classes ≤ 200 lignes
  Vérifier : ./mvnw checkstyle:check → 0 violation

ÉTAPE 2 — GitHub Actions 01-ci.yml (1j) — ADR-008
  5 jobs séparés obligatoires :
    Job 1 unit-tests     : MAVEN_OPTS=-Xmx512m (domain + application + ArchUnit)
    Job 2 integration    : MAVEN_OPTS=-Xmx512m, TESTCONTAINERS_REUSE_ENABLE=true
    Job 3 bdd-tests      : MAVEN_OPTS=-Xmx512m, TESTCONTAINERS_REUSE_ENABLE=true
    Job 4 contract-tests : spring-cloud-contract:generateTests
    Job 5 sonarcloud     : needs [unit, integration, bdd]
  ADR-010 gate : EXPLAIN PLAN checklist dans PR template
  Vérifier : premier build vert sur develop

ÉTAPE 3 — Dockerfile multi-stage (0.5j)
  Stage 1 dependencies : eclipse-temurin:21-jdk-alpine + mvn dependency:go-offline
  Stage 2 build        : mvn clean package + jar layertools extract
  Stage 3 runtime      : eclipse-temurin:21-jre-alpine
    Utilisateur non-root : addgroup docai + adduser docai
    JVM : -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
    HEALTHCHECK wget /actuator/health
  Vérifier : image < 300MB, utilisateur docai confirmé

ÉTAPE 4 — 02-docker.yml + 03-deploy-staging.yml (0.5j)
  02-docker : Build + Trivy CRITICAL=exit-code 1 + Push GHCR
  03-staging : deploy auto sur push develop avec health check post-déploiement
  Vérifier : Trivy bloque CRITICAL, image publiée GHCR

ÉTAPE 5 — Manifestes Kubernetes (0.5j)
  deployment.yaml : RollingUpdate maxUnavailable=0 maxSurge=1 (BR-K8S-001)
  hpa.yaml        : minReplicas=2 maxReplicas=10 CPU 70% (BR-K8S-004)
  Secrets via AWS Secrets Manager CSI Driver — jamais dans manifestes
  Vérifier : kubectl apply → 0 erreur en staging

ÉTAPE 6 — Feature Flags Unleash (0.5j)
  Interface FeatureFlagPort dans docai-domain/port/out/
  Adapter UnleashFeatureFlagAdapter avec fallback false (fail-safe)
  6 flags : billing.enabled, fraud.v2.enabled, extraction.mistral.enabled,
            dashboard.search.enabled, notifications.inapp.enabled, maintenance.mode
  Vérifier : billing.enabled = false en DEV

ÉTAPE 7 — Observabilité (0.5j)
  Logs JSON structurés avec tenantId + traceId dans chaque ligne de log
  PII masqués : IBAN → ****1234, SIRET → ***-***-***-***
  Métriques Micrometer → /actuator/prometheus
  Tracing OpenTelemetry → Grafana Tempo
  Vérifier : log visible avec tenantId dans Grafana, prometheus UP
```

---

## 🟠 speckit-checklist — Module 1.B

```
speckit-checklist

Génère la checklist complète du Module 1.B — skill docai-cicd-pipeline.
ADR vérifiés : ADR-008 (obligatoire) · ADR-010 (obligatoire).

CI/CD GITHUB ACTIONS — ADR-008 :
  - [ ] ADR-008 ✅ : 01-ci.yml avec 5 jobs séparés (jamais 1 seul job monolithique)
  - [ ] ADR-008 ✅ : MAVEN_OPTS=-Xmx512m -Xms256m sur TOUS les jobs
  - [ ] ADR-008 ✅ : TESTCONTAINERS_REUSE_ENABLE=true sur jobs intégration et BDD
  - [ ] Job SonarCloud dépend de tous les jobs tests (needs: [unit, integration, bdd])
  - [ ] Déclencheurs : push [develop, main] + pull_request [develop, main] + tag v*.*.*
  - [ ] 02-docker.yml : Trivy avec exit-code: 1 sur severity CRITICAL,HIGH
  - [ ] 03-deploy-staging.yml : health check post-déploiement avant SUCCESS
  - [ ] PR template inclut checklist ADR-010 EXPLAIN PLAN MongoDB
  - [ ] dependabot.yml configuré pour updates Maven hebdomadaires (BR-DEP-002)
  - [ ] CVE CRITICAL Dependabot → bloque déploiement production (BR-DEP-001)
  - [ ] Branches main + develop protégées (GitFlow — 1 reviewer + Quality Gate)
  - [ ] Convention de commits Conventional Commits documentée

SECRETS — ANNEX C :
  - [ ] Tous les secrets dans AWS Secrets Manager (BR-ROT-001)
  - [ ] Dates d'expiration configurées : OpenAI/Keycloak/Stripe 90j, MongoDB 180j (BR-ROT-002)
  - [ ] Alerte AWS si secret non rotaté > 90 jours configurée
  - [ ] Spring Cloud AWS configuré pour reload sans redéploiement (BR-ROT-004)
  - [ ] Journal de rotation des secrets créé (BR-ROT-003)

DOCKERFILE :
  - [ ] 3 stages : dependencies → build → runtime JRE Alpine
  - [ ] Base runtime : eclipse-temurin:21-jre-alpine (image minimale)
  - [ ] Utilisateur non-root : addgroup -S docai && adduser -S docai -G docai
  - [ ] USER docai avant ENTRYPOINT
  - [ ] JVM : -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
  - [ ] HEALTHCHECK : wget -qO- http://localhost:8080/actuator/health
  - [ ] Image finale < 300MB

KUBERNETES :
  - [ ] BR-K8S-001 ✅ : RollingUpdate maxUnavailable=0 maxSurge=1
        (0 pod indisponible pendant le déploiement)
  - [ ] BR-K8S-004 ✅ : minReplicas=2 maxReplicas=10 (toujours 2 en prod)
  - [ ] CPU target : 70% (HPA autoscaling)
  - [ ] Secrets via AWS Secrets Manager CSI Driver (JAMAIS dans manifestes YAML)
  - [ ] Health check post-déploiement obligatoire avant marquage SUCCESS

QUALITY GATES SONARCLOUD :
  - [ ] Coverage global ≥ 80%
  - [ ] Coverage docai-domain ≥ 90%
  - [ ] PIT Mutation Testing ≥ 85% sur docai-domain
  - [ ] 0 bug SonarCloud sur nouveau code
  - [ ] 0 vulnérabilité sur nouveau code
  - [ ] Duplication ≤ 3%

CHECKSTYLE :
  - [ ] Méthodes ≤ 20 lignes
  - [ ] Paramètres par méthode ≤ 4
  - [ ] Complexité cyclomatique ≤ 10
  - [ ] Classes ≤ 200 lignes
  - [ ] ./mvnw checkstyle:check → 0 violation

MONGODB — ADR-010 :
  - [ ] ADR-010 ✅ : Slow query log activé DEV (operations > 100ms loggées)
  - [ ] ADR-010 ✅ : Template PR contient checklist EXPLAIN PLAN
  - [ ] Aucun index ajouté sans EXPLAIN PLAN documenté dans la PR
  - [ ] Partial index utilisé si selectivité actif < 20%

FEATURE FLAGS :
  - [ ] FeatureFlagPort dans docai-domain/port/out/ (interface pure)
  - [ ] UnleashFeatureFlagAdapter avec fallback false si Unleash indisponible
  - [ ] billing.enabled = false (DEV + STAGING)
  - [ ] fraud.v2.enabled = false (DEV)
  - [ ] extraction.mistral.enabled = false (DEV)
  - [ ] dashboard.search.enabled = false (DEV)
  - [ ] notifications.inapp.enabled = true (DEV)
  - [ ] maintenance.mode = false

OBSERVABILITÉ :
  - [ ] Logs JSON structurés avec tenantId et traceId dans chaque log
  - [ ] PII masqués dans logs (IBAN, SIRET, email, nom)
  - [ ] Métriques Micrometer exposées sur /actuator/prometheus
  - [ ] Tracing OpenTelemetry → Grafana Tempo opérationnel en DEV
  - [ ] Dashboard Grafana de base configuré (CPU, mémoire, requêtes/s)
  - [ ] Alerte Grafana configurée : Circuit Breaker OPEN → notification

DEFINITION OF DONE :
  - [ ] Premier build 01-ci.yml vert sur develop
  - [ ] Image Docker publiée dans GHCR sans vulnérabilité CRITICAL
  - [ ] Déploiement staging automatique fonctionnel sur push develop
  - [ ] billing.enabled = false vérifié en DEV (Unleash)
  - [ ] Log structuré visible avec tenantId + traceId dans Grafana
  - [ ] /actuator/prometheus répond avec métriques Micrometer
```

---

## 🔴 speckit-tasks — Module 1.B

```
speckit-tasks

Découpe le Module 1.B en micro-tâches de 1 jour max — skill docai-cicd-pipeline.
Chaque tâche = 1 PR + 1 critère de done mesurable.
ADR-008 et ADR-010 à respecter dans chaque tâche concernée.

TÂCHE 1.B-01 — Checkstyle (0.5j)
  Action  : maven-checkstyle-plugin, règles méthodes/paramètres/complexité/classes
  PR      : ci: add Checkstyle rules methods 20 params 4 complexity 10
  Critère : ./mvnw checkstyle:check → 0 violation sur tout le projet

TÂCHE 1.B-02 — GitHub Actions 01-ci.yml — ADR-008 (1j)
  Action  : 5 jobs séparés (unit, integration, bdd, contract, sonarcloud)
            MAVEN_OPTS=-Xmx512m sur tous les jobs (ADR-008)
            TESTCONTAINERS_REUSE_ENABLE=true sur integration + bdd (ADR-008)
            PR template avec checklist EXPLAIN PLAN MongoDB (ADR-010)
  PR      : ci: add 01-ci.yml 5 jobs ADR-008 Xmx512m reuse + PR template ADR-010
  Critère : Premier build vert sur develop
            Violation ArchUnit dans docai-domain → CI rouge (test de régression)

TÂCHE 1.B-03 — Dockerfile multi-stage (0.5j)
  Action  : 3 stages (dependencies, build, runtime JRE 21 Alpine)
            Utilisateur non-root docai
            JVM UseContainerSupport + MaxRAMPercentage=75.0
  PR      : ci: add multi-stage Dockerfile JRE 21 Alpine user non-root
  Critère : docker build → image < 300MB
            docker inspect → utilisateur "docai" confirmé

TÂCHE 1.B-04 — 02-docker.yml + 03-deploy-staging.yml (0.5j)
  Action  : Build + Trivy CRITICAL exit-code 1 + Push GHCR
            Deploy staging auto sur push develop + health check post-deploy
  PR      : ci: add 02-docker Trivy CRITICAL blocking 03-deploy-staging auto
  Critère : Trivy bloque pipeline si CRITICAL détecté
            Image publiée dans GHCR après scan clean

TÂCHE 1.B-05 — Manifestes Kubernetes (0.5j)
  Action  : deployment.yaml (RollingUpdate maxUnavailable=0 — BR-K8S-001)
            service.yaml, hpa.yaml (min=2 max=10 CPU 70% — BR-K8S-004)
            Secrets AWS Secrets Manager CSI Driver (jamais YAML)
  PR      : ci: add Kubernetes manifests staging BR-K8S-001 BR-K8S-004
  Critère : kubectl apply → 0 erreur en staging
            Secrets non présents dans les manifestes YAML

TÂCHE 1.B-05b — GitFlow + Dependabot + Secrets (0.5j) — Annex C + GitFlow
  Action  : Protéger branches main + develop (1 reviewer + Quality Gate)
            dependabot.yml (Maven hebdomadaire — BR-DEP-002)
            Configurer AWS Secrets Manager (secrets + dates expiration — BR-ROT-001/002)
            Spring Cloud AWS reload sans redéploiement (BR-ROT-004)
            Journal de rotation des secrets (BR-ROT-003)
  PR      : ci: add GitFlow branch protection Dependabot Annex-C secrets rotation
  Critère : PR sans reviewer bloquée sur main et develop
            Dependabot PR créée automatiquement sur dépendance obsolète
            Alerte AWS si secret > 90j configurée

TÂCHE 1.B-06 — Feature Flags Unleash (0.5j)
  Action  : FeatureFlagPort (docai-domain/port/out/)
            UnleashFeatureFlagAdapter (fallback false si Unleash down)
            6 flags initiaux
  PR      : feat(flags): add FeatureFlagPort Unleash adapter 6 initial flags
  Critère : billing.enabled = false en DEV confirmé
            Unleash indisponible → fallback false (pas d'exception)

TÂCHE 1.B-07 — Observabilité (0.5j)
  Action  : Logs JSON structurés (tenantId + traceId dans chaque ligne)
            PII masqués (IBAN, SIRET, email)
            Métriques Micrometer → /actuator/prometheus
            Tracing OTEL → Grafana Tempo
  PR      : feat(observability): add structured logs PII masked metrics tracing
  Critère : Log visible dans Grafana avec tenantId
            /actuator/prometheus répond avec métriques
            Trace visible dans Grafana Tempo
```

---

## ⚫ speckit-analyse — Module 1.B

```
speckit-analyse

Analyse ce code par rapport aux règles des skills docai-cicd-pipeline
et docai-architecture-adr (ADR-008, ADR-010) :

Points à vérifier obligatoirement :

CI/CD — ADR-008 :
  1. Les jobs CI ont-ils MAVEN_OPTS=-Xmx512m ?
     → Obligatoire sur unit-tests, integration-tests, bdd-tests
  2. TESTCONTAINERS_REUSE_ENABLE=true est-il sur integration et bdd ?
     → Sinon OOM possible sur GitHub Runner 7GB
  3. Y a-t-il un seul job monolithique qui exécute tout ?
     → Violation ADR-008 — diviser en 3 jobs minimum

DOCKERFILE :
  4. L'image finale est-elle basée sur JRE (pas JDK) ?
     → eclipse-temurin:21-jre-alpine obligatoire pour < 300MB
  5. L'utilisateur est-il non-root ?
     → USER docai obligatoire avant ENTRYPOINT
  6. Les flags JVM sont-ils présents ?
     → -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0

KUBERNETES :
  7. RollingUpdate a-t-il maxUnavailable=0 ? (BR-K8S-001)
     → 0 pod indisponible pendant déploiement
  8. HPA a-t-il minReplicas=2 ? (BR-K8S-004)
     → Minimum 2 replicas en prod
  9. Des secrets sont-ils hardcodés dans les manifestes YAML ?
     → JAMAIS — utiliser AWS Secrets Manager CSI Driver

MONGODB — ADR-010 :
 10. Un index a-t-il été ajouté sans EXPLAIN PLAN documenté ?
     → Vérifier que la PR contient le résultat EXPLAIN PLAN

FEATURE FLAGS :
 11. FeatureFlagPort est-il dans docai-domain (pas dans un adapter) ?
     → Interface pure dans domain/port/out/
 12. L'adapter Unleash a-t-il un fallback false si Unleash est indisponible ?
     → Fail-safe obligatoire

OBSERVABILITÉ :
 13. Les logs contiennent-ils des données PII non masquées ?
     → IBAN, SIRET, email, nom doivent être masqués
 14. tenantId et traceId sont-ils présents dans chaque ligne de log ?
     → MDC.put("tenantId", ...) + MDC.put("traceId", ...) obligatoires

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 1.B

### Composant 1 — GitHub Actions 01-ci.yml (ADR-008)
```
speckit-implement

Implémente le workflow GitHub Actions 01-ci.yml — skill docai-cicd-pipeline.
ADR-008 obligatoire : 3 jobs séparés + MAVEN_OPTS=-Xmx512m + reuse TestContainers.

5 jobs séparés :

Job 1 — unit-tests :
  MAVEN_OPTS: -Xmx512m -Xms256m
  ./mvnw test -pl docai-domain,docai-application
  (inclut HexagonalArchitectureTest 12 règles ArchUnit)

Job 2 — integration-tests (needs: unit-tests) :
  MAVEN_OPTS: -Xmx512m
  TESTCONTAINERS_REUSE_ENABLE: true
  AWS_ACCESS_KEY_ID: test (LocalStack)
  AWS_SECRET_ACCESS_KEY: test
  ./mvnw verify -pl docai-adapter-out-mongodb,
                    docai-adapter-out-kafka,
                    docai-adapter-out-storage -P integration-tests

Job 3 — bdd-tests (needs: unit-tests) :
  MAVEN_OPTS: -Xmx512m
  TESTCONTAINERS_REUSE_ENABLE: true
  BILLING_ENABLED: "false"
  ./mvnw test -pl docai-bootstrap -Dtest=CucumberTestRunner

Job 4 — contract-tests (needs: unit-tests) :
  ./mvnw spring-cloud-contract:generateTests spring-cloud-contract:run

Job 5 — sonarcloud (needs: [unit-tests, integration-tests, bdd-tests]) :
  SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  ./mvnw verify sonar:sonar -Dsonar.projectKey=...

Déclencheurs : push [develop, main] + pull_request [develop, main] + tag v*.*.*

PR template : checklist ADR-010 EXPLAIN PLAN MongoDB obligatoire.
```

### Composant 2 — Dockerfile multi-stage
```
speckit-implement

Implémente le Dockerfile multi-stage DocAI — skill docai-cicd-pipeline.

3 stages obligatoires :

Stage 1 "dependencies" (eclipse-temurin:21-jdk-alpine) :
  COPY pom.xml + ./mvnw
  RUN ./mvnw dependency:go-offline -B --no-transfer-progress
  → Cache Maven pour accélérer les rebuilds

Stage 2 "build" (FROM dependencies) :
  COPY src ./src
  RUN ./mvnw clean package -DskipTests --no-transfer-progress
  RUN java -Djarmode=layertools -jar docai-bootstrap/target/*.jar extract
  → Spring Boot layers pour optimiser les rebuilds Docker

Stage 3 "runtime" (eclipse-temurin:21-jre-alpine) :
  RUN addgroup -S docai && adduser -S docai -G docai
  USER docai
  COPY --from=build /build/dependencies/ ./
  COPY --from=build /build/snapshot-dependencies/ ./
  COPY --from=build /build/spring-boot-loader/ ./
  COPY --from=build /build/application/ ./
  EXPOSE 8080
  HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
  ENTRYPOINT ["java",
    "-XX:+UseContainerSupport",
    "-XX:MaxRAMPercentage=75.0",
    "-Djava.security.egd=file:/dev/./urandom",
    "org.springframework.boot.loader.launch.JarLauncher"]

Objectif : image runtime < 300MB
```

### Composant 3 — Feature Flags Unleash
```
speckit-implement

Implémente le pattern Feature Flag Unleash — skill docai-feature-flag.

Interface dans docai-domain/port/out/ (Java pur — pas d'import Unleash) :
  public interface FeatureFlagPort {
    boolean isBillingEnabled();
    boolean isFraudV2Enabled();
    boolean isExtractionMistralEnabled();
    boolean isDashboardSearchEnabled();
    boolean isNotificationsInAppEnabled();
    boolean isMaintenanceModeEnabled();
  }

Adapter UnleashFeatureFlagAdapter dans docai-bootstrap/ :
  @Component, implements FeatureFlagPort
  Noms des flags Unleash (exacts) :
    billing.enabled
    fraud.v2.enabled
    extraction.mistral.enabled
    dashboard.search.enabled
    notifications.inapp.enabled
    maintenance.mode
  Fallback obligatoire : false si Unleash indisponible (fail-safe)
  try { unleash.isEnabled(flag) } catch (Exception e) { return false; }

Valeurs initiales DEV :
  billing.enabled = false
  fraud.v2.enabled = false
  extraction.mistral.enabled = false
  dashboard.search.enabled = false
  notifications.inapp.enabled = true
  maintenance.mode = false
```

---
---


---
---

# ═══════════════════════════════════════════════
# ÉTAPE 6 — STANDARDS & QUALITÉ (MASTER Partie 2 Module 2.B)
# ⚠️ PRÉREQUIS : Modules 1.A + 1.B terminés — domaine + CI/CD opérationnels
# ═══════════════════════════════════════════════

---

# MODULE 1.C — Standards & Qualité

> **Contenu :** Feature Flags stratégie, Templates Emails Amazon SES, Definition of Ready, Pull Request Template, Definition of Done CI/CD
> **Durée estimée :** 2 jours
> **Skills :** `docai-email-ses` · `docai-feature-flag` · `docai-cicd-pipeline` · `docai-architecture-adr` · `docai-annexes-standards`
> **ADR applicables :** ADR-003 (jitter si cache feature flags) · ADR-008 (DoD CI/CD) · ADR-010 (DoR EXPLAIN PLAN)
> **Annexes :** Annex B (MongoDB standards dans DoR) · Annex C (BR-EMAIL-002 PII masqués · BR-EMAIL-004 sandbox DEV)

---

## 🔵 speckit-specify — Module 1.C

```
speckit-specify

Module  : Module 1.C — Standards & Qualité
Partie  : Partie 1 — Setup & CI/CD (Semaine 1)
Skills  : docai-email-ses, docai-feature-flag, docai-cicd-pipeline,
          docai-architecture-adr, docai-annexes-standards

Objectif :
  - Stratégie Feature Flags Unleash (6 flags, déploiement progressif)
  - 19 templates emails Amazon SES + Thymeleaf (HTML + texte brut)
  - Definition of Ready (DoR) — 10 critères obligatoires avant chaque US
  - Pull Request Template — checklist code review obligatoire
  - Definition of Done CI/CD — critères de sortie mesurables

ADR applicables :
  ADR-003 : jitter ±10% si cache feature flags Valkey > 1h
  ADR-008 : DoD CI/CD inclut couverture domaine ≥ 90% + Xmx512m
  ADR-010 : DoR inclut EXPLAIN PLAN obligatoire sur toutes les requêtes MongoDB

Annexes applicables :
  Annex B  : DoR inclut standards MongoDB (snake_case, tenantId FIRST, @Indexed interdit)
  Annex C  : BR-EMAIL-002 (PII masqués dans logs) · BR-EMAIL-004 (sandbox DEV/STAGING)
  GitFlow  : PR Template s'appuie sur Conventional Commits + checklist ADR

Business Rules emails (BR-EMAIL) :
  BR-EMAIL-001 : Chaque template a une version HTML et texte brut
  BR-EMAIL-002 : Destinataire jamais loggué — [PII_MASKED] obligatoire
  BR-EMAIL-003 : Lien désinscription dans chaque email
  BR-EMAIL-004 : SES en mode sandbox en DEV et STAGING

Catalogue complet 19 templates (HTML + texte brut chacun) :

  PARTIE 1 — Onboarding (5 templates — implémenter maintenant) :
    welcome                  → Bienvenue après inscription
    email-verification       → Vérification adresse email
    invitation               → Invitation d'un membre d'équipe
    password-reset           → Réinitialisation mot de passe
    account-revoked          → Révocation accès utilisateur

  PARTIE 3 — Sécurité Module 0 (2 templates) :
    password-changed         → Confirmation changement MDP (Module 0.4)
    support-access-request   → Agent support demande accès impersonation (Module 0)

  PARTIE 3 — RGPD Module 0.5 (1 template) :
    data-deletion-confirmed  → Confirmation effacement données RGPD

  PARTIE 5 — Billing Module 7 (11 templates) :
    trial-ending-7days       → J-7 avant fin période FREE
    trial-ending-3days       → J-3 avant fin période FREE
    trial-expired            → Expiration période FREE
    subscription-activated   → Paiement Stripe réussi — abonnement actif
    invoice                  → Facture mensuelle (1er du mois)
    payment-failed           → Échec paiement Stripe
    subscription-canceled    → Résiliation abonnement
    downgrade-confirmed      → Confirmation downgrade plan (ADR-009)
    quota-warning-80         → 80% du quota mensuel atteint
    quota-warning-95         → 95% du quota mensuel atteint
    quota-exceeded           → 100% quota — documents bloqués

  Règle : créer uniquement les 5 templates Partie 1 maintenant.
           Les 14 restants → dans leur SpecKit de Partie respective.
```

---

## 🟢 speckit-clarify — Module 1.C

```
speckit-clarify

Clarifie les points suivants du Module 1.C
— skills docai-email-ses + docai-feature-flag :

1. Les 19 templates emails sont-ils tous nécessaires dès la Partie 1
   ou uniquement les templates liés à l'onboarding
   (welcome, email-verification, invitation, password-reset) ?

2. Amazon SES : domaine docai.fr est-il déjà vérifié dans AWS ?
   → Si non, la vérification SES peut bloquer l'envoi en DEV

3. Feature Flags Unleash : self-hosted dans docker-compose
   ou SaaS cloud (app.unleash.io) ?
   → Impact sur la configuration locale DEV

4. Le Pull Request Template est-il identique pour tous les modules
   ou personnalisé par Partie ?

5. La DoR inclut-elle la vérification des ADR applicables
   comme critère bloquant (critère #4 de la DoR) ?
   → ADR-001 si quota, ADR-003 si cache, ADR-007 si S3

6. Annex C — BR-EMAIL-004 : en DEV, les emails sont-ils
   catchés localement (MailHog) ou ignorés (sandbox=true) ?
```

---

## 🟡 speckit-plan — Module 1.C

```
speckit-plan

Génère le plan d'implémentation du Module 1.C
— skills docai-email-ses + docai-feature-flag + docai-cicd-pipeline.
Respecte ADR-003, ADR-008, ADR-010 + Annex B + Annex C.

Ordre obligatoire :

ÉTAPE 1 — Feature Flags Unleash — stratégie complète (0.5j)
  FeatureFlagPort dans docai-domain/port/out/ (interface pure)
  UnleashFeatureFlagAdapter avec fallback false (fail-safe)
  6 flags initiaux avec valeurs DEV
  ADR-003 : si cache Valkey sur les flags → jitter ±10%
  Vérifier : billing.enabled = false en DEV confirmé

ÉTAPE 2 — Templates Emails Amazon SES (1j)
  EmailNotificationPort dans docai-domain/port/out/
  AmazonSesEmailAdapter dans docai-adapter-out-external/
  Structure obligatoire : email-templates/fr/ (HTML + texte brut)
  Layout commun base.html (header logo, footer légal, lien désinscription)
  Annex C : BR-EMAIL-002 — [PII_MASKED] dans tous les logs email
  Annex C : BR-EMAIL-004 — sandbox: true en DEV et STAGING
  Priorité templates Partie 1 (onboarding) :
    welcome, email-verification, invitation, password-reset, account-revoked
  Vérifier : template welcome rendu correctement

ÉTAPE 3 — Definition of Ready (DoR) — 10 critères (0.25j)
  Document wiki/confluence avec les 10 critères obligatoires
  Annex B : critère DoR #N — EXPLAIN PLAN MongoDB documenté (ADR-010)
  Annex B : critère DoR #N — conventions nommage respectées (snake_case)
  ADR applicable identifié dans chaque US avant démarrage
  Vérifier : DoR intégrée dans le wiki équipe + checklist sprint planning

ÉTAPE 4 — Pull Request Template (0.25j)
  .github/pull_request_template.md avec toutes les sections
  Checklist architecture (ArchUnit 12 règles)
  Checklist ADR (vérification ADR applicable)
  Checklist sécurité (PII masqués, tenant isolation)
  Checklist performance (EXPLAIN PLAN, pas de N+1)
  Checklist tests (couverture domaine ≥ 90%)
  Vérifier : PR créée → template affiché automatiquement

ÉTAPE 5 — Definition of Done CI/CD (0.25j)
  DoD globale : tous les Quality Gates CI/CD verts
  ADR-008 : MAVEN_OPTS=-Xmx512m sur tous les jobs CI
  DoD domaine : couverture ≥ 90%, PIT ≥ 85%, 0 violation ArchUnit
  DoD email : BR-EMAIL-001 à 004 respectées
  Monitoring SES : bounce rate < 5%, complaint rate < 0.1%
  Vérifier : checklist DoD intégrée dans sprint review
```

---

## 🟠 speckit-checklist — Module 1.C

```
speckit-checklist

Génère la checklist complète du Module 1.C
— skills docai-email-ses + docai-feature-flag + docai-cicd-pipeline.
ADR vérifiés : ADR-003 · ADR-008 · ADR-010
Annexes vérifiées : Annex B (MongoDB DoR) · Annex C (BR-EMAIL)

FEATURE FLAGS :
  - [ ] FeatureFlagPort dans docai-domain/port/out/ (interface pure — pas d'import Unleash)
  - [ ] UnleashFeatureFlagAdapter avec fallback false si Unleash indisponible (fail-safe)
  - [ ] ADR-003 ✅ : si cache Valkey sur flags → JitterTtl.withJitter() obligatoire
  - [ ] 6 flags définis avec valeurs DEV :
        billing.enabled = false
        fraud.v2.enabled = false
        extraction.mistral.enabled = false
        dashboard.search.enabled = false
        notifications.inapp.enabled = true
        maintenance.mode = false
  - [ ] Feature Flag consulté AVANT chaque fonctionnalité gated (pas de hardcode)

TEMPLATES EMAILS AMAZON SES :
  - [ ] EmailNotificationPort dans docai-domain/port/out/
  - [ ] AmazonSesEmailAdapter dans docai-adapter-out-external/
  - [ ] BR-EMAIL-001 ✅ : chaque template a version HTML + texte brut fallback
  - [ ] BR-EMAIL-002 ✅ : destinataire jamais loggué — [PII_MASKED] dans tous les logs
  - [ ] BR-EMAIL-003 ✅ : lien désinscription présent dans chaque template HTML
  - [ ] BR-EMAIL-004 ✅ : sandbox: true en DEV et STAGING (Annex C)
  - [ ] Layout commun base.html (header, footer légal, variables communes)
  - [ ] Variables dynamiques via Map<String, Object> — jamais hardcodées
  - [ ] Envoi best-effort — SesException catchée, pas propagée
  - [ ] Monitoring SES configuré : bounce rate < 5%, complaint rate < 0.1%
  - [ ] Templates Partie 1 créés (priorité onboarding) :
        welcome, email-verification, invitation,
        password-reset, account-revoked
  - [ ] Les 14 templates restants documentés dans le catalogue
        (seront implémentés dans leurs Parties respectives)
  - [ ] Structure dossier email-templates/fr/ créée pour tous les templates futurs

DEFINITION OF READY (DoR) — 10 CRITÈRES :
  - [ ] US estimée en story points
  - [ ] Critères d'acceptance écrits en BDD (Given/When/Then)
  - [ ] Scénarios Gherkin rédigés et validés PO
  - [ ] ADR applicable identifié (ADR-001 si quota, ADR-003 si cache, ADR-007 si S3)
  - [ ] Dépendances inter-modules identifiées + interfaces stables
  - [ ] Accès services externes disponibles (clés INSEE, Stripe test, etc.)
  - [ ] Annex B ✅ : EXPLAIN PLAN MongoDB documenté si nouvelle requête (ADR-010)
  - [ ] Annex B ✅ : convention nommage collections vérifiée (snake_case pluriel)
  - [ ] Maquettes ou contrats API validés si endpoint nouveau
  - [ ] US tient dans 1 sprint (sinon découper)

PULL REQUEST TEMPLATE :
  - [ ] .github/pull_request_template.md créé
  - [ ] Section Architecture : 12 règles ArchUnit vérifiées
  - [ ] Section ADR : ADR applicable respecté + lien vers la règle
  - [ ] Section Sécurité : PII masqués, tenant isolation vérifiée
  - [ ] Section Performance : EXPLAIN PLAN MongoDB, pas de N+1 (ADR-010)
  - [ ] Section Tests : couverture domaine ≥ 90%, PIT ≥ 85%
  - [ ] Section Email (si applicable) : BR-EMAIL-001 à 004 respectées
  - [ ] Format Conventional Commits respecté dans les commits de la PR

DEFINITION OF DONE CI/CD :
  - [ ] ADR-008 ✅ : tous les jobs CI avec MAVEN_OPTS=-Xmx512m
  - [ ] ./mvnw checkstyle:check → 0 violation
  - [ ] ./mvnw test -Dtest=HexagonalArchitectureTest → 12 règles vertes
  - [ ] Coverage docai-domain ≥ 90% (JaCoCo)
  - [ ] PIT Mutation Testing ≥ 85% sur docai-domain
  - [ ] 0 bug SonarCloud sur nouveau code
  - [ ] 0 vulnérabilité SonarCloud sur nouveau code
  - [ ] Trivy : 0 vulnérabilité CRITICAL dans l'image Docker
  - [ ] Tous les scénarios BDD Cucumber passent
  - [ ] Template PR affiché automatiquement sur nouvelle PR
  - [ ] DoR vérifiée avant démarrage de chaque US

DEFINITION OF DONE :
  - [ ] Feature Flags opérationnels : billing.enabled = false confirmé en DEV
  - [ ] Template email welcome rendu correctement (HTML + texte brut)
  - [ ] SES en sandbox DEV : pas d'email réel envoyé (BR-EMAIL-004)
  - [ ] DoR documentée dans le wiki équipe
  - [ ] PR Template affiché automatiquement sur GitHub
  - [ ] DoD CI/CD validée sur le premier build vert
```

---

## 🔴 speckit-tasks — Module 1.C

```
speckit-tasks

Découpe le Module 1.C en micro-tâches de 1 jour max
— skills docai-email-ses + docai-feature-flag.
Chaque tâche = 1 PR + 1 critère de done mesurable.

TÂCHE 1.C-01 — Feature Flags stratégie complète (0.5j)
  Action  : FeatureFlagPort (domaine) + UnleashFeatureFlagAdapter (adapter)
            Fallback false si Unleash indisponible
            6 flags avec valeurs DEV
            ADR-003 : jitter si cache Valkey sur flags
  PR      : feat(flags): add FeatureFlagPort strategy 6 flags ADR-003 jitter
  Critère : billing.enabled = false en DEV confirmé
            Unleash indisponible → fallback false (aucune exception)

TÂCHE 1.C-02 — Templates Emails Amazon SES (1j)
  Action  : EmailNotificationPort + AmazonSesEmailAdapter
            Layout base.html commun (header, footer, lien désinscription)
            5 templates prioritaires : welcome, email-verification,
            invitation, password-reset, account-revoked
            BR-EMAIL-001 (HTML+texte) · BR-EMAIL-002 (PII masked)
            BR-EMAIL-003 (désinscription) · BR-EMAIL-004 (sandbox DEV)
  PR      : feat(email): add SES adapter 5 templates BR-EMAIL-001-004 sandbox
  Critère : Template welcome rendu correctement HTML + texte brut
            Log email : [PII_MASKED] visible (jamais l'email réel)
            sandbox: true en DEV → aucun email réel envoyé

TÂCHE 1.C-03 — DoR + PR Template + DoD (0.5j)
  Action  : DoR 10 critères documentée dans wiki
            .github/pull_request_template.md (Architecture, ADR,
            Sécurité, Performance, Tests, Email)
            DoD CI/CD documentée (Quality Gates complets)
            Annex B dans DoR (EXPLAIN PLAN, snake_case, tenantId FIRST)
  PR      : docs: add DoR 10 criteria PR template DoD CI/CD Annex-B standards
  Critère : PR créée → template affiché automatiquement sur GitHub
            DoR intégrée dans wiki équipe
            Checklist sprint planning mise à jour avec DoR
```

---

## ⚫ speckit-analyse — Module 1.C

```
speckit-analyse

Analyse ce code par rapport aux règles des skills docai-email-ses
et docai-feature-flag (ADR-003, Annex C BR-EMAIL) :

FEATURE FLAGS :
  1. FeatureFlagPort est-il dans docai-domain/port/out/ ?
     → Interface pure — zéro import Unleash dans le domaine
  2. L'adapter Unleash a-t-il un fallback false si indisponible ?
     → try { unleash.isEnabled(flag) } catch (e) { return false; }
  3. ADR-003 : si cache Valkey sur les flags, JitterTtl.withJitter() utilisé ?
     → Jamais de TTL fixe > 1h sans jitter

EMAILS AMAZON SES :
  4. BR-EMAIL-001 : chaque template a-t-il une version HTML ET texte brut ?
     → Fallback texte brut obligatoire pour clients sans HTML
  5. BR-EMAIL-002 : le destinataire est-il loggué quelque part ?
     → INTERDIT — uniquement [PII_MASKED] dans les logs
  6. BR-EMAIL-003 : le lien désinscription est-il dans le template HTML ?
     → Obligatoire dans chaque email
  7. BR-EMAIL-004 : sandbox: true est-il configuré en DEV et STAGING ?
     → application-dev.yml + application-staging.yml
  8. L'exception SesException est-elle catchée sans être propagée ?
     → Email = best-effort, pas bloquant pour le pipeline

DEFINITION OF READY :
  9. La DoR inclut-elle la vérification de l'ADR applicable ?
     → Critère #4 : ADR identifié avant démarrage US
 10. La DoR inclut-elle l'EXPLAIN PLAN MongoDB ? (Annex B + ADR-010)
     → Critère bloquant si nouvelle requête MongoDB dans la US

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 1.C

### Composant 1 — EmailNotificationPort + AmazonSesEmailAdapter
```
speckit-implement

Implémente l'adapter Email Amazon SES — skill docai-email-ses.

Interface EmailNotificationPort dans docai-domain/port/out/ :
  void send(EmailMessage message)
  record EmailMessage(String to, String template,
                      Map<String, Object> variables, String tenantId)

Adapter AmazonSesEmailAdapter dans docai-adapter-out-external/ :
  @Component implements EmailNotificationPort
  Injection : SesClient + TemplateEngine (Thymeleaf)
  FROM : noreply@docai.fr
  BR-EMAIL-002 : log.info("Email sent template={} tenantId={} recipient=[PII_MASKED]")
  BR-EMAIL-004 : si docai.email.sandbox=true → log uniquement, pas d'envoi SES
  Envoi best-effort : SesException catchée, pas propagée

Structure templates obligatoire (tous les 19 dossiers créés maintenant) :
  src/main/resources/email-templates/
  ├── fr/
  │   ├── layout/
  │   │   ├── base.html          ← header logo, footer légal, lien désinscription
  │   │   └── base-text.txt
  │   │
  │   ├── [PARTIE 1 — implémenter maintenant]
  │   │   ├── welcome.html + welcome-text.txt
  │   │   ├── email-verification.html + email-verification-text.txt
  │   │   ├── invitation.html + invitation-text.txt
  │   │   ├── password-reset.html + password-reset-text.txt
  │   │   └── account-revoked.html + account-revoked-text.txt
  │   │
  │   ├── [PARTIE 3 Module 0 — implémenter en Partie 3]
  │   │   ├── password-changed.html + password-changed-text.txt
  │   │   └── support-access-request.html + support-access-request-text.txt
  │   │
  │   ├── [PARTIE 3 Module 0.5 RGPD — implémenter en Partie 3]
  │   │   └── data-deletion-confirmed.html + data-deletion-confirmed-text.txt
  │   │
  │   └── [PARTIE 5 Module 7 Billing — implémenter en Partie 5]
  │       ├── trial-ending-7days.html + trial-ending-7days-text.txt
  │       ├── trial-ending-3days.html + trial-ending-3days-text.txt
  │       ├── trial-expired.html + trial-expired-text.txt
  │       ├── subscription-activated.html + subscription-activated-text.txt
  │       ├── invoice.html + invoice-text.txt
  │       ├── payment-failed.html + payment-failed-text.txt
  │       ├── subscription-canceled.html + subscription-canceled-text.txt
  │       ├── downgrade-confirmed.html + downgrade-confirmed-text.txt
  │       ├── quota-warning-80.html + quota-warning-80-text.txt
  │       ├── quota-warning-95.html + quota-warning-95-text.txt
  │       └── quota-exceeded.html + quota-exceeded-text.txt

Configuration application-dev.yml :
  docai.email.sandbox: true   ← BR-EMAIL-004 sandbox DEV

Monitoring SES à configurer :
  Bounce rate < 5% → alerte CloudWatch
  Complaint rate < 0.1% → alerte CloudWatch
```

### Composant 2 — Pull Request Template
```
speckit-implement

Crée le fichier .github/pull_request_template.md DocAI
— skills docai-cicd-pipeline + docai-architecture-adr.

Sections obligatoires :

## Description
  Résumé clair de la modification et de la valeur métier.

## Type de changement
  [ ] feat  [ ] fix  [ ] refactor  [ ] test  [ ] ci  [ ] docs

## Checklist Architecture (ArchUnit)
  - [ ] docai-domain sans import Spring/MongoDB/Kafka/AWS
  - [ ] Adapters n'appellent pas d'autres adapters directement
  - [ ] Controllers dans adapter-in-rest uniquement
  - [ ] Listeners Kafka dans adapter-in-kafka uniquement
  - [ ] Pas de @Transactional dans le domaine

## Checklist ADR
  - [ ] ADR applicable identifié et respecté
  - [ ] ADR-001 : quota Lua atomique (si quota modifié)
  - [ ] ADR-002 : clé partition = documentId (si Kafka modifié)
  - [ ] ADR-003 : TTL jitter ±10% (si cache Valkey modifié)
  - [ ] ADR-010 : EXPLAIN PLAN MongoDB documenté (si requête ajoutée)

## Checklist Sécurité
  - [ ] PII masqués dans les logs ([PII_MASKED])
  - [ ] tenantId présent dans toutes les requêtes MongoDB
  - [ ] Endpoints protégés avec @PreAuthorize et rôle correct
  - [ ] Pas de secret dans le code

## Checklist Tests
  - [ ] Couverture domaine ≥ 90% maintenue
  - [ ] PIT Mutation ≥ 85% sur docai-domain
  - [ ] Scénarios BDD mis à jour si comportement métier modifié
  - [ ] Tests nommés : should_X_when_Y()

## Checklist Email (si applicable)
  - [ ] BR-EMAIL-001 : template HTML + texte brut
  - [ ] BR-EMAIL-002 : [PII_MASKED] dans les logs
  - [ ] BR-EMAIL-003 : lien désinscription présent
  - [ ] BR-EMAIL-004 : sandbox testé en DEV

## Definition of Done
  - [ ] ./mvnw checkstyle:check → 0 violation
  - [ ] CI vert (unit + integration + bdd + sonarcloud)
  - [ ] 0 vulnérabilité CRITICAL Trivy
  - [ ] Documentation OpenAPI mise à jour si endpoint nouveau
```

### Composant 3 — Definition of Ready (DoR) complète
```
speckit-implement

Crée la Definition of Ready DocAI — skill docai-architecture-adr.
Annex B MongoDB + ADR-010 intégrés comme critères bloquants.

10 critères obligatoires avant démarrage de toute US :

| # | Critère | Responsable |
|---|---------|-------------|
| 1 | US estimée en story points | Équipe dev |
| 2 | Critères acceptance BDD (Given/When/Then) écrits | PO / Dev |
| 3 | Scénarios Gherkin rédigés et validés PO | Dev |
| 4 | ADR applicable identifié (ADR-001/002/003/007/010...) | Dev |
| 5 | Dépendances inter-modules identifiées | Dev |
| 6 | Accès services externes disponibles | Tech Lead |
| 7 | EXPLAIN PLAN MongoDB documenté si nouvelle requête (ADR-010 + Annex B) | Dev |
| 8 | Convention nommage collections vérifiée (snake_case pluriel — Annex B) | Dev |
| 9 | Maquettes ou contrats API validés si endpoint nouveau | Dev / PO |
|10 | US tient dans 1 sprint (sinon découper) | Tech Lead |

US bloquée si 1 critère non rempli → reste dans le backlog.
DoR vérifiée en sprint planning pour chaque US prévue au sprint.
```

---


---
---

## 📋 Vérification finale — Partie 1 complète

Avant de passer à la **Partie 2 — Commons**, valider tous les points :

```bash
# Structure Maven
./mvnw clean compile                                  # → BUILD SUCCESS
./mvnw test -Dtest=HexagonalArchitectureTest          # → 12 règles vertes
./mvnw checkstyle:check                               # → 0 violation
./mvnw org.pitest:pitest-maven:mutationCoverage \
  -pl docai-domain                                    # → ≥ 85%

# Infrastructure Docker
docker compose ps                                     # → tous healthy
docker exec docai-mongodb mongosh \
  --eval "rs.status().ok"                             # → 1
# Kafka UI : http://localhost:8090 → 8 topics exacts visibles
# Keycloak : http://localhost:8180 → realm docai + login OK

# ADR Vérifications
# ADR-002 : topics Kafka avec documentId partition key (vérifier kafka-init)
# ADR-006 : cache JWKS TTL 1h dans application.yml (vérifier config)
# ADR-008 : 01-ci.yml avec MAVEN_OPTS=-Xmx512m sur tous jobs (vérifier)
# ADR-010 : slow query log MongoDB actif en DEV (vérifier logs)

# Application
./mvnw spring-boot:run -pl docai-bootstrap \
  -Dspring-boot.run.profiles=dev
curl http://localhost:8080/actuator/health            # → {"status":"UP"}
curl http://localhost:8080/v1/documents               # → HTTP 401 sans JWT
curl http://localhost:8080/actuator/prometheus        # → métriques Micrometer

# CI/CD
# → Premier build 01-ci.yml vert sur GitHub Actions
# → Image Docker publiée dans GHCR (0 vulnérabilité CRITICAL Trivy)
# → billing.enabled = false confirmé en DEV (Unleash)
# → Logs structurés visibles avec tenantId + traceId dans Grafana
```

### ADR Récapitulatif — Partie 1

| ADR | Appliqué dans | Statut |
|-----|---------------|--------|
| ADR-001 Lua atomique | Préparer commons-quota (Partie 2) | ⚠️ À venir |
| ADR-002 Kafka documentId | kafka-init docker-compose.yml | ✅ Fait |
| ADR-003 TTL jitter | Préparer commons-kafka (Partie 2) | ⚠️ À venir |
| ADR-004 OCR → S3 | Préparer S3 adapter (Partie 4) | ⚠️ À venir |
| ADR-005 KMS PII | Module 0.5 RGPD (Partie 3) | ➡️ Plus tard |
| ADR-006 JWKS cache 1h | application.yml Keycloak config | ✅ Fait |
| ADR-007 AbortMultipart | S3 adapter (Partie 4 Module 1.1) | ⚠️ À venir |
| ADR-008 CI Xmx512m | 01-ci.yml 5 jobs séparés | ✅ Fait |
| ADR-009 Downgrade | Module 7 Billing (Partie 5) | ➡️ Plus tard |
| ADR-010 EXPLAIN PLAN | Slow query DEV + PR template | ✅ Fait |
| ADR-011 lastSyncedAt | Module 5 Dashboard (Partie 5) | ➡️ Plus tard |

---

*DocAI SpecKit — SPECKIT_ARCHI_SETUP_CICD.md — Version finale fusionnée*
*MASTER Partie 1 (Architecture) + MASTER Partie 2 (Setup & CI/CD)*
*6 Modules : A Architecture · B Stack · C Persistance · 1.A Setup · 1.B CI/CD · 1.C Standards*
*⚠️ Ordre implémentation : 1.A → A → B → C → 1.B → 1.C*
*10 Skills · 11 ADR · Annex B MongoDB · Annex C Secrets*
*BR-PAG-001→008 · BR-MIG-001→007 · BR-EMAIL-001→004 · BR-VIS-003*
*Corrections : Amazon S3 direct · Tempo port 4317 · Aucune régression*
