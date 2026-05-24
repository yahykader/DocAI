# Implementation Plan: Maven Multi-Module & Docker Compose Infrastructure

**Feature Branch**: `002-maven-docker-setup`  
**Plan Created**: 2026-05-24  
**Module**: 1.A — Structure Maven + Docker Compose | Partie 1 — Setup & CI/CD  
**Duration**: 3.5 days  
**Status**: Ready for implementation

---

## Technical Context

### Architecture Foundation
- **Pattern**: Hexagonal Architecture (Ports & Adapters) with 11 Maven modules
- **Domain Package Root**: `fr.docai` (per FR-001 clarification)
- **Infrastructure**: Docker Compose orchestrating 11 services locally
- **Security Model**: Multi-tenancy via Keycloak JWT with `tenant_id` claim (ADR-006)
- **Event-Driven**: Kafka 3.7 KRaft mode with documentId partition key (ADR-002)
- **Storage**: Real AWS S3 (no MinIO mock, per clarification)
- **Database**: MongoDB 7 Replica Set for transactions (mandatory)
- **Performance Analysis**: Combined mongosh explain() + MongoDB server-side profiling (ADR-010)

### Key Dependencies & Ordering
1. **Story 1 → Foundation**: Docker Compose + 11 services must be healthy first
2. **Story 2 → Build**: Maven module structure depends on correct POM hierarchy
3. **Story 3 → Auth**: Keycloak realm + Protocol Mapper enables token flow
4. **Story 4 → Seeding**: Requires Stories 1-3 operational (infrastructure + auth)
5. **Story 5 → CI/CD**: Maven profiles must exist before Pipeline can be configured

---

## Constitution Check

### Principle Compliance (Pre-Gate)

| Principle | Requirement | Status | Justification |
|-----------|-------------|--------|---------------|
| **Hexagonal Architecture** | Domain module zero Spring/MongoDB/Kafka/AWS imports | ✅ By Design | Module 1.A setup creates structure; ArchUnit tests verify |
| **DDD Bounded Contexts** | 4 contexts identified (Document, Fraud, Pipeline, Security) | ✅ By Design | Module 1.A only defines entities; implementation Phase 4-5 |
| **Test-First Development** | 90% domain coverage + ArchUnit 12 rules + PIT ≥ 85% | ✅ By Design | Module 1.A includes ArchUnit test framework; domain tests Phase 2-4 |
| **SOLID & Clean Code** | Max 20 lines/method, 4 params, complexity ≤ 10 | ✅ By Design | Checkstyle configured; enforced in CI |
| **Code Quality Gates** | Checkstyle + SonarCloud + ArchUnit + PIT | ✅ By Design | Module 1.A configures gates; verification Phase 2+ |
| **Observability** | Structured logging + metrics + traces | 🟡 Deferred | Infrastructure configured Phase 1; application logging Phase 3 |
| **Multi-Tenancy & Security** | `tenantId` in JWT + Keycloak realm + role-based access | ✅ By Design | Keycloak setup Phase 4; application enforcement Phase 3+ |

### Gate Assessment

**Gate 1 — Spec Completeness**: ✅ PASS
- All 5 user stories defined with acceptance scenarios
- 23 functional requirements (FR-001 through FR-023)
- 12 success criteria with measurable outcomes
- 11 assumptions documented
- 7 clarifications integrated

**Gate 2 — Constitution Alignment**: ✅ PASS
- Module 1.A foundations align with Hexagonal Architecture (Section I)
- Keycloak configuration per Multi-Tenancy principle (Section VII)
- ADR-002 (Kafka partition key), ADR-006 (JWKS cache), ADR-008 (CI job separation), ADR-010 (EXPLAIN PLAN) all addressed
- No conflicting technology choices

**Gate 3 — Architectural Drift**: ✅ PASS (Green)
- No existing codebase to drift from
- Spec requirements traceable to Constitution sections
- ADR constraints documented in plan

---

## Phase 0: Research & Clarification Resolution

**Status**: ✅ COMPLETE (All 7 clarifications resolved in spec)

