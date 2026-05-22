---
name: docai-kafka-event
description: "Crée un nouvel événement Kafka DocAI avec son schéma Avro et son record Java. Utiliser quand on ajoute un nouveau type d'événement dans le pipeline documentaire. Respecte la convention de nommage, les headers obligatoires et l'enregistrement dans Apicurio Registry."
---

# DocAI — Créer un Événement Kafka

## Convention de nommage

- **Namespace Avro :** `fr.docai.events`
- **Nom :** `{Sujet}{Action}Event` en PascalCase (ex: `DocumentUploadedEvent`)
- **Topic :** `docai.{domaine}.{action}` en kebab-case (ex: `docai.doc.uploaded`)
- **Consumer Group ID :** `docai.{module}.{consumer}.group`

## Schéma Avro — Template

```json
{
  "namespace": "fr.docai.events",
  "type": "record",
  "name": "MonNouvelEvent",
  "doc": "Description de ce que représente cet événement",
  "fields": [
    {"name": "eventId",    "type": "string",  "doc": "UUID unique de l'événement"},
    {"name": "documentId", "type": "string",  "doc": "ID du document concerné"},
    {"name": "tenantId",   "type": "string",  "doc": "Tenant émetteur"},
    {"name": "occurredAt", "type": "string",  "doc": "ISO 8601 timestamp"},
    
    // Champs spécifiques à l'événement — ajouter ici
    {"name": "monChamp",   "type": "string"},
    
    // Champs optionnels avec valeur par défaut null
    {"name": "champOptional", "type": ["null", "string"], "default": null}
  ]
}
```

## Champs obligatoires sur TOUS les events

| Champ | Type | Description |
|-------|------|-------------|
| `eventId` | string (UUID) | Identifiant unique de l'event (idempotence) |
| `documentId` | string (UUID) | Document concerné |
| `tenantId` | string | Tenant (isolation) |
| `occurredAt` | string (ISO 8601) | Timestamp de l'occurrence |

## Headers Kafka obligatoires sur chaque message

```java
List<Header> headers = List.of(
    new RecordHeader("tenant-id",      tenantId.getBytes(StandardCharsets.UTF_8)),
    new RecordHeader("correlation-id", correlationId.getBytes(StandardCharsets.UTF_8)),
    new RecordHeader("event-type",     "MonNouvelEvent".getBytes(StandardCharsets.UTF_8)),
    new RecordHeader("trace-id",       MDC.get("traceId").getBytes(StandardCharsets.UTF_8)),
    new RecordHeader("schema-version", "1".getBytes(StandardCharsets.UTF_8))
);
```

## Record Java correspondant (Domain Event)

```java
// Dans fr.docai.domain.event (module docai-domain)
// occurredAt doit être Instant — jamais String (voir docai-domain-model skill)
public record MonNouvelEvent(
    String eventId,
    String documentId,
    String tenantId,
    String monChamp,
    Instant occurredAt   // ← Instant obligatoire (pas String)
) {
    // Factory method pour la création
    public static MonNouvelEvent of(String documentId, String tenantId, String monChamp) {
        return new MonNouvelEvent(
            UUID.randomUUID().toString(),
            documentId,
            tenantId,
            monChamp,
            Instant.now()   // ← Instant.now() pas Instant.now().toString()
        );
    }
}
```

## Topics existants — ne pas dupliquer

| Topic | Event | Partitions | Rétention |
|-------|-------|-----------|-----------|
| `docai.doc.uploaded` | `DocumentUploadedEvent` | 6 | 7j |
| `docai.doc.classified` | `DocumentClassifiedEvent` | 6 | 7j |
| `docai.doc.extracted` | `DocumentExtractedEvent` | 6 | 7j |
| `docai.doc.fraud.analyzed` | `FraudAnalyzedEvent` | 6 | 7j |
| `docai.doc.completed` | `DocumentCompletedEvent` | 3 | 30j |
| `docai.doc.failed` | `DocumentFailedEvent` | 3 | 30j |
| `docai.doc.dlq` | — | 3 | **90j** |
| `docai.outbox.relay` | — (relay interne) | 3 | 1j |

## Enregistrement dans Apicurio Registry

Le schéma Avro est enregistré via Apicurio Registry. Configurer dans application.yml :
```yaml
spring.kafka.producer.properties:
  schema.registry.url: ${APICURIO_REGISTRY_URL}
  auto.register.schemas: false     # ← JAMAIS true en production — schémas gérés manuellement
  value.subject.name.strategy: io.confluent.kafka.serializers.subject.TopicNameStrategy
```

## Checklist

- [ ] Schéma Avro créé dans `src/main/avro/` avec les 4 champs obligatoires
- [ ] Record Java créé dans `fr.docai.domain.event` (module `docai-domain`)
- [ ] Factory method `of(...)` sur le record
- [ ] Topic déclaré dans `KafkaTopicConfig` avec `partitions=6` (pipeline) ou `partitions=3` (autres)
- [ ] Consumer Group ID conforme à la convention
- [ ] Headers obligatoires inclus lors de la publication
- [ ] Schéma compatible avec l'évolution backward (nouveaux champs avec `default: null`)
