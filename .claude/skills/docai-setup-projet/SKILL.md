---
name: docai-setup-projet
description: "Setup complet du projet DocAI — structure Maven 11 modules, Docker Compose (11 services locaux), variables d'environnement .env.example, configuration Keycloak realm-docai.json (5 rôles, 5 utilisateurs de test, mappers JWT), commandes de démarrage, vérification de l'installation, Definition of Done du setup. Utiliser quand on demande de créer le projet DocAI from scratch, configurer l'infrastructure locale, configurer Keycloak, écrire le docker-compose.yml, ou vérifier que l'installation est correcte."
---

# DocAI — Setup Projet
## Structure Maven · Docker Compose · Keycloak · Variables d'environnement

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 2 (Module 2.A)
> **Prérequis :** Lire `docai-architecture-adr` avant ce skill.

---

## 1. Prérequis système

| Prérequis | Version minimale | Vérification |
|-----------|-----------------|-------------|
| Java | **21 LTS** | `java -version` → `21.x.x` |
| Maven | 3.9+ | `mvn -version` |
| Docker Engine | 24+ | `docker -v` |
| Docker Compose | v2+ | `docker compose version` |
| RAM disponible | **8 GB** | Pour tous les services en parallèle |

**Ports requis :** 6379 (Valkey), 8080 (App), 8081 (Apicurio), 8090 (Kafka UI), 8180 (Keycloak), 9000 (MinIO API), 9001 (MinIO Console), 9090 (Prometheus), 9092/9094 (Kafka), 27017 (MongoDB), 3000 (Grafana), 3200 (Tempo), 4317 (OTEL Collector)

---

## 2. Structure Maven Multi-Modules

```
docai-parent/
├── pom.xml                          ← POM parent
├── .env.example                     ← Template variables (versionné dans Git)
├── .env                             ← Variables réelles (dans .gitignore)
├── docker-compose.yml
├── Dockerfile
├── sonar-project.properties
├── .github/
│   ├── workflows/
│   │   ├── 01-ci.yml
│   │   ├── 02-docker.yml
│   │   ├── 03-deploy-staging.yml
│   │   ├── 04-deploy-production.yml
│   │   └── 05-documentation.yml
│   ├── pull_request_template.md
│   └── dependabot.yml
├── k8s/
│   ├── base/                        (deployment.yaml, service.yaml, hpa.yaml, ingress.yaml)
│   ├── staging/
│   └── production/
├── infra/terraform/
│   ├── modules/ (s3-bucket, mongodb-atlas, keycloak-realm, kafka-cloud)
│   └── environments/ (staging, production)
├── docker/
│   ├── keycloak/realm-docai.json    ← Versionné dans Git
│   ├── prometheus/prometheus.yml
│   ├── tempo/tempo.yml
│   └── grafana/
│       └── provisioning/
├── docai-domain/
├── docai-application/
├── docai-adapter-in-rest/
├── docai-adapter-in-kafka/
├── docai-adapter-out-mongodb/
├── docai-adapter-out-kafka/
├── docai-adapter-out-valkey/
├── docai-adapter-out-ai/
├── docai-adapter-out-storage/
├── docai-adapter-out-external/
└── docai-bootstrap/
    └── src/main/resources/
        ├── application.yml
        ├── application-dev.yml
        ├── application-prod.yml
        └── email-templates/fr/  (19 templates HTML + texte brut)
```

---

## 3. Services Docker Compose — 11 services

