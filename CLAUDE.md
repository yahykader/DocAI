


# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**DocAI** is a B2B SaaS platform for intelligent document processing that automates reading, understanding, and verification of enterprise documents. The platform identifies document types, extracts key data, validates against official registries, and detects anomalies or potential fraud.

- **Status**: Specification Phase (Architecture & Planning complete)
- **Backend Spec**: `DOCAI_BACKEND_MASTER_SPECKIT_F.md` (Reference spec, technical foundation)
- **Frontend Spec**: `DOCAI_FRONTEND_MASTER_SPECKIT_V4.md` (Angular application)
- **Implementation Plan**: `DOCAI_BACKEND_MASTER_SPECKIT_F_V2.md` (Micro-task breakdown, max 1 day each)

---

## Architecture Overview

### Backend Architecture (Java/Spring Boot)

**Hexagonal Architecture** (Clean Code / Port & Adapter Pattern) with 11 Maven modules:

```
docai-domain              → Pure domain model (DDD), zero dependencies
docai-application         → UseCases/Application Services layer
docai-adapter-in-rest     → REST Controller adapters
docai-adapter-in-kafka    → Kafka Consumer adapters (events)
docai-adapter-out-mongodb → MongoDB persistence adapter
docai-adapter-out-kafka   → Kafka Producer adapters
docai-adapter-out-valkey  → Redis/Valkey caching adapter
docai-adapter-out-ai      → LLM/AI provider adapters (Claude, etc.)
docai-adapter-out-storage → AWS S3 adapter
docai-adapter-out-external → External API integrations (INSEE, BAN, RPPS)
docai-bootstrap           → Spring Boot entry point, configuration
```

**Key Principles**:
- **Domain-Driven Design (DDD)**: Bounded contexts for Document, Extraction, Fraud, Pipeline
- **Event-Driven**: Kafka for inter-service async communication
- **CQRS Pattern**: Separate read/write models where applicable
- **Testing**: ArchUnit validates hexagonal architecture (12 rules), PIT mutation testing (85% threshold)
- **Code Standards**: Checkstyle, max method length 20 lines, max params 4, cyclomatic complexity ≤ 10

### Frontend Architecture (Angular 21)

**Feature-First Architecture** with:
- **State Management**: NgRx 21 (Store, Effects, Entity Adapter, RouterStore, ComponentStore)
- **Component Pattern**: Smart (container) / Dumb (presentational) components
- **Styling**: TailwindCSS 4 + Angular Material 21
- **Auth**: Keycloak-Angular 21 integration with JWT & tenant_id claim
- **Change Detection**: OnPush everywhere
- **Testing**: BDD with Jasmine/Karma, Storybook for components

**Feature Modules** (development order):
1. **Setup & Core** - Project bootstrap, routing, standards
2. **Commons** - Shared Angular libraries (interceptors, guards, pipes, directives, services)
3. **Foundations** - Auth (signup/login/2FA), RGPD, Billing
4. **Pipeline** - Document upload, extraction visualization, fraud detection, monitoring
5. **Product** - Dashboard, API management, billing UI

---

## Technology Stack

### Backend
- **Runtime**: Java 21 + Spring Boot 4.0.x
- **Build**: Maven 3.9+ with 11 modular POMs
- **Database**: MongoDB 7 (with Replica Set for transactions)
- **Cache**: Valkey 8 (Redis compatible)
- **Message Queue**: Apache Kafka 3.7 (KRaft mode)
- **Schema Registry**: Apicurio Schema Registry
- **Authentication**: Keycloak 26 (OpenID Connect/OAuth2)
- **Storage**: AWS S3
- **AI/LLM**: Claude API (and others via adapter pattern)
- **Observability**: Prometheus, Grafana, OpenTelemetry, Grafana Tempo
- **Quality**: SonarCloud, ArchUnit, PIT, Checkstyle

### Frontend
- **Framework**: Angular 21 (TypeScript 5.x strict mode)
- **State**: NgRx 21 with Entity Adapter
- **Styling**: TailwindCSS 4 + Angular Material 21
- **Reactivity**: RxJS 7 + Signals
- **Auth**: Keycloak-Angular 21
- **UI Components**: Storybook, Material components
- **HTTP**: HttpClient with interceptors
- **i18n**: ngx-translate (prepared, backlog v2)

