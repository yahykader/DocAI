<!-- 
Sync Impact Report
==================
Version: 1.0.0 → 1.1.0 (MINOR: added ADR section, Annexes, corrected LLM stack)
Modified Principles: I (corrected LLM stack: Claude API → OpenAI GPT-4o / Mistral)
Added Sections: VIII (ADR — 11 decisions), IX (Operational Annexes)
Removed Sections: None
Templates Updated:
  ✅ plan-template.md — ADR references added
  ✅ spec-template.md — ADR compliance check added
  ✅ tasks-template.md — ADR gate added to task criteria
Follow-up: Reference skill docai-architecture-adr for full ADR implementation details

Last Updated: 2026-05-23
-->

# DocAI Constitution
## Specification Backend · Architecture & Code Quality Standards

---

## Core Principles

### I. Hexagonal Architecture (Ports & Adapters)

Every feature flows through the hexagonal boundary: domain → ports → adapters. No infrastructure code leaks into the domain. **Non-negotiable rules:**

- `docai-domain/` contains ONLY Java pure model (entities, value objects, ports, services, events). Zero Spring, MongoDB, Kafka, AWS imports.
- Inbound ports (`docai-adapter-in-rest/`, `docai-adapter-in-kafka/`) translate external protocols (HTTP, Kafka messages) to domain commands.
- Outbound ports (`docai-adapter-out-mongodb/`, `docai-adapter-out-storage/`, etc.) implement domain contracts, never exposing infrastructure details upward.
- Port interfaces live in `docai-domain/src/main/java/fr/docai/domain/port/` (pure, testable signatures).
- ArchUnit enforces this at every commit: any `docai-domain` file importing `org.springframework.*`, `com.mongodb.*`, or `org.apache.kafka.*` fails CI.

**Why:** Technology independence. Swap LLM providers, databases, or message brokers in hours, not weeks. Testability without containers.

---

### II. Domain-Driven Design (DDD) with Bounded Contexts

Domain logic is explicit, named, and organized by business capability. **Non-negotiable rules:**

- Entities and Value Objects model real business concepts (Document, ExtractionResult, FraudScore). No generic "data containers."
- Bounded contexts align with system modules: `document` (recognition, extraction), `fraud` (scoring, analysis), `pipeline` (orchestration), `security` (multi-tenancy, RBAC).
- Domain events (`DocumentRecognized`, `FraudDetected`, `ExtractionCompleted`) capture state changes; published async via Kafka.
- Aggregates enforce invariants and consistency boundaries.
- No shared entities across contexts. Cross-context communication via events and ports, never direct object passing.

**Why:** Maintainability at scale. Clear intent. Compliance-ready (rules are code, auditable, testable).

---

### III. Test-First Development (Non-Negotiable)

Tests drive design and are mandatory before production code. **Non-negotiable rules:**

- **Domain tests (JUnit 5 + Mockito):** 90% coverage minimum. Every entity, value object, domain service has unit tests. Zero Spring, zero containers.
- **Unit test execution:** < 1 second per test file. Mocks injected; real objects only for pure logic.
- **ArchUnit architecture tests:** Verify hexagonal boundaries at compile time. 12 rules enforcing domain isolation, port interfaces, package structure. Run in CI Phase 1 (Build), fail immediately if broken.
- **Integration tests (TestContainers):** MongoDB Replica Set, Kafka, Valkey in Docker; test complete flows end-to-end. ≥ 1 per adapter per public port.
- **Test organization:** `src/test/java/` mirrors `src/main/java/` structure. Test class = `{SourceClass}Test.java`. One responsibility per test method (`testXxx_GivenYyy_ThenZzz`).
- **Coverage gates:** Global ≥ 80%, Domain ≥ 90%, Mutation (PIT) ≥ 85% (domain only).

**Why:** Confidence in changes. Regression prevention. Design clarity. Legacy prevention.

---

### IV. SOLID Principles & Clean Code (Software Craftsman)

Code is written for humans first. **Non-negotiable rules:**

