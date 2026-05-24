# Implementation Tasks: Maven Multi-Module & Docker Compose Infrastructure

**Feature**: Module 1.A — Structure Maven + Docker Compose | Partie 1 — Setup & CI/CD  
**Branch**: `002-maven-docker-setup`  
**Total Tasks**: 42  
**Duration**: 3.5 days (8 phases covering 6 étapes, max 1 day each)  
**Generated**: 2026-05-24  
**Updated**: 2026-05-24 (Fixed C1, C2, C3 critical issues)

---

## Task Organization

Tasks are organized in **execution dependency order** across 8 phases:

1. **Phase 1 — Setup** (3 tasks) — Project initialization, no story label
2. **Phase 2 — Foundational** (2 tasks) — Prerequisites blocking all user stories
3. **Phase 3 — User Story 1** (3 tasks) — Developer Initializes Local Environment [US1]
4. **Phase 4 — User Story 2** (4 tasks) — Developer Builds Backend Application [US2]
5. **Phase 5 — User Story 3** (4 tasks) — Developer Configures Keycloak Realm [US3]
6. **Phase 6 — User Story 4** (3 tasks) — Developer Seeds Development Data [US4]
7. **Phase 7 — User Story 5** (4 tasks) — DevOps Prepares CI/CD Pipeline [US5]
8. **Phase 8 — Polish** (1 task) — Documentation & final validation

**Completion Order**: Phase 1 → Phase 2 → [Phase 3-5 in parallel] → Phase 6 → Phase 7 → Phase 8

---

## Phase 1: Setup (Project Initialization)

- [ ] T001 Create root project structure: pom.xml parent, 11 module directories per FR-001
- [ ] T002 Initialize git repository with conventional commits setup (if not already done)
- [ ] T003 Create .gitignore with .env, docker volumes, Maven target, IDE configs

---

## Phase 2: Foundational (Prerequisites)

### ÉTAPE 1 & 2 — Maven Structure + ArchUnit (1 day)

- [ ] T004 [P] Create parent pom.xml with Java 21, Spring Boot 4.0.x, shared dependency versions per FR-002
- [ ] T005 [P] Create 11 module pom.xml files with correct package roots (fr.docai.*) and dependency hierarchy per FR-003
- [ ] T006 Create HexagonalArchitectureTest class with 12 ArchUnit rules in docai-bootstrap/src/test/java
- [ ] T007 Verify all 11 modules compile and ArchUnit tests pass: `./mvnw clean test -Dtest=HexagonalArchitectureTest`

**Acceptance**: `./mvnw clean compile → SUCCESS` (all 11 modules), `./mvnw test -Dtest=HexagonalArchitectureTest → 12 rules PASS`

---

## Phase 3: User Story 1 — Developer Initializes Local Environment [US1]

### ÉTAPE 3 — Docker Compose Infrastructure (1 day)

- [ ] T008 [P] [US1] Create docker-compose.yml with 11 services (MongoDB, Kafka, Keycloak, Valkey, etc.) per FR-005
- [ ] T009 [P] [US1] Create mongodb-init service for Replica Set rs0 initialization (idempotent rs.initiate())
- [ ] T010 [P] [US1] Create kafka-init service to create 8 topics with documentId partition key per ADR-002
- [ ] T011 [US1] Verify all services start healthy: `docker compose up -d && docker compose ps` (all healthy within 30s)
- [ ] T012 [US1] Verify MongoDB Replica Set initialized: `docker exec mongodb mongosh --eval "rs.status().ok"` → 1
- [ ] T013 [US1] Verify 8 Kafka topics created with correct names: `docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092`

**Independent Test**: Clone repo → `docker compose up -d` → Verify all services healthy within 30 seconds per SC-001  
**Completion Criterion**: All 11 services show "healthy" or "running" status

---

## Phase 4: User Story 2 — Developer Builds Backend Application [US2]

### ÉTAPE 1-2 (continued) — Maven Configuration

- [ ] T014 [P] [US2] Configure Maven profiles: unit-tests, integration-tests, quality-gates per FR-004
- [ ] T015 [US2] Create Checkstyle configuration (20-line max method, 4 params, complexity ≤10) per FR-018
- [ ] T016 [US2] Configure JUnit 5 and Mockito for unit tests in docai-domain
- [ ] T017 [US2] Verify Maven profiles work independently:
  - `./mvnw test -P unit-tests` (unit tests only)
  - `./mvnw test -P integration-tests` (integration tests)
  - `./mvnw test -P quality-gates` (ArchUnit, Checkstyle)

**Independent Test**: `./mvnw clean package` → All 11 modules compile, no circular dependencies per SC-002  
**Completion Criterion**: Build completes in < 3 minutes, all 12 ArchUnit rules pass

---

## Phase 5: User Story 3 — Developer Configures Keycloak Realm [US3]

### ÉTAPE 4 — Keycloak Realm Configuration (0.5 days)

