# Tasks: Stack Technique & Intégrations DocAI (Module B)

**Input**: `specs/003-stack-technique/` — spec.md · plan.md · data-model.md · research.md · contracts/application-yml.md  
**Prérequis**: Module 1.A (Setup Projet) terminé — `./mvnw clean compile → BUILD SUCCESS`  
**Durée estimée**: 3h à 4h (demi-journée)  
**ADR non négociables**: ADR-002 · ADR-003 · ADR-006  
**Tests**: Inclus pour JitterTtl (TDD obligatoire Gate 3) et ArchUnit (FR-016b)

## Format: `[ID] [P?] [Story] Description avec chemin exact`

- **[P]**: Parallélisable (fichiers indépendants, aucune dépendance incomplète)
- **[Story]**: User Story de rattachement (US1–US4)
- Paths relatifs à la racine du dépôt

---

## Phase 1 : Fondation (Prérequis bloquant)

**Purpose**: Valider que Module 1.A est opérationnel avant toute modification

**⚠️ CRITIQUE** : Aucune tâche US ne commence tant que ce checkpoint n'est pas validé.

- [ ] T001 Vérifier Module 1.A — exécuter `cd backend && ./mvnw clean compile 2>&1 | tail -3` et confirmer `BUILD SUCCESS` sans erreur de dépendance

**Checkpoint**: `BUILD SUCCESS` attesté → User Stories peuvent démarrer en parallèle.

---

## Phase 2 : User Story 1 — Stack Technique correcte (Priority: P1) 🎯 MVP

**Goal**: POM parent avec versions exactes + schémas Avro auto-générés par `maven-avro-plugin`

**Independent Test**: `cd backend && ./mvnw clean compile -pl docai-adapter-out-kafka` → classes Avro générées dans `target/generated-sources/avro/`

### Implementation US1

- [ ] T002 [US1] Mettre à jour `<apicurio.version>` de `2.4.15.Final` à `2.6.5.Final` dans `backend/pom.xml` (propriété BOM ligne ~64) — résout CHK035
- [ ] T003 [US1] Ajouter `org.apache.avro:avro-maven-plugin:${avro.version}` en phase `generate-sources` avec `<sourceDirectory>${project.basedir}/src/main/avro/</sourceDirectory>` dans `backend/docai-adapter-out-kafka/pom.xml`
- [ ] T004 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentUploadedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId` (string), `tenantId` (string), `uploadedAt` (long logicalType=timestamp-millis), `s3Key` (string)
- [ ] T005 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentClassifiedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId`, `tenantId`, `documentType` (string), `confidence` (float), `classifiedAt` (long)
- [ ] T006 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentExtractedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId`, `tenantId`, `extractedFields` (map<string,string>), `extractedAt` (long)
- [ ] T007 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentValidatedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId`, `tenantId`, `validationStatus` (string), `validationResults` (array<ValidationResult>), `validatedAt` (long)
- [ ] T008 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentFraudAnalyzedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId`, `tenantId`, `fraudScore` (int 0-100), `riskLevel` (enum RiskLevel), `signals` (array<FraudSignal>), `occurredAt` (long) — topic: `docai.doc.fraud.analyzed` (Constitution autorité)
- [ ] T009 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentCompletedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId`, `tenantId`, `finalStatus` (enum), `occurredAt` (long)
- [ ] T010 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/DocumentFailedEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `documentId`, `tenantId`, `failureStage` (enum), `errorCode` (enum), `occurredAt` (long) — clé partition: `tenantId` (exception ADR-002)
- [ ] T011 [P] [US1] Créer `backend/docai-adapter-out-kafka/src/main/avro/OutboxRelayEvent.avsc` — namespace `fr.docai.kafka.events`, champs: `outboxId` (string), `aggregateId` (string), `eventType` (string), `payload` (bytes), `occurredAt` (long)
- [ ] T012 [US1] Exécuter `cd backend && ./mvnw clean compile -pl docai-adapter-out-kafka` — vérifier que les 8 classes `.java` sont générées sous `docai-adapter-out-kafka/target/generated-sources/avro/fr/docai/kafka/events/`

**Checkpoint US1**: `apicurio.version=2.6.5.Final` dans pom.xml + 8 fichiers `.avsc` présents + compile sans erreur.

---

## Phase 3 : User Story 2 — Topologie Kafka correcte (Priority: P1)

**Goal**: 8 Consumer Group IDs dans `application.yml`, jamais hardcodés en annotation Java + règle ArchUnit active

**Independent Test**: `./mvnw test -pl docai-bootstrap -Dtest=HexagonalArchitectureTest` → 0 violation `@KafkaListener` avec `groupId` littéral

### Implementation US2

