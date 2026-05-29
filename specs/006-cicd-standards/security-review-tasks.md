---
document_type: security-review
review_type: tasks
assessment_date: 2026-05-29
codebase_analyzed: DocAI / specs/006-cicd-standards
total_files_analyzed: 5
total_findings: 5
overall_risk: MODERATE
critical_count: 0
high_count: 0
medium_count: 2
low_count: 2
informational_count: 1
owasp_categories: [A05, A07, A08]
cwe_ids: [CWE-250, CWE-494, CWE-798]
field_summaries:
  document_type: "Always 'security-review'. Allows indexers to skip non-review documents."
  review_type: "Which command generated this document: audit, branch, staged, plan, tasks, or followup."
  assessment_date: "ISO 8601 date the review was performed (YYYY-MM-DD)."
  overall_risk: "Highest severity tier with active findings (CRITICAL, HIGH, MODERATE, LOW, INFORMATIONAL)."
  critical_count: "Number of Critical findings (CVSS 9.0-10.0)."
  high_count: "Number of High findings (CVSS 7.0-8.9)."
  medium_count: "Number of Medium findings (CVSS 4.0-6.9)."
  low_count: "Number of Low findings (CVSS 0.1-3.9)."
  informational_count: "Number of Informational findings."
  owasp_categories: "OWASP Top 10 2025 categories (A01-A10) that have at least one finding."
  cwe_ids: "CWE identifiers referenced in this document."
  finding_id: "Unique finding identifier (SEC-NNN) for cross-referencing and task linkage."
  location: "File path and line number of the vulnerable code (path/to/file.ext:line)."
  owasp_category: "OWASP Top 10 2025 category for this finding (AXX:2025-Name)."
  cwe: "Common Weakness Enumeration identifier with short name (CWE-NNN: Name)."
  cvss_score: "CVSS v3.1 base score (0.0-10.0). 9.0+=Critical, 7.0-8.9=High, 4.0-6.9=Medium, 0.1-3.9=Low."
  spec_kit_task: "Spec-Kit task ID for backlog tracking and remediation follow-up (TASK-SEC-NNN)."
---

# Security Review — Task Phase: Module 1.B CI/CD Pipeline & Standards

**Review Date**: 2026-05-29  
**Reviewer**: Security Review Extension (speckit-security-review-tasks)  
**Overall Risk**: 🟡 MODERATE

---

## Executive Summary

The Module 1.B task list is well-aligned with the plan security review (SEC-001 through SEC-004). All five findings from the plan review are correctly translated into implementation tasks — Kubernetes `securityContext` (T011), SHA pinning (T005/T009/T014/T015), `permissions:` blocks (T005/T009/T014/T015), Unleash token scoping (T022), and image signing deferral. No new HIGH findings were identified.

Five task-level gaps were found: two MEDIUM (no automated gate to verify SHA pinning is present, no task documents kubectl credential setup for staging/production), two LOW (TDD order inverted for T021/T023, docker-compose password missing LOCAL-ONLY annotation), and one informational (insecure Unleash token hardcoded in a validation curl command).

All findings are resolvable with minor task amendments. The task list is ready to implement after these corrections.

---

## Artifacts Reviewed

| File | Purpose |
|------|---------|
| `specs/006-cicd-standards/tasks.md` | 33 implementation tasks across 8 phases |
| `specs/006-cicd-standards/plan.md` | Implementation plan (Étapes 1–7, security amendments) |
| `specs/006-cicd-standards/spec.md` | Feature specification (FR-001 through FR-027b, conflict-resolved) |
| `specs/006-cicd-standards/security-review-plan.md` | Prior plan-phase review (SEC-001 through SEC-005) |
| `.specify/memory/constitution.md` | DocAI Constitution (Section VII secrets, Section III test-first) |

---

## Vulnerability Findings

### TASK-SEC-001 — No Automated Gate Validates SHA Pinning Compliance

| Field | Value |
|-------|-------|
| **Severity** | 🟡 MEDIUM |
| **CVSS Score** | 5.5 |
| **Location** | `tasks.md` — T007, T010, T016 (validation tasks) |
| **OWASP** | A08:2021 — Software and Data Integrity Failures |
| **CWE** | CWE-494: Download of Code Without Integrity Check |
| **Related Plan Finding** | SEC-002 |

