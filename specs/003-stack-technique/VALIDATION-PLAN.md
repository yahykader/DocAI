# Plan de Validation - Corrections Dépendances

**Objectif**: Valider que les 3 corrections résolvent les violations sans créer de régressions  
**Durée Estimée**: 15-20 minutes  
**Environnement**: Docker Compose + Maven 3.9+  

---

## Pre-Flight Checklist

```bash
# 1. Vérifier l'état du repo
git status  # Aucune modification non-commited en dehors de backend/pom.xml
git diff backend/pom.xml

# 2. Vérifier les services Docker
docker compose ps
# Doit montrer: HEALTHY ou running pour tous les services

# 3. Vérifier la branche
git branch -v
# Doit montrer: 004-stack-technique
```

---

## Phase 1: Build & Dependency Resolution (3-5 min)

```bash
cd backend

# 1A. Clean & Compile domain (plus haute couverture requis)
echo "=== Phase 1A: Domain Model ==="
MAVEN_OPTS=-Xmx512m mvn clean compile -pl docai-domain -DskipTests

# 1B. Compile bootstrap (contient configuration Kafka/Apicurio)
echo "=== Phase 1B: Bootstrap Configuration ==="
MAVEN_OPTS=-Xmx512m mvn clean compile -pl docai-bootstrap -DskipTests

# 1C. Vérifier dépendances résolues
echo "=== Phase 1C: Dependency Tree ==="
mvn dependency:tree -Dincludes="io.apicurio:apicurio-registry-serde-avro" \
                     -Dincludes="io.github.resilience4j:resilience4j-spring-boot3" \
                     -Dincludes="org.bytedeco:javacv-platform"
```

**Résultats Attendus**:
- ✅ Compilation SUCCESS (0 errors, 0 warnings)
- ✅ apicurio-registry-serde-avro:3.0.1
- ✅ resilience4j-spring-boot3:2.4.2
- ✅ javacv-platform:1.5.11 + javacpp:1.5.11

---

## Phase 2: Unit Tests + ArchUnit (5-8 min)

```bash
cd backend

echo "=== Phase 2: Unit Tests & Architecture Validation ==="
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests
```

**Critical Success Criteria**:
- ✅ All *Test.java & *Tests.java pass
- ✅ ArchUnit 12 hexagonal rules validated
- ✅ Coverage: Global ≥80%, Domain ≥90%
- ✅ 0 Checkstyle violations

**Rapports Générés**:
- `target/jacoco.exec` - Coverage metrics
- `target/archunit-summary.txt` - Architecture validation
- Console output avec summary

---

## Phase 3: Integration Tests (5-10 min)

```bash
cd backend

echo "=== Phase 3: Integration Tests (Real Services) ==="
# Prérequis: docker compose up -d (tous services running)

MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests \
  -DskipUnitTests
```

**Critical Success Criteria**:
- ✅ Kafka 3.7.0 connects successfully
- ✅ Apicurio 3.0.1 Schema Registry integrates
- ✅ MongoDB Replica Set transactions work
- ✅ TestContainers provide isolation (no prod dependencies)
- ✅ All *IT.java & *ITs.java tests pass

**Spécifique à Apicurio 3.0**:
```bash
# Vérifier que le Schema Registry est accessible
curl -s http://localhost:8081/apicurio/actuator/health | jq .

# Résultat attendu:
# {
#   "status": "UP",
#   "components": {
#     "schemaRegistry": {"status": "UP"}
#   }
# }
```

---

## Phase 4: Quality Gates (8-15 min)

```bash
cd backend

echo "=== Phase 4: Code Quality, Mutations, SonarCloud ==="
MAVEN_OPTS=-Xmx1g mvn clean verify -P quality-gates
```

**Sub-phases**:

### 4A: Unit Tests (for coverage)
- Runs all *Test.java & *Tests.java
- Generates JaCoCo coverage reports

### 4B: Checkstyle
- Max 20-line methods
- Max 4 parameters
- Cyclomatic complexity ≤ 10

### 4C: PIT Mutation Testing (Domain ≥85%)
```
Mutation threshold calculation:
- Before: PIT score = X%
- After: PIT score = Y%
- Tolerance: ±2% (due to JavaCV stabilization)
```

### 4D: JaCoCo Coverage Report
```bash
# Générer rapports HTML
mvn jacoco:report
# Ouvrir: backend/target/site/jacoco/index.html
```

### 4E: SonarCloud
```bash
# Si SONAR_TOKEN disponible:
mvn sonar:sonar -Dsonar.projectKey=docai-backend \
               -Dsonar.organization=your-org \
               -Dsonar.host.url=https://sonarcloud.io \
               -Dsonar.login=${SONAR_TOKEN}
```

---

## Validation Spécifique par Correction

### ✅ Correction V1: Apicurio 3.0.1

**Test directs**:
```bash
# 1. Vérifier Schema Registry est accessible
curl -s http://localhost:8081/apicurio/actuator/health

# 2. Vérifier KRaft mode works with Apicurio
# (Test dans integration tests: ApicurioSchemaRegistryIT.java)
mvn test -pl docai-adapter-out-kafka -Dtest=*SchemaRegistry*

# 3. Vérifier avro serialization/deserialization
mvn test -pl docai-adapter-out-kafka -Dtest=*Avro*
```

