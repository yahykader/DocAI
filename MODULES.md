# DocAI - 11 Hexagonal Architecture Modules

Each module follows the **hexagonal architecture** (ports & adapters) pattern with strict dependency rules enforced by ArchUnit.

---

## 1. `docai-domain` (Pure Domain Model - DDD)

**Responsibility**: Core business logic, entities, value objects, domain ports (interfaces).

**Dependencies**: **ZERO** external dependencies (except test deps: JUnit, Mockito)

**Key Classes**:
- **Entities**: `Document`, `Extraction`, `FraudAnalysis`, `Pipeline`
- **Value Objects**: `DocumentId`, `TenantId`, `Money`, `Percentage`
- **Ports (Interfaces)**:
  - `DocumentRepositoryPort` (persistence)
  - `DocumentValidatorPort` (external validation)
  - `OCRServicePort` (document processing)
  - `FraudDetectorPort` (fraud analysis)
  - `NotificationPort` (event publishing)

**Testing**:
- Unit tests: `*Test.java`
- Coverage target: **90%** (highest requirement)
- Mutation testing: **85%** (PIT)

**Constraints** (ArchUnit Rule 1-6):
- ❌ No `org.springframework.*`
- ❌ No `org.mongodb.*`
- ❌ No `org.springframework.kafka.*`
- ❌ No `software.amazon.awssdk.*`
- ❌ No `io.lettuce.*` (Redis)
- ❌ No `dev.langchain4j.*`, `com.stripe.*`, etc.

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-domain</artifactId>
```

---

## 2. `docai-application` (Use Cases & Services)

**Responsibility**: Application services, use cases, DTOs, mapping logic (orchestration layer).

**Dependencies**: `docai-domain` + Spring Framework (no web)

**Key Classes**:
- **Use Cases**: `CreateDocumentUseCase`, `ExtractDataUseCase`, `AnalyzeFraudUseCase`
- **Application Services**: `DocumentService`, `ExtractionService`, `FraudService`
- **DTOs**: `CreateDocumentRequest`, `DocumentResponse`, `ExtractionResult`
- **Mappers**: `DocumentMapper`, `ExtractionMapper` (MapStruct)

**Dependencies**:
- ✅ Spring Framework (`spring-context`, `spring-beans`)
- ✅ MapStruct (DTO mapping)
- ✅ Lombok (reducing boilerplate)
- ❌ Spring Web (Web is for adapters)
- ❌ Database drivers

**Testing**:
- Unit tests: `*Test.java`
- Coverage target: **80%**

**Constraints** (ArchUnit Rule 7):
- ❌ No dependencies on adapters (`fr.docai.adapter.*`)

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-application</artifactId>
```

---

## 3. `docai-adapter-in-rest` (Input: HTTP REST Controllers)

**Responsibility**: REST endpoints, HTTP request/response handling, OpenAPI documentation.

**Dependencies**: `docai-domain`, `docai-application`, Spring Web, Keycloak, SpringDoc

**Key Classes**:
- **Controllers**: `DocumentController`, `ExtractionController`, `FraudController`
- **Security**: `SecurityConfig`, `JwtAuthenticationFilter`
- **Validation**: `RequestValidator`
- **Exception Handlers**: `GlobalExceptionHandler`

**Features**:
- ✅ Spring Boot Web (`spring-boot-starter-web`)
- ✅ Security (`spring-boot-starter-security`, Keycloak)
- ✅ API Documentation (SpringDoc OpenAPI / Swagger)
- ✅ Input validation (`spring-boot-starter-validation`)
- ✅ Resilience4j (circuit breaker, rate limiting)
- ✅ Observability (Micrometer metrics)

**Testing**:
- Unit tests: `*Test.java`
- Integration tests: `*IT.java` (with MockMvc, WireMock)

