# Implementation Plan: Module A — Architecture Hexagonale

**Module**: Module A — Architecture & Principes (Référence Transversale)  
**Skill**: docai-architecture-adr  
**Plan Created**: 2026-05-24  
**Duration**: 2 days  
**Status**: Ready for implementation

---

## ⚠️ Prerequisites

**CRITICAL GATE**: Module 1.A (Setup Projet) must be completed FIRST.

Requirements from Module 1.A:
- ✅ Parent `pom.xml` with all 11 modules declared
- ✅ 11 module directories created with individual `pom.xml` files
- ✅ Java 21 + Spring Boot 4.0.x configured in parent POM
- ✅ Maven profiles configured (unit-tests, integration-tests, quality-gates)
- ✅ `./mvnw clean compile` → BUILD SUCCESS (all 11 modules)
- ✅ Package root: `fr.docai.*`

**Validation**: Run `./mvnw clean compile` and verify no compile errors before proceeding.

---

## Technical Context

### Architecture Foundation

**Hexagonal Architecture Model** (Ports & Adapters Pattern):
```
┌─────────────────────────────────────────────┐
│         DOMAIN LAYER (Pure Business)        │
│  - Zero Spring imports                      │
│  - Zero MongoDB imports                     │
│  - Zero Kafka imports                       │
│  - Zero AWS SDK imports                     │
├─────────────────────────────────────────────┤
│ APPLICATION LAYER (Use Cases & Services)    │
│  - Depends ONLY on Domain ports             │
│  - No direct framework imports              │
├─────────────────────────────────────────────┤
│ ADAPTER LAYERS (Technology-Specific)        │
│  - Inbound: REST, Kafka consumers           │
│  - Outbound: MongoDB, S3, LLM, Events       │
└─────────────────────────────────────────────┘
```

### Module Dependencies

**Module Structure** (11 Maven modules):
```
docai-domain              ← ZERO external dependencies (only JUnit 5 for tests)
docai-application         ← depends on: domain
docai-adapter-in-rest     ← depends on: application, domain, Spring Web
docai-adapter-in-kafka    ← depends on: application, domain, Kafka
docai-adapter-out-mongodb ← depends on: application, domain, MongoDB
docai-adapter-out-kafka   ← depends on: application, domain, Kafka
docai-adapter-out-valkey  ← depends on: application, domain, Redis/Valkey
docai-adapter-out-ai      ← depends on: application, domain, Claude API
docai-adapter-out-storage ← depends on: application, domain, AWS S3
docai-adapter-out-external← depends on: application, domain, HTTP clients
docai-bootstrap           ← assembles all modules, Spring Boot entry point
```

### Key Design Decisions

| Decision | Rationale | Alternatives Considered |
|----------|-----------|-------------------------|
| **Hexagonal over Layered** | Enables port-driven testing, technology independence | Layered (harder to swap implementations) |
| **Domain zero dependencies** | Ensures business logic pure, testable, reusable | Mix framework + domain (violates separation) |
| **Interface-first ports** | Defines contracts before implementation | Implementation-first (brittle, tight coupling) |
| **DDD Bounded Contexts** | 4 contexts: Document, Extraction, Fraud, Pipeline | Monolithic domain (harder to understand) |

### Quality Gates (BR-ARCH-*)

| Gate | Requirement | Validation |
|------|-------------|-----------|
| **BR-ARCH-001** | 12 ArchUnit rules active in CI | `./mvnw test -Dtest=HexagonalArchitectureTest` |
| **BR-ARCH-002** | Domain never imports framework | ArchUnit Rule 1-5 (must all pass) |
| **BR-ARCH-003** | Domain mutation score ≥ 85% | PIT mutation report |
| **BR-ARCH-004** | Domain coverage ≥ 90% | JaCoCo coverage report |
| **BR-ARCH-005** | Checkstyle: max 20 lines/method | `./mvnw checkstyle:check` |
| **BR-ARCH-006** | Checkstyle: max 4 parameters | `./mvnw checkstyle:check` |
| **BR-ARCH-007** | Checkstyle: cyclomatic complexity ≤ 10 | `./mvnw checkstyle:check` |

---

## Constitution Check

### Pre-Implementation Compliance

