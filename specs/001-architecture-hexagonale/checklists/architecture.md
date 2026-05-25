# Checklist: Module A — Architecture Hexagonale

**Module**: Module A — Architecture & Principes (Référence Transversale)  
**Skill**: docai-architecture-adr  
**Created**: 2026-05-24  
**Purpose**: Validate that architecture requirements are complete, clear, measurable, and consistent  
**Scope**: Hexagonal architecture, ArchUnit rules, SOLID principles, design patterns, ADR compliance

---

## 🎯 Checklist Purpose

This checklist validates the **QUALITY OF REQUIREMENTS** for Module A architecture, not the implementation. Each item tests whether requirements are:
- **Complete**: All necessary requirements documented?
- **Clear**: Unambiguous and specific?
- **Measurable**: Can be objectively verified?
- **Consistent**: Aligned without conflicts?

---

## Requirement Completeness

**Are all hexagonal architecture requirements fully specified?**

- [ ] CHK001 - Are domain module isolation requirements explicitly stated (ZERO Spring/MongoDB/Kafka/AWS imports)? [Completeness, Plan §ÉTAPE 1]
- [ ] CHK002 - Are application layer dependencies clearly defined (ONLY on domain)? [Completeness, Plan §ÉTAPE 1]
- [ ] CHK003 - Are adapter module separation rules documented (no inter-adapter dependencies)? [Completeness, Gap]
- [ ] CHK004 - Are all 11 module dependency relationships documented? [Completeness, Plan §Module Dependencies]
- [ ] CHK005 - Is the package root structure fully specified (fr.docai.*)? [Completeness, Plan §ÉTAPE 1]
- [ ] CHK006 - Are Maven module pom.xml requirements documented for each module type? [Completeness, Gap]

**Are all 12 ArchUnit rules requirements fully specified?**

- [ ] CHK007 - Are all 12 ArchUnit rules clearly defined with their purpose? [Completeness, Plan §ÉTAPE 2]
- [ ] CHK008 - Is the ArchUnit test class location explicitly required (docai-bootstrap)? [Completeness, Plan §ÉTAPE 2]
- [ ] CHK009 - Are the specific ArchUnit syntax/patterns required for each rule documented? [Completeness, Gap]
- [ ] CHK010 - Is the CI integration requirement documented (HexagonalArchitectureTest in Phase 1)? [Completeness, Plan §BR-ARCH-001]
- [ ] CHK011 - Are test execution commands documented? [Completeness, Plan §ÉTAPE 2]

**Are port structure requirements completely defined?**

- [ ] CHK012 - Are all 5 input ports explicitly named and their responsibilities documented? [Completeness, Plan §ÉTAPE 3]
- [ ] CHK013 - Are all 8 output ports explicitly named and their responsibilities documented? [Completeness, Plan §ÉTAPE 3]
- [ ] CHK014 - Is the domain event structure requirement documented? [Completeness, Plan §ÉTAPE 3]
- [ ] CHK015 - Is the domain exception hierarchy requirement documented? [Completeness, Plan §ÉTAPE 3]
- [ ] CHK016 - Are port directory structure requirements fully specified? [Completeness, Plan §ÉTAPE 3]

**Are quality gate requirements completely defined?**

- [ ] CHK017 - Are domain coverage requirements quantified (≥ 90%)? [Completeness, Plan §Quality Gates]
- [ ] CHK018 - Are mutation testing requirements quantified (≥ 85%)? [Completeness, Plan §Quality Gates]
- [ ] CHK019 - Are Checkstyle rules fully documented (method length, parameters, complexity)? [Completeness, Plan §ÉTAPE 4]
- [ ] CHK020 - Is the CI integration of quality gates documented? [Completeness, Gap]

---

## Requirement Clarity & Specificity

**Are hexagonal architecture requirements unambiguous?**

