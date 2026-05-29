---
document_type: security-review
review_type: plan
assessment_date: 2026-05-29
codebase_analyzed: DocAI / specs/006-cicd-standards
total_files_analyzed: 6
total_findings: 5
overall_risk: HIGH
critical_count: 0
high_count: 2
medium_count: 2
low_count: 1
informational_count: 0
owasp_categories: [A01, A05, A07, A08]
cwe_ids: [CWE-250, CWE-269, CWE-345, CWE-494, CWE-798]
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

# Security Review — Plan Phase: Module 1.B CI/CD Pipeline & Standards

**Review Date**: 2026-05-29  
**Reviewer**: Security Review Extension (speckit-security-review-plan)  
**Overall Risk**: 🟠 HIGH

---

## Executive Summary

The Module 1.B plan is well-structured and demonstrates strong security intent: non-root Docker user, Trivy CRITICAL gate, Unleash fail-safe fallback, PII masking in logs, and GitHub Environment protection for production. Five design-level gaps were identified that, if not addressed before implementation, would require retroactive hardening.

The two HIGH findings address supply chain integrity (unpinned GitHub Actions) and container privilege enforcement (missing Kubernetes securityContext). Both are straightforward to fix at plan time. The two MEDIUM findings address GITHUB_TOKEN over-permissioning and the Unleash insecure default API token leaking to non-local environments.

No CRITICAL findings. All findings are preventable during implementation with targeted additions to the plan.

---

## Artifacts Reviewed

| File | Purpose |
|------|---------|
| `specs/006-cicd-standards/plan.md` | Implementation plan (7 étapes) |
| `specs/006-cicd-standards/spec.md` | Feature specification (clarified) |
| `specs/006-cicd-standards/research.md` | Decision rationale |
| `specs/006-cicd-standards/data-model.md` | FeatureFlagPort model, K8s resources, Unleash config |
| `specs/006-cicd-standards/quickstart.md` | Local dev guide |
| `specs/006-cicd-standards/contracts/ci-job-matrix.md` | CI job dependency graph |
| `.specify/memory/constitution.md` | DocAI Constitution (security principles VII) |

---

## Vulnerability Findings

### SEC-001 — Kubernetes Deployment: securityContext Not Specified

| Field | Value |
|-------|-------|
| **Severity** | 🔴 HIGH |
| **CVSS Score** | 7.5 |
| **Location** | `k8s/deployment.yaml` (planned — Étape 5) |
| **OWASP** | A05:2021 — Security Misconfiguration |
| **CWE** | CWE-250: Unnecessary Privileges |
| **TASK** | TASK-SEC-001 |

**Problem**: The `data-model.md` Kubernetes resource model specifies resources, rolling strategy, and probes, but does NOT mention `securityContext` at the pod or container level. Without explicit enforcement, a base image update or misconfiguration could silently run the container as root despite the Dockerfile's `USER docai` instruction. Kubernetes does not enforce the Dockerfile `USER` directive unless `runAsNonRoot: true` is set at the manifest level.

**Missing from plan:**
```yaml
# deployment.yaml — container securityContext (add to plan)
securityContext:
  runAsNonRoot: true
  runAsUser: 1001          # UID assigned to user "docai" in Dockerfile
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop: [ALL]
```

**Fix**: Add the `securityContext` block to the `deployment.yaml` specification in plan.md Étape 5. If `readOnlyRootFilesystem: true` breaks the Spring Boot temp directory, add an `emptyDir` volume mount for `/tmp`.

---

### SEC-002 — GitHub Actions: Third-Party Actions Not Pinned to SHA

| Field | Value |
|-------|-------|
| **Severity** | 🔴 HIGH |
| **CVSS Score** | 7.8 |
| **Location** | `.github/workflows/01-ci.yml`, `02-docker.yml`, `03-deploy-staging.yml` (planned — Étapes 2–4) |
| **OWASP** | A08:2021 — Software and Data Integrity Failures |
| **CWE** | CWE-494: Download of Code Without Integrity Check |
| **TASK** | TASK-SEC-002 |