- [ ] T013 [US2] Vérifier dans `backend/docai-bootstrap/src/main/resources/application.yml` que `spring.kafka.consumer.group-id: docai-group` est absent (supprimé) — son remplacement est la section `kafka.groups.*`
- [ ] T014 [US2] Confirmer que les 8 Consumer Group IDs sont définis dans `kafka.groups.*` de `backend/docai-bootstrap/src/main/resources/application.yml` avec la convention `docai.{module}.{name}.group` :
  ```
  kafka.groups.upload: docai.upload.processor.group
  kafka.groups.classification: docai.classification.processor.group
  kafka.groups.extraction.llm: docai.extraction.llm.group
  kafka.groups.extraction.ocr: docai.extraction.ocr.group
  kafka.groups.validation: docai.validation.processor.group
  kafka.groups.fraud.analyser: docai.fraud.analyser.group
  kafka.groups.pipeline.orchestrator: docai.pipeline.orchestrator.group
  kafka.groups.outbox.relay: docai.outbox.relay.group
  ```
  Note CHK011: Le user input mentionnait 10 groupes — les 8 groupes ci-dessus sont alignés sur les 8 topics du spec FR-013 ; les 2 groupes supplémentaires (dashboard, notification) appartiennent aux modules 4-5 hors scope Module B.
- [ ] T015 [US2] Ajouter ou vérifier la règle ArchUnit `noKafkaListenerLiteralGroupId` dans `backend/docai-bootstrap/src/test/java/fr/docai/bootstrap/arch/HexagonalArchitectureTest.java` :
  Règle : toute méthode annotée `@KafkaListener` dont l'attribut `groupId` est non vide et ne commence pas par `${` → FAIL (FR-016b)
- [ ] T016 [US2] Exécuter `cd backend && ./mvnw test -pl docai-bootstrap -Dtest=HexagonalArchitectureTest` — vérifier `BUILD SUCCESS` (0 violation groupId littéral)

**Checkpoint US2**: 8 Consumer Group IDs en `application.yml` · `spring.kafka.consumer.group-id` absent · règle ArchUnit passe au vert.

---

## Phase 4 : User Story 3 — Cache Valkey sans Thunder Herd (Priority: P2)

**Goal**: Classe `JitterTtl` avec tests TDD + 9 stratégies de cache documentées dans `application.yml`

**Independent Test**: `./mvnw test -pl docai-adapter-out-valkey` → `JitterTtlTest` 100% vert + `docker exec docai_valkey valkey-cli ping` → `PONG`

### Tests pour US3 (TDD — écrire AVANT l'implémentation)

> **⚠️ NOTE**: Écrire ces tests en premier — ils doivent ÉCHOUER avant l'implémentation de `JitterTtl`

- [ ] T017 [P] [US3] Créer `backend/docai-adapter-out-valkey/src/test/java/fr/docai/adapter/out/valkey/util/JitterTtlTest.java` avec 6 cas de test :
  1. `withJitter(Duration.ofHours(24))` retourne valeur ∈ `[21.6h, 26.4h]` sur 1000 itérations
  2. `withJitter(Duration.ZERO)` retourne `Duration.ZERO`
  3. `withJitter(null)` lève `NullPointerException`
  4. `withJitter(Duration.ofMinutes(30))` retourne valeur ∈ `[27m, 33m]` (±10%)
  5. `withJitter(duration, 0.25)` retourne valeur ∈ `[base*0.75, base*1.25]`
  6. `withJitter(duration, 0.26)` lève `IllegalArgumentException` (borne supérieure jitterFactor)

### Implementation US3

- [ ] T018 [US3] Créer `backend/docai-adapter-out-valkey/src/main/java/fr/docai/adapter/out/valkey/util/JitterTtl.java` — classe `final`, constructeur privé, méthodes statiques `withJitter(Duration base)` et `withJitter(Duration base, double jitterFactor)`, utilise `ThreadLocalRandom.current()`, guard `jitterFactor ∈ [0.0, 0.25]` sinon `IllegalArgumentException`
- [ ] T019 [US3] Exécuter `cd backend && ./mvnw test -pl docai-adapter-out-valkey -Dtest=JitterTtlTest` — vérifier que les 6 tests passent au vert
- [ ] T020 [US3] Confirmer les TTL corrects dans `backend/docai-bootstrap/src/main/resources/application.yml` section `docai.cache.*` — valeurs conformes à la Constitution et data-model.md (résout CHK014) :
  ```yaml
  docai.cache.extraction-llm-ttl: 24h     # (spec FR-020 erroné : 1h — corriger à 24h)
  docai.cache.insee-siret-ttl: 7d         # (spec FR-020 erroné : 24h — corriger à 7d)
  docai.cache.ban-address-ttl: 30d        # (spec FR-020 erroné : 7j — corriger à 30d)
  docai.cache.rpps-practitioner-ttl: 7d   # (spec FR-020 erroné : 12h — corriger à 7d)
  docai.cache.classification-result-ttl: 30m
  docai.cache.idempotence-ttl: 24h        # TTL fixe — exception ADR-003
  # quota TTL: fin de mois (calculé dynamiquement) — exception ADR-003
  # jwt-blacklist TTL: = expiration token (calculé dynamiquement) — exception ADR-003
  ```
