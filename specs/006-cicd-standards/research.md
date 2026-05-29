# Research: CI/CD Pipeline & Standards (Module 1.B)

**Phase 0 output** | **Date**: 2026-05-29 | **Status**: Complete — no NEEDS CLARIFICATION remaining

---

## Pre-existing codebase findings

All five questions that would normally require research were resolved by reading the current codebase:

| Topic | Finding |
|-------|---------|
| Checkstyle integration | `maven-checkstyle-plugin` already in `<pluginManagement>` AND bound in `quality-gates` Maven profile; only `ClassLength` rule is missing from `checkstyle.xml` |
| JSON structured logging | `logback-spring.xml` + `logstash-logback-encoder` fully configured for staging/prod profiles; `traceId` + `tenantId` MDC included |
| OpenTelemetry approach | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` present in `docai-bootstrap/pom.xml`; OTLP endpoint → Grafana Tempo port 4317 in `application.yml` |
| MDC propagation | `TenantMdcFilter` exists in `docai-adapter-in-rest` — propagates `tenantId` from JWT to MDC on every request |
| Maven CI profiles | 3 profiles (`unit-tests`, `integration-tests`, `quality-gates`) fully implemented in parent `pom.xml`; maps to the 5 GitHub Actions jobs via profile + flag combinations |

---

## Decision: Docker Registry

- **Decision**: GitHub Container Registry (`ghcr.io`)
- **Rationale**: Project is GitHub-hosted; `GITHUB_TOKEN` built-in to every workflow run — zero extra secret management. Trivy GitHub Action natively authenticates to ghcr.io. Image reference is `ghcr.io/${{ github.repository_owner }}/docai-backend`.
- **Alternatives considered**: Docker Hub (rate limits, extra credentials), AWS ECR (viable but adds IAM complexity for a CI-only push)

---

## Decision: Production Deployment Gate

- **Decision**: GitHub Environment `production` with required reviewer
- **Rationale**: B2B SaaS prod deployments require human sign-off. GitHub Environments provide this natively: reviewer must approve before `kubectl` executes. Free on Team/Enterprise plans. Secrets scoped to environment.
- **Alternatives considered**: Tag-based auto-deploy (no human gate), fully automatic (unacceptable for prod)

---

## Decision: Unleash Hosting

- **Decision**: Self-hosted via `docker-compose.yml` (image `unleashorg/unleash-server` + PostgreSQL 16)
- **Rationale**: All infrastructure is already Dockerized. Keeps the single `docker compose up -d` workflow intact. No external SaaS accounts, no API keys in CI secrets. Unleash SDK connects to `http://localhost:4242` locally and `http://unleash:4242` from app container.
- **Alternatives considered**: `app.unleash.io` SaaS (external dependency, cost, network latency from CI), in-house table (no gradual rollout, no UI)

---

## Decision: GitHub Actions Runner

- **Decision**: `ubuntu-latest` (GitHub-hosted, 7 GB RAM)
- **Rationale**: ADR-008 was specifically designed for this constraint. `MAVEN_OPTS=-Xmx512m` on 5 of the 6 CI steps and container reuse prevent OOM. No infrastructure overhead.
- **Alternatives considered**: Self-hosted runner (more RAM but maintenance burden, security risk), hybrid (adds complexity without clear benefit given ADR-008 mitigations)

---

## Decision: ADR-010 EXPLAIN PLAN Gate

- **Decision**: Manual PR review checklist (`.github/pull_request_template.md`)
- **Rationale**: Automated EXPLAIN PLAN in CI requires a representative dataset and index state that TestContainers does not reliably provide. False positives (COLLSCAN on empty test collections) would erode trust. A mandatory checklist in the PR template ensures human verification on every new query with zero false-positive risk.
- **Alternatives considered**: Automated `explain()` script in integration job (too fragile, depends on seed data), hybrid (adds CI script complexity for marginal gain)

---

## Unleash SDK: Java client

- **Library**: `io.getunleash:unleash-client-java` (latest stable)
- **Integration point**: `docai-bootstrap` — initialized as a `@Bean DefaultUnleash` with `UnleashConfig` pointing to `${docai.unleash.url}`
- **Fail-safe**: SDK's `isEnabled()` returns `false` by default when Unleash is unreachable (built-in); adapter wraps in `try/catch` as additional safety layer

---

## Kubernetes: Secrets CSI Driver

- **Decision**: AWS Secrets Manager CSI Driver (`secrets-store.csi.k8s.io`)
- **Rationale**: Aligns with Constitution Section VII ("AWS Secrets Manager in production"). Secrets mounted as environment variables at pod startup; never stored in Git or Kubernetes `Secret` objects.
- **Impact**: `deployment.yaml` uses `envFrom: secretRef` pointing to `SecretProviderClass`; no `Secret` YAML is committed to this repository.
