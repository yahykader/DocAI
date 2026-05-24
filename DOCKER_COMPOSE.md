# DocAI Docker Compose Setup — ADR-002 & ADR-006

**Status**: ✅ Ready for local development  
**Last Updated**: 2026-05-24

---

## Overview

This `docker-compose.yml` provides a complete local development environment for DocAI with 11 services:
- **Database**: MongoDB 7.0 (Replica Set)
- **Message Queue**: Apache Kafka 3.7.0 (with Zookeeper)
- **Schema Registry**: Apicurio 2.6.0.Final
- **Cache**: Valkey 8
- **Authentication**: Keycloak 26.0
- **Observability**: Prometheus, Grafana, Grafana Tempo (with integrated OTLP)

All services include mandatory **healthchecks** and are networked on `docai-network`.

---

## Quick Start

### 1. Prerequisites

```bash
# Check Docker & Docker Compose installed
docker --version
docker compose --version

# Minimum versions
# Docker: 20.10+
# Docker Compose: 2.0+
```

### 2. Start All Services

```bash
# Start services in background
docker compose up -d

# Watch initialization
docker compose logs -f

# Verify all healthy
docker compose ps
```

**Expected output** (all services healthy):
```
NAME                      STATUS
docai-mongodb             healthy
docai-mongodb-init        exited (0)
docai-kafka               healthy
docai-zookeeper           healthy
docai-kafka-init          exited (0)
docai-kafka-ui            healthy
docai-apicurio            healthy
docai-valkey              healthy
docai-keycloak            healthy
docai-keycloak-db         healthy
docai-prometheus          healthy
docai-grafana             healthy
docai-tempo               healthy
```

### 3. Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| **API** | http://localhost:8080 | (will be served by Spring Boot) |
| **Swagger** | http://localhost:8080/swagger-ui.html | — |
| **Keycloak Admin** | http://localhost:8180/admin | admin / admin |
| **Keycloak Realm** | http://localhost:8180/realms/docai | — |
| **Kafka UI** | http://localhost:8090 | — |
| **Prometheus** | http://localhost:9090 | — |
| **Grafana** | http://localhost:3000 | admin / admin |
| **Tempo** | http://localhost:3200 | — |
| **MongoDB** | mongodb://localhost:27017 | admin / password |

---

## Service Details

### Database Layer

#### MongoDB 7.0 (Replica Set)
```yaml
Port: 27017
Connection: mongodb://admin:password@localhost:27017/docai?authSource=admin&replicaSet=rs0
Replica Set: rs0 (single-node for local dev)
Data Volume: mongodb_data
```

**Why Replica Set?** Required for transactions (multi-document ACID transactions in MongoDB).

**Healthcheck**: Pings MongoDB admin database.

#### mongodb-init
Initializes the MongoDB Replica Set automatically on first startup.
- Depends on `mongodb` service being healthy
- Runs `rs.initiate()` command once and exits
- Status: `exited (0)` when healthy

### Message Queue

#### Apache Kafka 3.7.0 + Zookeeper
```yaml
Bootstrap Server: localhost:9092 (host), kafka:29092 (container)
KRaft Broker ID: 1
Zookeeper: zookeeper:2181
Data Volumes: kafka_data, zookeeper_data, zookeeper_logs
```

**Healthcheck**: Checks broker API versions.

#### kafka-init
Creates 8 Kafka topics (ADR-002 — documentId as partition key):

| Topic | Partitions | Retention | Use Case |
|-------|-----------|-----------|----------|
| `docai.doc.uploaded` | 6 | 7 days | Document ingestion events |
| `docai.doc.classified` | 6 | 7 days | Document classification results |
| `docai.doc.extracted` | 6 | 7 days | Data extraction results |
| `docai.doc.fraud.analyzed` | 6 | 7 days | Fraud analysis results |
| `docai.doc.completed` | 3 | 30 days | Successfully processed documents |
| `docai.doc.failed` | 3 | 30 days | Failed document processing |
| `docai.doc.dlq` | 3 | 90 days | Dead letter queue (poison pills) |
| `docai.outbox.relay` | 3 | 1 day | Transactional outbox pattern |

