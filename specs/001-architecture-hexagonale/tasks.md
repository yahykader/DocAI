# Tasks: Module A — Architecture Hexagonale

**Module**: Module A — Architecture & Principes (Référence Transversale)  
**Source**: MASTER SpecKit Partie 1 Module 1.A  
**Skill**: docai-architecture-adr  
**Total Tasks**: 30 (28 original + 2 additions from F010, F012 fixes)  
**Duration**: 2.5 days  
**Generated**: 2026-05-24  
**Last Updated**: 2026-05-25 (fixes applied)

---

## Overview

Module A establishes the hexagonal architecture foundation for DocAI with three core components:

1. **BLOC 1 — Architecture Hexagonale** (11 Maven modules with strict separation)
2. **BLOC 2 — SOLID Principles** (Applied to DocAI with validation patterns)
3. **BLOC 3 — Design Patterns Catalogue** (Per-module pattern implementations)

This task list focuses on **BLOC 1** (Architecture structure) and **foundational architecture validation**.

---

## Phase 1: Domain Module Structure & Port Definition

**Purpose**: Create pure domain model with zero external dependencies

### Domain Model Package Structure

- [ ] T001 Create domain entity base class in `docai-domain/src/main/java/fr/docai/domain/common/Entity.java` (id, createdAt fields)
- [ ] T002 [P] Create domain value objects for Tenant in `docai-domain/src/main/java/fr/docai/domain/tenant/TenantId.java`
- [ ] T003 [P] Create domain value objects for Document in `docai-domain/src/main/java/fr/docai/domain/document/DocumentId.java`
- [ ] T004 [P] Create domain value objects for Extraction in `docai-domain/src/main/java/fr/docai/domain/extraction/ExtractionId.java`
- [ ] T005 [P] Create domain value objects for Fraud in `docai-domain/src/main/java/fr/docai/domain/fraud/AnalysisId.java`

### Input Ports (Core → Adapters In)

- [ ] T006 [P] Create `DocumentClassificationPort` interface in `docai-domain/src/main/java/fr/docai/domain/document/port/in/DocumentClassificationPort.java`
  - Method: `DocumentType classify(Document doc)`
  - Zero dependency on Spring, framework classes
  - **Acceptance Criteria** (F006 Fix): 
    - [ ] Interface compiles cleanly
    - [ ] ArchUnit Rule 10 detects as port (interface)
    - [ ] No Spring imports visible: `grep -r "springframework" docai-domain/src/main/java/fr/docai/domain/document/port/in/ → empty`
    - [ ] Can be discovered by ArchUnit Rule 4
    - [ ] Methods have clear parameter types and return types

- [ ] T007 [P] Create `DocumentExtractionPort` interface in `docai-domain/src/main/java/fr/docai/domain/extraction/port/in/DocumentExtractionPort.java`
  - Method: `ExtractionResult extract(Document doc, DocumentType type)`
  - Zero dependency on Spring, framework classes
  - **Acceptance Criteria** (F006 Fix):
    - [ ] Interface compiles cleanly
    - [ ] ArchUnit Rule 10 detects as port (interface)
    - [ ] No Spring imports: `grep -r "springframework" docai-domain/src/main/java/fr/docai/domain/extraction/port/in/ → empty`
    - [ ] All method parameters have explicit types (not Object)
    - [ ] Return type is explicit (not void or Object)

- [ ] T008 [P] Create `DocumentValidationPort` interface in `docai-domain/src/main/java/fr/docai/domain/validation/port/in/DocumentValidationPort.java`
  - Method: `ValidationReport validate(Document doc, ExtractionResult result)`
  - Zero dependency on Spring, framework classes
  - **Acceptance Criteria** (F006 Fix):
    - [ ] Interface compiles cleanly
    - [ ] No framework imports detected
    - [ ] ArchUnit Rule 4 validates port location
    - [ ] Method signature matches specification
    - [ ] Multi-tenancy ready (can accept TenantId if needed)

- [ ] T009 [P] Create `FraudDetectionPort` interface in `docai-domain/src/main/java/fr/docai/domain/fraud/port/in/FraudDetectionPort.java`
  - Method: `FraudAnalysis analyze(Document doc, ExtractionResult result)`
  - Zero dependency on Spring, framework classes
  - **Acceptance Criteria** (F006 Fix):
    - [ ] Interface compiles cleanly
    - [ ] No Spring imports
    - [ ] ArchUnit Rule 10 detects as interface
    - [ ] Return type is FraudAnalysis (domain entity)
    - [ ] ADR-002 noted: fraud analyzer registry uses Strategy pattern (deferred to Phase 3)

