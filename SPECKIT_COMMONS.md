# DocAI — SpecKit Partie 2 : Commons
## MASTER Partie 2 · Semaines 2–3 · 7 Modules · 7 Skills

> **Objectif :** Implémenter les 7 modules commons partagés, testés à ≥ 90% de couverture, disponibles comme dépendances Maven pour tous les modules métier.
> **Règle absolue :** L'ordre d'implémentation ci-dessous est NON-NÉGOCIABLE — chaque commons bloque le suivant.
> **Prérequis :** Partie 1 validée — `./mvnw clean compile` BUILD SUCCESS, tous les services Docker healthy, CI vert.

---

## ⚠️ Ordre d'implémentation obligatoire

> **Les 7 commons doivent être implémentés ET testés (≥ 90% coverage) avant de démarrer le Module 0 (Partie 3).**
> Sans eux, chaque développeur réimplémente la même plomberie → code dupliqué dès le premier sprint.

```
┌─────────────────────────────────────────────────────────────────────┐
│  ORDRE D'IMPLÉMENTATION SEMAINES 2–3                                │
├─────┬──────────────────────────────┬──────────────────────────────┤
│  1  │ Module 2.A                   │ commons-multitenancy          │
│     │ (0.D.1 — 2 jours)           │ TenantContext, TenantJwtFilter│
│     │                              │ MongoTenantFilter             │
│     │                              │ ValkeyTokenBlacklistAdapter   │
├─────┼──────────────────────────────┼──────────────────────────────┤
│  2  │ Module 2.B                   │ commons-api                   │
│     │ (0.D.2 — 1 jour)            │ ApiResponse<T>, RFC 7807      │
│     │                              │ GlobalExceptionHandler        │
│     │                              │ IdempotencyFilter             │
├─────┼──────────────────────────────┼──────────────────────────────┤
│  3  │ Module 2.C                   │ commons-audit                 │
│     │ (0.D.3 — 1 jour)            │ AuditEvent, AuditPort         │
│     │                              │ @Audited AOP                  │
├─────┼──────────────────────────────┼──────────────────────────────┤
│  4  │ Module 2.D                   │ commons-outbox                │
│     │ (0.D.4 — 2 jours)           │ OutboxMessage, OutboxRelay    │
│     │                              │ Transaction atomique Mongo    │
├─────┼──────────────────────────────┼──────────────────────────────┤
│  5  │ Module 2.E                   │ commons-quota                 │
│     │ (0.D.5 — 1 jour)            │ QuotaPort, Script Lua ADR-001 │
│     │                              │ @QuotaProtected AOP           │
├─────┼──────────────────────────────┼──────────────────────────────┤
│  6  │ Module 2.F                   │ commons-kafka                 │
│     │ (0.D.6 — 2 jours)           │ ResilientKafkaConsumer        │
│     │                              │ JitterTtl ADR-003             │
│     │                              │ DLQ + Idempotence Valkey      │
├─────┼──────────────────────────────┼──────────────────────────────┤
│  7  │ Module 2.G                   │ commons-testing               │
│     │ (0.D.7 — 1 jour)            │ AbstractIntegrationTest       │
│     │                              │ TestBuilders, WireMock stubs  │
└─────┴──────────────────────────────┴──────────────────────────────┘
```

> **Pourquoi cet ordre ?**
> - `commons-multitenancy` est utilisé par tous les adapters MongoDB et REST → en premier.
> - `commons-api` (GlobalExceptionHandler) dépend de TenantContext → après multitenancy.
> - `commons-audit` (@Audited) est une annotation AOP indépendante → peut suivre.
> - `commons-outbox` nécessite MongoDB (OutboxMongoAdapter) → après commons-api.
> - `commons-quota` (script Lua Valkey ADR-001) → dépend de commons-multitenancy pour le tenantId.
> - `commons-kafka` (JitterTtl, DLQ) → dépend de commons-outbox pour la publication via Outbox.
> - `commons-testing` → dépend de TOUS pour fournir les stubs et builders complets.

---

## Skills associés — Partie 2 complète

| Skill | Rôle |
|-------|------|
| `docai-commons-implement` | Signatures Java exactes des 7 commons, interfaces, ordre, AbstractIntegrationTest — **LIRE EN PREMIER** |
| `docai-architecture-adr` | 12 règles ArchUnit, ADR-001/002/003/006/008, SOLID, Design Patterns — vérifier avant chaque commit |
| `docai-stack-technique` | Kafka topologie, Valkey stratégies, Resilience4j seuils, 8 schémas Avro |
| `docai-persistance-standards` | MongoDB conventions, BR-PAG-001→008, BR-MIG-001→007, pagination |
| `docai-observability` | Logs JSON structurés, MDC, PII masqués, 14 métriques Micrometer, OpenTelemetry |
| `docai-cicd-pipeline` | GitHub Actions, sonar-project.properties, Quality Gates, NFR-CI |
| `docai-annexes-standards` | Production Readiness Checklist, GitFlow, Dependabot, rotation secrets BR-ROT |
| `docai-adapter-kafka` | Pattern ResilientKafkaConsumer, consumer group IDs, Avro, Apicurio Registry |
| `docai-adapter-valkey` | Cache-Aside, Write-Through, TTL jitter ADR-003, 9 stratégies cache |
| `docai-test-integration` | AbstractIntegrationTest, TestContainers reuse, withReuse=true |

---

## Référence ADR — Applicables aux Commons (NON-NÉGOCIABLES)

| ADR | Priorité | Règle obligatoire | Commons concerné |
|-----|----------|-------------------|-----------------|
| **ADR-001** | 🔴 Critique | Quota : script Lua **ATOMIQUE** Valkey — jamais `GET` puis `INCR` séparés | `commons-quota` |
| **ADR-002** | 🔴 Critique | Kafka : clé partition = `documentId` pour le pipeline — jamais `tenantId` | `commons-kafka` |
| **ADR-003** | 🔴 Critique | Cache : TTL avec jitter `±10%` sur tout TTL > 1h — jamais TTL fixe | `commons-kafka` (JitterTtl) |
| **ADR-006** | 🟠 Important | JWKS Keycloak en cache local TTL 1h — sinon Keycloak down = tous bloqués | `commons-multitenancy` |
| **ADR-008** | 🟠 Important | CI : 3 jobs séparés + JVM `-Xmx512m` + TestContainers `reuse=true` | `commons-testing` |

---

## Références Annexes — Applicables dès Partie 2

### Annex B — Standards MongoDB (ADR-010)
| Règle | Application Partie 2 |
|-------|----------------------|
| Collections `snake_case` pluriel | `audit_entries`, `outbox_events` |
| Champs `camelCase`, dates suffixées `At` | `occurredAt`, `createdAt`, `publishedAt` |
| JAMAIS `@Indexed` dans le code Java | Tous les index via **Mongock uniquement** |
| `tenantId` EN PREMIER dans index composés | `{ tenantId: 1, occurredAt: -1 }` (audit) · `{ tenantId: 1, status: 1, createdAt: 1 }` (outbox) |
| EXPLAIN PLAN obligatoire avant merge | `winningPlan.stage = IXSCAN` sur findByTenant() et findPending() |
| `@RollbackExecution` sur chaque migration | V005 audit_entries · V002 outbox_events |
| Nommage migrations | `V005_commons_audit_entries_collection` · `V002_commons_outbox_events_collection` |

### Annex B — 15 Collections MongoDB (référence complète projet)
| Collection | Rôle | Caractéristique clé |
|-----------|------|---------------------|
| `documents` | Aggregate racine du pipeline | Statut, metadata, S3 key |
| `extraction_results` | Résultats OCR + LLM | Schéma `fields[]` variable par type |
| `fraud_analyses` | Analyse fraude — **immuable après création** | Tableau `signals[]` |
| `audit_entries` | Journal immuable — **append-only** | TTL index 5 ans — **commons-audit** |
| `outbox_events` | Outbox Pattern | Statut PENDING/PUBLISHED/FAILED — **commons-outbox** |
| `document_summary_views` | Read Model CQRS Dashboard | Agrégat dénormalisé — ADR-011 |
| `webhook_deliveries` | Log livraisons webhooks | Tableau `attempts[]` |
| `api_keys` | Clés API clients | Hash SHA-256+sel, jamais en clair |
| `tenant_configs` | Configuration par tenant | Plan, quotas |
| `subscriptions` | Abonnements Stripe | Cycle de vie billing |
| `login_history` | Historique connexions | TTL index 90 jours |
| `invitation_tokens` | Tokens d'invitation | TTL index 7 jours |
| `password_reset_tokens` | Tokens reset MDP | TTL index 1h |
| `notifications` | Notifications in-app | TTL index 90 jours |
| `dlq_messages` | Messages DLQ archivés | Rétention 90 jours |

### Annex B — BR-MIG-001→007 (Règles Mongock — NON-NÉGOCIABLES)
| Règle | Obligation |
|-------|-----------|
| **BR-MIG-001** | Chaque migration dans sa propre classe `@ChangeUnit` |
| **BR-MIG-002** | Migrations backward-compatible — jamais supprimer un champ en 1 migration |
| **BR-MIG-003** | `auto-index-creation: false` en production — uniquement via Mongock |
| **BR-MIG-004** | Chaque migration a une méthode `@RollbackExecution` |
| **BR-MIG-005** | Pas de logique métier dans une migration — uniquement DDL |
| **BR-MIG-006** | Migrations testées en staging avant la production |
| **BR-MIG-007** | Migration échouée = application refuse de démarrer + alerte Tech Lead |

> ⚠️ **Migrations Partie 2 :** V002 (outbox_events) · V005 (audit_entries)
> Convention nommage : `V{NNN}_{module}_{description}` ex: `V002_commons_outbox_events_collection`

### Annex B — BR-PAG-001→008 (Pagination — NON-NÉGOCIABLES)
| Règle | Obligation |
|-------|-----------|
| **BR-PAG-001** | Paramètres `page`, `size`, `sort` sur tous les endpoints liste |
| **BR-PAG-002** | `size` maximum 100 — sinon HTTP 400 |
| **BR-PAG-003** | `size` par défaut 20 |
| **BR-PAG-004** | Réponse avec `totalElements` + `totalPages` |
| **BR-PAG-005** | `size > 100` → HTTP 400 "Maximum page size is 100" |
| **BR-PAG-006** | Tri par défaut `createdAt,desc` |
| **BR-PAG-007** | Champs de tri documentés dans OpenAPI |
| **BR-PAG-008** | Implémenté une seule fois dans `commons-api` — **jamais réimplémenté** |

### Annex C — Secrets applicables Partie 2
| Secret | Utilisé dans | Stockage DEV | Stockage PROD |
|--------|-------------|--------------|---------------|
| `VALKEY_URL` | commons-multitenancy, commons-quota, commons-kafka | `.env` local | AWS Secrets Manager |
| `KEYCLOAK_JWK_URI` | TenantJwtFilter (ADR-006 cache 1h) | `.env` local | AWS Secrets Manager |
| `KAFKA_BOOTSTRAP_SERVERS` | commons-kafka, commons-outbox | `.env` local | AWS Secrets Manager |
| `MONGODB_URI` | commons-outbox, commons-audit | `.env` local | AWS Secrets Manager |

### Clean Code Standards (docai-persistance-standards — applicables à TOUS les commons)
| Règle | Seuil | Vérification |
|-------|-------|-------------|
| Longueur méthode | ≤ **20 lignes** | Checkstyle `MethodLength` |
| Nombre de paramètres | ≤ **4** | Checkstyle `ParameterNumber` |
| Complexité cyclomatique | ≤ **10** | Checkstyle `CyclomaticComplexity` |
| Longueur classe | ≤ **200 lignes** | Checkstyle `FileLength` |
| Nommage | `*UseCase` (ports IN) · `*Port` (ports OUT) · `*Adapter` (adapters) | ArchUnit Règles 4/5/6 |

### SOLID appliqué aux Commons DocAI
| Principe | Application concrète dans les commons |
|---------|---------------------------------------|
| **S** — Single Responsibility | 1 commons = 1 responsabilité (multitenancy ≠ audit ≠ quota) |
| **O** — Open/Closed | `ResilientKafkaConsumer` extensible via `handle()` — fermé à modification |
| **L** — Liskov | `ValkeyQuotaAdapter` interchangeable avec tout autre `QuotaPort` |
| **I** — Interface Segregation | `QuotaPort` ≠ `AuditPort` ≠ `IdempotencyPort` — ports fins, pas de god-interface |
| **D** — Dependency Inversion | Use cases dépendent des Ports — jamais des Adapters Valkey/Mongo directement |

### Architecture Hexagonale — 12 Règles ArchUnit (commons)
```java
// HexagonalArchitectureTest — vérifiées à CHAQUE commit CI (ADR-008)
Règle 1  : domaine pur Java — ZERO import Spring/MongoDB/Kafka/AWS/Stripe
Règle 2  : adapters ne s'appellent pas entre eux
Règle 3  : application dépend uniquement du domaine
Règle 4  : ports IN (*UseCase) dans domain/port/in/
Règle 5  : ports OUT (*Port) dans domain/port/out/
Règle 6  : adapters (*Adapter) dans docai-adapter-*
Règle 7  : pas de MongoDB direct dans le domaine (org.springframework.data.mongodb)
Règle 8  : pas de Kafka direct dans le domaine (org.springframework.kafka)
Règle 9  : @RestController dans adapter-in-rest uniquement
Règle 10 : @KafkaListener dans adapter-in-kafka uniquement
Règle 11 : @Document MongoDB dans adapter-out-mongodb uniquement
Règle 12 : pas de @Transactional dans le domaine

// Ports OUT créés en Partie 2 (tous dans docai-domain/port/out/) :
QuotaPort · AuditPort · OutboxRepository · OutboxEventPublisher
IdempotencyPort · TokenBlacklistPort · IdentityProviderPort · KafkaEventPublisher
```

### Stratégies Cache Valkey — 9 stratégies (ADR-003)
| Cache | Clé Valkey | TTL | Jitter | Commons |
|-------|-----------|-----|--------|---------|
| JWT Blacklist | `jwt:blacklist:{jti}` | Durée restante token | **FIXE** (ADR-003 exception) | commons-multitenancy |
| Idempotence Kafka | `{topic}:{partition}:{offset}` | 24h | **FIXE** (ADR-003 exception) | commons-kafka |
| Idempotence upload | `idempotency:{key}` | 24h | **FIXE** (ADR-003 exception) | commons-api |
| Quota mensuel | `quota:{tenantId}:documents:{YYYY-MM}` | Reset 1er du mois | **FIXE** (ADR-003 exception) | commons-quota |
| Classification SHA-256 | `classification:{sha256}` | 1h | ±10% | (Module 1.2) |
| Extraction LLM | `extraction:{sha256}` | 24h | ±10% | (Module 2.1) |
| INSEE SIRET | `insee:siret:{siret}` | 7j | ±10% | (Module 2.2) |
| BAN adresse | `ban:adresse:{hash}` | 30j | ±10% | (Module 2.2) |
| RPPS médecin | `rpps:{rppsNumber}` | 7j | ±10% | (Module 2.2) |

> ⚠️ **ADR-003 :** `JitterTtl.withJitter()` OBLIGATOIRE sur tout TTL > 1h **sauf** les 4 exceptions FIXE ci-dessus.

### Consumer Groups Kafka — 10 groupes (ADR-002)
| Consumer | Group ID | Topic |
|----------|----------|-------|
| `ClassificationKafkaConsumer` | `docai.recognition.classification.group` | `docai.doc.uploaded` |
| `ExtractionKafkaConsumer` | `docai.extraction.extraction.group` | `docai.doc.classified` |
| `ValidationKafkaConsumer` | `docai.extraction.validation.group` | `docai.doc.extracted` |
| `FraudKafkaConsumer` | `docai.fraud.analysis.group` | `docai.doc.extracted` |
| `CompletionKafkaConsumer` | `docai.pipeline.completion.group` | `docai.doc.fraud.analyzed` |
| `DashboardProjectionConsumer` | `docai.dashboard.projection.group` | Tous topics |
| `WebhookDeliveryConsumer` | `docai.integration.webhook.group` | `docai.doc.completed` |
| `NotificationKafkaConsumer` | `docai.notification.alert.group` | `docai.doc.fraud.analyzed` |
| `AlertKafkaConsumer` (SSE) | `docai.notification.sse.group` | `docai.doc.fraud.analyzed` |
| `DlqMonitorConsumer` | `docai.pipeline.dlq.group` | `docai.doc.dlq` |

> **Règle ADR-002 :** Group ID défini dans `application.yml` — **JAMAIS** en dur dans `@KafkaListener(groupId = "...")`
> **Convention :** `docai.{module}.{name}.group`

### Resilience4j — Seuils exacts 8 services
| Service | Circuit Breaker | Retry | Bulkhead | Timeout |
|---------|----------------|-------|----------|---------|
| LLM (OpenAI/Mistral) | 50%/10 calls | 3× exp 1s | 20 threads | 30s |
| OCR Tess4J | 50%/5 calls | 3× 2s | 10 threads | 60s |
| API INSEE | 60%/5 calls | 2× 2s | 5 threads | 5s |
| API BAN | 60%/5 calls | 2× 2s | 5 threads | 5s |
| API RPPS | 60%/8 calls | 2× 3s | 5 threads | 5s |
| Apache Tika | 50%/5 calls | 2× 1s | 5 threads | **15s** (BR-VIS-003) |
| OpenCV/JavaCV | 50%/5 calls | 1× | 5 threads | **15s** (BR-VIS-003) |
| Amazon S3 | 50%/10 calls | 3× exp 1s | 20 threads | 30s |

> **Wait duration OPEN :** 30s (LLM, S3) / 60s (OCR) — **HALF_OPEN** après 3 appels autorisés

