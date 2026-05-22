---
name: docai-commons-implement
description: Implémente un composant du module docai-commons (TenantContext, OutboxMessage, QuotaPort, ResilientKafkaConsumer, AuditPort, IdempotencyPort, AbstractIntegrationTest). Utiliser quand on demande de créer ou compléter un commons avant de démarrer un module métier. Les commons sont obligatoires avant tout module.
---

# DocAI — Implémenter un Commons

## Ordre obligatoire d'implémentation

| # | Commons | Utilisé par | Durée |
|---|---------|-------------|-------|
| 1 | **commons-multitenancy** | Tous les adapters MongoDB + REST | 2 jours |
| 2 | **commons-api** | Tous les controllers REST | 1 jour |
| 3 | **commons-audit** | Tous les use cases sensibles | 1 jour |
| 4 | **commons-outbox** | Tous les publishers Kafka | 2 jours |
| 5 | **commons-quota** | Module 1 + Module 7 Billing | 1 jour |
| 6 | **commons-kafka** | Tous les consumers Kafka | 2 jours |
| 7 | **commons-testing** | Tous les tests d'intégration | 1 jour |

## commons-multitenancy

```java
// TenantContext — ThreadLocal holder
public final class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();
    public static void set(String tenantId) { TENANT.set(tenantId); }
    public static String get() {
        String t = TENANT.get();
        if (t == null) throw new TenantNotSetException("tenant-id absent du contexte");
        return t;
    }
    public static Optional<String> getOptional() { return Optional.ofNullable(TENANT.get()); }
    public static void clear() { TENANT.remove(); }
}

// TenantJwtFilter — Extrait tenant_id du JWT Keycloak
@Component
@Order(1)
public class TenantJwtFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        try {
            String tenantId = extractTenantFromJwt(req); // claim tenant_id
            TenantContext.set(tenantId);
            MDC.put("tenantId", tenantId);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }
}
```

## commons-outbox

```java
public record OutboxMessage(
    UUID id,
    String aggregateType,   // "Document", "Tenant", "Subscription"
    String aggregateId,
    String eventType,       // "DocumentUploaded", "DocumentClassified"
    String payload,         // JSON sérialisé
    String tenantId,
    String partitionKey,    // = documentId pour pipeline, tenantId sinon
    Instant createdAt,
    OutboxStatus status     // PENDING, PUBLISHED, FAILED
) {}

public enum OutboxStatus { PENDING, PUBLISHED, FAILED }

// Repository MongoDB de l'outbox
public interface OutboxRepository {
    void save(OutboxMessage message);
    List<OutboxMessage> findPending(int batchSize);       // ORDER BY createdAt ASC
    void markPublished(UUID messageId);
    void markFailed(UUID messageId, String reason, int attempts);
    void deletePublishedOlderThan(Instant before);        // Nettoyage quotidien
}
```

## commons-quota

```java
public enum QuotaStatus { ALLOWED, QUOTA_WARNING_80, QUOTA_WARNING_95, QUOTA_EXCEEDED }

public record QuotaCheckResult(
    QuotaStatus status,
    long currentUsage,
    long limit,
    long remaining,
    Instant resetAt
) {}

public interface QuotaPort {
    // Vérifie ET incrémente atomiquement via script Lua (ADR-001)
    QuotaCheckResult checkAndConsume(String tenantId, int amount);
    QuotaCheckResult getCurrentUsage(String tenantId);
    void reset(String tenantId); // Job mensuel
}

// Annotation AOP
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuotaProtected {
    int amount() default 1;
    String resource() default "documents";
}
```

## commons-kafka

```java
// Base class pour tous les consumers — étendre cette classe
public abstract class ResilientKafkaConsumer<T> {
    public abstract void handle(T event, KafkaConsumerContext context);

    protected final void processWithIdempotence(ConsumerRecord<String, T> record) {
        String offsetKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        if (isAlreadyProcessed(offsetKey)) return; // Idempotence via Valkey
        handle(record.value(), buildContext(record));
        markAsProcessed(offsetKey);
    }
    protected final void sendToDlq(ConsumerRecord<String, T> record, Exception cause) { ... }
    protected final boolean isAlreadyProcessed(String offsetKey) { ... }
    protected final void markAsProcessed(String offsetKey) { ... }
}

public record KafkaConsumerContext(
    String tenantId, String correlationId, String traceId, int attempt
) {}

// TTL avec jitter ±10% (ADR-003) — évite les thundering herds
public final class JitterTtl {
    public static Duration withJitter(Duration baseTtl) {
        double jitter = 0.9 + Math.random() * 0.2; // ±10%
        return Duration.ofMillis((long)(baseTtl.toMillis() * jitter));
    }
    public static Duration fixed(Duration ttl) { return ttl; }
}
```