| Principle | Requirement | Status | Justification |
|-----------|-------------|--------|---------------|
| **Hexagonal Architecture** | Domain module zero Spring/MongoDB/Kafka/AWS imports | ✅ By Design | Module A enforces via ArchUnit |
| **SOLID Principles** | Single Responsibility (1 port = 1 responsibility) | ✅ By Design | Ports define minimal contracts |
| **DDD Bounded Contexts** | 4 contexts with clear boundaries | ✅ By Design | Document, Extraction, Fraud, Pipeline |
| **Port Isolation** | Ports ⊂ Domain, Adapters ⊂ Layers | ✅ By Design | ArchUnit rules verify |
| **Code Quality** | Checkstyle + ArchUnit + PIT in CI | ✅ Configurable | Gate configuration in parent POM |
| **Test Coverage** | Domain ≥ 90%, global ≥ 80% | ✅ Enforceable | JaCoCo + PIT in Maven profiles |

### Gate Assessment

**Gate 1 — Module Dependencies**: ✅ PASS
- All 11 modules have correct POMs
- docai-domain has ONLY JUnit 5 + Mockito (test scope)
- No transitive framework imports in domain

**Gate 2 — ArchUnit Rules Ready**: ✅ PASS (After ÉTAPE 2)
- 12 rules defined and tested
- Rules integrated into CI pipeline
- All rules passing before ÉTAPE 3

**Gate 3 — Port Structure Valid**: ✅ PASS (After ÉTAPE 3)
- All 13 input/output ports defined
- No implementation details in ports (interfaces only)
- ArchUnit rules 4, 5, 10 verify this

**Gate 4 — SOLID Verification**: ✅ PASS (After ÉTAPE 4)
- Checkstyle enforces 20-line method limit
- All code follows SOLID principles
- 0 violations in domain module

### Architectural Drift Check

**Pre-Drift Factors**:
- No existing codebase (greenfield)
- Spec requirements traceable to Constitution
- ADR-002, ADR-010 constraints documented

**Risk**: LOW — Architecture defined upfront before any implementation

---

## Phase 0: Research & Clarification Resolution

**Status**: ✅ COMPLETE

All architectural decisions pre-defined in BLOC 1, BLOC 2, BLOC 3:

### Resolved Questions

| Q | Decision | Source |
|---|----------|--------|
| Domain dependencies? | ZERO external frameworks | BLOC 1 requirement |
| Port strategy? | 13 ports (5 IN, 8 OUT) | BLOC 1 + BLOC 3 catalog |
| ArchUnit rules? | 12 rules enforcing hexagonal | BR-ARCH-001/002 |
| Test coverage? | Domain ≥ 90%, global ≥ 80% | BR-ARCH-004 |
| Mutation testing? | PIT ≥ 85% domain score | BR-ARCH-003 |
| Code metrics? | 20 lines/method, 4 params, CC ≤ 10 | Checkstyle config |

### No Research Tasks Needed

All architectural choices made at spec time. Proceed directly to ÉTAPE 1.

---

## Implementation Roadmap: 4 ÉTAPES

### ÉTAPE 1 — Structure Maven Hexagonale (0.5 days)

**Goal**: Verify 11 modules compile with zero errors, docai-domain has zero framework imports

**Status**: ✅ Inherited from Module 1.A

**Verification**:
```bash
./mvnw clean compile
# Expected: BUILD SUCCESS
# All 11 modules compile
```

**Deliverables** (from Module 1.A):
1. Parent `pom.xml` with module declarations
2. 11 module `pom.xml` files with correct package roots (`fr.docai.*`)
3. `docai-domain/pom.xml` with ONLY test dependencies (JUnit 5, Mockito)
4. Maven profiles: `unit-tests`, `integration-tests`, `quality-gates`, `dev-profile`

**Acceptance Criteria**:
- ✅ `./mvnw clean compile → BUILD SUCCESS`
- ✅ `mvnw dependency:tree -pl docai-domain` shows no framework dependencies
- ✅ All 11 module directories exist with src/ structure

---

### ÉTAPE 2 — HexagonalArchitectureTest (12 ArchUnit Règles) (0.75 days)

**Goal**: Create ArchUnit test class with all 12 rules, all passing