### Infrastructure (Docker Compose)
All services run via `docker-compose` locally:
- MongoDB 7 (Replica Set, port 27017)
- Kafka 3.7 + KRaft (ports 9092)
- Kafka UI (port 8080)
- Apicurio Schema Registry (port 8081)
- Keycloak 26 (port 8180)
- Valkey 8 (port 6379)
- Prometheus (port 9090)
- Grafana (port 3000)
- OpenTelemetry Collector (port 4317)
- Grafana Tempo (port 3200)

---

## Development Setup

### Prerequisites
- Java 21 (JDK)
- Maven 3.9+
- Docker & Docker Compose
- Node.js 20+ (for Angular frontend)
- Git (with conventional commits)

### Initial Setup

1. **Clone & Navigate**
   ```bash
   git clone <repo> && cd DocAI
   ```

2. **Start Infrastructure** (runs all Docker Compose services)
   ```bash
   docker compose up -d
   ```
   Verify all services healthy:
   ```bash
   docker compose ps  # all should be "healthy" or "running"
   ```
   Access services:
   - Keycloak: http://localhost:8180/admin (admin/admin)
   - Kafka UI: http://localhost:8080
   - Grafana: http://localhost:3000 (admin/admin)
   - Prometheus: http://localhost:9090

3. **Configure Environment** (backend)
   ```bash
   cp .env.example .env  # Edit with local AWS credentials, Keycloak secrets, etc.
   ```

4. **Keycloak Setup**
   - Import `realm-docai.json` (if provided)
   - Verify realm `docai` exists with clients: `docai-backend`, `docai-frontend`, `docai-admin`
   - Verify JWT token includes `tenant_id` claim

### Backend Commands

**All Maven commands run from `backend/` directory** (or use `-f backend/pom.xml` from root):

```bash
# Navigate to backend directory
cd backend

# Build entire project
mvn clean package

# Build specific module
mvn clean package -pl docai-domain

# Run tests for domain (highest coverage requirement 90%)
mvn test -pl docai-domain

# Run all tests
mvn clean test

# Run Checkstyle validation
mvn checkstyle:check

# Run ArchUnit architecture tests (12 hexagonal rules)
mvn test -pl docai-bootstrap -Dtest=HexagonalArchitectureTest

# Run PIT mutation testing (domain only, threshold 85%)
MAVEN_OPTS=-Xmx1g mvn pit:mutationCoverage -pl docai-domain

# Start Spring Boot application (after docker compose up)
mvn spring-boot:run -pl docai-bootstrap

# Access Swagger API docs
curl http://localhost:8080/swagger-ui.html

# Check application health
curl http://localhost:8080/actuator/health

# View logs
docker compose logs -f <service-name>  # e.g., mongo, kafka, keycloak
```

**From root directory** (alternative):
```bash
mvn -f backend/pom.xml clean package
mvn -f backend/pom.xml spring-boot:run -pl docai-bootstrap
```

### Frontend Commands

```bash
# Install dependencies
npm install

# Start dev server (ng serve)
npm start  # http://localhost:4200

# Run unit tests
npm test

# Run e2e tests (if configured)
npm run e2e

# Build for production
npm run build

# Run Storybook (component documentation)
npm run storybook

# Lint code
npm run lint

# Format code
npm run format
```

---

## CI/CD & Testing Strategy

**DocAI uses a 3-tier CI/CD approach** with Maven profiles enforcing quality gates at each level.

### Job 1: Unit Tests + ArchUnit (Fast Feedback)

```bash
cd backend
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests
```

**What runs**:
- JUnit 5 unit tests (`*Test.java`, `*Tests.java`)
- ArchUnit 12 hexagonal architecture rules
- Excludes integration tests (`*IT.java`)

**Coverage Requirements**:
- Global: ≥ 80%
- Domain: ≥ 90%

**Duration**: ~2-3 minutes

### Job 2: Integration Tests (Real Services)

```bash
cd backend
MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests
```

**What runs**:
- Integration tests (`*IT.java`, `*ITs.java`)
- Cucumber BDD scenarios
- TestContainers (MongoDB, Kafka in Docker)
- WireMock stubs for external APIs
- Excludes unit tests

**Requirements**:
- Docker daemon running
- No external service calls needed (TestContainers provide isolation)

**Duration**: ~5-10 minutes

### Job 3: Quality Gates (Code Standards + Mutations)

```bash
cd backend
MAVEN_OPTS=-Xmx1g mvn clean verify -P quality-gates
```

**What runs**:
1. Unit tests (for coverage metrics)
2. Checkstyle (max 20-line methods, 4 params, complexity ≤ 10)
3. **PIT Mutation Testing**: Domain ≥ 85%, Global ≥ 80%
4. JaCoCo coverage reports
5. SonarCloud analysis