### Observabilité — 14 Métriques Micrometer + 6 Alertes Grafana
**Métriques exposées sur `/actuator/prometheus` :**
```
docai_document_upload_total{tenant, type}
docai_document_processing_duration_seconds{module}
docai_circuit_breaker_state{service}
docai_cache_hit_ratio{region}
docai_kafka_consumer_lag{topic, group}
docai_quota_usage_ratio{tenant, plan}
docai_audit_entries_written_total{tenant}
docai_outbox_pending_total
docai_outbox_relay_duration_seconds
docai_tenant_request_total{tenant, endpoint}
docai_valkey_operation_duration_seconds{operation}
docai_fraud_score_histogram{bucket}
docai_dlq_messages_total{topic}
docai_jitter_ttl_applied_total{cache}
```
**6 Alertes Grafana bloquantes :**
```
error rate > 1%          → alerte PagerDuty immédiate
Circuit Breaker OPEN     → alerte Slack + PagerDuty
Kafka lag > 1000         → alerte Slack
Valkey hit ratio < 30%   → alerte Slack
DLQ > 10 messages        → alerte Slack
P99 latence > 500ms      → alerte Slack
```

### Logs JSON structurés — Configuration obligatoire
```xml
<!-- logback-spring.xml dans docai-bootstrap/src/main/resources/ -->
<configuration>
  <springProfile name="!dev">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>tenantId</includeMdcKeyName>
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>correlationId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="JSON"/></root>
  </springProfile>
</configuration>
```
**Règles PII obligatoires :**
- Email → `[PII_MASKED]` dans tous les logs
- IBAN → `****{4 derniers chiffres}`
- SIRET → `***-***-***-***`
- Nom/prénom → `[PII_MASKED]`
- JAMAIS logguer un token JWT complet

### Definition of Ready (DoR) — 10 critères avant chaque US commons
```
DoR #1  : User Story rédigée (Qui / Quoi / Pourquoi)
DoR #2  : Critères d'acceptation BDD définis (Given/When/Then)
DoR #3  : ADR applicable identifié (ADR-001 si quota, ADR-003 si cache...)
DoR #4  : EXPLAIN PLAN MongoDB documenté si nouvelle requête (ADR-010)
DoR #5  : Conventions nommage vérifiées (snake_case collections, camelCase champs)
DoR #6  : Interface Port créée dans docai-domain avant l'adapter
DoR #7  : BR-MIG applicable identifié si migration Mongock
DoR #8  : Coverage cible définie (≥ 90% commons, ≥ 85% PIT mutation)
DoR #9  : Dépendances Maven disponibles (commons publiés en local)
DoR #10 : PR size estimée (max 400 lignes — sinon découper en 2 PR)
```

### NFR-CI — Seuils bloquants Pipeline Partie 2
```
NFR-CI-001 : build unit-tests        < 4 min  (MAVEN_OPTS=-Xmx512m ADR-008)
NFR-CI-002 : build integration-tests < 15 min (TestContainers reuse ADR-008)
NFR-CI-003 : build bdd-tests         < 20 min
NFR-CI-004 : Coverage commons        ≥ 90%    → PR bloquée sinon
NFR-CI-005 : PIT Mutation docai-domain ≥ 85%  → PR bloquée sinon
NFR-CI-006 : 0 violation ArchUnit    → pipeline arrêté immédiatement
NFR-CI-007 : 0 bug SonarCloud        → merge bloqué
NFR-CI-008 : Quality Gate SonarCloud → vert obligatoire
NFR-CI-009 : AbstractIntegrationTest démarre < 60s (reuse mode)
```

### BR-ARCH-001→004 (Architecture — NON-NÉGOCIABLES)
| Règle | Obligation |
|-------|-----------|
| **BR-ARCH-001** | `HexagonalArchitectureTest` s'exécute en CI à chaque commit |
| **BR-ARCH-002** | 12 règles ArchUnit toutes actives — aucune désactivation sans validation Tech Lead |
| **BR-ARCH-003** | PIT Mutation Testing ≥ **85%** sur `docai-domain` |
| **BR-ARCH-004** | JaCoCo ≥ **90%** sur `docai-domain` |

### BR-ROT-001→004 (Rotation des Secrets — Annex C)
| Règle | Obligation |
|-------|-----------|
| **BR-ROT-001** | Tous les secrets dans AWS Secrets Manager avec date d'expiration |
| **BR-ROT-002** | OpenAI/Keycloak/Stripe = 90j · MongoDB = 180j · KMS = annuelle auto |
| **BR-ROT-003** | Journal de rotation des secrets créé et maintenu |
| **BR-ROT-004** | Spring Cloud AWS configuré pour reload sans redéploiement |

### BR-DEP-001→002 (Dependabot)
| Règle | Obligation |
|-------|-----------|
| **BR-DEP-001** | CVE CRITICAL Dependabot → bloque déploiement production |
| **BR-DEP-002** | `dependabot.yml` configuré pour updates Maven hebdomadaires |

### GitFlow + Dependabot — Stratégie de branches
| Branche | Protection | Déploiement |
|---------|-----------|-------------|
| `main` | ✅ PR + 1 reviewer + Quality Gate | Production (approbation manuelle) |
| `develop` | ✅ PR + Quality Gate | Staging (automatique) |
| `feature/UC-MOD-XXX-description` | ❌ Libre | Aucun |
| `hotfix/v1.0.1-description` | ❌ Libre | PR vers main + develop |

> **Convention commits :** `feat/fix/test/refactor/ci/perf(scope): message`
> **Dependabot :** `dependabot.yml` → Maven hebdomadaire (BR-DEP-002)

---

## ✅ Critère de passage → Partie 3
- [ ] `./mvnw test -Dtest=HexagonalArchitectureTest` → 12 règles passent
- [ ] PIT Mutation Testing ≥ 85% sur `docai-domain` (commons domaine)
- [ ] `AbstractIntegrationTest` démarre MongoDB + Kafka + Valkey + LocalStack S3 en mode reuse < 60s
- [ ] `JitterTtl.withJitter()` utilisé sur tous les TTL Valkey > 1h (ADR-003)
- [ ] `TenantContext.clear()` dans un bloc `finally` — jamais oublié (ADR-006)
- [ ] Script Lua quota atomique testé : race condition 100 requêtes simultanées → quota exact (ADR-001)
- [ ] Idempotence consumer Kafka testée : event reçu 2× → `handle()` appelé 1× seulement (ADR-002)
- [ ] Transaction Outbox testée : Document + OutboxMessage dans même ClientSession MongoDB
- [ ] Commons publiés dans le repo Maven local : `./mvnw install -pl docai-commons`

---
---


---
---

# ═══════════════════════════════════════════════
# MODULE 2.A — commons-multitenancy
# Tâche 0.D.1 — Durée : 2 jours
# ✅ IMPLÉMENTER EN PREMIER — utilisé par tous les adapters
# ═══════════════════════════════════════════════

---

# MODULE 2.A — commons-multitenancy

> **Contenu :** `TenantContext`, `TenantJwtFilter`, `MongoTenantFilter`, `IdentityProviderPort`, `TokenBlacklistPort`, `KeycloakIdentityAdapter`, `ValkeyTokenBlacklistAdapter`
> **Durée estimée :** 2 jours
> **Skills :** `docai-commons-implement` · `docai-architecture-adr` · `docai-adapter-valkey`
> **ADR applicables :** ADR-006 (cache JWKS Keycloak 1h)

---

## 🔵 speckit-specify — Module 2.A

```
speckit-specify

Module  : Module 2.A — commons-multitenancy
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-architecture-adr, docai-adapter-valkey

Objectif :
  - TenantContext ThreadLocal (set, get, getOptional, clear)
  - TenantNotSetException (domaine pur — pas de Spring)
  - TenantJwtFilter (extrait tenant_id du JWT Keycloak, injecte TenantContext)
  - MongoTenantFilter (ajoute automatiquement tenantId à toutes les requêtes MongoDB)
  - IdentityProviderPort (interface domaine : createUser, assignRole, revokeUser,
    authenticate, refreshToken, blacklistJwt)
  - TokenBlacklistPort (interface domaine : blacklist, isBlacklisted)
  - KeycloakIdentityAdapter (implémentation IdentityProviderPort)
  - ValkeyTokenBlacklistAdapter (implémentation TokenBlacklistPort)

Composants :
  package fr.docai.commons.multitenancy (dans docai-commons-multitenancy)

  TenantContext :
    ThreadLocal<String> isolé par thread
    set(String tenantId) → TENANT.set(tenantId)
    get() → lève TenantNotSetException si null
    getOptional() → Optional.ofNullable(TENANT.get())
    clear() → TENANT.remove() — TOUJOURS dans un finally

  TenantJwtFilter extends OncePerRequestFilter :
    @Order(1) — premier filtre de la chaîne
    Extrait tenant_id du claim JWT Keycloak
    MDC.put("tenantId", tenantId) pour les logs structurés
    TenantContext.clear() dans finally OBLIGATOIRE

  MongoTenantFilter :
    Implémente MongoCallback (Spring Data MongoDB)
    Ajoute automatiquement { tenantId: TenantContext.get() }
    à chaque query MongoDB — pas besoin de le faire manuellement

  IdentityProviderPort (dans docai-domain/port/out/) :
    void createUser(String tenantId, String email, String role)
    void assignRole(String tenantId, String userId, String role)
    void revokeUser(String tenantId, String userId)
    AuthToken authenticate(String email, String password)
    AuthToken refreshToken(String refreshToken)
    void blacklistJwt(String jti, Duration ttl)

  TokenBlacklistPort (dans docai-domain/port/out/) :
    void blacklist(String jti, Duration ttl)
    boolean isBlacklisted(String jti)

ADR applicables à ce module :
  ADR-006 : cache JWKS Keycloak TTL 1h — NE PAS appeler Keycloak à chaque requête
```

---

## 🟢 speckit-clarify — Module 2.A

```
speckit-clarify

Clarifie les points suivants du Module 2.A — skill docai-commons-implement :

1. Le claim JWT contenant le tenant est-il nommé tenant_id ou tenantId
   dans le realm Keycloak configuré en Partie 1 ?
2. Le TenantJwtFilter doit-il gérer les requêtes publiques (actuator/health)
   sans JWT — retourner 401 ou laisser passer ?
3. Le MongoTenantFilter est-il activé sur TOUS les repositories MongoDB
   ou configuré manuellement par collection ?
4. La blacklist JWT (ValkeyTokenBlacklistAdapter) utilise-t-elle
   le JTI (JWT ID) ou le token complet comme clé Valkey ?
5. Le TTL de blacklist JWT dans Valkey correspond-il au TTL d'expiration
   du token (extrait du claim exp) ou une valeur fixe configurée ?
   → ADR-006 : le cache JWKS est distinct de la blacklist JWT
6. KeycloakIdentityAdapter : utilise-t-il l'Admin REST API de Keycloak
   ou le Keycloak Java Client ?
```

---

## 🟡 speckit-plan — Module 2.A

```
speckit-plan

Génère le plan d'implémentation du Module 2.A — skill docai-commons-implement.
Respecte ADR-006 (cache JWKS).

Ordre obligatoire :

ÉTAPE 1 — TenantContext + TenantNotSetException (0.25j)
  Package fr.docai.commons.multitenancy
  ThreadLocal<String> — set, get, getOptional, clear
  TenantNotSetException dans docai-domain (jamais dans commons)
  Tests unitaires : set/get/clear, concurrence multi-thread, TenantNotSetException si null
  Vérifier : coverage ≥ 90% sur TenantContext

ÉTAPE 2 — TenantJwtFilter (0.5j)
  @Component, @Order(1), extends OncePerRequestFilter
  Extraire claim tenant_id du JWT Keycloak (ADR-006 : cache JWKS configuré)
  MDC.put("tenantId", tenantId) → logs structurés automatiques
  finally { TenantContext.clear(); MDC.remove("tenantId"); }
  Tests : JWT valide → TenantContext.get() = "acme-corp"
          JWT invalide → 401 ProblemDetail RFC 7807
          finally garanti même si exception dans la chaîne

ÉTAPE 3 — MongoTenantFilter (0.5j)
  Implémente MongoCallback ou AbstractMongoEventListener
  Injecte automatiquement tenantId dans chaque query MongoDB
  Tests : query sans filtre → tenantId ajouté automatiquement
          requête cross-tenant impossible (tenantId isolé)

ÉTAPE 4 — Ports domaine + Adapters Keycloak + Valkey (0.75j)
  IdentityProviderPort dans docai-domain/port/out/ (interface pure)
  TokenBlacklistPort dans docai-domain/port/out/ (interface pure)
  KeycloakIdentityAdapter dans docai-adapter-out-external
  ValkeyTokenBlacklistAdapter dans docai-adapter-out-valkey
  Tests intégration : login Keycloak → JWT avec tenant_id
                       blacklist → isBlacklisted = true (TTL respecté)
```

---

## 🟠 speckit-checklist — Module 2.A

```
speckit-checklist

Génère la checklist complète du Module 2.A — skill docai-commons-implement.
ADR vérifiés : ADR-006.

TENANT CONTEXT :
  - [ ] ThreadLocal<String> thread-safe testé (concurrence OK)
  - [ ] TenantContext.get() lève TenantNotSetException si null — jamais null silencieux
  - [ ] TenantContext.getOptional() retourne Optional.empty() si non défini
  - [ ] TenantContext.clear() TOUJOURS dans un bloc finally — jamais oublié

FILTRE JWT :
  - [ ] @Order(1) — premier filtre Spring avant tout endpoint
  - [ ] Claim tenant_id extrait du JWT Keycloak (pas tenantId, pas tenant)
  - [ ] MDC.put("tenantId", ...) → visible dans tous les logs de la requête
  - [ ] ADR-006 ✅ : cache JWKS TTL 1h configuré (pas d'appel Keycloak par requête)
  - [ ] Requête sans JWT sur endpoint public → laissée passer (actuator, swagger)
  - [ ] Requête sans JWT sur endpoint protégé → 401 ProblemDetail RFC 7807

ISOLATION MONGO :
  - [ ] MongoTenantFilter actif sur tous les repositories — pas d'opt-out possible
  - [ ] Query sans tenantId → tenantId injecté automatiquement
  - [ ] COLLSCAN inter-tenant impossible si tenantId premier dans l'index composé

PORTS DOMAINE :
  - [ ] IdentityProviderPort dans docai-domain/port/out/ — interface pure sans Spring
  - [ ] TokenBlacklistPort dans docai-domain/port/out/ — interface pure sans Spring
  - [ ] ArchUnit : aucun import Spring dans docai-domain → CI vert

ADAPTERS :
  - [ ] KeycloakIdentityAdapter dans docai-adapter-out-external
  - [ ] ValkeyTokenBlacklistAdapter dans docai-adapter-out-valkey
  - [ ] TTL blacklist JWT = durée de vie restante du token (extrait claim exp)

DEFINITION OF DONE :
  - [ ] Coverage ≥ 90% sur commons-multitenancy (BR-ARCH-004)
  - [ ] Test multithread : 10 threads → 10 tenantId distincts isolés
  - [ ] ./mvnw test -Dtest=HexagonalArchitectureTest → 12 règles vertes (BR-ARCH-001/002)
  - [ ] PIT Mutation Testing ≥ 85% sur docai-domain (BR-ARCH-003)
```

---

## 🔴 speckit-tasks — Module 2.A

```
speckit-tasks

Découpe le Module 2.A en micro-tâches — skill docai-commons-implement.
Chaque tâche = 1 PR + 1 critère de done mesurable.

TÂCHE 2.A-01 — TenantContext + TenantNotSetException (0.5j)
  Action  : ThreadLocal<String>, set/get/getOptional/clear, TenantNotSetException
  PR      : feat(commons): add TenantContext ThreadLocal TenantNotSetException
  Critère : ./mvnw test -pl commons-multitenancy → coverage ≥ 90%
            Test concurrence : 10 threads → 10 tenantId isolés

TÂCHE 2.A-02 — TenantJwtFilter + MongoTenantFilter (1j)
  Action  : @Order(1) OncePerRequestFilter, claim tenant_id, MDC
            MongoTenantFilter injection automatique tenantId
  PR      : feat(commons): add TenantJwtFilter ADR-006 + MongoTenantFilter
  Critère : JWT valide → TenantContext.get() = "acme-corp"
            finally garanti (vérifier avec exception simulée dans filter chain)
            Requête MongoDB → tenantId ajouté automatiquement (log activé)

TÂCHE 2.A-03 — Ports + Adapters Keycloak + Valkey (0.5j)
  Action  : IdentityProviderPort + TokenBlacklistPort (domaine pur)
            KeycloakIdentityAdapter + ValkeyTokenBlacklistAdapter
  PR      : feat(commons): add IdentityProviderPort TokenBlacklistPort adapters
  Critère : ArchUnit → 0 violation (ports dans domain, adapters dans adapters)
            Login Keycloak → JWT avec tenant_id (test intégration)
            Blacklist → isBlacklisted = true avec TTL correct
```

---

## ⚫ speckit-analyse — Module 2.A

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-commons-implement
et docai-architecture-adr (ADR-006) :

Points à vérifier obligatoirement :

TENANT CONTEXT :
  1. TenantContext.clear() est-il dans un bloc finally ?
     → Si dans try sans finally → fuite mémoire ThreadLocal en production
  2. TenantContext.get() retourne-t-il null ou lève-t-il une exception ?
     → DOIT lancer TenantNotSetException — jamais retourner null silencieux

FILTRE JWT :
  3. ADR-006 : le cache JWKS Keycloak est-il configuré TTL 1h ?
     → spring.security.oauth2.resourceserver.jwt.jwk-set-cache-duration: 1h
  4. Le filtre a-t-il @Order(1) ?
     → Doit être le PREMIER filtre — sinon les logs n'ont pas le tenantId

ARCHITECTURE :
  5. IdentityProviderPort est-il dans docai-domain ?
     → Jamais dans docai-adapter-* — violation ArchUnit Règle 3
  6. KeycloakIdentityAdapter importe-t-il des classes de docai-domain directement ?
     → Doit passer par les interfaces/ports — jamais dépendance directe domain

ISOLATION MONGO :
  7. Le MongoTenantFilter est-il appliqué automatiquement ou manuellement ?
     → Doit être automatique — jamais confier l'isolation à chaque développeur

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.A

