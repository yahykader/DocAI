---
name: docai-adapter-kafka
description: "Crée un Consumer ou Producer Kafka dans docai-adapter-in-kafka ou docai-adapter-out-kafka. Utiliser pour implémenter un listener Kafka, publier un événement, ou configurer l'Outbox Pattern. Applique les conventions DocAI : headers obligatoires, consumer group IDs, schémas Avro, idempotence."
---

# DocAI — Créer un Adapter Kafka

## Topics et Consumer Groups DocAI — Référence complète

| Topic | Consumer Class | Group ID |
|-------|---------------|----------|
| `docai.doc.uploaded` | `ClassificationKafkaConsumer` | `docai.recognition.classification.group` |
| `docai.doc.classified` | `ExtractionKafkaConsumer` | `docai.extraction.extraction.group` |
| `docai.doc.extracted` | `FraudKafkaConsumer` | `docai.fraud.analysis.group` |
| `docai.doc.extracted` | `ValidationKafkaConsumer` | `docai.extraction.validation.group` |
| `docai.doc.fraud.analyzed` | `CompletionKafkaConsumer` | `docai.pipeline.completion.group` |
| `docai.doc.fraud.analyzed` | `AlertKafkaConsumer` (SSE) | `docai.notification.sse.group` |
| `docai.doc.fraud.analyzed` | `NotificationKafkaConsumer` | `docai.notification.alert.group` |
| `docai.doc.completed` | `WebhookDeliveryConsumer` | `docai.integration.webhook.group` |
| `docai.doc.completed` | `DashboardProjectionConsumer` | `docai.dashboard.projection.group` |
| `docai.doc.dlq` | `DlqMonitorConsumer` | `docai.pipeline.dlq.group` |

> **ADR-002 :** Clé de partition = `documentId` sur tous les topics pipeline. Exception : DLQ = `tenantId`.

## Consumer Kafka — Structure type (étendre ResilientKafkaConsumer)

```java
// Package : fr.docai.adapter.in.kafka
// TOUJOURS étendre ResilientKafkaConsumer — jamais implémenter manuellement l'idempotence
@Component
public class MonKafkaConsumer extends ResilientKafkaConsumer<MonEvent> {

    private final MonUseCase monUseCase;

    public MonKafkaConsumer(MonUseCase monUseCase) {
        this.monUseCase = monUseCase;
    }

    @KafkaListener(
        topics = "docai.doc.uploaded",
        groupId = "docai.recognition.classification.group"
    )
    public void consume(ConsumerRecord<String, MonEvent> record, Acknowledgment ack) {
        // processWithIdempotence gère : idempotence Valkey (clé topic:partition:offset),
        // DLQ après 3 échecs, MDC tenantId/traceId, TenantContext
        processWithIdempotence(record, () -> {
            MonEvent event = record.value();
            TenantContext.set(event.getTenantId());
            try {
                monUseCase.execute(new MonCommand(event.getTenantId(), event.getDocumentId()));
            } finally {
                TenantContext.clear();
            }
        });
        ack.acknowledge(); // ACK manuel — jamais auto-commit
    }

    @Override
    public void handle(MonEvent event, KafkaConsumerContext context) {
        // Implémentation alternative si on surcharge handle() directement
        monUseCase.execute(new MonCommand(context.tenantId(), event.getDocumentId()));
    }
}
```

> **Idempotence :** clé Valkey = `{topic}:{partition}:{offset}`, TTL 24h fixe. **Jamais** par `correlationId` seul.

## Producer Kafka — Outbox Pattern (OBLIGATOIRE pour les événements critiques)

```java
// Package : fr.docai.adapter.out.kafka
// L'Outbox garantit qu'un event est publié même si Kafka est temporairement indisponible
@Component
public class OutboxEventPublisher implements EventPublisherPort {

    private final OutboxRepository outboxRepository; // Sauvegarde en MongoDB d'abord

    @Override
    public void publish(DomainEvent event) {
        // Sauvegarder dans l'outbox MongoDB (même transaction que la donnée)
        OutboxEntry entry = OutboxEntry.of(event);
        outboxRepository.save(entry);
        // Le OutboxPoller publiera sur Kafka de manière asynchrone
    }
}

// OutboxPoller — publié périodiquement
@Component
public class OutboxPoller {
    @Scheduled(fixedDelay = 1000)  // Toutes les secondes
    public void pollAndPublish() {
        // Récupérer les entries non publiées, publier sur Kafka, marquer comme publiées
    }
}
```

## Headers obligatoires sur chaque message publié

```java
// Toujours inclure ces headers lors de la publication
List<Header> headers = List.of(
    new RecordHeader("tenant-id", tenantId.getBytes()),
    new RecordHeader("correlation-id", correlationId.getBytes()),
    new RecordHeader("event-type", eventType.getBytes()),
    new RecordHeader("trace-id", MDC.get("traceId").getBytes()),
    new RecordHeader("schema-version", "1".getBytes())
);
ProducerRecord<String, Object> record = new ProducerRecord<>(
    topic, null, null, documentId, eventPayload, headers
);
```

## Gestion de l'idempotence — Via ResilientKafkaConsumer

```java
// L'idempotence est gérée AUTOMATIQUEMENT par ResilientKafkaConsumer
// Clé Valkey : {topic}:{partition}:{offset} — TTL 24h fixe (pas de jitter)
// Jamais utiliser correlationId seul — un même correlationId peut avoir plusieurs offsets

// Si on veut vérifier manuellement (rare) :
protected final boolean isAlreadyProcessed(String offsetKey) {
    // offsetKey = record.topic() + ":" + record.partition() + ":" + record.offset()
    return valkey.hasKey("idempotent:" + offsetKey);
}
```

## Checklist

- [ ] Consumer **étend `ResilientKafkaConsumer`** — jamais de consumer standalone
- [ ] `groupId` conforme à la convention `docai.{module}.{consumer}.group`
- [ ] Idempotence via `processWithIdempotence(record, () -> {...})` — clé `topic:partition:offset`
- [ ] ACK manuel (`Acknowledgment ack`) — jamais auto-commit
- [ ] DLQ automatique après 3 échecs (géré par `ResilientKafkaConsumer`)
- [ ] `TenantContext.set()` dans le handler + `TenantContext.clear()` dans finally
- [ ] Outbox Pattern pour tout event critique (pas de publish direct)
- [ ] Clé partition = `documentId` (ADR-002) — configurer dans `ProducerRecord`
- [ ] Test d'intégration avec TestContainers Kafka (via `AbstractIntegrationTest`)