**Note**: Use `-Xmx1g` or `-Xmx2g` for PIT mutation testing (memory intensive)

**Duration**: ~8-15 minutes

### Local Development Workflow

Run tests before committing:

```bash
cd backend

# 1. Fast unit tests (2-3 min)
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests

# 2. Integration tests (5-10 min, if modifying adapters)
MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests

# 3. Full quality gates (8-15 min, before PR)
MAVEN_OPTS=-Xmx1g mvn clean verify -P quality-gates
```

---

## Development Workflow

### Micro-Task Approach
Each implementation task is designed for **1 day maximum**. See `DOCAI_BACKEND_MASTER_SPECKIT_F_V2.md` for the complete breakdown.

**Mandatory Development Order** (each depends on previous):
1. **Part 1 — Setup** (~2 weeks): Maven structure, Docker, Keycloak, seeding, quality standards
2. **Part 2 — Commons** (~2 weeks): 7 shared components (filters, mappers, validators, exceptions, audit, performance, shell)
3. **Part 3 — Foundations** (~4 weeks): Security, multi-tenancy, authentication, RGPD, billing
4. **Part 4 — Pipeline** (~14 weeks): Modules 1-4 (document recognition, extraction, fraud, orchestration)
5. **Part 5 — Product** (~7 weeks): Dashboard, API management, billing UI

### Git Workflow
- **Convention**: Use Conventional Commits (feat:, fix:, refactor:, docs:, test:, chore:)
- **Branching**: Feature branches from `main`
- **PR Size**: Align PRs with micro-tasks (1-day chunks)
- **Review**: All PRs require code review + passing CI

### Code Quality Gates
- **Coverage**: Global ≥ 80%, Domain ≥ 90%
- **SonarCloud**: 0 bugs, 0 vulnerabilities, ≤ 3% duplication
- **ArchUnit**: 12 hexagonal architecture rules must pass
- **PIT**: Domain mutation score ≥ 85%
- **Checkstyle**: All conventions checked

### Key Directories

**Backend** (located in `backend/` subdirectory):
- `backend/pom.xml` → Parent Maven POM (11 modules)
- `backend/docai-domain/` → Domain model, entities, value objects, ports (90% coverage target)
- `backend/docai-application/` → Use cases, application services, DTOs
- `backend/docai-adapter-in-rest/` → REST controller adapters
- `backend/docai-adapter-in-kafka/` → Kafka consumer adapters
- `backend/docai-adapter-out-**/` → Outbound adapters (MongoDB, Kafka, Valkey, AI, Storage, External)
- `backend/docai-bootstrap/` → Spring Boot entry point, ArchUnit tests, configuration
- `.specify/` → Speckit specifications and templates

**Frontend** (to be created):
- `src/app/` → Main application code
- `src/app/shared/` → Shared libraries, guards, interceptors, pipes
- `src/app/features/` → Feature modules (auth, pipeline, dashboard, etc.)
- `src/app/core/` → Core services, stores (NgRx)
- `.storybook/` → Storybook configuration

---

## Important References

### Specification Files
- **Backend Spec** (complete technical foundation): `DOCAI_BACKEND_MASTER_SPECKIT_F.md`
- **Implementation Plan** (micro-tasks, max 1 day each): `DOCAI_BACKEND_MASTER_SPECKIT_F_V2.md`
- **Frontend Spec** (Angular architecture): `DOCAI_FRONTEND_MASTER_SPECKIT_V4.md`

### Speckit Configuration
- `.specify/init-options.json` → Speckit setup (Claude AI integration, sequential branching)
- `.specify/templates/` → Spec, plan, tasks, checklist templates
- `.specify/workflows/` → Workflow registry for CI/CD

### Key Services & Ports
| Service | Port | URL |
|---------|------|-----|
| Spring Boot API | 8080 | http://localhost:8080 |
| Swagger/OpenAPI | 8080 | http://localhost:8080/swagger-ui.html |
| Angular Frontend | 4200 | http://localhost:4200 |
| Keycloak | 8180 | http://localhost:8180/admin |
| Kafka UI | 8080 | http://localhost:8080 |
| Grafana | 3000 | http://localhost:3000 |
| MongoDB | 27017 | mongodb://localhost:27017 |

### Key Credentials (Development Only)
- **Keycloak Admin**: admin / admin
- **Grafana Admin**: admin / admin
- **Test User**: admin@acme-corp.test / Test1234! (imported from realm-docai.json)