- [ ] T010 [P] Create `PipelineOrchestrationPort` interface in `docai-domain/src/main/java/fr/docai/domain/pipeline/port/in/PipelineOrchestrationPort.java`
  - Method: `PipelineResult orchestrate(Document doc)`
  - Zero dependency on Spring, framework classes
  - **Acceptance Criteria** (F006 Fix):
    - [ ] Interface compiles cleanly
    - [ ] No framework imports visible
    - [ ] ArchUnit rules 4, 10 validate structure
    - [ ] Method signature matches specification
    - [ ] Return type is complete (includes status, results, errors)

### Output Ports (Domain → External Systems)

#### Persistence Ports

- [ ] T011 [P] Create `DocumentRepository` port in `docai-domain/src/main/java/fr/docai/domain/document/port/out/DocumentRepository.java`
  - Methods: `save(Document)`, `findById(DocumentId)`, `findByTenant(TenantId)`
  - No MongoDB, JPA, or Spring imports

- [ ] T012 [P] Create `ExtractionRepository` port in `docai-domain/src/main/java/fr/docai/domain/extraction/port/out/ExtractionRepository.java`
  - Methods: `save(Extraction)`, `findById(ExtractionId)`, `findLatestByDocument(DocumentId)`
  - No MongoDB, JPA, or Spring imports

- [ ] T013 [P] Create `TenantRepository` port in `docai-domain/src/main/java/fr/docai/domain/tenant/port/out/TenantRepository.java`
  - Methods: `save(Tenant)`, `findById(TenantId)`, `findByName(String)`
  - No MongoDB, JPA, or Spring imports

#### AI/LLM Integration Ports

- [ ] T014 [P] Create `OcrPort` interface in `docai-domain/src/main/java/fr/docai/domain/document/port/out/OcrPort.java`
  - Method: `OcrResult extractText(Document doc)`
  - Interchangeable implementations: TesseractOcrAdapter, CloudVisionAdapter

- [ ] T015 [P] Create `LlmPort` interface in `docai-domain/src/main/java/fr/docai/domain/extraction/port/out/LlmPort.java`
  - Method: `LlmResponse extract(String text, ExtractionSchema schema)`
  - Implementations: ClaudeAdapter, OpenAiAdapter

#### Storage Ports

- [ ] T016 [P] Create `StoragePort` interface in `docai-domain/src/main/java/fr/docai/domain/document/port/out/StoragePort.java`
  - Methods: `upload(DocumentId, byte[])`, `download(DocumentId)`, `delete(DocumentId)`
  - No AWS SDK, S3 client, or Spring imports

#### Cache Ports

- [ ] T017 [P] Create `CachePort` interface in `docai-domain/src/main/java/fr/docai/domain/cache/port/out/CachePort.java`
  - Methods: `put(String key, Object value, Duration ttl)`, `get(String key)`, `invalidate(String key)`
  - No Redis/Valkey client imports

#### Message Publish Ports

- [ ] T018 [P] Create `DocumentEventPublisher` port in `docai-domain/src/main/java/fr/docai/domain/document/port/out/DocumentEventPublisher.java`
  - Methods: `publishDocumentClassified(DocumentId)`, `publishExtractionCompleted(DocumentId)`
  - No Kafka client imports

- [ ] T019 [P] Create `ValidationEventPublisher` port in `docai-domain/src/main/java/fr/docai/domain/validation/port/out/ValidationEventPublisher.java`
  - Methods: `publishValidationCompleted(DocumentId)`, `publishValidationFailed(DocumentId)`
  - No Kafka client imports

#### External API Ports

- [ ] T020 [P] Create `BankAccountValidatorPort` port in `docai-domain/src/main/java/fr/docai/domain/validation/port/out/external/BankAccountValidatorPort.java`
  - Method: `ValidationResult validate(Iban, Bic)`
  - Anti-corruption layer pattern

- [ ] T021 [P] Create `SiretValidatorPort` port in `docai-domain/src/main/java/fr/docai/domain/validation/port/out/external/SiretValidatorPort.java`
  - Method: `SiretInfo lookup(String siret)` (INSEE API)
  - Anti-corruption layer pattern

- [ ] T022 [P] Create `AddressValidatorPort` port in `docai-domain/src/main/java/fr/docai/domain/validation/port/out/external/AddressValidatorPort.java`
  - Method: `AddressInfo validate(Address)` (BAN API)
  - Anti-corruption layer pattern

---