**Partition Key**: `documentId` ensures all events for a document stay on the same partition (ordering guarantees).

**Status**: `exited (0)` when topics created successfully.

#### kafka-ui (provectuslabs)
Web UI for Kafka cluster management and monitoring.
```
Port: 8090
Cluster: docai
Schema Registry: http://apicurio:8081
```

### Schema Registry

#### Apicurio 2.6.0.Final
Kafka schema registry using PostgreSQL backend (in-memory for local).
```
Port: 8081
Kafka: kafka:29092
```

Used for Avro schema management in `docai-adapter-in-kafka` and `docai-adapter-out-kafka`.

### Cache Layer

#### Valkey 8
Redis-compatible caching engine (replaces Redis).
```
Port: 6379
Command: valkey-cli ping
Data Volume: valkey_data
```

Used by `docai-adapter-out-valkey` for distributed caching.

### Authentication

#### Keycloak 26.0 (ADR-006)
OpenID Connect/OAuth2 identity provider with automatic realm import.

**Configuration**:
```
Port: 8180
Realm: docai
Admin: admin / admin
Import File: realm-docai.json
```

**Clients** (auto-imported):
- `docai-backend` (service account) — Backend API
- `docai-frontend` (public client) — Angular SPA
- `docai-admin` (public client) — Admin console

**Test Users** (auto-imported):
- `admin@docai.local` / `Admin1234!` (realm roles: user, admin)
- `testuser@acme-corp.local` / `Test1234!` (realm roles: user)

**Protocol Mappers** (JWT claims):
- `tenant_id` — Multi-tenancy identifier
- `email` — Email address
- `given_name`, `family_name` — Name fields
- `roles` — Realm roles

**Healthcheck**: HTTP health endpoint.

#### keycloak-db (PostgreSQL 16)
Database backend for Keycloak.
```
Port: 5432
Database: keycloak
Username: keycloak / keycloak
Data Volume: keycloak_db_data
```

### Observability

#### Prometheus
Metrics scraper and time-series database.
```
Port: 9090
Config: prometheus.yml
Retention: 30 days
Data Volume: prometheus_data
```

**Scrape Targets**:
- `prometheus:9090` (self)
- `localhost:8080/actuator/prometheus` (Spring Boot metrics)
- `kafka:9999` (Kafka metrics)
- `mongodb-exporter:9216` (MongoDB metrics)

#### Grafana
Visualization and dashboarding platform.
```
Port: 3000
Admin: admin / admin
Data Volume: grafana_data
```

**Pre-configured Datasources**:
- Prometheus (default)
- Tempo (with service map, traces-to-metrics)

#### Grafana Tempo
Distributed tracing backend with integrated OTLP receivers.

```
Ports:
  3200 — HTTP UI
  4317 — OTLP gRPC receiver (OpenTelemetry)
  4318 — OTLP HTTP receiver (OpenTelemetry)
Config: tempo.yml
Data Volume: tempo_data
```

**Important**: No separate OTEL Collector needed. Tempo handles both gRPC (port 4317) and HTTP (port 4318) OTLP ingestion natively.

Spring Boot application sends traces to `http://localhost:4317` (configured in `application.yml`).

---

## Development Workflow

### Verify Setup

```bash
# Check all services healthy
docker compose ps

# View logs (follow mode)
docker compose logs -f

# View specific service logs
docker compose logs -f kafka
docker compose logs -f keycloak
```

### Start Spring Boot Application

```bash
# In separate terminal (services still running in background)
cd docai-bootstrap
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/docai-bootstrap-*.jar
```

### Access APIs

```bash
# Health check
curl http://localhost:8080/actuator/health

# Swagger UI
curl http://localhost:8080/swagger-ui.html

# Keycloak token endpoint
curl -X POST http://localhost:8180/realms/docai/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=docai-backend" \
  -d "client_secret=docai-backend-secret-change-in-prod" \
  -d "username=admin" \
  -d "password=Admin1234!" \
  | jq .

# Use token in API calls
TOKEN=$(curl -s -X POST ... | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/documents
```