**Duration**: 4-5 hours (rule implementation + testing)

**ADR Enforcement** (F005 Fix): This ÉTAPE enforces critical ADR constraints:
- **ADR-002**: Kafka partition key requirement documented in architecture (explicit in port design gates for ÉTAPE 3)
- **ADR-010**: MongoDB EXPLAIN PLAN requirement explicit in persistence port gates for ÉTAPE 3

#### Rule Implementation Details

**Test Class**: `docai-bootstrap/src/test/java/fr/docai/bootstrap/HexagonalArchitectureTest.java`

```java
@AnalyzeClasses(packages = "fr.docai")
class HexagonalArchitectureTest {
    
    // Rule 1: Domain never imports Spring
    @ArchTest
    static final ArchRule rule1_domain_no_spring = ...
    
    // Rule 2: Domain never imports MongoDB
    @ArchTest
    static final ArchRule rule2_domain_no_mongodb = ...
    
    // ... (Rules 3-12)
}
```

#### 12 Rules Specification (Detailed)

| # | Rule | Forbidden Packages | ArchUnit Code | Rationale |
|---|------|-------------------|----------------|-----------|
| 1 | Domain ✗ Spring | `org.springframework.**` | `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("org.springframework..**")` | Domain must be framework-agnostic |
| 2 | Domain ✗ MongoDB | `com.mongodb.**`, `org.springframework.data.mongodb.**` | `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("com.mongodb..**", "org.springframework.data.mongodb..**")` | No persistence in domain |
| 3 | Domain ✗ Kafka | `org.apache.kafka.**`, `org.springframework.kafka.**` | `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("org.apache.kafka..**", "org.springframework.kafka..**")` | No messaging in domain |
| 4 | Domain ✗ AWS SDK | `software.amazon.**` | `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("software.amazon..**")` | No cloud SDK in domain |
| 5 | Domain ✗ HTTP clients | `com.fasterxml.jackson.**`, `org.apache.http.**`, `com.squareup.okhttp.**` | `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackages("com.fasterxml.jackson..**", "org.apache.http..**", "com.squareup.okhttp..**")` | No external HTTP calls in domain |
| 6 | Application → domain only | Domain packages, `java.**`, `jakarta.validation.**` | `classes().that().resideInPackage("..application..").should().onlyDependOnClassesThat().resideInPackages("..domain..", "java..**", "jakarta.validation..**")` | Use cases depend on domain contracts only |
| 7 | Adapters-IN on app+domain | Application, Domain, `org.springframework.web.**` | `classes().that().resideInPackage("..adapter.in..*").should().onlyDependOnClassesThat().resideInPackages("..application..", "..domain..", "org.springframework.web..**", "java..**")` | Inbound adapters can use Spring Web |
| 8 | Adapters-OUT implement ports | Output adapter packages only | `classes().that().implement(RepositoryPort.class).and().resideInPackage("..adapter.out..*").should().resideInPackage("..adapter.out..*")` | Adapter implementations isolated |
| 9 | No circular dependencies | All modules | `slices().matching("..fr.docai.(*)..*").should().notDependOnEachOther()` | Prevents architectural cycles |
| 10 | Ports are interfaces | Port packages | `classes().that().resideInPackage("..port..").should().beInterfaces()` | Ports define contracts only |
| 11 | DTOs ✗ domain | Application DTO packages | `classes().that().resideInPackage("..domain..").should().notDependOnClassesThat().resideInPackage("..application..dto..*")` | Domain never imports presentation models |
| 12 | Bootstrap isolated | Bootstrap packages | `classes().that().resideInPackage("..bootstrap..").should().onlyBeDependedOnByClassesThat().resideInPackage("..bootstrap..*")` | Bootstrap is entry point only |

#### Verification

```bash
./mvnw test -Dtest=HexagonalArchitectureTest
# Expected: 12/12 rules PASS ✅

./mvnw clean package -DskipTests
# Verify no compilation errors
# Check ArchUnit rules in CI
```

**Coverage Requirements** (BR-ARCH-004):
```bash
./mvnw clean test -P quality-gates
# Domain coverage ≥ 90% (JaCoCo)
# Mutation score ≥ 85% (PIT)
```