- [ ] T018 [P] [US3] Create docker/keycloak/realm-docai.json with 5 roles (admin, tenant-admin, manager, user, viewer) per FR-009
- [ ] T019 [P] [US3] Add 5 test users to realm-docai.json with tenant_id User Attribute per FR-009, clarification CHK088
- [ ] T020 [P] [US3] Configure Protocol Mapper in realm-docai.json (User Attribute mapper, tenant_id → JWT claim) per FR-010 & ADR-006
- [ ] T021 [US3] Mount realm-docai.json in docker-compose.yml (Keycloak volume)
- [ ] T022 [US3] Create init-keycloak.sh (idempotent realm import script) per FR-011
- [ ] T023 [US3] Verify Keycloak realm initialized: Login as test user, decode JWT, verify tenant_id claim present per SC-010

**Independent Test**: Import realm-docai.json → Create test user → Obtain JWT → Verify tenant_id claim present per SC-010  
**Completion Criterion**: JWT tokens include tenant_id claim; re-import idempotent (no duplicate users)

---

## Phase 6: User Story 4 — Developer Seeds Development Data [US4]

### ÉTAPE 5-6 — Configuration + Seeding Service (1 day)

- [ ] T024 [US4] Create .env.example with all required variables (MongoDB, Kafka, Keycloak, AWS S3, Valkey) per FR-012, FR-013
- [ ] T025 [US4] Create application.yml (docai-bootstrap) with Spring Boot configuration reading from .env per FR-014
- [ ] T026 [US4] Create application-dev.yml with MongoDB profiling level 1 (100ms threshold) per ADR-010 & FR-022
- [ ] T027 [US4] Ensure .env in .gitignore (.env.example committed, .env git-ignored) per CHK010, FR-014
- [ ] T028 [US4] Create SeedingService (@Profile("seed")) with idempotent seedTenants() and seedUsers() per FR-015, FR-016, clarification CHK087
- [ ] T029 [US4] Implement find-or-create logic for tenants/users (upsert by natural key: email/slug) per clarification CHK088
- [ ] T030 [US4] Create SeedDataConstants with 3 tenants and 10+ test users (realistic data) per FR-015
- [ ] T031 [US4] Verify seeding on startup: `./mvnw spring-boot:run -Dspring.profiles.active=seed` → Seeds data, creates 3 tenants + 10+ users per SC-007
- [ ] T032 [US4] Verify idempotency: Run seeding twice with same profile → Zero duplicates per FR-016

**Independent Test**: Run with `--spring.profiles.active=seed` → Verify 3 tenants + 10+ users seeded in MongoDB (< 5 seconds) per SC-007  
**Completion Criterion**: Seeding profile activation creates exactly 3 tenants, 10+ users; idempotent (no duplicates on re-run)

---

## Phase 7: User Story 5 — DevOps Prepares CI/CD Pipeline Structure [US5]

### Maven Profiles & CI/CD Configuration

- [ ] T033 [P] [US5] Configure Maven profiles for CI/CD (unit-tests, integration-tests, quality-gates) per FR-004
- [ ] T034 [P] [US5] Create CI job templates (.github/workflows or .gitlab-ci.yml) with MAVEN_OPTS=-Xmx512m per ADR-008, CHK006
- [ ] T035 [US5] Configure ArchUnit test execution in quality-gates profile per FR-020, SC-004
- [ ] T036 [US5] Configure Checkstyle validation in Maven per FR-018
- [ ] T037 [US5] Verify Maven profiles execute independently in CI context per SC-002, SC-004

**Independent Test**: Run each profile separately → unit-tests, integration-tests, quality-gates all pass  
**Completion Criterion**: All 3 Maven profiles work independently; CI can run tests in parallel

---

## Phase 8: Polish & Documentation

- [ ] T038 [P] Create DOCKER_SETUP.md with troubleshooting guide (service startup, health checks, volume cleanup, port conflicts) per FR-023, SC-001
- [ ] T039 [P] Create/Update CLAUDE.md with seeding instructions and profile activation steps per FR-023
- [ ] T040 Update spec.md Clarifications section with all 4 resolved ambiguities (CHK085-088)
- [ ] T041 Run `/speckit-analyse` to verify architecture compliance against all 10 points
- [ ] T042 Final validation: All 12 success criteria met per SC-001 through SC-012

**Final Acceptance**: 
- ✅ All 11 services healthy within 30s (SC-001)
- ✅ Maven build < 3 minutes (SC-002)
- ✅ Domain coverage ≥ 90% (SC-003)
- ✅ 12 ArchUnit rules pass (SC-004)
- ✅ Setup < 10 minutes (SC-005)
- ✅ Keycloak realm idempotent (SC-006)
- ✅ Seeding < 5 seconds (SC-007)
- ✅ Spring Boot starts < 15 seconds (SC-008)
- ✅ 8 Kafka topics with documentId key (SC-009)
- ✅ JWT includes tenant_id claim (SC-010)
- ✅ MongoDB EXPLAIN PLAN tooling functional (SC-011)
- ✅ 100% documentation complete (SC-012)

