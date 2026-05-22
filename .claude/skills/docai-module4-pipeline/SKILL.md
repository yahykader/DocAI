---
name: docai-module4-pipeline
description: "Implémente le Module 4 DocAI (pipeline Kafka at-least-once, OutboxPoller, idempotence consumers par clé Valkey, DLQ, retry exponentiel, replay admin). Utiliser quand on demande d'implémenter le pipeline, l'idempotence Kafka, la Dead Letter Queue, le replay de messages DLQ, ou l'OutboxPoller."
---

# DocAI — Module 4 Orchestration & Pipeline (Phase 4.1 + 4.2)

## Architecture pipeline complet

```
Document soumis
  ↓ Outbox (at-least-once garanti)
docai.doc.uploaded → ClassificationKafkaConsumer
  ↓ (idempotent, commit manuel)
docai.doc.classified → ExtractionKafkaConsumer
  ↓
docai.doc.extracted → FraudKafkaConsumer
  ↓
docai.doc.fraud.analyzed → CompletionKafkaConsumer
  ↓
docai.doc.completed → WebhookDeliveryConsumer

Erreurs → DLQ (docai.doc.dlq) → Replay admin possible
```

## Business Rules

| ID | Règle |
|----|-------|
| BR-ORC-001 | Chaque étape déclenchée par un event Kafka dédié |
| BR-ORC-002 | Tous les consumers utilisent le commit manuel (at-least-once) |
| BR-ORC-003 | Chaque consumer est idempotent (N traitements = même résultat) |
| BR-ORC-004 | Idempotence via clé Valkey : `{topic}:{partition}:{offset}` (TTL 24h) |
| BR-ORC-010 | Erreurs transitoires retentées 3× backoff exponentiel (1s, 2s, 4s) |
| BR-ORC-011 | Après 3 échecs → DLQ (`docai.doc.dlq`) |
| BR-ORC-012 | DLQ monitorée — alerte Grafana si > 10 messages |
| BR-ORC-013 | Messages DLQ rejouables manuellement via API admin |

## OutboxPollerAdapter — Publication garantie

```java
@Component
public class OutboxPollerAdapter {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(OutboxPollerAdapter.class);

    // Polling toutes les 500ms (configurable)
    @Scheduled(fixedDelayString = "${docai.outbox.poll-interval-ms:500}")
    public void pollAndPublish() {
        List<OutboxMessage> pending = outboxRepository.findPending(
            Integer.parseInt(env.getProperty("docai.outbox.batch-size", "100"))
        );

        for (OutboxMessage msg : pending) {
            try {
                // Clé partition = documentId (ADR-002 — ordre garanti par document)
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                    msg.topic(), null, null, msg.partitionKey(), msg.payload(),
                    buildHeaders(msg)
                );

                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS); // Synchrone — ack garantie
                outboxRepository.markPublished(msg.id());

                log.info("Outbox event published eventId={} topic={} documentId={}",
                    msg.id(), msg.topic(), msg.aggregateId());

            } catch (Exception e) {
                int attempts = msg.attempts() + 1;
                if (attempts >= 5) {
                    outboxRepository.markFailed(msg.id(), e.getMessage(), attempts);
                    log.error("Outbox event FAILED after 5 attempts eventId={} documentId={}",
                        msg.id(), msg.aggregateId(), e);
                    // Alerte monitoring
                } else {
                    outboxRepository.markFailed(msg.id(), e.getMessage(), attempts);
                    log.warn("Outbox event retry attempt={}/5 eventId={}", attempts, msg.id());
                }
            }
        }
    }

    private List<Header> buildHeaders(OutboxMessage msg) {
        return List.of(
            new RecordHeader("tenant-id", msg.tenantId().getBytes()),
            new RecordHeader("event-type", msg.eventType().getBytes()),
            new RecordHeader("correlation-id", UUID.randomUUID().toString().getBytes()),
            new RecordHeader("schema-version", "1".getBytes())
        );
    }
}
```

## Idempotence consumer — Via ResilientKafkaConsumer (commons-kafka)

```java
// L'idempotence est gérée automatiquement par ResilientKafkaConsumer
// Clé Valkey : {topic}:{partition}:{offset} — TTL 24h fixe (pas de jitter)
// SETNX atomique → si clé existe → message ignoré silencieusement

// Tous les consumers étendent ResilientKafkaConsumer :
@Component
public class ExtractionKafkaConsumer extends ResilientKafkaConsumer<DocumentClassifiedEvent> {

    @KafkaListener(topics = "docai.doc.classified",
                   groupId = "docai.extraction.extraction.group")
    public void consume(ConsumerRecord<String, DocumentClassifiedEvent> record, Acknowledgment ack) {
        processWithIdempotence(record, () -> {
            // handler — TenantContext set/clear géré par ResilientKafkaConsumer
            extractDocumentUseCase.execute(record.value().getDocumentId());
        });
        ack.acknowledge();
    }
}
// Si le traitement échoue → clé Valkey supprimée → retry autorisé
// Après 3 échecs → DLQ automatique (géré par ResilientKafkaConsumer)
```

## DLQ — Dead Letter Queue

