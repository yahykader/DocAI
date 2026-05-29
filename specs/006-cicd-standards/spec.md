# Feature Specification: CI/CD Pipeline & Standards Setup (Module 1.B)

**Feature Branch**: `006-cicd-standards`  
**Created**: 2026-05-29  
**Status**: Draft  
**Module**: Module 1.B — Standards & CI/CD (Partie 1 — Setup, Semaine 1)

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Automated Quality Gate on Every Push (Priority: P1)

A developer pushes code to a feature branch. The CI pipeline automatically starts, runs three independent jobs in sequence, and provides clear feedback within minutes: unit tests + hexagonal architecture checks, then integration tests against real services, then code quality gates. If any mandatory gate fails, the pipeline stops and the developer is notified before the code can be merged.

**Why this priority**: This is the safety net for the entire project. Without it, code quality standards cannot be enforced consistently and every other module depends on this feedback loop being in place.

**Independent Test**: Can be fully tested by pushing a commit to any branch and observing that three CI jobs run in order, each producing a pass/fail result that gates the next step.

**Acceptance Scenarios**:

1. **Given** a developer pushes code with an ArchUnit violation, **When** Job 1 (unit tests) runs, **Then** the pipeline stops immediately and no subsequent jobs execute.
2. **Given** a developer pushes code where `docai-domain` test coverage drops below 90%, **When** Job 3 (quality gates) runs, **Then** the pull request is blocked from merging.
3. **Given** a developer pushes code with global coverage below 80%, **When** Job 3 runs, **Then** the pull request is blocked with a clear coverage report.
4. **Given** SonarCloud detects one or more new bugs in the changed code, **When** Job 3 completes, **Then** the merge is blocked and the developer sees which lines introduced bugs.
5. **Given** a developer pushes valid code passing all gates, **When** all three jobs complete, **Then** the pull request is green and eligible for merge.

---

### User Story 2 — Secure Docker Image Build and Publish (Priority: P2)

A DevOps engineer merges code to `main`. The pipeline automatically builds a production-ready Docker image using a multi-stage build, scans it for security vulnerabilities, and publishes it to the container registry only if no critical vulnerabilities are found. The image runs as a non-privileged user to reduce attack surface.

**Why this priority**: A publishable, security-scanned image is required before any deployment to staging or production is possible.

**Independent Test**: Can be fully tested by merging to `main`, observing the Docker build job run, injecting a known CRITICAL CVE to verify the image is blocked, then removing it to confirm the image is published successfully.

**Acceptance Scenarios**:

1. **Given** a successful merge to `main`, **When** the Docker job runs, **Then** the image is built using a multi-stage process producing a minimal JRE 21 Alpine image.
2. **Given** a built image, **When** the security scanner runs, **Then** any image containing a CRITICAL vulnerability is not pushed to the registry.
3. **Given** a clean image passing the security scan, **When** the publish step runs, **Then** the image is available in the registry tagged with the commit SHA and `latest`.
4. **Given** the published image is run as a container, **When** the process identity is checked, **Then** the application runs as the non-root user `docai`.

---

### User Story 3 — Automated Staging Deployment with Kubernetes (Priority: P3)

After a Docker image is published, the pipeline automatically deploys the new version to the staging environment using Kubernetes manifests. The deployment scales automatically based on CPU load and the service becomes reachable at its internal endpoint.

**Why this priority**: Staging deployments validate the full integration of code, image, and configuration before production, but the CI/quality gates (P1) and secure image (P2) are prerequisites.

**Independent Test**: Can be fully tested by observing a staging rollout after a successful Docker publish, verifying the new pod version is running, and confirming the HPA activates under simulated load.

**Acceptance Scenarios**:

1. **Given** a new Docker image is published, **When** the staging deploy job triggers, **Then** Kubernetes applies the deployment manifest and rolls out the new version with zero downtime.
2. **Given** the application is running in staging, **When** CPU utilization exceeds the defined threshold, **Then** the HPA automatically increases the replica count within 60 seconds.
3. **Given** the staging deployment is complete, **When** the internal service endpoint is queried, **Then** the health check responds successfully.

---

### User Story 4 — Feature Flag-Controlled Rollout (Priority: P3)

A product owner wants to enable a new processing feature for a subset of tenants without redeploying. The feature flag configuration in Unleash allows toggling six defined flags per tenant or globally, with changes taking effect at runtime without a restart.

