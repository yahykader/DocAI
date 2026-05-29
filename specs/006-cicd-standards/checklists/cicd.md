# CI/CD Pipeline & Standards — Requirements Quality Checklist

**Purpose**: Unit-test the quality of Module 1.B requirements — completeness, clarity, consistency, and measurability across spec.md and plan.md. Items ask whether requirements are *well-written*, not whether the implementation works.  
**Created**: 2026-05-29  
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md)  
**ADR scope**: ADR-008 (CI OOM prevention) · ADR-010 (EXPLAIN PLAN gate)  
**Checklist type**: Requirements quality (not implementation verification)

---

## Requirement Completeness

- [ ] CHK001 - Are Dependabot dependency update requirements (weekly Maven cadence, CRITICAL CVE deployment block) documented as formal spec functional requirements? Referenced in implementation intent as BR-DEP-001/BR-DEP-002 but absent from FR-001 through FR-027b. [Gap]

- [ ] CHK002 - Is the ClassLength constraint (≤ 200 lines per class) formalized as a functional requirement in the spec? Currently only in plan Étape 1 — missing from the FR-010 to FR-012 code standards section. [Gap, Spec §FR-010]

- [ ] CHK003 - Are PII masking requirements (IBAN, SIRET, email, name) explicitly specified as a functional requirement? Plan confirms the implementation exists but FR-023 does not mandate PII masking as an observable requirement. [Gap, Spec §FR-023]

- [ ] CHK004 - Are the 6 feature flag names and their default values per environment (DEV, STAGING, PROD) documented in the spec? FR-019 states "exactly 6 feature flags" but leaves naming and defaults entirely to the plan. [Gap, Spec §FR-019]

- [ ] CHK005 - Is the Kubernetes container securityContext (runAsNonRoot, readOnlyRootFilesystem, allowPrivilegeEscalation, capabilities.drop) formalized as a spec functional requirement? Added via plan security amendment SEC-001 but not back-propagated to FR-016. [Gap, Spec §FR-016]

- [ ] CHK006 - Are GitHub Actions third-party action SHA-pinning requirements documented in the spec? Added as plan amendment (SEC-002) but absent from spec CI pipeline requirements. [Gap, Spec §FR-001]

- [ ] CHK007 - Is the Unleash insecure token restriction (local profile only) and staging/prod credentials management formally specified in the spec? Added as plan amendment SEC-004 but absent from FR-019 or FR-022. [Gap, Spec §FR-019]

- [ ] CHK008 - Are Grafana dashboard and Circuit Breaker OPEN alerting requirements documented as functional requirements? Referenced in implementation intent but absent from all spec FR sections. [Gap]

- [ ] CHK009 - Are secret rotation timelines (90-day: OpenAI/Keycloak/Stripe; 180-day: MongoDB) and reload-without-redeployment requirements explicitly documented in the spec or formally referenced via Constitution Annex C? [Gap, Completeness]

- [ ] CHK010 - Are Kubernetes deployment rollback requirements defined for failed staging/production rollouts? The edge cases section identifies OOM and registry unavailability but not rollback procedure or failure criteria. [Gap, Spec §Edge Cases]

---

## Requirement Clarity

- [ ] CHK011 - Is the slow query logging threshold quantified in FR-027? The requirement reads "configurable threshold" without specifying a value. Implementation intent implies 100ms — is this a requirement or an uncontrolled default? [Clarity, Spec §FR-027]

- [ ] CHK012 - Are the specific numeric values for Kubernetes manifests (maxUnavailable, maxSurge, minReplicas, maxReplicas, CPU target percentage) stated in the spec requirements, or deferred entirely to plan/implementation? FR-016 and FR-018 describe categories without quantifying values. [Clarity, Spec §FR-016, §FR-018]

- [ ] CHK013 - Is the `MAVEN_OPTS` specification complete and authoritative? FR-005 specifies `-Xmx512m` and `-Xmx1g` (job 3), but implementation intent adds `-Xms256m`. Is the minimum heap setting a requirement or a suggested default? [Clarity, Spec §FR-005]

- [ ] CHK014 - Is the Trivy vulnerability severity gate consistent across all spec references? FR-015 specifies CRITICAL only, but implementation intent includes HIGH. Does the spec require blocking on HIGH-severity CVEs? [Clarity, Conflict, Spec §FR-015]

- [ ] CHK015 - Is the "manual approval" requirement for production deployment sufficiently specific — does it define the number of required reviewers, who qualifies, and whether the GitHub Environment name `production` is a hard requirement? [Clarity, Spec §FR-015c]

- [ ] CHK016 - Is the Conventional Commits convention documented in spec requirements or only in CLAUDE.md project instructions? If it's a measurable requirement (e.g., CI commit-lint check), it should appear as a formal FR or Assumption with testable acceptance criteria. [Clarity, Gap]

---

## Requirement Consistency

- [ ] CHK017 - Is the `notifications.inapp.enabled` default value consistent across all authoritative documents? Plan Étape 6 flags table shows `false`; implementation intent specifies `true` for DEV. Which value is the authoritative requirement? [Conflict, Spec §FR-019]