**Expected Warnings** (NOT errors):
```
Apicurio 3.0 uses newer OpenTelemetry API
May see deprecation warnings about io.apicurio:apicurio-registry-client:2.x
These are NORMAL during 2→3 transition
```

---

### ✅ Correction V2: Resilience4j 2.4.2

**Test directs**:
```bash
# 1. Vérifier circuit breaker patterns
mvn test -pl docai-adapter-out-external -Dtest=*CircuitBreaker*

# 2. Vérifier retry policies
mvn test -pl docai-adapter-out-external -Dtest=*Retry*

# 3. Vérifier rate limiting with bucket4j
mvn test -pl docai-adapter-in-rest -Dtest=*RateLimit*
```

**Performance Improvement Check**:
```bash
# Run tests with timing
mvn test -pl docai-adapter-out-external -Dorg.slf4j.simpleLogger.defaultLogLevel=info

# Look for:
# - Retry backoff timing (exponential)
# - Circuit breaker transitions
# - Bulkhead isolation (if used)
```

---

### ✅ Correction V8: JavaCV 1.5.11 + Explicit javacpp

**Test directs**:
```bash
# 1. Vérifier dépendance unique
mvn dependency:tree -Dincludes="org.bytedeco:javacpp"
# Doit afficher EXACTEMENT 1 version (1.5.11)

# 2. Vérifier pas de conflicts
mvn dependency:tree | grep -i "javacpp\|javacv" | grep "\[1.5"

# 3. Test document processing avec JavaCV
mvn test -pl docai-adapter-out-ai -Dtest=*DocumentProcessing*

# 4. Test multi-platform loading
mvn test -pl docai-adapter-out-ai -Dtest=*NativeBinding*
```

**Expected Behavior**:
```
[INFO] Loading platform-specific FFmpeg bindings...
[INFO] Using system OS: Windows-11-x64 | macOS-x64 | Linux-x64
[INFO] javacpp loaded successfully
[INFO] OpenCV initialized (v4.x.x)
```

---

## Fichiers Critiques à Vérifier

```
Backend Structure (post-corrections):
backend/
├── pom.xml ✅ MODIFIÉ (3 versions updated)
├── docai-bootstrap/
│   ├── src/main/resources/
│   │   ├── application.yml (Apicurio config)
│   │   └── application-local.yml
│   └── pom.xml (Should reference parent properties)
├── docai-adapter-out-kafka/
│   └── src/test/java/*SchemaRegistryIT.java (Apicurio tests)
└── docai-adapter-out-ai/
    └── src/test/java/*DocumentProcessingIT.java (JavaCV tests)
```

---

## Rollback Plan (si needed)

Si les corrections causent des régressions:

```bash
# 1. Revert le pom.xml
git checkout backend/pom.xml

# 2. Rebuild
cd backend
mvn clean compile -DskipTests

# 3. Rapporter le problème avec:
git diff HEAD -- backend/pom.xml  # Save current state
mvn dependency:tree > /tmp/deps.txt
java -version
mvn -version
```

---

## Métriques à Tracker

**Before Corrections**:
```
- Build time: X seconds
- PIT Mutation score: Y%
- Test count: Z
- Coverage: A%
```

**After Corrections**:
```
- Build time: X' seconds (should be ±5%)
- PIT Mutation score: Y'% (should be ±2%)
- Test count: Z' (should be same)
- Coverage: A'% (should be same)
```

---

## Commandes Rapides (Copier-Coller)

```bash
cd backend

# Full validation in one command
echo "=== PHASE 1: BUILD ===" && \
MAVEN_OPTS=-Xmx512m mvn clean compile && \
echo "=== PHASE 2: UNIT TESTS ===" && \
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests && \
echo "=== PHASE 3: INTEGRATION ===" && \
MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests && \
echo "=== PHASE 4: QUALITY GATES ===" && \
MAVEN_OPTS=-Xmx1g mvn clean verify -P quality-gates && \
echo "=== ✅ ALL VALIDATIONS PASSED ===" && \
git diff --stat backend/pom.xml
```

---

## Expected Success Output

```
[INFO] ========================================
[INFO] BUILD SUCCESS
[INFO] ========================================
[INFO] Total time: XX s
[INFO] Finished at: 2026-05-26T14:XX:XX+02:00
[INFO]
[INFO] Apicurio 3.0.1: ✅ RESOLVED
[INFO] Resilience4j 2.4.2: ✅ RESOLVED  
[INFO] JavaCV 1.5.11: ✅ RESOLVED
[INFO]
[INFO] Architecture: 12/12 ArchUnit rules passed
[INFO] Coverage: Domain 9X.X% | Global 8X.X%
[INFO] Tests: YYY passed, 0 failed
[INFO] Mutations: ZZ.Z% (≥85% threshold)
```

---

## Next Steps (Post-Validation)

1. ✅ All validations pass → **Create PR**
2. Create commit with message:
   ```
   fix(deps): upgrade apicurio, resilience4j, manage javacpp conflicts
   
   - Apicurio: 2.4.15 → 3.0.1 (Kafka 3.7 compatibility)
   - Resilience4j: 2.3.0 → 2.4.2 (SB 4.0 optimization)
   - JavaCV: 1.5.11 + explicit javacpp (transitive conflict resolution)
   
   Fixes violations V1, V2, V8 from stack technique audit
   ```

3. Push to `004-stack-technique`
4. Create PR against `main` with CI/CD validation

---

**Last Updated**: 2026-05-26  
**Status**: READY FOR VALIDATION  