```yaml
# docker-compose.yml — tous les services locaux DocAI
services:
  mongodb:
    image: mongo:7.0
    container_name: docai-mongodb
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_ROOT_USER:-docai_root}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD:-docai_secret_local}
    command: ["--replSet", "rs0", "--bind_ip_all"]
    ports: ["27017:27017"]
    volumes:
      - mongodb_data:/data/db
    healthcheck:
      test: echo 'db.runCommand("ping").ok' | mongosh -u docai_root -p docai_secret_local --authenticationDatabase admin --quiet
      interval: 10s
      timeout: 5s
      retries: 5

  # Initialiser le Replica Set MongoDB (requis pour les transactions)
  mongodb-init:
    image: mongo:7.0
    depends_on: [mongodb]
    command: >
      mongosh --host mongodb:27017 -u docai_root -p docai_secret_local
      --authenticationDatabase admin
      --eval "rs.initiate({_id:'rs0', members:[{_id:0, host:'mongodb:27017'}]})"

  kafka:
    image: apache/kafka:3.7.0
    container_name: docai-kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:9094
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
    ports: ["9092:9092", "9094:9094"]

  # Création des 8 topics Kafka au démarrage
  kafka-init:
    image: apache/kafka:3.7.0
    depends_on: [kafka]
    command: >
      bash -c "
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.uploaded --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.classified --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.extracted --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.fraud.analyzed --partitions 6 --replication-factor 1 --config retention.ms=604800000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.completed --partitions 3 --replication-factor 1 --config retention.ms=2592000000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.failed --partitions 3 --replication-factor 1 --config retention.ms=2592000000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.doc.dlq --partitions 3 --replication-factor 1 --config retention.ms=7776000000 &&
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic docai.outbox.relay --partitions 3 --replication-factor 1 --config retention.ms=86400000
      "

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: docai-kafka-ui
    depends_on: [kafka]
    ports: ["8090:8080"]
    environment:
      KAFKA_CLUSTERS_0_NAME: docai-local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092

  apicurio:
    image: apicurio/apicurio-registry:2.6.0.Final
    container_name: docai-apicurio
    ports: ["8081:8080"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 10s

  valkey:
    image: valkey/valkey:8
    container_name: docai-valkey
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "valkey-cli", "ping"]
      interval: 10s

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    container_name: docai-keycloak
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin123
    volumes:
      - ./docker/keycloak/realm-docai.json:/opt/keycloak/data/import/realm-docai.json
    ports: ["8180:8080"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 15s

  minio:
    image: minio/minio:latest
    container_name: docai-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-docai_minio}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-docai_minio_secret}
    ports: ["9000:9000", "9001:9001"]
    volumes: [minio_data:/data]

  prometheus:
    image: prom/prometheus:latest
    container_name: docai-prometheus
    ports: ["9090:9090"]
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    container_name: docai-grafana
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin123
    volumes:
      - ./docker/grafana/provisioning:/etc/grafana/provisioning

volumes:
  mongodb_data:
  minio_data:
```

---

## 4. Configuration Keycloak — Realm DocAI

**Realm :** `docai`

**Clients :**

| Client | Type | Flow | Usage |
|--------|------|------|-------|
| `docai-backend` | Confidential | Client Credentials | Communication Spring Boot ↔ Keycloak |
| `docai-frontend` | Public | Authorization Code + **PKCE** | Connexion utilisateurs navigateur |

**Durées des tokens :**
- Access Token (JWT) : **15 minutes**
- Refresh Token : **8 heures**
- Session SSO : 24 heures

**5 Rôles :**

| Rôle | Permissions |
|------|------------|
| `TENANT_ADMIN` | Gestion totale du tenant, API keys, webhooks, quotas, billing |
| `ANALYST` | Upload, consultation, correction manuelle extractions |
| `VIEWER` | Lecture seule sur tous les documents du tenant |
| `FRAUD_REVIEWER` | Queue de révision fraude, décisions APPROVED/REJECTED |
| `SYSTEM` | Communication inter-services (client_credentials flow) |

**5 Utilisateurs de test (DEV uniquement) :**

| Email | Rôle | Tenant | MDP |
|-------|------|--------|-----|
| `admin@acme-corp.test` | TENANT_ADMIN | acme-corp | `Test1234!` |
| `analyst@acme-corp.test` | ANALYST | acme-corp | `Test1234!` |
| `viewer@acme-corp.test` | VIEWER | acme-corp | `Test1234!` |
| `reviewer@acme-corp.test` | FRAUD_REVIEWER | acme-corp | `Test1234!` |
| `admin@beta-assur.test` | TENANT_ADMIN | beta-assur | `Test1234!` |

> `admin@acme-corp.test` et `admin@beta-assur.test` = test isolation multi-tenant.

**Claims JWT (Protocol Mapper obligatoire) :**
```json
{
  "sub": "usr-123",
  "email": "analyst@acme-corp.test",
  "tenant_id": "acme-corp",
  "roles": ["ANALYST"],
  "exp": 1748000000
}
```

Mapper `tenant_id` : type "User Attribute" → attribut `tenant_id` → claim JWT `tenant_id`.

**Fichier :** `docker/keycloak/realm-docai.json` — **versionné dans Git** (configuration, pas un secret).

---

## 5. Variables d'environnement .env.example

