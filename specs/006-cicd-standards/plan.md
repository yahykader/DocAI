# Implementation Plan: CI/CD Pipeline & Standards Setup (Module 1.B)

**Branch**: `006-cicd-standards` | **Date**: 2026-05-29 | **Spec**: [spec.md](./spec.md)

---

## Summary

Seven sequential steps delivering: a ClassLength gate added to Checkstyle, a five-job GitHub Actions CI pipeline (mapped onto three existing Maven profiles, ADR-008 memory limits preserved), a three-stage Dockerfile with non-root user `docai`, Docker publish + staging/production deploy workflows, Kubernetes manifests with HPA, a hexagonal `FeatureFlagPort` with Unleash self-hosted in docker-compose, and end-to-end observability verification. Steps 1–5 are infrastructure-only; steps 6–7 touch Java source. Five of the seven étapes start from partially-done work (checkstyle rules and all observability wiring already exist in the codebase).

---

## Technical Context

**Language/Version**: Java 21 (LTS), Spring Boot 4.0.x  
**Primary Dependencies**: Maven 3.9+, GitHub Actions (`ubuntu-latest`, 7 GB), `eclipse-temurin:21-jre-alpine`, Unleash Java SDK (self-hosted), Micrometer, OpenTelemetry, logstash-logback-encoder  
**Storage**: MongoDB 7 replica set, Valkey 8 — no new collections in this module  
**Testing**: JUnit 5, TestContainers (`withReuse(true)` — ADR-008), ArchUnit  
**Target Platform**: GitHub Actions runners + Kubernetes (staging & production)  
**Project Type**: B2B SaaS backend service (Spring Boot)  
**Performance Goals**: CI full pipeline ≤ 15 min; Docker image < 300 MB; feature flag toggle effective in < 30 s  
**Constraints**: `MAVEN_OPTS=-Xmx512m` (CI jobs 1–3), `-Xmx1g` (quality gates); no CRITICAL Trivy CVE published; management port 9091 (actuator, not 8080)  
**Scale/Scope**: ~4 days work; pure infrastructure module — no new domain entities, no new MongoDB collections

---

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| Hexagonal Architecture | ✅ Pass | `FeatureFlagPort` in `docai-domain/port/out/`; `UnleashFeatureFlagAdapter` in `docai-bootstrap` (cross-cutting) |
| Domain Purity | ✅ Pass | Port interface has zero Spring/Unleash imports; adapter wired only via `docai-bootstrap` |
| Test-First Development | ✅ Pass | Unit test for `UnleashFeatureFlagAdapter` fallback on Unleash unavailability; ArchUnit rules verified by CI job 1 |
| Code Quality Gates (V) | ✅ Pass | `maven-checkstyle-plugin` already in `quality-gates` profile; ClassLength rule to be added |
| ADR-008 (OOM prevention) | ⚠️ Adapted | ADR-008 specifies 3 CI jobs; this plan maps to 5 GitHub Actions jobs (unit, integration, bdd, contract, sonarcloud) onto the 3 Maven profiles. Core OOM constraint (`-Xmx512m` + container reuse) fully preserved. See Complexity Tracking. |
| ADR-010 (EXPLAIN PLAN) | ✅ Pass | Manual PR review gate; PR template includes EXPLAIN PLAN checklist |
| Observability (VI) | ✅ Pass | All wiring already done (`logback-spring.xml`, `TenantMdcFilter`, OTLP, Prometheus); Étape 7 verifies end-to-end |
| Security & Secrets (VII) | ✅ Pass | `GITHUB_TOKEN` for ghcr.io; AWS Secrets Manager CSI for K8s secrets; no secrets in manifests |
| Multi-Tenancy (VII) | ✅ N/A | No new entities; `TenantMdcFilter` already propagates `tenantId` to MDC |

---

## Project Structure

### Documentation (this feature)