- [ ] CHK021 - Is "ZERO import" quantified — does it mean direct only or transitive imports too? [Clarity, Ambiguity]
- [ ] CHK022 - Is "depends ONLY on domain" specified for application layer (transitive OK or not)? [Clarity, Ambiguity]
- [ ] CHK023 - Are framework exclusions explicitly listed (Spring, MongoDB, Kafka, AWS SDK)? [Clarity, Plan §ÉTAPE 1]
- [ ] CHK024 - Is the penalty/action for architecture violations documented? [Clarity, Gap]

**Are ArchUnit rule definitions specific and testable?**

- [ ] CHK025 - Is Rule 1 (pure Java domain) quantified with exact ArchUnit syntax? [Clarity, Plan §ÉTAPE 2]
- [ ] CHK026 - Is Rule 2 (no inter-adapter calls) defined with measurable criteria? [Clarity, Gap]
- [ ] CHK027 - Is Rule 3 (application → domain only) specified with exact dependency scope? [Clarity, Gap]
- [ ] CHK028 - Are Rules 4-5 (port location) specified with exact directory paths? [Clarity, Plan §ÉTAPE 3]
- [ ] CHK029 - Is Rule 6 (adapters implement ports) specified with exact class relationship? [Clarity, Gap]
- [ ] CHK030 - Is Rule 10 (ports are interfaces) specified to exclude abstract classes? [Clarity, Plan §ÉTAPE 2]

**Are port responsibilities clearly defined?**

- [ ] CHK031 - Is each input port's single responsibility documented? [Clarity, Plan §ÉTAPE 3]
- [ ] CHK032 - Is each output port's contract (method signatures) documented? [Clarity, Plan §ÉTAPE 3]
- [ ] CHK033 - Are domain event base class properties (eventId, occurredAt) documented? [Clarity, Plan §ÉTAPE 3]
- [ ] CHK034 - Are domain exception constructors and inheritance documented? [Clarity, Plan §ÉTAPE 3]

**Are code quality metrics clearly specified?**

- [ ] CHK035 - Is "20 lines max per method" measured including or excluding blank/comment lines? [Clarity, Ambiguity]
- [ ] CHK036 - Is "4 parameters max" specified for constructors and regular methods separately? [Clarity, Gap]
- [ ] CHK037 - Is cyclomatic complexity ≤ 10 applicable to all code or domain only? [Clarity, Ambiguity]

---

## Requirement Consistency

**Are architecture requirements consistent across documents?**

- [ ] CHK038 - Are domain isolation requirements consistent between Plan, Tasks, and this checklist? [Consistency, Plan §ÉTAPE 1, Tasks §T001-T005]
- [ ] CHK039 - Are the 12 ArchUnit rules consistent across Plan and Tasks? [Consistency, Plan §ÉTAPE 2, Tasks §T024-T035]
- [ ] CHK040 - Are port definitions consistent between Plan (13 ports) and Tasks (T006-T022)? [Consistency, Plan §ÉTAPE 3, Tasks §T006-T022]
- [ ] CHK041 - Are quality gate metrics (coverage ≥ 90%, mutation ≥ 85%) consistent across documents? [Consistency, Plan §Quality Gates]

**Are ArchUnit rules internally consistent?**

- [ ] CHK042 - Do Rules 1-5 (domain isolation) conflict with Rule 12 (@Transactional in domain)? [Consistency, Plan §ÉTAPE 2]
- [ ] CHK043 - Are Rules 4-5 (port location) consistent with Rule 6 (adapter implementation)? [Consistency, Plan §ÉTAPE 2]
- [ ] CHK044 - Is Rule 2 (no inter-adapter calls) consistent with Rule 8 (Kafka access isolated)? [Consistency, Plan §ÉTAPE 2]

**Are SOLID principle requirements consistent?**

- [ ] CHK045 - Does the Single Responsibility principle (S) conflict with port aggregation? [Consistency, Plan §SOLID]
- [ ] CHK046 - Are Open/Closed (O) and Dependency Inversion (D) specifications aligned? [Consistency, Plan §SOLID]
- [ ] CHK047 - Is Liskov Substitution (L) example (OcrPort) consistent across all ports? [Consistency, Plan §SOLID]

---

## Acceptance Criteria Quality

**Are success criteria for each ÉTAPE measurable?**

