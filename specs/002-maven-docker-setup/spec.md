# Feature Specification: Maven Multi-Module & Docker Compose Infrastructure

**Feature Branch**: `002-maven-docker-setup`  
**Created**: 2026-05-24  
**Status**: Draft  
**Input**: Module 1.A — Structure Maven + Docker Compose | Partie 1 — Setup & CI/CD

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Developer Initializes Local Environment (Priority: P1)

A developer clones the DocAI repository and wants to set up a complete, production-like local development environment with all infrastructure services running.

**Why this priority**: This is the critical path—without successful environment initialization, no development can proceed. All developers must be able to do this independently.

**Independent Test**: Can be fully tested by: (1) Cloning repository, (2) Running docker-compose setup, (3) Verifying all 11 services are healthy and accessible on their documented ports.

**Acceptance Scenarios**:

1. **Given** a fresh clone of the repository, **When** developer executes `docker compose up -d`, **Then** all 11 services start successfully
2. **Given** all services are running, **When** developer checks service health with `docker compose ps`, **Then** all services show "healthy" or "running" status
3. **Given** environment is initialized, **When** developer accesses documented service URLs, **Then** each service responds correctly:
   - Keycloak at http://localhost:8180/admin
   - Kafka UI at http://localhost:8080
   - Grafana at http://localhost:3000
   - MongoDB at mongodb://localhost:27017
   - Prometheus at http://localhost:9090

---

### User Story 2 - Developer Builds Backend Application (Priority: P1)

A developer wants to build the entire 11-module Maven project with correct dependency hierarchy and module organization.

**Why this priority**: Required for any backend development. Hexagonal architecture isolation depends on correct module structure and dependency management.

**Independent Test**: Can be fully tested by running: `./mvnw clean package` and verifying (1) all 11 modules compile, (2) no circular dependencies, (3) domain module has zero external dependencies.

**Acceptance Scenarios**:

1. **Given** 11 Maven modules with hexagonal architecture, **When** developer runs `./mvnw clean package`, **Then** all modules compile successfully
2. **Given** modules are compiled, **When** running `./mvnw dependency:tree`, **Then** no circular dependency warnings appear
3. **Given** parent pom.xml defines module versions, **When** checking domain module, **Then** domain module has zero Spring/MongoDB/Kafka/AWS dependencies

---

### User Story 3 - Developer Configures Keycloak Realm (Priority: P1)

A developer needs to configure Keycloak with the docai realm, authentication flow, roles, and protocol mappers for JWT tenant_id claim.

**Why this priority**: Authentication and multi-tenancy are foundational—all microservices depend on this. Critical for secure local testing.

**Independent Test**: Can be fully tested by: (1) Importing realm-docai.json, (2) Creating test user, (3) Obtaining JWT token, (4) Verifying JWT contains tenant_id claim.

**Acceptance Scenarios**:

1. **Given** Keycloak is running, **When** developer imports `realm-docai.json`, **Then** realm "docai" is created with 5 configured roles
2. **Given** realm is imported, **When** developer creates/logs in as test user, **Then** JWT token is issued
3. **Given** JWT token is obtained, **When** decoding the token, **Then** JWT claims include tenant_id field with assigned tenant value

---

### User Story 4 - Developer Seeds Development Data (Priority: P2)

A developer wants to populate local database with realistic test data (3 tenants, 10+ users) for local testing without manual setup.

**Why this priority**: Enables faster iteration on features; avoids repetitive manual data creation. Dependency on Stories 1-3 existing (infrastructure and Keycloak).

**Independent Test**: Can be tested independently by: Running SeedingService and verifying MongoDB contains expected tenant and user documents.

**Acceptance Scenarios**:

1. **Given** SeedingService endpoint is available, **When** developer calls seeding endpoint, **Then** 3 tenants are created in MongoDB
2. **Given** tenants are seeded, **When** querying users collection, **Then** at least 10 test users exist with correct tenant assignments
3. **Given** seeding is idempotent, **When** running seed operation twice, **Then** no duplicate data is created

