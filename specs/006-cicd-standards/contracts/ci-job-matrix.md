# CI Job Dependency Matrix (Module 1.B)

**File**: `.github/workflows/01-ci.yml`

---

## Job Graph

```
push / pull_request
        │
        ▼
  ┌─────────────┐
  │ unit-tests  │  MAVEN_OPTS=-Xmx512m
  │ -P unit-    │  mvn clean test -P unit-tests
  │   tests     │  ArchUnit 12 rules validated here
  └──────┬──────┘
         │ on success
    ┌────┼────────────────────────┐
    ▼    ▼                        ▼
┌──────────┐  ┌──────────┐  ┌──────────────┐
│integration│  │bdd-tests │  │contract-tests│
│-Xmx512m  │  │-Xmx512m  │  │-Xmx512m      │
│REUSE=true│  │REUSE=true│  │generateTests │
└────┬─────┘  └────┬─────┘  └──────────────┘
     │              │
     └──────┬───────┘
            │ needs: [integration, bdd-tests]
            ▼
      ┌──────────┐
      │sonarcloud│  MAVEN_OPTS=-Xmx1g
      │-P quality│  merged coverage from jobs 1+2+3
      │  -gates  │  sonar:sonar (SONAR_TOKEN required)
      └──────────┘
```

---

## Job Specifications

| Job | Trigger | Maven Profile | `MAVEN_OPTS` | Container Reuse | Needs |
|-----|---------|--------------|-------------|-----------------|-------|
| `unit-tests` | push, PR | `unit-tests` | `-Xmx512m` | No | — |
| `integration` | push, PR | `integration-tests` | `-Xmx512m` | Yes | `unit-tests` |
| `bdd-tests` | push, PR | `integration-tests` | `-Xmx512m` | Yes | `unit-tests` |
| `contract-tests` | push, PR | *(direct goal)* | `-Xmx512m` | No | `unit-tests` |
| `sonarcloud` | push to `main`/`develop` only | `quality-gates` | `-Xmx1g` | No | `integration`, `bdd-tests` |

---

## Branch Protection Rules (recommended)

Required status checks before merge:
- `unit-tests` ✅
- `integration` ✅
- `bdd-tests` ✅
- `contract-tests` ✅

`sonarcloud` runs on merge to `main`/`develop` only (not on every feature branch push).

---

## Workflow Trigger Configuration

```yaml
# .github/workflows/01-ci.yml triggers
on:
  push:
    branches: [main, develop, 'feature/**']
  pull_request:
    branches: [main, develop]
```