- [ ] CHK048 - Is ÉTAPE 1 success criterion "BUILD SUCCESS" objectively verifiable? [Measurability, Plan §ÉTAPE 1]
- [ ] CHK049 - Is ÉTAPE 2 success criterion "12/12 rules PASS" measurable in CI? [Measurability, Plan §ÉTAPE 2]
- [ ] CHK050 - Is ÉTAPE 3 success criterion "structure conforme to ArchUnit" quantified? [Measurability, Plan §ÉTAPE 3]
- [ ] CHK051 - Is ÉTAPE 4 success criterion "0 violations" verifiable with Checkstyle? [Measurability, Plan §ÉTAPE 4]
- [ ] CHK052 - Are code quality metrics measurable with standard tools (JaCoCo, PIT)? [Measurability, Plan §Quality Gates]

**Are ArchUnit rule success criteria quantifiable?**

- [ ] CHK053 - Can Rule 1 (domain pure Java) be verified with dependency:tree analysis? [Measurability, Plan §ÉTAPE 2]
- [ ] CHK054 - Can Rule 2 (no inter-adapter calls) be verified with ArchUnit syntax? [Measurability, Gap]
- [ ] CHK055 - Can Rule 10 (ports are interfaces) be verified with ArchUnit isInterface() check? [Measurability, Plan §ÉTAPE 2]

**Are quality gate success criteria quantifiable?**

- [ ] CHK056 - Can "domain coverage ≥ 90%" be measured with JaCoCo reports? [Measurability, Plan §Quality Gates]
- [ ] CHK057 - Can "mutation score ≥ 85%" be measured with PIT reports? [Measurability, Plan §Quality Gates]
- [ ] CHK058 - Can Checkstyle violations be automatically counted and gated? [Measurability, Plan §ÉTAPE 4]

---

## Scenario Coverage

**Are requirements specified for all hexagonal architecture scenarios?**

- [ ] CHK059 - Are requirements defined for domain entity creation in modules? [Coverage, Plan §ÉTAPE 1]
- [ ] CHK060 - Are requirements for port implementation in adapters documented? [Coverage, Plan §ÉTAPE 3]
- [ ] CHK061 - Are requirements for inter-module communication (via ports only) documented? [Coverage, Gap]
- [ ] CHK062 - Are requirements for adding new modules specified? [Coverage, Gap]

**Are all ArchUnit rule verification scenarios documented?**

- [ ] CHK063 - Is the scenario of adding a new framework to domain dependencies documented (should FAIL)? [Coverage, Gap]
- [ ] CHK064 - Is the scenario of direct adapter-to-adapter calls documented (should FAIL)? [Coverage, Gap]
- [ ] CHK065 - Is the scenario of adding a port outside docai-domain documented (should FAIL)? [Coverage, Gap]

**Are port implementation scenarios documented?**

- [ ] CHK066 - Are requirements for implementing multiple output ports documented? [Coverage, Plan §ÉTAPE 3]
- [ ] CHK067 - Are requirements for a port used by multiple use cases documented? [Coverage, Gap]
- [ ] CHK068 - Are requirements for circular port dependencies documented (should be prevented)? [Coverage, Gap]

**Are test coverage scenarios documented?**

- [ ] CHK069 - Are requirements for testing domain entities documented? [Coverage, Gap]
- [ ] CHK070 - Are requirements for testing port implementations documented? [Coverage, Gap]
- [ ] CHK071 - Are requirements for testing cross-domain interactions documented? [Coverage, Gap]

---

## Edge Case & Exception Coverage

**Are boundary conditions defined?**

- [ ] CHK072 - What happens when a domain entity is instantiated outside ÉTAPE 1? [Edge Case, Gap]
- [ ] CHK073 - What happens when an adapter implements multiple ports? [Edge Case, Gap]
- [ ] CHK074 - What happens when a port is never implemented? [Edge Case, Gap]
- [ ] CHK075 - Are exception cases for domain exceptions documented? [Edge Case, Plan §ÉTAPE 3]

**Are error/failure scenarios defined?**