### Composant 1 — TenantContext + TenantNotSetException
```
speckit-implement

Implémente TenantContext et TenantNotSetException — skill docai-commons-implement.
Module Maven : docai-commons-multitenancy (ou docai-domain pour l'exception).

TenantContext (fr.docai.commons.multitenancy) :
  public final class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();
    private TenantContext() {}
    public static void set(String tenantId) { TENANT.set(tenantId); }
    public static String get() {
      String t = TENANT.get();
      if (t == null) throw new TenantNotSetException("tenant-id absent du contexte");
      return t;
    }
    public static Optional<String> getOptional() { return Optional.ofNullable(TENANT.get()); }
    public static void clear() { TENANT.remove(); }
  }

TenantNotSetException (fr.docai.domain.exception) :
  public class TenantNotSetException extends RuntimeException {
    public TenantNotSetException(String message) { super(message); }
  }

Tests unitaires obligatoires :
  - set/get : valeur correcte retournée
  - clear : get() après clear() lève TenantNotSetException
  - getOptional() après clear() → Optional.empty()
  - Concurrence : 10 threads avec tenantId différents → chacun isolé (CountDownLatch)
```

### Composant 2 — TenantJwtFilter (ADR-006)
```
speckit-implement

Implémente TenantJwtFilter — skill docai-commons-implement.
ADR-006 : cache JWKS Keycloak TTL 1h — jamais d'appel réseau à chaque requête.

@Component
@Order(1)
public class TenantJwtFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth instanceof JwtAuthenticationToken jwt) {
        String tenantId = jwt.getToken().getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
          sendError(res, "claim tenant_id manquant dans le JWT");
          return;
        }
        TenantContext.set(tenantId);
        MDC.put("tenantId", tenantId);
        MDC.put("traceId", UUID.randomUUID().toString()); // sera remplacé par OTel
      }
      chain.doFilter(req, res);
    } finally {
      TenantContext.clear();    // OBLIGATOIRE — jamais oublier
      MDC.remove("tenantId");
      MDC.remove("traceId");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    String path = req.getRequestURI();
    return path.startsWith("/actuator") || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs");
  }
}

application.yml (ADR-006 — cache JWKS obligatoire) :
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            jwk-set-uri: ${KEYCLOAK_JWK_URI}
            jwk-set-cache-duration: 1h      # ADR-006
            jwk-set-cache-refresh-duration: 5m
```

### Composant 3 — MongoTenantFilter
```
speckit-implement

Implémente MongoTenantFilter — skill docai-commons-implement.
Objectif : isolation automatique des données par tenant sans intervention du développeur.

@Component
public class MongoTenantFilter extends AbstractMongoEventListener<Object> {

  @Override
  public void onBeforeConvert(BeforeConvertEvent<Object> event) {
    // Injection automatique tenantId à la sauvegarde
    Object source = event.getSource();
    if (source instanceof TenantAware tenantAware) {
      if (tenantAware.getTenantId() == null) {
        tenantAware.setTenantId(TenantContext.get());
      }
    }
  }
}

// Interface à implémenter par tous les @Document MongoDB
public interface TenantAware {
  String getTenantId();
  void setTenantId(String tenantId);
}

// Règle ArchUnit à ajouter dans HexagonalArchitectureTest :
// Tous les @Document doivent implémenter TenantAware
// → détectée automatiquement en CI à chaque commit
```

### Composant 4 — Logs JSON structurés + MDC (docai-observability)
```
speckit-implement

Configure les logs JSON structurés — skill docai-observability.
traceId + tenantId dans CHAQUE log. PII masqués. Jamais texte brut en staging/prod.

<!-- logback-spring.xml dans docai-bootstrap/src/main/resources/ -->
<configuration>
  <!-- Profil DEV : console texte lisible -->
  <springProfile name="dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG"><appender-ref ref="CONSOLE"/></root>
  </springProfile>

  <!-- Profils staging/prod : JSON structuré -->
  <springProfile name="!dev">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>tenantId</includeMdcKeyName>
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>correlationId</includeMdcKeyName>
        <customFields>{"service":"docai-backend"}</customFields>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="JSON"/></root>
  </springProfile>
</configuration>

// Format JSON obligatoire (chaque ligne de log) :
// {
//   "timestamp": "2026-05-22T10:00:00.000Z",
//   "level": "INFO",
//   "service": "docai-backend",
//   "traceId": "abc123def456",
//   "spanId": "789xyz",
//   "tenantId": "acme-corp",
//   "correlationId": "uuid-v4",
//   "message": "Document uploaded successfully"
// }

// Masquage PII obligatoire (BR-EMAIL-002 étendu) :
// email    → log.info("Sent to [PII_MASKED]") — jamais l'email réel
// IBAN     → "****" + 4 derniers chiffres
// SIRET    → "[PARTIAL_MASK]"
// nom/prénom → "[PII_MASKED]"

// Niveaux de log obligatoires :
// ERROR : exceptions non récupérables (pipeline arrêté, CB OPEN)
// WARN  : état dégradé récupéré (fallback activé, cache miss, retry)
// INFO  : flux nominal (soumission, classification, extraction, fraude)
// DEBUG : développement local UNIQUEMENT — désactivé staging/prod
```
# Tâche 0.D.2 — Durée : 1 jour
# ═══════════════════════════════════════════════

---

# MODULE 2.B — commons-api

> **Contenu :** `ApiResponse<T>`, `PageMetadata`, `ProblemDetail` RFC 7807, `GlobalExceptionHandler`, `IdempotencyPort`, `IdempotencyFilter`
> **Durée estimée :** 1 jour
> **Skills :** `docai-commons-implement` · `docai-architecture-adr` · `docai-persistance-standards`
> **ADR applicables :** ADR-010 (conventions nommage pagination)

---

## 🔵 speckit-specify — Module 2.B

```
speckit-specify

Module  : Module 2.B — commons-api
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-architecture-adr, docai-persistance-standards

Objectif :
  - ApiResponse<T> : enveloppe standard pour toutes les réponses REST
  - PageMetadata : métadonnées de pagination conformes BR-PAG-001 à 008
  - ProblemDetail : format d'erreur RFC 7807 pour toutes les exceptions
  - GlobalExceptionHandler : gestion centralisée, jamais de stack trace en production
  - IdempotencyPort : interface domaine pour déduplication des requêtes
  - IdempotencyFilter : filtre Spring interceptant X-Idempotency-Key

Composants :

  ApiResponse<T> :
    T data
    PageMetadata pagination (null si non paginé)
    static <T> ApiResponse<T> of(T data)
    static <T> ApiResponse<T> paginated(T data, PageMetadata metadata)

  PageMetadata (BR-PAG-001 à 008) :
    int number (0-based)
    int size
    long totalElements
    int totalPages
    boolean first
    boolean last

  ProblemDetail (RFC 7807) :
    String type         → URL du type d'erreur (ex: /errors/tenant-not-found)
    String title        → "Tenant Not Found"
    int status          → code HTTP
    String detail       → message lisible
    String instance     → endpoint appelé (/v1/documents)
    String traceId      → corrélation logs
    Instant timestamp   → horodatage UTC

  GlobalExceptionHandler (@RestControllerAdvice) :
    TenantNotSetException        → 401
    DocumentNotFoundException    → 404
    QuotaExceededException       → 429
    ValidationException          → 400
    Exception (catch-all)        → 500 (sans stack trace en production)

  IdempotencyPort (dans docai-domain/port/out/) :
    boolean tryAcquire(String idempotencyKey, Duration ttl)
    Optional<String> getCachedResponse(String idempotencyKey)
    void cacheResponse(String idempotencyKey, String response, Duration ttl)

ADR applicables à ce module :
  ADR-010 : EXPLAIN PLAN sur tout index utilisé par les endpoints paginés
  BR-PAG-001 → 008 : pagination standardisée sur tous les endpoints list
```

---

## 🟢 speckit-clarify — Module 2.B

```
speckit-clarify

Clarifie les points suivants du Module 2.B — skill docai-commons-implement :

1. Le champ type du ProblemDetail RFC 7807 pointe-t-il vers une vraie URL
   de documentation ou une chaîne symbolique (ex: /errors/quota-exceeded) ?
2. La pagination (PageMetadata) est-elle 0-based ou 1-based pour le numéro de page ?
   → BR-PAG-001 définit-il un numéro de page à partir de 0 ou de 1 ?
3. L'IdempotencyFilter intercepte-t-il TOUS les endpoints POST/PATCH/PUT
   ou uniquement ceux annotés d'une annotation spécifique ?
4. Le GlobalExceptionHandler produit-il des stack traces en mode DEV ?
   → Important pour le debugging sans exposer les internals en production.
5. Le header X-Idempotency-Key est-il requis sur POST /v1/documents
   ou facultatif (avec TTL 24h si présent) ?
6. Le traceId dans ProblemDetail vient-il de OpenTelemetry (MDC automatique)
   ou est-il généré manuellement dans le GlobalExceptionHandler ?
```

---

## 🟡 speckit-plan — Module 2.B

```
speckit-plan

Génère le plan d'implémentation du Module 2.B — skill docai-commons-implement.

Ordre obligatoire :

ÉTAPE 1 — ApiResponse + PageMetadata (0.25j)
  Record immuable ApiResponse<T> (of, paginated)
  PageMetadata conforme BR-PAG-001 à 008 (number, size, totalElements, totalPages, first, last)
  Tests : sérialisation JSON correcte, pagination vide, pagination pleine

ÉTAPE 2 — ProblemDetail RFC 7807 (0.25j)
  Record immuable (type, title, status, detail, instance, traceId, timestamp)
  Jamais de stack trace dans le payload JSON
  Tests : sérialisation conforme RFC 7807, Content-Type: application/problem+json

ÉTAPE 3 — GlobalExceptionHandler (0.25j)
  @RestControllerAdvice dans docai-adapter-in-rest
  Mapping de chaque exception métier → ProblemDetail + code HTTP correct
  Catch-all Exception → 500 sans stack trace en production
  Tests : chaque exception → code HTTP correct + body RFC 7807

ÉTAPE 4 — IdempotencyPort + IdempotencyFilter (0.25j)
  IdempotencyPort interface dans docai-domain/port/out/
  IdempotencyFilter @Order(2) — après TenantJwtFilter
  Vérifier X-Idempotency-Key → retourner la réponse mise en cache si déjà traitée
  TTL 24h avec jitter ±10% (ADR-003)
  Tests : 2 requêtes identiques → 1 seul traitement, même réponse
```

---

## 🟠 speckit-checklist — Module 2.B

```
speckit-checklist

Génère la checklist complète du Module 2.B — skill docai-commons-implement.

API RESPONSE :
  - [ ] ApiResponse.of(data) → { data: ..., pagination: null }
  - [ ] ApiResponse.paginated(data, meta) → { data: ..., pagination: { ... } }
  - [ ] Sérialisation JSON correcte (pas de champs null dans pagination de list)
  - [ ] Content-Type: application/json sur toutes les réponses success

PAGINATION BR-PAG :
  - [ ] PageMetadata conforme BR-PAG-001 (number 0-based, size, totalElements, totalPages)
  - [ ] first = (number == 0)
  - [ ] last = (number == totalPages - 1)
  - [ ] Taille de page par défaut : 20 (BR-PAG-001)
  - [ ] Taille de page max : 100 (BR-PAG-002)

PROBLEM DETAIL RFC 7807 :
  - [ ] Content-Type: application/problem+json (RFC 7807 obligatoire)
  - [ ] Champ type → URL descriptive (pas "error" ou "unknown")
  - [ ] Champ traceId → corrélation avec les logs Grafana
  - [ ] Champ timestamp → UTC ISO-8601
  - [ ] JAMAIS de stack trace dans le payload JSON en production

GLOBAL EXCEPTION HANDLER :
  - [ ] TenantNotSetException → 401
  - [ ] DocumentNotFoundException → 404
  - [ ] QuotaExceededException → 429 avec header Retry-After
  - [ ] MethodArgumentNotValidException → 400 avec détail des champs invalides
  - [ ] Exception (catch-all) → 500 SANS message d'implémentation exposé

IDEMPOTENCY :
  - [ ] IdempotencyPort dans docai-domain/port/out/ — interface pure
  - [ ] IdempotencyFilter @Order(2) — après TenantJwtFilter @Order(1)
  - [ ] X-Idempotency-Key présent → TTL 24h + jitter ±10% (ADR-003)
  - [ ] Requête dupliquée → même réponse HTTP (même code, même body)
  - [ ] Requête dupliquée → handle() du use case appelé 1× seulement

DEFINITION OF DONE :
  - [ ] Coverage ≥ 90% sur commons-api
  - [ ] ArchUnit → IdempotencyPort dans domain, 0 violation
```

---

## 🔴 speckit-tasks — Module 2.B

```
speckit-tasks

Découpe le Module 2.B en micro-tâches — skill docai-commons-implement.

TÂCHE 2.B-01 — ApiResponse + PageMetadata + ProblemDetail (0.5j)
  Action  : Records immuables, sérialisation JSON, BR-PAG-001 à 008
  PR      : feat(commons): add ApiResponse PageMetadata ProblemDetail RFC7807
  Critère : sérialisation JSON correcte (test MockMvc)
            Content-Type: application/problem+json pour les erreurs

TÂCHE 2.B-02 — GlobalExceptionHandler + IdempotencyPort + Filter (0.5j)
  Action  : @RestControllerAdvice, mapping exceptions → ProblemDetail
            IdempotencyPort interface + IdempotencyFilter
  PR      : feat(commons): add GlobalExceptionHandler IdempotencyPort Filter
  Critère : chaque exception → code HTTP correct (test @WebMvcTest)
            Requête dupliquée X-Idempotency-Key → 1 seul traitement
```

---

## ⚫ speckit-analyse — Module 2.B

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-commons-implement :

Points à vérifier :

  1. Le GlobalExceptionHandler retourne-t-il un objet Java générique ou ProblemDetail ?
     → Doit retourner ProblemDetail RFC 7807 avec Content-Type: application/problem+json
  2. Y a-t-il un catch Exception → e.printStackTrace() quelque part ?
     → INTERDIT en production — les stack traces ne doivent jamais atteindre le client
  3. La pagination est-elle 0-based (first page = 0) ?
     → BR-PAG-001 impose 0-based — vérifier la valeur de PageMetadata.number
  4. IdempotencyPort est-il dans docai-domain ?
     → Violation ArchUnit si dans docai-adapter-*
  5. Le TTL de l'idempotency key utilise-t-il JitterTtl.withJitter() ?
     → ADR-003 : tout TTL > 1h doit avoir un jitter ±10%

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.B

### Composant 1 — ApiResponse + PageMetadata
```
speckit-implement

Implémente ApiResponse et PageMetadata — skill docai-commons-implement.
Conforme BR-PAG-001 à 008.

public record ApiResponse<T>(
    T data,
    PageMetadata pagination
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }
    public static <T> ApiResponse<T> paginated(T data, PageMetadata pagination) {
        return new ApiResponse<>(data, pagination);
    }
}

public record PageMetadata(
    int number,          // 0-based (BR-PAG-001)
    int size,            // taille de la page (défaut 20, max 100)
    long totalElements,
    int totalPages,
    boolean first,       // number == 0
    boolean last         // number == totalPages - 1
) {
    public static PageMetadata of(Page<?> page) {
        return new PageMetadata(
            page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast()
        );
    }
}
```

### Composant 2 — ProblemDetail RFC 7807 + GlobalExceptionHandler
```
speckit-implement

Implémente ProblemDetail RFC 7807 et GlobalExceptionHandler — skill docai-commons-implement.

public record ProblemDetail(
    String type,         // ex: /errors/quota-exceeded
    String title,
    int status,
    String detail,
    String instance,
    String traceId,
    Instant timestamp
) {}

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantNotSetException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleTenantNotSet(TenantNotSetException ex, HttpServletRequest req) {
        return problem("/errors/unauthorized", "Unauthorized", 401,
            ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(DocumentNotFoundException ex, HttpServletRequest req) {
        return problem("/errors/not-found", "Not Found", 404,
            ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(QuotaExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ProblemDetail handleQuota(QuotaExceededException ex, HttpServletRequest req) {
        return problem("/errors/quota-exceeded", "Quota Exceeded", 429,
            ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return problem("/errors/internal-error", "Internal Server Error", 500,
            "An unexpected error occurred", req.getRequestURI());
        // JAMAIS ex.getMessage() ici — ne pas exposer les internals
    }

    private ProblemDetail problem(String type, String title, int status,
                                  String detail, String instance) {
        return new ProblemDetail(type, title, status, detail, instance,
            MDC.get("traceId"), Instant.now());
    }
}
```

### Composant 3 — Pagination globale BR-PAG-001→008
```
speckit-implement

Implémente la pagination globale dans commons-api — skill docai-persistance-standards.
BR-PAG-001→008 — implémenté UNE SEULE FOIS, jamais réimplémenté (BR-PAG-008).

// ApiResponse avec PageInfo imbriqué
public record ApiResponse<T>(
    List<T> data,
    PageInfo page          // null si réponse non paginée
) {
    public record PageInfo(
        int number,        // 0-based (BR-PAG-001)
        int size,          // taille effective de la page
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
    ) {
        public static PageInfo of(Page<?> page) {
            return new PageInfo(page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
        }
    }
}

// Validation dans chaque Controller (BR-PAG-002/005)
@GetMapping
public ApiResponse<DocumentSummary> list(
    @RequestParam(defaultValue = "0")              int page,
    @RequestParam(defaultValue = "20")             int size,    // BR-PAG-003
    @RequestParam(defaultValue = "createdAt,desc") String sort) {

    if (size > 100) {                                           // BR-PAG-002 + BR-PAG-005
        throw new ValidationException("Maximum page size is 100");
    }
    // Logique paginée via commons-api
}

// Format réponse exact (BR-PAG-004) :
// {
//   "data": [...],
//   "page": {
//     "number": 0,       // BR-PAG-001 : 0-based
//     "size": 20,
//     "totalElements": 1250,
//     "totalPages": 63,
//     "first": true,
//     "last": false
//   }
// }

// EXPLAIN PLAN à valider avant merge (ADR-010) :
// db.documents.find({tenantId:"acme"})
//   .sort({createdAt:-1}).skip(0).limit(20).explain("executionStats")
// → winningPlan.stage = IXSCAN obligatoire
```
# Tâche 0.D.3 — Durée : 1 jour
# ═══════════════════════════════════════════════

---

# MODULE 2.C — commons-audit

> **Contenu :** `AuditEvent`, `AuditPort`, annotation `@Audited`, `AuditAspect` AOP, `AuditMongoAdapter`
> **Durée estimée :** 1 jour
> **Skills :** `docai-commons-implement` · `docai-architecture-adr` · `docai-adapter-mongodb`
> **ADR applicables :** ADR-010 (index audit collection TTL 5 ans)

