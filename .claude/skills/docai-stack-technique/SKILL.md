---
name: docai-stack-technique
description: "Stack technique complète DocAI — technologies, versions, topologie Kafka (8 topics + consumer groups + schémas Avro), stratégies cache Valkey (TTL jitter ADR-003), configuration Resilience4j (seuils exacts), application.yml complet. Utiliser avant d'implémenter tout adapter Kafka, adapter Valkey, adapter LLM/OCR, configuration Spring, ou quand on demande les versions des dépendances, les noms de topics, les consumer group IDs, les configurations Resilience4j, ou le YAML de configuration."
---

# DocAI — Stack Technique
## Référence complète technologies, Kafka, Valkey, Resilience4j

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 1 (Module 1.B + 1.C)

---

## 1. Stack Technique — Décisions

| Composant | Technologie | Version | Justification |
|-----------|-------------|---------|---------------|
| **Langage** | Java | 21 (LTS) | Virtual Threads, Records, Sealed Classes |
| **Framework** | Spring Boot | **4.0.x** | Support natif Virtual Threads |
| **Sécurité** | Spring Security 6 + Keycloak | 26 | IAM externalisé, JWT, RBAC multi-tenant |
| **Messagerie** | Apache Kafka | 3.7 (KRaft) | Mode KRaft — pas de Zookeeper |
| **Schema Registry** | **Apicurio Registry** | 2.6 | Apache 2.0 — remplace Confluent (licence restrictive) |
| **Cache** | **Valkey** 8.x | — | Fork Linux Foundation de Redis — BSD 3-Clause |
| **Persistance** | MongoDB | 7.0 | Schéma flexible, transactions (replica set obligatoire) |
| **Stockage** | Amazon S3 (prod) / LocalStack (tests) | SDK v2 | SLA 99.999999999% |
| **Résilience** | Resilience4j | 2.x | CircuitBreaker, Retry, Bulkhead, RateLimiter |
| **Rate Limiting** | Bucket4j 8.x + Valkey | — | Token bucket distribué multi-instance |
| **OCR** | Tess4J 5.x + **Apache PDFBox 3.x** | — | Tess4J pour images, PDFBox pour PDF texte natif |
| **Métadonnées** | Apache Tika | 2.x | Détection Photoshop/GIMP dans métadonnées fichier |
| **Vision IA** | OpenAI GPT-4o | — | gpt-4o (classification + extraction) |
| **LLM alternatif** | Mistral (via Spring AI) | — | Feature Flag `extraction.mistral.enabled` |
| **Analyse visuelle** | JavaCV (OpenCV Java) | 4.9.0 | Binding Java d'OpenCV — utiliser JavaCV pas OpenCV direct |
| **Mapping** | MapStruct | 1.6 | Compile-time, zéro réflexion |
| **Migrations DB** | Mongock | 5.x | Migration MongoDB versionnée (≈ Flyway) |
| **Métriques** | Micrometer + Prometheus | — | Grafana dashboards |
| **Tracing** | OpenTelemetry + Grafana Tempo | — | `traceId` propagé dans tous les logs |
| **API Docs** | SpringDoc OpenAPI | 2.x | Swagger UI auto, spec OpenAPI 3.1 |
| **Tests** | JUnit 5, TestContainers, ArchUnit, WireMock, Cucumber | — | Pyramide complète |
| **Feature Flags** | Unleash (self-hosted) | — | 6 flags définis, kill switch production |

> ⚠️ **Spring Boot 4.0.x** — pas 3.x. La version correcte est 4.0.x (nov. 2025).
> ⚠️ **Apicurio Registry** — pas Confluent Schema Registry (licence restrictive).
> ⚠️ **Valkey 8.x** — pas Redis (changement de licence mai 2025).
> ⚠️ **JavaCV** — pas `org.opencv` direct pour l'analyse visuelle.

---

## 2. Topologie Kafka — 8 Topics