- [ ] CHK076 - What happens if an ArchUnit rule fails in CI? [Exception, Gap]
- [ ] CHK077 - What happens if coverage or mutation targets are not met? [Exception, Gap]
- [ ] CHK078 - What happens if a module violates hexagonal constraints? [Exception, Gap]
- [ ] CHK079 - What happens if a developer adds a framework import to domain? [Exception, Gap]

**Are rollback/recovery scenarios defined?**

- [ ] CHK080 - Are requirements for reverting architectural changes documented? [Recovery, Gap]
- [ ] CHK081 - Are requirements for gradual migration to hexagonal architecture documented? [Recovery, Gap]

---

## Non-Functional Requirements

**Are performance requirements specified?**

- [ ] CHK082 - Are compilation time requirements specified for the 11-module build? [Non-Functional, Gap]
- [ ] CHK083 - Are test execution time requirements for ArchUnit rules specified? [Non-Functional, Gap]
- [ ] CHK084 - Are code analysis tool execution time requirements specified? [Non-Functional, Gap]

**Are maintainability requirements specified?**

- [ ] CHK085 - Are requirements for code documentation density specified? [Non-Functional, Gap]
- [ ] CHK086 - Are requirements for domain module readability specified? [Non-Functional, Gap]
- [ ] CHK087 - Are requirements for port interface clarity specified? [Non-Functional, Gap]

**Are scalability requirements specified?**

- [ ] CHK088 - Are requirements for adding new modules beyond 11 specified? [Non-Functional, Gap]
- [ ] CHK089 - Are requirements for adding new ports beyond 13 specified? [Non-Functional, Gap]
- [ ] CHK090 - Are requirements for managing large bounded contexts specified? [Non-Functional, Gap]

---

## Dependencies & Assumptions

**Are external dependencies documented?**

- [ ] CHK091 - Is the dependency on Module 1.A (Setup Projet) explicitly documented? [Dependency, Plan §Prerequisites]
- [ ] CHK092 - Are the Maven version requirements (3.9+) documented? [Dependency, Plan §Prerequisites]
- [ ] CHK093 - Is the Java version requirement (21) documented? [Dependency, Plan §Prerequisites]
- [ ] CHK094 - Are ArchUnit library version requirements specified? [Dependency, Gap]
- [ ] CHK095 - Are Checkstyle configuration version requirements specified? [Dependency, Gap]
- [ ] CHK096 - Are JaCoCo and PIT tool version requirements specified? [Dependency, Gap]

**Are assumptions documented?**

- [ ] CHK097 - Is the assumption "developers have Maven installed" documented? [Assumption, Gap]
- [ ] CHK098 - Is the assumption "CI system has Java 21 available" documented? [Assumption, Gap]
- [ ] CHK099 - Is the assumption "all 11 modules are compiled before testing" documented? [Assumption, Gap]
- [ ] CHK100 - Is the assumption "domain module will always be tested separately" documented? [Assumption, Gap]

**Are constraints documented?**

- [ ] CHK101 - Are organizational constraints (e.g., "must use Spring Boot") documented? [Constraint, Gap]
- [ ] CHK102 - Are technical constraints (e.g., "Java 21 minimum") documented? [Constraint, Plan §Prerequisites]
- [ ] CHK103 - Are timeline constraints for implementation documented? [Constraint, Plan §Duration: 2 days]

---

## SOLID Principles Specification

**Are SOLID principle requirements clearly defined?**

- [ ] CHK104 - Is the Single Responsibility principle (S) definition specific to use cases? [Clarity, Plan §SOLID]
- [ ] CHK105 - Is the Open/Closed principle (O) requirement to use Strategy Pattern documented with examples? [Clarity, Plan §SOLID]
- [ ] CHK106 - Is Liskov Substitution (L) requirement specified with exact port interchangeability examples? [Clarity, Plan §SOLID]
- [ ] CHK107 - Is Interface Segregation (I) requirement quantified for port granularity? [Clarity, Plan §SOLID]
- [ ] CHK108 - Is Dependency Inversion (D) requirement specified for port dependencies? [Clarity, Plan §SOLID]

**Are SOLID principle violations detectable?**

