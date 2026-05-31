# Tasks: CI/CD Pipeline & Standards Setup (Module 1.B)

**Input**: `specs/006-cicd-standards/` — plan.md, spec.md, data-model.md, contracts/  
**Feature Branch**: `006-cicd-standards`  
**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Data Model**: [data-model.md](./data-model.md)  
**ADR in force**: ADR-008 (OOM prevention) · ADR-010 (EXPLAIN PLAN gate)

**Tests**: Unit test included for `UnleashFeatureFlagAdapter` fail-safe behavior (specified in plan.md). No other tests — infrastructure module with no new domain logic.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no intra-phase dependencies)
- **[USx]**: User story this task belongs to (US1–US5 from spec.md)
- Exact file paths included in every task description

---

## Phase 1: Setup

**Purpose**: Verify docker-compose infrastructure health and scaffold new directory structures before implementation begins.

- [ ] T001 Verify docker-compose stack is healthy — run `docker compose ps` and confirm mongodb, kafka, keycloak, prometheus, grafana, tempo are all in state Running or Healthy; resolve any unhealthy service before proceeding
- [x] T002 [P] Create `.github/workflows/` directory and `k8s/` directory at repository root (empty scaffolding consumed by downstream phases)

**Checkpoint**: Directory structure ready — Phase 2 can begin

---

## Phase 2: Foundational (Blocking Prerequisite)

**Purpose**: `ClassLength` Checkstyle rule must exist in `backend/checkstyle.xml` before the CI pipeline can enforce it. No user story work can begin until T004 passes.

**⚠️ CRITICAL**: Phase 3 (CI pipeline) depends on this phase being complete.

- [x] T003 Add `<module name="ClassLength"><property name="max" value="200"/><property name="countEmpty" value="false"/></module>` inside `<module name="TreeWalker">` in `backend/checkstyle.xml` (plan Étape 1)
- [ ] T004 Verify Checkstyle: run `cd backend && MAVEN_OPTS=-Xmx512m mvn checkstyle:check -P quality-gates` — expected: BUILD SUCCESS with 0 violations across all modules

**Checkpoint**: Checkstyle configured — Phase 3 (US1) and Phases 4–7 can now begin

---

## Phase 3: User Story 1 — Automated Quality Gate on Every Push (P1) 🎯 MVP

**Goal**: Five-job CI pipeline enforces ArchUnit, test coverage, Checkstyle, and SonarCloud gates on every push and pull request. Developer receives pass/fail feedback within 15 minutes.

**Independent Test**: Push a commit with an ArchUnit violation to `develop` — Job 1 (`unit-tests`) fails and Jobs 2–5 do not run. Push a valid commit — all 5 jobs pass and PR is eligible for merge.

- [x] T005 [US1] Create `.github/workflows/01-ci.yml` with the following 5 jobs and their configuration:
  - `unit-tests`: `MAVEN_OPTS=-Xmx512m`, `mvn clean test -P unit-tests`, runs on every push/PR, `permissions: contents: read`
  - `integration`: `MAVEN_OPTS=-Xmx512m`, `TESTCONTAINERS_REUSE_ENABLE=true`, `mvn clean verify -P integration-tests`, `needs: [unit-tests]`, `permissions: contents: read`
  - `bdd-tests`: `MAVEN_OPTS=-Xmx512m`, `TESTCONTAINERS_REUSE_ENABLE=true`, `mvn clean verify -P integration-tests -Dcucumber.filter.tags=@bdd`, `needs: [unit-tests]`, `permissions: contents: read`
  - `contract-tests`: `MAVEN_OPTS=-Xmx512m`, `mvn spring-cloud-contract:generateTests verify`, `needs: [unit-tests]`, `permissions: contents: read`
  - `sonarcloud`: `MAVEN_OPTS=-Xmx1g`, `mvn verify sonar:sonar -P quality-gates -DskipPit`, `needs: [integration, bdd-tests]`, runs on push to main/develop only, `permissions: contents: read`
  - Triggers: `push: branches: [main, develop, 'feature/**']`, `pull_request: branches: [main, develop]`
  - All third-party `uses:` references pinned to full commit SHA (SEC-002)
  - See `contracts/ci-job-matrix.md` for dependency graph reference