**Problem**: The plan references named GitHub Actions (`aquasecurity/trivy-action`, `docker/build-push-action`, `docker/login-action`) by version tag (e.g., `@v3`). Tags are mutable — an attacker who compromises the action's repository can push a backdoored version under the same tag. The CI pipeline executes this code with write access to `GITHUB_TOKEN`, the staging kubeconfig, and potentially AWS credentials.

**Constitution alignment**: Constitution Section VII ("No Hardcoded Data / Secrets") extends logically to external code execution integrity.

**Fix**: Pin all third-party actions to their full commit SHA in the plan. Example:
```yaml
# INSECURE (plan currently implies this):
uses: aquasecurity/trivy-action@v0.24.0

# SECURE (what plan must specify):
uses: aquasecurity/trivy-action@6e7b7d1fd3e4fef0c5fa8cce1229c54b2c9bd0d8  # v0.24.0
```
Add a note in plan Étape 2 that all `uses:` references must include the SHA digest. Dependabot can automate SHA updates.

---

### SEC-003 — GitHub Actions: GITHUB_TOKEN Over-Permissioned

| Field | Value |
|-------|-------|
| **Severity** | 🟡 MEDIUM |
| **CVSS Score** | 5.3 |
| **Location** | `.github/workflows/02-docker.yml` (planned — Étape 4) |
| **OWASP** | A01:2021 — Broken Access Control |
| **CWE** | CWE-269: Improper Privilege Management |
| **TASK** | TASK-SEC-003 |

**Problem**: The plan states that `GITHUB_TOKEN` is used for ghcr.io push "with no additional secrets". This is correct, but the plan does not specify that workflows must explicitly declare `permissions:` blocks. Without explicit scoping, the default `GITHUB_TOKEN` permissions apply, which (depending on repository settings) can include `write` on contents, pull requests, issues, and deployments — far beyond what each workflow needs.

**Minimum required permissions per workflow:**

| Workflow | `permissions` block |
|----------|---------------------|
| `01-ci.yml` | `contents: read` |
| `02-docker.yml` | `contents: read`, `packages: write` |
| `03-deploy-staging.yml` | `contents: read`, `id-token: write` (for OIDC if used) |
| `04-deploy-production.yml` | `contents: read`, `id-token: write` |

**Fix**: Add to plan Étape 2: "Each workflow MUST declare an explicit `permissions:` block at the job level. Default permissions are not acceptable."

---

### SEC-004 — Unleash API Token: Insecure Default Not Scoped to Local Profile

| Field | Value |
|-------|-------|
| **Severity** | 🟡 MEDIUM |
| **CVSS Score** | 5.9 |
| **Location** | `data-model.md` (application.yml snippet), `quickstart.md` |
| **OWASP** | A07:2021 — Identification and Authentication Failures |
| **CWE** | CWE-798: Use of Hard-coded Credentials |
| **TASK** | TASK-SEC-004 |

**Problem**: The planned `application.yml` snippet uses `*:*.unleash-insecure-api-token` as the default value for `UNLEASH_API_TOKEN`. This token has wildcard permissions on Unleash and is documented as insecure. The plan does not explicitly state:

1. This default MUST only apply to the `local` Spring profile
2. Staging/prod environments MUST use a scoped Unleash API token stored in AWS Secrets Manager
3. The `unleash-db` PostgreSQL credentials (`unleash`/`unleash`) must be rotated for staging/prod

**Constitution reference**: Section VII, Annex C — all service credentials must be in AWS Secrets Manager with 90-day rotation.

**Fix**: Update the plan (Étape 6) to specify:
- `UNLEASH_API_TOKEN` default is only acceptable for `local` profile
- Staging/prod: scoped token in AWS Secrets Manager → mounted via CSI Driver
- `unleash-db` credentials follow standard PostgreSQL credential rotation (Annex C)
- Unleash service should not be exposed outside the cluster in staging/prod (no public ingress)

---

### SEC-005 — Docker Image: No Provenance Signing Planned

| Field | Value |
|-------|-------|
| **Severity** | 🔵 LOW |
| **CVSS Score** | 3.7 |
| **Location** | `.github/workflows/02-docker.yml` (planned — Étape 4) |
| **OWASP** | A08:2021 — Software and Data Integrity Failures |
| **CWE** | CWE-345: Insufficient Verification of Data Authenticity |
| **TASK** | TASK-SEC-005 |