---

## Architecture Decision Records (ADR)

- **Hexagonal Architecture**: Ensures domain isolation, testability, and technology independence
- **Event-Driven with Kafka**: Scales to millions of documents, supports async processing
- **CQRS Patterns**: Optimizes read and write models separately
- **DDD Bounded Contexts**: Clear domain isolation (Document, Extraction, Fraud, Pipeline)
- **Multi-Tenancy**: Built into security layer (Keycloak realm roles, tenant_id JWT claim)

---

## Common Development Scenarios

### Adding a New Use Case
1. Define domain entity in `docai-domain/src/main/java/com/docai/domain/`
2. Create port interfaces in same module
3. Implement use case in `docai-application/`
4. Add REST adapter in `docai-adapter-in-rest/` (if needed)
5. Add tests in domain (90% coverage target)
6. Run ArchUnit tests to verify architecture compliance

### Adding a New External Integration
1. Create port interface in `docai-domain/` (e.g., `BankAccountValidator`)
2. Implement adapter in `docai-adapter-out-external/` 
3. Add configuration in `docai-bootstrap/`
4. Mock in tests using port interface

### Database/Query Changes
1. Update MongoDB schema in `docai-adapter-out-mongodb/`
2. Consider read/write model separation (CQRS) if optimizing queries
3. Update domain entities if needed
4. Add migration if changing existing data

### Adding Frontend Feature
1. Create feature module under `src/app/features/`
2. Add NgRx store (if state needed) in `+store/`
3. Create smart component (container) in `containers/`
4. Create dumb components in `components/`
5. Add routing in feature's routing module
6. Document component in Storybook

---

## Debugging & Troubleshooting

### Services not starting
```bash
# Check Docker Compose status
docker compose ps

# View specific service logs
docker compose logs -f mongodb
docker compose logs -f kafka

# Restart all services
docker compose restart
```

### Maven build fails
```bash
cd backend

# Clean and rebuild
mvn clean install -DskipTests

# Check dependency conflicts
mvn dependency:tree

# From root directory
mvn -f backend/pom.xml clean install -DskipTests
```

### OutOfMemoryError during tests
```bash
cd backend

# Job 1 or 2: Increase to 1GB
MAVEN_OPTS=-Xmx1g mvn clean test -P unit-tests

# Job 3 (PIT mutation testing): Use 1-2GB
MAVEN_OPTS=-Xmx2g mvn clean verify -P quality-gates
```

### Tests fail locally but pass in CI
- Verify Keycloak realm imported correctly
- Check MongoDB Replica Set is healthy: `docker compose logs mongodb`
- Ensure `.env` file has correct credentials
- Verify JUnit 5 and Mockito versions align across modules

### Spring Boot won't start
- Check port 8080 is free
- Verify all docker-compose services are healthy
- Review application logs: `docker compose logs docai-backend` (when implemented)

---

## Next Steps for Implementation

1. **Part 1 — Setup**: Initialize Maven structure, docker-compose, Keycloak, seeding service
2. **Part 2 — Commons**: Build shared filters, mappers, validators, exception handlers
3. **Part 3 — Foundations**: Implement authentication, multi-tenancy, RGPD compliance
4. **Part 4 — Pipeline**: Implement document processing modules (recognition, extraction, fraud, orchestration)
5. **Part 5 — Product**: Build dashboard, API management, billing interfaces

See implementation plan in `DOCAI_BACKEND_MASTER_SPECKIT_F_V2.md` for detailed micro-task breakdown.

---

## Additional Resources

- **Speckit Help**: `.specify/` directory contains templates and workflow configuration
- **Backend Spec Complete**: `DOCAI_BACKEND_MASTER_SPECKIT_F.md` (technical authority)
- **Implementation Plan**: `DOCAI_BACKEND_MASTER_SPECKIT_F_V2.md` (daily tasks)
- **Frontend Spec**: `DOCAI_FRONTEND_MASTER_SPECKIT_V4.md` (Angular architecture)

---

<!-- SPECKIT START -->
**Active Feature Plan**: `specs/003-stack-technique/plan.md` (Module B — Stack Technique & Intégrations)  
**Branch**: `004-stack-technique` | **Status**: Plan complete → next `/speckit-tasks`
<!-- SPECKIT END -->

**Last Updated**: 2026-05-25  
**Project Stage**: Architecture & Planning Complete | Ready for Implementation Phase 1  
**Documentation**: Updated with CI/CD jobs, Maven working directory clarity, and testing strategy
