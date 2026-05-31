# DocAI CI/CD Jobs - ADR-008

## Overview

Five separate CI jobs enforce code quality and architectural integrity. Each job uses `MAVEN_OPTS=-Xmx512m` (jobs 1–4) or `MAVEN_OPTS=-Xmx1g` (job 5 — mutation testing). Jobs 2, 3, and 4 each depend on job 1. Job 5 depends on jobs 2 and 3.

```
unit-tests ──┬── integration ──────────────────┬── sonarcloud
             ├── bdd-tests ─────────────────────┘
             └── contract-tests
```

---

## Job 1: Unit Tests (`unit-tests`)

**Purpose**: Fast feedback on unit test coverage and ArchUnit architecture validation.

**Command**:
```bash
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests
```

**What runs**:
- All JUnit 5 unit tests (`*Test.java`, `*Tests.java`)
- ArchUnit 12 hexagonal architecture rules (`docai-bootstrap` module)
- Mockito mocks and assertions
- Excludes integration tests (`*IT.java`, `*ITs.java`)

**Coverage Requirements**:
- Global: ≥ 80% (JaCoCo)
- Domain (`docai-domain`): ≥ 90%

**Expected Duration**: ~2–3 minutes

**Fails if**: unit test failures, ArchUnit violations, coverage below threshold

---

## Job 2: Integration Tests (`integration`)

**Purpose**: End-to-end testing with real services (TestContainers — MongoDB Replica Set, Kafka).

**Command**:
```bash
TESTCONTAINERS_REUSE_ENABLE=true MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests
```

**What runs**:
- Integration tests (`*IT.java`, `*ITs.java`)
- WireMock stubs for external API calls
- TestContainers for MongoDB, Kafka (isolated, reused across run)

**Requirements**: Docker daemon running; `TESTCONTAINERS_REUSE_ENABLE=true` required (ADR-008 OOM prevention)

**Expected Duration**: ~5–10 minutes

**Fails if**: integration test failures, WireMock contract mismatches

---

## Job 3: BDD Tests (`bdd-tests`)

**Purpose**: Cucumber BDD scenarios against real services.

**Command**:
```bash
TESTCONTAINERS_REUSE_ENABLE=true MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests -Dcucumber.filter.tags=@bdd
```

**What runs**:
- Cucumber scenarios tagged `@bdd`
- TestContainers (reused — shared with job 2 startup cache)

**Requirements**: Docker daemon running; `TESTCONTAINERS_REUSE_ENABLE=true` required (ADR-008)

**Expected Duration**: ~3–7 minutes

**Fails if**: BDD scenario failures

---

## Job 4: Contract Tests (`contract-tests`)

**Purpose**: Spring Cloud Contract verification — consumer-driven contract tests.

**Command**:
```bash
MAVEN_OPTS=-Xmx512m mvn spring-cloud-contract:generateTests verify
```

**Expected Duration**: ~2–4 minutes

**Fails if**: generated contract tests fail, stubs not matching

---

## Job 5: SonarCloud / Quality Gates (`sonarcloud`)

**Purpose**: Enforces code standards, mutation testing, and SonarCloud analysis.

**Command**:
```bash
MAVEN_OPTS=-Xmx1g mvn verify sonar:sonar -P quality-gates -DskipPit
```

**Note**: `-Xmx1g` (not 512m) — mutation testing requires more heap (ADR-008).

**What runs**:
1. Unit tests (for coverage metrics)
2. Checkstyle: max 20-line methods, 4 parameters, cyclomatic complexity ≤ 10, class length ≤ 200 lines
3. PIT Mutation Testing: domain ≥ 85%, global ≥ 80%
4. JaCoCo Coverage Report
5. SonarCloud: 0 bugs, 0 vulnerabilities, ≤ 3% duplication

**Expected Duration**: ~8–15 minutes

**Fails if**: Checkstyle violations, mutation score below threshold, SonarCloud quality gate failure

---

## Local Development Workflow

```bash
cd backend

# 1. Fast unit tests (2–3 min)
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests

# 2. Integration tests (5–10 min, requires Docker)
TESTCONTAINERS_REUSE_ENABLE=true MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests

# 3. BDD tests
TESTCONTAINERS_REUSE_ENABLE=true MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests -Dcucumber.filter.tags=@bdd

# 4. Contract tests
MAVEN_OPTS=-Xmx512m mvn spring-cloud-contract:generateTests verify

# 5. Quality gates (slow — run before PR)
MAVEN_OPTS=-Xmx1g mvn verify sonar:sonar -P quality-gates -DskipPit
```