## Phase 2: ArchUnit Architecture Tests

**Purpose**: Enforce hexagonal architecture constraints at compile time

### ArchUnit Test Framework

- [ ] T023 Create `HexagonalArchitectureTest` class in `docai-bootstrap/src/test/java/fr/docai/bootstrap/HexagonalArchitectureTest.java`

### 12 ArchUnit Validation Rules

- [ ] T024 [P] Rule 1: Domain layer imports NEVER spring.* packages
  - Code: `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("org.springframework..*")`

- [ ] T025 [P] Rule 2: Domain layer imports NEVER mongodb packages
  - Code: `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("com.mongodb..*")`

- [ ] T026 [P] Rule 3: Domain layer imports NEVER kafka packages
  - Code: `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("org.apache.kafka..*")`

- [ ] T027 [P] Rule 4: Domain layer imports NEVER aws-sdk packages
  - Code: `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("software.amazon..*")`

- [ ] T028 [P] Rule 5: Domain layer imports NEVER external HTTP client packages
  - Code: `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("com.fasterxml.jackson..*", "org.apache.http..*")`

- [ ] T029 [P] Rule 6: Application layer depends ONLY on domain ports (not adapters)
  - Code: `classes().that().resideInPackage("..application..").should().onlyDependOnClassesThat().resideInPackages("..domain..", "java..*")`

- [ ] T030 [P] Rule 7: Input adapters (adapter-in-*) depend on application + domain
  - Code: `classes().that().resideInPackage("..adapter.in..*").should().onlyDependOnClassesThat().resideInPackages("..application..", "..domain..", "org.springframework..*", "java..*")`

- [ ] T031 [P] Rule 8: Output adapters (adapter-out-*) implement domain ports only
  - Code: `classes().that().implement(Port.class).and().resideInPackage("..adapter.out..*").should().resideInPackage("..adapter.out..*")`

- [ ] T032 [P] Rule 9: No circular dependencies between modules
  - Code: `slices().matching("..fr.docai.(*)..*").should().notDependOnEachOther()`

- [ ] T033 [P] Rule 10: Ports are interfaces ONLY (no implementation in domain)
  - Code: `classes().that().resideInPackage("..port..").should().beInterfaces()`

- [ ] T034 [P] Rule 11: DTOs in application layer NEVER referenced in domain
  - Code: `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackage("..application..dto..*")`

- [ ] T035 [P] Rule 12: Bootstrap module assembles all dependencies (no other module imports Bootstrap)
  - Code: `classes().that().resideInPackage("..bootstrap..").should().onlyBeDependedOnByClassesThat().resideInPackage("..bootstrap..*")`

### ArchUnit Test Execution

- [ ] T036 Run complete ArchUnit test suite: `./mvnw test -Dtest=HexagonalArchitectureTest`
  - All 12 rules MUST pass
  - Verify in CI on every commit

---

## Phase 3: Dependency Verification & Validation

**Purpose**: Ensure domain module is truly isolated

### Zero External Dependencies Verification

- [ ] T037 Verify domain module pom.xml has ONLY:
  - junit-jupiter (test scope)
  - mockito (test scope)
  - No Spring, MongoDB, Kafka, AWS, HTTP client dependencies

- [ ] T038 Run dependency tree analysis:
  - `./mvnw dependency:tree -pl docai-domain`
  - Verify output contains NO org.springframework, com.mongodb, org.apache.kafka, software.amazon references

- [ ] T039 Run Checkstyle on domain layer:
  - `./mvnw checkstyle:check -pl docai-domain`
  - Verify max method length: 20 lines
  - Verify max parameters: 4
  - Verify cyclomatic complexity: ≤ 10

---

## Phase 4: Documentation & Reference

**Purpose**: Document architecture patterns for team reference