### Kafka Topic Inspection

```bash
# List topics
docker compose exec kafka kafka-topics --list --bootstrap-server kafka:29092

# Consume messages
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic docai.doc.uploaded \
  --from-beginning

# Produce test message
docker compose exec kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic docai.doc.uploaded \
  --property "parse.key=true" \
  --property "key.separator=:"

# (paste: doc-123:{"documentId":"doc-123",...})
```

### MongoDB Data Inspection

```bash
# Connect with mongosh
mongosh 'mongodb://admin:password@localhost:27017/docai?authSource=admin'

# Common commands
show databases
use docai
show collections
db.documents.find()
db.documents.countDocuments()
```

### Grafana Dashboard Setup

1. Go to http://localhost:3000 (admin/admin)
2. Click **Connections** → **Data sources**
3. Verify `Prometheus` (green) and `Tempo` (green)
4. Create dashboards or import from Grafana marketplace

**Example Dashboard**: Import ID `3662` (Prometheus Node Exporter)

### Tempo Trace Visualization

1. Go to http://localhost:3200
2. Click **Explore**
3. Select **Tempo** datasource
4. Search traces by:
   - Service name
   - Duration
   - Tags (e.g., `tenant_id`)
5. View service graph and span details

---

## Configuration & Environment

### Environment Variables (.env)

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

**Key variables** (docker-compose.yml uses defaults, override in `.env` for Spring Boot):

```bash
# MongoDB
MONGODB_URI=mongodb://admin:password@localhost:27017/docai?authSource=admin&replicaSet=rs0

# Kafka
KAFKA_BROKERS=localhost:9092
KAFKA_SCHEMA_REGISTRY=http://localhost:8081

# Keycloak
KEYCLOAK_AUTH_SERVER_URL=http://localhost:8180
KEYCLOAK_REALM=docai
KEYCLOAK_CLIENT_ID=docai-backend
KEYCLOAK_CLIENT_SECRET=docai-backend-secret-change-in-prod

# Valkey
REDIS_HOST=localhost
REDIS_PORT=6379

# AWS S3 (local MinIO or real AWS)
AWS_REGION=eu-west-1
AWS_ACCESS_KEY_ID=minioadmin
AWS_SECRET_ACCESS_KEY=minioadmin
S3_BUCKET=docai-dev

# Observability
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_SERVICE_NAME=docai
```

### Prometheus Configuration

Edit `prometheus.yml` to add/remove scrape targets:

```yaml
scrape_configs:
  - job_name: 'spring-boot'
    static_configs:
      - targets: ['localhost:8080']
    scrape_interval: 5s
```

### Tempo Configuration

Edit `tempo.yml` for:
- OTLP receiver configuration
- Storage backend (local, S3, GCS, etc.)
- Retention policies

### Keycloak Realm Import

Automatic import from `realm-docai.json` on container startup.

To **update** realm:
1. Edit `realm-docai.json`
2. Restart Keycloak: `docker compose restart keycloak`
3. Verify via: http://localhost:8180/admin (admin/admin)

---

## Common Tasks

### Stop All Services

```bash
docker compose down

# Remove volumes (CAREFUL: deletes all data)
docker compose down -v
```

### Restart Specific Service

```bash
docker compose restart kafka
docker compose restart keycloak
```

### View Service Logs

```bash
# All services
docker compose logs -f

# Specific service (follow)
docker compose logs -f kafka

# Last N lines
docker compose logs --tail 50 mongodb
```

### Clean Up Everything

```bash
# Stop + remove containers + volumes
docker compose down -v

# Restart fresh
docker compose up -d
```

### Health Check Status

```bash
# Check individual service health
docker compose ps

# Test specific endpoint
docker compose exec kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

docker compose exec mongodb mongosh \
  --eval "db.adminCommand('ping')"
```

---

## Troubleshooting

### Services Not Starting

**Symptom**: `docker compose up -d` fails or services won't start.

**Solution**:
```bash
# Check logs
docker compose logs

# Verify Docker daemon
docker info

# Restart Docker daemon and try again
docker compose up -d
```

### MongoDB Replica Set Not Initialized