**Acceptance Criteria**:
- ✅ HexagonalArchitectureTest class created
- ✅ All 12 rules implemented and passing
- ✅ Domain coverage ≥ 90%
- ✅ Domain mutation score ≥ 85%
- ✅ Test runs in CI pipeline

---

### ÉTAPE 3 — Design Patterns de Base (Structure Vides) (0.75 days)

**Goal**: Create port interfaces and domain structure matching ArchUnit rules

**Duration**: 4-5 hours (port definition + validation)

#### Port Directories & Files

```
docai-domain/src/main/java/fr/docai/domain/
├── port/
│   ├── in/
│   │   ├── DocumentClassificationPort.java
│   │   ├── DocumentExtractionPort.java
│   │   ├── DocumentValidationPort.java
│   │   ├── FraudDetectionPort.java
│   │   └── PipelineOrchestrationPort.java
│   └── out/
│       ├── DocumentRepository.java
│       ├── ExtractionRepository.java
│       ├── TenantRepository.java
│       ├── OcrPort.java
│       ├── LlmPort.java
│       ├── StoragePort.java
│       ├── CachePort.java
│       ├── DocumentEventPublisher.java
│       ├── ValidationEventPublisher.java
│       ├── BankAccountValidatorPort.java
│       ├── SiretValidatorPort.java
│       └── AddressValidatorPort.java
├── event/
│   ├── DocumentClassifiedEvent.java
│   ├── ExtractionCompletedEvent.java
│   ├── ValidationCompletedEvent.java
│   ├── FraudAnalysisCompletedEvent.java
│   └── DomainEvent.java
└── exception/
    ├── DomainException.java
    ├── DocumentNotFoundException.java
    ├── ValidationException.java
    └── FraudAnalysisException.java
```

#### Port Definitions (Skeleton with Full Signatures)

**Example Input Port** (F009 Fix — Full Signatures):
```java
// fr.docai.domain.document.port.in.DocumentClassificationPort
public interface DocumentClassificationPort {
    /**
     * Classify a document based on its content and metadata.
     * @param document the Document entity to classify
     * @return DocumentType (invoice, receipt, contract, etc.)
     */
    DocumentType classify(Document document);
}
```

**Example Output Port** (F009 Fix — Full Signatures):
```java
// fr.docai.domain.document.port.out.DocumentRepository
public interface DocumentRepository {
    /**
     * Persist a document to durable storage.
     * @param tenantId tenant identifier for multi-tenancy
     * @param document the Document entity to save
     */
    void save(TenantId tenantId, Document document);
    
    /**
     * Retrieve a document by its identifier.
     * @param tenantId tenant identifier
     * @param documentId document identifier
     * @return Optional<Document> if found, empty otherwise
     */
    Optional<Document> findById(TenantId tenantId, DocumentId documentId);
    
    /**
     * Retrieve all documents for a tenant.
     * @param tenantId tenant identifier
     * @return List of Document entities for tenant (never null, empty list if none)
     */
    List<Document> findByTenant(TenantId tenantId);
}
```

#### Domain Events Base

```java
// fr.docai.domain.event.DomainEvent
public abstract class DomainEvent {
    private final String eventId = UUID.randomUUID().toString();
    private final LocalDateTime occurredAt = LocalDateTime.now();
    
    public String getEventId() { return eventId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
```

#### Domain Exceptions Base

```java
// fr.docai.domain.exception.DomainException
public abstract class DomainException extends RuntimeException {
    public DomainException(String message) { super(message); }
    public DomainException(String message, Throwable cause) { super(message, cause); }
}
```

#### Verification

```bash
# Verify ArchUnit rules 4 & 5 pass (ports isolation)
./mvnw test -Dtest=HexagonalArchitectureTest

# Verify structure
ls -R docai-domain/src/main/java/fr/docai/domain/port/
ls -R docai-domain/src/main/java/fr/docai/domain/event/
ls -R docai-domain/src/main/java/fr/docai/domain/exception/
```

**Acceptance Criteria**:
- ✅ All 13 port interfaces created (5 IN + 8 OUT)
- ✅ Port files have ZERO implementation (interfaces only)
- ✅ DomainEvent base class created
- ✅ DomainException base class created
- ✅ ArchUnit rules 4, 5, 10 verify structure
- ✅ No framework imports in ports

