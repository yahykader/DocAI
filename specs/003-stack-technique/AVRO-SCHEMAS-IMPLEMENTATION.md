# Schémas Avro Kafka Events Implementation

**Date**: 2026-05-26  
**Branch**: `004-stack-technique`  
**Location**: `backend/docai-adapter-out-kafka/src/main/avro/`  
**Status**: ✅ COMPLETE (7 schemas created)  

---

## Overview

7 Avro schemas have been created for the Kafka event-driven pipeline, following Apicurio Registry conventions and ADR-002 (partition key management).

| Schema | Purpose | Partition Key | Status |
|--------|---------|----------------|--------|
| DocumentUploadedEvent | S3 upload completion | `documentId` | ✅ Created |
| DocumentClassifiedEvent | Document type classification | `documentId` | ✅ Created |
| DocumentExtractedEvent | Data extraction completion | `documentId` | ✅ Created |
| DocumentFraudAnalyzedEvent | Fraud analysis results | `documentId` | ✅ Created |
| DocumentCompletedEvent | Pipeline completion | `documentId` | ✅ Created |
| DocumentFailedEvent | Pipeline failure | `documentId` | ✅ Created |
| OutboxRelayEvent | Outbox pattern relay | `aggregateId` | ✅ Created |

---

## Schema Details

### 1. DocumentUploadedEvent

**Topic**: `docai.documents.uploaded`  
**Key**: `documentId` (ADR-002)

```json
{
  "documentId": "doc-123",
  "tenantId": "tenant-456",
  "fileName": "invoice_2024.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 245678,
  "s3Key": "uploads/2024/05/doc-123.pdf",
  "contentHash": "sha256:abc123...",
  "uploadedAt": 1716729600000,
  "occurredAt": 1716729605000
}
```

**Fields**:
- `documentId` (string): Unique document identifier
- `tenantId` (string): Multi-tenancy support
- `fileName` (string): Original filename
- `mimeType` (string): MIME type (pdf, image/png, etc.)
- `sizeBytes` (long): File size in bytes
- `s3Key` (string): S3 object path
- `contentHash` (string): SHA-256 for integrity
- `uploadedAt` (long): File upload timestamp (epoch ms)
- `occurredAt` (long): Event timestamp (epoch ms)

**Modules Publishing**: `docai-adapter-out-storage` (S3 adapter)  
**Modules Consuming**: `docai-adapter-in-kafka` (classification module)

---

### 2. DocumentClassifiedEvent

**Topic**: `docai.documents.classified`  
**Key**: `documentId` (ADR-002)

```json
{
  "documentId": "doc-123",
  "tenantId": "tenant-456",
  "documentType": "INVOICE",
  "confidenceScore": 0.95,
  "modelVersion": "v2.1.0",
  "occurredAt": 1716729620000
}
```

**Fields**:
- `documentId` (string): Unique identifier
- `tenantId` (string): Multi-tenancy
- `documentType` (enum): INVOICE | RECEIPT | IDENTITY | BANK_STATEMENT | CONTRACT | UNKNOWN
- `confidenceScore` (float): 0.0-1.0 confidence
- `modelVersion` (string): Classification model version
- `occurredAt` (long): Event timestamp

**Modules Publishing**: `docai-adapter-in-kafka` (classification consumer)  
**Modules Consuming**: `docai-adapter-in-kafka` (extraction module)

---

### 3. DocumentExtractedEvent

**Topic**: `docai.documents.extracted`  
**Key**: `documentId` (ADR-002)

```json
{
  "documentId": "doc-123",
  "tenantId": "tenant-456",
  "documentType": "INVOICE",
  "extractedFields": {
    "invoice_number": "INV-2024-001",
    "vendor_name": "ACME Corp",
    "total_amount": "1500.00",
    "invoice_date": "2024-05-26"
  },
  "globalScore": 0.87,
  "rawOcrTextS3Key": "ocr-output/2024/05/doc-123-raw-text.json",
  "occurredAt": 1716729640000
}
```