- [x] T006 [P] [US1] Create `.github/pull_request_template.md` with EXPLAIN PLAN section (ADR-010): checkboxes for `explain()` shows `IXSCAN` not `COLLSCAN`, `tenantId` is first field in compound index, partial index considered if active documents < 20% of collection; plus standard Changes and Test coverage sections
- [ ] T007 [US1] Validate: push develop branch → observe all 5 CI jobs green in GitHub Actions UI; then push a branch with a deliberate ArchUnit violation in `docai-domain` → confirm `unit-tests` job fails and `integration`/`bdd-tests`/`contract-tests`/`sonarcloud` jobs are skipped; **also run SHA-pin gate** (TASK-SEC-001): `grep -rn "uses:.*@v[0-9]" .github/workflows/ && exit 1; grep -rn "uses:.*@main" .github/workflows/ && exit 1` → must produce no matches (zero mutable action tags permitted)

**Checkpoint**: CI pipeline operational — 5 jobs green on develop, ArchUnit gate confirmed blocking

---

## Phase 4: User Story 2 — Secure Docker Image Build and Publish (P2)

**Goal**: Multi-stage Dockerfile produces a < 300 MB JRE 21 Alpine image running as non-root user `docai`; `02-docker.yml` builds, scans with Trivy (CRITICAL gate), and pushes to `ghcr.io`.

**Independent Test**: `docker build -t docai-backend:test ./backend` → size < 300 MB; `docker run --rm docai-backend:test whoami` → prints `docai`. Inject a known CRITICAL CVE → `02-docker.yml` fails before push step.

- [x] T008 [US2] Create `backend/Dockerfile` with 3 stages:
  - Stage 1 `dependencies` from `eclipse-temurin:21-jdk-alpine`: copy `pom.xml` + module POMs, run `mvn dependency:go-offline -B` (cache layer)
  - Stage 2 `build` from `eclipse-temurin:21-jdk-alpine`: copy source, run `mvn clean package -DskipTests`, run `java -Djarmode=layertools -jar target/docai-bootstrap-*.jar extract --destination /app/extracted`
  - Stage 3 `runtime` from `eclipse-temurin:21-jre-alpine`: copy layered JARs from build stage; `RUN addgroup -S docai && adduser -S -G docai docai`; `USER docai`; `ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"`; `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]`; `HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD wget --quiet --tries=1 --spider http://localhost:9091/actuator/health || exit 1`
- [x] T009 [US2] Create `.github/workflows/02-docker.yml`: trigger on push to `main` and `develop`; `permissions: contents: read, packages: write`; jobs: (1) checkout, QEMU, Docker Buildx setup; (2) login to `ghcr.io` via `${{ secrets.GITHUB_TOKEN }}`; (3) build image with `docker/build-push-action` (SHA-pinned, SEC-002), `push: false`, store in local cache; (4) run `aquasecurity/trivy-action` (SHA-pinned) with `exit-code: 1`, `severity: CRITICAL`, `image-ref` pointing to cached image — step fails here if CRITICAL CVE found; (5) push image to `ghcr.io/${{ github.repository_owner }}/docai-backend:${{ github.sha }}`; tag `:latest` on `main` branch only; all third-party actions SHA-pinned (SEC-002)
- [ ] T010 [US2] Validate Dockerfile: run `docker build -t docai-backend:test ./backend` → `docker images docai-backend:test --format "{{.Size}}"` must be < 300 MB; run `docker run --rm docai-backend:test whoami` must print `docai`

**Checkpoint**: Dockerfile verified < 300 MB, user `docai` confirmed, Trivy CRITICAL gate blocking

---

## Phase 5: User Story 3 — Automated Staging Deployment with Kubernetes (P3)

**Goal**: K8s manifests define zero-downtime `RollingUpdate` (maxUnavailable=0) with HPA (min=2, max=10, CPU 70%); `03-deploy-staging.yml` deploys automatically on successful Docker publish; `04-deploy-production.yml` requires manual approval via GitHub Environment.

**Independent Test**: `kubectl apply --dry-run=client -f k8s/` → 0 errors, 3 resources configured; no `Secret` YAML present in `k8s/` directory.