All critical ambiguities resolved in `/speckit-clarify` session 2026-05-24:
- Q1: Java package root = `fr.docai`
- Q2: Storage strategy = Real AWS S3
- Q3: SeedingService timing = On-demand REST endpoint
- Q4: Keycloak realm management = JSON versioned + auto-imported
- Q5: Kafka topics creation = `kafka-init` service
- Q6: Keycloak `tenant_id` = User Attribute + Protocol Mapper
- Q7: MongoDB EXPLAIN PLAN = Combined mongosh + profiling

**Research tasks**: None remaining. Proceed to Phase 1.

---

## Phase 1: Design & Contracts

### 1.1 Data Model (`data-model.md`)

**Entities** (Infrastructure Layer):

| Entity | Purpose | Fields | Constraints |
|--------|---------|--------|-------------|
| **Tenant** | Customer organization | id, name, status, plan_type, created_at | Unique name per instance, status ∈ {ACTIVE, SUSPENDED, DELETED} |
| **User** | Human in the system | id, username, email, tenant_id, roles, created_at, last_login | Email unique per tenant, roles ⊆ {ADMIN, TENANT_ADMIN, MANAGER, USER, VIEWER} |
| **Keycloak Realm** | Auth boundary | realm_name, token_expiration, password_policies, clients | realm_name = "docai" (singleton) |
| **Service Health** | Infrastructure monitoring | service_name, status, last_checked_at | status ∈ {HEALTHY, UNHEALTHY, DEGRADED} |

**Relationships**:
- User → Tenant (N:1) — each user assigned to one tenant
- Tenant → Users (1:N)
- Keycloak Realm → Clients (1:N) — clients: docai-backend, docai-frontend, docai-admin

**Validation Rules**:
- `tenantId` ALWAYS included in User and future domain entities
- Service Health snapshots taken at startup + 30-second intervals

### 1.2 Interface Contracts

Since Module 1.A is infrastructure-only, no public API contracts yet. Contracts deferred to Module 3 (REST adapters).

**Docker Compose Service Contracts** (operational):
- All 11 services expose health checks (readiness probes)
- Keycloak exposes `/admin` UI + `/protocol/openid-connect/token` endpoint
- MongoDB exposes native protocol (27017) + mongosh CLI
- Kafka exposes native broker protocol (9092) + Kafka UI (8080)

### 1.3 Infrastructure Contracts

**Environment Variable Contract** (`.env.example`):
- All required variables documented with example values
- Developers must set AWS credentials before running
- No hardcoded secrets in code

**Docker Compose Service Dependencies**:
```
MongoDB (healthy) → Keycloak (healthy) → Kafka (healthy) → Application (starts)
```

---

## Implementation Roadmap: 6 Étapes

### ÉTAPE 1 — Maven Module Structure (0.5 days)

**Objective**: Create parent POM + 11 child module directories with proper dependency hierarchy

**Deliverables**:
1. **Parent pom.xml** (`pom.xml`)
   - ✅ Declares all 11 child modules in `<modules>` section
   - ✅ Defines shared dependency versions (Java 21, Spring Boot 4.0.x, Maven 3.9+)
   - ✅ Configures transitive dependency locking (BOM import)
   - ✅ Declares 4 Maven profiles: `unit-tests`, `integration-tests`, `quality-gates`, `dev-profile`

2. **Module Structure** (11 directories + pom.xml each)
   ```
   docai-domain/
   docai-application/
   docai-adapter-in-rest/
   docai-adapter-in-kafka/
   docai-adapter-out-mongodb/
   docai-adapter-out-kafka/
   docai-adapter-out-valkey/
   docai-adapter-out-ai/
   docai-adapter-out-storage/
   docai-adapter-out-external/
   docai-bootstrap/
   ```

3. **Package Structure** (per FR-001)
   - Each module contains `src/main/java/fr/docai/{module-name}/` directory
   - Package roots: `fr.docai.domain`, `fr.docai.application`, `fr.docai.adapter.in.rest`, etc.