- **Single Responsibility:** One class, one reason to change. Method: one business step max.
- **Open/Closed:** Open for extension (via ports), closed for modification.
- **Liskov Substitution:** Ports and interfaces must be substitutable (mock ≡ real implementation in tests).
- **Interface Segregation:** Ports are fine-grained. Clients depend only on methods they call.
- **Dependency Inversion:** Depend on abstractions (interfaces), not concrete implementations. Spring constructor injection enforces this.
- **Code standards:**
  - Max 20 lines per method (excluding braces, comments). Longer → extract helper.
  - Max 4 parameters per method. Fifth → wrap in command object or aggregate.
  - Max cyclomatic complexity = 10 (Checkstyle enforced).
  - No magic numbers; constants named explicitly (e.g., `private static final int FRAUD_SCORE_THRESHOLD = 75`).
  - No commented-out code; delete it. Git history preserves it.
  - Naming: clear intent. `document`, not `doc` or `d`. `extractionsByDocument`, not `map1`.

**Why:** Readability = maintainability = fewer bugs.

---

### V. Code Quality Gates (Non-Negotiable)

Every commit is verified by automated quality checks. Failures block CI/CD. **Non-negotiable rules:**

- **Checkstyle:** Coding conventions (indentation, naming, imports order, max line length 120). Configured in `checkstyle.xml`.
- **SonarCloud:** 0 bugs, 0 vulnerabilities, ≤ 3% duplication. Code smells tracked.
- **PIT Mutation Testing (domain only):** ≥ 85% mutation score. Verifies tests catch mutations.
- **ArchUnit Architecture Tests:** 12 rules enforcing hexagonal structure. Run in CI Phase 1; one failure = one PR rejection.
- **Maven plugins:**
  ```bash
  ./mvnw clean verify           # Runs all gates
  ./mvnw checkstyle:check       # Style check
  ./mvnw test                   # Unit + integration tests
  ./mvnw pit:mutationCoverage   # Mutation testing (domain)
  ```

**Why:** Prevents technical debt accumulation. Standards = consistency = team velocity.

---

### VI. Observability from Day One (Metrics, Logs, Traces)

Every critical path is observable. **Non-negotiable rules:**

- **Structured Logging:** SLF4J + JSON format (Logback). Include `tenant_id`, `document_id`, `correlation_id` in all logs. PII fields masked as `[PII_MASKED]`.
- **Metrics (Micrometer → Prometheus):** Counters for events, timers for durations, gauges for queues (Kafka lag).
- **Traces (OpenTelemetry → Grafana Tempo):** Every API request, Kafka message, DB query traced end-to-end.
- **Health Checks:** `/actuator/health/` exposes database, Kafka, cache, external API status.
- **Audit Logs:** Security-relevant events logged to separate collection (immutable, append-only, replicated).

**Why:** Production incidents resolved in minutes. Compliance evidence. Performance bottlenecks identified.

---

### VII. Multi-Tenancy & Security (Built-In, Not Bolted-On)

Tenancy and security policies are enforced in every layer. **Non-negotiable rules:**

- **JWT + Tenant ID:** Every API request includes JWT with `tenant_id` claim (from Keycloak 26). No multi-tenant query without tenant filter.
- **Domain Isolation:** Aggregates include `tenantId` field. Queries always filter: `db.documents.find({ tenantId, … })`. Audited by ArchUnit.
- **Role-Based Access Control:** 5 roles enforced at adapter level — `TENANT_ADMIN`, `ANALYST`, `VIEWER`, `FRAUD_REVIEWER`, `SYSTEM`. Domain services do NOT enforce roles.
- **Secrets Management:** API keys, DB credentials in `.env` (never committed). AWS Secrets Manager in production. Spring profiles: `local`, `dev`, `staging`, `prod`.
- **Encryption:** Sensitive PII fields (SSN, bank account) encrypted at rest via AWS KMS (ADR-005). TLS in transit (all external APIs).
- **No Hardcoded Data:** Test fixtures in `@BeforeEach`. No production credentials in code or comments. `TenantContext.get()` — never pass `tenantId` via request body.

