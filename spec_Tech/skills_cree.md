# DocAI — Référence Complète des Skills par Module
## 51 Skills · 5 Parties · 33 Semaines · Production Ready

> **Stack :** Java 21 · Spring Boot 4.0.x · Kafka · Keycloak 26 · MongoDB 7 · Valkey 8 · Amazon S3
> **Règle fondamentale :** Chaque partie bloque la suivante — ordre strict et non négociable.

---

## Vue d'ensemble

```
┌──────────┬──────────────────────────────────────┬──────────┬────────────┐
│  Partie  │  Contenu                             │ Durée    │ Semaines   │
├──────────┼──────────────────────────────────────┼──────────┼────────────┤
│    1     │  Setup + CI/CD                       │ 1 sem.   │     1      │
│    2     │  Commons (7 modules partagés)        │ 2 sem.   │   2–3      │
│    3     │  Fondations (Sécurité + RGPD)        │ 4 sem.   │   4–7      │
│    4     │  Pipeline (Modules 1 à 4)            │ 14 sem.  │   8–21     │
│    5     │  Produit (Dashboard + API + Billing) │ 9 sem.   │  22–33     │
├──────────┴──────────────────────────────────────┴──────────┴────────────┤
│                    TOTAL : 33 semaines (~8 mois)                        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Jalons clés

```
Semaine 1  → CI/CD vert + tous les services démarrent
Semaine 3  → 7 commons testés à ≥ 90% couverture
Semaine 7  → Sécurité + RGPD validés en staging
Semaine 11 → Document uploadé et classifié automatiquement
Semaine 17 → Document extrait avec JSON structuré
Semaine 21 → Fraude détectée et rejetée automatiquement
Semaine 24 → Pipeline résilient (zéro perte en cas de panne)
Semaine 27 → Dashboard temps réel opérationnel
Semaine 30 → API publique intégrée par un client externe
Semaine 33 → SaaS commercialisable, premier client facturé
```

---

---

# PARTIE 1 — Setup & CI/CD | Semaine 1

> **Objectif :** Projet fonctionnel, infrastructure locale démarrée, CI/CD vert.
> **Règle :** Si le CI/CD n'est pas vert fin semaine 1, on ne passe pas à la Partie 2.

## Modules

### Module 1.A — Structure Maven + Docker Compose
| Tâche | Contenu | Critère |
|-------|---------|---------|
| Structure Maven | POM parent + 11 modules (`docai-domain`, `docai-application`, 8 adapters, `docai-bootstrap`) | `./mvnw clean compile` passe |
| Docker Compose | 11 services : MongoDB 7 (RS), Kafka 3.7 (KRaft), Kafka UI, Apicurio, Valkey 8, Keycloak 26, Prometheus, Grafana, Tempo, OTEL Collector | `docker compose ps` → tous healthy |
| Amazon S3 + .env | Bucket S3 dev + `.env.example` documenté | Upload test réussi |
| Keycloak Realm | `realm-docai.json` : 3 clients, 5 rôles, 5 utilisateurs de test, Protocol Mapper `tenant_id` | Login `admin@acme-corp.test` → JWT avec `tenant_id` |
| Seeding DEV | 3 tenants + 10 utilisateurs + 6 documents PDF exemples | `curl /actuator/health` → UP |

### Module 1.B — Standards & CI/CD
| Tâche | Contenu | Critère |
|-------|---------|---------|
| Checkstyle | Méthodes ≤ 20 lignes, paramètres ≤ 4, complexité ≤ 10 | `./mvnw checkstyle:check` passe |
| ArchUnit | 12 règles ArchUnit dans `HexagonalArchitectureTest` | Toutes les règles passent |
| SonarCloud | Quality Gate : couverture ≥ 80%, 0 bug, 0 vuln, duplication ≤ 3% | Analyse SonarCloud réussie |
| Feature Flags | Unleash : 6 flags (`billing.enabled`, `fraud.v2.enabled`, `extraction.mistral.enabled`, `dashboard.search.enabled`, `notifications.inapp.enabled`, `maintenance.mode`) | `billing.enabled = false` en DEV vérifié |
| GitHub Actions | `01-ci.yml` (3 jobs ADR-008), `02-docker.yml`, `03-deploy-staging.yml` | Premier build GitHub Actions vert |

## Skills Partie 1
| Skill | Usage |
|-------|-------|
| `docai-setup-projet` | Structure Maven, Docker Compose, Keycloak, .env, commandes démarrage |
| `docai-cicd-pipeline` | GitHub Actions, Dockerfile multi-stage, K8s, Quality Gates |
| `docai-seeding` | SeedingService DEV, 3 tenants, 10 utilisateurs, idempotence |
| `docai-architecture-adr` | 12 règles ArchUnit, 11 ADR, conventions nommage |
| `docai-feature-flag` | Unleash, 6 flags, pattern FeatureFlagPort |
| `docai-observability` | Métriques Micrometer, logs JSON structurés, OpenTelemetry |

## ✅ Critère de passage → Partie 2
- [ ] CI/CD vert sur `develop`
- [ ] Tous les services `docker compose ps` → healthy
- [ ] Login `admin@acme-corp.test` → JWT valide avec `tenant_id`
- [ ] 8 topics Kafka visibles dans Kafka UI
- [ ] `./mvnw test -pl docai-domain` passe (ArchUnit)

---

---

# PARTIE 2 — Commons | Semaines 2–3

> **Objectif :** 7 composants réutilisables testés avant tout code métier.
> **Règle :** Commons implémentés ET testés (≥ 90%) avant de démarrer le Module 0.

## Modules — 7 commons dans l'ordre

| # | Commons | Contenu | Durée |
|---|---------|---------|-------|
| 1 | `commons-multitenancy` | `TenantContext`, `TenantJwtFilter`, `MongoTenantFilter`, `ValkeyTokenBlacklistAdapter` | 2j |
| 2 | `commons-api` | `ApiResponse<T>`, `ProblemDetail` RFC 7807, `GlobalExceptionHandler`, `IdempotencyFilter`, pagination | 1j |
| 3 | `commons-audit` | `AuditEvent`, `AuditPort`, annotation `@Audited` AOP | 1j |
| 4 | `commons-outbox` | `OutboxMessage`, `OutboxRepository`, `OutboxRelay` (scheduler 500ms) | 2j |
| 5 | `commons-quota` | `QuotaPort`, script Lua atomique Valkey (ADR-001), `@QuotaProtected` AOP | 1j |
| 6 | `commons-kafka` | `ResilientKafkaConsumer`, `OutboxKafkaProducer`, `JitterTtl` (ADR-003) | 2j |
| 7 | `commons-testing` | `AbstractIntegrationTest` (reuse TC — ADR-008), `TestBuilders`, WireMock stubs | 1j |

## ADR applicables aux commons
- **ADR-001** → `commons-quota` (script Lua atomique — jamais check + increment séparés)
- **ADR-002** → `commons-kafka` (clé partition = `documentId` pour le pipeline)
- **ADR-003** → `commons-kafka` (`JitterTtl.withJitter()` — jamais de TTL fixe > 1h)
- **ADR-006** → `commons-multitenancy` (cache JWKS Keycloak 1h)
- **ADR-008** → `commons-testing` (reuse TestContainers — `-Xmx512m` en CI)

## Skills Partie 2
| Skill | Usage |
|-------|-------|
| `docai-commons-implement` | Signatures Java exactes des 7 commons, interfaces, AbstractIntegrationTest |
| `docai-architecture-adr` | Référence ADR-001/002/003/006/008 |
| `docai-stack-technique` | Kafka topologie, Valkey stratégies, Resilience4j |
| `docai-persistance-standards` | MongoDB conventions, pagination BR-PAG-001 à 008 |
| `docai-adapter-kafka` | Pattern ResilientKafkaConsumer, consumer groups |
| `docai-adapter-valkey` | Cache-Aside, Write-Through, TTL jitter |
| `docai-test-integration` | AbstractIntegrationTest, TestContainers, withReuse=true |

## ✅ Critère de passage → Partie 3
- [ ] Couverture ≥ 90% sur chaque commons
- [ ] ArchUnit passe (domaine sans imports Spring/Mongo/Kafka)
- [ ] PIT Mutation Testing ≥ 85% sur les commons domaine
- [ ] `AbstractIntegrationTest` démarre MongoDB + Kafka + Valkey + LocalStack en mode reuse

---

---

# PARTIE 3 — Fondations Métier | Semaines 4–7

> **Objectif :** Sécurité, Auth, RGPD, Billing 100% opérationnels avant tout endpoint métier.
> **Règle :** Module 0 validé en staging avant de démarrer le Module 1.

## Module 0 — Sécurité & Multi-Tenancy | Semaines 4–5

| Phase | Contenu | Durée | Critère |
|-------|---------|-------|---------|
| Sécurité core | `TenantJwtFilter`, `GlobalExceptionHandler` RFC 7807, rate limiting Bucket4j, impersonation support | 3j | `GET /v1/documents` sans JWT → HTTP 401 |
| Phase 0.1 — Inscription | Signup automatique, `tenant_id` généré, `TENANT_ADMIN` attribué, emails bienvenue SES | 3j | Inscription → connexion en < 5 min |
| Phase 0.2 — Login | Login/Logout/Refresh Token, mot de passe oublié, blocage 5 tentatives, JWT blacklist Valkey | 3j | Logout → JWT invalide immédiatement |
| Phase 0.3 — Équipe | Invitation collègues, activation compte, révocation accès, `AuditEntry` immuable | 2j | Invitation → activation → connexion |
| Phase 0.4 — Profil | Changement MDP, changement email (vérification), historique connexions, 2FA TOTP | 2j | 2FA obligatoire pour plan Enterprise |

## Module 0.4 — Billing Fondations | Semaines 5–6

| Tâche | Contenu | Critère |
|-------|---------|---------|
| Feature Flag | `billing.enabled = false` par défaut | Désactiver → aucune restriction |
| Plans | FREE 50 docs, Starter 49€/500, Pro 199€/10K, Enterprise sur devis | `GET /v1/billing/plans` retourne 4 plans |
| Quota Lua | Script atomique Valkey (ADR-001), alertes 80% et 95% | 1000 uploads simultanés → quota exact |

## Module 0.5 — RGPD & Privacy | Semaines 6–7

| Tâche | Contenu | Durée | Critère |
|-------|---------|-------|---------|
| Rétention | Job quotidien suppression docs expirés S3 + MongoDB | 2j | Docs 90j supprimés en < 24h |
| Effacement | `DELETE /v1/rgpd/data` → anonymisation asynchrone < 72h | 2j | Email confirmation suppression envoyé |
| Export | `POST /v1/rgpd/export` → JSON S3 presigned URL 24h | 1j | Export reçu par email |
| Chiffrement PII | MongoDB Field Level Encryption AWS KMS (ADR-005) | 2j | PII illisibles en base directement |

## Skills Partie 3
| Skill | Usage |
|-------|-------|
| `docai-security-keycloak` | TenantJwtFilter, RBAC, Rate Limiting Bucket4j, RFC 7807, impersonation |
| `docai-module0-onboarding` | Inscription tenant, invitation équipe, activation, révocation |
| `docai-module0-auth` | Login, Logout, Refresh Token, JWT blacklist, mot de passe oublié |
| `docai-module0-profil` | Changement MDP, 2FA TOTP, historique connexions |
| `docai-module0-billing` | Feature Flag, plans FREE/Starter/Pro/Enterprise, quota Lua, Stripe |
| `docai-module0-rgpd` | Rétention, effacement, export, chiffrement KMS, suppression compte |
| `docai-email-ses` | Amazon SES, 19 templates email, Thymeleaf |
| `docai-bdd-scenario` | Scénarios BDD Gherkin/Cucumber pour chaque module |
| `docai-adapter-mongodb` | Repository, Mongock migrations, isolation tenant obligatoire |
| `docai-adapter-rest` | Controller REST, RFC 7807, JWT, versioning /v1, pagination |

## ✅ Critère de passage → Partie 4
- [ ] Isolation tenant : accès croisé → HTTP 404 (pas 403)
- [ ] Tous les BDD scénarios sécurité passent
- [ ] RGPD : effacement end-to-end (S3 + MongoDB + email confirmation)
- [ ] Chiffrement KMS : PII illisibles en base sans l'application

---

---

# PARTIE 4 — Pipeline de Traitement | Semaines 8–21

> **Objectif :** Pipeline complet Upload → Classification → Extraction → Validation → Fraude → Orchestration.
> **Règle :** Ordre strict entre modules — chaque module dépend du précédent.

## Module 1.1 — Upload & Validation | Semaines 8–9

| Contenu | Critère |
|---------|---------|
| `POST /v1/documents` multipart | HTTP 201 avec `documentId` UUID |
| Quota atomique Lua (ADR-001) | 429 QUOTA_EXCEEDED si dépassé |
| Idempotence X-Idempotency-Key (Valkey 24h) | Double soumission → même `documentId` |
| Upload S3 multipart + `AbortMultipartUpload` (ADR-007) | Coupure réseau → parties S3 nettoyées |
| Hash SHA-256 streaming → déduplication | Même fichier 2× → 1 seul document |
| Outbox Pattern (transaction atomique MongoDB) | Zéro perte si Kafka down |

## Module 1.2 — Classification IA | Semaines 10–12

| Contenu | Critère |
|---------|---------|
| `ClassificationKafkaConsumer` consomme `docai.doc.uploaded` | Consumer group `docai.recognition.classification.group` |
| `VisionModelAdapter` GPT-4o (temperature 0.0) | FACTURE, CNI, RIB, ORDONNANCE, BULLETIN_SALAIRE, PASSEPORT |
| `FallbackRuleBasedClassifier` heuristique | Fallback si Circuit Breaker OPEN |
| Score confiance ≥ 0.85 → CLASSIFIED, < 0.70 → NEEDS_REVIEW | Seuils respectés |
| Cache classification SHA-256 Valkey 1h ± jitter (ADR-003) | 2ème upload même fichier → 0 appel LLM |

## Module 2.1 — OCR + Extraction LLM | Semaines 13–15

| Contenu | Critère |
|---------|---------|
| `PdfBoxOcrAdapter` PDF texte natif | Extraction sans OCR |
| `Tess4JOcrAdapter` PDF scanné/image (langue FR) | OCR Tesseract < 60s |
| `rawOcrText` → S3 uniquement (ADR-004) | Jamais dans MongoDB |
| `OpenAiLlmAdapter` GPT-4o, temperature 0.0, JSON mode | Extraction structurée 6 types |
| Cache extraction SHA-256 Valkey 24h ± jitter (ADR-003) | Cache hit → 0 appel LLM |
| Circuit Breaker LLM OPEN → fallback NEEDS_REVIEW | Pipeline non bloqué |

## Module 2.2 — Validation Métier & APIs Externes | Semaines 16–17

| Contenu | Critère |
|---------|---------|
| Algorithme Luhn SIRET (local, sans API) | SIRET invalide → signal DATA_SIRET_INVALID |
| Modulo 97 IBAN (local, sans API) | IBAN invalide → signal bloquant |
| `InseeApiAdapter` OAuth2, cache 7j ± jitter | SIRET actif confirmé |
| `BanApiAdapter` Géoplateforme IGN, cache 30j ± jitter | Adresse normalisée |
| `RppsApiAdapter` FHIR ANS + mode LOCAL, cache 7j ± jitter | RPPS médecin actif confirmé |
| Fail-open obligatoire sur toutes les APIs externes | API down → pipeline continue |

## Module 2.3 — Correction Manuelle | Semaine 18

| Contenu | Critère |
|---------|---------|
| `PUT /v1/documents/{id}/fields/{field}` | Correction champ extrait |
| `AuditEntry` immuable pour chaque correction | `userId`, `oldValue`, `newValue`, `timestamp` |
| Invalidation cache Valkey après correction | Prochaine extraction repart de zéro |
| Revalidation automatique après correction | Signal fraude mis à jour |

## Module 3.1 — Fraude Scoring | Semaines 19–20

| Contenu | Critère |
|---------|---------|
| `FraudKafkaConsumer` consomme `docai.doc.extracted` | Consumer group `docai.fraud.analysis.group` |
| Scoring 0–100 multi-signaux | Score calculé correctement |
| Signaux données (SIRET invalide, IBAN invalide, date incohérente...) | Chaque signal avec son poids |
| Strategy Pattern : analyseurs injectés via Spring DI | Fail-safe : analyseur défaillant → pipeline continue |

## Module 3.2 — Analyseurs Avancés (Tika + OpenCV) | Semaines 20–21

| Contenu | Critère |
|---------|---------|
| `ApacheTikaMetadataAdapter` signaux META_* | Photoshop → `META_EDITOR_SUSPICIOUS` (poids 25) |
| `VisualAnalyzerAdapter` JavaCV signaux VISUAL_* | Timeout 15s obligatoire (BR-VIS-003) |
| `FraudAnalyzerRegistry` auto-enregistrement Spring | Nouveau `@Component` → inclus automatiquement |
| Fail-safe sur chaque analyseur | Exception → signal ignoré, pipeline continue |

## Module 3.3 — Révision Humaine | Semaines 21–22

| Contenu | Critère |
|---------|---------|
| Score 51–75 → queue révision FRAUD_REVIEWER | State machine PENDING_REVIEW → REVIEWING → APPROVED/REJECTED/ESCALATED |
| Score > 75 → REJECTED immédiat | Alerte SSE en < 2s |
| `ReviewDecision` Value Object immuable | Comment obligatoire si REJECTED/ESCALATED |
| `AuditEntry` immuable pour chaque décision | `reviewerId` masqué, `outcome`, `timestamp` |

## Module 4 — Orchestration & Pipeline | Semaines 22–24

| Phase | Contenu | Critère |
|-------|---------|---------|
| 4.1 — Kafka + Idempotence | `ResilientKafkaConsumer`, idempotence `topic:partition:offset`, `OutboxPoller` | Même offset 2× → traité 1× |
| 4.2 — Retry + DLQ | Retry exponentiel 3×, DLQ après 3 échecs, rétention 90j | DLQ alerte Grafana si > 10 messages |
| 4.3 — Saga + Compensation | 7 scénarios d'échec → NEEDS_REVIEW, state machine PENDING→COMPLETED | Kafka down 5min → zéro perte document |

## Skills Partie 4
| Skill | Usage |
|-------|-------|
| `docai-module1-upload` | Upload, idempotence, quota Lua, S3 multipart, Outbox |
| `docai-module1-classification` | Consumer Kafka, GPT-4o vision, fallback heuristique, cache |
| `docai-module2-ocr-llm` | PDFBox, Tess4J, OpenAI, cache SHA-256, ADR-003/004 |
| `docai-adapter-external-apis` | InseeApiAdapter, BanApiAdapter, RppsApiAdapter, ACL complet |
| `docai-module2-correction` | Correction manuelle, AuditEntry, invalidation cache |
| `docai-fraud-analysis` | Scoring 0-100, signaux, Strategy Pattern, Tika, OpenCV, révision humaine, SSE |
| `docai-module3-fraude-analyseurs` | ApacheTikaMetadataAdapter, VisualAnalyzerAdapter, Registry, timeout 15s |
| `docai-module4-pipeline` | Kafka idempotence, OutboxPoller, DLQ, retry exponentiel |
| `docai-saga-compensation` | 7 scénarios Saga, compensations, state machine PENDING→COMPLETED |
| `docai-adapter-kafka` | Consumer groups, ResilientKafkaConsumer, Outbox Pattern |
| `docai-adapter-s3` | Upload multipart, clé OCR, AbortMultipartUpload ADR-007 |
| `docai-adapter-mongodb` | Repository, Mongock V001→, index strategy, isolation tenant |
| `docai-kafka-event` | Schémas Avro, Apicurio Registry, occurredAt Instant |
| `docai-resilience` | Circuit Breaker/Retry/Bulkhead/Timeout seuils exacts |
| `docai-archunit-verify` | 12 règles ArchUnit, commandes vérification, violations courantes |
| `docai-contract-testing` | Spring Cloud Contract, stubs WireMock, endpoints publics |
| `docai-llm-prompt` | VisionModelAdapter, LlmExtractionAdapter, prompts système |
| `docai-bdd-scenario` | Scénarios BDD Cucumber pour chaque module |
| `docai-test-integration` | TestContainers, AbstractIntegrationTest, WireMock |
| `docai-performance-test` | k6, 3 scénarios (nominal/pointe/stress), seuils CI |

## ✅ Critère de passage → Partie 5
- [ ] Upload → Classification → Extraction → Validation → Fraude → COMPLETED end-to-end
- [ ] Zéro perte document lors d'une panne Kafka 5 min (Outbox + replay)
- [ ] Score fraude calculé sur chaque document traité
- [ ] Dashboard monitoring : lag Kafka < 1000 messages, Circuit Breakers CLOSED

---

---

# PARTIE 5 — Produit & Monétisation | Semaines 22–33

> **Objectif :** Dashboard temps réel, API publique, Billing Stripe → SaaS commercialisable.
> **Règle :** Modules dans l'ordre : 5 → 6 → 7.

## Module 5 — Dashboard & Reporting | Semaines 22–25

| Phase | Contenu | Critère |
|-------|---------|---------|
| 5.1 — Read Model CQRS | `DashboardProjectionConsumer`, `lastSyncedAt` (ADR-011), `ReadModelReconciliationScheduler` 5min | Latence dashboard < 100ms |
| 5.2 — SSE Temps Réel | `SseEmitter`, isolation tenant, keepalive 30s, limite 50 connexions/tenant | Alerte fraude SSE < 2s |
| 5.3 — Notifications In-App | Historique MongoDB TTL 90j, types FRAUD_ALERT/QUOTA_WARNING/PAYMENT_FAILED, marquage lu/non-lu | Badge non lues temps réel |
| 5.4 — Dashboard Avancé | Export CSV/XLSX, recherche full-text MongoDB, filtres avancés multi-critères | Feature Flag `dashboard.search.enabled` |

## Module 6 — Intégrations & API Publique | Semaines 26–30

| Phase | Contenu | Critère |
|-------|---------|---------|
| 6.1 — API Keys | SHA-256 + sel, scopes READ/WRITE/ADMIN, révocation immédiate, OpenAPI 3.1 | Time-to-first-call < 1h |
| 6.2 — Webhooks | HMAC-SHA256, retry 5× backoff exponentiel, DLQ après 5 échecs, log livraison MongoDB | 0 perte webhook sur 1000 livraisons |
| 6.3 — Quotas & Rate Limiting | Plans Starter 500/Pro 10K/Enterprise illimité, reset 1er du mois UTC, overage facturé | Reset mensuel atomique Lua (ADR-001) |

## Module 7 — Billing Stripe | Semaines 31–33

| Phase | Contenu | Critère |
|-------|---------|---------|
| Semaine 31 | Feature Flag, 4 plans, overage calculé temps réel | `GET /v1/billing/plans` retourne 4 plans |
| Semaine 32 | Stripe Checkout + Customer Portal + 5 webhooks Stripe | Paiement test CB réussi, idempotence webhooks |
| Semaine 33 | J-7/J-3/J-0 emails, PAST_DUE lecture seule, downgrade ADR-009, rapport mensuel | Client facturé en self-service complet |

## Skills Partie 5
| Skill | Usage |
|-------|-------|
| `docai-cqrs-readmodel` | Read Model CQRS, DashboardProjectionConsumer, lastSyncedAt ADR-011 |
| `docai-sse-realtime` | SseEmitter, AlertKafkaConsumer, isolation tenant, keepalive |
| `docai-notifications-inapp` | Historique MongoDB TTL 90j, badge, marquage lu/non-lu |
| `docai-module5-dashboard-avance` | Export CSV/XLSX, recherche full-text, filtres avancés |
| `docai-api-keys` | SHA-256, scopes, révocation, OpenAPI 3.1, contract tests |
| `docai-webhooks` | HMAC-SHA256, retry 5×, Circuit Breaker, DLQ, log livraison |
| `docai-rate-limiting` | Quotas plans, reset mensuel, overage, notifications 80%/95% |
| `docai-billing-stripe` | Stripe Checkout/Portal/Webhooks, cycle de vie FREE→PAID, ADR-009 |
| `docai-adapter-rest` | Controller REST, RFC 7807, versioning /v1 |
| `docai-contract-testing` | Spring Cloud Contract obligatoire sur tous les endpoints Module 6 |
| `docai-performance-test` | k6 obligatoire avant chaque release production |
| `docai-annexes-standards` | Production Readiness Checklist, chaos engineering, SLA 99.9% |

## ✅ Critère de passage → Production
- [ ] Inscription → choix plan → paiement CB → facture → tout en self-service
- [ ] API Key créée → premier appel en < 1h
- [ ] Webhook livré avec signature HMAC valide
- [ ] SLA respecté : traitement P95 < 30s, dashboard P95 < 100ms
- [ ] Chaos Engineering : 7 scénarios testés en staging
- [ ] Production Readiness Checklist complète (`docai-annexes-standards`)

---

---

# Référence Globale — Tous les Skills par Catégorie

## 🔵 Skills Transversaux (lire en premier — toujours)
| Skill | Description |
|-------|-------------|
| `docai-architecture-adr` | Architecture hexagonale, 12 règles ArchUnit, 11 ADR |
| `docai-stack-technique` | Stack V15.0, Kafka 8 topics, Valkey TTL, Resilience4j seuils |
| `docai-persistance-standards` | MongoDB collections, index, Mongock migrations, pagination |
| `docai-observability` | Métriques Micrometer, logs JSON structurés, OpenTelemetry |
| `docai-resilience` | Circuit Breaker/Retry/Bulkhead/Timeout — 8 services |
| `docai-domain-model` | Aggregate, Value Object, Domain Event, Java 21, Sealed Classes |
| `docai-usecase-implement` | Use Case, @Audited, @QuotaProtected, TenantContext, Outbox |

## ⚙️ Skills Setup & Infrastructure (Partie 1)
| Skill | Description |
|-------|-------------|
| `docai-setup-projet` | Structure Maven, Docker Compose, Keycloak, .env |
| `docai-cicd-pipeline` | GitHub Actions, Dockerfile multi-stage, K8s, Quality Gates |
| `docai-seeding` | SeedingService DEV, 3 tenants, 10 utilisateurs, idempotence |

## 🔧 Skills Commons (Partie 2)
| Skill | Description |
|-------|-------------|
| `docai-commons-implement` | 7 commons : TenantContext, Outbox, Quota, ResilientKafkaConsumer, AbstractIntegrationTest |

## 🔐 Skills Fondations Module 0 (Partie 3)
| Skill | Description |
|-------|-------------|
| `docai-security-keycloak` | TenantJwtFilter, RBAC 5 rôles, Rate Limiting Bucket4j, impersonation |
| `docai-module0-onboarding` | Inscription tenant, invitation équipe, activation, révocation |
| `docai-module0-auth` | Login, Logout, Refresh Token, JWT blacklist Valkey, MDP oublié |
| `docai-module0-profil` | Changement MDP, 2FA TOTP, historique connexions |
| `docai-module0-billing` | Feature Flag, plans FREE/Starter/Pro/Enterprise, quota Lua |
| `docai-module0-rgpd` | Rétention, effacement async, export portabilité, chiffrement KMS |
| `docai-email-ses` | Amazon SES, 19 templates Thymeleaf |

## 🚀 Skills Pipeline Modules 1→4 (Partie 4)
| Skill | Description |
|-------|-------------|
| `docai-module1-upload` | Upload multipart, idempotence, quota Lua, S3, Outbox |
| `docai-module1-classification` | Consumer Kafka, GPT-4o vision, fallback heuristique, cache |
| `docai-module2-ocr-llm` | PDFBox, Tess4J, OpenAI LLM, cache SHA-256, ADR-003/004 |
| `docai-adapter-external-apis` | InseeApiAdapter, BanApiAdapter, RppsApiAdapter, ACL, fail-open |
| `docai-module2-correction` | Correction manuelle, AuditEntry immuable, invalidation cache |
| `docai-fraud-analysis` | Scoring 0-100, signaux, Strategy Pattern, Tika, OpenCV, révision, SSE |
| `docai-module3-fraude-analyseurs` | ApacheTikaMetadataAdapter META_*, VisualAnalyzerAdapter VISUAL_*, timeout 15s |
| `docai-module4-pipeline` | Kafka at-least-once, OutboxPoller, DLQ, retry exponentiel |
| `docai-saga-compensation` | 7 scénarios Saga, compensations, state machine PENDING→COMPLETED |
| `docai-llm-prompt` | VisionModelAdapter, LlmExtractionAdapter, prompts système |

## 📊 Skills Produit Modules 5→7 (Partie 5)
| Skill | Description |
|-------|-------------|
| `docai-cqrs-readmodel` | Read Model CQRS, DashboardProjectionConsumer, lastSyncedAt ADR-011 |
| `docai-sse-realtime` | SseEmitter, AlertKafkaConsumer, isolation tenant, keepalive 30s |
| `docai-notifications-inapp` | Historique MongoDB TTL 90j, badge, marquage lu/non-lu |
| `docai-module5-dashboard-avance` | Export CSV/XLSX, recherche full-text, filtres avancés |
| `docai-api-keys` | SHA-256 + sel, scopes READ/WRITE/ADMIN, révocation, OpenAPI 3.1 |
| `docai-webhooks` | HMAC-SHA256, retry 5×, Circuit Breaker, DLQ, log livraison |
| `docai-rate-limiting` | Quotas plans, reset mensuel Lua, overage, notifications 80%/95% |
| `docai-billing-stripe` | Stripe Checkout/Portal/Webhooks, cycle FREE→PAID, downgrade ADR-009 |

## 🔌 Skills Adapters Patterns (transversaux)
| Skill | Description |
|-------|-------------|
| `docai-adapter-rest` | Controller REST, RFC 7807, JWT, versioning /v1 |
| `docai-adapter-mongodb` | Repository, @Document, Mongock V001→, isolation tenant |
| `docai-adapter-kafka` | Consumer groups, ResilientKafkaConsumer, Outbox Pattern |
| `docai-adapter-valkey` | Cache-Aside, Write-Through, TTL jitter ADR-003 |
| `docai-adapter-s3` | Upload multipart, clé OCR, AbortMultipartUpload ADR-007 |
| `docai-kafka-event` | Avro, Apicurio Registry, occurredAt Instant |
| `docai-archunit-verify` | 12 règles ArchUnit, violations courantes, commandes fix |
| `docai-contract-testing` | Spring Cloud Contract, stubs WireMock |
| `docai-feature-flag` | Unleash, 6 flags, pattern FeatureFlagPort |

## 🧪 Skills Tests & Qualité
| Skill | Description |
|-------|-------------|
| `docai-test-integration` | TestContainers, AbstractIntegrationTest, withReuse=true |
| `docai-bdd-scenario` | Gherkin, Cucumber, step definitions pour tous les modules |
| `docai-performance-test` | k6, 3 scénarios (nominal/pointe/stress), seuils CI |

## 📋 Skills Annexes & Standards
| Skill | Description |
|-------|-------------|
| `docai-annexes-standards` | Production Readiness Checklist, 7 scénarios Chaos Engineering, SLA 99.9% |

---

---

# Résumé — 51 Skills au Total

| Catégorie | Skills | Nombre |
|-----------|--------|--------|
| Transversaux | `docai-architecture-adr`, `docai-stack-technique`, `docai-persistance-standards`, `docai-observability`, `docai-resilience`, `docai-domain-model`, `docai-usecase-implement` | **7** |
| Setup & Infrastructure | `docai-setup-projet`, `docai-cicd-pipeline`, `docai-seeding` | **3** |
| Commons | `docai-commons-implement` | **1** |
| Fondations Module 0 | `docai-security-keycloak`, `docai-module0-onboarding`, `docai-module0-auth`, `docai-module0-profil`, `docai-module0-billing`, `docai-module0-rgpd`, `docai-email-ses` | **7** |
| Pipeline Modules 1→4 | `docai-module1-upload`, `docai-module1-classification`, `docai-module2-ocr-llm`, `docai-adapter-external-apis`, `docai-module2-correction`, `docai-fraud-analysis`, `docai-module3-fraude-analyseurs`, `docai-module4-pipeline`, `docai-saga-compensation`, `docai-llm-prompt` | **10** |
| Produit Modules 5→7 | `docai-cqrs-readmodel`, `docai-sse-realtime`, `docai-notifications-inapp`, `docai-module5-dashboard-avance`, `docai-api-keys`, `docai-webhooks`, `docai-rate-limiting`, `docai-billing-stripe` | **8** |
| Adapters Patterns | `docai-adapter-rest`, `docai-adapter-mongodb`, `docai-adapter-kafka`, `docai-adapter-valkey`, `docai-adapter-s3`, `docai-kafka-event`, `docai-archunit-verify`, `docai-contract-testing`, `docai-feature-flag` | **9** |
| Tests & Qualité | `docai-test-integration`, `docai-bdd-scenario`, `docai-performance-test` | **3** |
| Annexes & Standards | `docai-annexes-standards` | **1** |
| **TOTAL** | | **49** |

---

*DocAI Skills Reference — Généré depuis DOCAI_PLAN_DEVELOPPEMENT_SKILLS.md*