**Endpoints**:
```
POST   /api/documents              → Upload document
GET    /api/documents/{id}         → Retrieve document
GET    /api/documents              → List documents (paginated)
POST   /api/documents/{id}/extract → Trigger extraction
GET    /api/documents/{id}/fraud   → Get fraud analysis
```

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-in-rest</artifactId>
```

---

## 4. `docai-adapter-in-kafka` (Input: Event Consumer)

**Responsibility**: Kafka consumer for event-driven async processing.

**Dependencies**: `docai-domain`, `docai-application`, Spring Kafka, Apicurio (schema registry)

**Key Classes**:
- **Consumers**: `DocumentUploadedEventConsumer`, `ExtractionRequestedConsumer`
- **Event Mappers**: Avro schema integration
- **Error Handlers**: Dead-letter queue handling

**Features**:
- ✅ Spring Kafka (`spring-kafka`)
- ✅ Schema Registry (Apicurio + Avro)
- ✅ Resilience4j (retry, circuit breaker)
- ✅ BDD Testing (Cucumber)

**Event Topics**:
```
documents.uploaded        → Triggers OCR + extraction
documents.extraction-requested → Requests LLM analysis
fraud.detection-triggered → Fraud analysis needed
```

**Testing**:
- Integration tests: `*IT.java` (TestContainers + Kafka)
- Cucumber BDD: `features/` directory

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-in-kafka</artifactId>
```

---

## 5. `docai-adapter-out-mongodb` (Output: Persistence)

**Responsibility**: MongoDB repositories, database migrations (Mongock), entity persistence.

**Dependencies**: `docai-domain`, `docai-application`, Spring Data MongoDB, Mongock

**Key Classes**:
- **Repositories**: `DocumentRepository`, `ExtractionRepository`, `FraudAnalysisRepository`
- **MongoDB Documents**: `DocumentDocument`, `ExtractionDocument` (persistence models)
- **Migrations**: `Mongock` change sets in `db.changelog` package

**Features**:
- ✅ Spring Data MongoDB
- ✅ Mongock for database migrations
- ✅ MongoDB Replica Set (transactions support)
- ✅ Indexes and query optimization

**Database Collections**:
```
documents          → Document metadata
extractions        → Extracted data
fraud_analyses     → Fraud detection results
processing_logs    → Audit trail
```

**Testing**:
- Integration tests: `*IT.java` (TestContainers MongoDB)

**Constraints**:
- Implement `DocumentRepositoryPort` from `docai-domain`

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-out-mongodb</artifactId>
```

---

## 6. `docai-adapter-out-kafka` (Output: Event Producer)

**Responsibility**: Kafka event publishing, message serialization (Avro).

**Dependencies**: `docai-domain`, `docai-application`, Spring Kafka, Apicurio

**Key Classes**:
- **Producers**: `DocumentEventProducer`, `ExtractionEventProducer`, `FraudEventProducer`
- **Event Models**: Avro-generated classes for each event type
- **Serialization**: Apicurio schema registry integration

**Features**:
- ✅ Spring Kafka producer
- ✅ Avro serialization
- ✅ Resilience4j (circuit breaker for publishing)
- ✅ Transactional guarantees (exactly-once semantics option)

**Published Events**:
```
documents.uploaded          → When document stored
documents.extraction-started → Extraction began
documents.extraction-completed → Extraction done + results
fraud.analysis-completed    → Fraud verdict published
```

**Testing**:
- Integration tests: `*IT.java` (TestContainers Kafka)

**Constraints**:
- Implement `DocumentEventPort` from `docai-domain`

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-out-kafka</artifactId>
```

---

## 7. `docai-adapter-out-valkey` (Output: Cache)

**Responsibility**: Redis/Valkey caching, session storage, rate limiting state.

**Dependencies**: `docai-domain`, `docai-application`, Spring Data Redis (Lettuce client)

**Key Classes**:
- **Cache Managers**: `DocumentCacheManager`, `ExtractionCacheManager`
- **Cache Keys**: `CacheKeyGenerator`
- **TTL Policies**: Configurable cache expiration

**Features**:
- ✅ Spring Data Redis with Lettuce client
- ✅ Spring Cache abstraction (`@Cacheable`, `@CacheEvict`)
- ✅ Distributed sessions (if needed)
- ✅ Rate limiter state (Bucket4j)

**Cache Policies**:
```
documents:{tenantId}:{docId}        → 24h TTL
extractions:{tenantId}:{extractId}  → 48h TTL
fraud-scores:{tenantId}:{docId}     → 7 days TTL
```

**Testing**:
- Integration tests: `*IT.java` (TestContainers Redis)