**Why:** Regulatory compliance (GDPR, data isolation). Customer trust. Insider threat prevention.

---

---

## VIII. Architecture Decision Records (ADR)

> **These 11 ADR are non-negotiable.** They encode lessons learned and architectural decisions that cannot be violated without full team consensus. Any code that violates an ADR is treated as a critical bug (resolved within 24h).
>
> **Full implementation details:** skill `docai-architecture-adr` + `references/adr-details.md`

### ADR Summary Table

| ADR | Priority | Problem | Decision | Modules Impacted |
|-----|----------|---------|----------|-----------------|
| **ADR-001** | 🔴 Critical | Concurrent quota counters → silent overrun | **Atomic Lua script on Valkey** — never `GET` then `INCR` separately | Module 1 (Upload), Module 7 (Billing) |
| **ADR-002** | 🔴 Critical | Kafka event ordering per document | **Partition key = `documentId`** (never `tenantId` on pipeline topics) | ALL modules (Kafka) |
| **ADR-003** | 🔴 Critical | Thundering Herd on cache expiry | **TTL with ±10% jitter** on all Valkey keys > 1h (`JitterTtl.withJitter()`) | Module 2, Module 3, all adapters |
| **ADR-004** | 🔴 Critical | MongoDB 4MB transaction limit on OCR text | **Raw OCR text → S3 only** — only `rawOcrTextS3Key` stored in MongoDB | Module 2 (OCR/Extraction) |
| **ADR-005** | 🟠 Important | PII encryption key rotation | **AWS KMS** with automatic annual rotation — alias `alias/docai-pii-{env}` | Module 0.5 (RGPD) |
| **ADR-006** | 🟠 Important | Keycloak down → all users blocked | **Local JWKS cache TTL 1h** + minimum 2 Keycloak instances in production | Module 0 (Security) |
| **ADR-007** | 🟠 Important | Abandoned S3 multipart uploads billed indefinitely | **`AbortMultipartUpload` in catch** + S3 Lifecycle Rule 24h (Terraform) | Module 1 (Upload) |
| **ADR-008** | 🟠 Important | JVM OOM on GitHub Actions runners (7GB RAM) | **3 separate CI jobs** + `-Xmx512m` + `TestContainers reuse=true` | CI/CD |
| **ADR-009** | 🟡 Comfort | Downgrade leaves data orphaned | **Historical data read-only** — never deleted on downgrade. New quota applies next month. | Module 7 (Billing) |
| **ADR-010** | 🟡 Comfort | Full collection scans at 10M+ documents | **EXPLAIN PLAN before every merge** + partial indexes if active < 20% | ALL MongoDB modules |
| **ADR-011** | 🟡 Comfort | Read Model CQRS drift undetected | **`lastSyncedAt` field** + reconciliation job every 5 min + alert if lag > 30s | Module 5 (Dashboard) |

---

### ADR-001 — Atomic Lua Script for Quota

```java
// CORRECT (ADR-001)
String luaScript = """
    local current = redis.call('GET', KEYS[1])
    if current == false then current = 0 end
    if tonumber(current) >= tonumber(ARGV[1]) then return -1 end
    redis.call('INCR', KEYS[1])
    redis.call('EXPIRE', KEYS[1], ARGV[2])
    return tonumber(current) + 1
""";
// FORBIDDEN (ADR-001)
// Long count = valkey.opsForValue().get(key); // GET
// if (count < limit) valkey.opsForValue().increment(key); // then INCR — RACE CONDITION
```

### ADR-002 — Kafka Partition Key

```java
// CORRECT (ADR-002) — documentId as partition key
ProducerRecord<String, DocumentUploaded> record =
    new ProducerRecord<>("docai.doc.uploaded", documentId, event); // ← documentId

// FORBIDDEN (ADR-002)
// new ProducerRecord<>("docai.doc.uploaded", tenantId, event); // ← tenantId = wrong ordering
```

### ADR-003 — TTL Jitter

