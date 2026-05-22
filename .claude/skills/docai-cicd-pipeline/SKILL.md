---
name: docai-cicd-pipeline
description: Crée ou corrige les workflows CI/CD GitHub Actions DocAI (01-ci.yml, 02-docker.yml, 03-deploy-staging.yml, Dockerfile multi-stage, manifestes Kubernetes). Utiliser quand on demande de configurer le pipeline CI, un workflow GitHub Actions, le Dockerfile, les manifestes K8s, ou les Quality Gates SonarCloud.
---

# DocAI — Pipeline CI/CD

## Vue d'ensemble — 6 phases

```
git push → Phase 1 (Build+ArchUnit) → Phase 2 (Tests) → Phase 3 (SonarCloud)
         → Phase 4 (Docker+Trivy) → Phase 5 (Deploy) → Phase 6 (Docs)
```

## Déclencheurs par branche

| Événement | Tests | SonarCloud | Docker | Deploy |
|-----------|-------|-----------|--------|--------|
| PR → develop | ✅ | ✅ bloque PR | ❌ | ❌ |
| Push develop | ✅ | ✅ | ✅ | ✅ Staging auto |
| Tag v*.*.* | ✅ | ✅ | ✅ | ✅ Prod (approbation) |

## Quality Gates — seuils bloquants

| Condition | Conséquence |
|-----------|-------------|
| Violation ArchUnit | ❌ Pipeline arrêté |
| ≥ 1 test échoue | ❌ PR bloquée |
| Coverage global < 80% | ❌ PR bloquée |
| Coverage `docai-domain` < 90% | ❌ PR bloquée |
| ≥ 1 bug SonarCloud nouveau code | ❌ Merge bloqué |
| Vulnérabilité CRITICAL Docker | ❌ Image non publiée |

## 01-ci.yml — 3 jobs séparés (ADR-008 : éviter OOM GitHub Runner 7GB)

```yaml
name: CI — Build & Tests
on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop, main]

jobs:
  # Job 1 — Tests unitaires (sans Docker, JVM -Xmx512m)
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - name: Tests unitaires domaine
        env:
          MAVEN_OPTS: -Xmx512m -Xms256m    # ADR-008
        run: ./mvnw test -pl docai-domain,docai-application --no-transfer-progress

  # Job 2 — Tests intégration (TestContainers, reuse activé)
  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - name: Tests intégration
        env:
          MAVEN_OPTS: -Xmx512m -Xms256m
          TESTCONTAINERS_REUSE_ENABLE: true  # ADR-008 : reuse conteneurs
          AWS_ACCESS_KEY_ID: test
          AWS_SECRET_ACCESS_KEY: test
        run: |
          ./mvnw verify \
            -pl docai-adapter-out-mongodb,docai-adapter-out-kafka,docai-adapter-out-storage \
            -P integration-tests --no-transfer-progress

  # Job 3 — Tests BDD Cucumber
  bdd-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - name: Tests BDD
        env:
          MAVEN_OPTS: -Xmx512m -Xms256m
          TESTCONTAINERS_REUSE_ENABLE: true
          BILLING_ENABLED: "false"
        run: ./mvnw test -pl docai-bootstrap -Dtest=CucumberTestRunner --no-transfer-progress

  # Job 4 — Contract Tests Spring Cloud Contract
  contract-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - run: ./mvnw spring-cloud-contract:generateTests spring-cloud-contract:run

  # Job 5 — SonarCloud (après tous les tests)
  sonarcloud:
    runs-on: ubuntu-latest
    needs: [unit-tests, integration-tests, bdd-tests]
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - name: SonarCloud Analysis
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          ./mvnw verify sonar:sonar \
            -Dsonar.projectKey=${{ vars.SONAR_PROJECT_KEY }} \
            -Dsonar.organization=${{ vars.SONAR_ORGANIZATION }} \
            -Dsonar.host.url=https://sonarcloud.io \
            --no-transfer-progress
```

## 02-docker.yml — Build + Scan Trivy + Push GHCR

```yaml
name: Docker — Build, Scan & Push
on:
  push:
    branches: [develop, main]
    tags: ['v*.*.*']

jobs:
  docker:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      security-events: write
    steps:
      - uses: actions/checkout@v4
      - run: ./mvnw clean package -DskipTests --no-transfer-progress

      - name: Build Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: false
          tags: ghcr.io/${{ github.repository }}:${{ github.sha }}
          load: true

      - name: Scan Trivy
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository }}:${{ github.sha }}
          severity: CRITICAL,HIGH
          exit-code: 1          # CRITICAL bloque le pipeline

      - name: Push image
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: |
            ghcr.io/${{ github.repository }}:${{ github.sha }}
            ghcr.io/${{ github.repository }}:latest
```

## Dockerfile multi-stage (JRE 21 Alpine)

```dockerfile
# Stage 1 — Dépendances Maven (cache)
FROM eclipse-temurin:21-jdk-alpine AS dependencies
WORKDIR /build
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Stage 2 — Compilation
FROM dependencies AS build
COPY src ./src
RUN ./mvnw clean package -DskipTests --no-transfer-progress \
    && java -Djarmode=layertools -jar docai-bootstrap/target/*.jar extract

# Stage 3 — Runtime (~200MB)
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Sécurité : utilisateur non-root obligatoire
RUN addgroup -S docai && adduser -S docai -G docai
USER docai

COPY --from=build /build/dependencies/ ./
COPY --from=build /build/snapshot-dependencies/ ./
COPY --from=build /build/spring-boot-loader/ ./
COPY --from=build /build/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
```

## Manifestes Kubernetes — Points clés

```yaml
# deployment.yaml — RollingUpdate sans interruption (BR-K8S-001)
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0   # 0 pod down pendant le déploiement
    maxSurge: 1         # 1 pod supplémentaire pendant la transition

# hpa.yaml — Auto-scaling
minReplicas: 2          # Toujours 2 en prod (BR-K8S-004)
maxReplicas: 10
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

## Conventional Commits — format obligatoire

```bash
feat(recognition): add PDF classification support
fix(extraction): handle null LLM response
test(fraud): add BDD scenario for arithmetic signal
refactor(domain): extract ConfidenceScore value object
ci: add Trivy Docker image scanning step
perf(cache): increase Valkey TTL for LLM results
```

## Checklist

- [ ] 3 jobs CI séparés avec `MAVEN_OPTS=-Xmx512m` (ADR-008)
- [ ] `TESTCONTAINERS_REUSE_ENABLE=true` sur jobs integration et BDD
- [ ] Scan Trivy avec `exit-code: 1` sur CRITICAL
- [ ] Dockerfile avec utilisateur non-root `docai`
- [ ] `maxUnavailable: 0` dans RollingUpdate (BR-K8S-001)
- [ ] Minimum 2 replicas en production (BR-K8S-004)
- [ ] Secrets via AWS Secrets Manager CSI Driver — jamais dans manifestes
- [ ] Health check post-déploiement avant marquage SUCCESS