---

### ÉTAPE 4 — SOLID Vérification dans Checkstyle (0.25 days)

**Goal**: Verify Checkstyle configuration enforces code quality metrics

**Duration**: 1-2 hours (configuration verification + domain validation)

#### Checkstyle Configuration

**File**: `checkstyle.xml` (parent pom.xml location)

**Key Rules** (BR-ARCH-005, BR-ARCH-006, BR-ARCH-007):

```xml
<module name="MethodLength">
  <property name="max" value="20"/>
  <property name="severity" value="error"/>
</module>

<module name="ParameterNumber">
  <property name="max" value="4"/>
  <property name="severity" value="error"/>
</module>

<module name="CyclomaticComplexity">
  <property name="max" value="10"/>
  <property name="severity" value="error"/>
</module>
```

#### Verification

```bash
# Check domain module for violations
./mvnw checkstyle:check -pl docai-domain
# Expected: BUILD SUCCESS (0 violations)

# Check all modules
./mvnw checkstyle:check
# Expected: BUILD SUCCESS (0 violations)

# Run full quality gates profile
./mvnw clean test -P quality-gates
# Expected: ArchUnit + Checkstyle + PIT all PASS
```

#### Code Quality Metrics

| Metric | Target | Tool |
|--------|--------|------|
| Max method length | 20 lines | Checkstyle |
| Max parameters | 4 | Checkstyle |
| Cyclomatic complexity | ≤ 10 | Checkstyle |
| Domain coverage | ≥ 90% | JaCoCo |
| Domain mutation score | ≥ 85% | PIT |
| Domain ArchUnit rules | 12/12 | ArchUnit |

**Acceptance Criteria**:
- ✅ Checkstyle configuration in parent pom.xml
- ✅ `./mvnw checkstyle:check → BUILD SUCCESS`
- ✅ 0 violations in domain module
- ✅ 0 violations in all modules
- ✅ Checkstyle integrated into CI pipeline

---

## Maven Profile Configuration

### Profiles to Support ÉTAPE Verification

```xml
<profiles>
  <profile>
    <id>unit-tests</id>
    <build>
      <plugins>
        <!-- Only unit tests, no docker/integration tests -->
      </plugins>
    </build>
  </profile>

  <profile>
    <id>integration-tests</id>
    <build>
      <plugins>
        <!-- Integration tests requiring docker -->
      </plugins>
    </build>
  </profile>

  <profile>
    <id>quality-gates</id>
    <build>
      <plugins>
        <!-- ArchUnit + Checkstyle + PIT mutation -->
      </plugins>
    </build>
  </profile>

  <profile>
    <id>dev-profile</id>
    <!-- Development optimizations -->
  </profile>
</profiles>
```

### Running Quality Gates

```bash
# Run all quality checks
./mvnw clean test -P quality-gates

# Expected output:
# ✅ ArchUnit: 12/12 rules pass
# ✅ Checkstyle: 0 violations
# ✅ JaCoCo: Coverage ≥ 90% (domain)
# ✅ PIT: Mutation ≥ 85% (domain)
```

---

## Phase 1: Design & Contracts

### 1.1 Data Model

**Entities** (from BLOC 3 patterns, to be implemented in Phase 3+):

| Entity | Purpose | Module |
|--------|---------|--------|
| **Document** | Core domain object | docai-domain/document/ |
| **Extraction** | Result of classification + extraction | docai-domain/extraction/ |
| **Tenant** | Customer organization boundary | docai-domain/tenant/ |
| **ValidationReport** | Result of validation checks | docai-domain/validation/ |
| **FraudAnalysis** | Result of fraud detection | docai-domain/fraud/ |

**Relationships**:
- Document → Tenant (N:1)
- Document → Extraction (1:1)
- Document → ValidationReport (1:1)
- Document → FraudAnalysis (1:1)

### 1.2 Interface Contracts

Since Module A is architecture-only (no public APIs yet), contracts are deferred to Module 3 (REST adapters).

**Internal Contracts** (Port interfaces) — see ÉTAPE 3 above:
- 5 Input Ports (use case contracts)
- 8 Output Ports (adapter contracts)