```java
// CORRECT (ADR-003)
Duration ttl = JitterTtl.withJitter(Duration.ofHours(24)); // 21.6h–26.4h
valkey.opsForValue().set(key, value, ttl);

// FORBIDDEN (ADR-003) — fixed TTL > 1h causes thundering herd
// valkey.opsForValue().set(key, value, Duration.ofHours(24)); // exact 24h for 1000 keys
```

**Exception — Fixed TTL allowed for:**
- Idempotency keys (`idempotent:{topic}:{partition}:{offset}`) — precision required
- JWT blacklist (`jwt:blacklist:{jti}`) — must match token remaining lifetime exactly

### ADR-004 — OCR Text → S3 Only

```java
// CORRECT (ADR-004) — S3 key stored, not content
public class ExtractionResult {
    private final String rawOcrTextS3Key; // "{tenantId}/ocr/{documentId}/raw-text.txt"
    // private final String rawOcrText; // ← FORBIDDEN — may exceed 4MB MongoDB limit
}
```

### ADR-006 — JWKS Cache

```yaml
# application.yml — JWKS cached locally 1h (ADR-006)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_URL}/realms/docai/protocol/openid-connect/certs
          # Spring Security caches JWKS locally for 1h automatically
```

### ADR-007 — S3 Abort Multipart

```java
// CORRECT (ADR-007)
String uploadId = s3.createMultipartUpload(key).uploadId();
try {
    List<CompletedPart> parts = uploadParts(stream, key, uploadId);
    s3.completeMultipartUpload(key, uploadId, parts);
} catch (Exception e) {
    s3.abortMultipartUpload(key, uploadId); // ← MANDATORY on error (ADR-007)
    throw new StorageException("Upload failed", e);
}
```

### ADR-011 — Read Model Sync

```java
// CORRECT (ADR-011)
@Document(collection = "document_summary_views")
public class DocumentSummaryView {
    private Instant lastSyncedAt; // ← MANDATORY field (ADR-011)
    // Updated on every Kafka event by DashboardProjectionConsumer
}

// ReconciliationScheduler — detects lag > 30s every 5 minutes (ADR-011)
@Scheduled(fixedDelay = 300_000)
public void reconcile() { ... }
```

---

---

## Architectural Standards

### Maven Module Organization

```
docai-parent/                           ← POM parent (dependency management)
├── docai-domain/                       ← Pure domain model (0 infrastructure dependencies)
├── docai-application/                  ← Use cases (orchestration, commands, queries)
├── docai-adapter-in-rest/              ← REST controllers (Spring MVC)
├── docai-adapter-in-kafka/             ← Kafka consumer adapters
├── docai-adapter-out-mongodb/          ← MongoDB persistence
├── docai-adapter-out-kafka/            ← Kafka event publisher (Outbox Pattern)
├── docai-adapter-out-valkey/           ← Valkey caching
├── docai-adapter-out-ai/               ← LLM/OCR providers (OpenAI, Mistral, Tess4J, PDFBox)
├── docai-adapter-out-storage/          ← AWS S3 integration
├── docai-adapter-out-external/         ← External APIs (INSEE, BAN, RPPS)
└── docai-bootstrap/                    ← Spring Boot entry point, main config
```