---

## 🔵 speckit-specify — Module 2.C

```
speckit-specify

Module  : Module 2.C — commons-audit
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-architecture-adr, docai-adapter-mongodb

Objectif :
  - AuditEvent record immuable (id, tenantId, userId, action, resourceType,
    resourceId, metadata, occurredAt, ipAddress, isSupportAccess)
  - AuditPort interface (record async, findByTenant, findByDocument)
  - @Audited annotation (action, resourceType)
  - AuditAspect AOP (intercepte méthodes @Audited, construit AuditEvent, async)
  - AuditMongoAdapter (TTL index 5 ans — immuable — jamais de delete)

Règles absolues :
  - AuditEntry IMMUABLE : jamais de update ni delete (compliance légale)
  - record() ASYNCHRONE : ne bloque JAMAIS le thread appelant
  - TTL index MongoDB : 5 ans (documents_audit_entries collection)
  - isSupportAccess = true si impersonation support client

ADR applicables à ce module :
  ADR-010 : EXPLAIN PLAN sur l'index (tenantId + occurredAt) de audit_entries
```

---

## 🟢 speckit-clarify — Module 2.C

```
speckit-clarify

Clarifie les points suivants du Module 2.C — skill docai-commons-implement :

1. Le @Audited AOP intercepte-t-il uniquement les méthodes de use cases
   ou aussi les méthodes d'adapters ?
2. Le champ metadata de AuditEvent est-il un Map<String, Object> libre
   ou un type structuré par action (ex: DocumentUploadedMetadata) ?
3. L'userId dans AuditEvent est-il le sub JWT Keycloak ou l'email ?
4. L'AuditAspect utilise-t-il @Async de Spring ou CompletableFuture.runAsync() ?
5. isSupportAccess : comment distingue-t-on un accès support d'un accès normal
   dans le JWT ? Claim spécifique ou rôle SYSTEM ?
6. La collection MongoDB d'audit s'appelle-t-elle audit_entries ou
   une autre convention définie dans l'Annex B du SpecKit ?
```

---

## 🟡 speckit-plan — Module 2.C

```
speckit-plan

Génère le plan d'implémentation du Module 2.C — skill docai-commons-implement.

Ordre obligatoire :

ÉTAPE 1 — AuditEvent + AuditPort (0.25j)
  Record immuable AuditEvent (tous les champs — UUID + Instant.now())
  AuditPort interface dans docai-domain/port/out/
  Tests unitaires : création AuditEvent, equals, sérialisation

ÉTAPE 2 — @Audited + AuditAspect (0.5j)
  @Audited(action = "DOCUMENT_UPLOADED", resourceType = "Document")
  AuditAspect @Around : before/after méthode → construire AuditEvent
  Appel AuditPort.record() ASYNCHRONE (@Async ou CompletableFuture)
  Tests : méthode @Audited → AuditEvent créé avec tous les champs
          Vérifier que l'appel AuditPort ne bloque pas le thread (timing)

ÉTAPE 3 — AuditMongoAdapter + Migration Mongock (0.25j)
  Collection audit_entries (snake_case Annex B)
  TTL index : { occurredAt: 1 }, expireAfterSeconds: 157680000 (5 ans)
  Index composé : { tenantId: 1, occurredAt: -1 } (tenantId EN PREMIER — ADR-010)
  Migration Mongock V005_commons_audit_entries_collection
  EXPLAIN PLAN documenté dans la PR (ADR-010)
  Jamais de update/delete — insert only
```

---

## 🟠 speckit-checklist — Module 2.C

```
speckit-checklist

Génère la checklist complète du Module 2.C — skill docai-commons-implement.

AUDIT EVENT :
  - [ ] AuditEvent record immuable (pas de setter)
  - [ ] Champs obligatoires : id (UUID), tenantId, userId, action, occurredAt (Instant)
  - [ ] metadata Map<String, Object> (informations contextuelles)
  - [ ] isSupportAccess distingue l'impersonation support (compliance)

AUDIT PORT :
  - [ ] AuditPort dans docai-domain/port/out/ — interface pure sans Spring
  - [ ] record() asynchrone — NE BLOQUE PAS le thread appelant (vérifié au test)
  - [ ] findByTenant() retourne Page<AuditEvent> (pagination BR-PAG)
  - [ ] findByDocument() retourne List<AuditEvent> (filtré par resourceId)

ASPECT AOP :
  - [ ] @Audited(action, resourceType) applicable sur méthodes use case
  - [ ] AuditAspect extrait le tenantId de TenantContext
  - [ ] AuditAspect extrait le userId du SecurityContextHolder
  - [ ] Appel AuditPort.record() NON-BLOQUANT (test timing < 5ms overhead)

MONGO ADAPTER :
  - [ ] Collection : audit_entries (snake_case Annex B)
  - [ ] Insert only — jamais de update/delete (IMMUABLE)
  - [ ] TTL index : 5 ans (157 680 000 secondes)
  - [ ] Index composé : { tenantId: 1, occurredAt: -1 } (tenantId EN PREMIER — ADR-010)
  - [ ] EXPLAIN PLAN documenté dans la PR de migration (ADR-010)
  - [ ] Migration Mongock V00X avec @RollbackExecution

DEFINITION OF DONE :
  - [ ] Coverage ≥ 90% sur commons-audit
  - [ ] Test : méthode @Audited → AuditEvent créé en base avec tous les champs corrects
  - [ ] Test : overhead @Audited < 5ms (asynchrone vérifié)
```

---

## 🔴 speckit-tasks — Module 2.C

```
speckit-tasks

Découpe le Module 2.C en micro-tâches — skill docai-commons-implement.

TÂCHE 2.C-01 — AuditEvent + AuditPort + @Audited + AuditAspect (0.75j)
  Action  : Record immuable, interface port, annotation AOP, aspect async
  PR      : feat(commons): add AuditEvent AuditPort @Audited async AOP
  Critère : méthode @Audited → AuditEvent créé (test Spring context)
            Overhead < 5ms (async vérifié)
            ArchUnit → AuditPort dans domain, 0 violation

TÂCHE 2.C-02 — AuditMongoAdapter + Migration Mongock (0.25j)
  Action  : Insert-only adapter, TTL index 5 ans, migration V00X
  PR      : feat(commons): add AuditMongoAdapter TTL 5ans Mongock V00X
  Critère : EXPLAIN PLAN { tenantId: 1, occurredAt: -1 } → IXSCAN (ADR-010)
            Tentative de delete → exception (insert-only policy)
```

---

## ⚫ speckit-analyse — Module 2.C

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-commons-implement
et docai-architecture-adr (ADR-010) :

Points à vérifier :

  1. AuditPort est-il dans docai-domain/port/out/ ?
     → Violation ArchUnit Règle 3 si dans docai-adapter-* ou docai-application
  2. AuditEvent record est-il immuable (pas de setter) ?
     → Un AuditEvent avec setter permet la falsification — interdit
  3. L'appel AuditPort.record() est-il asynchrone ?
     → Si appel synchrone dans le thread principal → latence ajoutée sur chaque use case
  4. AuditMongoAdapter a-t-il une méthode delete() ou update() ?
     → INTERDIT — audit trail immuable, insert-only obligatoire (compliance légale)
  5. ADR-010 : l'index { tenantId: 1, occurredAt: -1 } est-il créé via Mongock
     ou via @Indexed dans le code Java ?
     → JAMAIS @Indexed — uniquement Mongock migration (Annex B)
  6. Le TTL index MongoDB est-il de 5 ans (157 680 000 secondes) ?
     → Vérifier dans la migration Mongock : expireAfterSeconds: 157680000
  7. L'AuditAspect intercepte-t-il les méthodes d'adapters ou uniquement de use cases ?
     → Doit cibler uniquement docai-application (use cases) — jamais les adapters

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.C

### Composant 1 — AuditEvent + AuditPort + @Audited
```
speckit-implement

Implémente AuditEvent, AuditPort, @Audited — skill docai-commons-implement.

// domaine — fr.docai.domain.model.audit
public record AuditEvent(
    UUID id,
    String tenantId,
    String userId,
    String action,          // "DOCUMENT_UPLOADED", "FRAUD_REVIEWED"
    String resourceType,    // "Document", "FraudAnalysis"
    String resourceId,
    Map<String, Object> metadata,
    Instant occurredAt,
    String ipAddress,
    boolean isSupportAccess
) {
    public static AuditEvent of(String action, String resourceType,
                                 String resourceId, Map<String, Object> metadata) {
        return new AuditEvent(
            UUID.randomUUID(),
            TenantContext.get(),
            resolveUserId(),
            action, resourceType, resourceId,
            metadata, Instant.now(), null, false
        );
    }
}

// domaine — port/out/
public interface AuditPort {
    void record(AuditEvent event);  // Async — jamais bloquant
    Page<AuditEvent> findByTenant(String tenantId, Pageable pageable);
    List<AuditEvent> findByDocument(String documentId);
}

// annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();        // "DOCUMENT_UPLOADED"
    String resourceType();  // "Document"
}

// aspect AOP — docai-application
@Aspect
@Component
public class AuditAspect {
    private final AuditPort auditPort;
    private final Executor auditExecutor;   // Thread pool dédié @Async

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed();
        CompletableFuture.runAsync(() -> {
            auditPort.record(AuditEvent.of(
                audited.action(), audited.resourceType(),
                extractResourceId(result), Map.of()
            ));
        }, auditExecutor);
        return result;
    }
}
```

### Composant 2 — AuditMongoAdapter + Migration Mongock
```
speckit-implement

Implémente AuditMongoAdapter insert-only + migration Mongock — skill docai-adapter-mongodb.
ADR-010 : EXPLAIN PLAN sur { tenantId: 1, occurredAt: -1 } avant merge.
Annex B : collection audit_entries, snake_case, tenantId EN PREMIER dans l'index.

// fr.docai.adapter.out.mongodb.audit
@Component
public class AuditMongoAdapter implements AuditPort {

    private final MongoTemplate mongoTemplate;

    @Override
    public void record(AuditEvent event) {
        // INSERT ONLY — jamais de update/delete (audit immuable)
        mongoTemplate.insert(toDocument(event), "audit_entries");
    }

    @Override
    public Page<AuditEvent> findByTenant(String tenantId, Pageable pageable) {
        Query query = Query.query(Criteria.where("tenantId").is(tenantId))
            .with(pageable)
            .with(Sort.by(Sort.Direction.DESC, "occurredAt"));
        List<AuditEventDocument> docs = mongoTemplate.find(query, AuditEventDocument.class, "audit_entries");
        long count = mongoTemplate.count(Query.query(Criteria.where("tenantId").is(tenantId)),
            "audit_entries");
        return new PageImpl<>(docs.stream().map(this::toDomain).toList(), pageable, count);
    }

    @Override
    public List<AuditEvent> findByDocument(String documentId) {
        return mongoTemplate.find(
            Query.query(Criteria.where("resourceId").is(documentId)),
            AuditEventDocument.class, "audit_entries"
        ).stream().map(this::toDomain).toList();
    }
}

// Migration Mongock (Annex B — nommage V{NNN}_{module}_{description})
@ChangeUnit(id = "V005_commons_audit_entries_collection", order = "005",
    author = "docai-team")
public class V005CreateAuditEntriesCollection {

    @Execution
    public void createCollection(MongoDatabase db) {
        // Créer la collection si elle n'existe pas
        if (!collectionExists(db, "audit_entries")) {
            db.createCollection("audit_entries");
        }

        // TTL index : 5 ans = 157 680 000 secondes (Annex B)
        db.getCollection("audit_entries").createIndex(
            new Document("occurredAt", 1),
            new IndexOptions().expireAfter(157_680_000L, TimeUnit.SECONDS)
                .name("idx_audit_ttl_5ans")
        );

        // Index composé : tenantId EN PREMIER (ADR-010 + Annex B)
        // EXPLAIN PLAN vérifié : winningPlan.stage = IXSCAN sur findByTenant()
        db.getCollection("audit_entries").createIndex(
            new Document("tenantId", 1).append("occurredAt", -1),
            new IndexOptions().name("idx_audit_tenant_date")
        );
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        db.getCollection("audit_entries").dropIndex("idx_audit_tenant_date");
        db.getCollection("audit_entries").dropIndex("idx_audit_ttl_5ans");
    }
}

// EXPLAIN PLAN à documenter dans la PR (ADR-010) :
// db.audit_entries.find({ tenantId: "acme-corp" })
//   .sort({ occurredAt: -1 }).explain("executionStats")
// → winningPlan.stage doit être "IXSCAN" — jamais "COLLSCAN"
```
# Tâche 0.D.4 — Durée : 2 jours
# ═══════════════════════════════════════════════

---

# MODULE 2.D — commons-outbox

> **Contenu :** `OutboxMessage`, `OutboxStatus`, `OutboxRepository`, `OutboxEventPublisher`, `OutboxMongoAdapter`, `OutboxRelayScheduler`
> **Durée estimée :** 2 jours
> **Skills :** `docai-commons-implement` · `docai-adapter-mongodb` · `docai-adapter-kafka`
> **ADR applicables :** ADR-002 (partitionKey = documentId pour pipeline)

---

## 🔵 speckit-specify — Module 2.D

```
speckit-specify

Module  : Module 2.D — commons-outbox
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-adapter-mongodb, docai-adapter-kafka

Objectif :
  - OutboxMessage record (id, aggregateType, aggregateId, eventType,
    payload, tenantId, partitionKey, createdAt, status)
  - OutboxStatus enum (PENDING, PUBLISHED, FAILED)
  - OutboxRepository interface (save, findPending, markPublished,
    markFailed, deletePublishedOlderThan)
  - OutboxEventPublisher interface (publish vers outbox depuis use case)
  - OutboxMongoAdapter (implémentation MongoDB de OutboxRepository)
  - OutboxRelayScheduler (@Scheduled 1s, batch 100, pub Kafka, markPublished)

Règles absolues :
  - Document + OutboxMessage dans la MÊME ClientSession MongoDB (atomique)
  - partitionKey = documentId pour le pipeline (ADR-002)
  - partitionKey = tenantId pour DLQ et failed
  - relay() : si Kafka DOWN → messages restent PENDING, relay réessaie
  - deletePublishedOlderThan(7 jours) : job quotidien de nettoyage

ADR applicables à ce module :
  ADR-002 : partitionKey = documentId (jamais tenantId pour pipeline)
```

---

## 🟢 speckit-clarify — Module 2.D

```
speckit-clarify

Clarifie les points suivants du Module 2.D — skill docai-commons-implement :

1. Le OutboxRelayScheduler utilise-t-il @Scheduled(fixedDelay = 1000)
   ou @Scheduled(fixedRate = 1000) ? (fixedDelay = 1s APRÈS la fin du batch)
2. La taille du batch de relay est-elle configurable dans application.yml
   ou fixée à 100 dans le code ?
3. En cas d'échec Kafka dans relay() : status → FAILED immédiatement
   ou retry avec backoff avant FAILED ?
4. Le topic de publication dans relay() est-il déduit de eventType
   (ex: DocumentUploaded → docai.doc.uploaded) ou stocké dans OutboxMessage ?
5. deletePublishedOlderThan() est-il un @Scheduled séparé (quotidien)
   ou inclus dans le relay() après chaque publication ?
6. La transaction MongoDB (ClientSession) est-elle gérée dans OutboxMongoAdapter
   ou dans le use case via @Transactional ?
```

---

## 🟡 speckit-plan — Module 2.D

```
speckit-plan

Génère le plan d'implémentation du Module 2.D — skill docai-commons-implement.
Respecte ADR-002 (partitionKey = documentId).

Ordre obligatoire :

ÉTAPE 1 — OutboxMessage + OutboxStatus + OutboxRepository (0.5j)
  Record immuable OutboxMessage avec partitionKey (ADR-002)
  OutboxStatus enum : PENDING, PUBLISHED, FAILED
  OutboxRepository interface dans docai-domain/port/out/
  OutboxEventPublisher interface dans docai-domain/port/out/
  Tests unitaires : création OutboxMessage, status transitions

ÉTAPE 2 — OutboxMongoAdapter (0.75j)
  Collection outbox_events (snake_case Annex B)
  save() : ATOMIQUE dans la même ClientSession que l'aggregate (ADR)
  findPending() : ORDER BY createdAt ASC, LIMIT batch_size
  markPublished() : status PENDING → PUBLISHED (update atomique)
  markFailed() : status → FAILED, incrémenter attempts
  Index : { tenantId: 1, status: 1, createdAt: 1 } (EXPLAIN PLAN ADR-010)
  Migration Mongock V002_commons_outbox_events_collection

ÉTAPE 3 — OutboxRelayScheduler (0.75j)
  @Scheduled(fixedDelay = 1000) — 1s après la fin du batch précédent
  findPending(100) → publier sur Kafka → markPublished()
  Si Kafka exception → markFailed() — message restera PENDING ou FAILED
  Topic déduit de eventType mapping (DocumentUploaded → docai.doc.uploaded)
  deletePublishedOlderThan(Instant.now().minus(7, DAYS)) → job quotidien séparé
  Tests intégration : PENDING → Kafka publié → PUBLISHED (TestContainers)
```

---

## 🟠 speckit-checklist — Module 2.D