- [x] T011 [P] [US3] Create `k8s/deployment.yaml`:
  - `strategy.type: RollingUpdate`, `maxUnavailable: 0`, `maxSurge: 1` (BR-K8S-001, zero downtime)
  - `resources.requests.memory: 512Mi`, `resources.limits.memory: 1Gi`, `resources.requests.cpu: 250m`, `resources.limits.cpu: 1000m`
  - `livenessProbe` on `GET /actuator/health/liveness` port 9091; `readinessProbe` on `GET /actuator/health/readiness` port 9091
  - Container-level `securityContext`: `runAsNonRoot: true`, `runAsUser: 1001`, `readOnlyRootFilesystem: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]` (SEC-001)
  - `volumes: [{name: tmp-dir, emptyDir: {}}]` + `volumeMounts: [{name: tmp-dir, mountPath: /tmp}]` (Spring Boot temp files under readOnlyRootFilesystem)
  - All env vars sourced from `secretKeyRef` referencing a `SecretProviderClass` (AWS Secrets Manager CSI Driver) — zero hardcoded values, zero `Secret` YAML
- [x] T012 [P] [US3] Create `k8s/service.yaml`: `type: ClusterIP`, `port: 8080`, `targetPort: 8080`; selector matching `app: docai-backend` label from deployment
- [x] T013 [P] [US3] Create `k8s/hpa.yaml`: `minReplicas: 2`, `maxReplicas: 10`, `targetCPUUtilizationPercentage: 70`, scaling target referencing `docai-backend` deployment (BR-K8S-004)
- [x] T014 [US3] Create `.github/workflows/03-deploy-staging.yml`: trigger `workflow_run` on `02-docker.yml` completed successfully on branch `develop`; `permissions: contents: read, id-token: write`; steps: `kubectl set image deployment/docai-backend docai-backend=ghcr.io/${{ github.repository_owner }}/docai-backend:${{ github.event.workflow_run.head_sha }}`; `kubectl rollout status deployment/docai-backend --timeout=120s`; post-deploy health check `curl -f https://staging.docai.fr/actuator/health`; all third-party actions SHA-pinned (SEC-002)
- [x] T015 [US3] Create `.github/workflows/04-deploy-production.yml`: trigger `workflow_dispatch` with input `image_tag`; `environment: production` (GitHub Environment protection — blocks execution until required reviewer approves); `permissions: contents: read, id-token: write`; same kubectl set-image + rollout status + health check steps as staging; all actions SHA-pinned (SEC-002)
- [ ] T016 [US3] Validate: run `kubectl apply --dry-run=client -f k8s/` → confirm output shows "deployment.apps/docai-backend configured", "service/docai-backend configured", "horizontalpodautoscaler.autoscaling/docai-backend configured" with 0 errors; confirm `ls k8s/` contains no file named `*secret*` or `*Secret*`
- [x] T016b [P] [US3] Document kubectl authentication mechanism in `docs/k8s-auth.md` (TASK-SEC-002): specify OIDC federation method in use (IRSA, GKE Workload Identity, or stored kubeconfig); list required GitHub secrets by name (e.g. `KUBE_CONFIG_STAGING`, `AWS_ROLE_ARN`); document minimum RBAC permissions for the CI service account — `patch` on `apps/deployments` + `get`/`watch` for rollout status in namespace `docai-staging`; include negative verification command: `kubectl auth can-i delete namespaces --as=<ci-service-account>` MUST return `no`

**Checkpoint**: K8s manifests validated dry-run clean, securityContext present, no Secret YAML, staging/production workflows created

---

## Phase 6: User Story 4 — Feature Flag-Controlled Rollout (P3)

**Goal**: Hexagonal `FeatureFlagPort` (zero deps) wired to self-hosted Unleash via `UnleashFeatureFlagAdapter`; 6 flags with safe defaults (`false`); fail-safe: Unleash unavailable → returns `false` with no exception.

**Independent Test**: `docker compose up -d unleash unleash-db` → `curl http://localhost:4242/api/client/features` shows 6 flags all `false`; stop `unleash` container → application returns `false` for all `isEnabled()` calls with no thrown exception (unit test T023 verifies this).