| Topic | Producteur | Consommateur | Rétention | Partitions | Clé partition |
|-------|-----------|--------------|-----------|-----------|---------------|
| `docai.doc.uploaded` | OutboxPoller | ClassificationKafkaConsumer | 7 jours | 6 | `documentId` |
| `docai.doc.classified` | OutboxPoller | ExtractionKafkaConsumer | 7 jours | 6 | `documentId` |
| `docai.doc.extracted` | OutboxPoller | FraudKafkaConsumer, ValidationKafkaConsumer | 7 jours | 6 | `documentId` |
| `docai.doc.fraud.analyzed` | OutboxPoller | CompletionKafkaConsumer, AlertKafkaConsumer | 7 jours | 6 | `documentId` |
| `docai.doc.completed` | OutboxPoller | WebhookDeliveryConsumer, NotificationKafkaConsumer | 30 jours | 3 | `documentId` |
| `docai.doc.failed` | Tous services | DlqMonitorConsumer | 30 jours | 3 | `tenantId` |
| `docai.doc.dlq` | DLQ handler | DlqMonitorConsumer | **90 jours** | 3 | `tenantId` |
| `docai.outbox.relay` | OutboxPollerAdapter | — | 1 jour | 3 | `documentId` |

**ADR-002 obligatoire :** Clé partition = `documentId` sur tous les topics pipeline. Exception : DLQ et notifications = `tenantId`.

### Consumer Group IDs (convention `docai.{module}.{name}.group`)

| Consumer | Group ID | Topic consommé |
|----------|----------|----------------|
| `ClassificationKafkaConsumer` | `docai.recognition.classification.group` | `docai.doc.uploaded` |
| `ExtractionKafkaConsumer` | `docai.extraction.extraction.group` | `docai.doc.classified` |
| `ValidationKafkaConsumer` | `docai.extraction.validation.group` | `docai.doc.extracted` |
| `FraudKafkaConsumer` | `docai.fraud.analysis.group` | `docai.doc.extracted` |
| `CompletionKafkaConsumer` | `docai.pipeline.completion.group` | `docai.doc.fraud.analyzed` |
| `DashboardProjectionConsumer` | `docai.dashboard.projection.group` | Tous les topics |
| `WebhookDeliveryConsumer` | `docai.integration.webhook.group` | `docai.doc.completed` |
| `NotificationKafkaConsumer` | `docai.notification.alert.group` | `docai.doc.fraud.analyzed` |
| `AlertKafkaConsumer` (SSE) | `docai.notification.sse.group` | `docai.doc.fraud.analyzed` |
| `DlqMonitorConsumer` | `docai.pipeline.dlq.group` | `docai.doc.dlq` |

**Règles :**
- Group ID défini dans `application.yml` — jamais en dur dans le code Java
- Chaque group ID a une alerte Grafana de lag configurée

---

## 3. Feature Flags Unleash — 6 flags définis

| Flag | Valeur DEV/STAGING | Valeur PROD | Description |
|------|--------------------|-------------|-------------|
| `billing.enabled` | `false` | `true` | Active Stripe + quotas payants |
| `fraud.v2.enabled` | `false` | Feature | Active scoring fraude v2 |
| `extraction.mistral.enabled` | `false` | Feature | Swap OpenAI → Mistral |
| `dashboard.search.enabled` | `false` | Feature | Recherche full-text |
| `notifications.inapp.enabled` | `true` | `true` | Centre de notifications |
| `maintenance.mode` | `false` | Kill switch | Bloque toutes les soumissions |

---

## 4. Stratégies Cache Valkey (ADR-003)

| Cache | Clé | TTL | Jitter | Usage |
|-------|-----|-----|--------|-------|
| Extraction LLM | `extraction:{sha256-fichier}` | 24h | ±10% (ADR-003) | Cache résultat extraction complet |
| INSEE SIRET | `insee:siret:{siret}` | 7j | ±10% | Éviter rate limit 30 req/min |
| API BAN Adresse | `ban:address:{hash}` | 30j | ±10% | Adresses stables |
| RPPS Médecin | `rpps:{numero}` | 7j | ±10% | Médecins actifs stables |
| JWT Blacklist | `jwt:blacklist:{jti}` | = durée restante JWT | Fixe | Logout immédiat |
| Idempotence | `idempotent:{topic}:{partition}:{offset}` | 24h | **Fixe** | Précision requise |
| Idempotence upload | `idempotency:{X-Idempotency-Key}` | 24h | **Fixe** | Précision requise |
| Quota mensuel | `quota:{tenantId}:{year}-{month}` | Reset 1er du mois | Fixe | Compteur atomique Lua |
| Classification SHA | `classification:{sha256}` | 1h | ±10% | Cache résultat classification |