**Problem**: T005, T009, T014, and T015 all specify that third-party `uses:` references MUST be pinned to full commit SHA. However, the corresponding validation tasks (T007, T010, T016) do not include a check that verifies SHA pinning is actually present in the generated workflow files. There is no `grep` or `yamllint` step that would catch a developer accidentally writing `uses: aquasecurity/trivy-action@v0.24.0` instead of the SHA equivalent.

Without an explicit gate, a pinning omission silently ships to `main` — a supply chain integrity failure under SEC-002.

**Recommended fix**: Add a sub-step to T007 (and/or T033):

```bash
# Verify no mutable tags in workflow files
grep -rn "uses:.*@v[0-9]" .github/workflows/ && echo "ERROR: mutable action tag detected" && exit 1
grep -rn "uses:.*@main" .github/workflows/ && echo "ERROR: branch-pinned action detected" && exit 1
echo "All actions are SHA-pinned"
```

Alternatively, add `actionlint` (GitHub Actions linting tool) as a sub-check inside T007 — it validates SHA pinning as part of its ruleset.

---

### TASK-SEC-002 — kubectl Credential Setup for Staging/Production Not Documented

| Field | Value |
|-------|-------|
| **Severity** | 🟡 MEDIUM |
| **CVSS Score** | 5.0 |
| **Location** | `tasks.md` — T014, T015 (staging and production deploy workflows) |
| **OWASP** | A05:2021 — Security Misconfiguration |
| **CWE** | CWE-250: Unnecessary Privileges |
| **Related Plan Finding** | None (new finding) |

**Problem**: T014 and T015 create deploy workflows that run `kubectl set image` and `kubectl rollout status`. Both specify `permissions: contents: read, id-token: write` — the `id-token: write` suggests OIDC-based cluster authentication, which is the correct pattern. However, no task:

1. Documents which cloud provider's OIDC federation is configured (AWS EKS IRSA, GKE Workload Identity, or kubeconfig secret)
2. Verifies that the Kubernetes cluster's RBAC is scoped to minimum permissions (only `apps/deployments` patch + rollout status read, not cluster-admin)
3. Confirms the staging kubeconfig or OIDC role is stored as a GitHub secret with the expected name (`KUBE_CONFIG_STAGING`, `AWS_ROLE_ARN`, etc.)

If the cluster RBAC grants cluster-admin or the kubeconfig secret is misconfigured, the deploy workflow can reach any cluster namespace.

**Recommended fix**: Add a task in Phase 8 (Polish) or as a sub-step in T016:

```markdown
- [ ] T016b [P] [US3] Document kubectl auth mechanism in `docs/k8s-auth.md`:
  - Confirm OIDC federation method (IRSA/Workload Identity/kubeconfig)
  - Document required GitHub secrets (`KUBE_CONFIG_STAGING`, etc.)
  - Document Kubernetes RBAC role: minimum permissions = Deployment patch + rollout status read in namespace `docai-staging`
  - Verify RBAC: `kubectl auth can-i patch deployments --as=github-actions-sa -n docai-staging` → yes
  - Verify RBAC (negative): `kubectl auth can-i delete namespaces --as=github-actions-sa` → no
```

---

### TASK-SEC-003 — TDD Order Inverted: T021 Listed Before T023

| Field | Value |
|-------|-------|
| **Severity** | 🔵 LOW |
| **CVSS Score** | 2.1 |
| **Location** | `tasks.md` — Phase 6, T021 and T023 |
| **OWASP** | N/A (Constitution III compliance) |
| **CWE** | N/A |
| **Constitution Reference** | Section III — Test-First Development (non-negotiable) |

**Problem**: Constitution Section III requires "Tests drive design and are mandatory before production code." The notes section at the bottom of tasks.md correctly states: "Write T023 test FIRST and confirm it fails before implementing T021 (fail-safe adapter)."