```bash
# .env.example — Copier en .env et compléter les valeurs
# JAMAIS committer .env dans Git — vérifier .gitignore

# MongoDB
MONGO_ROOT_USER=docai_root
MONGO_ROOT_PASSWORD=CHANGE_ME
MONGODB_URI=mongodb://docai_root:CHANGE_ME@localhost:27017/docai?authSource=admin&replicaSet=rs0

# Valkey / Redis
VALKEY_HOST=localhost
VALKEY_PORT=6379

# Kafka + Apicurio
KAFKA_BOOTSTRAP_SERVERS=localhost:9094
SCHEMA_REGISTRY_URL=http://localhost:8081

# Keycloak
KEYCLOAK_URL=http://localhost:8180
KEYCLOAK_REALM=docai
KEYCLOAK_CLIENT_ID=docai-backend
KEYCLOAK_CLIENT_SECRET=CHANGE_ME

# Amazon S3 (ou MinIO en local)
AWS_ACCESS_KEY_ID=docai_minio
AWS_SECRET_ACCESS_KEY=docai_minio_secret
AWS_REGION=eu-west-3
S3_BUCKET_NAME=docai-documents-dev

# LLM API (Groq gratuit en DEV, OpenAI en PROD)
OPENAI_API_KEY=CHANGE_ME
SPRING_AI_OPENAI_BASE_URL=https://api.groq.com/openai/v1
SPRING_AI_OPENAI_MODEL=llama3-70b-8192

# APIs Externes
INSEE_CLIENT_ID=CHANGE_ME
INSEE_CLIENT_SECRET=CHANGE_ME

# MinIO local
MINIO_ROOT_USER=docai_minio
MINIO_ROOT_PASSWORD=docai_minio_secret

# Application
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
```

---

## 6. Commandes de démarrage

```bash
# 1. Cloner et configurer
git clone https://github.com/votre-org/docai.git && cd docai
cp .env.example .env
# Éditer .env avec les vraies valeurs

# 2. Infrastructure locale
docker compose up -d

# 3. Vérifier l'état
docker compose ps   # Tous les services doivent être healthy

# 4. Builder le projet
./mvnw clean install -DskipTests

# 5. Démarrer l'app en dev
./mvnw spring-boot:run -pl docai-bootstrap -Dspring-boot.run.profiles=dev

# --- Commandes quotidiennes ---
./mvnw test -pl docai-domain,docai-application           # Tests unitaires (sans Docker)
./mvnw verify -pl docai-adapter-out-mongodb               # Tests intégration
./mvnw test -pl docai-bootstrap -Dtest=CucumberTestRunner # Tests BDD
./mvnw test -Dtest=HexagonalArchitectureTest              # ArchUnit
./mvnw jacoco:report                                       # Couverture
./mvnw org.pitest:pitest-maven:mutationCoverage -pl docai-domain  # PIT
```

---

## 7. Vérification de l'installation

| Service | URL / Commande | Résultat attendu |
|---------|----------------|-----------------|
| MongoDB RS | `docker exec docai-mongodb mongosh --eval "rs.status().ok"` | `1` |
| Kafka UI | http://localhost:8090 | 8 topics DocAI visibles |
| Keycloak | http://localhost:8180 | Realm `docai` visible, connexion alice réussie |
| Apicurio | http://localhost:8081/ui | Interface accessible |
| Valkey | `docker exec docai-valkey valkey-cli ping` | `PONG` |
| App | `GET /actuator/health` | `{"status":"UP"}` |
| Auth | `GET /v1/documents` sans JWT | HTTP 401 |
| Auth | `GET /v1/documents` avec JWT alice | HTTP 200 |
| Swagger | http://localhost:8080/swagger-ui.html | Interface OpenAPI |

---

## 8. Definition of Done — Setup

- [ ] `docker compose ps` → tous les services `healthy`
- [ ] MongoDB Replica Set initialisé (`rs.status().ok === 1`)
- [ ] 8 topics Kafka présents dans Kafka UI
- [ ] Realm `docai` importé avec 5 rôles + 5 utilisateurs de test
- [ ] Token JWT de `analyst@acme-corp.test` contient `tenant_id` et `roles`
- [ ] `./mvnw clean install -DskipTests` → BUILD SUCCESS
- [ ] ArchUnit : `docai-domain` sans import Spring/MongoDB/Kafka
- [ ] `GET /actuator/health` → `{"status":"UP"}`
- [ ] `GET /v1/documents` sans JWT → HTTP 401
- [ ] `.env` dans `.gitignore` (secrets non commitués)
- [ ] `realm-docai.json` versionné dans `docker/keycloak/`