## commons-audit

```java
public record AuditEvent(
    UUID id, String tenantId, String userId, String action,
    String resourceType, String resourceId,
    Map<String, Object> metadata, Instant occurredAt,
    String ipAddress, boolean isSupportAccess
) {}

public interface AuditPort {
    void record(AuditEvent event);  // Async — ne bloque pas
    List<AuditEvent> findByTenant(String tenantId, Pageable pageable);
    List<AuditEvent> findByDocument(String documentId);
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();        // "DOCUMENT_UPLOADED"
    String resourceType();  // "Document"
}
```

## commons-api

```java
// GlobalExceptionHandler — RFC 7807 ProblemDetail (dans docai-adapter-in-rest)
// Catalogue codes erreur :
// DOC-001 (400), DOC-002 (409 doublon), DOC-003 (404), EXT-001 (422),
// FRD-001 (200 rejeté), QUOTA-001 (429), RATE-001 (429), AUTH-001 (401), AUTH-002 (403)

// ApiResponse — wrapper pagination obligatoire (BR-PAG-001 à 008)
public record ApiResponse<T>(
    T data,
    PageMetadata page   // null si réponse non paginée
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }
    public static <T> ApiResponse<T> paginated(T data, PageMetadata page) {
        return new ApiResponse<>(data, page);
    }
}

public record PageMetadata(
    int number, int size, long totalElements, int totalPages,
    boolean first, boolean last
) {}
```

## commons-testing

```java
// AbstractIntegrationTest — base pour tous les tests d'intégration
// ADR-008 : reuse=true obligatoire pour éviter OOM GitHub Actions runners (7GB RAM)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    static MongoDBContainer mongo =
        new MongoDBContainer("mongo:7.0").withReuse(true); // ADR-008

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
            .withReuse(true); // ADR-008

    @Container
    static GenericContainer<?> valkey =
        new GenericContainer<>("valkey/valkey:8")
            .withExposedPorts(6379)
            .withReuse(true); // ADR-008

    @Container
    static LocalStackContainer localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
            .withServices(S3)
            .withReuse(true); // ADR-008 — pour les tests S3

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", valkey::getHost);
        registry.add("spring.data.redis.port",
            () -> valkey.getMappedPort(6379).toString());
    }
}

// DocumentTestBuilder — Test Data Builder Pattern
public class DocumentTestBuilder {
    private String tenantId = "acme-corp";
    private String type = "FACTURE";
    private DocumentStatus status = DocumentStatus.PENDING;

    public static DocumentTestBuilder aDocument() { return new DocumentTestBuilder(); }
    public DocumentTestBuilder withTenantId(String t) { this.tenantId = t; return this; }
    public DocumentTestBuilder withType(String t) { this.type = t; return this; }
    public DocumentTestBuilder withStatus(DocumentStatus s) { this.status = s; return this; }
    public Document build() { return new Document(/* ... */); }
}
```

## IdempotencyPort

```java
// Dans fr.docai.domain.port.out/
public interface IdempotencyPort {
    // Vérifier si une clé d'idempotence a déjà été utilisée
    boolean isAlreadyProcessed(String key);
    // Marquer comme traitée — TTL fixe 24h (jamais de jitter pour l'idempotence)
    void markAsProcessed(String key);
    // Upload idempotence — clé = X-Idempotency-Key header
    Optional<String> getExistingDocumentId(String idempotencyKey);
    void storeIdempotencyKey(String idempotencyKey, String documentId);
}
// Clés Valkey :
//   Upload    : idempotency:{X-Idempotency-Key}         TTL 24h fixe
//   Consumer  : idempotent:{topic}:{partition}:{offset}  TTL 24h fixe
```

## Checklist

- [ ] Module Maven `docai-commons-{nom}` créé dans le POM parent
- [ ] Interfaces implémentées conformément aux signatures ci-dessus
- [ ] Tests unitaires ≥ 90% coverage (domaine critique)
- [ ] `JitterTtl.withJitter()` sur tous les TTL Valkey > 1h (sauf rate limiting et idempotence — ADR-003)
- [ ] `TenantContext.clear()` toujours dans un bloc `finally`
- [ ] `AbstractIntegrationTest` avec `withReuse(true)` sur tous les conteneurs (ADR-008)
- [ ] Commons publié dans le repository Maven local avant de démarrer le module métier
- [ ] `ApiResponse.paginated()` utilisé sur tous les endpoints liste (BR-PAG-008)