```text
specs/006-cicd-standards/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output (FeatureFlagPort contract)
├── quickstart.md        ← Phase 1 output (local dev with Unleash)
├── contracts/
│   ├── FeatureFlagPort.java   ← port interface contract (reference copy)
│   └── ci-job-matrix.md       ← CI job dependency graph
└── tasks.md             ← /speckit-tasks output
```

### Source Code (repository root)

```text
# New infrastructure files
.github/
├── workflows/
│   ├── 01-ci.yml                  ← 5-job CI pipeline (ADR-008 profiles)
│   ├── 02-docker.yml              ← Build + Trivy CRITICAL gate + Push ghcr.io
│   ├── 03-deploy-staging.yml      ← Auto-deploy on develop merge
│   └── 04-deploy-production.yml   ← Manual approval (GitHub Environment "production")
└── pull_request_template.md       ← ADR-010 EXPLAIN PLAN checklist

backend/
└── Dockerfile                     ← 3-stage multi-stage build

k8s/
├── deployment.yaml                ← RollingUpdate maxUnavailable=0 maxSurge=1
├── service.yaml                   ← ClusterIP on port 8080
└── hpa.yaml                       ← minReplicas=2 maxReplicas=10 CPU 70%

# Modified existing files
backend/checkstyle.xml             ← Add ClassLength rule (≤ 200 lines)
docker-compose.yml                 ← Add unleash + unleash-db services

# New Java source
backend/docai-domain/src/main/java/fr/docai/domain/port/out/
└── FeatureFlagPort.java           ← Hexagonal port (zero external deps)

backend/docai-bootstrap/src/main/java/fr/docai/bootstrap/
├── config/
│   └── UnleashConfig.java         ← @Bean DefaultUnleash (SDK initialization)
└── feature/
    └── UnleashFeatureFlagAdapter.java  ← implements FeatureFlagPort (fail-safe)

backend/docai-bootstrap/pom.xml    ← Add io.getunleash:unleash-client-java
backend/docai-bootstrap/src/main/resources/application.yml  ← Add docai.unleash.* props
```

**Structure Decision**: Hexagonal — port in `docai-domain`, adapter in `docai-bootstrap`. Feature flags are a cross-cutting infrastructure concern (used by all modules), not a bounded context, so a dedicated `docai-adapter-out-feature-flag` module is not warranted.

---

## Complexity Tracking

| Deviation | Justification | Alternative Rejected Because |
|-----------|--------------|------------------------------|
| 5 GitHub Actions jobs instead of ADR-008's 3 | BDD (Cucumber) and contract tests (Spring Cloud Contract) need isolated runners and separate PR status checks. SonarCloud needs aggregated coverage from all three test jobs before analyzing. | Merging BDD into the integration job conflates TestContainers `*IT` results with Cucumber scenario results; failure attribution becomes ambiguous in CI. |

---

## Implementation Steps

### Étape 1 — Checkstyle: ClassLength Rule (0.5j)

**Status:** Mostly done. `checkstyle.xml` already enforces MethodLength/ParameterNumber/CyclomaticComplexity. `maven-checkstyle-plugin` is already bound in the `quality-gates` Maven profile.

**Remaining work:**

**File to modify:** `backend/checkstyle.xml`  
Add inside `<module name="TreeWalker">`:
```xml
<!-- Class length check (max 200 lines) -->
<module name="ClassLength">
    <property name="max" value="200"/>
    <property name="countEmpty" value="false"/>
</module>
```

**Verification command:**
```bash
cd backend
MAVEN_OPTS=-Xmx512m mvn checkstyle:check -P quality-gates
# Expected: BUILD SUCCESS with 0 violations
```

---

### Étape 2 — GitHub Actions: 01-ci.yml (1j)

**Status:** Nothing exists. Full creation.

**Files to create:**
- `.github/workflows/01-ci.yml`
- `.github/pull_request_template.md`

**Job mapping to Maven profiles:**