**Why this priority**: Feature flags enable safe incremental rollout but depend on the application being deployed (P1/P2/P3) first. This can be tested independently once the application is running.

**Independent Test**: Can be fully tested by toggling a flag in Unleash and confirming the application behavior changes without a restart for each of the 6 configured flags.

**Acceptance Scenarios**:

1. **Given** a feature flag is disabled, **When** the relevant code path is triggered, **Then** the system follows the fallback behavior.
2. **Given** a feature flag is enabled for a specific tenant, **When** that tenant triggers the relevant action, **Then** the new behavior activates only for that tenant.
3. **Given** all 6 flags are configured, **When** the Unleash dashboard is viewed, **Then** all 6 flags are visible with their current states.

---

### User Story 5 — Structured Observability for Operations (Priority: P2)

An operations engineer investigating a slow document processing job can search logs by `tenantId` and `traceId` to correlate all log entries across services. Metrics dashboards show throughput and error rates, and distributed traces reveal which service component introduced latency.

**Why this priority**: Observability is needed from the first deployment to diagnose issues across all subsequent modules. It has the same urgency as the Docker image (P2) since it must be in place before integration begins.

**Independent Test**: Can be fully tested by generating a request, capturing its `traceId`, querying logs filtered by that `traceId`, confirming all log entries share the same trace, and verifying the trace appears in the distributed tracing UI.

**Acceptance Scenarios**:

1. **Given** any log entry is produced, **When** it is read in the log aggregator, **Then** it includes `tenantId`, `traceId`, severity, timestamp, and message as structured JSON fields.
2. **Given** a request spans multiple service calls, **When** the trace is retrieved, **Then** all spans are visible and linked under the same `traceId`.
3. **Given** the application is running, **When** the metrics endpoint is queried, **Then** at least throughput (requests/sec), error count, and JVM memory usage are available.

---

### Edge Cases

- What happens when a CI job exceeds the 7 GB GitHub runner memory limit?
- How does the pipeline behave if the container registry is temporarily unreachable?
- What happens when Unleash is unavailable at startup — does the application use safe defaults?
- How does the HPA behave when all replicas are already at the maximum?
- What happens when a `traceId` is missing from an incoming request — is one generated automatically?
- How does Checkstyle handle auto-generated code (e.g., Lombok, MapStruct output)?

---

## Requirements *(mandatory)*

### Functional Requirements

#### CI Pipeline (GitHub Actions)

- **FR-001**: The CI system MUST run three separate jobs: (1) unit tests + architecture checks, (2) integration tests, (3) quality gates and code analysis.
- **FR-002**: Each CI job MUST be individually cancellable and independently reportable to the pull request status.
- **FR-003**: Job 1 MUST fail immediately on any hexagonal architecture rule violation, without running remaining test suites.
- **FR-004**: Job 2 MUST activate container reuse to reduce startup overhead on the CI runner.
- **FR-005**: All jobs MUST cap JVM heap usage to prevent out-of-memory failures on shared CI runners (512 MB for jobs 1–2, 1 GB for job 3).
- **FR-006**: Job 3 MUST run Checkstyle validation; any violation of the method length, parameter count, or cyclomatic complexity limits MUST fail the build.
- **FR-007**: The `docai-domain` module MUST achieve at least 90% test coverage; any PR dropping below this threshold MUST be blocked.
- **FR-008**: Global project test coverage MUST remain at or above 80%; PRs violating this MUST be blocked.
- **FR-009**: The code analysis tool MUST block merges when it detects at least one new bug in the changed code.

#### Code Standards (Checkstyle)

- **FR-010**: All production Java methods MUST NOT exceed 20 lines of executable code.
- **FR-011**: All production Java methods MUST NOT declare more than 4 parameters.
- **FR-012**: The cyclomatic complexity of any method MUST NOT exceed 10.

#### Docker & Container Security

- **FR-013**: The Docker build MUST use a multi-stage process: a build stage producing the application artifact, and a runtime stage using JRE 21 on Alpine Linux.
- **FR-014**: The runtime container MUST run the application process as the non-root user `docai`.
- **FR-015**: The image MUST be scanned for vulnerabilities before publication; images with at least one CRITICAL-severity vulnerability MUST NOT be published.

#### Kubernetes Manifests

- **FR-016**: The deployment manifest MUST define resource requests and limits, liveness and readiness probes, and rolling update strategy.
- **FR-017**: The service manifest MUST expose the application on its designated internal port.
- **FR-018**: The horizontal pod autoscaler manifest MUST define scale triggers based on CPU utilization, with configurable minimum and maximum replica counts.

