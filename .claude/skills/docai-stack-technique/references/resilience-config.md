# DocAI — Configurations Resilience4j Complètes

> Référence : DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Module 1.B Phase 4

---

## application.yml — Resilience4j complet

```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm:                              # LLM (OpenAI, Mistral)
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.HttpServerErrorException
      ocr:                              # OCR Tess4J
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 2
      insee:                            # API INSEE SIRENE
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      ban:                              # API BAN Géoplateforme
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      rpps:                             # API FHIR ANS
        sliding-window-size: 8
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      tika:                             # Apache Tika
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      opencv:                           # JavaCV / OpenCV
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      s3:                               # Amazon S3
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s

  retry:
    instances:
      llm:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException$ServiceUnavailable
      ocr:
        max-attempts: 3
        wait-duration: 2s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
      insee:
        max-attempts: 2
        wait-duration: 2s
        enable-exponential-backoff: false
      ban:
        max-attempts: 2
        wait-duration: 2s
      rpps:
        max-attempts: 2
        wait-duration: 3s
      s3:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0

  bulkhead:
    instances:
      llm:
        max-concurrent-calls: 20        # Threads LLM max simultanés
        max-wait-duration: 100ms
      ocr:
        max-concurrent-calls: 10        # Threads OCR max simultanés
        max-wait-duration: 200ms
      insee:
        max-concurrent-calls: 5
        max-wait-duration: 50ms
      ban:
        max-concurrent-calls: 5
        max-wait-duration: 50ms
      rpps:
        max-concurrent-calls: 5
        max-wait-duration: 50ms
      tika:
        max-concurrent-calls: 5
        max-wait-duration: 100ms
      opencv:
        max-concurrent-calls: 5
        max-wait-duration: 100ms

  timelimiter:
    instances:
      llm:
        timeout-duration: 30s
        cancel-running-future: true
      ocr:
        timeout-duration: 60s
        cancel-running-future: true
      insee:
        timeout-duration: 5s
      ban:
        timeout-duration: 5s
      rpps:
        timeout-duration: 5s
      tika:
        timeout-duration: 15s           # Analyseur Tika max 15s
      opencv:
        timeout-duration: 15s           # Analyseur visuel max 15s (BR-VIS-003)
```

---

## Pattern d'utilisation dans les Adapters

```java
// Exemple : LLM Adapter avec Resilience4j complet
@Component
public class OpenAiLlmAdapter implements LlmPort {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;

    public ExtractionResult extract(String ocrText, DocumentType type) {
        // Chaîne Resilience4j : TimeLimiter → CircuitBreaker → Retry → Bulkhead
        Supplier<ExtractionResult> supplier = Bulkhead.decorateSupplier(bulkhead,
            CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry,
                    () -> callOpenAI(ocrText, type)
                )
            )
        );

        try {
            return timeLimiter.executeFutureSupplier(
                () -> CompletableFuture.supplyAsync(supplier)
            );
        } catch (CallNotPermittedException e) {
            // Circuit Breaker OPEN → fallback
            log.warn("Circuit Breaker OPEN service=LLM documentType={}", type);
            return ExtractionResult.partial(); // → NEEDS_REVIEW
        } catch (BulkheadFullException e) {
            // Bulkhead saturé
            log.warn("Bulkhead full service=LLM");
            throw new ServiceBusyException("LLM service temporarily busy");
        }
    }
}
```

---

## Métriques Resilience4j à exposer

```java
// Enregistrement automatique via Micrometer
@Bean
public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)
                               .bindTo(meterRegistry);
    return registry;
}
// Métriques automatiques :
// resilience4j_circuitbreaker_state{name="llm"} 0=CLOSED, 1=OPEN, 2=HALF_OPEN
// resilience4j_circuitbreaker_calls_total{name="llm", kind="successful|failed|not_permitted"}
// resilience4j_retry_calls_total{name="llm", kind="successful_without_retry|failed_with_retry"}
```
