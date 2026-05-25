# Specification: Module A — Architecture Hexagonale

**Module**: Module A — Architecture & Principles (Reference Transversal)  
**Skill**: docai-architecture-adr  
**Version**: 1.0.0  
**Date**: 2026-05-25  
**Status**: Ready for Implementation

---

## Overview

Module A establishes the **non-negotiable architectural foundation** for DocAI backend. It defines hexagonal architecture principles, enforces domain isolation via ArchUnit, and establishes quality standards that all downstream modules (Module B, Module C, Module 2-11) must follow.

**Module Type**: Reference/Principles (read first, implement second after Module 1.A)  
**Implementation Order**: Section 2 Module 1.A → **Section 1 Module A** → Section 1 Module B → ...

---

## Functional Requirements

### FR-ARCH-001: Hexagonal Architecture Foundation
**Requirement**: Establish 11 Maven modules organized as hexagonal architecture (Ports & Adapters pattern)

**Acceptance Criteria**:
- ✅ All 11 modules compile: `./mvnw clean compile → BUILD SUCCESS`
- ✅ docai-domain contains ZERO Spring/MongoDB/Kafka/AWS imports (production code)
- ✅ Module dependency graph is acyclic
- ✅ Parent pom.xml declares all 11 modules in <modules> section
- ✅ Each module has correct package root: `fr.docai.*`

**User Story**: As a backend architect, I need clear module boundaries so that domain logic remains testable and technology-independent.

**Success Metric**: Zero framework imports in domain module, all 11 modules compile cleanly.

---

### FR-ARCH-002: ArchUnit Architecture Validation (12 Rules)
**Requirement**: Enforce hexagonal architecture constraints at compile-time via ArchUnit static analysis

**Acceptance Criteria**:
- ✅ HexagonalArchitectureTest class created in docai-bootstrap module
- ✅ All 12 ArchUnit rules implemented and passing
- ✅ Rules test runs in CI pipeline on every commit
- ✅ Rules cover: domain isolation (6 rules), port structure (3 rules), adapter patterns (3 rules)
- ✅ Rule failures block PR merges (CI gate)

**12 Rules**:
1. Domain ✗ Spring packages
2. Domain ✗ MongoDB packages
3. Domain ✗ Kafka packages
4. Domain ✗ AWS SDK packages
5. Domain ✗ HTTP client packages
6. Application layer → domain only
7. Input adapters → application + domain + Spring only
8. Output adapters implement domain ports only
9. No circular dependencies between modules
10. Ports are interfaces (no implementation in domain)
11. DTOs not referenced in domain
12. Bootstrap module isolation

**User Story**: As a developer, I need automated enforcement so architecture violations are caught before code review.

**Success Metric**: All 12 rules pass, zero false positives in clean build.

---

### FR-ARCH-003: Domain Port Definition (13 Ports)
**Requirement**: Define all input and output port interfaces in domain module (zero implementation)

**Acceptance Criteria**:
- ✅ 5 Input Ports created (use case contracts):
  - DocumentClassificationPort
  - DocumentExtractionPort
  - DocumentValidationPort
  - FraudDetectionPort
  - PipelineOrchestrationPort
- ✅ 8 Output Ports created (adapter contracts):
  - DocumentRepository, ExtractionRepository, TenantRepository (persistence)
  - OcrPort, LlmPort (AI/LLM)
  - StoragePort (cloud storage)
  - CachePort (in-memory cache)
  - DocumentEventPublisher, ValidationEventPublisher (events)
  - BankAccountValidatorPort, SiretValidatorPort, AddressValidatorPort (external APIs)
- ✅ All ports are interfaces (zero implementation code)
- ✅ Ports discoverable via ArchUnit Rule 10
- ✅ No framework imports in port definitions
- ✅ Port method signatures are minimal and focused (Interface Segregation Principle)

**User Story**: As an application developer, I need clear port contracts so I can implement adapters and use cases without coupling to infrastructure.