- [ ] T021 [US3] Corriger les TTL erronés dans `specs/003-stack-technique/spec.md` §FR-020 : mettre à jour les stratégies 1–4 avec les valeurs correctes (LLM→24h, INSEE→7j, BAN→30j, RPPS→7j) — résout CHK014
- [ ] T022 [US3] Vérifier la connexion Valkey : `docker exec docai_valkey valkey-cli ping` → réponse `PONG`

**Checkpoint US3**: `JitterTtlTest` 100% vert · TTL corrects dans `application.yml` · Valkey `PONG`.

---

## Phase 5 : User Story 4 — Résilience Resilience4j par service (Priority: P2)

**Goal**: 7 instances Resilience4j actives avec seuils exacts Constitution Annex A + JWKS TTL 1h (ADR-006)

**Independent Test**: `curl -s http://localhost:9091/actuator/health` → `"circuitBreakers": { "llm": {"status":"UP"}, ... }` — toutes instances `CLOSED`

### Implementation US4

- [ ] T023 [US4] Vérifier dans `backend/docai-bootstrap/src/main/resources/application.yml` que les 4 sections Resilience4j (`circuitbreaker`, `retry`, `bulkhead`, `timelimiter`) existent pour les 7 instances : `llm`, `tika`, `opencv`, `insee`, `ban`, `rpps`, `s3`
- [ ] T024 [US4] Vérifier les seuils LLM dans `backend/docai-bootstrap/src/main/resources/application.yml` :
  - `circuitbreaker.llm.failureRateThreshold: 50`
  - `circuitbreaker.llm.slidingWindowSize: 10`
  - `circuitbreaker.llm.waitDurationInOpenState: 60s`
  - `circuitbreaker.llm.permittedNumberOfCallsInHalfOpenState: 3`
  - `circuitbreaker.llm.automaticTransitionFromOpenToHalfOpenEnabled: true`
  - `retry.llm.maxAttempts: 3` + `enableExponentialBackoff: true` + `exponentialBackoffMultiplier: 2`
  - `bulkhead.llm.maxConcurrentCalls: 20`
  - `timelimiter.llm.timeoutDuration: 30s` + `cancelRunningFuture: true`
- [ ] T025 [US4] Vérifier les seuils Tika/OpenCV (BR-VIS-003) dans `backend/docai-bootstrap/src/main/resources/application.yml` — `timelimiter.tika.timeoutDuration: 15s` et `timelimiter.opencv.timeoutDuration: 15s` avec `cancelRunningFuture: true`
- [ ] T026 [US4] Vérifier que `spring.security.oauth2.resourceserver.jwt.jwks-cache-ttl: 1h` est présent dans `backend/docai-bootstrap/src/main/resources/application.yml` (ADR-006 — défaut Spring = 5min, insuffisant)
- [ ] T027 [US4] Démarrer Spring Boot (`cd backend && ./mvnw spring-boot:run -pl docai-bootstrap`) et exécuter `curl -s http://localhost:9091/actuator/health | python -m json.tool` — confirmer que `components.circuitBreakers` liste les 7 instances en état `UP` (= `CLOSED`)
- [ ] T028 [US4] Vérifier le seuil LLM via Actuator : `curl -s http://localhost:9091/actuator/circuitbreakerevents?name=llm` — confirmer que le CB répond sans erreur de configuration

**Checkpoint US4**: Spring Boot démarre sans erreur de binding Resilience4j · 7 instances `CLOSED` · JWKS TTL 1h configuré.

---

## Phase 6 : Polish & Cohérence inter-artefacts

**Purpose**: Corrections croisées identifiées par le checklist — résolution des conflits bloquants restants

- [ ] T029 [P] Corriger les URLs Actuator dans `specs/003-stack-technique/quickstart.md` — remplacer `http://localhost:8080/api/actuator/health` par `http://localhost:9091/actuator/health` (résout CHK034)
- [ ] T030 [P] Résoudre le conflit CHK017/CHK036 — décider localisation canonique de `JitterTtl` : soit (a) créer le module `docai-commons` dans `backend/pom.xml` <modules> et déplacer `JitterTtl.java`, soit (b) confirmer `docai-adapter-out-valkey` et corriger `specs/003-stack-technique/spec.md` §Assumptions + `data-model.md` §Entité 4 pour supprimer la mention `docai-commons`
- [ ] T031 [P] Mettre à jour le header de `specs/003-stack-technique/checklists/stack-technique.md` — cocher les items résolus (CHK014, CHK034, CHK035) et indiquer les items restants à reporter sur Module 3 (CHK007, CHK009, CHK020)
- [ ] T032 Exécuter `cd backend && ./mvnw clean compile` (build complet, tous modules) — vérifier `BUILD SUCCESS` final