**Constraints**:
- Implement `CachePort` from `docai-domain`

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-out-valkey</artifactId>
```

---

## 8. `docai-adapter-out-ai` (Output: LLM Integration)

**Responsibility**: LLM API integration (Claude, OpenAI, etc.), prompt engineering, response parsing.

**Dependencies**: `docai-domain`, `docai-application`, LangChain4j, HTTP client

**Key Classes**:
- **AI Services**: `ClaudeAIService`, `DocumentAnalyzerAI`, `FraudDetectionAI`
- **Prompts**: `DocumentExtractionPrompt`, `FraudAnalysisPrompt`
- **Response Parsers**: `ExtractionResponseParser`, `FraudScoreParser`
- **Error Handling**: Retry logic, fallback strategies

**Features**:
- ✅ LangChain4j (Claude Anthropic support)
- ✅ Streaming responses (for real-time UI)
- ✅ Resilience4j (circuit breaker, retry, timeout)
- ✅ Cost tracking (tokens used)

**Supported Models**:
```
claude-3-5-sonnet-20241022  → Fast + accurate, recommended
claude-3-opus-20250219      → Most capable (slower, expensive)
```

**Testing**:
- Integration tests: `*IT.java` (WireMock for API mocking)

**Constraints**:
- Implement `DocumentAnalyzerPort`, `FraudDetectorPort` from `docai-domain`
- Handle API errors gracefully

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-out-ai</artifactId>
```

---

## 9. `docai-adapter-out-storage` (Output: Cloud Storage)

**Responsibility**: AWS S3 document storage, file upload/download, archival.

**Dependencies**: `docai-domain`, `docai-application`, AWS SDK v2, Spring Cloud AWS

**Key Classes**:
- **Storage Services**: `DocumentStorageService`, `ArchiveService`
- **S3 Operations**: Upload, download, delete, list
- **File Processing**: PDF extraction, image OCR preparation

**Features**:
- ✅ AWS SDK v2 (modern, async-capable)
- ✅ Spring Cloud AWS S3 integration
- ✅ Multipart upload for large files
- ✅ Signed URLs for secure download links
- ✅ Resilience4j (retry, circuit breaker)

**S3 Bucket Structure**:
```
s3://docai-dev/
├── documents/
│   └── {tenantId}/{documentId}/
│       ├── original.pdf
│       ├── pages-*.png
│       └── metadata.json
├── extractions/
│   └── {tenantId}/{extractionId}/results.json
└── archive/
    └── {year}/{month}/{documentId}.tar.gz
```

**Testing**:
- Integration tests: `*IT.java` (TestContainers with LocalStack or MinIO)

**Constraints**:
- Implement `DocumentStoragePort` from `docai-domain`

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-out-storage</artifactId>
```

---

## 10. `docai-adapter-out-external` (Output: External APIs)

**Responsibility**: Integration with external validation APIs (INSEE, BAN, RPPS, etc.).

**Dependencies**: `docai-domain`, `docai-application`, HTTP client, Resilience4j

**Key Classes**:
- **External Clients**: `INSEEClient`, `BANClient`, `RPPSClient`
- **Validators**: `SirenSiretValidator`, `AddressValidator`, `ProfessionalValidator`
- **Rate Limiters**: Bucket4j for API quota management

**Integrations**:
```
INSEE       → SIREN/SIRET validation (French companies)
BAN         → Address validation (French addresses)
RPPS        → Professional registry (healthcare practitioners)
```

**Features**:
- ✅ Resilience4j (circuit breaker, retry, timeout, bulkhead)
- ✅ Bucket4j (rate limiting to respect API quotas)
- ✅ WireMock testing (mock external APIs)
- ✅ Graceful degradation (fallback on API unavailability)

**Testing**:
- Integration tests: `*IT.java` (WireMock for external API mocking)

**Constraints**:
- Implement `ExternalValidatorPort` from `docai-domain`
- Must handle API errors without crashing (circuit breaker)

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-adapter-out-external</artifactId>
```

---

## 11. `docai-bootstrap` (Spring Boot Entry Point)

**Responsibility**: Application bootstrap, configuration, main entry point, ArchUnit validation.

**Dependencies**: **All** modules (adapters + application + domain)

**Key Classes**:
- **Main**: `DocaiApplication.java` (Spring Boot entry point)
- **Configuration**: `WebConfig`, `SecurityConfig`, `MongoConfig`, `KafkaConfig`, `CacheConfig`, `OTelConfig`
- **ArchUnit Tests**: `HexagonalArchitectureTest` (12 rules validation)