```
speckit-checklist

Génère la checklist complète du Module 2.D — skill docai-commons-implement.
ADR vérifiés : ADR-002.

OUTBOX MESSAGE :
  - [ ] ADR-002 ✅ : partitionKey = documentId pour pipeline
  - [ ] ADR-002 ✅ : partitionKey = tenantId pour failed et DLQ
  - [ ] OutboxMessage record immuable (pas de setter)
  - [ ] payload = JSON sérialisé de l'event (String — pas d'Object)

OUTBOX REPOSITORY :
  - [ ] OutboxRepository dans docai-domain/port/out/ — interface pure
  - [ ] OutboxEventPublisher dans docai-domain/port/out/ — interface pure
  - [ ] findPending() trié par createdAt ASC (ordre FIFO garanti)
  - [ ] deletePublishedOlderThan() → nettoyage quotidien (pas de croissance infinie)

MONGO ADAPTER :
  - [ ] save() ATOMIQUE : Document + OutboxMessage dans même ClientSession
  - [ ] Collection : outbox_events (snake_case Annex B)
  - [ ] Index { tenantId: 1, status: 1, createdAt: 1 } (ADR-010 EXPLAIN PLAN)
  - [ ] EXPLAIN PLAN → IXSCAN sur findPending() (jamais COLLSCAN)
  - [ ] Migration Mongock V00X avec @RollbackExecution

RELAY SCHEDULER :
  - [ ] @Scheduled(fixedDelay = 1000) — pas fixedRate (évite l'accumulation)
  - [ ] Batch 100 messages max par cycle
  - [ ] Kafka DOWN → messages restent PENDING — relay réessaie au prochain cycle
  - [ ] markPublished() atomique (pas de race condition entre 2 relays)
  - [ ] Cleanup : deletePublishedOlderThan(7 jours) — job @Scheduled quotidien séparé

DEFINITION OF DONE :
  - [ ] Coverage ≥ 90% sur commons-outbox
  - [ ] Test intégration : PENDING → Kafka publié → PUBLISHED (TestContainers)
  - [ ] Test atomicité : Exception après save(Document) → OutboxMessage aussi rollbacké
  - [ ] Test resilience : Kafka simulé DOWN → messages restent PENDING → relay reprend
```

---

## 🔴 speckit-tasks — Module 2.D

```
speckit-tasks

Découpe le Module 2.D en micro-tâches — skill docai-commons-implement.

TÂCHE 2.D-01 — OutboxMessage + OutboxRepository + OutboxEventPublisher (0.5j)
  Action  : Records, enums, interfaces domaine
  PR      : feat(commons): add OutboxMessage OutboxRepository OutboxEventPublisher
  Critère : ArchUnit → interfaces dans domain, 0 violation
            Coverage ≥ 90% sur records/enums

TÂCHE 2.D-02 — OutboxMongoAdapter + Migration Mongock (0.75j)
  Action  : Implémentation MongoDB, transaction ClientSession, index, Mongock V00X
  PR      : feat(commons): add OutboxMongoAdapter transaction Mongock VXXX
  Critère : Transaction atomique testée (abort → rollback vérifié)
            EXPLAIN PLAN → IXSCAN sur findPending() documenté dans PR

TÂCHE 2.D-03 — OutboxRelayScheduler (0.75j)
  Action  : @Scheduled 1s, batch 100, pub Kafka, markPublished, cleanup 7j
  PR      : feat(commons): add OutboxRelayScheduler Kafka relay cleanup
  Critère : Test intégration PENDING → PUBLISHED (TestContainers Kafka)
            Test Kafka DOWN → PENDING → relay reprend quand Kafka UP
```

---

## ⚫ speckit-analyse — Module 2.D

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-commons-implement
et docai-architecture-adr (ADR-002) :

Points à vérifier :

  1. ADR-002 : le partitionKey est-il documentId pour les topics pipeline ?
     → Si partitionKey = tenantId sur docai.doc.uploaded → violation ADR-002 critique
  2. La transaction MongoDB est-elle atomique (Document + OutboxMessage) ?
     → Si save(Document) et save(OutboxMessage) dans 2 sessions séparées
       → risque de message perdu si crash entre les deux
  3. OutboxRelayScheduler utilise-t-il @Scheduled(fixedDelay) ou fixedRate ?
     → fixedRate peut provoquer l'accumulation de batch si le précédent n'est pas terminé
     → fixedDelay = 1s APRÈS la fin du batch — obligatoire
  4. Le relay() publie-t-il sur Kafka de façon synchrone (get()) ou asynchrone ?
     → Asynchrone sans get() → risque de markPublished() avant la confirmation Kafka
     → Doit être synchrone : kafkaTemplate.send(...).get()
  5. OutboxRepository est-il dans docai-domain/port/out/ ?
     → Violation ArchUnit si dans docai-adapter-*
  6. deletePublishedOlderThan() est-il un job séparé ou dans le relay() ?
     → Doit être un @Scheduled séparé (cron quotidien) pour ne pas ralentir le relay

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.D

### Composant 1 — OutboxMessage + Interfaces
```
speckit-implement

Implémente OutboxMessage et interfaces — skill docai-commons-implement.
ADR-002 : partitionKey = documentId pour le pipeline.

// fr.docai.domain.model.outbox
public record OutboxMessage(
    UUID id,
    String aggregateType,   // "Document", "Tenant", "Subscription"
    String aggregateId,
    String eventType,       // "DocumentUploaded", "DocumentClassified"
    String payload,         // JSON sérialisé — jamais Object directement
    String tenantId,
    String partitionKey,    // ADR-002 : documentId (pipeline) ou tenantId (DLQ)
    Instant createdAt,
    OutboxStatus status
) {}

public enum OutboxStatus { PENDING, PUBLISHED, FAILED }

// fr.docai.domain.port.out
public interface OutboxRepository {
    void save(OutboxMessage message);
    List<OutboxMessage> findPending(int batchSize);           // ORDER BY createdAt ASC
    void markPublished(UUID messageId);
    void markFailed(UUID messageId, String reason, int attempts);
    void deletePublishedOlderThan(Instant before);
}

public interface OutboxEventPublisher {
    void publish(String aggregateType, String aggregateId,
                 String eventType, Object payload,
                 String tenantId, String partitionKey);      // ADR-002
}
```

### Composant 2 — OutboxRelayScheduler
```
speckit-implement

Implémente OutboxRelayScheduler — skill docai-commons-implement.
ADR-002 : clé Kafka = partitionKey du OutboxMessage.

@Component
@ConditionalOnProperty(name = "docai.outbox.relay.enabled", havingValue = "true",
    matchIfMissing = true)
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)  // 1s après la fin du batch précédent
    public void relay() {
        List<OutboxMessage> pending = outboxRepository.findPending(BATCH_SIZE);
        for (OutboxMessage msg : pending) {
            try {
                String topic = resolveTopicFrom(msg.eventType());
                kafkaTemplate.send(topic, msg.partitionKey(), msg.payload()).get(); // sync
                outboxRepository.markPublished(msg.id());
            } catch (Exception e) {
                log.error("Outbox relay failed for {}: {}", msg.id(), e.getMessage());
                outboxRepository.markFailed(msg.id(), e.getMessage(), 1);
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *")  // 2h du matin chaque jour
    public void cleanup() {
        outboxRepository.deletePublishedOlderThan(Instant.now().minus(7, ChronoUnit.DAYS));
    }

    private String resolveTopicFrom(String eventType) {
        return switch (eventType) {
            case "DocumentUploaded"     -> "docai.doc.uploaded";
            case "DocumentClassified"   -> "docai.doc.classified";
            case "DocumentExtracted"    -> "docai.doc.extracted";
            case "FraudAnalyzed"        -> "docai.doc.fraud.analyzed";
            case "DocumentCompleted"    -> "docai.doc.completed";
            default -> "docai.outbox.relay";
        };
    }
}
```

### Composant 3 — Schéma Avro OutboxRelayEvent (ADR-002)
```
speckit-implement

Crée le schéma Avro pour le relay Outbox — skill docai-adapter-kafka.
Enregistré dans Apicurio Registry 2.6 (jamais Confluent Schema Registry).
ADR-002 : partitionKey = documentId pour pipeline, tenantId pour DLQ.

// docai-adapter-out-kafka/src/main/avro/outbox-relay-event.avsc
{
  "namespace": "fr.docai.kafka.avro",
  "type": "record",
  "name": "OutboxRelayEvent",
  "doc": "Event publié par l'OutboxRelayScheduler vers les topics pipeline",
  "fields": [
    {"name": "eventId",       "type": "string",  "doc": "UUID du OutboxMessage"},
    {"name": "aggregateType", "type": "string",  "doc": "Document, Tenant, Subscription"},
    {"name": "aggregateId",   "type": "string",  "doc": "ID de l'aggregate source"},
    {"name": "eventType",     "type": "string",  "doc": "DocumentUploaded, DocumentClassified..."},
    {"name": "payload",       "type": "string",  "doc": "JSON sérialisé de l'event métier"},
    {"name": "tenantId",      "type": "string",  "doc": "Isolement multi-tenant"},
    {"name": "partitionKey",  "type": "string",  "doc": "ADR-002: documentId (pipeline) ou tenantId (DLQ)"},
    {"name": "occurredAt",    "type": "string",  "doc": "ISO-8601 UTC — Instant.toString()"},
    {"name": "schemaVersion", "type": "string",  "default": "1.0.0"}
  ]
}

// Headers Kafka obligatoires sur chaque ProducerRecord :
// tenant-id      → TenantContext.get()
// correlation-id → UUID.randomUUID().toString()
// event-type     → msg.eventType()
// trace-id       → MDC.get("traceId")
// schema-version → "1.0.0"

// Enregistrement dans Apicurio Registry :
// POST http://localhost:8081/apis/registry/v2/groups/docai/artifacts
// Content-Type: application/json; artifactType=AVRO
// X-Registry-ArtifactId: outbox-relay-event
```

### Composant 4 — Mongock V001 (première migration documents)
```
speckit-implement

Implémente la première migration Mongock DocAI — skill docai-persistance-standards.
ADR-010 : tenantId EN PREMIER dans tous les index composés.
BR-MIG-001→007 : règles toutes respectées.

@ChangeUnit(id = "V001_setup_documents_collection",
            order = "001", author = "docai-team")
public class V001SetupDocumentsCollection {

  @Execution
  public void execute(MongoDatabase db) {
    MongoCollection<Document> col = db.getCollection("documents");

    // Index 1 — tenantId EN PREMIER (ADR-010) : liste filtrée par statut
    col.createIndex(Indexes.ascending("tenantId", "status", "createdAt"),
      new IndexOptions().name("idx_tenant_status_created"));

    // Index 2 — tenantId EN PREMIER : liste filtrée par type
    col.createIndex(Indexes.ascending("tenantId", "type", "createdAt"),
      new IndexOptions().name("idx_tenant_type_created"));

    // Index 3 — tenantId EN PREMIER : pagination chronologique
    col.createIndex(Indexes.ascending("tenantId", "createdAt"),
      new IndexOptions().name("idx_tenant_created"));

    // Index 4 — Unique : déduplication upload SHA-256
    col.createIndex(Indexes.ascending("contentHash", "tenantId"),
      new IndexOptions().unique(true).name("idx_content_hash_unique"));

    // Index 5 — Unique sparse : idempotence X-Idempotency-Key
    col.createIndex(Indexes.ascending("idempotencyKey"),
      new IndexOptions().unique(true).sparse(true).name("idx_idempotency_unique"));
  }

  @RollbackExecution  // BR-MIG-004 : toujours présent
  public void rollback(MongoDatabase db) {
    db.getCollection("documents").drop();
  }
}

// EXPLAIN PLAN à documenter dans la PR (ADR-010) :
// db.documents.find({tenantId:"acme", status:"PENDING"}).explain("executionStats")
// → winningPlan.stage doit être "IXSCAN" — jamais "COLLSCAN"
```

---
---


---
---

# ═══════════════════════════════════════════════
# Tâche 0.D.5 — Durée : 1 jour
# ═══════════════════════════════════════════════

---

# MODULE 2.E — commons-quota

> **Contenu :** `QuotaStatus`, `QuotaCheckResult`, `QuotaPort`, `@QuotaProtected`, `QuotaAspect`, `ValkeyQuotaAdapter` (script Lua atomique)
> **Durée estimée :** 1 jour
> **Skills :** `docai-commons-implement` · `docai-adapter-valkey` · `docai-architecture-adr`
> **ADR applicables :** ADR-001 (script Lua atomique — CRITIQUE)

---

## 🔵 speckit-specify — Module 2.E

```
speckit-specify

Module  : Module 2.E — commons-quota
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-adapter-valkey, docai-architecture-adr

Objectif :
  - QuotaStatus enum (ALLOWED, QUOTA_WARNING_80, QUOTA_WARNING_95, QUOTA_EXCEEDED)
  - QuotaCheckResult record (status, currentUsage, limit, remaining, resetAt)
  - QuotaPort interface (checkAndConsume atomique, getCurrentUsage, reset)
  - @QuotaProtected annotation (amount, resource)
  - QuotaAspect AOP (intercepte @QuotaProtected, appelle QuotaPort)
  - ValkeyQuotaAdapter (script Lua ATOMIQUE INCR + comparaison en une opération)

Règle CRITIQUE ADR-001 :
  JAMAIS de GET puis INCR séparés — race condition garantie sous charge.
  Script Lua Valkey : INCR la clé, COMPARER au quota en une seule opération atomique.
  Clé Valkey : quota:{tenantId}:documents:{YYYY-MM}

  Script Lua (exécuté atomiquement) :
    local current = redis.call('INCR', KEYS[1])
    if current == 1 then
      redis.call('EXPIREAT', KEYS[1], ARGV[2])  -- expire 1er du mois suivant
    end
    return current

ADR applicables à ce module :
  ADR-001 ✅ CRITIQUE : script Lua atomique — jamais GET puis INCR séparés
```

---

## 🟢 speckit-clarify — Module 2.E

```
speckit-clarify

Clarifie les points suivants du Module 2.E — skill docai-commons-implement :

1. Les limites par plan (Starter: 500, Pro: 10k, Enterprise: illimité)
   sont-elles stockées dans application.yml, dans MongoDB (Subscription),
   ou dans Valkey à côté du compteur ?
2. La réinitialisation mensuelle (reset le 1er du mois) est-elle gérée
   par un TTL Valkey (EXPIREAT au 1er du mois suivant)
   ou par un job @Scheduled QuotaResetScheduler ?
3. QUOTA_WARNING_80 et QUOTA_WARNING_95 déclenchent-ils un événement Kafka
   ou une notification directement dans checkAndConsume() ?
4. Le @QuotaProtected AOP lève-t-il QuotaExceededException immédiatement
   ou retourne-t-il un résultat spécial (status QUOTA_EXCEEDED dans QuotaCheckResult) ?
5. En cas d'erreur Valkey (DOWN) dans checkAndConsume() : fail-open (on autorise)
   ou fail-closed (on bloque) ? — important pour la disponibilité du service
```

---

## 🟡 speckit-plan — Module 2.E

```
speckit-plan

Génère le plan d'implémentation du Module 2.E — skill docai-commons-implement.
CRITIQUE : ADR-001 (script Lua atomique — jamais GET puis INCR séparés).

Ordre obligatoire :

ÉTAPE 1 — QuotaStatus + QuotaCheckResult + QuotaPort (0.25j)
  QuotaStatus : ALLOWED, QUOTA_WARNING_80, QUOTA_WARNING_95, QUOTA_EXCEEDED
  QuotaCheckResult record (status, currentUsage, limit, remaining, resetAt)
  QuotaPort interface dans docai-domain/port/out/
  @QuotaProtected annotation (amount = 1, resource = "documents")
  Tests unitaires : transitions de status

ÉTAPE 2 — ValkeyQuotaAdapter avec script Lua (ADR-001) (0.5j)
  Script Lua : INCR + EXPIREAT atomique (une seule opération Redis)
  Clé : quota:{tenantId}:documents:{YYYY-MM}
  EXPIREAT calculé = 1er du mois suivant à 00:00:00 UTC
  Calcul du status : 80% → WARNING_80, 95% → WARNING_95, 100% → EXCEEDED
  Fail-open si Valkey DOWN (ALLOWED avec log d'erreur)
  Tests : race condition 100 threads simultanés → quota exact respecté (ADR-001)

ÉTAPE 3 — QuotaAspect AOP (0.25j)
  @Around("@annotation(quotaProtected)")
  Appelle QuotaPort.checkAndConsume(tenantId, amount)
  Si QUOTA_EXCEEDED → lève QuotaExceededException (interceptée par GlobalExceptionHandler)
  Tests : @QuotaProtected → use case appelé SI quota OK, exception SI dépassé
```

---

## 🟠 speckit-checklist — Module 2.E

```
speckit-checklist

Génère la checklist complète du Module 2.E — skill docai-commons-implement.
ADR vérifiés : ADR-001 CRITIQUE.

QUOTA DOMAIN :
  - [ ] QuotaPort dans docai-domain/port/out/ — interface pure sans Spring
  - [ ] QuotaStatus : 4 états (ALLOWED, WARNING_80, WARNING_95, EXCEEDED)
  - [ ] QuotaCheckResult : currentUsage, limit, remaining, resetAt
  - [ ] @QuotaProtected annotation compilable sans Spring (RetentionPolicy.RUNTIME)

VALKEY ADAPTER — ADR-001 :
  - [ ] ADR-001 ✅ CRITIQUE : script Lua ATOMIQUE — jamais GET puis INCR séparés
  - [ ] Script Lua exécuté via RedisScript dans ValkeyQuotaAdapter
  - [ ] Clé Valkey : quota:{tenantId}:documents:{YYYY-MM}
  - [ ] EXPIREAT = 1er du mois suivant à 00:00:00 UTC (reset automatique)
  - [ ] Fail-open si Valkey DOWN : ALLOWED retourné avec log ERROR
  - [ ] Test race condition : 100 threads → quota exactement respecté (atomicité Lua)

AOP :
  - [ ] QuotaAspect @Around sur @QuotaProtected
  - [ ] EXCEEDED → lève QuotaExceededException → 429 (GlobalExceptionHandler)
  - [ ] WARNING_80 → log + éventuellement événement Kafka (selon clarification)
  - [ ] WARNING_95 → log + éventuellement événement Kafka (selon clarification)

DEFINITION OF DONE :
  - [ ] Coverage ≥ 90% sur commons-quota
  - [ ] Test ADR-001 : 100 threads simultanés → quota = exactement la limite définie
  - [ ] Test : Valkey simulé DOWN → ALLOWED (fail-open) avec log ERROR
  - [ ] Test : @QuotaProtected sur use case → EXCEEDED → QuotaExceededException → 429
```

---

## 🔴 speckit-tasks — Module 2.E

```
speckit-tasks

Découpe le Module 2.E en micro-tâches — skill docai-commons-implement.

TÂCHE 2.E-01 — QuotaStatus + QuotaCheckResult + QuotaPort + @QuotaProtected (0.25j)
  Action  : Enum, record, interface, annotation — domaine pur
  PR      : feat(commons): add QuotaPort QuotaStatus QuotaCheckResult @QuotaProtected
  Critère : ArchUnit → QuotaPort dans domain, 0 violation
            Coverage ≥ 90% sur les enums et records

TÂCHE 2.E-02 — ValkeyQuotaAdapter + script Lua ADR-001 + QuotaAspect (0.75j)
  Action  : Script Lua atomique, fail-open, QuotaAspect AOP
  PR      : feat(commons): add ValkeyQuotaAdapter Lua ADR-001 QuotaAspect
  Critère : Test ADR-001 : 100 threads → quota exact (race condition testée)
            Test fail-open : Valkey DOWN → ALLOWED (log ERROR)
            Test AOP : EXCEEDED → 429 (test MockMvc)
```