- [ ] T040 Create `ARCHITECTURE_GUIDE.md` documenting (F003 Fix — Detailed Requirements):
  - **Section 1: Architecture Overview** (500-700 words)
    - Hexagonal architecture diagram (ASCII or Mermaid)
    - Justify why hexagonal chosen over layered, monolithic, microservices
    - Show domain → ports → adapters flow with examples
  - **Section 2: Module Structure** (300-400 words)
    - 11 Maven modules with dependency graph
    - Package naming: `fr.docai.{domain,application,adapter-in-*,adapter-out-*,bootstrap}`
    - Each module's responsibility and scope
  - **Section 3: Port Catalog** (600-800 words)
    - All 13 ports listed with purpose, methods, and design rationale
    - 5 Input Ports: table with interface name, primary method, usage example
    - 8 Output Ports: grouped by category (persistence, AI/LLM, storage, caching, events, external APIs)
    - Indicate which ADRs apply to each port (e.g., DocumentEventPublisher → ADR-002)
  - **Section 4: 12 ArchUnit Rules Explained** (700-900 words)
    - Each rule: what it prevents, why it matters, code example showing violation
    - Link each rule to constitution principle
    - Document exceptions procedure (ADR amendments)
  - **Section 5: Design Patterns** (500-600 words)
    - Patterns used in each module: Strategy (classification), Registry (fraud analyzers), Anti-Corruption Layer (external APIs), CQRS (read model), Cache-Aside
    - Why each pattern chosen, tradeoffs
  - **Section 6: Bounded Contexts (DDD)** (400-500 words)
    - 4 contexts: Document, Extraction, Fraud, Pipeline
    - Entity diagrams per context
    - Cross-context communication via events (Kafka, ADR-002)
  - **Section 7: Quick-Start for Developers** (300-400 words)
    - How to add a new domain entity
    - How to define a new port
    - How to implement a new adapter
    - Common ArchUnit rule violations and how to fix
  - **Section 8: Versioning & Governance** (200-300 words)
    - When/how to amend this guide
    - ADR amendment process
    - Constitution enforcement
  - **Appendix: Quick Reference**
    - 13-port quick lookup table
    - 12 ArchUnit rules quick reference
    - Maven commands reference
  
  **Acceptance Criteria** (F003 Fix):
  - [ ] Document exists at `/docs/ARCHITECTURE_GUIDE.md` or similar (committed to repo)
  - [ ] Word count ≥ 2000 words (sections 1-8, excluding appendix)
  - [ ] All 13 ports documented with examples
  - [ ] All 12 ArchUnit rules explained with violations shown
  - [ ] ASCII or Mermaid diagram included (at minimum, hexagonal diagram)
  - [ ] Links to constitution and ADRs throughout
  - [ ] No outdated references (e.g., "ÉTAPE 1 TODO" — should say "ÉTAPE 1 COMPLETE")
  - [ ] Reviewed by tech lead before merge

- [ ] T041 Create `GIT_WORKFLOW.md` documenting version control strategy (F010 Fix):
  - Branch naming convention: `feature/{task-id}-{short-name}`
  - Commit message format: `{type}({scope}): {subject} [ref #{task-id}]`
  - PR size guidelines: one task per PR, max 500 LOC
  - Merge strategy: Squash + rebase
  - Tag strategy: `v00{N}-{phase-name}` for each ÉTAPE completion
  
  **Acceptance Criteria**:
  - [ ] Document exists at `/docs/GIT_WORKFLOW.md`
  - [ ] All conventions match this spec's plan.md (Git Workflow section)
  - [ ] Example commits and PRs shown
  - [ ] Tag strategy explained with examples
  - [ ] Integrated into developer onboarding checklist

- [ ] T042 Create backward compatibility test suite for port interfaces (F012 Fix):
  - Test that existing port mocks still compile against new port interfaces
  - Test that port changes don't break adapter stubs from Phase 3+
  - Document rollback procedure if port interface must change mid-implementation
  
  **Acceptance Criteria**:
  - [ ] Test class created: `docai-domain/src/test/java/.../PortBackwardCompatibilityTest.java`
  - [ ] Each port has compatibility test (13 tests total)
  - [ ] Compatibility tests pass before any port modification
  - [ ] Documentation: "How to safely evolve port interfaces" in ARCHITECTURE_GUIDE.md
  - [ ] Rollback procedure documented (revert to previous version, maintain adapter compatibility)

---

## Dependencies & Execution Order

### Phases
- **Phase 1 → Phase 2**: All domain ports must exist before ArchUnit tests run
- **Phase 2 → Phase 3**: ArchUnit tests provide confidence for dependency verification
- **Phase 3 → Phase 4**: Documentation captures validated architecture

### Within Phase 1
- T001: Create base Entity first (blocking)
- T002-T005: Value objects can run in parallel [P]
- T006-T022: Port definitions can run in parallel [P] (all independent)

### Within Phase 2
- T023: Create test class first (blocking)
- T024-T035: Individual ArchUnit rules can run in parallel [P]
- T036: Test execution runs all rules together

### Critical Path
```
T001 (Entity base)
  → T006-T022 (Ports parallel)
    → T023 (Test class)
      → T024-T035 (Rules parallel)
        → T036 (Test execution)
          → T037-T039 (Verification parallel)
            → T040 (Documentation)
```