### Technology Stack (Locked)

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| **Runtime** | Java 21 (LTS) | 21 | Latest stable LTS, Virtual Threads, Records, Sealed Classes |
| **Framework** | Spring Boot | **4.0.x** | Native Virtual Thread support |
| **Database** | MongoDB | 7.x | Flexible schema, native multi-doc transactions (replica set required) |
| **Cache** | **Valkey** (Redis-compat) | 8.x | Linux Foundation fork of Redis (BSD 3-Clause license) |
| **Messaging** | Apache Kafka | 3.7 (KRaft) | KRaft mode — no Zookeeper dependency |
| **Schema Registry** | **Apicurio Registry** | 2.6 | Apache 2.0 — replaces Confluent (restrictive license) |
| **Auth** | Keycloak | **26** | OIDC/OAuth2, multi-tenant realms, PKCE |
| **Storage** | AWS S3 | SDK v2 | 99.999999999% durability, compliance |
| **AI/LLM (primary)** | **OpenAI GPT-4o** | Latest | temperature=0.0, JSON mode, vision classification |
| **AI/LLM (alternate)** | **Mistral** (Feature Flag) | Latest | `extraction.mistral.enabled` flag — transparent swap |
| **OCR (PDF native)** | **Apache PDFBox 3.x** | 3.x | Extracts text from native-text PDFs without OCR |
| **OCR (scanned)** | **Tess4J 5.x** | 5.x | Tesseract Java binding — French language config |
| **Visual Analysis** | **JavaCV (OpenCV)** | 4.9.0 | `org.bytedeco:opencv` — NOT `org.opencv` directly |
| **Metadata Analysis** | **Apache Tika** | 2.x | Photoshop/GIMP detection in file metadata |
| **Resilience** | Resilience4j | 2.x | CircuitBreaker, Retry, Bulkhead, TimeLimiter |
| **Rate Limiting** | Bucket4j + Valkey | 8.x | Distributed token bucket |
| **Migrations** | Mongock | 5.x | MongoDB versioned migrations (≈ Flyway for Mongo) |
| **Mapping** | MapStruct | 1.6 | Compile-time, zero reflection |
| **Testing** | JUnit 5, Mockito | Latest | Modern, composable, fast |
| **Test Infra** | TestContainers | Latest | Real services in tests — `withReuse(true)` (ADR-008) |
| **Quality** | SonarCloud, ArchUnit, PIT | — | Mandatory CI gates |
| **Observability** | OpenTelemetry, Prometheus, Grafana, Tempo | Latest | Vendor-neutral, full-stack visibility |
| **Feature Flags** | Unleash (self-hosted) | — | 6 flags: `billing.enabled`, `fraud.v2.enabled`, `extraction.mistral.enabled`, `dashboard.search.enabled`, `notifications.inapp.enabled`, `maintenance.mode` |

> ⚠️ **Critical stack corrections vs legacy docs:**
> - `Valkey 8.x` — NOT Redis (license change)
> - `Apicurio Registry` — NOT Confluent Schema Registry (license)
> - `Spring Boot 4.0.x` — NOT 3.x
> - `OpenAI GPT-4o` as primary LLM — NOT Claude API
> - `JavaCV` — NOT `org.opencv` direct binding
> - `PDFBox 3.x + Tess4J 5.x` for OCR — NOT EasyOCR (Python only, incompatible with Java)

---

### Key Architectural Patterns

| Pattern | Module | Description |
|---------|--------|-------------|
| **Outbox Pattern** | All Kafka publishers | Atomic MongoDB transaction + Kafka event — zero message loss |
| **Cache-Aside** | Extraction, Validation | Valkey checked before LLM/API call. ADR-003 jitter applied. |
| **Circuit Breaker** | LLM, OCR, external APIs | Resilience4j — fallback to NEEDS_REVIEW state on OPEN |
| **Saga** | Pipeline | Compensations on each of 7 failure scenarios |
| **CQRS** | Dashboard | Read Model separated from write-side, reconciliation ADR-011 |
| **Strategy** | Classification, Fraud, Extraction | Interchangeable algorithms per document type |
| **Registry** | Fraud analyzers | `FraudAnalyzerRegistry` — Spring auto-registration of analyzers |
| **Anti-Corruption Layer** | External APIs | INSEE, BAN, RPPS adapters isolate domain from external API changes |
| **Fail-Safe** | Fraud analyzers | Failed analyzer → empty signals, pipeline continues (never crashes) |

---

### Kafka Topics & Consumer Groups

| Topic | Partitions | Retention | Partition Key |
|-------|-----------|-----------|---------------|
| `docai.doc.uploaded` | 6 | 7 days | `documentId` (ADR-002) |
| `docai.doc.classified` | 6 | 7 days | `documentId` (ADR-002) |
| `docai.doc.extracted` | 6 | 7 days | `documentId` (ADR-002) |
| `docai.doc.fraud.analyzed` | 6 | 7 days | `documentId` (ADR-002) |
| `docai.doc.completed` | 3 | 30 days | `documentId` (ADR-002) |
| `docai.doc.failed` | 3 | 30 days | `tenantId` |
| `docai.doc.dlq` | 3 | **90 days** | `tenantId` |
| `docai.outbox.relay` | 3 | 1 day | `documentId` |

