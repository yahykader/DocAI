# DocAI - Quick Start Guide

**Status**: Maven multi-module structure ready (11 modules, hexagonal architecture)

---

## 1️⃣ Prerequisites

- **Java 21**: [Download JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- **Maven 3.9+**: `mvn -v` (or use `./mvnw` if available)
- **Docker & Docker Compose**: For infrastructure (MongoDB, Kafka, Keycloak, Redis, etc.)
- **Git**: Already initialized ✅

---

## 2️⃣ Start Infrastructure (Docker Compose)

```bash
# Start all services (MongoDB Replica Set, Kafka, Keycloak, Redis, Prometheus, Grafana)
docker compose up -d

# Verify all are healthy
docker compose ps

# View logs
docker compose logs -f mongodb
docker compose logs -f kafka
```

**Wait for all services to be "healthy" before proceeding.**

---

## 3️⃣ Configure Local Environment

```bash
# Copy environment template
cp .env.example .env

# Edit .env with local credentials (optional for local dev)
# Database/Keycloak credentials are already in docker-compose.yml
```

---

## 4️⃣ Verify Maven Structure

```bash
# Validate all POMs
mvn validate

# Check dependencies
mvn dependency:tree -pl docai-domain

# List all modules
mvn help:describe
```

---

## 5️⃣ Run Tests (3 CI Jobs)

### Job 1: Fast Unit Tests + ArchUnit
```bash
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests
```
**Expected**: ~2-3 minutes  
**Tests**: JUnit + 12 ArchUnit hexagonal architecture rules  
**Coverage**: Global ≥80%, Domain ≥90%

### Job 2: Integration Tests (with TestContainers)
```bash
# Requires Docker daemon
MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests
```
**Expected**: ~5-10 minutes  
**Tests**: *IT.java tests, BDD scenarios, real MongoDB/Kafka  
**Setup**: TestContainers handle everything

### Job 3: Quality Gates (Code Quality + Mutations)
```bash
# Slow but comprehensive
MAVEN_OPTS=-Xmx512m mvn clean verify -P quality-gates
```
**Expected**: ~8-15 minutes  
**Checks**: Checkstyle, PIT mutations, JaCoCo coverage, SonarCloud

---

## 6️⃣ Build & Run Application

```bash
# Build all modules
mvn clean package

# Build only bootstrap (entry point)
mvn clean package -pl docai-bootstrap

# Run Spring Boot application
mvn spring-boot:run -pl docai-bootstrap

# Access Swagger UI
curl http://localhost:8080/swagger-ui.html
```

---

## 7️⃣ Access Services

Once application is running:

| Service | URL | Login |
|---------|-----|-------|
| **API** | http://localhost:8080/swagger-ui.html | (JWT auth) |
| **Keycloak** | http://localhost:8180/admin | admin / admin |
| **Grafana** | http://localhost:3000 | admin / admin |
| **Prometheus** | http://localhost:9090 | — |
| **Kafka UI** | http://localhost:8080 | — |
| **MongoDB** | mongodb://localhost:27017 | admin / password |

---

## 8️⃣ Module Structure

```
docai/ (11 modules)
├── docai-domain                  ← Pure DDD (ZERO external deps)
├── docai-application             ← Use cases & services
├── docai-adapter-in-rest         ← REST controllers (HTTP)
├── docai-adapter-in-kafka        ← Event consumer
├── docai-adapter-out-mongodb     ← Persistence (MongoDB)
├── docai-adapter-out-kafka       ← Event producer
├── docai-adapter-out-valkey      ← Caching (Redis/Valkey)
├── docai-adapter-out-ai          ← LLM integration (Claude)
├── docai-adapter-out-storage     ← Cloud storage (AWS S3)
├── docai-adapter-out-external    ← External APIs (INSEE, BAN, RPPS)
└── docai-bootstrap               ← Spring Boot entry point
```

Each module has:
- `pom.xml` (dependencies, plugins)
- `src/main/java/` (source code)
- `src/test/java/` (unit & integration tests)

---

## 9️⃣ Development Workflow

### Add New Feature

1. **Start from domain** (`docai-domain`)
   - Define entity, value object
   - Define port (interface)
   - Write domain tests (target 90% coverage)

2. **Implement use case** (`docai-application`)
   - Create use case service
   - Map DTOs
   - Write tests (target 80% coverage)

3. **Add REST endpoint** (`docai-adapter-in-rest`)
   - Create controller
   - Add OpenAPI documentation
   - Test with MockMvc

4. **Implement persistence** (`docai-adapter-out-mongodb`)
   - Create repository implementing port
   - Add MongoDB migrations (Mongock)
   - Test with TestContainers

5. **Run architecture validation**
   ```bash
   mvn test -pl docai-bootstrap
   ```
   This runs ArchUnit 12 rules to ensure clean boundaries.

### Before Committing

```bash
# Run all 3 CI jobs locally
mvn clean test -P unit-tests                    # Fast (2-3 min)
MAVEN_OPTS=-Xmx1g mvn clean verify -P quality-gates  # Slow (8-15 min, use 1g for PIT)
```

---

## 🔟 Common Commands

```bash
# Build specific module
mvn clean package -pl docai-domain

# Run tests for specific module
mvn test -pl docai-adapter-out-mongodb

# Run only ArchUnit tests (architecture validation)
mvn test -pl docai-bootstrap -Dtest=HexagonalArchitectureTest

# View test coverage report (after running Job 3)
open docai-domain/target/site/jacoco/index.html

# View mutation testing report (after running Job 3)
open docai-domain/target/pit-reports/index.html

# Clean everything
mvn clean

# Skip tests during build
mvn clean package -DskipTests

# Build with higher memory for PIT mutation testing
MAVEN_OPTS=-Xmx2g mvn clean verify -P quality-gates
```

---

## 📚 Documentation

- **`CLAUDE.md`**: Project overview, stack, architecture
- **`CI_JOBS.md`**: 3-job CI/CD details
- **`MODULES.md`**: All 11 modules explained
- **`IMPLEMENTATION_SUMMARY.md`**: What was implemented (this session)
- **`DOCAI_BACKEND_MASTER_SPECKIT_F.md`**: Complete technical spec
- **`DOCAI_BACKEND_MASTER_SPECKIT_F_V2.md`**: Micro-task breakdown

---

## ❓ Troubleshooting

### "Maven not found"
```bash
# Check Maven version
mvn -v

# Or use Java's built-in compiler
javac -version
```

### "OutOfMemoryError in tests"
```bash
# Increase heap for PIT mutation testing
MAVEN_OPTS=-Xmx1g mvn verify -P quality-gates
```

### "Docker containers not healthy"
```bash
# Check Docker daemon
docker ps

# View container logs
docker compose logs mongodb
docker compose logs kafka

# Restart everything
docker compose down
docker compose up -d
```

### "ArchUnit tests fail on valid code"
- Ensure port interfaces in domain end with `Port` (e.g., `DocumentRepositoryPort`)
- Check adapter POMs don't import domain POMs (one-way dependency)

---

## 🎯 Next Steps

1. **Create domain entities** (docai-domain)
   - Document, Extraction, FraudAnalysis entities
   - DocumentId, TenantId value objects

2. **Write use cases** (docai-application)
   - CreateDocumentUseCase, ExtractDataUseCase, AnalyzeFraudUseCase

3. **Build REST API** (docai-adapter-in-rest)
   - DocumentController with OpenAPI docs

4. **Implement persistence** (docai-adapter-out-mongodb)
   - DocumentRepository implementing port

5. **Add event streaming** (Kafka adapters)
   - Event producers & consumers

6. **Integrate LLM** (docai-adapter-out-ai)
   - Claude API integration via LangChain4j

---

## 📞 Getting Help

- Check **CI_JOBS.md** for CI/CD issues
- Check **MODULES.md** for module-specific questions
- Review **CLAUDE.md** for architectural decisions
- See **IMPLEMENTATION_SUMMARY.md** for what's been built

---

**Ready? Start with Job 1:**
```bash
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests
```

**Good luck! 🚀**