### 1.3 Quickstart Validation

**After Module A completion**, verify:

```bash
# Clone fresh repo
git clone <repo> docai-fresh
cd docai-fresh

# Step 1: Build all modules
./mvnw clean compile
# Expected: BUILD SUCCESS

# Step 2: Run ArchUnit tests
./mvnw test -Dtest=HexagonalArchitectureTest
# Expected: 12/12 rules PASS ✅

# Step 3: Verify quality gates
./mvnw checkstyle:check
# Expected: BUILD SUCCESS (0 violations)

# Step 4: Verify domain coverage
./mvnw clean test -P quality-gates
# Expected: Coverage ≥ 90%, Mutation ≥ 85%
```

---

## Implementation Strategy

### MVP Scope (MINIMUM VIABLE PRODUCT)

**Complete all 4 ÉTAPES to have foundational architecture ready**:

1. ✅ Maven modules compile cleanly
2. ✅ HexagonalArchitectureTest with 12 passing rules
3. ✅ Port interfaces defined (structures, no implementation)
4. ✅ Checkstyle enforces code quality

**After Module A**: Foundation ready for Phase 2 (Commons implementation)

### Incremental Delivery

```
ÉTAPE 1 (0.5d) ────────┐
                        └─→ ÉTAPE 2 (0.75d) ────────┐
                                                      └─→ ÉTAPE 3 (0.75d) ───────┐
                                                                                 └─→ ÉTAPE 4 (0.25d)
                                                                                                    ↓
                                                                        ✅ Module A Complete
                                                          Foundation ready for Module 2+ (2d total)
```

### Parallel Opportunities Within ÉTAPE 3

All port definitions can be created in parallel:
- Developer A: Create 5 Input Ports (T006-T010)
- Developer B: Create 8 Output Ports (T011-T022)
- Developer C: Create Events + Exceptions (parallel, no dependencies)

---

## Dependencies & Blocking Issues

### Hard Blocking Dependencies

```
ÉTAPE 1 ─────REQUIRED BY───→ ÉTAPE 2
        (11 compiled modules)

ÉTAPE 2 ─────REQUIRED BY───→ ÉTAPE 3
        (ArchUnit rules validate port structure)

ÉTAPE 3 ─────REQUIRED BY───→ ÉTAPE 4
        (ports exist to validate SOLID)

Module A ─────REQUIRED BY───→ Module 2 (Commons)
        (foundation for all downstream modules)
```

### Architectural Constraints

- ✅ docai-domain MUST have ZERO external framework imports
- ✅ All ports MUST be interfaces (no implementation)
- ✅ All 12 ArchUnit rules MUST pass in CI
- ✅ All Checkstyle violations MUST be fixed before commit

---

## Success Criteria & Gate Exit

### ÉTAPE 1 Complete ✅
```
./mvnw clean compile
# BUILD SUCCESS
mvnw dependency:tree -pl docai-domain
# NO framework dependencies visible
```

### ÉTAPE 2 Complete ✅
```
./mvnw test -Dtest=HexagonalArchitectureTest
# 12/12 rules PASS ✅
```

### ÉTAPE 3 Complete ✅
```
ls -la docai-domain/src/main/java/fr/docai/domain/port/in/ | wc -l
# 5 port files
ls -la docai-domain/src/main/java/fr/docai/domain/port/out/ | wc -l
# 8 port files
./mvnw test -Dtest=HexagonalArchitectureTest
# Rules 4, 5, 10 verify port structure → PASS ✅
```

### ÉTAPE 4 Complete ✅
```
./mvnw checkstyle:check
# BUILD SUCCESS (0 violations)
./mvnw clean test -P quality-gates
# ArchUnit + Checkstyle + JaCoCo + PIT all PASS ✅
```

### Module A Gate Exit ✅
```
✅ All 11 modules compile
✅ HexagonalArchitectureTest: 12/12 rules pass
✅ 13 port interfaces defined
✅ Checkstyle: 0 violations
✅ Domain coverage ≥ 90%
✅ Domain mutation score ≥ 85%
✅ No framework imports in domain
→ READY FOR MODULE 2 (Commons)
```

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| ArchUnit rules too strict | Slows development | Document rationale, provide exceptions carefully |
| Port proliferation | Design complexity | Keep ports fine-grained (single responsibility) |
| Coverage targets missed | Build failures | Write domain tests as part of each port |
| Checkstyle violations | CI blocking | Enforce locally with pre-commit hooks |
| Circular dependencies | Architectural debt | Weekly dependency:tree analysis |