---

### User Story 5 - DevOps Prepares CI/CD Pipeline Structure (Priority: P2)

DevOps/CI teams need the Maven project structure to support automated CI with separate job configurations for unit tests, integration tests, and quality gates.

**Why this priority**: Enables automated quality gates and deployment. Dependency on Story 2 (Maven structure is correct).

**Independent Test**: Can be tested by: Verifying pom.xml supports profiles for `unit-tests`, `integration-tests`, `quality-gates` that can run independently.

**Acceptance Scenarios**:

1. **Given** Maven profiles are configured, **When** running `./mvnw test -P unit-tests`, **Then** only unit tests execute (no docker dependencies)
2. **Given** Maven profiles exist, **When** running `./mvnw test -P integration-tests`, **Then** integration tests run against Docker services
3. **Given** quality profiles exist, **When** running `./mvnw test -P quality-gates`, **Then** ArchUnit, Checkstyle, and PIT tests execute

---

### Edge Cases

- What happens when a developer clones the repo on a machine where Docker is not installed? → Provide clear error message directing to Docker installation
- What happens if MongoDB Replica Set is not ready when Kafka topics are created? → Compose file uses `depends_on: {condition: service_healthy}` to enforce order
- What happens if developer runs seeding twice? → SeedingService must be idempotent; use upsert operations
- What happens if Keycloak initialization fails? → Docker Compose should fail and provide clear logs; documentation must include troubleshooting steps

---

## Requirements *(mandatory)*

### Functional Requirements

#### Maven Module Structure

- **FR-001**: Parent pom.xml MUST declare 11 child modules with hexagonal architecture using root package `fr.docai`:
  - docai-domain (pure domain, zero Spring/external dependencies; package: `fr.docai.domain`)
  - docai-application (use cases, depends only on domain; package: `fr.docai.application`)
  - docai-adapter-in-rest (REST controllers; package: `fr.docai.adapter.in.rest`)
  - docai-adapter-in-kafka (Kafka consumers; package: `fr.docai.adapter.in.kafka`)
  - docai-adapter-out-mongodb (MongoDB persistence; package: `fr.docai.adapter.out.mongodb`)
  - docai-adapter-out-kafka (Kafka producers; package: `fr.docai.adapter.out.kafka`)
  - docai-adapter-out-valkey (Redis/Valkey caching; package: `fr.docai.adapter.out.valkey`)
  - docai-adapter-out-ai (LLM/Claude adapter; package: `fr.docai.adapter.out.ai`)
  - docai-adapter-out-storage (AWS S3 storage; package: `fr.docai.adapter.out.storage`)
  - docai-adapter-out-external (external API integrations; package: `fr.docai.adapter.out.external`)
  - docai-bootstrap (Spring Boot entry point; package: `fr.docai.bootstrap`)

- **FR-002**: Parent pom.xml MUST define shared dependency versions for Java 21, Spring Boot 4.0.x, Maven 3.9+, and lock all transitive dependencies

- **FR-003**: Module pom.xml files MUST enforce dependency constraints: domain has zero external dependencies, application depends only on domain, adapters depend on domain/application

- **FR-004**: Build MUST support Maven profiles for unit-tests, integration-tests, and quality-gates that can run independently

#### Docker Compose Infrastructure

- **FR-005**: docker-compose.yml MUST define exactly 11 services:
  - MongoDB 7 (Replica Set rs0, port 27017, health check enabled)
  - Kafka 3.7 (KRaft mode, port 9092, health check enabled)
  - Apicurio Schema Registry (port 8081, health check enabled)
  - Keycloak 26 (port 8180, admin/admin credentials, health check enabled)
  - Valkey 8 (port 6379, health check enabled)
  - Prometheus (port 9090)
  - Grafana (port 3000, admin/admin credentials)
  - OpenTelemetry Collector (port 4317 OTLP)
  - Grafana Tempo (port 3200)
  - Kafka UI (port 8080)
  - [Optional DevOps service for monitoring]