4. **Dependency Constraints** (enforced via pom.xml)
   - ✅ `docai-domain`: Zero Spring/MongoDB/Kafka/AWS dependencies
   - ✅ `docai-application`: Depends only on `docai-domain`
   - ✅ Adapter modules: Depend on domain/application, never on other adapters

**Acceptance Criteria**:
- ✅ `./mvnw clean compile` → BUILD SUCCESS (all 11 modules)
- ✅ `./mvnw dependency:tree` → No circular dependency warnings
- ✅ `docai-domain` pom.xml: zero external Spring/infrastructure imports
- ✅ Parent pom.xml declares all 11 modules + shared versions

**Validation**:
- Run in terminal: `./mvnw clean compile -DskipTests`
- Expected: All modules compile without errors in < 60 seconds
- Check: `mvnw dependency:tree | grep -i circular` → (empty output = pass)

---

### ÉTAPE 2 — ArchUnit Architecture Tests (0.5 days)

**Objective**: Create HexagonalArchitectureTest with 12 rules to enforce architecture constraints

**Deliverables**:
1. **HexagonalArchitectureTest class** (`docai-bootstrap/src/test/java/.../HexagonalArchitectureTest.java`)
   - 12 ArchUnit rules (per Constitution Section I):
     1. Domain must not import Spring classes
     2. Domain must not import MongoDB classes
     3. Domain must not import Kafka classes
     4. Domain must not import AWS classes
     5. Application must not import adapter packages
     6. Adapters must not import other adapters
     7. Adapters must not import bootstrap
     8. No circular dependencies between modules
     9. Domain imports only domain/application
     10. Inbound adapters translate external protocols
     11. Outbound adapters implement domain ports
     12. Bootstrap imports all adapters (wiring only)

2. **Test Configuration**
   - Uses `ArchUnit 1.1.x` (latest)
   - Scans all 11 module classpaths
   - Reports violations with remediation suggestions

**Acceptance Criteria**:
- ✅ All 12 rules execute without errors
- ✅ All 12 rules PASS (zero violations)
- ✅ Test completes in < 5 seconds
- ✅ Test can run standalone: `./mvnw test -Dtest=HexagonalArchitectureTest`

**Validation**:
```bash
./mvnw test -Dtest=HexagonalArchitectureTest
# Expected: Tests run and all 12 pass (0 failures)
```

---

### ÉTAPE 3 — Docker Compose Infrastructure (1 day)

**Objective**: Define 11 services in docker-compose.yml with health checks, volumes, and dependencies

**Deliverables**:
1. **docker-compose.yml** (root directory)
   - ✅ 11 services configured:
     - MongoDB 7 (Replica Set `rs0`, port 27017, `depends_on: {condition: service_healthy}`)
     - Kafka 3.7 (KRaft mode, port 9092, health check)
     - Apicurio Schema Registry (port 8081, health check)
     - Keycloak 26 (port 8180, admin/admin, health check)
     - Valkey 8 (port 6379, health check)
     - Prometheus (port 9090)
     - Grafana (port 3000, admin/admin)
     - OpenTelemetry Collector (port 4317 OTLP)
     - Grafana Tempo (port 3200)
     - Kafka UI (port 8080)
     - [DevOps monitoring service, optional]

   - ✅ Health checks defined (readiness probes appropriate to each service)
   - ✅ Volumes for data persistence:
     - `mongo_data` (MongoDB)
     - `kafka_data` (Kafka)
     - `redis_data` (Valkey, optional)
     - `prometheus.yml` (read-only)
     - Grafana provisioning (dashboards, data sources)
     - Tempo config (traces backend)

   - ✅ Service dependencies enforce order:
     ```yaml
     depends_on:
       mongodb:
         condition: service_healthy
       keycloak:
         condition: service_healthy
       kafka:
         condition: service_healthy
     ```

2. **mongodb-init Service** (Replica Set initialization)
   - ✅ Entrypoint: Runs `rs.initiate()` on first startup
   - ✅ Idempotent: Skips if replica set already initialized
   - ✅ Returns healthy status only after RS ready