---

## Independent Test Criteria

### After Phase 1 (Domain + Ports)
- ✅ Domain module compiles: `./mvnw clean compile -pl docai-domain`
- ✅ No Spring/MongoDB/Kafka imports in domain code
- ✅ All 13 port interfaces exist and have correct signatures

### After Phase 2 (ArchUnit Tests)
- ✅ HexagonalArchitectureTest runs without errors
- ✅ All 12 ArchUnit rules pass
- ✅ Test integrates into CI pipeline

### After Phase 3 (Verification)
- ✅ Dependency tree shows zero external framework imports in domain
- ✅ Checkstyle validation passes all metrics
- ✅ No circular dependencies detected

### After Phase 4 (Documentation & Git Workflow)
- ✅ ARCHITECTURE_GUIDE.md exists (≥ 2000 words, all 13 ports documented)
- ✅ Port diagram shows all 13 ports and their relationships
- ✅ Package structure explained with rationale
- ✅ GIT_WORKFLOW.md exists with branch naming, commit messages, PR strategy
- ✅ Backward compatibility test suite (T042) passes all 13 port tests
- ✅ Rollback procedure documented in ARCHITECTURE_GUIDE.md

---

## Implementation Notes

### Port Naming Convention
- **Input Ports** (use cases): `{UseCaseName}Port` (e.g., `DocumentClassificationPort`)
- **Output Ports** (repositories): `{EntityName}Repository` (e.g., `DocumentRepository`)
- **Output Ports** (external): `{ServiceName}Port` (e.g., `OcrPort`, `LlmPort`)

### File Organization
```
docai-domain/src/main/java/fr/docai/domain/
  ├── common/
  │   └── Entity.java
  ├── document/
  │   ├── DocumentId.java
  │   ├── Document.java
  │   └── port/
  │       ├── in/DocumentClassificationPort.java
  │       └── out/DocumentRepository.java, OcrPort.java, StoragePort.java
  ├── extraction/
  │   ├── ExtractionId.java
  │   ├── Extraction.java
  │   └── port/
  │       ├── in/DocumentExtractionPort.java
  │       └── out/ExtractionRepository.java, LlmPort.java
  ├── tenant/
  │   ├── TenantId.java
  │   └── port/out/TenantRepository.java
  ├── validation/
  │   ├── port/
  │   │   ├── in/DocumentValidationPort.java
  │   │   └── out/BankAccountValidatorPort.java, SiretValidatorPort.java, AddressValidatorPort.java
  ├── fraud/
  │   ├── AnalysisId.java
  │   └── port/
  │       ├── in/FraudDetectionPort.java
  └── pipeline/
      └── port/in/PipelineOrchestrationPort.java
```

### Domain Module pom.xml Template
```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>fr.docai</groupId>
  <artifactId>docai-domain</artifactId>
  <version>${project.version}</version>
  <name>DocAI Domain</name>

  <dependencies>
    <!-- Test only -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

---

## Success Metrics

| Metric | Target | Validation |
|--------|--------|-----------|
| Domain module external dependencies | 0 | `./mvnw dependency:tree -pl docai-domain \| grep -v "test"` |
| ArchUnit rules passing | 12/12 | `./mvnw test -Dtest=HexagonalArchitectureTest` |
| Domain module method length | ≤ 20 lines | Checkstyle report |
| Domain module parameters per method | ≤ 4 | Checkstyle report |
| Domain module cyclomatic complexity | ≤ 10 | Checkstyle report |
| Circular dependencies | 0 | `./mvnw dependency:tree` output |
| ARCHITECTURE_GUIDE.md | ≥ 2000 words, all 13 ports documented | Documentation review |
| GIT_WORKFLOW.md | Complete branching, commit, PR strategy | Documentation review |
| Backward compatibility tests | 13/13 port tests passing | PortBackwardCompatibilityTest execution |
| Port interface stability | 0 breaking changes mid-implementation | Code review gate |

---

## Integration with Overall Project

This Module A architecture serves as the **foundation** for all subsequent modules:

- **Module 2** (Commons): Implements filters, mappers, validators using ports from Module A
- **Module 3-5** (Foundations, Pipeline, Product): All adapters implement ports defined here
- **CI/CD**: ArchUnit tests run on every commit as quality gate

---

**Last Updated**: 2026-05-25 (F010, F012 fixes added)  
**Status**: Ready for Implementation  
**Estimated Duration**: 2.5 days  
**Critical Path**: 16 tasks (T001 → T023 → T036 → T040 → T041 → T042)