- [ ] CHK018 - Is the "three separate jobs" wording in FR-001 consistent with the five-job implementation plan? The ADR-008 deviation is justified in plan Complexity Tracking but FR-001 still reads "three separate jobs (1) … (2) … (3) …". [Inconsistency, Spec §FR-001]

- [ ] CHK019 - Are workflow trigger conditions (branches, event types, tag patterns) consistently defined for all four workflow files (01-ci, 02-docker, 03-staging, 04-production)? User Story 2 mentions `main` merge but the complete trigger matrix is absent from spec requirements. [Completeness, Consistency]

- [ ] CHK020 - Is the management port (9091) consistently specified across HEALTHCHECK, liveness/readiness probe, and Prometheus scrape target requirements? FR-016 and FR-025 reference these endpoints without naming the port. [Consistency, Spec §FR-016, §FR-025]

---

## Acceptance Criteria Quality

- [ ] CHK021 - Is the Docker image size constraint (< 300 MB) captured as a measurable success criterion? Appears in plan Technical Context but absent from SC-001 through SC-010. [Measurability, Gap]

- [ ] CHK022 - Are PIT mutation testing (≥ 85% on docai-domain) and SonarCloud duplication (≤ 3%) thresholds defined as measurable success criteria? Referenced in CLAUDE.md project standards but absent from spec SC section. [Measurability, Gap]

- [ ] CHK023 - Are per-job CI duration targets defined? SC-001 specifies end-to-end feedback ≤ 15 min but not individual job ceilings — is a per-job breakdown required for SLA accountability? [Measurability, Spec §SC-001]

- [ ] CHK024 - Is the HPA scale-out responsiveness formalized as a success criterion? User Story 3 scenario states "within 60 seconds" but this timing is not captured in SC-001 through SC-010. [Measurability, Spec §US-3]

---

## Scenario & Edge Case Coverage

- [ ] CHK025 - Are requirements defined for CI job OOM behavior? The edge cases section asks "what happens when a CI job exceeds 7GB runner memory" but does not specify expected behavior, retry strategy, or developer notification. [Coverage, Spec §Edge Cases]

- [ ] CHK026 - Are requirements defined for the Docker registry unavailability scenario? The edge cases section identifies this scenario but provides no required fallback behavior or alert mechanism. [Coverage, Spec §Edge Cases]

- [ ] CHK027 - Are requirements defined for post-publish CVE discovery? FR-015 prevents publishing a CRITICAL-CVE image, but does not specify required behavior when a critical vulnerability is found in an already-published image. [Coverage, Gap, Spec §FR-015]

- [ ] CHK028 - Is the RollingUpdate strategy explicitly scoped as the only supported deployment model? Are blue-green or canary deployment requirements intentionally excluded, and is this exclusion documented? [Coverage, Spec §FR-016]

---

## Security Requirements Coverage

- [ ] CHK029 - Is the prohibition on `Secret` YAML files in the repository formally stated as a spec FR, or only described in plan notes? Without an FR, the constraint cannot be validated against the spec's acceptance criteria. [Security, Gap, Spec §FR-016]

- [ ] CHK030 - Is the Trivy scan scope defined? FR-015 requires scanning but does not specify whether it covers OS packages, JVM runtime, application dependencies, or all layers. An unclear scope may leave known-bad dependency trees undetected. [Clarity, Security, Spec §FR-015]

- [ ] CHK031 - Are GITHUB_TOKEN permission scope requirements (per-workflow `permissions:` blocks) documented in the spec? Added as plan amendment SEC-003 but absent from spec CI pipeline requirements. [Gap, Security, Spec §FR-001]

---

## Non-Functional Requirements

- [ ] CHK032 - Is the Unleash flag polling interval (15 seconds) documented as an NFR? SC-006 specifies "within 30 seconds" propagation but does not constrain the SDK polling interval — implementers could choose a value that violates SC-006. [NFR, Clarity, Spec §SC-006]

- [ ] CHK033 - Are Checkstyle exclusion patterns for auto-generated code (Lombok, MapStruct) formally specified as an NFR? Currently placed in Assumptions — could be interpreted differently by different implementers and is not testable from the spec alone. [NFR, Spec §Assumptions]

- [ ] CHK034 - Is Unleash internal-only access (ClusterIP, no public ingress in staging/prod) specified as an NFR? Added as plan amendment SEC-004 but absent from spec network or security requirements. [NFR, Security, Gap]

---

## Dependencies & Assumptions Validation

- [ ] CHK035 - Is the pre-provisioned Kubernetes cluster assumption validated and traceable? Assumptions state "A Kubernetes staging cluster is already available" but no FR or SC verifies deployment against a missing or mis-configured cluster. [Assumption, Spec §Assumptions]

- [ ] CHK036 - Are Constitution Annex C references (BR-ROT-001 to BR-ROT-004) explicitly cited in the spec so that implementers can locate rotation requirements without external document lookup? [Traceability, Gap]

---

## Notes

- Check items off as completed: `[x]`
- Items flagged `[Conflict]` require a decision before implementation begins
- Items flagged `[Gap]` indicate a missing requirement that should be added to spec or accepted as a documented assumption
- Items flagged `[Inconsistency]` require alignment between spec, plan, and implementation intent before the task is generated
- **Priority order for resolution**: CHK017 (conflict) · CHK018 (inconsistency) · CHK014 (conflict) first, then all [Gap] items