---

## ⚫ speckit-analyse — Module 2.E

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-commons-implement
et docai-architecture-adr (ADR-001) :

Points à vérifier :

  1. ADR-001 CRITIQUE : le quota est-il incrémenté via un GET puis un INCR séparés ?
     → Race condition garantie sous charge — doit être un script Lua atomique en 1 opération
  2. La clé Valkey suit-elle le format quota:{tenantId}:documents:{YYYY-MM} ?
     → Format différent → reset mensuel cassé ou collisions entre tenants
  3. L'EXPIREAT est-il calculé sur le 1er du mois suivant à 00:00:00 UTC ?
     → Si TTL fixe (ex: 30j) → reset le 30e jour au lieu du 1er du mois
  4. Le fail-open est-il implémenté si Valkey est DOWN ?
     → Si Valkey DOWN → QuotaExceededException → service indisponible
     → Doit retourner ALLOWED avec log ERROR
  5. QuotaPort est-il dans docai-domain/port/out/ ?
     → Violation ArchUnit si dans docai-adapter-*
  6. Le QuotaAspect lève-t-il QuotaExceededException synchronement ?
     → Doit être synchrone — avant d'appeler le use case

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.E

### Composant 1 — Script Lua Atomique (ADR-001)
```
speckit-implement

Implémente ValkeyQuotaAdapter avec script Lua atomique — skill docai-adapter-valkey.
ADR-001 CRITIQUE : jamais GET puis INCR séparés — race condition garantie.

@Component
public class ValkeyQuotaAdapter implements QuotaPort {

    // Script Lua exécuté ATOMIQUEMENT par Valkey
    private static final String LUA_SCRIPT = """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('EXPIREAT', KEYS[1], tonumber(ARGV[1]))
        end
        return current
        """;

    private final RedisTemplate<String, String> redisTemplate;
    private final TenantSubscriptionPort tenantSubscriptionPort; // récupère la limite

    @Override
    public QuotaCheckResult checkAndConsume(String tenantId, int amount) {
        try {
            String key = quotaKey(tenantId);
            long expireAt = firstOfNextMonthEpoch();
            Long current = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
                List.of(key),
                String.valueOf(expireAt)
            );
            long limit = tenantSubscriptionPort.getQuotaLimit(tenantId);
            long used = current != null ? current : 0;
            QuotaStatus status = computeStatus(used, limit);
            return new QuotaCheckResult(status, used, limit, Math.max(0, limit - used),
                firstOfNextMonth());
        } catch (Exception e) {
            log.error("Quota check failed for tenant {} — fail-open: {}", tenantId, e.getMessage());
            return new QuotaCheckResult(QuotaStatus.ALLOWED, 0, Long.MAX_VALUE, Long.MAX_VALUE, null);
        }
    }

    private String quotaKey(String tenantId) {
        return String.format("quota:%s:documents:%s", tenantId,
            YearMonth.now(ZoneOffset.UTC).toString()); // YYYY-MM
    }

    private long firstOfNextMonthEpoch() {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(1)
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private QuotaStatus computeStatus(long used, long limit) {
        if (limit == Long.MAX_VALUE) return QuotaStatus.ALLOWED;  // Enterprise illimité
        if (used >= limit)           return QuotaStatus.QUOTA_EXCEEDED;
        if (used >= limit * 0.95)    return QuotaStatus.QUOTA_WARNING_95;
        if (used >= limit * 0.80)    return QuotaStatus.QUOTA_WARNING_80;
        return QuotaStatus.ALLOWED;
    }
}
```

### Composant 2 — QuotaAspect AOP
```
speckit-implement

Implémente QuotaAspect — skill docai-commons-implement.
Intercepte @QuotaProtected, appelle QuotaPort.checkAndConsume(), lève QuotaExceededException.

@Aspect
@Component
public class QuotaAspect {

    private final QuotaPort quotaPort;
    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(quotaProtected)")
    public Object checkQuota(ProceedingJoinPoint pjp, QuotaProtected quotaProtected)
            throws Throwable {
        String tenantId = TenantContext.get();
        int amount = quotaProtected.amount();

        QuotaCheckResult result = quotaPort.checkAndConsume(tenantId, amount);

        switch (result.status()) {
            case QUOTA_EXCEEDED -> throw new QuotaExceededException(
                String.format("Quota dépassé pour le tenant %s : %d/%d utilisés",
                    tenantId, result.currentUsage(), result.limit()));
            case QUOTA_WARNING_95 -> {
                log.warn("Quota 95% atteint tenant={} used={}/{}", tenantId,
                    result.currentUsage(), result.limit());
                eventPublisher.publishEvent(new QuotaWarningEvent(tenantId, 95, result));
            }
            case QUOTA_WARNING_80 -> {
                log.warn("Quota 80% atteint tenant={} used={}/{}", tenantId,
                    result.currentUsage(), result.limit());
                eventPublisher.publishEvent(new QuotaWarningEvent(tenantId, 80, result));
            }
            default -> {} // ALLOWED — continuer normalement
        }

        return pjp.proceed();
    }
}

// Usage dans un use case :
// @QuotaProtected(amount = 1, resource = "documents")
// @Audited(action = "DOCUMENT_UPLOADED", resourceType = "Document")
// public DocumentUploadedResult upload(UploadDocumentCommand cmd) { ... }
```

---
---


---
---

# ═══════════════════════════════════════════════
# MODULE 2.F — commons-kafka
# Tâche 0.D.6 — Durée : 2 jours
# ═══════════════════════════════════════════════

---

# MODULE 2.F — commons-kafka

> **Contenu :** `ResilientKafkaConsumer<T>`, `KafkaConsumerContext`, `KafkaEventPublisher`, `JitterTtl`
> **Durée estimée :** 2 jours
> **Skills :** `docai-commons-implement` · `docai-adapter-kafka` · `docai-adapter-valkey`
> **ADR applicables :** ADR-002 (partitionKey = documentId), ADR-003 (JitterTtl ±10%)

---

## 🔵 speckit-specify — Module 2.F

```
speckit-specify

Module  : Module 2.F — commons-kafka
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-adapter-kafka, docai-adapter-valkey

Objectif :
  - ResilientKafkaConsumer<T> abstraite (handle à implémenter,
    processWithIdempotence, sendToDlq, isAlreadyProcessed, markAsProcessed)
  - KafkaConsumerContext record (tenantId, correlationId, traceId, attempt)
  - KafkaEventPublisher interface (publishViaOutbox avec partitionKey ADR-002)
  - JitterTtl utilitaire (withJitter ±10%, fixed pour rate limiting et idempotence)

Règles ADR :
  ADR-002 : clé partition Kafka = documentId pour le pipeline (jamais tenantId)
  ADR-003 : JitterTtl.withJitter() sur tout TTL > 1h — jamais TTL fixe

  Clé idempotence Valkey : {topic}:{partition}:{offset}
  TTL clé idempotence : FIXE 24h (ADR-003 : idempotence = TTL fixe, pas de jitter)

Headers Kafka obligatoires sur chaque message :
  tenant-id       (extrait du JWT)
  correlation-id  (UUID généré à l'upload)
  event-type      (ex: DocumentUploaded)
  trace-id        (OpenTelemetry)
  schema-version  (ex: 1.0.0 — Apicurio Registry)

DLQ après 3 échecs :
  Topic DLQ : docai.doc.dlq
  clé DLQ   : tenantId (ADR-002 exception — DLQ partitionné par tenant)
  Payload DLQ : { originalTopic, originalKey, payload, error, failedAt }

ADR applicables à ce module :
  ADR-002 ✅ : partitionKey = documentId (pipeline) / tenantId (DLQ)
  ADR-003 ✅ : JitterTtl.withJitter() sur tout TTL > 1h (sauf rate limiting et idempotence)
```

---

## 🟢 speckit-clarify — Module 2.F

```
speckit-clarify

Clarifie les points suivants du Module 2.F — skill docai-commons-implement :

1. Le nombre de tentatives avant DLQ est-il configurable dans application.yml
   ou fixé à 3 dans ResilientKafkaConsumer ?
2. isAlreadyProcessed() utilise-t-il Valkey SETNX ou GET pour vérifier
   l'idempotence par offset Kafka ?
3. Le TTL de la clé d'idempotence est-il 24h FIXE (ADR-003 exception pour idempotence)
   ou avec jitter ±10% ?
   → ADR-003 précise que l'idempotence ET le rate limiting utilisent TTL fixe
4. Le Consumer Group ID est-il défini dans application.yml ou dans l'annotation
   @KafkaListener de chaque consumer ?
   → Doit être dans application.yml (jamais en dur dans le code)
5. Le KafkaConsumerContext est-il construit en lisant les headers du ConsumerRecord
   ou extrait du JWT présent dans le payload de l'event ?
6. JitterTtl.withJitter() doit-il être utilisé uniquement pour les TTL Valkey
   ou aussi pour les TTL Mongock/cleanup schedulers ?
```

---

## 🟡 speckit-plan — Module 2.F

```
speckit-plan

Génère le plan d'implémentation du Module 2.F — skill docai-commons-implement.
Respecte ADR-002 (documentId partition) et ADR-003 (jitter TTL).

Ordre obligatoire :

ÉTAPE 1 — JitterTtl (0.25j)
  withJitter(Duration baseTtl) → variation ±10% aléatoire
  fixed(Duration ttl) → pas de jitter (rate limiting + idempotence)
  Tests : 100 appels → 100 valeurs différentes dans [0.9 × base, 1.1 × base]
  Tests : fixed() → valeur exacte toujours identique

ÉTAPE 2 — KafkaConsumerContext + Headers (0.5j)
  Record KafkaConsumerContext (tenantId, correlationId, traceId, attempt)
  Extraction des 5 headers Kafka obligatoires
  Validation : tenant-id absent → exception (jamais traiter un event sans tenant)
  Tests unitaires : extraction headers → KafkaConsumerContext correct

ÉTAPE 3 — ResilientKafkaConsumer<T> (0.75j)
  Classe abstraite — handle() à implémenter par chaque consumer
  processWithIdempotence() : vérifier Valkey → appeler handle() → marquer traité
  sendToDlq() : after 3 échecs → publier sur docai.doc.dlq (clé = tenantId ADR-002)
  isAlreadyProcessed() : SETNX Valkey TTL 24h fixe (ADR-003 — idempotence = fixe)
  Tests : event reçu 2× → handle() appelé 1× (idempotence OK)
          3 échecs → DLQ → event sur docai.doc.dlq

ÉTAPE 4 — KafkaEventPublisher interface (0.5j)
  Interface dans docai-domain/port/out/
  publishViaOutbox(topic, partitionKey, payload, tenantId) (ADR-002)
  Implémentation via OutboxEventPublisher (délégation)
  Tests : ArchUnit → interface dans domain, 0 violation
```

---

## 🟠 speckit-checklist — Module 2.F

```
speckit-checklist

Génère la checklist complète du Module 2.F — skill docai-commons-implement.
ADR vérifiés : ADR-002, ADR-003.

JITTER TTL — ADR-003 :
  - [ ] ADR-003 ✅ : JitterTtl.withJitter() sur tout TTL > 1h dans Valkey
  - [ ] JitterTtl.fixed() pour rate limiting et idempotence (TTL fixe — ADR-003 exception)
  - [ ] Test : 100 appels withJitter(24h) → 100 durées différentes dans [21.6h, 26.4h]
  - [ ] JitterTtl.withJitter() utilisé dans commons-kafka pour TTL consumer group

KAFKA CONSUMER CONTEXT :
  - [ ] 5 headers obligatoires : tenant-id, correlation-id, event-type, trace-id, schema-version
  - [ ] tenant-id absent → exception AVANT de traiter (jamais silencieux)
  - [ ] KafkaConsumerContext record immuable

RESILIENT CONSUMER :
  - [ ] ADR-002 ✅ : processWithIdempotence() utilise {topic}:{partition}:{offset} comme clé
  - [ ] ADR-003 ✅ : TTL idempotence = 24h FIXE (JitterTtl.fixed — exception idempotence)
  - [ ] Idempotence : event reçu 2× → handle() appelé 1× seulement
  - [ ] DLQ après 3 échecs → docai.doc.dlq (partitionKey = tenantId — ADR-002 exception)
  - [ ] sendToDlq() : payload DLQ contient originalTopic, originalKey, error, failedAt
  - [ ] Consumer Group IDs définis dans application.yml — JAMAIS en dur dans @KafkaListener

KAFKA EVENT PUBLISHER :
  - [ ] KafkaEventPublisher dans docai-domain/port/out/ — interface pure
  - [ ] ADR-002 ✅ : publishViaOutbox avec partitionKey = documentId (pipeline)
  - [ ] ADR-002 ✅ : partitionKey = tenantId pour DLQ et failed

DEFINITION OF DONE :
  - [ ] Coverage ≥ 90% sur commons-kafka
  - [ ] Test idempotence : event offset duplicate → handle() appelé 1× (Valkey SETNX)
  - [ ] Test DLQ : 3 échecs consécutifs → event sur docai.doc.dlq
  - [ ] Test JitterTtl : 100 TTL différents dans les bornes [0.9×base, 1.1×base]
```

---

## 🔴 speckit-tasks — Module 2.F

```
speckit-tasks

Découpe le Module 2.F en micro-tâches — skill docai-commons-implement.

TÂCHE 2.F-01 — JitterTtl + KafkaConsumerContext (0.5j)
  Action  : JitterTtl (withJitter, fixed), KafkaConsumerContext record + headers
  PR      : feat(commons): add JitterTtl ADR-003 KafkaConsumerContext headers
  Critère : Test 100 TTL différents dans [0.9×base, 1.1×base]
            Test extraction 5 headers → context correct

TÂCHE 2.F-02 — ResilientKafkaConsumer<T> + DLQ (1j)
  Action  : Classe abstraite, processWithIdempotence, sendToDlq après 3 échecs
  PR      : feat(commons): add ResilientKafkaConsumer idempotence DLQ 3 retries
  Critère : Test idempotence : event dupliqué → handle() 1× seulement
            Test DLQ : 3 échecs → event sur docai.doc.dlq avec partitionKey=tenantId

TÂCHE 2.F-03 — KafkaEventPublisher interface (0.5j)
  Action  : Interface domaine + vérification ArchUnit
  PR      : feat(commons): add KafkaEventPublisher ADR-002 interface domain
  Critère : ArchUnit → interface dans domain, 0 violation
            publishViaOutbox partitionKey = documentId (test unitaire)
```

---

## ⚫ speckit-analyse — Module 2.F

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-commons-implement
et docai-architecture-adr (ADR-002, ADR-003) :

Points à vérifier :

  1. ADR-002 : processWithIdempotence() utilise-t-il {topic}:{partition}:{offset} comme clé ?
     → Si clé = messageId ou UUID → idempotence non garantie entre redémarrages
  2. ADR-003 : le TTL de la clé d'idempotence utilise-t-il JitterTtl.fixed() ?
     → L'idempotence doit avoir un TTL FIXE (exception ADR-003) — jamais withJitter()
  3. ADR-003 : les autres TTL Valkey (cache, session) utilisent-ils JitterTtl.withJitter() ?
     → Tout TTL > 1h qui n'est PAS rate limiting ou idempotence → withJitter() obligatoire
  4. ADR-002 : sendToDlq() utilise-t-il tenantId comme partitionKey DLQ ?
     → Pour docai.doc.dlq : partitionKey = tenantId (exception pipeline)
  5. Les Consumer Group IDs sont-ils définis dans application.yml ?
     → JAMAIS en dur dans @KafkaListener(groupId = "...") — violation ADR-002
  6. KafkaEventPublisher est-il dans docai-domain/port/out/ ?
     → Violation ArchUnit si dans docai-adapter-*
  7. Les 5 headers Kafka (tenant-id, correlation-id, event-type, trace-id, schema-version)
     sont-ils propagés sur chaque message publié ?
     → Header absent → consumer ne peut pas construire KafkaConsumerContext

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.F

### Composant 1 — JitterTtl (ADR-003)
```
speckit-implement

Implémente JitterTtl — skill docai-commons-implement.
ADR-003 : tout TTL > 1h doit avoir un jitter ±10% pour éviter les thundering herds.

public final class JitterTtl {
    private JitterTtl() {}

    // ADR-003 : utiliser PARTOUT sauf rate limiting et idempotence
    public static Duration withJitter(Duration baseTtl) {
        double factor = 0.9 + Math.random() * 0.2; // [0.9, 1.1] — ±10%
        return Duration.ofMillis((long) (baseTtl.toMillis() * factor));
    }

    // ADR-003 : TTL fixe pour rate limiting (Bucket4j) et idempotence (Kafka offset)
    public static Duration fixed(Duration ttl) {
        return ttl;
    }
}

// Usages corrects :
// Cache classification LLM (24h) :
//   valkey.set(key, value, JitterTtl.withJitter(Duration.ofHours(24)));
// Idempotence Kafka offset (24h) :
//   valkey.setnx(key, "1", JitterTtl.fixed(Duration.ofHours(24)));
// Rate limiting bucket (1h) :
//   bucket.toBuilder().withCustomRefillStrategy(JitterTtl.fixed(Duration.ofHours(1)));
```