```java
// Consumer DLQ — surveille les messages en échec
@Component
public class DlqMonitorConsumer {

    @KafkaListener(
        topics = "docai.doc.dlq",
        groupId = "docai.pipeline.dlq.group"
    )
    public void consume(@Payload Object failedPayload,
                        @Header("original-topic") String originalTopic,
                        @Header("failure-reason") String reason,
                        @Header("attempt-count") String attempts,
                        Acknowledgment ack) {
        // Persister en MongoDB pour consultation admin
        DlqMessage dlqMessage = DlqMessage.of(
            failedPayload, originalTopic, reason,
            Integer.parseInt(attempts), Instant.now()
        );
        dlqRepository.save(dlqMessage);

        // Alerte si seuil dépassé
        long dlqCount = dlqRepository.countPending();
        if (dlqCount > 10) {
            log.error("DLQ threshold exceeded count={} — alerting", dlqCount);
            metricsRegistry.counter("docai.dlq.threshold.exceeded").increment();
        }

        ack.acknowledge();
    }
}

// Replay DLQ — API admin
@Component
public class ReplayDlqMessageUseCaseImpl implements ReplayDlqMessageUseCase {

    @Override
    @Audited(action = "DLQ_MESSAGE_REPLAYED", resourceType = "DlqMessage")
    public void execute(ReplayDlqCommand command) {
        DlqMessage msg = dlqRepository.findById(command.dlqMessageId())
            .orElseThrow(() -> new DlqMessageNotFoundException(command.dlqMessageId()));

        // Re-publier sur le topic original
        kafkaTemplate.send(msg.originalTopic(), msg.partitionKey(), msg.payload());

        dlqRepository.markReplayed(msg.id(), command.replayedBy(), Instant.now());

        log.info("DLQ message replayed dlqId={} originalTopic={} replayedBy=[PII_MASKED]",
            msg.id(), msg.originalTopic());
    }
}
```

## Endpoints DLQ admin

```java
@RestController
@RequestMapping("/v1/admin/dlq")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class DlqAdminController {

    @GetMapping
    public ResponseEntity<ApiResponse<List<DlqMessageSummary>>> listDlqMessages(
        Pageable pageable
    ) {
        String tenantId = TenantContext.get();
        return ResponseEntity.ok(ApiResponse.paginated(
            dlqRepository.findByTenantId(tenantId, pageable).stream()
                .map(DlqMessageSummary::from).collect(Collectors.toList()),
            buildPageMetadata(pageable)
        ));
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Void> replayMessage(@PathVariable String id) {
        replayDlqUseCase.execute(new ReplayDlqCommand(id, getCurrentUserId()));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        dlqRepository.delete(id, TenantContext.get());
        return ResponseEntity.noContent().build();
    }
}
```

## Configuration topics Kafka

```yaml
# Topics avec rétention différenciée
docai.doc.uploaded:       partitions=6, retention=7j, replication=3
docai.doc.classified:     partitions=6, retention=7j, replication=3
docai.doc.extracted:      partitions=6, retention=7j, replication=3
docai.doc.fraud.analyzed: partitions=6, retention=7j, replication=3
docai.doc.completed:      partitions=6, retention=7j, replication=3
docai.doc.dlq:            partitions=3, retention=90j, replication=3  # 90 jours (BR-ORC-015)
docai.outbox.relay:       partitions=3, retention=1j, replication=3
```

## Scénarios BDD

```gherkin
Scenario: Idempotence — même offset traité 2×
  Given un event Kafka topic=docai.doc.uploaded partition=0 offset=42
  And l'offset 42 est déjà en Valkey (TTL 24h)
  When le consumer reçoit l'event une 2ème fois
  Then le traitement est ignoré silencieusement
  And aucun doublon n'est créé en base

Scenario: Outbox — panne Kafka → publication à la reprise
  Given un OutboxEvent PENDING créé avant la panne Kafka
  When Kafka redevient disponible et le OutboxPoller tourne
  Then l'event est publié dans les 500ms
  And l'OutboxEvent passe en statut PUBLISHED

Scenario: DLQ — 3 échecs → message en DLQ
  Given un message qui échoue 3 fois consécutives
  When le 3ème retry échoue
  Then le message est envoyé sur docai.doc.dlq
  And DlqMessage créé en MongoDB avec raison et originalTopic

Scenario: Replay DLQ — TENANT_ADMIN rejoue un message
  Given un DlqMessage en attente
  When TENANT_ADMIN appelle POST /v1/admin/dlq/{id}/replay
  Then le message est republié sur le topic original
  And DlqMessage marqué comme REPLAYED + AuditEntry créé
```

## Checklist

- [ ] OutboxPoller `@Scheduled` 500ms, batch 100, retry 5× backoff exponentiel
- [ ] Clé idempotence `{topic}:{partition}:{offset}` TTL 24h fixe dans Valkey
- [ ] `setIfAbsent` (SETNX) atomique pour l'idempotence
- [ ] DLQ rétention 90 jours (BR-ORC-015)
- [ ] Alerte Grafana si DLQ > 10 messages (BR-ORC-012)
- [ ] Endpoint replay DLQ protégé `TENANT_ADMIN` + `@Audited`
- [ ] Clé partition = `documentId` sur tous les topics pipeline (ADR-002)
- [ ] Test : panne Kafka simulée → OutboxPoller publie à la reprise
- [ ] Test : même offset 2× → second ignoré silencieusement
- [ ] Test : 3 échecs → DLQ → replay → traitement normal
