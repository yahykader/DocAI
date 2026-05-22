---
name: docai-observability
description: "Ajoute l'observabilité dans un module DocAI (métriques Micrometer/Prometheus, logs JSON structurés, masquage PII, tracing OpenTelemetry). Utiliser quand on demande d'ajouter des métriques, des logs structurés, du tracing, ou quand un log contient des données PII à masquer."
---

# DocAI — Ajouter l'Observabilité

## Métriques Micrometer — Référence complète

```java
// Métriques obligatoires par module
docai_document_upload_total{tenant, type}              // Counter — uploads
docai_document_processing_duration_seconds{module}     // Timer — latences
docai_extraction_confidence_score{doc_type}            // Histogram — scores OCR/LLM
docai_fraud_score_distribution{risk_level}             // Histogram — distribution fraude
docai_circuit_breaker_state{service}                   // Gauge — état CB
docai_cache_hit_ratio{region}                          // Gauge — efficacité cache
docai_kafka_consumer_lag{topic, group}                 // Gauge — lag consumers
// Métriques Fraude analyseurs (Phase 3.2)
docai_fraud_analyzer_failure{analyzer}                 // Counter — analyseur échoué (Tika, OpenCV)
// Métriques Read Model CQRS (ADR-011)
docai_read_model_sync_lag_seconds                      // Histogram — délai write-side → Read Model
docai_read_model_desync_total                          // Counter — désynchronisations détectées
docai_read_model_resync_total                          // Counter — resynchronisations effectuées
// Métriques DLQ + Quota
docai_dlq_messages_total{topic}                        // Counter — messages en DLQ
docai_quota_usage_percent{tenant, plan}                // Gauge — % quota utilisé
```

## Ajouter des métriques dans un Use Case

```java
@Component
public class UploadDocumentUseCaseImpl implements UploadDocumentUseCase {

    private final MeterRegistry meterRegistry;
    private final Counter uploadCounter;
    private final Timer uploadTimer;

    public UploadDocumentUseCaseImpl(MeterRegistry meterRegistry, ...) {
        this.meterRegistry = meterRegistry;
        this.uploadCounter = Counter.builder("docai.document.upload.total")
            .description("Total document uploads")
            .register(meterRegistry);
        this.uploadTimer = Timer.builder("docai.document.processing.duration")
            .tag("module", "upload")
            .description("Document upload processing time")
            .register(meterRegistry);
    }

    @Override
    public UploadResult execute(UploadCommand command) {
        return uploadTimer.record(() -> {
            UploadResult result = doUpload(command);

            // Incrémenter le counter avec les tags
            uploadCounter.increment();
            meterRegistry.counter("docai.document.upload.total",
                "tenant", command.tenantId(),
                "type", result.mimeType()
            ).increment();

            return result;
        });
    }
}
```

## Logs JSON structurés — Format obligatoire

```json
{
  "timestamp": "2026-05-14T10:00:00Z",
  "level": "INFO",
  "service": "docai-backend",
  "traceId": "abc-123-xyz",
  "spanId": "def-456",
  "tenantId": "acme-corp",
  "userId": "usr-789",
  "message": "Document uploaded successfully"
}
```

## Règles de logging — CRITIQUES

### Ce qui DOIT être loggé (INFO)
```java
// Upload
log.info("Document uploaded documentId={} tenantId={} mimeType={} sizeBytes={} durationMs={}",
    documentId, tenantId, mimeType, sizeBytes, duration);

// Changement de statut pipeline
log.info("Document status changed documentId={} tenantId={} fromStatus={} toStatus={} durationMs={}",
    documentId, tenantId, fromStatus, toStatus, duration);

// Décision fraude
log.info("Fraud decision documentId={} tenantId={} score={} riskLevel={}",
    documentId, tenantId, score, riskLevel);

// Action TENANT_ADMIN
log.info("Admin action tenantId={} action={} targetUserId=[PII_MASKED]",
    tenantId, action);
```