**Symptom**: MongoDB healthy but `mongodb-init` still running.

**Solution**:
```bash
# Force restart
docker compose down
docker compose up -d

# Or manually initialize
docker compose exec mongodb mongosh \
  --eval "rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'mongodb:27017'}]})"
```

### Kafka Topics Not Created

**Symptom**: `kafka-init` exited but topics missing.

**Solution**:
```bash
# Check logs
docker compose logs kafka-init

# Manually create topics
docker compose exec kafka kafka-topics --create \
  --bootstrap-server kafka:29092 \
  --topic docai.doc.uploaded \
  --partitions 6 \
  --replication-factor 1
```

### Port Conflicts

**Symptom**: `Error response from daemon: ... Address already in use`.

**Solution**:
```bash
# Find process using port (e.g., 8080)
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Or change docker-compose.yml ports
# Modify "8080:8080" to "8081:8080" etc.
```

### Keycloak Login Fails

**Symptom**: Login redirect loops or 401 errors.

**Solution**:
1. Verify realm imported: http://localhost:8180/admin → admin/admin
2. Check client config: Realm → docai → Clients → docai-backend
3. Verify redirect URI matches: `http://localhost:8080/*`
4. Check JWT claim `tenant_id` in test token:
   ```bash
   curl -s -X POST http://localhost:8180/realms/docai/protocol/openid-connect/token \
     ... | jq .access_token | jq -R 'split(".")[1] | @base64d | fromjson'
   ```

### Memory Issues

**Symptom**: Containers crash with OOM errors.

**Solution**:
```bash
# Check Docker resource limits
docker stats

# Reduce service memory usage in docker-compose.yml
# Or increase Docker Desktop memory limit
```

---

## Architecture Decisions

- **ADR-002**: 8 Kafka topics with `documentId` partition key ensures ordering per document
- **ADR-006**: Keycloak 26 with automatic realm import enables self-service multi-tenancy setup
- **Tempo Integration**: Built-in OTLP receivers (gRPC + HTTP) eliminate need for separate Collector
- **MongoDB Replica Set**: Single-node for local dev, scales to multi-node in production
- **Healthchecks**: Every service has health validation to ensure readiness before dependent services start

---

## Performance Tuning (Optional)

### Increase Kafka Partitions
```bash
docker compose exec kafka kafka-topics --alter \
  --bootstrap-server kafka:29092 \
  --topic docai.doc.uploaded \
  --partitions 12
```

### MongoDB Replica Set to Multi-Node

Edit `docker-compose.yml` to add secondary:
```yaml
mongodb-secondary:
  image: mongo:7.0
  environment:
    MONGO_INITDB_ROOT_USERNAME: admin
    MONGO_INITDB_ROOT_PASSWORD: password
  command: --replSet rs0 --bind_ip_all
```

Update `mongodb-init` to:
```javascript
rs.initiate({
  _id: 'rs0',
  members: [
    { _id: 0, host: 'mongodb:27017' },
    { _id: 1, host: 'mongodb-secondary:27017' }
  ]
})
```

### Prometheus Retention
Edit `prometheus.yml`:
```yaml
--storage.tsdb.retention.time=90d  # Increase from 30d
```

---

## Next Steps

1. **Start infrastructure**: `docker compose up -d`
2. **Configure Spring Boot**: Copy `.env.example` → `.env`
3. **Run backend tests**: `mvn clean test -P unit-tests`
4. **Start application**: `mvn spring-boot:run -pl docai-bootstrap`
5. **Access API**: http://localhost:8080/swagger-ui.html
6. **Monitor**: http://localhost:3000 (Grafana)

---

## References

- **Docker Compose**: https://docs.docker.com/compose/
- **Kafka**: https://kafka.apache.org/
- **Keycloak**: https://www.keycloak.org/
- **Grafana Tempo**: https://grafana.com/docs/tempo/
- **OpenTelemetry**: https://opentelemetry.io/

---

**Last Updated**: 2026-05-24  
**Status**: Ready for local development  
**ADR References**: ADR-002 (Kafka topics), ADR-006 (Keycloak realm import)