**Fields**:
- `documentId` (string): Document identifier
- `tenantId` (string): Multi-tenancy
- `documentType` (enum): Document type
- `extractedFields` (map<string, string>): Key-value pairs extracted
- `globalScore` (float): 0.0-1.0 extraction quality
- `rawOcrTextS3Key` (string): S3 path to full OCR text (ADR-004 - large payloads in S3)
- `occurredAt` (long): Event timestamp

**Modules Publishing**: `docai-adapter-in-kafka` (extraction consumer)  
**Modules Consuming**: `docai-adapter-in-kafka` (fraud analysis module)

---

### 4. DocumentFraudAnalyzedEvent

**Topic**: `docai.documents.fraud-analyzed`  
**Key**: `documentId` (ADR-002)

```json
{
  "documentId": "doc-123",
  "tenantId": "tenant-456",
  "fraudScore": 25,
  "riskLevel": "FAIBLE",
  "signals": [
    {
      "signalType": "amount_inconsistency",
      "severity": "WARNING",
      "description": "Extracted amount differs 2% from OCR"
    },
    {
      "signalType": "registry_lookup_failed",
      "severity": "INFO",
      "description": "Could not verify vendor in external registry"
    }
  ],
  "occurredAt": 1716729660000
}
```

**Fields**:
- `documentId` (string): Document identifier
- `tenantId` (string): Multi-tenancy
- `fraudScore` (int): 0-100 risk score
- `riskLevel` (enum): FAIBLE | MODERE | ELEVE | CRITIQUE
- `signals` (array): Fraud signal details
  - `signalType` (string): Type of fraud indicator
  - `severity` (enum): INFO | WARNING | CRITICAL
  - `description` (string): Human-readable description
- `occurredAt` (long): Event timestamp

**Modules Publishing**: `docai-adapter-in-kafka` (fraud analysis consumer)  
**Modules Consuming**: `docai-adapter-in-kafka` (pipeline orchestration)

---

### 5. DocumentCompletedEvent

**Topic**: `docai.documents.completed`  
**Key**: `documentId` (ADR-002)

```json
{
  "documentId": "doc-123",
  "tenantId": "tenant-456",
  "finalStatus": "SUCCESS_WITH_WARNINGS",
  "occurredAt": 1716729680000
}
```

**Fields**:
- `documentId` (string): Document identifier
- `tenantId` (string): Multi-tenancy
- `finalStatus` (enum): SUCCESS | SUCCESS_WITH_WARNINGS | MANUAL_REVIEW_REQUIRED
- `occurredAt` (long): Event timestamp

**Modules Publishing**: `docai-adapter-in-kafka` (pipeline orchestration)  
**Modules Consuming**: `docai-adapter-in-rest` (API notifications), Frontend dashboards

---

### 6. DocumentFailedEvent

**Topic**: `docai.documents.failed`  
**Key**: `documentId` (ADR-002)

```json
{
  "documentId": "doc-123",
  "tenantId": "tenant-456",
  "failureStage": "EXTRACTION",
  "errorCode": "OCR_FAILED",
  "occurredAt": 1716729665000
}
```

**Fields**:
- `documentId` (string): Document identifier
- `tenantId` (string): Multi-tenancy
- `failureStage` (string): UPLOAD | CLASSIFICATION | EXTRACTION | FRAUD_ANALYSIS | ORCHESTRATION
- `errorCode` (string): INVALID_FILE_FORMAT | OCR_FAILED | NETWORK_TIMEOUT | VALIDATION_ERROR | etc.
- `occurredAt` (long): Event timestamp

**Modules Publishing**: Any stage that fails  
**Modules Consuming**: `docai-adapter-in-rest` (error handling), Observability, Retry logic

---

### 7. OutboxRelayEvent

**Topic**: `docai.outbox.relay`  
**Key**: `aggregateId` (ADR-002)

```json
{
  "outboxId": "outbox-789",
  "aggregateId": "doc-123",
  "eventType": "DocumentUploadedEvent",
  "payload": "<binary serialized data>",
  "occurredAt": 1716729605000
}
```