**Consumer Group naming convention:** `docai.{module}.{consumer}.group`

---

### Valkey Cache Keys Reference

| Data | Key Pattern | TTL | Jitter |
|------|-------------|-----|--------|
| LLM extraction | `extraction:{tenantId}:{sha256}` | 24h | ±10% (ADR-003) |
| INSEE SIRET | `insee:siret:{siret}` | 7 days | ±10% (ADR-003) |
| BAN address | `ban:address:{sha256(addr)}` | **30 days** | ±10% (ADR-003) |
| RPPS practitioner | `rpps:{number}` | 7 days | ±10% (ADR-003) |
| JWT blacklist | `jwt:blacklist:{jti}` | = token remaining TTL | None |
| Idempotency (upload) | `idempotency:{X-Idempotency-Key}` | 24h | None |
| Idempotency (consumer) | `idempotent:{topic}:{partition}:{offset}` | 24h | None |
| Monthly quota | `quota:{tenantId}:{year}-{month}` | Until 1st of next month | None |

---

## Development Workflow

### Git Branching & Commits

- **Conventional Commits:** `{type}({scope}): {subject}`. Types: `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`, `perf:`.
- **Examples:** `feat(domain): add fraud scoring service` / `fix(adapter-rest): handle null extraction results`
- **Feature branches:** `feature/{task-number}-{short-name}`. Example: `feature/001-hexagonal-setup`.
- **PR size:** One micro-task (max 1 day of work) per PR.
- **Merge strategy:** Squash + rebase.

### Code Review Checklist

Every PR review verifies:

1. ✅ **Architecture:** Hexagonal boundaries respected (ArchUnit passes — 12 rules).
2. ✅ **ADR Compliance:** No ADR violated (check ADR table above before approving).
3. ✅ **Testing:** New code tested. Coverage did not decrease. Integration test if new adapter.
4. ✅ **Code Quality:** Checkstyle, SonarCloud, complexity ≤ 10.
5. ✅ **Security:** No hardcoded secrets. `TenantContext.get()` used (not request body). Rate limiting applied.
6. ✅ **Naming:** Clear intent. No abbreviations. `tenantId` in every aggregate, every query.
7. ✅ **SOLID:** Method ≤ 20 lines. Max 4 params. One responsibility.
8. ✅ **Observability:** Structured logs with `tenantId` + `traceId`. PII masked. Metrics added if new path.

---

## IX. Operational Annexes

### Annex A — Resilience4j Thresholds (Non-Negotiable)

| Service | Circuit Breaker | Retry | Bulkhead | Timeout |
|---------|----------------|-------|----------|---------|
| OpenAI GPT-4o / Mistral | 50% / 10 calls | 3× exp. backoff (1s) | 20 threads | **30s** |
| Tess4J OCR | 50% / 5 calls | 3× exp. backoff | 10 threads | **60s** |
| INSEE API | 60% / 5 calls | 2× backoff (2s) | 5 threads | **5s** |
| BAN API | 60% / 5 calls | 2× backoff (500ms) | 5 threads | **5s** |
| RPPS API | 60% / 8 calls | 2× fixed (1s) | 5 threads | **5s** |
| Apache Tika | 50% / 5 calls | 2× backoff (1s) | 5 threads | **15s** ← BR-VIS-003 |
| OpenCV/JavaCV | 50% / 5 calls | 1× | 5 threads | **15s** ← BR-VIS-003 |
| Amazon S3 | 50% / 10 calls | 3× exp. backoff (1s) | 20 threads | **30s** |

> **BR-VIS-003:** Tika and OpenCV timeouts are EXACTLY 15s. Never increase. Fraud analyzer fail-safe prevents pipeline crash.

---

### Annex B — MongoDB Standards