- **FR-006**: Docker Compose MUST use `depends_on` with `condition: service_healthy` to enforce service initialization order: MongoDB → Keycloak → Kafka → Application

- **FR-007**: All services MUST have health check probes defined (readiness/liveness checks appropriate to service type)

- **FR-008**: docker-compose.yml MUST mount volumes for:
  - MongoDB data persistence (`mongo_data` volume)
  - Kafka data persistence (`kafka_data` volume)
  - Valkey data persistence (optional, `redis_data` volume)
  - Prometheus configuration (read-only mount of `prometheus.yml`)
  - Grafana provisioning (data sources, dashboards)
  - Tempo configuration (traces backend)

#### Keycloak Configuration

- **FR-009**: realm-docai.json file MUST be version-controlled in `docker/keycloak/realm-docai.json` and define Keycloak realm with:
  - Realm name: "docai"
  - 5 roles: admin, tenant-admin, manager, user, viewer
  - 5 test users (e.g., admin@acme-corp.test, user@acme-corp.test, etc.) with assigned roles and tenant_id User Attributes
  - OpenID Connect clients: docai-backend, docai-frontend, docai-admin
  - Keycloak Docker Compose MUST mount this file and auto-import on first startup via idempotent init script (no manual UI import required)

- **FR-010**: JWT token issued by Keycloak MUST include tenant_id claim via Protocol Mapper:
  - tenant_id stored as Keycloak User Attribute (custom attribute on user entity)
  - Protocol Mapper configuration (defined in realm-docai.json or auto-created):
    - Mapper name: "Add tenant_id"
    - Mapper Type: "User Attribute"
    - Token Claim Name: "tenant_id"
    - User Attribute Name: "tenant_id"
    - Claim JSON Type: String
    - Include in ID token: true
    - Include in access token: true
  - Mapper is configured per client (at minimum for docai-backend and docai-frontend clients)

- **FR-011**: Keycloak initialization script (init-keycloak.sh or similar) MUST be idempotent: re-importing realm-docai.json should not duplicate users or misconfigure existing realm

#### Configuration & Environment

- **FR-012**: .env.example MUST document all required environment variables:
  - JAVA_OPTS (memory settings, JVM flags)
  - Spring Boot properties (spring.application.name, server.port, etc.)
  - MongoDB connection string (MONGODB_URI)
  - Kafka broker addresses (KAFKA_BROKERS)
  - Keycloak endpoints and credentials (KEYCLOAK_ISSUER_URI, KEYCLOAK_CLIENT_ID, etc.)
  - AWS S3 credentials (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION, S3_BUCKET_NAME) — developers must configure real AWS S3 bucket, MinIO is NOT used locally
  - Valkey connection (VALKEY_HOST, VALKEY_PORT)
  - Observability (PROMETHEUS_ENABLED, OTLP_EXPORTER_ENDPOINT)

- **FR-013**: .env.example MUST include inline comments explaining each variable's purpose and example values

- **FR-014**: Runtime must read .env file via Docker Compose or Spring Boot auto-configuration; no hardcoded credentials in code

#### Seeding Service

- **FR-015**: SeedingService MUST create initial development data automatically on Spring Boot startup when the `seed` profile is active:
  - Triggered by: `--spring.profiles.active=seed` or `spring.profiles.active=seed` in application.yml
  - Creates 3 tenants (ACME Corp, Tech Innovations Inc, Global Trade Ltd) with unique tenant_ids
  - Creates 10+ test users distributed across tenants with realistic names, emails, and role assignments
  - Each user assignable to Keycloak with matching tenant_id attribute
  - No REST HTTP endpoint; activation is profile-driven only

- **FR-016**: SeedingService MUST be idempotent:
  - Seeding operation can be run multiple times without creating duplicates
  - Uses find-or-create pattern with natural keys (email for users, slug/code for tenants)
  - Upsert operations: if entity exists by natural key, skip silently (no error, no duplicate)
  - Idempotency applies to repeated profile activations and multiple Docker startup attempts