- [x] T017 [US4] Add two services to `docker-compose.yml`: `unleash-db` (`image: postgres:16-alpine`, env POSTGRES_DB/USER/PASSWORD all set to `unleash` — **add inline comment `# LOCAL PROFILE ONLY — staging/prod uses AWS Secrets Manager (SEC-004)`**; named volume `unleash-db-data`); `unleash` (`image: unleashorg/unleash-server:latest`, port 4242, `depends_on: unleash-db`, env DATABASE_URL pointing to unleash-db)
- [x] T018 [P] [US4] Create `backend/docai-domain/src/main/java/fr/docai/domain/port/out/FeatureFlagPort.java`: interface with `boolean isEnabled(String flagName)` and `boolean isEnabled(String flagName, String tenantId)`; zero Spring/Unleash imports; Javadoc: "Implementations MUST be fail-safe: any exception returns false (flag disabled)" — reference file: `specs/006-cicd-standards/contracts/FeatureFlagPort.java`
- [x] T019 [P] [US4] Add `<dependency><groupId>io.getunleash</groupId><artifactId>unleash-client-java</artifactId></dependency>` to `backend/docai-bootstrap/pom.xml` (use latest stable version from Maven Central)
- [x] T020 [US4] Create `backend/docai-bootstrap/src/main/java/fr/docai/bootstrap/config/UnleashConfig.java`: `@Configuration` class; `@Bean` method returning `DefaultUnleash` initialized with `UnleashConfig.newConfig().appName("${docai.unleash.app-name}").instanceId("docai-backend").unleashAPI("${docai.unleash.url}/api").apiKey("${docai.unleash.api-token}").build()`
- [x] T021 [US4] **Write unit test FIRST (TDD — Constitution III, TASK-SEC-003)**: create `backend/docai-bootstrap/src/test/java/fr/docai/bootstrap/feature/UnleashFeatureFlagAdapterTest.java`; mock `DefaultUnleash` to throw `RuntimeException` on both `isEnabled()` overloads; assert `adapter.isEnabled("billing.enabled")` returns `false` and no exception propagates; assert `adapter.isEnabled("billing.enabled", "tenant-acme")` returns `false` and no exception propagates; test class annotated with `@ExtendWith(MockitoExtension.class)`; **confirm test is RED before proceeding to T022**
- [x] T022 [P] [US4] Add to `backend/docai-bootstrap/src/main/resources/application.yml`:
  ```yaml
  docai:
    unleash:
      url: ${UNLEASH_URL:http://localhost:4242}
      api-token: ${UNLEASH_API_TOKEN:*:*.unleash-insecure-api-token}  # local profile only — SEC-004
      app-name: docai-backend
  ```
  Add inline comment: "UNLEASH_API_TOKEN default is local profile only. Staging/prod: scoped token in AWS Secrets Manager, mounted via CSI Driver."
- [x] T023 [US4] **Implement fail-safe adapter to make T021 GREEN**: create `backend/docai-bootstrap/src/main/java/fr/docai/bootstrap/feature/UnleashFeatureFlagAdapter.java`; `@Component` implementing `FeatureFlagPort`; constructor injection of `DefaultUnleash unleash`; `isEnabled(flagName)` → `try { return unleash.isEnabled(flagName); } catch (Exception e) { log.warn("Unleash unavailable for flag {}", flagName); return false; }`; `isEnabled(flagName, tenantId)` → same pattern using `UnleashContext.builder().userId(tenantId).build()`; never rethrows; **run T021 test suite — must be GREEN before continuing**
- [ ] T024 [US4] Validate feature flags: run `docker compose up -d unleash unleash-db`, wait 15 s for init, then `curl http://localhost:4242/api/client/features -H "Authorization: *:*.unleash-insecure-api-token"` (token is local profile only per SEC-004 — replace with AWS Secrets Manager value in staging/prod) → HTTP 200; confirm `billing.enabled` flag exists with `enabled: false`; confirm all 6 flags present: `billing.enabled`, `fraud.v2.enabled`, `extraction.mistral.enabled`, `dashboard.search.enabled`, `notifications.inapp.enabled`, `maintenance.mode`

**Checkpoint**: 6 flags configured (all `false`), `FeatureFlagPort` wired, fail-safe unit test passing, `billing.enabled=false` confirmed in DEV

---

## Phase 7: User Story 5 — Structured Observability for Operations (P2)

**Goal**: Verify end-to-end: structured JSON logs (tenantId + traceId, PII masked), Prometheus metrics on `/actuator/prometheus`, distributed traces in Grafana Tempo — all existing wiring confirmed working.

**Independent Test**: Make a request with `SPRING_PROFILES_ACTIVE=staging`; retrieve its `traceId`; confirm JSON log lines include `"tenantId"` and `"traceId"` fields; confirm `curl http://localhost:9091/actuator/prometheus | grep http_server_requests` returns data; confirm trace visible in Grafana Tempo.

- [x] T025 [P] [US5] Open `prometheus.yml` (mounted by the `prometheus` docker-compose service) and verify it contains a scrape job targeting `localhost:9091` with `metrics_path: /actuator/prometheus`; if missing, add:
  ```yaml
  - job_name: docai-backend
    static_configs:
      - targets: ['host.docker.internal:9091']
    metrics_path: /actuator/prometheus
  ```
