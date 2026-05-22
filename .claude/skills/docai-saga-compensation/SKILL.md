---
name: docai-saga-compensation
description: "Implémente les compensations Saga du pipeline DocAI (7 scénarios d'échec, state machine PENDING→COMPLETED, NEEDS_REVIEW, DLQ). Utiliser quand on demande d'implémenter une compensation, de gérer un échec dans le pipeline, la state machine du document, ou le pattern Saga."
---

# DocAI — Saga & Compensations Pipeline

## State Machine globale

```
PENDING
  │ Upload OK + S3 + Outbox → Classification déclenchée
  ▼
CLASSIFIED ←── Classification OK (confidence ≥ 0.70)
  │        │
  │        └── confidence < 0.70 → NEEDS_REVIEW ←── Toute compensation arrive ici
  │                                     │ Révision manuelle ANALYST
  │ Extraction OK                       ▼
  ▼                                 REVIEWING
EXTRACTED                               │ Décision FRAUD_REVIEWER
  │ Fraude OK                      APPROVED / REJECTED
  ▼
FRAUD_ANALYZED
  │ score 0-25  → APPROVED automatiquement
  │ score 26-50 → FLAGGED (warning, pipeline continue)
  │ score 51-75 → NEEDS_REVIEW (révision obligatoire)
  │ score >75   → REJECTED immédiat + alerte SSE
  ▼
COMPLETED ──── Webhook livré ──── Fin
  └── Webhook non livré → COMPLETED (état inchangé, webhook en DLQ)
```

## Business Rules Saga

| ID | Règle |
|----|-------|
| BR-SAGA-001 | Tout document échoué passe en NEEDS_REVIEW — jamais bloqué |
| BR-SAGA-002 | La compensation ne supprime JAMAIS le fichier S3 (sauf AbortMultipart) |
| BR-SAGA-003 | Chaque compensation génère un AuditEntry avec raison et étape |
| BR-SAGA-004 | Score fraude -1 = analyse incomplète (distinct de 0 = aucun risque) |
| BR-SAGA-005 | Webhook non livré ne bloque JAMAIS l'état COMPLETED |
| BR-SAGA-006 | L'idempotency-key reste valide 24h même en cas d'échec |

## Les 7 scénarios de compensation

### Scénario 1 — Échec classification (après 3 retries)

```java
// Dans ClassificationKafkaConsumer — après 3 retries exhausted → DLQ
// Dans DlqMonitorConsumer — traitement des messages DLQ classification

@Component
public class ClassificationFailureHandler {
    public void handleClassificationFailure(String documentId, String tenantId, String reason) {
        // Compensation : NEEDS_REVIEW (jamais suppression)
        documentRepository.updateStatus(documentId, tenantId, DocumentStatus.NEEDS_REVIEW);

        // AuditEntry obligatoire
        auditPort.record(AuditEvent.builder()
            .action("CLASSIFICATION_FAILED")
            .resourceId(documentId)
            .tenantId(tenantId)
            .metadata(Map.of("reason", reason, "failedStage", "CLASSIFICATION"))
            .build());

        // Notification tenant
        notificationPort.notify(tenantId, NotificationType.REVIEW_REQUIRED, documentId);

        log.warn("Classification failed after retries documentId={} tenantId={} reason={}",
            documentId, tenantId, reason);
    }
}
```

### Scénario 2 — Échec extraction (LLM indisponible)

```java
// Si OCR a réussi → ExtractionResult partiel sauvegardé (rawOcrTextS3Key présent)
// Si OCR a aussi échoué → ExtractionResult absent
public void handleExtractionFailure(String documentId, String tenantId,
                                     String ocrTextS3Key, String reason) {
    if (ocrTextS3Key != null) {
        // OCR partiel disponible — sauvegarder quand même
        ExtractionResult partial = ExtractionResult.partial(documentId, tenantId, ocrTextS3Key);
        extractionRepository.save(partial);
        log.info("Partial extraction saved documentId={} ocrAvailable=true", documentId);
    }

    documentRepository.updateStatus(documentId, tenantId, DocumentStatus.NEEDS_REVIEW);
    auditPort.record(AuditEvent.of("EXTRACTION_FAILED", documentId, tenantId,
        Map.of("reason", reason, "ocrAvailable", ocrTextS3Key != null)));
}
```

### Scénario 3 — Analyse fraude partielle

