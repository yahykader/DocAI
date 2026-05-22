---
name: docai-cqrs-readmodel
description: "Implémente le Read Model CQRS DocAI (DashboardProjectionConsumer, ReadModelReconciliationScheduler, lastSyncedAt, ADR-011). Utiliser quand on demande d'implémenter le dashboard, le Read Model, la projection Kafka, le job de réconciliation, ou la reconstruction du Read Model."
---

# DocAI — CQRS Read Model & Réconciliation

## Principe CQRS DocAI

```
Write Side (MongoDB write collections)
  └── documents, extraction_results, fraud_analyses

Read Side (MongoDB read collections — dénormalisées)
  └── document_summary_views  ← mis à jour par DashboardProjectionConsumer

Séparation stricte :
  - Les endpoints dashboard lisent UNIQUEMENT le Read Model
  - Jamais de JOIN ou requête sur les collections write-side depuis le dashboard
```

## ADR-011 — lastSyncedAt + job réconciliation (OBLIGATOIRE)

```java
// OBLIGATOIRE sur chaque entrée du Read Model
// PAS de @Indexed — index créés uniquement via Mongock (ADR-010)
@Document(collection = "document_summary_views")
public class DocumentSummaryView {
    @Id private String id;
    @Field("tenant_id") private String tenantId;      // PAS @Indexed ici
    @Field("document_id") private String documentId;  // PAS @Indexed ici
    @Field("status") private String status;
    @Field("type") private String documentType;
    @Field("risk_level") private String riskLevel;
    @Field("fraud_score") private Integer fraudScore;
    @Field("extraction_score") private Double extractionScore;
    @Field("created_at") private Instant createdAt;
    @Field("updated_at") private Instant updatedAt;

    // ADR-011 : champ obligatoire pour détecter les désynchronisations
    @Field("last_synced_at") private Instant lastSyncedAt;
}
```

## DashboardProjectionConsumer — Mise à jour Read Model

```java
@Component
public class DashboardProjectionConsumer extends ResilientKafkaConsumer<Object> {

    private final DocumentSummaryViewRepository viewRepository;

    // Écoute TOUS les events du pipeline
    @KafkaListener(topics = {
        "docai.doc.uploaded",
        "docai.doc.classified",
        "docai.doc.extracted",
        "docai.doc.fraud.analyzed",
        "docai.doc.completed",
        "docai.doc.failed"
    }, groupId = "docai.dashboard.projection.group")
    public void consume(@Payload Object event,
                        @Header("event-type") String eventType,
                        @Header("tenant-id") String tenantId,
                        Acknowledgment ack) {
        try {
            projectEvent(event, eventType, tenantId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Projection failed eventType={} reason={}", eventType, e.getMessage());
            ack.acknowledge(); // Ne pas bloquer le pipeline pour le dashboard
        }
    }

    private void projectEvent(Object event, String eventType, String tenantId) {
        String documentId = extractDocumentId(event);

        DocumentSummaryView view = viewRepository
            .findByDocumentIdAndTenantId(documentId, tenantId)
            .orElse(new DocumentSummaryView(documentId, tenantId));

        // Mise à jour selon le type d'event
        switch (eventType) {
            case "DocumentUploaded"    -> view.setStatus("PENDING");
            case "DocumentClassified"  -> view.setStatus("CLASSIFIED");
            case "DocumentExtracted"   -> view.setStatus("EXTRACTED");
            case "FraudAnalyzed"       -> {
                view.setStatus("FRAUD_ANALYZED");
                view.setFraudScore(extractFraudScore(event));
                view.setRiskLevel(extractRiskLevel(event));
            }
            case "DocumentCompleted"   -> view.setStatus("COMPLETED");
            case "DocumentFailed"      -> view.setStatus("NEEDS_REVIEW");
        }

        // ADR-011 : mise à jour lastSyncedAt obligatoire
        view.setLastSyncedAt(Instant.now());
        viewRepository.save(view);

        log.info("Read Model updated documentId={} tenantId={} status={} lastSyncedAt={}",
            documentId, tenantId, view.getStatus(), view.getLastSyncedAt());
    }
}
```

## ReadModelReconciliationScheduler — ADR-011