### F008 Fix — ArchUnit Rule Exception Procedure

**If a rule is legitimately too strict**, follow this approval workflow:

1. **Request Exception** (Developer):
   - Document the violation in a comment: `// ArchUnit-Exception-Rule-N: {reason}`
   - Provide business justification in PR description
   - Example: "Rule 6 exception needed because: application layer must call third-party library (not domain port)"

2. **Review & Approval** (Tech Lead):
   - Assess: Is this a one-time exception or architectural drift?
   - Approve ONLY if: exception is time-bound, documented in ADR amendment process, or architectural review required
   - Alternative: Refactor instead of exception (preferred)

3. **Document Exception** (If approved):
   - Update ADR log: `ADRs/ADR-amendments.md`
   - Record: rule ID, reason, approval date, expiration date (if temporary)
   - Example: "ADR Amendment 2026-05-25: Rule 6 relaxed until Phase 3 for QuotaService temporary exception"

4. **Revisit in Review Cycle** (Quarterly):
   - Quarterly architecture review checks: are exceptions still needed?
   - Refactor or make permanent ADR amendment

**Default**: Refactor code instead of adding exception (architecture should guide code, not vice versa).

---

## Success Metrics (F011 Fix — Enhanced)

| Metric | Target | Validation | Type |
|--------|--------|-----------|------|
| **Module compilation** | All 11 modules compile | `./mvnw clean compile → BUILD SUCCESS` | Functional |
| **ArchUnit rules** | 12/12 passing | `./mvnw test -Dtest=HexagonalArchitectureTest` | Functional |
| **Port isolation** | 0 framework imports in domain | `./mvnw dependency:tree -pl docai-domain \| grep -E "spring\|mongo\|kafka\|aws"` → empty | Functional |
| **Code quality** | 0 Checkstyle violations | `./mvnw checkstyle:check` | Quality |
| **SonarCloud** | 0 bugs, 0 vulnerabilities, ≤ 3% duplication | SonarCloud dashboard | Quality |
| **Circular dependencies** | 0 detected | `./mvnw dependency:tree` analysis | Functional |
| **ADR compliance** | All 11 ADRs mapped to modules | ADR compliance score ≥ 95% | Governance |
| **Coverage** | Domain ≥ 90%, global ≥ 80% | JaCoCo report | Functional |
| **Mutation testing** | Domain ≥ 85% | PIT mutation report | Quality |
| **Code review** | All reviews pass | 0 critical findings, ≤ 3 medium findings | Quality |
| **CI integration** | All checks pass | GitHub Actions / GitLab CI gates green | Functional |
| **Documentation** | ARCHITECTURE_GUIDE.md complete | ≥ 2000 words, all 13 ports documented | Documentation |

---

## Git Workflow & Version Control (F010 Fix)

### Branch Naming Convention

- **Feature branches**: `feature/{task-id}-{short-name}`
  - Example: `feature/T001-maven-structure`, `feature/T023-archunit-tests`
  - One task per branch (micro-task = one PR)
  - Branch from: `main` (or feature branch for Module A)

### Commit Message Strategy

- **Conventional Commits**: `{type}({scope}): {subject} [ref #{task-id}]`
  - `feat(domain)`: New domain entity or port
  - `test(archunit)`: ArchUnit rule implementation
  - `refactor(pom)`: Maven POM changes
  - `docs(guide)`: Documentation updates
  - Example: `feat(domain): Create DocumentRepository port [ref #T011]`

### Pull Request Strategy

- **One task per PR** (max 1 day of work)
- **Size**: 200-500 lines of code max (excluding generated code)
- **Squash + rebase** merge strategy
- **Require**: All CI checks pass + 1 approval (code review)

### Tag Strategy (F010 Enhancement)