**Problem**: The plan covers Trivy CRITICAL scanning but does not mention image signing. In a B2B SaaS platform where customers may inspect image provenance (SOC 2, ISO 27001), unsigned images create an audit gap. An attacker with push access to ghcr.io could substitute a malicious image under the same tag.

**Fix** (deferred to later sprint is acceptable): Add to plan Étape 4 as a forward-looking note: "Image signing with Cosign (Sigstore) + SBOM generation to be added in a hardening sprint. Trivy + SLSA provenance attestation via `docker/build-push-action` `provenance: true` covers near-term audit requirements."

---

## Confirmed Secure Patterns

The following design choices are explicitly validated:

| Pattern | Status | Detail |
|---------|--------|--------|
| Non-root container user (`docai`) | ✅ Secure | Dockerfile creates `addgroup/adduser docai`; SEC-001 adds K8s enforcement |
| Trivy CRITICAL gate | ✅ Secure | `exit-code: 1` blocks image publish on CRITICAL CVE |
| GitHub Environment `production` with required reviewer | ✅ Secure | Human approval gate before prod kubectl |
| Unleash fail-safe fallback | ✅ Secure | `catch: return false` — SDK errors never propagate |
| PII masking in structured logs | ✅ Secure | `MaskingJsonGeneratorDecorator` covers IBAN, SIRET, email, phone |
| `tenantId` MDC propagation | ✅ Secure | `TenantMdcFilter` already implemented |
| Management port separated (9091) | ✅ Secure | Actuator not exposed on public 8080 port |
| Secrets via AWS Secrets Manager CSI | ✅ Secure | No `Secret` YAML in repository |
| ArchUnit CI gate | ✅ Secure | Hexagonal boundary enforced at CI — no infra leaks into domain |
| GITHUB_TOKEN for ghcr.io (no extra secrets) | ✅ Secure | Minimal secret surface for image publishing |

---

## Remediation Priority

| Finding | Severity | Fix Effort | Fix Timing |
|---------|---------|-----------|------------|
| SEC-001 Kubernetes securityContext | HIGH | 30 min | Before Étape 5 implementation |
| SEC-002 Actions not pinned to SHA | HIGH | 1h | Before Étape 2 implementation |
| SEC-003 GITHUB_TOKEN over-permissioned | MEDIUM | 30 min | Before Étape 2 implementation |
| SEC-004 Unleash insecure default | MEDIUM | 30 min | Before Étape 6 implementation |
| SEC-005 No image signing | LOW | 2–4h | Deferred — hardening sprint |

---

## Recommended Plan Amendments

### Amendment 1 — Add to plan.md Étape 2 (GitHub Actions)

```markdown
**Security requirements (mandatory):**
- All `uses:` references to third-party actions MUST be pinned to their full commit SHA
- Each workflow job MUST declare an explicit `permissions:` block (see ci-job-matrix.md)
- `01-ci.yml`: permissions: contents: read
- `02-docker.yml`: permissions: contents: read, packages: write
- `03-deploy-staging.yml` / `04-deploy-production.yml`: permissions: contents: read, id-token: write
```

### Amendment 2 — Add to plan.md Étape 5 (Kubernetes)

```markdown
**Security requirements (mandatory):**
- `deployment.yaml` MUST include container-level securityContext:
  runAsNonRoot: true, runAsUser: 1001, readOnlyRootFilesystem: true,
  allowPrivilegeEscalation: false, capabilities.drop: [ALL]
- Add emptyDir volume for /tmp if readOnlyRootFilesystem causes Spring Boot startup failure
```

### Amendment 3 — Add to plan.md Étape 6 (Unleash)

```markdown
**Security requirements (mandatory):**
- UNLEASH_API_TOKEN default (*:*.unleash-insecure-api-token) is ONLY valid for local profile
- staging/prod: scoped Unleash API token in AWS Secrets Manager, mounted via CSI Driver
- unleash-db credentials (unleash/unleash) are local-only; staging/prod uses rotated credentials (Annex C)
- Unleash service has no public ingress in staging/prod (internal ClusterIP only)
```