### Composant 2 — ResilientKafkaConsumer<T>
```
speckit-implement

Implémente ResilientKafkaConsumer<T> — skill docai-commons-implement.
ADR-002 : clé idempotence = {topic}:{partition}:{offset}.
ADR-003 : TTL idempotence = 24h FIXE (exception — pas de jitter).

public abstract class ResilientKafkaConsumer<T> {

    private static final int MAX_ATTEMPTS = 3;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // À implémenter dans chaque consumer métier
    public abstract void handle(T event, KafkaConsumerContext context);

    // Appeler dans @KafkaListener — ne PAS surcharger
    protected final void processWithIdempotence(ConsumerRecord<String, T> record) {
        String offsetKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        if (isAlreadyProcessed(offsetKey)) {
            log.debug("Event already processed, skipping: {}", offsetKey);
            return;
        }
        KafkaConsumerContext ctx = buildContext(record);
        TenantContext.set(ctx.tenantId());
        try {
            handle(record.value(), ctx);
            markAsProcessed(offsetKey);
        } catch (Exception e) {
            log.error("Consumer error attempt {}/{}: {}", ctx.attempt(), MAX_ATTEMPTS, e.getMessage());
            if (ctx.attempt() >= MAX_ATTEMPTS) {
                sendToDlq(record, e);
            } else {
                throw e; // laisse Kafka gérer le retry
            }
        } finally {
            TenantContext.clear();
        }
    }

    protected final boolean isAlreadyProcessed(String offsetKey) {
        Boolean absent = redisTemplate.opsForValue()
            .setIfAbsent(offsetKey, "1", JitterTtl.fixed(Duration.ofHours(24))); // ADR-003 fixe
        return Boolean.FALSE.equals(absent); // false = clé existait déjà → déjà traité
    }

    protected final void markAsProcessed(String offsetKey) {
        // Déjà fait dans isAlreadyProcessed via SETNX — rien à faire
    }

    protected final void sendToDlq(ConsumerRecord<String, T> record, Exception cause) {
        String tenantId = record.headers().lastHeader("tenant-id") != null
            ? new String(record.headers().lastHeader("tenant-id").value()) : "unknown";
        String dlqPayload = buildDlqPayload(record, cause);
        kafkaTemplate.send("docai.doc.dlq", tenantId, dlqPayload); // ADR-002 : clé = tenantId pour DLQ
        log.error("Message sent to DLQ for tenant {}: {}", tenantId, cause.getMessage());
    }
}
```

### Composant 3 — 8 Schémas Avro events Kafka (ADR-002)
```
speckit-implement

Crée les 8 schémas Avro — skill docai-adapter-kafka.
Localisation : docai-adapter-out-kafka/src/main/avro/
Namespace : fr.docai.kafka.events — Enregistrement : Apicurio Registry 2.6.

DocumentUploadedEvent.avsc :
  fields : documentId (string), tenantId (string), fileName (string),
           mimeType (string), sizeBytes (long), s3Key (string),
           contentHash (string), uploadedAt (long epoch ms), occurredAt (long)

DocumentClassifiedEvent.avsc :
  fields : documentId, tenantId, documentType (enum CARTE_IDENTITE/PASSEPORT/
           ORDONNANCE/BULLETIN_SALAIRE/JUSTIFICATIF_DOMICILE/KBIS/UNKNOWN),
           confidenceScore (float 0.0-1.0), modelVersion (string), occurredAt (long)

DocumentExtractedEvent.avsc :
  fields : documentId, tenantId, documentType (enum),
           extractedFields (map<string,string>), globalScore (float),
           rawOcrTextS3Key (string — ADR-004), occurredAt (long)

DocumentFraudAnalyzedEvent.avsc :
  fields : documentId, tenantId, fraudScore (int 0-100),
           riskLevel (enum FAIBLE/MODERE/ELEVE/CRITIQUE),
           signals (array<FraudSignal>), occurredAt (long)

DocumentCompletedEvent.avsc :
  fields : documentId, tenantId, finalStatus (enum), occurredAt (long)

DocumentFailedEvent.avsc :
  fields : documentId, tenantId, failureStage (string),
           errorCode (string), occurredAt (long)

DocumentFailedDlqEvent.avsc :
  fields : originalTopic (string), originalKey (string),
           payload (bytes), error (string), failedAt (long)

OutboxRelayEvent.avsc :
  fields : outboxId (string), aggregateId (string), eventType (string),
           payload (bytes), tenantId (string), occurredAt (long)

Convention Apicurio Registry :
  POST http://localhost:8081/apis/registry/v2/groups/docai/artifacts
  Content-Type: application/json; artifactType=AVRO
  X-Registry-ArtifactId: document-uploaded-event (etc.)
  auto.register.schemas: false  # Toujours via Apicurio — jamais auto
```

### Composant 4 — application.yml Resilience4j seuils exacts (BR-VIS-003)
```
speckit-implement

Configure Resilience4j dans application.yml — skill docai-stack-technique.
Seuils exacts pour 8 services. BR-VIS-003 : Tika + OpenCV timeout 15s JAMAIS modifier.

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
        permittedNumberOfCallsInHalfOpenState: 3
      ban:
        failureRateThreshold: 60
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 30s
      rpps:
        failureRateThreshold: 60
        minimumNumberOfCalls: 8
        waitDurationInOpenState: 30s
      s3:
        failureRateThreshold: 50
        minimumNumberOfCalls: 10
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      llm:    { timeoutDuration: 30s }
      ocr:    { timeoutDuration: 60s }
      tika:   { timeoutDuration: 15s }  # BR-VIS-003 — JAMAIS modifier
      opencv: { timeoutDuration: 15s }  # BR-VIS-003 — JAMAIS modifier
      insee:  { timeoutDuration: 5s }
      ban:    { timeoutDuration: 5s }
      rpps:   { timeoutDuration: 5s }
      s3:     { timeoutDuration: 30s }
  retry:
    instances:
      llm:   { maxAttempts: 3, waitDuration: 1s, enableExponentialBackoff: true }
      ocr:   { maxAttempts: 3, waitDuration: 2s }
      insee: { maxAttempts: 2, waitDuration: 2s }
      ban:   { maxAttempts: 2, waitDuration: 2s }
      rpps:  { maxAttempts: 2, waitDuration: 3s }
      tika:  { maxAttempts: 2, waitDuration: 1s }
      s3:    { maxAttempts: 3, waitDuration: 1s, enableExponentialBackoff: true }
  bulkhead:
    instances:
      llm:    { maxConcurrentCalls: 20 }
      ocr:    { maxConcurrentCalls: 10 }
      insee:  { maxConcurrentCalls: 5 }
      ban:    { maxConcurrentCalls: 5 }
      rpps:   { maxConcurrentCalls: 5 }
      tika:   { maxConcurrentCalls: 5 }
      opencv: { maxConcurrentCalls: 5 }
      s3:     { maxConcurrentCalls: 20 }
```
# Tâche 0.D.7 — Durée : 1 jour
# ✅ IMPLÉMENTER EN DERNIER — dépend de tous les autres commons
# ═══════════════════════════════════════════════

---

# MODULE 2.G — commons-testing

> **Contenu :** `AbstractIntegrationTest` (TestContainers reuse), Test Data Builders, WireMock stubs, `KafkaTestHelper`
> **Durée estimée :** 1 jour
> **Skills :** `docai-commons-implement` · `docai-test-integration` · `docai-architecture-adr`
> **ADR applicables :** ADR-008 (reuse TestContainers, -Xmx512m CI)

---

## 🔵 speckit-specify — Module 2.G

```
speckit-specify

Module  : Module 2.G — commons-testing
Partie  : Partie 2 — Commons (Semaines 2–3)
Skills  : docai-commons-implement, docai-test-integration, docai-architecture-adr

Objectif :
  - AbstractIntegrationTest : @SpringBootTest + @Testcontainers
    Conteneurs partagés (mode reuse) : MongoDB, Kafka, Valkey, LocalStack S3
    Démarrage < 60s (ADR-008)
  - Test Data Builders : DocumentTestBuilder, TenantTestBuilder,
    FraudAnalysisTestBuilder, ExtractionResultTestBuilder
  - WireMock stubs : OpenAI, Mistral, INSEE, BAN, RPPS, Keycloak (pour tests)
  - KafkaTestHelper : publish event, await event, assert event (timeout 10s)

Règles ADR-008 :
  TESTCONTAINERS_REUSE_ENABLE=true dans CI (.testcontainers.properties)
  MAVEN_OPTS=-Xmx512m sur tous les jobs CI (ADR-008)
  Conteneurs partagés entre tests → démarrage une seule fois par suite

Conteneurs requis :
  MongoDB 7 Replica Set   (rs0 — requis pour transactions Mongo)
  Kafka 3.7 KRaft         (avec les 8 topics créés automatiquement)
  Valkey 8                (équivalent Redis — PAS de conteneur "redis")
  LocalStack S3           (bucket docai-documents-test créé automatiquement)

WireMock stubs obligatoires :
  OpenAI gpt-4o    → réponse JSON classification fictive (confidence 0.92)
  Mistral          → réponse JSON extraction fictive
  INSEE SIRENE v3  → SIRET valide + SIRET invalide
  BAN Géoplateforme → adresse valide + adresse invalide
  RPPS ANS         → médecin valide + médecin inexistant
  Keycloak         → token valide + token expiré (pour tests d'auth)

ADR applicables à ce module :
  ADR-008 ✅ : TESTCONTAINERS_REUSE_ENABLE=true + MAVEN_OPTS=-Xmx512m
```

---

## 🟢 speckit-clarify — Module 2.G

```
speckit-clarify

Clarifie les points suivants du Module 2.G — skill docai-commons-implement :

1. Les Test Data Builders utilisent-ils le pattern Builder classique
   ou le pattern fluent avec méthodes statiques (DocumentTestBuilder.aDocument()) ?
2. WireMock : les stubs sont-ils définis dans des fichiers JSON sous
   src/test/resources/wiremock/ ou programmatiquement en Java ?
3. KafkaTestHelper.awaitEvent() utilise-t-il Awaitility ou un CompletableFuture
   avec timeout 10s ?
4. AbstractIntegrationTest déclare-t-il les conteneurs comme @Container static
   (partagés entre toutes les méthodes de test d'une classe) ou au niveau de la classe ?
5. Le bucket LocalStack est-il créé automatiquement dans @BeforeAll
   ou dans le conteneur lui-même via un script init ?
6. Les stubs WireMock sont-ils réinitialisés entre chaque test (@BeforeEach)
   ou persistants entre toutes les méthodes ?
```

---

## 🟡 speckit-plan — Module 2.G

```
speckit-plan

Génère le plan d'implémentation du Module 2.G — skill docai-test-integration.
Respecte ADR-008 (reuse TestContainers, Xmx512m CI).

Ordre obligatoire :

ÉTAPE 1 — AbstractIntegrationTest + TestContainers (0.5j)
  @SpringBootTest(webEnvironment = RANDOM_PORT)
  @Testcontainers + @Container static (reuse mode)
  4 conteneurs partagés : MongoDB RS, Kafka KRaft, Valkey, LocalStack
  .testcontainers.properties : testcontainers.reuse.enable=true
  @DynamicPropertySource pour spring.data.mongodb.uri, kafka.bootstrap-servers, etc.
  Création automatique du bucket S3 docai-documents-test dans @BeforeAll
  Démarrage < 60s en mode reuse (mesurer sur CI)

ÉTAPE 2 — Test Data Builders (0.25j)
  DocumentTestBuilder (fluent, valeurs par défaut sensibles)
  TenantTestBuilder (3 tenants préconfigurés : acme-corp PRO, beta-assur STARTER)
  FraudAnalysisTestBuilder
  ExtractionResultTestBuilder
  Tests : builders produisent des entités valides

ÉTAPE 3 — WireMock stubs + KafkaTestHelper (0.25j)
  WireMockServer démarré une fois (@BeforeAll)
  Stubs fichiers JSON : openai_classify_ok, openai_classify_error,
    insee_siret_valide, insee_siret_invalide, ban_adresse_ok, rpps_ok
  KafkaTestHelper : publishEvent(topic, key, payload), awaitEvent(topic, timeout 10s)
  Tests : stub OpenAI → réponse classification (confidence: 0.92)
```

---

## 🟠 speckit-checklist — Module 2.G

```
speckit-checklist

Génère la checklist complète du Module 2.G — skill docai-commons-implement.
ADR vérifiés : ADR-008.

ABSTRACT INTEGRATION TEST :
  - [ ] ADR-008 ✅ : testcontainers.reuse.enable=true dans .testcontainers.properties
  - [ ] ADR-008 ✅ : MAVEN_OPTS=-Xmx512m dans 01-ci.yml (jobs integration + bdd)
  - [ ] 4 conteneurs @Container static (MongoDB RS, Kafka KRaft, Valkey 8, LocalStack)
  - [ ] MongoDB Replica Set initialisé (rs.initiate() dans conteneur init)
  - [ ] Kafka : 8 topics créés automatiquement dans @BeforeAll
  - [ ] LocalStack S3 : bucket docai-documents-test créé dans @BeforeAll
  - [ ] @DynamicPropertySource configure toutes les URLs spring.*
  - [ ] Démarrage < 60s en mode reuse (mesuré sur CI — doit apparaître dans les logs)

TEST DATA BUILDERS :
  - [ ] DocumentTestBuilder (fluent) — valeurs par défaut : tenantId=acme-corp, status=PENDING
  - [ ] TenantTestBuilder — 3 tenants préconfigurés avec plans corrects
  - [ ] FraudAnalysisTestBuilder — score par défaut 25 (non-frauduleux)
  - [ ] ExtractionResultTestBuilder — confidence 0.92 par défaut

WIREMOCK :
  - [ ] Stub OpenAI : réponse classification confidence 0.92
  - [ ] Stub OpenAI : réponse erreur 500 (pour tests Circuit Breaker)
  - [ ] Stub INSEE SIRET : valide + invalide
  - [ ] Stub BAN adresse : valide + invalide
  - [ ] Stub RPPS médecin : valide + inexistant
  - [ ] Stub Keycloak : token valide + token expiré

KAFKA TEST HELPER :
  - [ ] publishEvent(topic, key, payload) avec headers obligatoires
  - [ ] awaitEvent(topic, timeout) avec Awaitility (timeout 10s)
  - [ ] assertEvent(event, expectedType) — vérification du type d'event

DEFINITION OF DONE :
  - [ ] AbstractIntegrationTest.class.getDeclaredMethod("contextLoads") → vert en < 60s
  - [ ] Tous les builders produisent des entités valides (sans erreur de validation)
  - [ ] Tous les stubs WireMock répondent correctement
  - [ ] KafkaTestHelper.awaitEvent → event reçu en < 10s (TestContainers Kafka)
```

---

## 🔴 speckit-tasks — Module 2.G

```
speckit-tasks

Découpe le Module 2.G en micro-tâches — skill docai-test-integration.

TÂCHE 2.G-01 — AbstractIntegrationTest + 4 conteneurs (0.5j)
  Action  : @SpringBootTest, 4 @Container static, @DynamicPropertySource, reuse
  PR      : test(commons): add AbstractIntegrationTest TestContainers reuse ADR-008
  Critère : contextLoads() vert en < 60s sur CI (TESTCONTAINERS_REUSE_ENABLE=true)
            MongoDB RS initialisé, Kafka 8 topics, S3 bucket créé

TÂCHE 2.G-02 — Test Data Builders + WireMock + KafkaTestHelper (0.5j)
  Action  : 4 builders, 10 stubs WireMock, KafkaTestHelper awaitEvent
  PR      : test(commons): add TestBuilders WireMock stubs KafkaTestHelper
  Critère : Stub OpenAI → réponse confidence 0.92 (test MockMvc)
            awaitEvent → event reçu < 10s (test Kafka)
```

---

## ⚫ speckit-analyse — Module 2.G

```
speckit-analyse

Analyse ce code par rapport aux règles du skill docai-test-integration
et docai-architecture-adr (ADR-008) :

Points à vérifier :

  1. ADR-008 : les conteneurs sont-ils @Container static (partagés) ?
     → Si @Container sans static → nouveaux conteneurs créés par test → CI très lente
  2. ADR-008 : TESTCONTAINERS_REUSE_ENABLE=true est-il dans .testcontainers.properties ?
     → Fichier manquant → reuse ignoré → conteneurs recréés à chaque run CI
  3. MongoDB utilise-t-il le mode Replica Set (--replSet rs0) ?
     → Sans Replica Set → transactions MongoDB impossibles → tests d'intégration échouent
  4. Le conteneur Valkey est-il valkey/valkey:8.0 (pas redis:*) ?
     → DocAI utilise Valkey — jamais l'image Redis officielle (stack technique V15.0)
  5. @DynamicPropertySource configure-t-il toutes les URLs Spring ?
     → URL manquante → bean non trouvé → ApplicationContext fail au démarrage
  6. Les Test Data Builders ont-ils des valeurs par défaut cohérentes avec le seeding DEV ?
     → tenantId par défaut = "acme-corp" (aligné avec SeedingService Partie 1)
  7. Les stubs WireMock sont-ils réinitialisés entre les tests si nécessaire ?
     → Stub persistant entre tests → faux positifs difficiles à tracer

[coller le code ici]
```

---

## 🟣 speckit-implement — Module 2.G

### Composant 1 — AbstractIntegrationTest (ADR-008)
```
speckit-implement

Implémente AbstractIntegrationTest — skill docai-test-integration.
ADR-008 : reuse TestContainers + Xmx512m CI.

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static MongoDBContainer MONGODB = new MongoDBContainer("mongo:7.0")
        .withReuse(true)
        .withCommand("--replSet", "rs0");   // Requis pour les transactions

    @Container
    static KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.7.0"))
        .withReuse(true);

    @Container
    static GenericContainer<?> VALKEY = new GenericContainer<>("valkey/valkey:8.0")
        .withReuse(true)
        .withExposedPorts(6379);

    @Container
    static LocalStackContainer LOCALSTACK = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.4"))
        .withServices(LocalStackContainer.Service.S3)
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
            () -> MONGODB.getReplicaSetUrl("docai_test"));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port",
            () -> VALKEY.getMappedPort(6379).toString());
        registry.add("aws.s3.endpoint-url",
            () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());
    }

    @BeforeAll
    static void initContainers() throws Exception {
        // Init MongoDB Replica Set
        MONGODB.execInContainer("mongosh", "--eval", "rs.initiate()");
        Thread.sleep(2000); // attendre l'élection du primary

        // Créer le bucket S3 de test
        S3Client s3 = S3Client.builder()
            .endpointOverride(LOCALSTACK.getEndpointOverride(S3))
            .region(Region.EU_WEST_3)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
        s3.createBucket(r -> r.bucket("docai-documents-test"));
    }
}

# .testcontainers.properties (ADR-008)
testcontainers.reuse.enable=true
```