However, the task ordering in Phase 6 lists T021 (adapter implementation) before T023 (unit test). A developer reading linearly will implement T021 first, making T023 a verification test rather than a design-driving test. The fail-safe `try/catch` pattern in T021 should be driven by a red T023 test first.

This is a Constitution III compliance issue, not a security risk per se — but the fail-safe behavior is a security property (Unleash unavailability must not crash the application), so its test deserves the design authority that TDD provides.

**Recommended fix**: Swap T021 and T023 in the task list so T023 appears first:

```
T021 → becomes T023-a (unit test, write first, confirm red)
T023 → becomes T021-a (adapter, implement to make T023-a green)
```

Or relabel and explicitly mark: `T021 [US4] Write unit test for UnleashFeatureFlagAdapter fail-safe...` and `T022 [US4] Implement UnleashFeatureFlagAdapter...`.

---

### TASK-SEC-004 — docker-compose Unleash PostgreSQL Password Missing LOCAL-ONLY Annotation

| Field | Value |
|-------|-------|
| **Severity** | 🔵 LOW |
| **CVSS Score** | 2.5 |
| **Location** | `tasks.md` — T017 |
| **OWASP** | A07:2021 — Identification and Authentication Failures |
| **CWE** | CWE-798: Use of Hard-coded Credentials |
| **Related Plan Finding** | SEC-004 |

**Problem**: T017 instructs adding `unleash-db` to `docker-compose.yml` with `POSTGRES_DB/USER/PASSWORD all set to "unleash"`. Plan amendment SEC-004 restricts this default to the local profile only, but the T017 task description does not include an inline comment marking the credentials as LOCAL-ONLY.

A developer implementing T017 would write `POSTGRES_PASSWORD: unleash` in docker-compose.yml without seeing SEC-004's constraint. If the same docker-compose.yml is later adapted for a staging environment (common for quick staging tests), these plaintext credentials could be inadvertently promoted.

**Recommended fix**: Update T017's docker-compose snippet to include an inline comment:

```yaml
unleash-db:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: unleash
    POSTGRES_USER: unleash
    POSTGRES_PASSWORD: unleash  # LOCAL PROFILE ONLY — staging/prod: AWS Secrets Manager (SEC-004)
  volumes:
    - unleash-db-data:/var/lib/postgresql/data
```

---

### TASK-SEC-005 — Unleash Insecure Token Hardcoded in T024 Validation Command (Informational)

| Field | Value |
|-------|-------|
| **Severity** | ℹ️ INFORMATIONAL |
| **CVSS Score** | 0.0 |
| **Location** | `tasks.md` — T024 |
| **OWASP** | A07:2021 — Identification and Authentication Failures |
| **CWE** | CWE-798: Use of Hard-coded Credentials |
| **Related Plan Finding** | SEC-004 |

**Problem**: T024 includes:

```bash
curl http://localhost:4242/api/client/features -H "Authorization: *:*.unleash-insecure-api-token"
```

The `*:*.unleash-insecure-api-token` token is documented as unsafe (plan SEC-004). Embedding it directly in a validation task normalizes using it as "the reference token." While tasks.md is documentation rather than committed code, it sets a behavioral precedent.

**Recommended fix** (low priority): Replace with a variable reference:

```bash
UNLEASH_TOKEN="*:*.unleash-insecure-api-token"  # local only per SEC-004
curl http://localhost:4242/api/client/features -H "Authorization: ${UNLEASH_TOKEN}"
```

Add a comment: `# This token is valid for local profile only; replace with AWS Secrets Manager value in staging/prod`.

---

## Confirmed Secure Patterns

The plan-phase findings (SEC-001 through SEC-005) are all correctly translated into tasks:

| Plan Finding | Task | Status |
|-------------|------|--------|
| SEC-001: Kubernetes securityContext | T011 (`runAsNonRoot: true`, `runAsUser: 1001`, `readOnlyRootFilesystem: true`, `/tmp` emptyDir) | ✅ Fully addressed |
| SEC-002: SHA-pinned actions | T005, T009, T014, T015 — explicit SHA requirement in every workflow task | ✅ Required; TASK-SEC-001 adds enforcement gate |
| SEC-003: GITHUB_TOKEN permissions blocks | T005 (per-job `permissions`), T009 (`packages: write`), T014/T015 (`id-token: write`) | ✅ Fully addressed |
| SEC-004: Unleash token scoped to local | T022 (inline comment in `application.yml`) | ✅ Addressed; TASK-SEC-004 adds docker-compose annotation |
| SEC-005: Image signing deferred | Not in task list — correctly omitted for this sprint | ✅ Scope boundary correct |