#### Feature Flags

- **FR-019**: The system MUST define and configure exactly 6 feature flags in the feature flag management system.
- **FR-020**: Each feature flag MUST support per-tenant targeting so the same flag can be active for one tenant and inactive for another simultaneously.
- **FR-021**: Feature flag state changes MUST take effect at runtime without requiring an application restart or redeployment.
- **FR-022**: When the feature flag service is unavailable, the application MUST fall back to safe default values (flags treated as disabled) without crashing.

#### Observability

- **FR-023**: Every log entry produced by the application MUST be formatted as structured JSON, including at minimum: `timestamp`, `level`, `message`, `tenantId`, and `traceId`.
- **FR-024**: The application MUST propagate `traceId` across all inbound and outbound calls; if no `traceId` is present on an incoming request, one MUST be generated automatically.
- **FR-025**: The application MUST expose operational metrics covering request throughput, error rates, and JVM resource consumption.
- **FR-026**: The application MUST integrate with the distributed tracing system so that all spans produced by a single request are linked under the same trace.
- **FR-027**: In the development environment, slow database queries exceeding a configurable threshold MUST be logged with their execution plan for diagnostic use.

### Key Entities

- **CI Pipeline**: A sequence of three ordered jobs (unit-arch, integration, quality) triggered on push and pull request events; each job has a pass/fail status reported to the branch protection rules.
- **Docker Image**: An immutable, versioned artifact produced from the source code; tagged with commit SHA; associated with a Trivy security scan result.
- **Kubernetes Manifest Set**: Three files (Deployment, Service, HPA) that together define how the application runs, is reachable, and scales in a cluster.
- **Feature Flag**: A named toggle stored in the feature flag service, supporting global and per-tenant states; consumed at runtime by the application without restart.
- **Structured Log Entry**: A JSON-formatted record attached to a single request context, carrying `tenantId` and `traceId` to enable correlation across service boundaries.
- **Distributed Trace**: A tree of spans sharing a common `traceId`, representing the full execution path of a request across service components.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer receives pass/fail feedback from all three CI stages within 15 minutes of pushing a commit.
- **SC-002**: Any ArchUnit architecture violation causes the pipeline to stop within the first CI job, before integration tests run.
- **SC-003**: A pull request that reduces `docai-domain` coverage below 90% or global coverage below 80% is automatically blocked from merging without manual intervention.
- **SC-004**: Zero Docker images containing a CRITICAL vulnerability are reachable in the container registry.
- **SC-005**: The published container image starts with a non-root process identity (verifiable in 5 seconds by inspecting the running container).
- **SC-006**: All 6 feature flags can be toggled independently without restarting the application, with the new state reflected within 30 seconds.
- **SC-007**: An operations engineer can retrieve all log entries for a given request in under 10 seconds by filtering on `traceId` alone.
- **SC-008**: 100% of log entries produced by the application include both `tenantId` and `traceId` fields when processing a tenant-scoped request.
- **SC-009**: The distributed tracing UI shows a complete trace for any processed request, with all spans linked under the same `traceId`.
- **SC-010**: Slow queries in the development environment are logged within 1 second of their execution, including sufficient diagnostic detail to identify the query and its execution plan.

---

## Assumptions

- The GitHub repository uses branch protection rules that consume CI job statuses to block direct merges to `main`.
- The container registry (e.g., GitHub Container Registry or equivalent) is already provisioned and accessible from the CI environment.
- A Kubernetes staging cluster is already available; `kubectl` access credentials are stored as CI secrets.
- The feature flag service (Unleash) is already running in the shared infrastructure (see Docker Compose services); the application only needs to connect and register flags.
- Auto-generated code (produced by annotation processors such as Lombok or MapStruct) is excluded from Checkstyle and coverage enforcement.
- The distributed tracing system and metrics collection infrastructure are part of the shared Docker Compose stack (Grafana Tempo, Prometheus); the application only adds the integration layer.
- ADR-008 is in force: memory limits (512 MB for jobs 1–2, 1 GB for job 3) and container reuse for integration tests are non-negotiable CI constraints.
- ADR-010 is in force: slow query logging is activated only in the development profile; it is not enabled in staging or production.
- The 6 feature flags correspond to capabilities planned in subsequent modules (Module 1 through Module 4); their exact names and default states will be confirmed during planning.