```java
@Component
public class ReadModelReconciliationScheduler {

    private final DocumentSummaryViewRepository viewRepository;
    private final DocumentRepository writeRepository;
    private final MeterRegistry meterRegistry;

    // Job toutes les 5 minutes — détecte les désynchronisations
    @Scheduled(fixedDelay = 300_000)
    public void detectAndResyncDesynchronizations() {
        Instant threshold = Instant.now().minusSeconds(30); // Lag > 30s = désynchronisé

        // Documents write-side mis à jour après lastSyncedAt + 30s du Read Model
        List<String> desyncedIds = viewRepository.findDesynchronized(threshold);

        if (!desyncedIds.isEmpty()) {
            log.warn("Desynchronized Read Model entries detected count={}", desyncedIds.size());
            meterRegistry.counter("docai.read_model.desync.total")
                .increment(desyncedIds.size());
        }

        // Resynchronisation ciblée
        for (String documentId : desyncedIds) {
            try {
                resyncDocument(documentId);
                meterRegistry.counter("docai.read_model.resync.total").increment();
            } catch (Exception e) {
                log.error("Resync failed documentId={}", documentId, e);
            }
        }
    }

    private void resyncDocument(String documentId) {
        // Lire l'état actuel depuis la write-side
        Document doc = writeRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        // Mettre à jour le Read Model
        DocumentSummaryView view = viewRepository
            .findByDocumentIdAndTenantId(documentId, doc.getTenantId())
            .orElse(new DocumentSummaryView(documentId, doc.getTenantId()));

        view.setStatus(doc.getStatus().name());
        view.setLastSyncedAt(Instant.now());
        viewRepository.save(view);

        auditPort.record(AuditEvent.of("READ_MODEL_RESYNCED", documentId, doc.getTenantId(),
            Map.of("cause", "SCHEDULER_RECONCILIATION")));

        log.info("Read Model resynced documentId={} tenantId={}", documentId, doc.getTenantId());
    }
}
```

## Endpoint admin — Reconstruction complète sans downtime

```java
// POST /v1/admin/read-model/rebuild
// Reconstruit le Read Model complet sans interruption de service
@PostMapping("/v1/admin/read-model/rebuild")
@PreAuthorize("hasRole('SYSTEM')")
public ResponseEntity<Void> rebuildReadModel() {
    // 1. Écrire dans une collection temporaire
    // 2. Rejouer tous les events Kafka des 7 derniers jours
    // 3. Swap atomique : renommer temp → document_summary_views
    rebuildService.rebuildAsync(); // Async — pas de timeout HTTP
    return ResponseEntity.accepted().build();
}
```

## Index Read Model (optimisés dashboard)

```java
// Convention nommage Mongock : V{NNN}_{module}_{description}
@ChangeUnit(id = "V006_dashboard_summary_views_create_collection", order = "006", author = "docai-team")
public class V006DashboardSummaryViewsCreateCollection {
    @Execution
    public void execute(MongoDatabase db) {
        db.createCollection("document_summary_views");

        // tenantId EN PREMIER dans tous les index (ADR-010)
        db.getCollection("document_summary_views").createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("tenant_id"),
                Indexes.ascending("status"),
                Indexes.descending("created_at")),
            new IndexOptions().name("idx_tenant_status_date"));

        db.getCollection("document_summary_views").createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("tenant_id"),
                Indexes.ascending("risk_level"),
                Indexes.descending("created_at")),
            new IndexOptions().name("idx_tenant_risk_date"));

        // Index pour le job réconciliation ADR-011
        db.getCollection("document_summary_views").createIndex(
            Indexes.ascending("last_synced_at"),
            new IndexOptions().name("idx_last_synced_at"));
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        db.getCollection("document_summary_views").drop();
    }
}
```

## Métriques Prometheus

```
docai_read_model_sync_lag_seconds    — Histogram — délai write-side → Read Model
docai_read_model_desync_total        — Counter — désynchronisations détectées
docai_read_model_resync_total        — Counter — resynchronisations effectuées
```

## Checklist

- [ ] `lastSyncedAt` présent sur chaque entrée `document_summary_views` (ADR-011)
- [ ] `DashboardProjectionConsumer` écoute tous les topics pipeline
- [ ] `lastSyncedAt` mis à jour à chaque event traité
- [ ] Job réconciliation toutes les 5 min (lag > 30s détecté)
- [ ] Alerte Grafana si > 10 désynchronisations en 5 min
- [ ] Endpoint `POST /v1/admin/read-model/rebuild` (rôle SYSTEM)
- [ ] Index MongoDB sur `tenant_id + status + created_at`
- [ ] Requêtes dashboard < 100ms avec 100 000 documents — EXPLAIN PLAN validé
- [ ] Test : consumer arrêté 10 min → réconciliation rattrape à la reprise