**Checkpoint Final**: Build complet · checklist mise à jour · conflits CHK014/CHK034/CHK035 résolus.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Fondation (Phase 1)**: Aucune dépendance — démarrage immédiat
- **US1 (Phase 2)**: Dépend de Phase 1 — bloque T003–T011 si compile échoue
- **US2 (Phase 3)**: Dépend de Phase 1 — indépendant de US1 (fichiers différents)
- **US3 (Phase 4)**: Dépend de Phase 1 — T017 (tests) doit précéder T018 (implémentation)
- **US4 (Phase 5)**: Dépend de Phase 1 — requiert Spring Boot fonctionnel pour T027/T028
- **Polish (Phase 6)**: Dépend de US1–US4 complétés

### User Story Dependencies

- **US1 (P1)**: Démarre après Phase 1 — indépendant des autres US
- **US2 (P1)**: Démarre après Phase 1 — indépendant de US1 (peut être parallèle)
- **US3 (P2)**: Démarre après Phase 1 — indépendant de US1/US2
- **US4 (P2)**: Démarre après Phase 1 — requiert `application.yml` compilable (issu de US1/US2)

### Within Each User Story

- TDD (US3) : T017 (tests) DOIT être écrit et ÉCHOUER avant T018 (implémentation)
- Avro (US1) : T003 (plugin) DOIT précéder T012 (compile check)
- Resilience4j (US4) : T023–T026 (config vérification) AVANT T027–T028 (démarrage)

---

## Parallel Opportunities

```bash
# Phase 2 — Créer les 8 fichiers .avsc en parallèle :
Task T004: DocumentUploadedEvent.avsc
Task T005: DocumentClassifiedEvent.avsc
Task T006: DocumentExtractedEvent.avsc
Task T007: DocumentValidatedEvent.avsc
Task T008: DocumentFraudDetectedEvent.avsc
Task T009: DocumentCompletedEvent.avsc
Task T010: DocumentFailedEvent.avsc
Task T011: OutboxRelayEvent.avsc

# Phase 4 — TDD JitterTtl (test en parallèle de l'implémentation si 2 devs) :
Task T017: JitterTtlTest.java (dev A)
Task T018: JitterTtl.java    (dev B — après T017 committé et rouge)

# Phase 6 — Polish indépendant :
Task T029: quickstart.md fixes
Task T030: JitterTtl localisation
Task T031: checklist mise à jour
```

---

## Implementation Strategy

### MVP (Phase 1 + Phase 2 — US1)

1. Valider Module 1.A (T001)
2. Corriger `apicurio.version` (T002) + `maven-avro-plugin` (T003)
3. Créer les 8 fichiers `.avsc` (T004–T011) en parallèle
4. **STOP et VALIDE**: `./mvnw clean compile -pl docai-adapter-out-kafka → BUILD SUCCESS`
5. Demo: 8 classes Avro générées + apicurio 2.6.5.Final

### Livraison Incrémentale

1. Phase 1 + US1 → Foundation Avro prête
2. US2 → Consumer Groups conformes ADR-002
3. US3 → Cache Valkey sans Thunder Herd (JitterTtl)
4. US4 → Résilience complète (8 instances Resilience4j)
5. Phase 6 → Cohérence inter-artefacts résolue

### Stratégie Parallèle (2 développeurs)

- Dev A: US1 (POM + Avro) → US3 (JitterTtl)
- Dev B: US2 (Kafka Groups) → US4 (Resilience4j)
- Merge: Phase 6 ensemble

---

## Notes

- `[P]` = fichiers différents, aucune dépendance — parallélisable
- **CHK014** (TTL erronés dans spec FR-020) : corrigé par T020 + T021
- **CHK034** (port Actuator quickstart) : corrigé par T029
- **CHK035** (apicurio.version pom.xml) : corrigé par T002
- **CHK017/CHK036** (JitterTtl localisation) : décision requise dans T030
- **CHK007/CHK009/CHK020** : reporter sur Module 3 (topics naming, Tess4J OCR instance)
- Committer après chaque checkpoint de phase
- Vérifier avec `./mvnw clean test -P unit-tests` avant chaque PR
- Port Actuator : **9091** (jamais 8080) — séparation SEC-002