- **Collection naming:** `snake_case` plural (`documents`, `extraction_results`, `fraud_analyses`)
- **Field naming:** `camelCase`. Dates suffixed with `At` (`createdAt`, `updatedAt`, `lastSyncedAt`)
- **NEVER `@Indexed` in `@Document`** — all indexes via Mongock migrations only (ADR-010)
- **`auto-index-creation: false`** in production
- **`tenantId` FIRST** in all compound indexes (ADR-010)
- **EXPLAIN PLAN mandatory** before merging any query: `winningPlan.stage` must be `IXSCAN`, never `COLLSCAN`
- **Migration naming:** `V{NNN}_{module}_{description}` — e.g., `V001_setup_documents_collection`
- **Every migration has `@RollbackExecution`** method

---

### Annex C — Security & Secrets Rotation

| Secret | Storage | Rotation Frequency | Alert |
|--------|---------|-------------------|-------|
| OpenAI API Key | AWS Secrets Manager | **90 days** | AWS alert if > 90d |
| Keycloak Client Secret | AWS Secrets Manager | 90 days | AWS alert |
| Stripe Webhook Secret | AWS Secrets Manager | 90 days | AWS alert |
| INSEE OAuth2 Credentials | AWS Secrets Manager | 90 days | AWS alert |
| MongoDB Credentials | AWS Secrets Manager | 180 days | AWS alert |
| KMS PII Encryption Key | AWS KMS | **Annual (automatic)** | CloudTrail |

**Rotation procedure:** Update secret in AWS Secrets Manager → Spring Cloud AWS reloads automatically (no redeploy) → Revoke old secret → Document in rotation journal.

---

### Annex D — Production Readiness Checklist

Before any production deployment, ALL of the following must be verified:

**Security**
- [ ] All secrets in AWS Secrets Manager (no `.env` in production)
- [ ] WAF in front of load balancer
- [ ] TLS 1.3 on all public endpoints
- [ ] OWASP ZAP scan — 0 HIGH/CRITICAL vulnerabilities
- [ ] External pentest completed

**Reliability**
- [ ] MongoDB: 3-node replica set, multi-AZ
- [ ] Kafka: 3 brokers minimum, replication factor 3
- [ ] Keycloak: 2+ instances (ADR-006)
- [ ] Circuit Breaker tested: LLM down → NEEDS_REVIEW (not crash)
- [ ] DLQ monitored: Grafana alert if > 10 messages
- [ ] MongoDB backup: daily snapshot, monthly restore test

**Observability**
- [ ] Grafana dashboards: pipeline, fraud, API, JVM heap, Kafka lag
- [ ] Alerts: P95 latency, error rate > 1%, Kafka lag > 1000, heap > 85%
- [ ] All logs: structured JSON with `traceId`, `tenantId`
- [ ] Status page published (Instatus or Statuspage.io)

**Performance — SLA**
- [ ] P95 document processing: **< 30 seconds**
- [ ] P95 dashboard: **< 100ms**
- [ ] P95 upload: **< 2 seconds**
- [ ] Fraud SSE alert: **< 2 seconds** (BR-FRD-015)
- [ ] Uptime SLA: **99.9%** (< 8.7h downtime/year)

**Chaos Engineering (7 scenarios — staging only)**
1. Stop Keycloak 20 min → connected users NOT blocked (ADR-006 JWKS cache)
2. Stop Kafka 5 min → zero document loss (Outbox Pattern)
3. Saturate LLM (429) → documents in NEEDS_REVIEW (Circuit Breaker fallback)
4. Fill MongoDB disk 95% → health check alert + pod removed from traffic
5. Stop 1 of 3 pods → zero HTTP errors (RollingUpdate)
6. Exceed LLM quota → partial extraction, pipeline not blocked
7. 10× normal document flood → HPA scale-out, lag absorbed in < 5 min

---

### Annex E — Dependency Policy