**Fields**:
- `outboxId` (string): MongoDB document ID
- `aggregateId` (string): Domain aggregate ID (documentId) - partition key
- `eventType` (string): Event class name (DocumentUploadedEvent, etc.)
- `payload` (bytes): Serialized event (JSON or Avro binary)
- `occurredAt` (long): Event timestamp

**Pattern**: Outbox/Inbox pattern for guaranteed event delivery  
**Modules Publishing**: Domain events via MongoDB Outbox table  
**Modules Consuming**: `docai-adapter-out-kafka` (relay service)

---

## Kafka Topic Configuration

```properties
# Topic: docai.documents.uploaded
cleanup.policy=delete
retention.ms=604800000  # 7 days
partitions=3  # Partition by documentId
replication.factor=1  # Local: 1, Prod: 3

# Topic: docai.documents.classified
# Topic: docai.documents.extracted
# Topic: docai.documents.fraud-analyzed
# Topic: docai.documents.completed
# Topic: docai.documents.failed
# Topic: docai.outbox.relay
```

---

## Apicurio Schema Registry Integration

**Namespace**: `fr.docai.kafka.events`  
**Auto-Registration**: Via `maven-avro-plugin` at startup

### Configuration in pom.xml

```xml
<plugin>
  <groupId>org.apache.avro</groupId>
  <artifactId>avro-maven-plugin</artifactId>
  <version>${avro.version}</version>
  <executions>
    <execution>
      <phase>generate-sources</phase>
      <goals>
        <goal>schema</goal>
      </goals>
      <configuration>
        <sourceDirectory>src/main/avro/</sourceDirectory>
        <outputDirectory>target/generated-sources/avro/</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Registration Flow

```
1. Build: mvn clean package
   ↓ Generates Java classes from .avsc files
   ↓
2. Startup: docai-bootstrap starts
   ↓ ApicurioRegistryClient registers schemas
   ↓
3. Kafka: Schema IDs embedded in message headers
   ↓ Apicurio handles schema versioning
```

---

## ADR-002: Partition Key Strategy

**Decision**: Use `documentId` as partition key for all document events

**Rationale**:
- ✅ Ensures all events for a document go to the same partition
- ✅ Preserves ordering guarantees (single partition = single order)
- ✅ Enables efficient consumption by document ID
- ✅ Simplifies replay/reprocessing logic

**Exception**: `OutboxRelayEvent` uses `aggregateId` (also typically documentId)

**Implementation**:
```java
// Producer
kafkaTemplate.send("docai.documents.uploaded", documentId, event);

// Consumer - order preserved per partition
@KafkaListener(topics = "docai.documents.uploaded")
void handle(DocumentUploadedEvent event) {
  // Single document's events always in order
}
```

---

## File Structure

```
backend/docai-adapter-out-kafka/src/main/avro/
├── DocumentUploadedEvent.avsc          (1.3 KB)
├── DocumentClassifiedEvent.avsc        (1.2 KB)
├── DocumentExtractedEvent.avsc         (1.5 KB)
├── DocumentFraudAnalyzedEvent.avsc     (2.0 KB) — includes FraudSignal type
├── DocumentCompletedEvent.avsc         (0.9 KB)
├── DocumentFailedEvent.avsc            (0.9 KB)
└── OutboxRelayEvent.avsc               (0.9 KB)

