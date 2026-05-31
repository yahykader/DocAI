# Quickstart: Module 1.B — CI/CD & Standards

**Date**: 2026-05-29

---

## Prerequisites

- Docker & Docker Compose installed
- Java 21 JDK
- Maven 3.9+
- `kubectl` configured against the staging cluster (for Étape 4–5 only)

---

## Local Development (All Services)

```bash
# Start full infrastructure including Unleash
docker compose up -d

# Verify all services healthy (including new unleash + unleash-db)
docker compose ps
# Expected: all services show "healthy" or "running"

# Unleash UI
open http://localhost:4242
# Default credentials: admin / unleash4all
```

---

## Run Application Locally

```bash
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -pl docai-bootstrap
```

Application starts at `http://localhost:8080`  
Management/metrics at `http://localhost:9091/actuator`

---

## Verify Feature Flags

```bash
# Check Unleash API (all 6 flags registered)
curl http://localhost:4242/api/client/features \
  -H "Authorization: *:*.unleash-insecure-api-token" \
  | python -m json.tool | grep '"name"'

# Expected output:
# "name": "billing.enabled"
# "name": "fraud.v2.enabled"
# "name": "extraction.mistral.enabled"
# "name": "dashboard.search.enabled"
# "name": "notifications.inapp.enabled"
# "name": "maintenance.mode"
```

Toggle a flag via UI: `http://localhost:4242` → Feature Toggles → `billing.enabled` → Enable → change takes effect within 15 seconds (polling interval).

---

## Verify Observability

```bash
# 1. Prometheus metrics
curl http://localhost:9091/actuator/prometheus | grep http_server_requests

# 2. Structured JSON log (requires staging profile)
SPRING_PROFILES_ACTIVE=staging mvn spring-boot:run -pl docai-bootstrap 2>&1 | head -5
# Each line should be JSON with "traceId" and "tenantId" fields

# 3. Grafana dashboards
open http://localhost:3000
# Login: admin / admin
# Explore → Tempo → search for traces in last 5 minutes

# 4. Grafana Tempo direct trace search
curl http://localhost:3200/api/search?limit=5
```

---

## Run Checkstyle Validation

```bash
cd backend
MAVEN_OPTS=-Xmx512m mvn checkstyle:check -P quality-gates
# Expected: BUILD SUCCESS, 0 violations
```

---

## Build Docker Image Locally

```bash
cd backend
docker build -t docai-backend:local .

# Verify image size < 300 MB
docker images docai-backend:local --format "Size: {{.Size}}"

# Verify non-root user
docker run --rm docai-backend:local whoami
# Expected: docai

# Run locally (requires infra up)
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=local \
  -e MONGODB_URI=mongodb://admin:password@host.docker.internal:27017/docai?authSource=admin&replicaSet=rs0 \
  -p 8080:8080 -p 9091:9091 \
  docai-backend:local
```

---

## Run CI Jobs Locally

```bash
cd backend

# Job 1: Unit tests + ArchUnit
MAVEN_OPTS=-Xmx512m mvn clean test -P unit-tests

# Job 2: Integration tests
TESTCONTAINERS_REUSE_ENABLE=true MAVEN_OPTS=-Xmx512m mvn clean verify -P integration-tests

# Job 3 (quality gates + sonarcloud — needs SONAR_TOKEN)
MAVEN_OPTS=-Xmx1g mvn verify sonar:sonar -P quality-gates \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.organization=your-org \
  -Dsonar.projectKey=docai
```

---

## Apply Kubernetes Manifests (Staging)

```bash
# Dry run first (uses Kustomize overlay — k8s/ root contains kustomization.yaml)
kubectl apply --dry-run=client -k k8s/overlays/staging

# Apply (requires KUBECONFIG configured for staging)
kubectl apply -k k8s/overlays/staging

# Verify rollout
kubectl rollout status deployment/docai-backend --timeout=120s

# Check HPA
kubectl get hpa docai-backend
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `unleash` service not starting | Check `unleash-db` is healthy first: `docker compose logs unleash-db` |
| Feature flags not updating | Polling interval is 15s; check Unleash SDK logs for connection errors |
| Docker image > 300 MB | Ensure `dependency:go-offline` stage cached; check for unneeded fat JARs in build stage |
| Checkstyle fails after adding new class | Class > 200 lines → extract methods/inner classes; method > 20 lines → extract helper |
| Trivy blocks valid image | Update base image `eclipse-temurin:21-jre-alpine` to latest patch: `docker pull eclipse-temurin:21-jre-alpine` |
| OOM in CI job | Verify `MAVEN_OPTS=-Xmx512m` is set; if job 3, use `-Xmx1g` |