| Type | Max Delay | Approver | Required Test |
|------|----------|---------|--------------|
| CVE CRITICAL | **24h** | Tech Lead | Full CI |
| CVE HIGH | **72h** | Tech Lead | Full CI |
| Patch (x.y.Z) | 1 week | Dev | Full CI |
| Minor (x.Y.0) | 2 weeks | Tech Lead | CI + manual test |
| Major (X.0.0) | Dedicated sprint | Team | CI + non-regression |

- No dependency without compatible license (Apache 2.0, MIT, BSD).
- Dependabot configured for weekly Maven updates.
- Spring Boot + Java LTS updated within 3 months of release.

---

## Governance

### Constitution Enforcement

- **This document is authority.** It overrides all other practices, conventions, or personal preferences.
- **Amendments require:** Rationale (why), impact analysis (what changes), migration plan (how).
- **Version bumps:** MAJOR = backward incompatible principle removal; MINOR = new principle or section; PATCH = clarification.
- **Review cycle:** Quarterly (May, Aug, Nov, Feb) or on explicit amendment request.

### Compliance & Auditing

- **CI Pipeline enforces:** ArchUnit (12 rules), Checkstyle, coverage gates, mutation testing. Failure = PR blocked.
- **Code reviews verify:** Constitution principles + ADR compliance (Section VIII).
- **Architecture reviews (monthly):** Scan for drift, tech debt, emerging patterns.
- **Incident retrospectives:** Root cause analysis includes "did we follow the constitution and the ADRs?"

### Non-Negotiable Principles

The following are IMMUTABLE and cannot be amended without full team consensus:

1. **Hexagonal Architecture** — `docai-domain/` remains framework-free (ArchUnit enforced).
2. **Domain Purity** — Zero Spring/MongoDB/Kafka imports in `docai-domain/`.
3. **Test-First Development** — 90% domain coverage, ArchUnit, PIT ≥ 85%.
4. **Multi-Tenancy by Default** — Every entity has `tenantId`. Every query filtered.
5. **Security & Secrets** — No hardcoded credentials. Encryption at rest. TLS in transit.
6. **ADR Compliance** — 11 ADR are non-negotiable (Section VIII). Violations = critical bugs.

Violations of these principles are treated as critical bugs and resolved within 24 hours.

---

## Quick Reference

| Aspect | Standard | Tool | Gate |
|--------|----------|------|------|
| **Architecture** | Hexagonal (domain isolated) | ArchUnit (12 rules) | CI Phase 1 (Build) |
| **Code Style** | Checkstyle conventions | Checkstyle Maven plugin | CI Phase 2 (Style) |
| **Unit Tests** | 90% domain, 80% global | JaCoCo, SonarCloud | CI Phase 2 (Test) |
| **Mutation Tests** | ≥ 85% domain | PIT Maven plugin | CI Phase 2 (Mutation) |
| **Code Quality** | 0 bugs, 0 vulns, ≤ 3% duplication | SonarCloud | CI Phase 3 (Analysis) |
| **Method Length** | ≤ 20 lines | Checkstyle + review | CI + review |
| **Method Params** | ≤ 4 | Checkstyle | CI + review |
| **Cyclomatic Complexity** | ≤ 10 | Checkstyle | CI + review |
| **ADR Compliance** | 11 ADR non-negotiable | Code review | Review + CI |
| **TTL Jitter** | ±10% on all Valkey keys > 1h | Code review | ADR-003 |
| **Kafka partition key** | `documentId` on pipeline topics | Code review | ADR-002 |
| **Quota counter** | Atomic Lua script | Code review | ADR-001 |
| **OCR text storage** | S3 only, never MongoDB | Code review | ADR-004 |
| **Multi-Tenancy** | `tenantId` in all queries | ArchUnit + review | CI + review |
| **Secrets** | `.env` local / AWS Secrets Mgr prod | `.gitignore` + audit | Pre-commit hook |
| **Observability** | Structured logs, metrics, traces | OpenTelemetry, Prometheus | Review + monitoring |

---

**Version**: 1.1.0 | **Ratified**: 2026-05-23 | **Last Amended**: 2026-05-23
**Reference Spec**: DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0
**Reference Skill**: `docai-architecture-adr` (ADR full details + ArchUnit 12 rules)