```java
// Si au moins 1 analyseur a répondu → FraudAnalysis créée avec PARTIAL_ANALYSIS
// Score -1 uniquement si AUCUN analyseur n'a répondu
public void handlePartialFraudAnalysis(String documentId, String tenantId,
                                        List<FraudSignal> partialSignals) {
    int score = partialSignals.isEmpty() ? -1 : computeScore(partialSignals);

    FraudAnalysis analysis = FraudAnalysis.partial(documentId, tenantId, score, partialSignals);
    fraudRepository.save(analysis);

    // Document → NEEDS_REVIEW (sécurité — mieux vaut révision humaine)
    documentRepository.updateStatus(documentId, tenantId, DocumentStatus.NEEDS_REVIEW);

    log.warn("Partial fraud analysis documentId={} score={} signalCount={}",
        documentId, score, partialSignals.size());
}
```

### Scénario 4 — Webhook non livré après 5 retries

```java
// Le document reste COMPLETED — webhook en DLQ
public void handleWebhookPermanentlyFailed(String webhookId, String tenantId,
                                            String documentId) {
    webhookRepository.markPermanentlyFailed(webhookId);

    // Document COMPLETED inchangé (BR-SAGA-005)
    // Notification dashboard — pas de modification d'état
    notificationPort.notify(tenantId, NotificationType.WEBHOOK_FAILED, webhookId);

    auditPort.record(AuditEvent.of("WEBHOOK_DELIVERY_FAILED", webhookId, tenantId,
        Map.of("attempts", 5, "documentId", documentId)));

    log.error("Webhook permanently failed webhookId={} tenantId={} — manual replay available",
        webhookId, tenantId);
}
```

### Scénario 5 — Upload S3 interrompu (AbortMultipart — ADR-007)

```java
// Dans AwsS3StorageAdapter.upload()
public String upload(String tenantId, String documentId, byte[] content, String contentType) {
    String uploadId = null;
    try {
        CreateMultipartUploadResponse initResponse = s3Client.createMultipartUpload(...);
        uploadId = initResponse.uploadId();

        // Upload parts...
        List<CompletedPart> parts = uploadParts(uploadId, content);

        // CompleteMultipartUpload uniquement si tout est OK
        s3Client.completeMultipartUpload(b -> b.uploadId(uploadId).multipartUpload(...));
        return buildS3Key(tenantId, documentId);

    } catch (Exception e) {
        // Compensation obligatoire — ADR-007
        if (uploadId != null) {
            s3Client.abortMultipartUpload(b -> b.uploadId(uploadId));
            log.info("AbortMultipartUpload called documentId={}", documentId);
        }
        throw new StorageUploadException("Upload S3 interrompu", e);
        // Le client reçoit HTTP 503 → peut re-soumettre avec la même idempotency-key
    }
}
```

### Scénario 6 — Panne MongoDB après upload S3

```java
// Le fichier S3 est uploadé mais MongoDB échoue
// → HTTP 503 retourné au client
// → Le client re-soumet avec la même idempotency-key → traité comme premier envoi
// → Lifecycle Rule S3 supprime l'objet orphelin après 90 jours si jamais référencé
// → Log ERROR dans CloudWatch (audit impossible si MongoDB en panne)
```

### Scénario 7 — Panne entre Outbox sauvegardé et Kafka

```java
// L'Outbox Pattern protège nativement ce scénario
// Au redémarrage, OutboxRelay repollute les events PENDING et les publie
// Le consumer Kafka est idempotent → doublon ignoré silencieusement
// Aucune compensation nécessaire

@Scheduled(fixedDelay = 500) // Toutes les 500ms
public void pollAndPublish() {
    List<OutboxMessage> pending = outboxRepository.findPending(100);
    for (OutboxMessage msg : pending) {
        try {
            kafkaTemplate.send(buildRecord(msg)).get(5, TimeUnit.SECONDS);
            outboxRepository.markPublished(msg.id());
        } catch (Exception e) {
            outboxRepository.markFailed(msg.id(), e.getMessage(), msg.attempts() + 1);
        }
    }
}
```

## Checklist

- [ ] Scénario 1 testé : classification échoue 3× → NEEDS_REVIEW + AuditEntry
- [ ] Scénario 2 testé : LLM down → extraction partielle + NEEDS_REVIEW
- [ ] Scénario 3 testé : fraude partielle → score=-1 + NEEDS_REVIEW
- [ ] Scénario 4 testé : webhook 5× → document reste COMPLETED + DLQ
- [ ] Scénario 5 testé : upload interrompu → AbortMultipartUpload appelé
- [ ] Scénario 7 testé : OutboxRelay publie PENDING après redémarrage
- [ ] State machine : toutes les transitions valides testées
- [ ] Jamais de suppression S3 en compensation (sauf AbortMultipart)
- [ ] AuditEntry créé pour chaque compensation