### View Coverage Report

```bash
open docai-domain/target/site/jacoco/index.html
```

### View PIT Mutation Report

```bash
open docai-domain/target/pit-reports/index.html
```

---

## Memory Management (ADR-008)

| Job | `MAVEN_OPTS` | `TESTCONTAINERS_REUSE_ENABLE` | Reason |
|-----|-------------|-------------------------------|--------|
| unit-tests | `-Xmx512m` | — | No containers |
| integration | `-Xmx512m` | `true` | OOM prevention on 7 GB runner |
| bdd-tests | `-Xmx512m` | `true` | OOM prevention on 7 GB runner |
| contract-tests | `-Xmx512m` | — | No containers |
| sonarcloud | `-Xmx1g` | — | PIT mutation testing needs more heap |

---

## Architecture Rules (12 ArchUnit Rules — Enforced in Job 1)

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

## CI Configuration Example (GitHub Actions)

All third-party `uses:` references MUST be pinned to full commit SHA — never version tags like `@v3` or `@main` (SEC-002). Use Dependabot (`github-actions` ecosystem, weekly) to receive automated SHA-update PRs.

```yaml
name: CI

on:
  push:
    branches: [main, develop, 'feature/**']
  pull_request:
    branches: [main, develop]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
      - uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00  # v4.7.1
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Unit tests + ArchUnit
        env:
          MAVEN_OPTS: -Xmx512m
        run: cd backend && mvn clean test -P unit-tests

  integration:
    runs-on: ubuntu-latest
    needs: [unit-tests]
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
      - uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00  # v4.7.1
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Integration tests (TestContainers)
        env:
          MAVEN_OPTS: -Xmx512m
          TESTCONTAINERS_REUSE_ENABLE: "true"
        run: cd backend && mvn clean verify -P integration-tests

  bdd-tests:
    runs-on: ubuntu-latest
    needs: [unit-tests]
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
      - uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00  # v4.7.1
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: BDD / Cucumber tests
        env:
          MAVEN_OPTS: -Xmx512m
          TESTCONTAINERS_REUSE_ENABLE: "true"
        run: cd backend && mvn clean verify -P integration-tests -Dcucumber.filter.tags=@bdd

  contract-tests:
    runs-on: ubuntu-latest
    needs: [unit-tests]
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
      - uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00  # v4.7.1
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Spring Cloud Contract tests
        env:
          MAVEN_OPTS: -Xmx512m
        run: cd backend && mvn spring-cloud-contract:generateTests verify

  sonarcloud:
    runs-on: ubuntu-latest
    needs: [integration, bdd-tests]
    if: github.event_name == 'push'
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
        with:
          fetch-depth: 0
      - uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00  # v4.7.1
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Quality gates + SonarCloud
        env:
          MAVEN_OPTS: -Xmx1g
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: cd backend && mvn verify sonar:sonar -P quality-gates -DskipPit
```

---

## Troubleshooting

### "Out of Memory" in PIT mutation testing

**Cause**: Heap too small for mutation analysis.

**Solution**: Job 5 (`sonarcloud`) already uses `-Xmx1g`. Local override if needed:
```bash
MAVEN_OPTS=-Xmx2g mvn clean verify -P quality-gates
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
docker info
TESTCONTAINERS_REUSE_ENABLE=true mvn clean verify -P integration-tests -X
```

### SonarCloud fails on duplication

**Cause**: Code duplicated across modules.

**Solution**: Extract common logic to `docai-application` or create a shared utility module.

---

## References

- **ADR-008**: Five CI jobs, `MAVEN_OPTS` caps, `TESTCONTAINERS_REUSE_ENABLE=true` on jobs 2–3
- **SEC-002**: All third-party `uses:` pinned to full commit SHA
- **ArchUnit Rules**: `docai-bootstrap/src/test/java/fr/docai/bootstrap/HexagonalArchitectureTest.java`
- **Checkstyle Config**: `backend/checkstyle.xml` (20-line max methods, 4 params, complexity ≤ 10, class ≤ 200 lines)
- **Code Coverage**: JaCoCo (global ≥ 80%, domain ≥ 90%)
- **Mutation Testing**: PIT (domain ≥ 85%, global ≥ 80%)
