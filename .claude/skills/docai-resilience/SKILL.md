---
name: docai-resilience
description: Ajoute la résilience Resilience4j sur un adapter externe DocAI (Circuit Breaker, Retry, Bulkhead, Timeout). Utiliser quand on implémente un adapter vers LLM, OCR, API INSEE, API BAN, API RPPS, Apache Tika, OpenCV/JavaCV, Amazon S3 ou Stripe. Applique les seuils exacts V15.0 (timeout 15s BR-VIS-003 pour Tika/OpenCV) et les fallbacks fail-safe.
---

# DocAI — Ajouter la Résilience Resilience4j

## Seuils par service — RÉFÉRENCE OBLIGATOIRE

| Service | Circuit Breaker | Retry | Bulkhead | Timeout |
|---------|----------------|-------|----------|---------|
| LLM API (OpenAI/Mistral) | 50% / 10 calls | 3× backoff expo (1s,2s,4s) | 20 threads | 30s |
| OCR Engine (Tess4J) | 50% / 5 calls | 3× backoff expo | 10 threads | 60s |
| API INSEE | 60% / 5 calls | 3× backoff expo (1s,2s,4s) | 5 threads | 5s |
| API BAN | 60% / 5 calls | 3× backoff expo (500ms) | 5 threads | 5s |
| API RPPS | 60% / 8 calls | 2× fixe (1s) | 5 threads | 5s |
| Apache Tika | 50% / 5 calls | 2× backoff expo (1s) | 5 threads | **15s (BR-VIS-003)** |
| OpenCV/JavaCV | 50% / 5 calls | 1× | 5 threads | **15s (BR-VIS-003)** |
| Amazon S3 | 50% / 10 calls | 3× backoff expo (1s,2s,4s) | 20 threads | 30s |

## Configuration application.yml — à ajouter

```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
      ocr:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
      insee:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      ban:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      rpps:
        sliding-window-size: 8
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      tika:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      opencv:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      s3:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  retry:
    instances:
      llm:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2   # 1s → 2s → 4s
      insee:
        max-attempts: 3
        wait-duration: 1s
      ban:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
      rpps:
        max-attempts: 2
        wait-duration: 1s
      tika:
        max-attempts: 2
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
      s3:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
  bulkhead:
    instances:
      llm:
        max-concurrent-calls: 20
        max-wait-duration: 0ms
      ocr:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
      insee:
        max-concurrent-calls: 5
        max-wait-duration: 0ms
      ban:
        max-concurrent-calls: 5
        max-wait-duration: 0ms
      rpps:
        max-concurrent-calls: 5
        max-wait-duration: 0ms
      tika:
        max-concurrent-calls: 5
        max-wait-duration: 100ms
      opencv:
        max-concurrent-calls: 5
        max-wait-duration: 100ms
      s3:
        max-concurrent-calls: 20
        max-wait-duration: 0ms
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
        timeout-duration: 15s    # BR-VIS-003 OBLIGATOIRE
        cancel-running-future: true
      opencv:
        timeout-duration: 15s    # BR-VIS-003 OBLIGATOIRE
        cancel-running-future: true
      s3:
        timeout-duration: 30s
        cancel-running-future: true
```

> ⚠️ **BR-VIS-003** : `tika` et `opencv` ont un timeout de **15s strictement**. Ne jamais augmenter.

## Adapter avec Resilience4j — Pattern complet

```java
@Component
public class VisionModelAdapter implements ClassificationModelPort {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private static final Logger log = LoggerFactory.getLogger(VisionModelAdapter.class);

    public VisionModelAdapter(
        WebClient.Builder webClientBuilder,
        CircuitBreakerRegistry circuitBreakerRegistry,
        RetryRegistry retryRegistry,
        BulkheadRegistry bulkheadRegistry
    ) {
        this.webClient = webClientBuilder.baseUrl("${docai.llm.base-url}").build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("llm");
        this.retry = retryRegistry.retry("llm");
        this.bulkhead = bulkheadRegistry.bulkhead("llm");
    }

    @Override
    public ClassificationResult classify(String documentId, byte[] content) {
        Supplier<ClassificationResult> decoratedCall = Decorators
            .ofSupplier(() -> callVisionModel(documentId, content))
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .withBulkhead(bulkhead)
            .withFallback(
                List.of(CallNotPermittedException.class,  // Circuit Breaker OPEN
                        BulkheadFullException.class,
                        TimeoutException.class),
                ex -> fallback(documentId, ex)
            )
            .decorate();

        return decoratedCall.get();
    }

    private ClassificationResult callVisionModel(String documentId, byte[] content) {
        // Appel HTTP réel au LLM
        log.info("Calling vision model documentId={}", documentId);
        return webClient.post()
            .uri("/classify")
            .bodyValue(buildRequest(content))
            .retrieve()
            .bodyToMono(ClassificationResult.class)
            .block(Duration.ofSeconds(30));
    }

    // Fallback fail-safe — jamais bloquer le pipeline
    private ClassificationResult fallback(String documentId, Throwable ex) {
        log.warn("Vision model unavailable, applying fallback documentId={} reason={}",
            documentId, ex.getClass().getSimpleName());
        return ClassificationResult.needsReview(documentId, "CIRCUIT_BREAKER_OPEN");
    }
}
```

## États du Circuit Breaker — à tester obligatoirement

```
CLOSED → appels normaux
   ↓ (seuil d'échec dépassé)
OPEN → fail-fast immédiat, fallback appliqué
   ↓ (wait-duration écoulée)
HALF_OPEN → teste quelques appels
   ↓ (succès) → CLOSED
   ↓ (échec)  → OPEN
```

## Test du Circuit Breaker

```java
@Test
void should_apply_fallback_when_circuit_breaker_is_open() {
    // Forcer l'ouverture du circuit breaker
    circuitBreaker.transitionToOpenState();

    ClassificationResult result = adapter.classify("doc-123", new byte[]{});

    assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
    assertThat(result.reason()).isEqualTo("CIRCUIT_BREAKER_OPEN");
    // Vérifier qu'aucun appel HTTP n'a été fait
    wireMock.verify(0, postRequestedFor(urlEqualTo("/classify")));
}

@Test
void should_retry_3_times_before_fallback() {
    wireMock.stubFor(post("/classify").willReturn(serverError()));

    adapter.classify("doc-123", new byte[]{});

    wireMock.verify(3, postRequestedFor(urlEqualTo("/classify")));
}
```

## Métriques Resilience4j exposées automatiquement

```
resilience4j_circuitbreaker_state{name="llm"}          → 0=CLOSED, 1=OPEN, 2=HALF_OPEN
resilience4j_circuitbreaker_failure_rate{name="llm"}   → % d'échecs
resilience4j_retry_calls_total{name="llm",kind="failed_with_retry"}
resilience4j_bulkhead_available_concurrent_calls{name="llm"}
```

## Checklist

- [ ] Seuils conformes au tableau de référence ci-dessus (8 services)
- [ ] Fallback défini pour `CallNotPermittedException`, `BulkheadFullException`, `TimeoutException`
- [ ] Fallback fail-safe : document en `NEEDS_REVIEW`, jamais bloqué
- [ ] **BR-VIS-003 : timeout `tika` et `opencv` = 15s exactement** (ne pas augmenter)
- [ ] Test : Circuit Breaker CLOSED → OPEN → HALF_OPEN → CLOSED
- [ ] Test : retries avant fallback
- [ ] Logs `WARN` sur fallback avec `documentId` et `reason`
- [ ] Métriques `resilience4j_circuitbreaker_state` visibles dans Prometheus