- **FR-017**: (DEPRECATED — Replaced by FR-015 clarification) 
  - SeedingService is NOT exposed via REST endpoint
  - Activation is exclusively via Spring profile `seed` at application startup

#### Code Quality & Standards

- **FR-018**: All modules MUST comply with Checkstyle configuration:
  - Max method length: 20 lines
  - Max method parameters: 4
  - Cyclomatic complexity ≤ 10
  - No trailing whitespace, proper indentation

- **FR-019**: Domain module MUST achieve test coverage ≥ 90% (measured by line coverage)

- **FR-020**: ArchUnit tests MUST validate hexagonal architecture (12 rules):
  - Domain imports no Spring, MongoDB, Kafka, or AWS classes
  - Application imports no adapter packages
  - Adapters import only domain/application, never other adapters
  - No circular dependencies between modules
  - No external dependencies leak into domain layer

- **FR-021**: Kafka topics MUST be created via separate `kafka-init` service in docker-compose:
  - kafka-init service runs `kafka-topics.sh` to create 8 topics with documentId partition key (per ADR-002)
  - Service depends on Kafka broker being healthy (`depends_on: {condition: service_healthy}`)
  - Idempotent creation: topics only created if they don't already exist
  - Topic configuration: replication factor 1 (dev), 3 partitions (default for local)

- **FR-022**: MongoDB performance analysis MUST support ADR-010 (EXPLAIN PLAN before indexing):
  - mongosh CLI tool available in MongoDB container for manual query analysis via `db.collection.explain()` 
  - MongoDB server-side profiling enabled in docker-compose dev environment (profiling level 1: log slow operations ≥ 100ms)
  - Profiling logs accessible via `docker logs mongodb | grep "command duration"` or similar monitoring
  - Developers can manually run `mongosh` inside container and execute `db.collection.find({...}).explain("executionStats")` to analyze query plans
  - Documentation includes example EXPLAIN PLAN workflow and how to identify missing indexes (ADR-010)

- **FR-023**: Documentation MUST be complete:
  - CLAUDE.md updated with development setup instructions
  - DOCKER_SETUP.md with troubleshooting guide
  - ADR-002, ADR-006, ADR-008, ADR-010 documented in .specify/adr/ directory
  - ADR-010 includes mongosh explain() examples and MongoDB profiling configuration reference

### Key Entities *(data model)*

- **Tenant**: Represents a customer organization with unique tenant_id, name, configuration
  - Attributes: id, name, status, created_at, plan_type
  
- **User**: Represents a human user in the system with assigned tenant and roles
  - Attributes: id, username, email, tenant_id, roles, created_at, last_login
  
- **Keycloak Realm**: Logical security boundary containing users, roles, and clients
  - Configuration: realm name, token expiration, password policies, protocol mappers

- **Service Health**: Represents health status of infrastructure services
  - Attributes: service_name, status (healthy/unhealthy), last_checked_at

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 11 Docker Compose services start successfully and show "healthy" status within 30 seconds of `docker compose up -d`

- **SC-002**: Maven build completes successfully in under 3 minutes on developer machine (with clean caches) with all 11 modules compiling

- **SC-003**: All domain module tests pass and achieve ≥ 90% code coverage without requiring external services

- **SC-004**: 100% of ArchUnit tests pass, confirming hexagonal architecture constraints are enforced

- **SC-005**: New developer can set up complete environment in under 10 minutes following documentation: clone → docker compose up → mvnw clean package → access Keycloak

- **SC-006**: Keycloak realm initialization succeeds on first import with all 5 roles and 5 test users created; importing twice produces no duplicates

- **SC-007**: SeedingService creates 3 tenants and 10+ users in under 5 seconds; running seeding twice produces no duplicates

- **SC-008**: Spring Boot application starts within 15 seconds with health check responding at /actuator/health