**Success Metric**: 13 port interfaces exist, compile cleanly, zero framework imports detected.

---

### FR-ARCH-004: Domain Isolation Verification
**Requirement**: Verify domain module is completely isolated from external frameworks

**Acceptance Criteria**:
- ✅ `./mvnw dependency:tree -pl docai-domain` shows ZERO framework dependencies (excluding test scope)
- ✅ Domain module pom.xml contains only:
  - JUnit 5 (test scope)
  - Mockito (test scope)
  - ArchUnit (test scope)
- ✅ No transitive framework imports visible in dependency tree
- ✅ Static analysis confirms zero Spring/MongoDB/Kafka/AWS imports

**User Story**: As a security architect, I need to ensure domain logic cannot accidentally depend on infrastructure, preventing vendor lock-in.

**Success Metric**: Zero external framework dependencies in production domain code.

---

### FR-ARCH-005: Code Quality Standards (SOLID)
**Requirement**: Enforce SOLID principles and code metrics via Checkstyle

**Acceptance Criteria**:
- ✅ Checkstyle configuration in parent pom.xml enforces:
  - Max 20 lines per method (BR-ARCH-005)
  - Max 4 parameters per method (BR-ARCH-006)
  - Max cyclomatic complexity = 10 (BR-ARCH-007)
- ✅ `./mvnw checkstyle:check → BUILD SUCCESS (0 violations)`
- ✅ Domain module passes all Checkstyle rules
- ✅ All modules pass all Checkstyle rules

**User Story**: As a code reviewer, I need automated style enforcement so quality is consistent across the team.

**Success Metric**: Zero Checkstyle violations in all modules.

---

### FR-ARCH-006: Test Coverage & Mutation Testing
**Requirement**: Establish minimum quality thresholds for domain layer testing

**Acceptance Criteria**:
- ✅ Domain module coverage ≥ 90% (JaCoCo - BR-ARCH-004)
- ✅ Domain module mutation score ≥ 85% (PIT - BR-ARCH-003)
- ✅ Global project coverage ≥ 80%
- ✅ Coverage gates enforced in CI pipeline
- ✅ PIT mutation report shows tests detect code mutations effectively

**User Story**: As a QA lead, I need proof that tests actually catch bugs (via mutation testing), not just code coverage.

**Success Metric**: Domain coverage ≥ 90%, mutation score ≥ 85%.

---

### FR-ARCH-007: Maven Profiles for Testing Modes
**Requirement**: Support multiple testing profiles (unit, integration, quality-gates)

**Acceptance Criteria**:
- ✅ `unit-tests` profile runs fast unit tests only (< 30 seconds)
- ✅ `integration-tests` profile runs integration tests with TestContainers
- ✅ `quality-gates` profile runs: ArchUnit + Checkstyle + JaCoCo + PIT
- ✅ `dev-profile` includes optimizations for development iteration
- ✅ Profiles are mutually exclusive and clearly documented

**User Story**: As a developer, I need fast feedback loops for unit tests while preserving full quality gates for CI.

**Success Metric**: Each profile runs independently, quality-gates profile passes all checks.

---

### FR-ARCH-008: Bounded Contexts Definition (DDD)
**Requirement**: Define 4 domain-driven design bounded contexts

**Acceptance Criteria**:
- ✅ Document context: Document entity, DocumentId, DocumentType, document ports
- ✅ Extraction context: Extraction entity, ExtractionResult, extraction ports
- ✅ Fraud context: FraudAnalysis entity, AnalysisId, fraud detection ports
- ✅ Pipeline context: orchestration logic, saga pattern, pipeline ports
- ✅ Clear boundaries between contexts documented in ARCHITECTURE_GUIDE.md
- ✅ Cross-context communication via events (Kafka), never direct object passing

**User Story**: As a domain expert, I need clear bounded contexts so business logic is organized by capability, not layer.

**Success Metric**: 4 contexts defined with clear boundaries, zero cross-context dependencies.

---