- [ ] CHK109 - Are violations of Single Responsibility principle detectable in code review? [Measurability, Gap]
- [ ] CHK110 - Are violations of Open/Closed principle (using if/else instead of Strategy) detectable? [Measurability, Gap]
- [ ] CHK111 - Are violations of Liskov Substitution principle detectable with ArchUnit? [Measurability, Gap]
- [ ] CHK112 - Are violations of Interface Segregation principle detectable? [Measurability, Gap]
- [ ] CHK113 - Are violations of Dependency Inversion principle detectable with ArchUnit Rule 6? [Measurability, Plan §ÉTAPE 2]

---

## Design Patterns & ADR Compliance

**Are design pattern requirements specified?**

- [ ] CHK114 - Is the Outbox Pattern requirement documented with implementation details? [Completeness, Plan §Design Patterns]
- [ ] CHK115 - Is the Strategy Pattern requirement documented with use cases? [Completeness, Plan §Design Patterns]
- [ ] CHK116 - Is the Registry Pattern requirement documented for FraudAnalyzerRegistry? [Completeness, Plan §Design Patterns]
- [ ] CHK117 - Is the Composite Pattern requirement documented for fraud analysis? [Completeness, Plan §Design Patterns]
- [ ] CHK118 - Is the Cache-Aside Pattern requirement documented? [Completeness, Plan §Design Patterns]
- [ ] CHK119 - Is the Anti-Corruption Layer requirement documented? [Completeness, Plan §Design Patterns]
- [ ] CHK120 - Is the CQRS Pattern requirement documented? [Completeness, Plan §Design Patterns]

**Are ADR requirements specified?**

- [ ] CHK121 - Is ADR-002 (Outbox Pattern with documentId partition key) documented? [Completeness, Plan §Design Patterns]
- [ ] CHK122 - Is ADR-010 (EXPLAIN PLAN MongoDB requirement) documented? [Completeness, Gap]
- [ ] CHK123 - Are ADR constraints integrated into domain module requirements? [Consistency, Gap]
- [ ] CHK124 - Are ADR constraints verified by ArchUnit rules? [Consistency, Gap]

---

## Business Rules Specification

**Are business rules (BR-ARCH-*) clearly defined?**

- [ ] CHK125 - Is BR-ARCH-001 (HexagonalArchitectureTest in CI Phase 1) quantified with execution timing? [Clarity, Plan §BR-ARCH-001]
- [ ] CHK126 - Is BR-ARCH-002 (12 rules all active, no deactivation) enforced in CI? [Clarity, Plan §BR-ARCH-002]
- [ ] CHK127 - Is BR-ARCH-003 (PIT ≥ 85%) measured and gated in CI? [Clarity, Plan §BR-ARCH-003]
- [ ] CHK128 - Is BR-ARCH-004 (JaCoCo ≥ 90%) measured and gated in CI? [Clarity, Plan §BR-ARCH-004]

**Are business rule violations detectable?**

- [ ] CHK129 - Can BR-ARCH-001 (HexagonalArchitectureTest execution) be verified from CI logs? [Measurability, Gap]
- [ ] CHK130 - Can BR-ARCH-002 (12 rules active) be verified from test output? [Measurability, Gap]
- [ ] CHK131 - Can BR-ARCH-003 (PIT ≥ 85%) be verified from mutation reports? [Measurability, Gap]
- [ ] CHK132 - Can BR-ARCH-004 (JaCoCo ≥ 90%) be verified from coverage reports? [Measurability, Gap]

---

## Traceability & Specification IDs

**Are all requirements traceable?**

- [ ] CHK133 - Does the plan use unique IDs for each requirement (e.g., FR-001, BR-ARCH-001)? [Traceability, Gap]
- [ ] CHK134 - Are all tasks in tasks.md traceable to specific requirements? [Traceability, Tasks §T001-T040]
- [ ] CHK135 - Are all checklist items traceable to plan or tasks? [Traceability, Checklist §All items]
- [ ] CHK136 - Is there a requirements traceability matrix documenting coverage? [Traceability, Gap]