---

## Parallel Execution Strategy

### Possible Parallel Workflows (Save 1.5 days)

**Day 1**: Phase 1 + Phase 2 (Setup + Maven + ArchUnit)
```
T001-T003 (Setup)
├── T004-T007 (Maven structure + ArchUnit tests)
└── Complete by end of Day 1
```

**Day 2** (Start after Day 1 complete): Phases 3-5 in **parallel**
```
T008-T013 (US1 Docker Compose)     [1 hour]
├── T014-T017 (US2 Maven Profiles)  [1 hour]  
├── T018-T023 (US3 Keycloak)        [1 hour]
└── All complete by Day 2 lunch
```

**Day 2-3**: Phases 6-7 (Seeding + CI/CD)
```
T024-T032 (US4 Seeding)   [4 hours]
T033-T037 (US5 CI/CD)     [2 hours, parallel after T007]
└── Complete by Day 3 morning
```

**Day 3**: Phase 8 (Polish)
```
T038-T042 (Documentation + Final Validation)
└── Complete by Day 3 end
```

**Total Critical Path**: 1 + 1 + 1 + 0.5 = **3.5 days** ✓

---

## Dependency Map

```
T001-T003 (Setup)
    ↓
T004-T007 (Maven + ArchUnit) ← BLOCKING for all stories
    ↓
    ├─→ T008-T013 (US1: Docker)
    ├─→ T014-T017 (US2: Build)
    ├─→ T018-T023 (US3: Keycloak)
    │       ↓
    │   T024-T032 (US4: Seeding) ← Requires Docker + Keycloak
    │       ↓
    │   T033-T037 (US5: CI/CD)
    │
    └─→ T038-T042 (Polish)
```

**Critical Path**: T001-T007 → T008 or T018 → T024-T032 → T038-T042

---

## Testing Strategy

Each user story includes independent test criteria (can test in isolation):

| Story | Independent Test | Success Criterion |
|-------|-----------------|-------------------|
| US1 | `docker compose up -d` → All healthy | All 11 services healthy within 30s (SC-001) |
| US2 | `./mvnw clean package` → BUILD SUCCESS | All modules compile, no dependencies (SC-002) |
| US3 | Import realm → Login → Decode JWT | JWT includes tenant_id claim (SC-010) |
| US4 | Run with `--spring.profiles.active=seed` | 3 tenants + 10+ users, idempotent (SC-007) |
| US5 | Run each Maven profile separately | All 3 profiles work independently (SC-004) |

---

## Success Metrics

| Metric | Target | Task(s) | Validation |
|--------|--------|---------|-----------|
| Service startup | ≤ 30 seconds | T008-T013 | `docker compose ps` all healthy |
| Maven build | < 3 minutes | T004-T007 | `./mvnw clean package` timing |
| Domain coverage | ≥ 90% | T016 | SonarCloud or JaCoCo report (Phase 2+) |
| ArchUnit rules | 12/12 PASS | T006-T007 | `./mvnw test -Dtest=HexagonalArchitectureTest` |
| Seeding speed | < 5 seconds | T028-T032 | Profile startup timing measurement |
| JWT claims | tenant_id present | T023 | Decode JWT at jwt.io or programmatically |
| Idempotency | Zero duplicates | T032 | Run twice, verify counts unchanged |

---

## Notes

- **Clarifications Integrated**: CHK085 (real AWS S3), CHK086 (profile activation, no HTTP endpoint), CHK087 (auto-seeding on startup), CHK088 (zero duplicates via find-or-create)
- **ADR References**: ADR-002 (Kafka documentId key), ADR-006 (JWKS 1h cache), ADR-008 (CI MAVEN_OPTS), ADR-010 (MongoDB EXPLAIN PLAN)
- **Blocking Tasks**: T004-T007 must complete before user story phases can start
- **Recommended MVP**: Complete Phases 1-3 for minimum viable setup (stories 1-2, basic Docker + Maven)
- **Full Scope**: All 8 phases for complete Module 1.A with seeding, CI/CD, documentation

---

## Next Steps

1. **Start Phase 1** (Setup): Create project structure, initialize git
2. **Complete Phase 2** (Foundational): Maven structure + ArchUnit tests (Day 1)
3. **Parallel Phases 3-5** (Stories 1-3): Docker, Maven profiles, Keycloak (Day 2)
4. **Phase 6** (Story 4): Seeding service with profile activation (Day 2-3)
5. **Phase 7** (Story 5): CI/CD pipeline templates (Day 3)
6. **Phase 8** (Polish): Documentation + final validation (Day 3 end)

**Ready to start**: Yes — All requirements clarified, all 4 ambiguities resolved, all dependencies documented.