**Features**:
- ✅ Spring Boot 4.0.x
- ✅ Keycloak security integration
- ✅ OpenTelemetry tracing (OTEL collector)
- ✅ Prometheus metrics export
- ✅ Graceful shutdown
- ✅ Health checks (`/actuator/health`)

**Configuration Files**:
```
application.yml              → Main configuration
application-local.yml        → Local development (docker-compose)
application-production.yml   → Production settings
```

**ArchUnit Tests** (12 rules):
1. Domain → No Spring
2. Domain → No MongoDB
3. Domain → No Kafka
4. Domain → No AWS SDK
5. Domain → No Redis
6. Domain → No external libraries
7. Application → No adapters
8. Adapter-In → Only domain/application
9. Adapter-Out → Only domain/application
10. Bootstrap → All layers allowed
11. No cyclic dependencies
12. Ports must be interfaces

**Testing**:
- Unit tests: `*Test.java`
- Integration tests: `*IT.java` (full Spring context)
- Architecture tests: `HexagonalArchitectureTest.java`

**Maven**:
```xml
<groupId>fr.docai</groupId>
<artifactId>docai-bootstrap</artifactId>
<packaging>jar</packaging>
```

**Build & Run**:
```bash
# Build
mvn clean package -pl docai-bootstrap

# Run locally
mvn spring-boot:run -pl docai-bootstrap

# Run in Docker
docker build -t docai:latest -f docai-bootstrap/Dockerfile .
docker run -p 8080:8080 --env-file .env docai:latest
```

---

## Dependency Tree

```
docai-domain
    ├─ docai-application
    │   ├─ docai-adapter-in-rest
    │   │   └─ docai-bootstrap (SPRING BOOT)
    │   ├─ docai-adapter-in-kafka
    │   │   └─ docai-bootstrap
    │   ├─ docai-adapter-out-mongodb
    │   │   └─ docai-bootstrap
    │   ├─ docai-adapter-out-kafka
    │   │   └─ docai-bootstrap
    │   ├─ docai-adapter-out-valkey
    │   │   └─ docai-bootstrap
    │   ├─ docai-adapter-out-ai
    │   │   └─ docai-bootstrap
    │   ├─ docai-adapter-out-storage
    │   │   └─ docai-bootstrap
    │   └─ docai-adapter-out-external
    │       └─ docai-bootstrap
```

---

## Testing Matrix

| Module | Unit | Integration | Coverage | Mutation | ArchUnit |
|--------|------|-------------|----------|----------|----------|
| docai-domain | ✅ | — | 90% | 85% | — |
| docai-application | ✅ | — | 80% | — | — |
| docai-adapter-in-rest | ✅ | ✅ (MockMvc) | 80% | — | — |
| docai-adapter-in-kafka | ✅ | ✅ (TestContainers) | 80% | — | — |
| docai-adapter-out-mongodb | ✅ | ✅ (TestContainers) | 80% | — | — |
| docai-adapter-out-kafka | ✅ | ✅ (TestContainers) | 80% | — | — |
| docai-adapter-out-valkey | ✅ | ✅ (TestContainers) | 80% | — | — |
| docai-adapter-out-ai | ✅ | ✅ (WireMock) | 80% | — | — |
| docai-adapter-out-storage | ✅ | ✅ (WireMock/LocalStack) | 80% | — | — |
| docai-adapter-out-external | ✅ | ✅ (WireMock) | 80% | — | — |
| docai-bootstrap | ✅ | ✅ | 80% | — | ✅ (12 rules) |

---

## Development Order

1. **docai-domain** (foundation)
2. **docai-application** (business logic)
3. **docai-adapter-in-rest** (REST API)
4. **docai-adapter-in-kafka** (event consumption)
5. **docai-adapter-out-mongodb** (persistence)
6. **docai-adapter-out-kafka** (event publishing)
7. **docai-adapter-out-valkey** (caching)
8. **docai-adapter-out-ai** (LLM integration)
9. **docai-adapter-out-storage** (S3 storage)
10. **docai-adapter-out-external** (external APIs)
11. **docai-bootstrap** (wiring everything together)