Additional secure patterns confirmed in tasks:

| Pattern | Task | Detail |
|---------|------|--------|
| No `Secret` YAML in k8s/ | T016 (explicit `ls k8s/` check for `*secret*` files) | ✅ Validated by dry-run |
| `emptyDir` volume for /tmp | T011 (mitigates `readOnlyRootFilesystem` Spring Boot startup failure) | ✅ SEC-001 compliant |
| Fail-safe Unleash adapter | T021 + T023 (catch-all → `return false`, never rethrows) | ✅ Constitution-compliant |
| Branch protection enforcement | T030 (1 reviewer + status checks required, no direct push) | ✅ GitFlow compliant |
| Secrets rotation documentation | T031, T032 (Annex C: 90d/180d timelines, rotation journal) | ✅ BR-ROT-001 through BR-ROT-004 |
| Dependabot github-actions ecosystem | T029 (weekly SHA updates via Dependabot PRs) | ✅ SEC-002 automation |
| Trivy CRITICAL gate | T009 (`exit-code: 1` on CRITICAL; HIGH reported but non-blocking per CHK014 resolution) | ✅ FR-015 implemented |

---

## Task Sequencing Assessment

Security task ordering is correct for all HIGH-risk tasks:

| Risk | Task | Precedes | Assessment |
|------|------|---------|------------|
| Supply chain | SHA pinning (T005, T009) | Any branch merge to main | ✅ Required before CI is live |
| Privilege escalation | securityContext (T011) | Staging deployment (T014) | ✅ K8s manifest validated before deploy |
| Secret leakage | Unleash token annotation (T022) | Docker image build (T009) | ✅ Annotation in place before flag reads occur |
| Container root | Non-root user (T008) | Trivy scan (T009) | ✅ Dockerfile precedes image scan |
| Test-before-code | T023 fail-safe test | T021 adapter | ❌ TASK-SEC-003: order inverted in task list |

The only sequencing defect is TASK-SEC-003 (TDD order for T021/T023), which is LOW severity and correctable by reordering tasks in Phase 6.

---

## Parallel Task Safety

Parallel tasks marked `[P]` have been checked for security prerequisite bypass:

- **T011 [P], T012 [P], T013 [P]** (K8s manifests): All run after Phase 4 (Dockerfile) is complete. T011 carries the `securityContext` requirement independently — no parallel path can skip it. ✅
- **T018 [P]** (`FeatureFlagPort.java`): Interface only, no auth or secrets involved. ✅
- **T025 [P], T026 [P]** (Prometheus/Tempo config): Read-only verification of existing config. No secrets modified. ✅
- **T029 [P], T030 [P], T031 [P], T032 [P]** (Polish): All independent documentation/config tasks. No security prerequisite violated. ✅

---

## Remediation Priority

| Finding | Severity | Fix Effort | Blocking? |
|---------|---------|-----------|-----------|
| TASK-SEC-001 Missing SHA-pin gate in T007 | MEDIUM | 15 min (add grep step) | Recommended before T007 execution |
| TASK-SEC-002 kubectl RBAC documentation | MEDIUM | 30 min (add T016b) | Recommended before T014 execution |
| TASK-SEC-003 TDD order T021 before T023 | LOW | 5 min (reorder tasks) | Recommended before Phase 6 starts |
| TASK-SEC-004 docker-compose password annotation | LOW | 5 min (add comment) | Recommended in T017 |
| TASK-SEC-005 Token in curl command | INFORMATIONAL | 5 min (add comment) | Optional |

---

## Memory Hub INDEX.md Row

```text
| specs/006-cicd-standards/security-review-tasks.md | tasks | 2026-05-29 | MODERATE | C:0 H:0 M:2 L:2 I:1 | A05,A07,A08 |
```