### Ce qui NE DOIT JAMAIS être loggé
```java
// INTERDIT — données PII
log.info("User {} uploaded document", user.getEmail());        // ❌ Email
log.info("CNI number: {}", cniNumber);                         // ❌ Numéro CNI
log.info("IBAN: {}", bankAccount.getIban());                   // ❌ IBAN
log.info("JWT token: {}", jwtToken);                           // ❌ Token
log.info("API Key: {}", apiKey);                               // ❌ Secret

// CORRECT — données masquées
log.info("User [PII_MASKED] uploaded documentId={}", documentId);  // ✅
log.info("CNI processed documentId={} tenantId={}", documentId, tenantId); // ✅
```

### Niveaux de log — règles
```java
// ERROR — nécessite intervention humaine
log.error("Circuit Breaker OPEN service={} documentId={}", service, documentId, ex);
log.error("MongoDB write failed documentId={} tenantId={}", documentId, tenantId, ex);

// WARN — situation anormale mais récupérée
log.warn("Retry succeeded attempt={} documentId={}", attempt, documentId);
log.warn("Quota warning tenantId={} usage={}% limit={}", tenantId, pct, limit);
log.warn("Low confidence score={} documentId={} action=NEEDS_REVIEW", score, documentId);

// INFO — événement métier important (voir liste ci-dessus)
// DEBUG — désactivé en prod
log.debug("MongoDB query executed collection={} filter={}", collection, filter);
```

## Tracing OpenTelemetry — propagation obligatoire

```java
// Le traceId est propagé automatiquement via :
// 1. Headers HTTP : traceparent, tracestate
// 2. Headers Kafka : trace-id (header custom)
// 3. MDC Logback : traceId, spanId

// Dans chaque consumer Kafka — extraire et propager
@KafkaListener(topics = "docai.doc.uploaded")
public void consume(@Header("trace-id") String traceId, ...) {
    MDC.put("traceId", traceId);
    MDC.put("tenantId", tenantId);
    try {
        // traitement
    } finally {
        MDC.clear();  // OBLIGATOIRE — évite les fuites de contexte
    }
}
```

## Alertes Grafana — seuils configurés

| Alerte | Seuil | Canal |
|--------|-------|-------|
| Error rate > 1% sur 5 min | `sum(rate(errors[5m])) / sum(rate(requests[5m])) > 0.01` | PagerDuty |
| P99 latence > 500ms | `histogram_quantile(0.99, ...) > 0.5` | Slack |
| Circuit Breaker OPEN | `docai_circuit_breaker_state > 0` | PagerDuty immédiat |
| Kafka lag > 1000 messages | `docai_kafka_consumer_lag > 1000` | Slack |
| Cache hit ratio < 30% | `docai_cache_hit_ratio < 0.3` | Slack |

## Checklist

- [ ] Counter `docai_document_upload_total` avec tags `tenant` et `type`
- [ ] Timer `docai_document_processing_duration_seconds` avec tag `module`
- [ ] Logs INFO sur : upload, changement statut, décision fraude, action admin
- [ ] Aucune donnée PII dans les logs → `[PII_MASKED]`
- [ ] `traceId` et `tenantId` dans chaque log (via MDC)
- [ ] `MDC.clear()` dans `finally` sur chaque consumer Kafka et filtre HTTP
- [ ] Niveau DEBUG désactivé en staging et prod (`application-prod.yml`)
- [ ] Métriques visibles dans Prometheus : `curl localhost:8080/actuator/prometheus`
- [ ] **ADR-011** : métriques `docai_read_model_sync_lag_seconds`, `docai_read_model_desync_total`, `docai_read_model_resync_total` exposées (Module 5)
- [ ] **Phase 3.2** : métrique `docai_fraud_analyzer_failure{analyzer}` incrémentée sur chaque analyseur Tika/OpenCV en échec
- [ ] Alertes Grafana configurées (Error rate > 1%, P99 > 500ms, CB OPEN, Kafka lag > 1000)