| GitHub Job | Maven Profile | `MAVEN_OPTS` | Command | Container Reuse |
|------------|--------------|-------------|---------|-----------------|
| `unit-tests` | `unit-tests` | `-Xmx512m` | `mvn clean test -P unit-tests` | No |
| `integration` | `integration-tests` | `-Xmx512m` | `mvn clean verify -P integration-tests` | Yes |
| `bdd-tests` | `integration-tests` | `-Xmx512m` | `mvn clean verify -P integration-tests -Dcucumber.filter.tags=@bdd` | Yes |
| `contract-tests` | *(direct goal)* | `-Xmx512m` | `mvn spring-cloud-contract:generateTests verify` | No |
| `sonarcloud` | `quality-gates` | `-Xmx1g` | `mvn verify sonar:sonar -P quality-gates` | No |

**Job dependencies:**
```
unit-tests ──┬──→ integration
             ├──→ bdd-tests
             ├──→ contract-tests
             └──→ sonarcloud (needs: integration + bdd-tests for merged coverage)
```

**PR template (ADR-010)** at `.github/pull_request_template.md`:
```markdown
## EXPLAIN PLAN Checklist (ADR-010)
For every new or modified MongoDB query in this PR:
- [ ] Ran `explain()` — winningPlan.stage is `IXSCAN` (not `COLLSCAN`)
- [ ] `tenantId` is first field in compound index (Annex B)
- [ ] Partial index considered if active documents < 20% of collection

## Changes
...

## Test coverage
...
```

**Security requirements (mandatory — SEC-002, SEC-003):**
- All `uses:` references to third-party actions MUST be pinned to their full commit SHA (not tag). Tags are mutable and a compromised action maintainer can push malicious code under the same tag.
- Each workflow job MUST declare an explicit `permissions:` block:
  - `01-ci.yml`: `contents: read`
  - `02-docker.yml`: `contents: read`, `packages: write`
  - `03-deploy-staging.yml` / `04-deploy-production.yml`: `contents: read`, `id-token: write`

**Verification:** Push `develop` branch → all 5 jobs green in GitHub Actions

---

### Étape 3 — Dockerfile Multi-Stage (0.5j)

**Status:** Nothing exists. Full creation.

**File to create:** `backend/Dockerfile`

**Stage breakdown:**

| Stage | Base Image | Purpose |
|-------|-----------|---------|
| `dependencies` | `eclipse-temurin:21-jdk-alpine` | `mvn dependency:go-offline -B` (cache layer) |
| `build` | `eclipse-temurin:21-jdk-alpine` | `mvn clean package -DskipTests` + `java -Djarmode=layertools extract` |
| `runtime` | `eclipse-temurin:21-jre-alpine` | Copy layered JARs; create user `docai`; JVM flags; HEALTHCHECK |

**Non-root user setup (runtime stage):**
```dockerfile
RUN addgroup -S docai && adduser -S -G docai docai
USER docai
```

**JVM flags (runtime stage):**
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

**Health check** (management port = 9091 per `application.yml`):
```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:9091/actuator/health || exit 1
```

**Verification:**
```bash
docker build -t docai-backend:test ./backend
docker images docai-backend:test --format "{{.Size}}"  # must be < 300MB
docker run --rm docai-backend:test whoami                # must print "docai"
```

---

### Étape 4 — 02-docker.yml + 03-deploy-staging.yml (0.5j)

**Status:** Nothing exists. Full creation.

**Files to create:**
- `.github/workflows/02-docker.yml`
- `.github/workflows/03-deploy-staging.yml`
- `.github/workflows/04-deploy-production.yml`

**02-docker.yml flow:**
1. Checkout + set up QEMU + Docker Buildx
2. Login to `ghcr.io` via `GITHUB_TOKEN` (no extra secrets)
3. Build image (push: false) → local cache
4. Run `aquasecurity/trivy-action` with `exit-code: 1` on `CRITICAL` severity → blocks publish if CRITICAL found
5. Push image to `ghcr.io/${{ github.repository_owner }}/docai-backend:${{ github.sha }}`
6. Tag `:latest` on `main` branch only

