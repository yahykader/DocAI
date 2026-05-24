# DocAI CI/CD Jobs - ADR-008

## Overview

Three separate CI jobs enforce code quality and architectural integrity. Each job is independent and runs `MAVEN_OPTS=-Xmx512m` for memory efficiency.

---

## Job 1: Unit Tests (ci-job=unit-tests)

**Purpose**: Fast feedback on unit test coverage and ArchUnit architecture validation.

**Command**:
```bash
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests
```

**What runs**:
- All JUnit 5 unit tests (`*Test.java`, `*Tests.java`)
- ArchUnit 12 hexagonal architecture rules (docai-bootstrap module)
- Mockito mocks and assertions
- Excludes integration tests (`*IT.java`, `*ITs.java`)

**Coverage Requirements**:
- Global: ≥ 80% (JaCoCo)
- Domain (`docai-domain`): ≥ 90% (target for highest quality)

**Expected Duration**: ~2-3 minutes

**Fails if**:
- Unit test failures
- ArchUnit rule violations (12 rules enforcing hexagonal architecture)
- Coverage below threshold

---

## Job 2: Integration Tests (ci-job=integration-tests)

**Purpose**: End-to-end testing with real services (TestContainers, MongoDB Replica Set, Kafka).

**Command**:
```bash
MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests
```

**What runs**:
- Integration tests (`*IT.java`, `*ITs.java`)
- Cucumber BDD scenarios (if defined)
- WireMock stubbed external API calls
- TestContainers for MongoDB, Kafka (isolated environments)
- Excludes unit tests (run separately in Job 1)

**Requirements for Integration Tests**:
- Docker daemon running (for TestContainers)
- Minimal 512MB heap
- No external service dependencies (TestContainers provide isolation)

**Expected Duration**: ~5-10 minutes (depends on TestContainers startup)

**Fails if**:
- Integration test failures
- BDD scenario failures
- External API mocks (WireMock) not matching expected contracts

---

## Job 3: Quality Gates (ci-job=quality-gates)

**Purpose**: Enforces code standards and mutation testing for domain robustness.

**Command**:
```bash
MAVEN_OPTS=-Xmx512m mvn clean verify -P quality-gates
```

**What runs**:
1. **Unit Tests** (for coverage): Runs all unit tests to gather code coverage metrics
2. **Checkstyle** (code formatting): Max 20-line methods, 4 parameters, cyclomatic complexity ≤ 10
3. **PIT Mutation Testing**:
   - Domain (`docai-domain`): ≥ 85% mutation score
   - Global: ≥ 80% mutation score
4. **JaCoCo Coverage Report**: Generates HTML coverage report
5. **SonarCloud**: Code quality analysis (0 bugs, 0 vulnerabilities, ≤ 3% duplication)

**Expected Duration**: ~8-15 minutes (PIT mutation testing is slow)

**Fails if**:
- Checkstyle violations (code style)
- Mutation score below threshold
- SonarCloud rules violated (bugs, vulnerabilities, duplication)

---

## Local Development Workflow

### Run All Jobs Locally (Simulating CI)

```bash
# 1. Unit tests
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests

# 2. Integration tests (requires Docker)
MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests

# 3. Quality gates (slow, but comprehensive)
MAVEN_OPTS=-Xmx512m mvn clean verify -P quality-gates
```

### Quick Local Build (Before Commit)

```bash
# Fast feedback: unit tests + ArchUnit only
mvn clean test -P unit-tests
```

### View Coverage Report

```bash
# After running Job 3, JaCoCo generates HTML reports
open docai-domain/target/site/jacoco/index.html
```

### View PIT Mutation Report

```bash
# After running Job 3, PIT generates detailed mutation report
open docai-domain/target/pit-reports/index.html
```

---

## Memory Management

All jobs use `MAVEN_OPTS=-Xmx512m` to:
- Run reliably in CI with limited resources
- Prevent out-of-memory errors (especially with PIT mutation testing)
- Keep build times predictable

**Local Development**: Can use larger heap if needed (e.g., `-Xmx1g`)

---

## Architecture Rules (12 ArchUnit Rules - Enforced in Job 1)

Hexagonal architecture validation in `docai-bootstrap/src/test/java/fr/docai/bootstrap/HexagonalArchitectureTest.java`:

1. **Domain** → No Spring dependencies
2. **Domain** → No MongoDB dependencies
3. **Domain** → No Kafka dependencies
4. **Domain** → No AWS SDK dependencies
5. **Domain** → No Redis/Valkey dependencies
6. **Domain** → No external libraries (LangChain, Stripe, etc.)
7. **Application** → No adapter dependencies
8. **Adapter-In** → Only depends on domain, application, and other adapters
9. **Adapter-Out** → Only depends on domain, application, and other adapters
10. **Bootstrap** → May depend on all layers
11. **No cyclic dependencies** between layers
12. **Ports** (interfaces in domain) → Must be implemented by adapters

---

## CI Configuration Example (GitHub Actions / GitLab CI)

### GitHub Actions `.github/workflows/ci.yml`

```yaml
name: CI

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - env:
          MAVEN_OPTS: -Xmx512m
        run: mvn clean test -P unit-tests

  integration-tests:
    runs-on: ubuntu-latest
    services:
      docker:
        image: docker:dind
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - env:
          MAVEN_OPTS: -Xmx512m
        run: mvn clean verify -P integration-tests

  quality-gates:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - env:
          MAVEN_OPTS: -Xmx512m
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: mvn clean verify -P quality-gates
```

---

## Troubleshooting

### "Out of Memory" in PIT mutation testing

**Cause**: Heap too small for mutation analysis.

**Solution**: Increase memory for Job 3:
```bash
MAVEN_OPTS=-Xmx1g mvn clean verify -P quality-gates
```

### ArchUnit fails on valid code

**Cause**: Port interface naming convention (must end with `Port`).

**Example**: Create interfaces in `docai-domain` like:
```java
public interface DocumentValidatorPort { ... }
```

### Integration tests timeout with TestContainers

**Cause**: Docker daemon not running or network issues.

**Solution**:
```bash
# Check Docker is running
docker info

# Run with verbose output
mvn clean verify -P integration-tests -X
```

### SonarCloud fails on duplication

**Cause**: Code duplicated across modules.

**Solution**: Extract common logic to `docai-application` or create a shared utility module.

---

## References

- **ADR-008**: Hexagonal architecture with 3 CI jobs, MAVEN_OPTS=-Xmx512m
- **ArchUnit Rules**: `docai-bootstrap/src/test/java/fr/docai/bootstrap/HexagonalArchitectureTest.java`
- **Checkstyle Config**: `checkstyle.xml` (20-line max, 4 params, complexity ≤ 10)
- **Code Coverage**: JaCoCo (global ≥ 80%, domain ≥ 90%)
- **Mutation Testing**: PIT (domain ≥ 85%, global ≥ 80%)