- **SC-009**: All 8 Kafka topics (per ADR-002) are created automatically via `kafka-init` service on first docker-compose startup with correct partition key (documentId)

- **SC-010**: JWT token obtained from Keycloak includes tenant_id claim; token can be decoded and verified by Spring Boot security layer

- **SC-011**: MongoDB EXPLAIN PLAN tooling is functional (ADR-010):
  - mongosh is accessible inside MongoDB Docker container
  - Developer can run `docker exec -it mongodb mongosh` and execute `db.collection.explain()` commands
  - MongoDB profiling is enabled in dev environment (level 1, capturing slow operations ≥ 100ms)
  - Profiling logs are captured and accessible for query performance analysis

- **SC-012**: Documentation is 100% complete: setup guide covers all prerequisites, commands, troubleshooting; no "TODO" or "[NEEDS HELP]" placeholders remain

---

## Clarifications

### Session 2026-05-24 (Initial, 7 questions)

- Q1: Java package root → A: `fr.docai` for all 11 modules
- Q2: S3 storage strategy → A: Real AWS S3 with dev credentials (no MinIO)
- Q3 (SUPERSEDED): SeedingService timing → A: ~~On-demand REST endpoint~~ (see CHK086-087 below)
- Q4: Keycloak realm-docai.json management → A: JSON versioned in `docker/keycloak/`, mounted as volume, imported automatically via idempotent init script
- Q5: Kafka topics creation → A: Separate `kafka-init` service in docker-compose (not application auto-creation)
- Q6: Keycloak tenant_id configuration → A: User Attribute + Protocol Mapper to JWT claim (standard Keycloak configuration)
- Q7: MongoDB EXPLAIN PLAN tool (ADR-010) → A: Combined approach—mongosh explain() for manual analysis + MongoDB profiling enabled in dev profile

### Session 2026-05-24 (Ambiguity Resolution, 4 items CHK085-088)

- **CHK085**: Real AWS S3 vs. local development setup → **A**: Developers configure real AWS S3 credentials in `.env` (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION, S3_BUCKET_NAME). MinIO removed entirely from docker-compose.yml. LocalStack/TestContainers reserved for CI integration tests only.

- **CHK086**: SeedingService activation method (HTTP endpoint vs. Spring profile) → **A**: No REST endpoint. Activation exclusively via Spring profile flag: `--spring.profiles.active=seed` or configured in `application.yml`. Developers control seeding via JVM argument or property file.

- **CHK087**: Auto-seeding vs. on-demand seeding → **A**: Auto-seeding on Spring Boot startup when `seed` profile is active. No on-demand HTTP trigger. Eliminates idempotency testing burden for repeated manual runs; seeding happens once per application boot.

- **CHK088**: Idempotent seeding definition (zero vs. acceptable duplicates) → **A**: Exact zero duplicates via find-or-create pattern using natural keys (email for User, slug/code for Tenant). If entity exists, skip silently with no error or duplicate creation.

---

## Assumptions

- Developers have Docker Desktop or Docker Engine + Docker Compose installed (version ≥ 2.0)
- Java 21 JDK is installed and available in system PATH
- Maven 3.9+ is installed; `./mvnw` wrapper is included in repository
- Developers use either macOS, Windows (with WSL2), or Linux; Windows-native batch scripts are out of scope for v1
- MongoDB Replica Set is mandatory for transactions; single-instance MongoDB not supported
- Keycloak realm can be imported from JSON file; manual configuration via UI is not part of automated setup
- AWS S3 credentials are provided via environment variables (not AWS credentials file); real AWS S3 bucket must be configured (no MinIO mock service)
- Network ports 3000, 3200, 4317, 6379, 8080, 8081, 8180, 9090, 9092, 27017 are available on developer machine
- No proxy or VPN restrictions block Docker image pulls from Docker Hub
- SeedingService is a Spring Bean/REST endpoint; no separate external seed script needed
- All infrastructure services (11 services) must be running; partial environments are not supported for development