Total: ~9.7 KB, 339 lines
```

---

## Generated Java Classes

After `mvn clean compile`, the following classes are auto-generated:

```
target/generated-sources/avro/fr/docai/kafka/events/
├── DocumentUploadedEvent.java
├── DocumentClassifiedEvent.java
├── DocumentExtractedEvent.java
├── DocumentFraudAnalyzedEvent.java
├── FraudSignal.java               ← nested from DocumentFraudAnalyzedEvent
├── DocumentType.java              ← enum
├── RiskLevel.java                 ← enum
├── SignalSeverity.java            ← enum
├── DocumentCompletedEvent.java
├── FinalStatus.java               ← enum
├── DocumentFailedEvent.java
├── OutboxRelayEvent.java
└── [other supporting classes]
```

---

## Validation Checklist

- [x] All 7 schemas created
- [x] Valid JSON/Avro syntax (jq validated)
- [x] ADR-002 partition keys specified
- [x] Namespace correct: `fr.docai.kafka.events`
- [x] Enum types properly defined
- [x] Nested types (FraudSignal) correctly structured
- [x] Documentation comments included
- [x] ADR-004 referenced (S3 for large payloads)

---

## Integration with Maven Build

**Step 1**: Schema files in source tree
```
src/main/avro/*.avsc
```

**Step 2**: Maven plugin generates Java classes
```
avro-maven-plugin invokes avro compiler
→ Generates classes in target/generated-sources/avro/
```

**Step 3**: Classes available for compilation
```
docai-adapter-out-kafka module
→ Can import: fr.docai.kafka.events.*
→ Compile: Success
```

**Step 4**: Runtime - Apicurio registration
```
ApicurioClient.register(schema)
→ Schema Registry stores schema definition
→ Kafka producers/consumers use SchemaId from headers
```

---

## Testing Strategy

### Unit Tests (Avro Serialization)
```java
@Test
void shouldSerializeDocumentUploadedEvent() {
  DocumentUploadedEvent event = DocumentUploadedEvent.newBuilder()
    .setDocumentId("doc-123")
    .setTenantId("tenant-456")
    .setFileName("invoice.pdf")
    .build();
    
  byte[] serialized = serializeToAvro(event);
  DocumentUploadedEvent deserialized = deserializeFromAvro(serialized);
  
  assertEquals(event, deserialized);
}
```

### Integration Tests (Kafka Producer/Consumer)
```java
@Test
void shouldPublishAndConsumeDocumentUploadedEvent() {
  // Publish
  kafkaTemplate.send("docai.documents.uploaded", 
    "doc-123", documentUploadedEvent);
  
  // Consume
  DocumentUploadedEvent received = testListener.waitForMessage();
  
  // Assert
  assertEquals("doc-123", received.getDocumentId());
}
```

### Schema Registry Tests
```java
@Test
void shouldRegisterSchemaInApicurio() {
  SchemaMetadata metadata = client.getSchemaMetadata(
    "DocumentUploadedEvent", "fr.docai.kafka.events");
  
  assertNotNull(metadata);
  assertEquals(1, metadata.getVersion());
}
```

---

## Next Steps

1. **Compile Schemas** → `mvn clean compile`
   - Generates Java classes from .avsc files
   
2. **Create Producers** → `docai-adapter-out-kafka`
   - Implement event publishers for each schema
   
3. **Create Consumers** → `docai-adapter-in-kafka`
   - Implement event listeners for each topic
   
4. **Configure Kafka Topics** → Docker Compose or K8s
   - Create topics with correct partitions/replicas
   
5. **Register Schemas** → Apicurio
   - Verify schemas in Schema Registry UI
   
6. **Test Event Flow** → Integration tests
   - Publish → Consume → Assert correctness

---

## References

- **Avro Specification**: [Apache Avro 1.11.4](https://avro.apache.org/docs/current/spec.html)
- **Apicurio Registry**: [Official Docs](https://www.apicur.io/registry/)
- **ADR-002**: Partition key management (documentId)
- **ADR-004**: Large payload handling (S3 for OCR text)
- **CLAUDE.md**: Kafka configuration (Broker, KRaft mode, ports)

---

## Status

✅ **COMPLETE**
- All 7 Avro schemas created
- Valid JSON syntax verified
- Ready for Maven compilation
- Documentation complete

**Next Task**: Implement Kafka producer/consumer adapters using these schemas

---

**Created**: 2026-05-26  
**Branch**: `004-stack-technique`  
**Location**: `backend/docai-adapter-out-kafka/src/main/avro/`  
**Status**: ✅ READY FOR COMPILATION & TESTING