**03-deploy-staging.yml flow:**
1. Trigger: `workflow_run` on `02-docker.yml` completed successfully, on branch `develop`
2. `kubectl set image deployment/docai-backend docai-backend=ghcr.io/.../docai-backend:$SHA`
3. `kubectl rollout status deployment/docai-backend --timeout=120s`
4. Post-deploy health check: `curl -f https://staging.docai.fr/actuator/health`

**04-deploy-production.yml flow:**
1. Trigger: `workflow_dispatch` (manual) with `image_tag` input
2. `environment: production` → GitHub Environment protection → required reviewer gate
3. Same kubectl + rollout steps as staging

**Verification:**
- Push branch with `CVE-2023-0001` simulation → Trivy step fails, image NOT pushed to ghcr.io
- Clean push → image visible at `ghcr.io/*/docai-backend:$SHA`

---

### Étape 5 — Manifestes Kubernetes (0.5j)

**Status:** Nothing exists. Full creation.

**Files to create:** `k8s/deployment.yaml`, `k8s/service.yaml`, `k8s/hpa.yaml`

**deployment.yaml key parameters:**

| Parameter | Value | Rule |
|-----------|-------|------|
| `strategy.type` | `RollingUpdate` | BR-K8S-001 |
| `maxUnavailable` | `0` | BR-K8S-001: zero downtime |
| `maxSurge` | `1` | One extra pod during rollout |
| `resources.requests.memory` | `512Mi` | Aligns with ADR-008 JVM cap |
| `resources.limits.memory` | `1Gi` | Safety cap above JVM max |
| `resources.requests.cpu` | `250m` | HPA baseline |
| `livenessProbe.path` | `/actuator/health/liveness` | Port 9091 |
| `readinessProbe.path` | `/actuator/health/readiness` | Port 9091 |

**hpa.yaml key parameters (BR-K8S-004):**

| Parameter | Value |
|-----------|-------|
| `minReplicas` | `2` (HA minimum) |
| `maxReplicas` | `10` |
| `targetCPUUtilizationPercentage` | `70` |

**Secrets rule:** All environment variables reference `secretKeyRef` pointing to a `SecretProviderClass` (AWS Secrets Manager CSI Driver). Zero `Secret` YAML files in this repository.

**Security requirements (mandatory — SEC-001):**
- `deployment.yaml` MUST include a container-level `securityContext`:
  ```yaml
  securityContext:
    runAsNonRoot: true
    runAsUser: 1001        # UID of user "docai" created in Dockerfile
    readOnlyRootFilesystem: true
    allowPrivilegeEscalation: false
    capabilities:
      drop: [ALL]
  ```
- If `readOnlyRootFilesystem: true` breaks Spring Boot startup (temp files), add an `emptyDir` volume mount for `/tmp`.

**Verification:**
```bash
kubectl apply --dry-run=client -k k8s/overlays/staging
# Expected: 3 resources configured (no errors)
```

---

### Étape 6 — Feature Flags Unleash (0.5j)

**Status:** Nothing exists. Full creation.

**Files to create/modify:**

| File | Action |
|------|--------|
| `docker-compose.yml` | Add `unleash` (port 4242) + `unleash-db` (PostgreSQL 16) |
| `backend/docai-domain/src/main/java/fr/docai/domain/port/out/FeatureFlagPort.java` | New port interface |
| `backend/docai-bootstrap/src/main/java/fr/docai/bootstrap/config/UnleashConfig.java` | `@Bean DefaultUnleash` |
| `backend/docai-bootstrap/src/main/java/fr/docai/bootstrap/feature/UnleashFeatureFlagAdapter.java` | implements FeatureFlagPort |
| `backend/docai-bootstrap/pom.xml` | Add `io.getunleash:unleash-client-java` |
| `backend/docai-bootstrap/src/main/resources/application.yml` | Add `docai.unleash.url`, `docai.unleash.api-token`, `docai.unleash.app-name` |