### Composant 2 — Test Data Builders
```
speckit-implement

Implémente les Test Data Builders — skill docai-commons-implement.
Pattern fluent avec valeurs par défaut sensibles.

public class DocumentTestBuilder {
    private UUID id = UUID.randomUUID();
    private String tenantId = "acme-corp";
    private DocumentStatus status = DocumentStatus.PENDING;
    private DocumentType type = DocumentType.UNKNOWN;
    private String fileName = "test-document.pdf";
    private long fileSize = 1024L;
    private String s3Key = "acme-corp/" + id + "/test-document.pdf";

    public static DocumentTestBuilder aDocument() { return new DocumentTestBuilder(); }
    public DocumentTestBuilder withTenantId(String tenantId) { this.tenantId = tenantId; return this; }
    public DocumentTestBuilder withStatus(DocumentStatus status) { this.status = status; return this; }
    public DocumentTestBuilder withType(DocumentType type) { this.type = type; return this; }

    public Document build() {
        return Document.of(id, tenantId, fileName, fileSize, s3Key, status, type);
    }

    public static DocumentTestBuilder aClassifiedDocument() {
        return aDocument().withStatus(DocumentStatus.CLASSIFIED).withType(DocumentType.CARTE_IDENTITE);
    }
    public static DocumentTestBuilder aFraudulentDocument() {
        return aDocument().withStatus(DocumentStatus.REJECTED);
    }
}
```

---
---


---
---

# CONFIGURATION — application.yml Partie 2 Commons

## application.yml — base commune (tous profils)

```yaml
# ── Valkey (commons-multitenancy + commons-quota + commons-kafka) ─────
spring:
  data:
    redis:
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      timeout: 2s
      lettuce:
        pool:
          max-active: 20
          max-idle: 10

# ── Keycloak JWKS Cache (ADR-006) ─────────────────────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWK_URI}
          jwk-set-cache-duration: 1h        # ADR-006 — OBLIGATOIRE
          jwk-set-cache-refresh-duration: 5m

# ── Kafka (commons-kafka + commons-outbox) ────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all           # Durabilité maximale
      retries: 3
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false  # Commit manuel — idempotence maîtrisée

# ── Consumer Group IDs (ADR-002 — jamais en dur dans @KafkaListener) ──
docai:
  kafka:
    consumer-groups:
      outbox-relay:  docai.outbox.relay.group
      dlq-monitor:   docai.dlq.monitor.group
  outbox:
    relay:
      enabled: true
      batch-size: 100
      cleanup-days: 7
  quota:
    plans:
      free:       0           # FREE : pas de soumissions
      starter:    500         # Starter : 500/mois
      pro:        10000       # Pro : 10 000/mois
      enterprise: 2147483647  # Enterprise : illimité (Integer.MAX_VALUE)
  audit:
    async:
      core-pool-size: 2
      max-pool-size: 5
      queue-capacity: 100
```

## application-dev.yml — profil DEV local

```yaml
# ── Valkey DEV ────────────────────────────────────────────────────────
spring:
  data:
    redis:
      host: localhost
      port: 6379

# ── Kafka DEV ─────────────────────────────────────────────────────────
  kafka:
    bootstrap-servers: localhost:9092

# ── MongoDB DEV (Replica Set local) ───────────────────────────────────
  data:
    mongodb:
      uri: mongodb://localhost:27017/docai_dev?replicaSet=rs0
      auto-index-creation: false   # Annex B — index via Mongock uniquement

# ── Keycloak DEV local ────────────────────────────────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8180/realms/docai/protocol/openid-connect/certs

# ── Quota DEV : limites réduites pour faciliter les tests ─────────────
docai:
  quota:
    plans:
      starter: 50          # 50 en DEV (pas 500) pour tester rapidement le dépassement
```

## application-prod.yml — profil PRODUCTION

```yaml
# ── MongoDB PROD (Atlas M30 — auto-index désactivé Annex B) ──────────
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}
      auto-index-creation: false   # OBLIGATOIRE en production (Annex B)

# ── Keycloak PROD (cluster 2 instances — ADR-006) ────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWK_URI}
          jwk-set-cache-duration: 1h

# ── Kafka PROD (3 brokers RF=3) ───────────────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      retries: 5

# ── Valkey PROD (cluster managé) ─────────────────────────────────────
  data:
    redis:
      host: ${VALKEY_HOST}
      port: ${VALKEY_PORT:6379}
      ssl:
        enabled: true
```

## application.yml — 9 stratégies cache Valkey (ADR-003)

```yaml
# ── Stratégies Cache Valkey (ADR-003 — jitter ±10% sauf exceptions FIXE) ──
docai:
  cache:
    # TTL avec JitterTtl.withJitter() — thundering herd prevention
    extraction-llm:
      ttl: 24h        # → JitterTtl.withJitter(Duration.ofHours(24))
      key: "extraction:{sha256}"
    insee-siret:
      ttl: 7d         # → JitterTtl.withJitter(Duration.ofDays(7))
      key: "insee:siret:{siret}"
    ban-address:
      ttl: 30d        # → JitterTtl.withJitter(Duration.ofDays(30))
      key: "ban:adresse:{hash}"
    rpps:
      ttl: 7d         # → JitterTtl.withJitter(Duration.ofDays(7))
      key: "rpps:{rppsNumber}"
    classification:
      ttl: 1h         # → JitterTtl.withJitter(Duration.ofHours(1))
      key: "classification:{sha256}"

    # TTL FIXE — ADR-003 exceptions (précision obligatoire)
    jwt-blacklist:
      ttl: dynamic    # TTL fixe = durée restante du token (extrait claim exp)
      key: "jwt:blacklist:{jti}"
    kafka-idempotency:
      ttl: 24h        # → JitterTtl.fixed(Duration.ofHours(24))
      key: "idempotent:{topic}:{partition}:{offset}"
    upload-idempotency:
      ttl: 24h        # → JitterTtl.fixed(Duration.ofHours(24))
      key: "idempotency:{X-Idempotency-Key}"
    quota-monthly:
      ttl: monthly    # → EXPIREAT = 1er du mois suivant 00:00:00 UTC
      key: "quota:{tenantId}:documents:{YYYY-MM}"
```

| Service | DEV (local) | STAGING | PRODUCTION |
|---------|-------------|---------|-----------|
| **Valkey** | Docker local :6379 | Cloud managé (1 nœud) | Cloud managé cluster mode |
| **Kafka** | Docker local KRaft :9092 | Cloud 3 brokers RF=3 | Cloud 3 brokers RF=3 |
| **Keycloak JWKS** | http://localhost:8180/realms/docai | Cloud 1 replica | Cluster 2 instances (ADR-006) |
| **MongoDB** | Docker RS local | Atlas M10 3 nœuds | Atlas M30 3 nœuds multi-AZ |
| **Quota Starter** | 50 docs (test rapide) | 500 docs (réel) | 500 docs (réel) |
| **Audit TTL** | 5 ans (même qu'en prod) | 5 ans | 5 ans |
| **Outbox relay** | 1s fixedDelay | 1s fixedDelay | 1s fixedDelay |
| **Secrets** | `.env` local | AWS Secrets Manager | AWS Secrets Manager |

> **Règles absolues DEV** : jamais de vraie clé Keycloak prod, Quota Starter réduit à 50, `.env` jamais commité
> **Règles absolues STAGING** : données jamais copiées depuis PROD (RGPD), Keycloak realm staging séparé
> **Règles absolues PRODUCTION** : tous secrets AWS Secrets Manager, `auto-index-creation: false` vérifié

---

# PR TEMPLATE — Commons (à ajouter dans `.github/pull_request_template.md`)

```markdown
## Description
<!-- Décrire le commons implémenté et les composants ajoutés -->

## Type de changement
- [ ] feat(commons): nouveau composant commons
- [ ] fix(commons): correction commons existant
- [ ] test(commons): ajout/correction tests commons
- [ ] refactor(commons): refactoring sans changement de comportement

## Checklist Architecture (ArchUnit)
- [ ] `./mvnw test -Dtest=HexagonalArchitectureTest` → 12 règles vertes
- [ ] Interfaces/Ports dans `docai-domain/port/out/` (jamais dans adapter-*)
- [ ] Aucun import Spring dans `docai-domain`
- [ ] Aucun import MongoDB/Kafka/Valkey dans `docai-domain`

## Checklist ADR Commons
- [ ] **ADR-001** : `ValkeyQuotaAdapter` utilise script Lua atomique (jamais GET+INCR séparés)
- [ ] **ADR-002** : `partitionKey = documentId` pour pipeline · `tenantId` pour DLQ
- [ ] **ADR-003** : `JitterTtl.withJitter()` sur tout TTL > 1h (sauf rate limiting + idempotence)
- [ ] **ADR-006** : cache JWKS Keycloak `jwk-set-cache-duration: 1h` dans application.yml
- [ ] **ADR-008** : `TESTCONTAINERS_REUSE_ENABLE=true` dans `.testcontainers.properties`
- [ ] **ADR-010** : EXPLAIN PLAN documenté si index MongoDB ajouté → `winningPlan.stage = IXSCAN`

## Checklist Sécurité Commons
- [ ] `TenantContext.clear()` dans un bloc `finally` (jamais oublié)
- [ ] Aucune stack trace exposée dans les réponses d'erreur (`GlobalExceptionHandler`)
- [ ] TTL blacklist JWT = durée de vie restante du token (extrait du claim `exp`)
- [ ] Audit trail : insert-only — aucun `update()` ni `delete()` sur `audit_entries`

## Checklist Tests
- [ ] Coverage ≥ 90% sur le commons modifié
- [ ] PIT Mutation Testing ≥ 85% sur `docai-domain`
- [ ] Test de concurrence (si commons-quota) : 100 threads → quota exact (ADR-001)
- [ ] Test d'idempotence (si commons-kafka) : event dupliqué → `handle()` appelé 1× (ADR-002)
- [ ] Test transaction Outbox : Document + OutboxMessage dans même ClientSession

## Checklist Annex B MongoDB (si migration Mongock)
- [ ] Collection nommée en `snake_case` pluriel : `audit_entries`, `outbox_events`
- [ ] Migration nommée `V{NNN}_{module}_{description}` (ex: `V005_commons_audit_entries`)
- [ ] `@RollbackExecution` présent et testé
- [ ] `tenantId` EN PREMIER dans tout index composé
- [ ] EXPLAIN PLAN documenté dans cette PR (résultat `executionStats`)

## Definition of Done
- [ ] Coverage ≥ 90% sur le commons (rapport JaCoCo vérifié)
- [ ] `./mvnw checkstyle:check` → 0 violation
- [ ] `./mvnw install -pl <commons-modifié>` → BUILD SUCCESS
- [ ] Review par au moins 1 développeur senior
- [ ] Quality Gate SonarCloud → vert
```

---
---


---
---

# VALIDATION FINALE — Partie 2 Commons

## Commandes de validation avant passage en Partie 3

```bash
# Coverage global — chaque commons doit atteindre ≥ 90%
./mvnw verify -pl commons-multitenancy,commons-api,commons-audit,\
  commons-outbox,commons-quota,commons-kafka,commons-testing \
  jacoco:report
# → Vérifier rapport : target/site/jacoco/index.html

# ArchUnit — 12 règles (aucune violation tolérée)
./mvnw test -Dtest=HexagonalArchitectureTest
# → BUILD SUCCESS, 12 tests verts

# PIT Mutation Testing — ≥ 85% sur domaine
./mvnw org.pitest:pitest-maven:mutationCoverage -pl docai-domain
# → mutation coverage ≥ 85%

# ADR-001 — Race condition quota (100 threads)
./mvnw test -Dtest=ValkeyQuotaAdapterRaceConditionTest
# → 100 threads → quota exactement respecté

# ADR-002 — Idempotence Kafka (event dupliqué)
./mvnw test -Dtest=ResilientKafkaConsumerIdempotenceTest
# → event dupliqué → handle() appelé 1× seulement

# ADR-003 — JitterTtl (100 TTL différents)
./mvnw test -Dtest=JitterTtlTest
# → 100 valeurs distinctes dans [0.9×base, 1.1×base]

# ADR-008 — AbstractIntegrationTest démarre en reuse < 60s
./mvnw test -Dtest=AbstractIntegrationTest -pl commons-testing \
  -DTESTCONTAINERS_REUSE_ENABLE=true
# → Context loads en < 60s

# Vérification TenantContext.clear() dans finally
# (analyser statiquement avec ArchUnit ou grep)
grep -r "TenantContext.set" --include="*.java" | \
  grep -v "TenantContext.clear" \
  | head -5
# → 0 résultat (chaque set() est suivi d'un clear() dans finally)

# Publication dans le repo Maven local (pour Partie 3)
./mvnw install -pl commons-multitenancy,commons-api,commons-audit,\
  commons-outbox,commons-quota,commons-kafka,commons-testing
# → BUILD SUCCESS — commons disponibles pour Module 0 (Partie 3)
```

---

## Definition of Ready (DoR) — Tableau officiel 10 critères

```
speckit-implement

Crée la Definition of Ready DocAI — skill docai-architecture-adr.
Annex B MongoDB + ADR-010 intégrés comme critères bloquants.
US bloquée si 1 critère non rempli → reste dans le backlog.

| #  | Critère                                                              | Responsable |
|----|----------------------------------------------------------------------|-------------|
|  1 | US estimée en story points                                           | Équipe dev  |
|  2 | Critères d'acceptance BDD écrits (Given/When/Then)                   | PO / Dev    |
|  3 | Scénarios Gherkin rédigés et validés PO                              | Dev         |
|  4 | ADR applicable identifié (ADR-001/002/003/006/008/010...)            | Dev         |
|  5 | Dépendances inter-modules identifiées (commons publiés en local)      | Dev         |
|  6 | Accès services externes disponibles (Valkey, Kafka, MongoDB up)      | Tech Lead   |
|  7 | EXPLAIN PLAN MongoDB documenté si nouvelle requête (ADR-010 Annex B) | Dev         |
|  8 | Convention nommage collections vérifiée (snake_case pluriel Annex B) | Dev         |
|  9 | Contrats API validés si nouveau endpoint (OpenAPI spec mise à jour)  | Dev / PO    |
| 10 | US tient dans 1 sprint (sinon découper en 2 US max)                  | Tech Lead   |

DoR vérifiée en sprint planning pour chaque US prévue au sprint.
```

---

## sonar-project.properties — Exclusions spécifiques commons

```properties
# sonar-project.properties (racine du projet)
sonar.projectKey=docai_backend
sonar.organization=votre-organisation
sonar.projectName=DocAI Backend
sonar.projectVersion=1.0
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes
sonar.java.test.binaries=target/test-classes
sonar.java.libraries=target/dependency

# Exclusions générales
sonar.exclusions=\
  **/generated/**,\
  **/*MapperImpl.java,\
  **/*Exception.java,\
  **/docai-bootstrap/**

# Exclusions coverage — domaine immuable + events
sonar.coverage.exclusions=\
  **/domain/exception/**,\
  **/*Event.java,\
  **/*Command.java,\
  **/*Query.java,\
  **/domain/model/**

# Exclusions spécifiques commons (records Java 21 — couverture structurelle)
sonar.coverage.exclusions+=\
  **/OutboxMessage.java,\
  **/AuditEvent.java,\
  **/QuotaCheckResult.java,\
  **/KafkaConsumerContext.java,\
  **/PageMetadata.java,\
  **/ProblemDetail.java

# Éviter faux positifs duplication sur records
sonar.cpd.exclusions=**/domain/model/**

# Seuils Quality Gate commons (bloquants — NFR-CI-004/005/007/008)
sonar.qualitygate.wait=true
# → Coverage ≥ 90% commons (domaine critique)
# → 0 bug nouveau code
# → Duplication ≤ 3%
```

---

## ADR Récapitulatif — Partie 2

| ADR | Appliqué dans | Statut |
|-----|---------------|--------|
| ADR-001 Lua atomique | ValkeyQuotaAdapter — script Lua INCR+COMPARE atomique | ✅ Fait |
| ADR-002 Kafka documentId | ResilientKafkaConsumer + KafkaEventPublisher (partitionKey) | ✅ Fait |
| ADR-003 TTL jitter ±10% | JitterTtl.withJitter() — Valkey cache (hors rate limiting + idempotence) | ✅ Fait |
| ADR-004 OCR → S3 | Préparer S3 adapter (Partie 4 Module 1.1) | ⚠️ À venir |
| ADR-005 KMS PII | Module 0.5 RGPD (Partie 3) | ➡️ Plus tard |
| ADR-006 JWKS cache 1h | TenantJwtFilter + application.yml | ✅ Fait |
| ADR-007 AbortMultipart | S3 adapter (Partie 4) | ⚠️ À venir |
| ADR-008 CI Xmx512m | AbstractIntegrationTest + TESTCONTAINERS_REUSE_ENABLE | ✅ Fait |
| ADR-009 Downgrade | Module 7 Billing (Partie 5) | ➡️ Plus tard |
| ADR-010 EXPLAIN PLAN | Index audit_entries + outbox_events documentés dans PR | ✅ Fait |
| ADR-011 lastSyncedAt | Module 5 Dashboard (Partie 5) | ➡️ Plus tard |

---

*DocAI SpecKit — SPECKIT_COMMONS.md*
*MASTER Partie 2 — Commons · Semaines 2–3*
*7 Modules : 2.A multitenancy · 2.B api · 2.C audit · 2.D outbox · 2.E quota · 2.F kafka · 2.G testing*
*⚠️ Ordre implémentation : 2.A → 2.B → 2.C → 2.D → 2.E → 2.F → 2.G*
*10 Skills · ADR-001/002/003/006/008/010 · BR-PAG-001→008 · BR-MIG-001→007 · BR-ARCH-001→004 · BR-ROT-001→004 · BR-DEP-001→002 · Annex B MongoDB · Annex C Secrets*
*7× speckit-specify · clarify · plan · checklist · tasks · analyse · implement*
*35+ corrections cumulées : nommage collections · migrations · BR rules · SOLID · ArchUnit · Ports · Valkey 9 TTL YAML · Consumer Groups · Resilience4j YAML · 8 Avro schemas · Micrometer · Logs JSON · DoR · NFR-CI · GitFlow · sonar-project.properties · Pagination implement · Mongock V001 · QuotaAspect · AuditMongoAdapter · application.yml complet · PR Template · DEV/STAGING/PROD*