### FR-ARCH-009: ADR Enforcement Gates
**Requirement**: Integrate Architecture Decision Records (ADR) into module design requirements

**Acceptance Criteria**:
- ✅ ADR-002 (Kafka partition key = documentId) is explicit in port design gates
- ✅ ADR-010 (MongoDB EXPLAIN PLAN before merge) is explicit in persistence port design
- ✅ ADR-006 (JWKS cache 1h TTL) documented in security assumptions
- ✅ Other ADRs (ADR-001, ADR-003, ADR-004, ADR-007, ADR-008, ADR-009, ADR-011) documented as Phase 3+ gates
- ✅ ADR violations are treated as critical bugs (< 24h fix SLA)

**User Story**: As an architect, I need ADRs enforced in design so we don't regress on architectural decisions made earlier.

**Success Metric**: All 11 ADRs mapped to implementation phases, critical ADRs (002, 010) explicit in Module A.

---

### FR-ARCH-010: Documentation & Architecture Guide
**Requirement**: Create ARCHITECTURE_GUIDE.md for team reference

**Acceptance Criteria**:
- ✅ Hexagonal architecture diagram (ASCII or Mermaid)
- ✅ Port catalog listing all 13 ports with purpose and methods
- ✅ 12 ArchUnit rules explained with rationale
- ✅ Module dependency graph
- ✅ Package structure documented: `fr.docai.{domain,application,adapter-in-*,adapter-out-*,bootstrap}`
- ✅ Design pattern usage per module (Strategy, Registry, Anti-Corruption Layer, etc.)
- ✅ Quick-start validation script for new developers
- ✅ Versioning and change management process

**User Story**: As a new team member, I need clear architecture documentation so I can navigate the codebase effectively.

**Success Metric**: ARCHITECTURE_GUIDE.md is readable, complete, and referenced in PR reviews.

---

## Acceptance Criteria (Cross-Cutting)

### AC-001: Constitution Compliance
- All 7 DocAI Constitution principles must be respected
- No principle violations without team consensus
- ADR compliance verified in code review

### AC-002: Git & Version Control
- Conventional commits: `{type}({scope}): {subject}`
- Feature branches: `feature/{task-id}-{name}`
- PR size: one micro-task per PR
- All commits must include task references

### AC-003: Code Review Gates
- Zero SonarCloud violations (bugs, vulnerabilities)
- ArchUnit: 12/12 rules passing
- Checkstyle: 0 violations
- Coverage: Domain ≥ 90%, global ≥ 80%
- Mutation testing: Domain ≥ 85%

### AC-004: Traceability
- Each task (T001-T040) maps to one or more FR-ARCH-###
- Each FR-ARCH maps to implementation ÉTAPE(s)
- Each ÉTAPE has success criteria

---

## Non-Functional Requirements

### NFR-ARCH-001: Maintainability
- Method length ≤ 20 lines (Checkstyle)
- Method parameters ≤ 4 (Checkstyle)
- Cyclomatic complexity ≤ 10
- No circular dependencies
- Clear naming (no abbreviations)

### NFR-ARCH-002: Testability
- Domain tests run in < 1 second per file
- Zero Spring/Docker in unit tests
- Ports are mockable (Liskov Substitution Principle)
- TestContainers for integration tests only

### NFR-ARCH-003: Technology Independence
- Domain zero external framework imports (proven by ArchUnit)
- Swap implementations via ports (e.g., MongoDB → PostgreSQL)
- No vendor lock-in in domain layer

### NFR-ARCH-004: Scalability
- Supports 1M+ documents per tenant
- Kafka partition key = documentId (ADR-002) enables ordered processing
- Cache-aside pattern with TTL jitter (ADR-003) prevents thundering herd

### NFR-ARCH-005: Compliance
- Multi-tenancy enforced (tenantId in all queries)
- No hardcoded secrets
- PII fields encrypted at rest (ADR-005)
- Audit logs for security-relevant events

---

## Success Criteria