After each phase completion, create annotated tag:
- `v001-architecture-foundation` — After ÉTAPE 1 (Maven structure)
- `v002-archunit-validation` — After ÉTAPE 2 (ArchUnit tests)
- `v003-ports-defined` — After ÉTAPE 3 (Port definitions)
- `v004-module-a-complete` — After ÉTAPE 4 (Documentation + validation)

---

## ÉTAPE to Task Mapping (F002 Fix — Naming Clarity)

| ÉTAPE | Name | Duration | Tasks | Goal |
|-------|------|----------|-------|------|
| **ÉTAPE 1** | Maven Structure | 0.5d | T001-T005 | 11 modules compile, domain isolated |
| **ÉTAPE 2** | ArchUnit Tests | 0.75d | T023-T036 | 12 rules pass, architecture validated |
| **ÉTAPE 3** | Port Design | 0.75d | T006-T022 | 13 ports defined, SOLID verified |
| **ÉTAPE 4** | SOLID Verification | 0.25d | T037-T040 | Checkstyle green, documentation complete |

**Clarification**: "ÉTAPE" (Phase) and "Tasks" (T###) are different organizational dimensions:
- **ÉTAPE**: Logical implementation phases with clear gates
- **Tasks**: Granular executable units (1-2 hours each)
- All tasks within an ÉTAPE must complete before moving to next ÉTAPE
- Tasks can be parallelized [P] within same ÉTAPE

---

## Integration with Overall Project

### Dependency Chain

```
Module A (Architecture Hexagonale) ← FOUNDATION
    ↓
Module B (Design Patterns & ADR) — 11 ADR compliance patterns
    ↓
Module C (Persistence Standards) — MongoDB, Mongock, indexes
    ↓
Module 1.A (Project Setup) — Maven 11 modules, Docker, Keycloak
    ↓
Module 2 (Commons) — 7 shared components
    ↓
Module 3-4 (Foundations) — Security, multi-tenancy, RGPD, billing
    ↓
Module 5-8 (Pipeline) — Document processing: classification, extraction, fraud, orchestration
    ↓
Module 9-11 (Product) — Dashboard, API management, billing UI
```

**Critical Path**: Module A MUST be complete before Module 2-11 implementation can proceed (architecture foundation)

### Hexagonal Principles in Downstream Modules

**Module 2 (Commons)**:
- Implements filters, mappers, validators using ports from Module A
- No domain imports (respects hexagonal boundary)

**Module 3+ (Adapters)**:
- ALL adapters implement ports defined in Module A
- docai-adapter-out-* modules depend on docai-domain only (for port interfaces)

**Module 11 (Bootstrap)**:
- Assembles all adapters → implements all ports
- Spring configuration happens ONLY in bootstrap

---

## Next Steps After Module A

1. **Module 2 (Commons)** — Implement shared filters, mappers, validators
2. **Module 3+ (Adapters)** — Implement ALL output port interfaces
3. **Module 4+ (Application)** — Implement use cases as input port implementations
4. **CI/CD** — Verify ArchUnit rules on every commit

---

## Review Checklist

Before marking Module A as COMPLETE:

- [ ] ÉTAPE 1: All 11 modules compile successfully
- [ ] ÉTAPE 2: HexagonalArchitectureTest created with 12 rules
- [ ] ÉTAPE 2: All 12 ArchUnit rules passing
- [ ] ÉTAPE 3: 5 Input Ports defined in docai-domain/port/in/
- [ ] ÉTAPE 3: 8 Output Ports defined in docai-domain/port/out/
- [ ] ÉTAPE 3: Domain events base class created
- [ ] ÉTAPE 3: Domain exceptions base class created
- [ ] ÉTAPE 4: Checkstyle configuration in parent pom.xml
- [ ] ÉTAPE 4: Zero Checkstyle violations
- [ ] ÉTAPE 4: Domain coverage ≥ 90%
- [ ] ÉTAPE 4: Domain mutation score ≥ 85%
- [ ] Documentation: ARCHITECTURE_GUIDE.md created
- [ ] CI Integration: All tests pass in CI pipeline

---

**Last Updated**: 2026-05-24  
**Status**: Ready for Implementation  
**Estimated Duration**: 2 days  
**Critical Path**: ÉTAPE 1 → ÉTAPE 2 → ÉTAPE 3 → ÉTAPE 4