3. **kafka-init Service** (Topic creation)
   - ✅ Depends on Kafka broker healthy
   - ✅ Creates 8 topics with correct replication/partition config (per ADR-002):
     - `docai.doc.uploaded` (6 partitions, partition key: `documentId`)
     - `docai.doc.classified` (6 partitions, partition key: `documentId`)
     - `docai.doc.extracted` (6 partitions, partition key: `documentId`)
     - `docai.doc.fraud.analyzed` (6 partitions, partition key: `documentId`)
     - `docai.doc.completed` (3 partitions, partition key: `documentId`)
     - `docai.doc.failed` (3 partitions, partition key: `tenantId`)
     - `docai.doc.dlq` (3 partitions, partition key: `tenantId`)
     - `docai.outbox.relay` (3 partitions, partition key: `documentId`)
   - ✅ Idempotent creation (topics only if don't exist)

4. **MongoDB Profiling Setup** (ADR-010)
   - ✅ docker-compose.yml includes:
     ```bash
     mongosh --eval "db.setProfilingLevel(1, { slowms: 100 })"
     ```
   - ✅ Enables server-side profiling at 100ms threshold

**Acceptance Criteria**:
- ✅ `docker compose up -d` → All 11 services start within 60 seconds
- ✅ `docker compose ps` → All services show "healthy" or "running"
- ✅ Service health verification:
  - MongoDB: `docker exec mongodb mongosh --eval "db.adminCommand('ping')"` → `{ok: 1}`
  - Kafka: `docker exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092` → (broker info)
  - Keycloak: `curl http://localhost:8180/admin/realms/master` → (401 Unauthorized OK — auth required)
  - Grafana: `curl http://localhost:3000/api/health` → {status: "ok"}
- ✅ All 8 Kafka topics created: `docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092`
- ✅ MongoDB replica set initialized: `docker exec mongodb mongosh --eval "rs.status().ok"` → `1`

**Validation**:
```bash
cd <repo-root>
docker compose up -d
sleep 30
docker compose ps  # All healthy/running
docker exec mongodb mongosh --eval "db.adminCommand('ping')"  # {ok: 1}
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092  # All 8 topics listed
```

**Troubleshooting**:
- If MongoDB replica set init fails: Check `docker compose logs mongodb` for errors
- If Kafka topics not created: Verify `kafka-init` service completed: `docker compose logs kafka-init`
- If Keycloak unhealthy: Check `docker compose logs keycloak` for database connection issues

---

### ÉTAPE 4 — Keycloak Realm Configuration (0.5 days)

**Objective**: Create realm-docai.json and configure auto-import with Protocol Mapper for tenant_id claim

**Deliverables**:
1. **realm-docai.json** (`docker/keycloak/realm-docai.json`)
   - ✅ Realm name: `docai`
   - ✅ 5 roles defined: `admin`, `tenant-admin`, `manager`, `user`, `viewer`
   - ✅ 5 test users created (e.g., `admin@acme-corp.test`, `user@acme-corp.test`, etc.)
   - ✅ Each user has:
     - Assigned roles
     - User Attribute: `tenant_id` (e.g., `acme-corp`, `beta-assur`, `gamma-rh`)
     - Email address
   - ✅ 3 OpenID Connect clients configured:
     - `docai-backend` (confidential, service account enabled)
     - `docai-frontend` (public, PKCE enabled)
     - `docai-admin` (confidential)
   - ✅ Protocol Mapper: "Add tenant_id"
     - Mapper Type: User Attribute
     - Token Claim Name: `tenant_id`
     - User Attribute Name: `tenant_id`
     - Claim JSON Type: String
     - Include in ID token: YES
     - Include in access token: YES

2. **Keycloak Init Script** (`docker/keycloak/init-keycloak.sh`)
   - ✅ Entrypoint script in Keycloak docker-compose service
   - ✅ Imports realm-docai.json using Keycloak admin API (or CLI)
   - ✅ Idempotent: Skips if realm already exists (checks `GET /admin/realms/docai`)
   - ✅ Creates test users with tenant_id attributes (no duplicates on re-import)

3. **Docker Compose Mount** (in docker-compose.yml)
   - ✅ Mount realm-docai.json as read-only volume
   - ✅ Mount init script in entrypoint
   - ✅ Keycloak service depends on MongoDB healthy

**Acceptance Criteria**:
- ✅ Keycloak admin UI accessible: http://localhost:8180/admin (login: admin/admin)
- ✅ Realm "docai" exists with 5 roles
- ✅ 5 test users exist with roles assigned
- ✅ Login test: `curl -X POST http://localhost:8180/realms/docai/protocol/openid-connect/token ...` → JWT returned
- ✅ JWT claims include `tenant_id` (decode JWT at jwt.io or programmatically)
- ✅ Re-import realm-docai.json (push to compose) → No duplicate users created

**Validation**:
```bash
# Test Keycloak init
curl -X POST \
  -d "client_id=docai-backend&client_secret=<secret>&grant_type=client_credentials" \
  http://localhost:8180/realms/docai/protocol/openid-connect/token

# Decode JWT and verify tenant_id claim
# (Use jwt.io or write small test script)

# Verify realm exists
curl http://localhost:8180/admin/realms/docai \
  -H "Authorization: Bearer <admin_token>"
```

---

### ÉTAPE 5 — Environment Configuration (.env.example + application.yml) (0.5 days)

**Objective**: Document all required environment variables and Spring Boot configuration

**Deliverables**:
1. **.env.example** (root directory)
   - ✅ All required variables documented:
     - `JAVA_OPTS` (JVM memory, flags)
     - Spring Boot: `spring.application.name`, `server.port`, etc.
     - MongoDB: `MONGODB_URI`
     - Kafka: `KAFKA_BROKERS`, `KAFKA_SECURITY_PROTOCOL`
     - Keycloak: `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`
     - AWS S3: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `S3_BUCKET_NAME`
     - Valkey: `VALKEY_HOST`, `VALKEY_PORT`
     - Observability: `PROMETHEUS_ENABLED`, `OTLP_EXPORTER_ENDPOINT`, `OTLP_EXPORTER_OTLP_HEADERS`

   - ✅ Each variable has:
     - Inline comment explaining purpose
     - Example value (CHANGE_ME for secrets)
     - Valid range or format (where applicable)

   - ✅ Values marked `CHANGE_ME`:
     - `AWS_ACCESS_KEY_ID`
     - `AWS_SECRET_ACCESS_KEY`
     - `S3_BUCKET_NAME`
     - `KEYCLOAK_CLIENT_SECRET`

   - ✅ Developers must copy .env.example → .env and set actual values

2. **application.yml** (docai-bootstrap/src/main/resources/)
   - ✅ Reads environment variables from .env
   - ✅ Profiles configured:
     - `application-dev.yml` (MongoDB replica set, Kafka, Keycloak localhost)
     - `application-prod.yml` (AWS-hosted services, secrets from AWS Secrets Manager)

   - ✅ Key configuration sections:
     ```yaml
     spring:
       application:
         name: docai-backend
       data:
         mongodb:
           uri: ${MONGODB_URI:mongodb://localhost:27017/docai?replicaSet=rs0}
       kafka:
         bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
       security:
         oauth2:
           resourceserver:
             jwt:
               issuer-uri: ${KEYCLOAK_ISSUER_URI}
               # JWKS cached locally 1h (ADR-006)
     ```

   - ✅ ADR-010 MongoDB profiling enabled:
     ```yaml
     spring:
       data:
         mongodb:
           # Dev profile only
           auto-index-creation: false
           profiling-level: 1  # Log slow queries ≥ 100ms
     ```

   - ✅ Valkey configuration:
     ```yaml
     spring:
       data:
         redis:
           host: ${VALKEY_HOST:localhost}
           port: ${VALKEY_PORT:6379}
     ```

3. **.env in .gitignore**
   - ✅ Ensure `.env` is in `.gitignore` (never committed)
   - ✅ `.env.example` is committed (template for developers)

**Acceptance Criteria**:
- ✅ .env.example documents all required variables (≥ 15 variables)
- ✅ Each variable has inline comment
- ✅ Example values provided (CHANGE_ME for secrets)
- ✅ application.yml reads from .env (Spring auto-configuration)
- ✅ Profiles exist: `dev`, `prod`
- ✅ .env in .gitignore
- ✅ `./mvnw spring-boot:run` starts (after docker compose up) and prints health check

**Validation**:
```bash
# Copy template
cp .env.example .env

# Edit .env with actual AWS credentials (dev account)
# Then test Spring Boot startup
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Expected: Application starts, Spring banner printed, health check available
# curl http://localhost:8080/actuator/health → {status: "UP"}
```

---

### ÉTAPE 6 — SeedingService with Spring Profile Activation (0.5 days)

**Objective**: Create profile-driven SeedingService for automatic development data seeding on Spring Boot startup (per clarification CHK086-087)

**Deliverables**:
1. **SeedingService Component** (docai-bootstrap/src/main/java/.../SeedingService.java)
   - ✅ @Component with @Profile("seed") — only active when `--spring.profiles.active=seed`
   - ✅ Implements ApplicationListener<ContextRefreshedEvent> or uses @EventListener to trigger on startup
   - ✅ Methods:
     - `seedTenants()` — Creates 3 tenants (ACME Corp, Tech Innovations Inc, Global Trade Ltd)
     - `seedUsers()` — Creates 10+ test users with roles and tenant assignments
     - `seedAll()` — Calls both (orchestrator)
   - ✅ Idempotent implementation using find-or-create pattern (per clarification CHK088):
     - Natural keys: email (User), slug (Tenant)
     - MongoDB operations: `Document.replaceOne(Filter, Document, ReplaceOptions(upsert: true))`
     - Running twice produces zero duplicates
     - No exception if entity already exists by natural key

2. **NO REST Controller** (clarification CHK086)
   - ✅ Seeding triggered exclusively by Spring profile activation, NOT HTTP endpoint
   - ✅ Developers activate via: `./mvnw spring-boot:run -Dspring.profiles.active=seed`
   - ✅ Or configure in `application-seed.yml`: `spring.profiles.active: seed`

3. **Test Data Definition** (SeedDataConstants.java)
   - ✅ 3 tenants:
     - Tenant 1: "ACME Corp" (tenant_id: `acme-corp`)
     - Tenant 2: "Tech Innovations Inc" (tenant_id: `beta-assur`)
     - Tenant 3: "Global Trade Ltd" (tenant_id: `gamma-rh`)
   - ✅ 10+ test users (realistic names, emails):
     - admin@acme-corp.test (role: ADMIN, tenant: acme-corp)
     - user1@acme-corp.test (role: USER, tenant: acme-corp)
     - [and 8+ more across tenants with realistic data]

4. **Integration with Keycloak** (deferred to Phase 3, documented in plan)
   - Note: SeedingService seeds MongoDB only in ÉTAPE 6
   - Keycloak user creation will be handled in a later phase via Keycloak admin API

**Acceptance Criteria** (per clarification CHK087):
- ✅ `docker compose up -d` (infrastructure running)
- ✅ `./mvnw spring-boot:run -Dspring.profiles.active=seed` → Application starts and seeds data automatically
- ✅ Verify in MongoDB: `docker exec mongodb mongosh --eval "db.tenants.countDocuments()"` → 3
- ✅ Verify in MongoDB: `docker exec mongodb mongosh --eval "db.users.countDocuments()"` → 10+
- ✅ Run Spring Boot twice with `seed` profile: both times produce zero duplicates
- ✅ Verify idempotency: `db.tenants.countDocuments()` still = 3 after second run
- ✅ Verify idempotency: `db.users.countDocuments()` still = 10+ after second run (per clarification CHK088)

**Validation**:
```bash
# Start infrastructure
docker compose up -d
sleep 30

# Start Spring Boot with seed profile (auto-seeding on startup)
./mvnw spring-boot:run -Dspring.profiles.active=seed

# In another terminal, verify tenants in MongoDB
docker exec mongodb mongosh --eval "db.tenants.countDocuments()"  # Expected: 3

# Verify users in MongoDB
docker exec mongodb mongosh --eval "db.users.countDocuments()"   # Expected: 10+

# Stop Spring Boot and run again with seed profile (idempotency test)
# mvnw spring-boot:run -Dspring.profiles.active=seed
# docker exec mongodb mongosh --eval "db.tenants.countDocuments()"  # Expected: still 3
curl -X POST http://localhost:8080/dev/seed

# Verify counts unchanged
docker exec mongodb mongosh --eval "db.tenants.find().count()"  # Expected: 3 (same)
docker exec mongodb mongosh --eval "db.users.find().count()"   # Expected: 10+ (same)
```

---

## Deliverables Summary

### Artifacts Created
| Artifact | Path | Responsibility | Status |
|----------|------|-----------------|--------|
| **Parent POM** | `pom.xml` | ÉTAPE 1 | ✅ Deliverable |
| **11 Module POMs** | `docai-{module}/pom.xml` | ÉTAPE 1 | ✅ Deliverable |
| **ArchUnit Test** | `docai-bootstrap/src/test/java/.../HexagonalArchitectureTest.java` | ÉTAPE 2 | ✅ Deliverable |
| **Docker Compose** | `docker-compose.yml` | ÉTAPE 3 | ✅ Deliverable |
| **MongoDB Init** | `docker/mongodb/init-replica-set.sh` | ÉTAPE 3 | ✅ Deliverable |
| **Kafka Init** | `docker/kafka/init-topics.sh` | ÉTAPE 3 | ✅ Deliverable |
| **Keycloak Realm** | `docker/keycloak/realm-docai.json` | ÉTAPE 4 | ✅ Deliverable |
| **Keycloak Init** | `docker/keycloak/init-keycloak.sh` | ÉTAPE 4 | ✅ Deliverable |
| **.env Template** | `.env.example` | ÉTAPE 5 | ✅ Deliverable |
| **Spring Config (Dev)** | `docai-bootstrap/src/main/resources/application-dev.yml` | ÉTAPE 5 | ✅ Deliverable |
| **Spring Config (Prod)** | `docai-bootstrap/src/main/resources/application-prod.yml` | ÉTAPE 5 | ✅ Deliverable |
| **SeedingService** | `docai-bootstrap/src/main/java/.../SeedingService.java` | ÉTAPE 6 | ✅ Deliverable |
| **SeedController** | `docai-adapter-in-rest/src/main/java/.../SeedController.java` | ÉTAPE 6 | ✅ Deliverable |
| **Test Data** | `docai-bootstrap/src/main/java/.../SeedDataConstants.java` | ÉTAPE 6 | ✅ Deliverable |

### Documentation
| Document | Path | Scope |
|----------|------|-------|
| **Setup Guide** | `DOCKER_SETUP.md` | Docker Compose, environment, troubleshooting |
| **ADR References** | `.specify/adr/ADR-002.md`, `ADR-006.md`, `ADR-008.md`, `ADR-010.md` | Architectural decisions |
| **CLAUDE.md Update** | `CLAUDE.md` (Development Setup section) | Commands, quick reference |

---

## Success Criteria (Module 1.A Complete)

✅ **SC-001**: All 11 Docker services start healthy within 30s  
✅ **SC-002**: Maven build completes in under 3 minutes  
✅ **SC-003**: Domain module tests pass with ≥ 90% coverage  
✅ **SC-004**: 100% ArchUnit tests pass (all 12 rules)  
✅ **SC-005**: New developer sets up environment in under 10 minutes  
✅ **SC-006**: Keycloak realm imports successfully, no duplicates on re-import  
✅ **SC-007**: SeedingService creates 3 tenants + 10 users in < 5s, idempotent  
✅ **SC-008**: Spring Boot starts within 15s, health check responds  
✅ **SC-009**: All 8 Kafka topics created automatically  
✅ **SC-010**: JWT token includes `tenant_id` claim  
✅ **SC-011**: MongoDB EXPLAIN PLAN tooling functional (mongosh + profiling)  
✅ **SC-012**: Documentation 100% complete (no TODO placeholders)  

---

## Timeline

| Étape | Duration | Start | End | Dependency |
|-------|----------|-------|-----|------------|
| 1 | 0.5d | Day 1 | Day 1 (12:00) | None |
| 2 | 0.5d | Day 1 (12:00) | Day 1 (17:00) | ÉTAPE 1 ✅ |
| 3 | 1d | Day 1 (17:00) | Day 2 (17:00) | ÉTAPE 1 ✅ |
| 4 | 0.5d | Day 2 (17:00) | Day 3 (12:00) | ÉTAPE 3 ✅ |
| 5 | 0.5d | Day 3 (12:00) | Day 3 (17:00) | ÉTAPE 3 ✅ |
| 6 | 0.5d | Day 3 (17:00) | Day 4 (12:00) | ÉTAPE 5 ✅ |
| **Total** | **3.5d** | **Day 1** | **Day 4 (12:00)** | — |

---

## Quality Gates & Validation

### Mandatory CI/CD Checks (Upon Each Étape Completion)

**ÉTAPE 1 Complete**:
```bash
./mvnw clean compile -DskipTests
# Expected: BUILD SUCCESS in < 60s, all 11 modules
```

**ÉTAPE 2 Complete**:
```bash
./mvnw test -Dtest=HexagonalArchitectureTest
# Expected: 12 rules pass, execution < 5s
```

**ÉTAPE 3 Complete**:
```bash
docker compose up -d && sleep 30 && docker compose ps
# Expected: All 11 services healthy/running
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092
# Expected: All 8 topics listed
```

**ÉTAPE 4 Complete**:
```bash
curl -I http://localhost:8180/admin/realms/docai
# Expected: 200 or 401 (both OK — auth required, but realm exists)
```

**ÉTAPE 5 Complete**:
```bash
./mvnw spring-boot:run -Dspring.profiles.active=dev &
sleep 15
curl http://localhost:8080/actuator/health
# Expected: {status: "UP"}
```

**ÉTAPE 6 Complete**:
```bash
curl -X POST http://localhost:8080/dev/seed
docker exec mongodb mongosh --eval "db.tenants.count()"
# Expected: 3 tenants, 10+ users, idempotent (same counts after 2nd seed)
```

---

## Known Constraints & Mitigation

| Constraint | Impact | Mitigation |
|-----------|--------|-----------|
| Docker image pulls depend on network | Slow initial setup | Cache images on first `docker compose pull` |
| Keycloak startup slow (30-60s) | Timeline dependency on ÉTAPE 3 | Start in ÉTAPE 1, validate in ÉTAPE 4 |
| MongoDB Replica Set requires 3+ nodes local | May not start on low-disk systems | Document min 5GB free space |
| AWS S3 credentials required | Blocks ÉTAPE 5 if missing | Generate test IAM user in advance |
| Port conflicts (8 services on localhost) | Setup failure | Document port reservation and conflict resolution |

---

## Acceptance & Sign-Off

**Module 1.A Implementation Plan ready for execution.**

- ✅ All 6 étapes detailed with deliverables
- ✅ Acceptance criteria measurable and verifiable
- ✅ Success criteria aligned with feature spec
- ✅ Constitution compliance verified (Gate 2 ✅)
- ✅ No blocker ambiguities (Phase 0 ✅)
- ✅ Timeline realistic (3.5 days)
- ✅ Quality gates automated (CI/CD checks defined)

**Next action**: Execute ÉTAPE 1 (Maven structure) or run `/speckit-tasks` to generate detailed micro-task breakdown.

---

**Plan Status**: ✅ READY FOR IMPLEMENTATION  
**Last Updated**: 2026-05-24  
**Plan Version**: 1.0