- [x] T026 [P] [US5] Open Grafana Tempo config file (mounted by `tempo` docker-compose service, typically `tempo.yml`) and verify `receivers.otlp.protocols.grpc` section enables endpoint `0.0.0.0:4317`; if missing, add the gRPC OTLP receiver block; confirm `docker-compose.yml` exposes port 4317 on the `tempo` service
- [x] T027 [US5] Open `backend/docai-bootstrap/pom.xml` and find the commented `opentelemetry-spring-boot-starter` dependency; evaluate whether `micrometer-tracing-bridge-otel` alone provides Spring Boot 4 auto-instrumentation (check that `@Observed` and HTTP spans are created without the starter); document the decision with a comment in `pom.xml`; if the starter is needed, uncomment it and verify no classpath conflict with `micrometer-tracing-bridge-otel`
- [ ] T028 [US5] Run end-to-end observability verification sequence:
  1. `docker compose up -d prometheus grafana tempo`
  2. `cd backend && SPRING_PROFILES_ACTIVE=staging mvn spring-boot:run -pl docai-bootstrap`
  3. `curl -H "Authorization: Bearer <test-token>" http://localhost:8080/v1/health`
  4. Verify: `curl http://localhost:9091/actuator/prometheus | grep http_server_requests_seconds` returns non-empty output
  5. Verify: application log output contains `"tenantId"` and `"traceId"` JSON fields on the health request line
  6. Verify: trace visible in Grafana Tempo at `http://localhost:3000` → Explore → Tempo → search last 5 minutes

**Checkpoint**: Prometheus scraping, structured logs with tenantId+traceId, Tempo traces — all confirmed in DEV

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: GitFlow branch protection, Dependabot automation, and Annex C secrets rotation documentation — applicable across all workflows and environments.

- [x] T029 [P] Create `.github/dependabot.yml`: `version: 2`; ecosystem `maven`, directory `/backend`, schedule `weekly` (BR-DEP-002); `open-pull-requests-limit: 10`; add second entry for `github-actions` ecosystem, directory `/`, schedule `weekly` (keeps SHA-pinned actions up to date via automated PRs)
- [ ] T030 [P] Configure GitHub branch protection rules for `main` and `develop` via GitHub repository settings: require 1 reviewer before merge; require status checks `unit-tests`, `integration`, `bdd-tests`, `contract-tests` all passing; disallow direct push; disallow force push (GitFlow enforcement per 1.B-05b)
- [x] T031 [P] Create `docs/secrets-rotation.md`: table of secrets with names, services, rotation periods (OpenAI/Keycloak/Stripe: 90 days, MongoDB atlas: 180 days per BR-ROT-001/BR-ROT-002); document AWS Config rule `secretsmanager-secret-rotation-enabled` alert setup; document `spring.cloud.aws.secretsmanager.reload-strategy: restart_context` in `application.yml` for reload-without-redeploy (BR-ROT-004)
- [x] T032 [P] Create `docs/secrets-rotation-journal.md`: markdown table with columns `Secret Name | Last Rotated | Rotated By | Next Rotation Due | Notes` and one example row per secret defined in T031 (BR-ROT-003)
- [ ] T033 Full Definition of Done validation — verify all 6 DoD criteria:
  1. Push feature branch → `01-ci.yml` all 5 jobs green on `develop`
  2. Merge `develop` → `02-docker.yml` publishes image to GHCR without CRITICAL CVE
  3. Staging deployment triggers automatically and `GET https://staging.docai.fr/actuator/health` returns HTTP 200
  4. `billing.enabled` = false confirmed via `curl http://localhost:4242/api/client/features`
  5. Structured log line with `"tenantId"` + `"traceId"` visible in Grafana log explorer
  6. `curl http://localhost:9091/actuator/prometheus` returns Micrometer metrics including `http_server_requests_seconds`

**Checkpoint**: All Definition of Done criteria met — Module 1.B complete

---

## Dependencies & Execution Order

### Phase Dependencies

| Phase | Depends On | Notes |
|-------|-----------|-------|
| Phase 1 — Setup | None | Start immediately |
| Phase 2 — Foundational | Phase 1 | BLOCKS Phase 3 (Checkstyle rule must exist before CI validates it) |
| Phase 3 — US1 | Phase 2 | CI needs Checkstyle configured |
| Phase 4 — US2 | Phase 2 | Dockerfile independent; 02-docker.yml needs 01-ci.yml on develop |
| Phase 5 — US3 | Phase 4 | Staging deploy triggers on 02-docker.yml success |
| Phase 6 — US4 | Phase 2 | Fully independent of Phases 3–5 |
| Phase 7 — US5 | Phase 2 | Fully independent of Phases 3–6 |
| Phase 8 — Polish | Phases 3–7 | Cross-cutting finalization |