**Are specification cross-references complete?**

- [ ] CHK137 - Do all ArchUnit rules reference corresponding tasks? [Traceability, Plan §ÉTAPE 2, Tasks §T024-T035]
- [ ] CHK138 - Do all ÉTAPE deliverables reference corresponding tasks? [Traceability, Plan §4 ÉTAPES, Tasks §All]
- [ ] CHK139 - Do all quality gates reference corresponding verification tasks? [Traceability, Plan §Quality Gates, Tasks §T037-T039]

---

## Conflicts & Ambiguities Summary

**Identified Conflicts**:
- [ ] CHK140 - CONFLICT: Are Rules 1-5 (domain isolation) vs. Rule 12 (@Transactional in domain) clearly resolved? [Conflict, Plan §ÉTAPE 2]
- [ ] CHK141 - CONFLICT: Can domain coverage (≥ 90%) and mutation score (≥ 85%) both be achieved? [Conflict, Plan §Quality Gates]

**Identified Ambiguities**:
- [ ] CHK142 - AMBIGUITY: Does "ZERO import" mean direct imports only or transitive? [Ambiguity, Plan §ÉTAPE 1]
- [ ] CHK143 - AMBIGUITY: Are method length limits (20 lines) measured with or without blank/comment lines? [Ambiguity, Plan §ÉTAPE 4]
- [ ] CHK144 - AMBIGUITY: Can parameters be overloaded, or is the 4-parameter limit absolute? [Ambiguity, Plan §ÉTAPE 4]

**Identified Gaps**:
- [ ] CHK145 - GAP: Are requirements for module dependency visualization documented? [Gap, Plan §all]
- [ ] CHK146 - GAP: Are requirements for circular dependency detection documented? [Gap, Plan §ÉTAPE 2]
- [ ] CHK147 - GAP: Are requirements for versioning ports and adapters documented? [Gap, Plan §all]
- [ ] CHK148 - GAP: Are requirements for deprecating old ports documented? [Gap, Plan §all]
- [ ] CHK149 - GAP: Are requirements for monitoring architecture compliance at runtime documented? [Gap, Plan §all]

---

## Implementation Readiness

**Are requirements implementation-ready?**

- [ ] CHK150 - Can a developer start ÉTAPE 1 immediately with these requirements? [Readiness, Plan §ÉTAPE 1]
- [ ] CHK151 - Are all pre-requisites for ÉTAPE 2 available before starting? [Readiness, Plan §Prerequisites]
- [ ] CHK152 - Can a developer complete ÉTAPE 3 with documented port templates? [Readiness, Plan §ÉTAPE 3]
- [ ] CHK153 - Are all success criteria objective and measurable before implementation starts? [Readiness, Plan §Success Criteria]

**Are requirements testable before implementation?**

- [ ] CHK154 - Can ArchUnit rules be written before domain code exists? [Testability, Plan §ÉTAPE 2]
- [ ] CHK155 - Can Checkstyle configuration be set before coding starts? [Testability, Plan §ÉTAPE 4]
- [ ] CHK156 - Can JaCoCo coverage targets be verified before writing domain tests? [Testability, Plan §Quality Gates]

---

## Summary

**Total Checklist Items**: 156  
**Critical Items** (blocking implementation):
- CHK001-CHK006 (Domain isolation completeness)
- CHK007-CHK011 (ArchUnit rules completeness)
- CHK021-CHK037 (Clarity of requirements)
- CHK142-CHK149 (Gaps and ambiguities)

**Recommended Review Order**:
1. **First**: Review Conflicts & Ambiguities (CHK140-CHK149) — resolve before coding
2. **Second**: Review Completeness (CHK001-CHK020) — ensure all requirements documented
3. **Third**: Review Clarity (CHK021-CHK037) — eliminate ambiguities
4. **Fourth**: Review Consistency (CHK038-CHK047) — align documents
5. **Finally**: Review remaining categories (Coverage, Scenarios, Edge Cases, Dependencies)

---

**Status**: Ready for Review  
**Next Action**: Address identified Conflicts & Ambiguities before starting implementation

