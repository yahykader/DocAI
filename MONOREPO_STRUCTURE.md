# DocAI Monorepo Structure

**Status**: ✅ Restructured as monorepo (2026-05-24)

---

## Directory Layout

```
DocAI/
├── backend/                              # Java/Spring Boot Backend
│   ├── docai-domain/                     # DDD domain model (ZERO external deps)
│   ├── docai-application/                # Application services / use cases
│   ├── docai-adapter-in-rest/            # REST controller adapters
│   ├── docai-adapter-in-kafka/           # Kafka consumer adapters
│   ├── docai-adapter-out-mongodb/        # MongoDB persistence adapter
│   ├── docai-adapter-out-kafka/          # Kafka producer adapter
│   ├── docai-adapter-out-valkey/         # Valkey/Redis cache adapter
│   ├── docai-adapter-out-ai/             # AI/LLM provider adapters
│   ├── docai-adapter-out-storage/        # AWS S3 adapter
│   ├── docai-adapter-out-external/       # External API integrations
│   ├── docai-bootstrap/                  # Spring Boot entry point
│   └── pom.xml                           # Parent Maven POM
│
├── frontend/                             # Angular Frontend (empty for now)
│   └── .gitkeep
│
├── docker-compose.yml                    # Docker Compose (11 services)
├── prometheus.yml                        # Prometheus scrape config
├── tempo.yml                             # Grafana Tempo config
├── realm-docai.json                      # Keycloak realm export
├── grafana/
│   └── provisioning/
│       └── datasources/
│           └── prometheus.yml            # Grafana datasources config
│
├── .dockerignore                         # Docker ignore patterns
├── .env.example                          # Environment variables template
├── .gitignore
├── CLAUDE.md                             # Project instructions
├── DOCKER_COMPOSE.md                     # Docker Compose documentation
├── MONOREPO_STRUCTURE.md                 # This file
│
├── .specify/                             # Speckit configuration
├── specs/                                # Specification documents
└── .git/                                 # Git repository

```

---

## Backend Development

### Build Commands

```bash
# From root: build entire backend
mvn -f backend/pom.xml clean package

# Or: cd into backend first
cd backend
mvn clean package
```

### Specific Module Commands

```bash
cd backend

# Build domain module only
mvn clean package -pl docai-domain

# Run domain tests (90% coverage requirement)
mvn test -pl docai-domain

# Run all tests
mvn clean test

# Run Checkstyle validation
mvn checkstyle:check

# Run ArchUnit architecture tests
mvn test -pl docai-bootstrap -Dtest=HexagonalArchitectureTest

# Run PIT mutation testing (domain, 85% threshold)
mvn pit:mutationCoverage -pl docai-domain

# Start Spring Boot application (after docker compose up)
mvn spring-boot:run -pl docai-bootstrap
```

### CI/CD Profiles (from backend/)

```bash
cd backend

# Unit tests only (fast feedback)
mvn clean test -P unit-tests

# Integration tests (TestContainers)
mvn clean test -P integration-tests

# Quality gates (Checkstyle + PIT + JaCoCo + SonarCloud)
mvn clean test -P quality-gates
```

---

## Frontend Development (Phase 2)

`/frontend` directory is currently empty and will be populated in Phase 2 with:
- Angular 21 project structure
- NgRx store (state management)
- Feature modules
- Shared components and services
- Storybook documentation

Expected structure:
```
frontend/
├── src/
│   ├── app/
│   │   ├── core/          # Core services (auth, http)
│   │   ├── shared/        # Shared components, guards, pipes
│   │   └── features/      # Feature modules
│   ├── assets/
│   └── styles/
├── package.json
└── angular.json
```

---

## Docker Compose Services

All services run from the project root:

```bash
# Start all services
docker compose up -d

# Verify services are healthy
docker compose ps

# View logs
docker compose logs -f

# Stop all services
docker compose down
```

**Services**:
- MongoDB 7.0 (Replica Set) — port 27017
- Kafka 3.7 (with Zookeeper) — port 9092
- Kafka UI — port 8090
- Apicurio Schema Registry — port 8081
- Valkey 8 (Redis-compatible) — port 6379
- Keycloak 26 — port 8180
- Prometheus — port 9090
- Grafana — port 3000
- Grafana Tempo (OTLP integrated) — ports 3200, 4317, 4318

---

## Key Decisions

### Why Monorepo?

1. **Synchronized API evolution** — Backend API changes and frontend updates in one PR
2. **Unified CI/CD pipeline** — Single build, test, and deployment workflow
3. **Easier integration testing** — Backend + frontend E2E tests in same environment
4. **Single git history** — One repository to clone, one version number
5. **Clear separation** — `/backend` and `/frontend` directories keep concerns isolated

### Why Not Polyrepo?

- ❌ Harder to keep API contracts synchronized
- ❌ Complex release coordination (multiple repos, versions)
- ❌ Duplicate CI/CD configuration
- ❌ Two repositories to maintain and onboard developers into

---

## Development Workflow

### Backend Development

1. **Start infrastructure**:
   ```bash
   docker compose up -d
   docker compose logs -f  # Wait for healthy status
   ```

2. **Build backend**:
   ```bash
   cd backend
   mvn clean package
   ```

3. **Run tests**:
   ```bash
   cd backend
   mvn clean test                 # All tests
   mvn test -P unit-tests         # Fast feedback
   mvn test -pl docai-domain      # Domain only (90% coverage)
   ```

4. **Start application**:
   ```bash
   cd backend
   mvn spring-boot:run -pl docai-bootstrap
   # Access API: http://localhost:8080/swagger-ui.html
   ```

5. **Monitor**:
   - Grafana: http://localhost:3000 (admin/admin)
   - Tempo: http://localhost:3200 (traces)
   - Prometheus: http://localhost:9090 (metrics)

---

## Hexagonal Architecture (Backend)

Layer dependencies:
```
domain (pure, zero external deps)
  ↑ (depends on)
application (use cases, orchestration)
  ↑ (depends on)
adapters:
  - adapter-in-rest (HTTP)
  - adapter-in-kafka (async events)
  - adapter-out-* (persistence, external APIs)
  ↑ (depends on)
bootstrap (Spring Boot composition root)
```

**ArchUnit enforces 12 rules** (run at every test):
- Domain → NO Spring, MongoDB, Kafka, AWS SDK, Valkey
- Application → NO adapter dependencies
- Adapters → Only domain/application, no cross-adapter coupling
- NO cyclic dependencies

---

## Environment Setup

```bash
# Copy environment template
cp .env.example .env

# Edit with your settings:
# - AWS credentials (for S3)
# - Keycloak realm settings
# - Database connection strings
```

---

## Next Steps

1. ✅ **Phase 1** — Setup complete (Maven, Docker, Keycloak)
2. ⏳ **Phase 2** — Backend Commons + Frontend bootstrap
3. **Phase 3** — Authentication & Security
4. **Phase 4** — Document Pipeline
5. **Phase 5** — Dashboard & Product features

---

**Last Updated**: 2026-05-24  
**Project Stage**: Ready for Phase 2 (Backend Commons + Frontend Setup)