### User Story Dependencies

| Story | Priority | Depends On | Can Parallel With |
|-------|---------|-----------|------------------|
| US1 — CI Quality Gate | P1 | Phase 2 | US2, US4, US5 |
| US2 — Secure Docker Image | P2 | Phase 2 | US1, US4, US5 |
| US3 — K8s Staging Deploy | P3 | US2 (02-docker.yml) | US4, US5 |
| US4 — Feature Flag Rollout | P3 | Phase 2 | US1, US2, US5 |
| US5 — Structured Observability | P2 | Phase 2 | US1, US2, US4 |

### Parallel Opportunities

```bash
# After Phase 2 completes, launch simultaneously:
Story US1 → T005 (01-ci.yml), T006 (PR template)          # different files
Story US2 → T008 (Dockerfile)                              # independent
Story US4 → T017 (docker-compose.yml), T018 (FeatureFlagPort.java),
            T019 (pom.xml), T022 (application.yml)         # all different files
Story US5 → T025 (prometheus.yml), T026 (tempo.yml)        # different files

# Within US3 (after T009 exists):
T011, T012, T013 → deployment.yaml, service.yaml, hpa.yaml # fully parallel

# Polish phase (all independent):
T029, T030, T031, T032 → all different files/systems
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Phase 1: Setup (T001–T002)
2. Phase 2: Foundational (T003–T004) — Checkstyle
3. Phase 3: US1 (T005–T007) — CI pipeline green on `develop`
4. **STOP AND VALIDATE**: ArchUnit violation → CI fails; clean push → all 5 jobs green
5. **MVP Delivered**: automated quality gate enforced on every push

### Incremental Delivery

| Sprint | Scope | Deliverable |
|--------|-------|------------|
| Day 1 | Setup + Foundational + US1 | CI pipeline green, quality gates active |
| Day 2 | US2 + US5 | Docker image published, observability verified |
| Day 3 | US3 | Staging auto-deploy working |
| Day 4 | US4 + Polish | Feature flags live, GitFlow + Dependabot + secrets rotation |

### Parallel Team Strategy (2 developers)

After Phase 2 completes:
- **Dev A**: US1 → US2 → US3 (CI + Docker + Staging pipeline)
- **Dev B**: US4 → US5 (Feature Flags + Observability)
- Merge both branches; Phase 8 (Polish) finalizes cross-cutting items together

---

## Conflicts (resolved 2026-05-29)

| ID | Conflict | Affected Tasks | Resolution |
|----|---------|---------------|-----------|
| CHK017 | `notifications.inapp.enabled`: plan.md Étape 6 = `false`; checklist input = `true` in DEV | T024 | **RESOLVED**: `false` in all environments. plan.md Étape 6 + data-model.md Feature Flag Registry are authoritative. spec.md FR-019 updated with full flag table, all defaults `false`. |
| CHK018 | FR-001 said "three separate jobs"; plan uses 5 | T005 | **RESOLVED**: spec.md FR-001 updated to "five separate jobs" (unit-tests, integration, bdd-tests, contract-tests, sonarcloud) with full dependency graph. FR-004, FR-005, FR-006, SC-001, US1, Key Entities all updated consistently. |
| CHK014 | FR-015 specified CRITICAL only; checklist input said CRITICAL,HIGH | T009 | **RESOLVED**: CRITICAL blocks publication (`exit-code: 1`); HIGH is reported in workflow summary but does NOT block. spec.md FR-015 updated to make this explicit. |

---

## Notes

- All third-party GitHub Actions `uses:` in T005, T009, T014, T015 MUST be pinned to full commit SHA, not version tags (SEC-002). Use Dependabot (T029) to automate SHA updates.
- Write T023 test FIRST and confirm it fails before implementing T021 (fail-safe adapter)
- Commit after each task or logical group using Conventional Commits format
- See `contracts/ci-job-matrix.md` for the visual CI job dependency graph (referenced by T005)
- `UNLEASH_API_TOKEN` default is local profile only (SEC-004) — document this explicitly in T022
- `k8s/` directory must never contain any file with `Kind: Secret` — enforced by T016 validation and policy