### S1: All 11 Maven modules compile cleanly
```bash
./mvnw clean compile
# Expected: BUILD SUCCESS (all 11 modules)
```

### S2: HexagonalArchitectureTest passes all 12 rules
```bash
./mvnw test -Dtest=HexagonalArchitectureTest
# Expected: 12/12 rules PASS ✅
```

### S3: Domain module has zero external framework imports
```bash
./mvnw dependency:tree -pl docai-domain | grep -E "spring|mongo|kafka|aws"
# Expected: (no output — zero matches)
```

### S4: Checkstyle validation passes
```bash
./mvnw checkstyle:check
# Expected: BUILD SUCCESS (0 violations)
```

### S5: Domain coverage and mutation thresholds met
```bash
./mvnw clean test -P quality-gates
# Expected:
# - JaCoCo: Domain ≥ 90% ✅
# - PIT: Domain ≥ 85% ✅
# - ArchUnit: 12/12 rules ✅
```

### S6: 13 port interfaces defined and discoverable
```bash
ls -1 docai-domain/src/main/java/fr/docai/domain/*/port/{in,out}/ | wc -l
# Expected: 13 (5 IN + 8 OUT)
```

### S7: ARCHITECTURE_GUIDE.md exists and is comprehensive
```bash
wc -w docs/ARCHITECTURE_GUIDE.md
# Expected: > 2000 words
```

---

## Dependencies & Blocking Conditions

### Hard Blocking Dependencies
```
Module 1.A (Maven setup, 11 modules)
  ↓ (REQUIRED FOR)
Module A (Architecture Hexagonale)
  ↓ (FOUNDATION FOR)
Module B (Design Patterns & ADR)
Module C (Persistence Standards)
Module 2-11 (Implementation phases)
```

### Architectural Constraints
- docai-domain MUST remain framework-free
- All 12 ArchUnit rules MUST pass in CI
- All ADRs MUST be respected (violations = critical bugs)
- No code review approval without green CI

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| ArchUnit rules too strict | Development friction | Document exceptions, approve carefully |
| Port design complexity | Decision paralysis | Keep ports fine-grained (single responsibility) |
| Coverage targets missed | Build failures | Write unit tests incrementally |
| Circular dependencies | Architectural debt | Weekly dependency:tree audit |
| ADR violations | Cascading issues | Code review focus on ADR compliance |

---

## Next Steps (Implementation Order)

1. ✅ Create spec.md (THIS DOCUMENT)
2. → Run `/speckit-staff-review-run` again to validate
3. → Fix all findings F002-F012 in plan.md and tasks.md
4. → Implement tasks T001-T040 following ÉTAPE 1-4 roadmap
5. → CI validates all 12 ArchUnit rules on every commit
6. → Create PR with all changes
7. → `/speckit.ship` to complete Module A

---

## Traceability Matrix

| FR-ARCH-### | ÉTAPE | Tasks | Implementation Phase |
|-------------|-------|-------|----------------------|
| FR-ARCH-001 | 1 | T001-T005 | Phase 1 (Maven structure) |
| FR-ARCH-002 | 2 | T023-T036 | Phase 2 (ArchUnit tests) |
| FR-ARCH-003 | 3 | T006-T022 | Phase 1-3 (Port definition) |
| FR-ARCH-004 | 3 | T037-T039 | Phase 3 (Verification) |
| FR-ARCH-005 | 4 | T037-T039 | Phase 4 (SOLID verification) |
| FR-ARCH-006 | 2,4 | T036, T037-T039 | Testing (quality gates) |
| FR-ARCH-007 | 1,2,4 | T001, T036, T037-T039 | All phases |
| FR-ARCH-008 | 1,3 | T001-T022 | Phase 1-3 (domain structure) |
| FR-ARCH-009 | 2,3 | T024-T035 | Phase 2-3 (ADR gates) |
| FR-ARCH-010 | 4 | T040 | Phase 4 (documentation) |

---

**Version**: 1.0.0 | **Status**: Ready for Implementation | **Date**: 2026-05-25