**FeatureFlagPort (domain — zero external imports):**
```java
package fr.docai.domain.port.out;

public interface FeatureFlagPort {
    boolean isEnabled(String flagName);
    boolean isEnabled(String flagName, String tenantId);
}
```

**6 Flags and their defaults:**

| Flag Name | Default | Activates |
|-----------|---------|-----------|
| `billing.enabled` | `false` | Module 7 billing |
| `fraud.v2.enabled` | `false` | New fraud algorithm |
| `extraction.mistral.enabled` | `false` | Mistral LLM (Constitution tech stack) |
| `dashboard.search.enabled` | `false` | Module 5 advanced search |
| `notifications.inapp.enabled` | `false` | In-app notifications |
| `maintenance.mode` | `false` | Global maintenance gate |

**Fail-safe rule:** `UnleashFeatureFlagAdapter.isEnabled()` wraps all SDK calls in `try/catch`; returns `false` on any exception (SDK unreachable, network error, NPE). Never propagates exceptions to callers.

**Security requirements (mandatory — SEC-004):**
- `UNLEASH_API_TOKEN` default (`*:*.unleash-insecure-api-token`) is **only valid for the `local` Spring profile**. It MUST NOT be used in staging or production.
- Staging/prod: scoped Unleash API token stored in AWS Secrets Manager → mounted via CSI Driver as environment variable.
- `unleash-db` credentials (`unleash`/`unleash`) are local-only; staging/prod uses rotated PostgreSQL credentials following Constitution Annex C (90-day rotation).
- Unleash service has no public ingress in staging/prod — internal ClusterIP only, reachable exclusively from the app pod.

**Verification:**
```bash
docker compose up -d unleash unleash-db
# Wait 15s for init
curl http://localhost:4242/api/client/features -H "Authorization: *:*.unleash-insecure-api-token"
# Verify billing.enabled = false in application context
```

---

### Étape 7 — Observabilité: Vérification End-to-End (0.5j)

**Status:** Mostly done. All wiring exists. This étape verifies gaps and confirms integration.

**What already exists (do NOT re-implement):**
- `logback-spring.xml` — JSON logging with `traceId` + `tenantId` MDC, PII masking
- `docai-adapter-in-rest/.../filter/TenantMdcFilter.java` — propagates `tenantId` to MDC
- `docai-bootstrap/pom.xml` — `micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `logstash-logback-encoder`
- `application.yml` — `management.otlp.tracing.endpoint` → Grafana Tempo port 4317; management port 9091; Prometheus enabled

**Gaps to fill:**

| Gap | Action |
|-----|--------|
| `opentelemetry-spring-boot-starter` is commented out in `docai-bootstrap/pom.xml` | Evaluate whether `micrometer-tracing-bridge-otel` alone suffices for Spring Boot 4 auto-instrumentation, or uncomment the starter |
| Prometheus `prometheus.yml` scrape config | Verify `localhost:9091/actuator/prometheus` is listed as a scrape target in `prometheus.yml` |
| Grafana Tempo `tempo.yml` | Verify OTLP receiver is enabled on port 4317 (gRPC) |

**Verification sequence:**
```bash
# 1. Start infra
docker compose up -d prometheus grafana tempo

# 2. Start application
cd backend && mvn spring-boot:run -pl docai-bootstrap

# 3. Trigger a request
curl -H "Authorization: Bearer <token>" http://localhost:8080/v1/health

# 4. Check Prometheus
curl http://localhost:9091/actuator/prometheus | grep http_server_requests

# 5. Check Grafana Tempo (trace search by traceId)
# http://localhost:3000 → Explore → Tempo → search for last 5m

# 6. Check logs contain tenantId + traceId in JSON
# Requires staging profile: SPRING_PROFILES_ACTIVE=staging
```