**Jitter obligatoire (ADR-003) :** `JitterTtl.withJitter(Duration.ofHours(24))` pour tout TTL > 1h.
**Exception :** Idempotence, JWT blacklist = TTL fixe.

---

## 5. Resilience4j — Seuils exacts par service

> Lire `references/resilience-config.md` pour les configurations YAML complètes.

| Service | Circuit Breaker | Retry | Bulkhead | Timeout |
|---------|----------------|-------|----------|---------|
| LLM (OpenAI/Mistral) | 50% / 10 calls | 3× exp. backoff 1s | 20 threads | 30s |
| OCR Tess4J | 50% / 5 calls | 3× backoff 2s | 10 threads | 60s |
| API INSEE | 60% / 5 calls | 2× backoff 2s | 5 threads | 5s |
| API BAN | 60% / 5 calls | 2× backoff 2s | 5 threads | 5s |
| API RPPS | 60% / 8 calls | 2× backoff 3s | 5 threads | 5s |
| Apache Tika | 50% / 5 calls | 2× backoff 1s | 5 threads | 15s |
| OpenCV/JavaCV | 50% / 5 calls | 1× | 5 threads | 15s |
| Amazon S3 | 50% / 10 calls | 3× exp. backoff 1s | 20 threads | 30s |

**Wait duration open state :** 30s (LLM, S3), 60s (OCR).
**Transition HALF_OPEN après :** 3 appels autorisés.

---

## 6. Métriques Micrometer — Catalogue complet

```
docai_document_upload_total{tenant, type}           — Counter
docai_document_processing_duration_seconds{module}  — Histogram
docai_extraction_confidence_score{doc_type}         — Histogram
docai_fraud_score_distribution{risk_level}          — Histogram
docai_circuit_breaker_state{service}                — Gauge
docai_cache_hit_ratio{region}                       — Gauge
docai_kafka_consumer_lag{topic, group}              — Gauge
docai_classification_duration_seconds               — Histogram
docai_classification_confidence_score               — Histogram
docai_ocr_duration_seconds                          — Histogram
docai_fraud_signal_detected{signal_type}            — Counter
docai_dlq_threshold_exceeded                        — Counter
docai_read_model_sync_lag_seconds                   — Histogram  (ADR-011)
docai_read_model_desync_total                       — Counter    (ADR-011)
docai_quota_usage{tenant, percent}                  — Gauge
```

**Alertes Grafana :**
- Error rate > 1% sur 5 min → PagerDuty
- p99 latence > 500ms → Slack
- Circuit Breaker OPEN → PagerDuty immédiat
- Kafka consumer lag > 1 000 messages → Slack
- Valkey cache hit ratio < 30% → Slack
- DLQ > 10 messages → Slack

---

## 7. Logs structurés — Politique obligatoire

**Format :** JSON via Logstash Logback Encoder (jamais de logs en texte brut en staging/prod)

```json
{
  "timestamp": "2026-05-22T10:00:00.000Z",
  "level": "INFO",
  "service": "docai-backend",
  "traceId": "abc123",
  "spanId": "def456",
  "tenantId": "acme-corp",
  "message": "Document submitted"
}
```

**Règles obligatoires :**
- `traceId` et `tenantId` dans CHAQUE log
- PII (email, nom, SIRET, IBAN) → `[PII_MASKED]`
- Niveaux : ERROR (exceptions non récupérables), WARN (état dégradé récupéré), INFO (flux nominal), DEBUG (dev local uniquement)
- Jamais de contenu de fichier dans les logs

> **Pour les configs YAML complètes Resilience4j :** voir `references/resilience-config.md`
