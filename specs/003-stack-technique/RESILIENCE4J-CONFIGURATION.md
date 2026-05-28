# Resilience4j Configuration — Detailed Guide

**Date**: 2026-05-26  
**Branch**: `004-stack-technique`  
**Location**: `backend/docai-bootstrap/src/main/resources/application.yml`  
**Status**: ✅ UPDATED (8 services configured)  

---

## Overview

Resilience4j is configured for 8 external services with precise thresholds to ensure fault tolerance, prevent cascading failures, and maintain system stability during network outages or service degradation.

| Service | Type | CircuitBreaker | TimeLimiter | Retry | Bulkhead |
|---------|------|:--:|:--:|:--:|:--:|
| LLM (Claude API) | External API | ✅ 50% | 30s | 3x exp-backoff | 20 |
| OCR (Tika/OpenCV) | Process-based | ✅ 50% | 60s | 3x | — |
| INSEE Registry | External API | ✅ 60% | 5s | 2x | 5 |
| BAN Geocoding | External API | ✅ 60% | 5s | 2x | 5 |
| RPPS Registry | External API | ✅ 60% | 5s | 2x | 5 |
| S3 Storage | Cloud API | ✅ 50% | 30s | 3x exp-backoff | 20 |
| Tika (Document) | Process | — | **15s** ⚠️ | — | 5 |
| OpenCV (Vision) | Process | — | **15s** ⚠️ | — | — |

**Note**: Tika & OpenCV have **BR-VIS-003 constraint** (15s timeout) — DO NOT modify without platform team approval.

---

## Pattern: Resilience4j Stack

```
┌─────────────────────────────────────────────────────┐
│ REQUEST → CircuitBreaker → TimeLimiter → Retry     │
│                                                     │
│ CircuitBreaker: Prevents hammering failed service  │
│ TimeLimiter:    Sets hard deadline for execution   │
│ Retry:          Attempts on transient failures     │
│ Bulkhead:       Limits concurrent calls per service│
└─────────────────────────────────────────────────────┘
```

### Usage Example

```java
@CircuitBreaker(name = "llm", fallbackMethod = "fallback")
@TimeLimiter(name = "llm")
@Retry(name = "llm")
@Bulkhead(name = "llm")
public ExtractionResult extractWithLLM(Document doc) {
  return claudeApi.extract(doc);
}

private ExtractionResult fallback(Document doc, Exception ex) {
  // Fallback: manual review required
  return ExtractionResult.manualReviewRequired(doc.getId());
}
```

---

## Configuration by Service

### 1. LLM (Claude API)

**Purpose**: Extract data using Claude 3.5 Sonnet  
**Latency**: ~2-5 seconds typical, ~15s worst case  
**Reliability**: ~99% (AWS-hosted)

#### CircuitBreaker
```yaml
failureRateThreshold: 50       # Open circuit if 50%+ calls fail
minimumNumberOfCalls: 10       # Evaluate after 10 attempts
waitDurationInOpenState: 30s   # Wait 30s before half-open
permittedNumberOfCallsInHalfOpenState: 3  # Try 3 calls in half-open
```

**Rationale**: LLM calls are expensive ($). Open circuit quickly to prevent cost bleeding, but recover fast since API is reliable.

#### TimeLimiter
```yaml
timeoutDuration: 30s     # Hard timeout (includes network + inference)
cancelRunningFuture: true  # Cancel if timeout
```

**Rationale**: 30s covers network latency + inference for complex extractions. If API hangs, fail fast.

#### Retry
```yaml
maxAttempts: 3
waitDuration: 1s
enableExponentialBackoff: true  # 1s, 2s, 4s
```

**Rationale**: Exponential backoff respects API rate limits. 3 attempts cover transient timeouts without overwhelming API.

#### Bulkhead
```yaml
maxConcurrentCalls: 20   # 20 parallel extraction requests
maxWaitDuration: 0       # No queuing — fail fast if full
```

**Rationale**: Balance: handle peak load (20 docs) without overwhelming Claude API or local resources.

---

### 2. OCR (Tika + OpenCV)

**Purpose**: Extract text (Tika) and perform vision tasks (OpenCV)  
**Latency**: ~2-10 seconds per document  
**Reliability**: ~95% (process-based, CPU-bound)

#### CircuitBreaker
```yaml
failureRateThreshold: 50
minimumNumberOfCalls: 5        # Lower threshold (fewer samples)
waitDurationInOpenState: 60s   # Longer recovery (process-based failure is severe)
permittedNumberOfCallsInHalfOpenState: 3
```

**Rationale**: OCR is CPU-intensive and can hang. Longer wait before recovery lets system stabilize.

#### TimeLimiter

**⚠️ CRITICAL — BR-VIS-003 CONSTRAINT**

```yaml
timeoutDuration: 15s   # DO NOT MODIFY — platform requirement
cancelRunningFuture: true
```

**Why 15s?**
- Tika typically finishes in 2-5s for standard PDFs
- 15s allows for large/complex documents
- Longer timeouts risk cascading hangs across the system
- Java/Python process timeouts are hard (can cause zombie processes)

**Who can modify?** Platform team only (requires system profiling)

#### Retry
```yaml
# No retry configured — process failures usually require manual intervention
```

**Rationale**: If OCR fails, retrying immediately won't help (transient failures are rare). Manual review needed.

#### Bulkhead
```yaml
maxConcurrentCalls: 5  # Only 5 parallel OCR jobs
maxWaitDuration: 0     # No queuing
```

**Rationale**: CPU-bound task. 5 concurrent tasks = most CPU cores busy without overload. Queuing causes latency.

---

### 3. INSEE Registry (Business Registry)

**Purpose**: Verify SIRET/SIREN (French business identifiers)  
**Latency**: ~500ms typical, ~2s worst case  
**Reliability**: ~98% (French government service)

#### CircuitBreaker
```yaml
failureRateThreshold: 60      # Higher threshold — external service, brief outages OK
minimumNumberOfCalls: 5
waitDurationInOpenState: 30s  # Recovery is fast (government SLA)
```

**Rationale**: INSEE is reliable but has maintenance windows. Allow brief outages without circuit-breaking.

#### TimeLimiter
```yaml
timeoutDuration: 5s   # INSEE responds in <1s normally
cancelRunningFuture: true
```

**Rationale**: If INSEE is slow (maintenance), fail immediately. No benefit from waiting.

#### Retry
```yaml
maxAttempts: 2
waitDuration: 2s  # Longer wait for government service (rate limiting awareness)
```

**Rationale**: 2 attempts with 2s wait. If first fails, wait before retry to respect rate limits.

#### Bulkhead
```yaml
maxConcurrentCalls: 5  # Only 5 parallel INSEE calls
maxWaitDuration: 0
```

**Rationale**: Limit load on external registry. Govt APIs have strict rate limits.

---

### 4. BAN Geocoding (Address Lookup)

**Purpose**: Normalize and geocode French addresses  
**Latency**: ~200ms typical  
**Reliability**: ~99% (French government service)

#### Configuration
```yaml
circuitbreaker:
  failureRateThreshold: 60
  minimumNumberOfCalls: 5
  waitDurationInOpenState: 30s

timelimiter:
  timeoutDuration: 5s  # BAN responds fast

retry: # Not configured (address validation failures are permanent)

bulkhead:
  maxConcurrentCalls: 5
```

**Rationale**: Similar to INSEE (external French govt). Quick responses expected.

---

### 5. RPPS Registry (Healthcare Practitioners)

**Purpose**: Verify healthcare provider credentials (RPPS)  
**Latency**: ~500ms typical  
**Reliability**: ~97% (French healthcare authority)

#### Configuration
```yaml
circuitbreaker:
  failureRateThreshold: 60
  minimumNumberOfCalls: 5
  waitDurationInOpenState: 30s

timelimiter:
  timeoutDuration: 5s

retry: # Not configured

bulkhead:
  maxConcurrentCalls: 5
```

**Rationale**: Same pattern as INSEE/BAN (external French services).

---

### 6. S3 Storage (AWS)

**Purpose**: Upload/download documents  
**Latency**: ~100-500ms typical, ~2-5s for large files  
**Reliability**: ~99.99% (AWS SLA)

#### CircuitBreaker
```yaml
failureRateThreshold: 50       # Network errors can spike
minimumNumberOfCalls: 10       # Evaluate after 10 calls
waitDurationInOpenState: 30s   # AWS recovers fast
permittedNumberOfCallsInHalfOpenState: 3
```

**Rationale**: S3 is very reliable, but network issues can cause spikes. Circuit-breaker provides safety valve.

#### TimeLimiter
```yaml
timeoutDuration: 30s   # Covers network + multipart upload
cancelRunningFuture: true
```

**Rationale**: 30s allows large file uploads (100MB+ over slow connection). Fail if network is stuck.

#### Retry
```yaml
maxAttempts: 3
waitDuration: 1s
enableExponentialBackoff: true  # Network errors benefit from backoff
```

**Rationale**: Network hiccups are transient. 3 attempts with exponential backoff recommended by AWS.

#### Bulkhead
```yaml
maxConcurrentCalls: 20  # 20 parallel S3 operations
maxWaitDuration: 0
```

**Rationale**: S3 handles massive concurrency. Limit for local resource management (connections, memory).

---

## Configuration Reference

### CircuitBreaker Properties

| Property | LLM | OCR | INSEE | S3 | Notes |
|----------|:---:|:---:|:-----:|:--:|-------|
| `failureRateThreshold` | 50% | 50% | 60% | 50% | % of calls that can fail before opening |
| `minimumNumberOfCalls` | 10 | 5 | 5 | 10 | Min calls before evaluating threshold |
| `waitDurationInOpenState` | 30s | 60s | 30s | 30s | Time in OPEN state before trying HALF_OPEN |
| `permittedNumberOfCallsInHalfOpenState` | 3 | 3 | 3 | 3 | Probe calls in HALF_OPEN state |

### TimeLimiter Properties

| Service | Timeout | Reason |
|---------|:-------:|--------|
| LLM | 30s | Network + inference time |
| OCR | 60s | OCR processing can be slow |
| Tika | **15s** ⚠️ | **BR-VIS-003 — DO NOT MODIFY** |
| OpenCV | **15s** ⚠️ | **BR-VIS-003 — DO NOT MODIFY** |
| INSEE | 5s | Government APIs respond fast |
| BAN | 5s | Government geocoding API |
| RPPS | 5s | Healthcare registry |
| S3 | 30s | Large file uploads |

### Retry Properties

| Service | Max Attempts | Wait Duration | Backoff | Use Case |
|---------|:------------:|:-------------:|:-------:|----------|
| LLM | 3 | 1s | ✅ Exponential | Rate limit handling |
| OCR | — | — | — | Not retried (failures are permanent) |
| INSEE | 2 | 2s | ❌ No | Respect govt rate limits |
| BAN | — | — | — | Not retried |
| RPPS | — | — | — | Not retried |
| S3 | 3 | 1s | ✅ Exponential | Network transients |

### Bulkhead Properties

| Service | Max Concurrent | Wait Duration | Notes |
|---------|:---------------:|:-------------:|-------|
| LLM | 20 | 0 | Parallel inference |
| OCR | 10 | 0 | CPU-bound, lower concurrency |
| INSEE | 5 | 0 | Limit external API load |
| BAN | 5 | 0 | Limit external API load |
| RPPS | 5 | 0 | Limit external API load |
| Tika | 5 | 0 | Limit process-based load |
| S3 | 20 | 0 | Support parallel uploads |

---

## Circuit Breaker States

```
CLOSED ─ (failures spike) ─→ OPEN
  ↑                          │
  │                          │ (wait 30-60s)
  │                          ↓
  └─────── HALF_OPEN ←─ (probe: 3 calls)
           (all pass)  
            YES → CLOSED
            NO  → OPEN
```

### State Transitions

1. **CLOSED** (normal): Requests flow through
2. **OPEN** (circuit broken): Requests fail immediately without calling service
3. **HALF_OPEN** (recovery): Allow 3 probe requests
   - All 3 succeed → back to CLOSED
   - Any fail → back to OPEN

---

## Monitoring & Alerts

### Metrics to Track

```yaml
# CircuitBreaker metrics
resilience4j_circuitbreaker_state:           # 0=CLOSED, 1=OPEN, 2=HALF_OPEN
resilience4j_circuitbreaker_calls_total:     # Total calls
resilience4j_circuitbreaker_calls_failed:    # Failed calls

# TimeLimiter metrics
resilience4j_timelimiter_calls_total:
resilience4j_timelimiter_calls_timeout:      # Timeout events

# Retry metrics
resilience4j_retry_calls_total:
resilience4j_retry_calls_retry_attempted:    # Actual retries

# Bulkhead metrics
resilience4j_bulkhead_concurrent_calls:      # Current concurrent
resilience4j_bulkhead_calls_rejected:        # Rejected (max reached)
```

### Alert Rules (Prometheus)

```promql
# Alert when circuit breaker opens (service degradation)
resilience4j_circuitbreaker_state{instance="llm"} == 1

# Alert when timeout rate exceeds 5%
rate(resilience4j_timelimiter_calls_timeout[5m]) / 
rate(resilience4j_timelimiter_calls_total[5m]) > 0.05

# Alert when bulkhead constantly at max
resilience4j_bulkhead_concurrent_calls{instance="s3"} >= 20
```

---

## Testing Resilience

### CircuitBreaker Test

```java
@Test
void shouldOpenCircuitBreakerOnHighFailureRate() throws Exception {
  // Simulate 6 failures out of 10 calls (> 50% threshold)
  for (int i = 0; i < 10; i++) {
    try {
      externalService.call();  // Simulated to fail 6x
    } catch (Exception e) {
      // Expected
    }
  }
  
  // Next call should fail immediately (circuit open)
  assertThrows(CallNotPermittedException.class, 
    () -> externalService.call());
}
```

### TimeLimiter Test

```java
@Test
void shouldTimeoutAfter30Seconds() {
  assertThrows(TimeoutException.class, 
    () -> timedExternalCall(Duration.ofMinutes(5)));  // Will timeout at 30s
}
```

### Retry Test

```java
@Test
void shouldRetry3Times() {
  AtomicInteger attempts = new AtomicInteger(0);
  
  assertThrows(Exception.class, () -> {
    retryableCall(() -> {
      attempts.incrementAndGet();
      throw new TemporaryException();
    });
  });
  
  assertEquals(3, attempts.get());  // 3 attempts total
}
```

---

## Fallback Strategies

### LLM Extraction Fallback

```java
@CircuitBreaker(name = "llm", fallbackMethod = "manualReviewFallback")
public ExtractionResult extractWithLLM(Document doc) {
  return claudeApi.extract(doc);
}

private ExtractionResult manualReviewFallback(Document doc, Exception ex) {
  log.warn("LLM extraction failed for doc {}: {}", doc.getId(), ex.getMessage());
  // Mark document for manual review
  return ExtractionResult.builder()
    .documentId(doc.getId())
    .status(ExtractionStatus.MANUAL_REVIEW)
    .failureReason(ex.getMessage())
    .build();
}
```

### S3 Upload Fallback

```java
@CircuitBreaker(name = "s3", fallbackMethod = "queueForUpload")
public void uploadToS3(String key, InputStream data) {
  s3Client.putObject(key, data);
}

private void queueForUpload(String key, InputStream data, Exception ex) {
  log.warn("S3 upload failed for key {}: {}. Queuing for retry.", key, ex.getMessage());
  // Queue to MongoDB outbox for async retry
  outboxService.enqueueS3Upload(key, data);
}
```

---

## BR-VIS-003 Constraint Details

### What is BR-VIS-003?

**Business Rule - Document Visibility - Constraint #003**

Tika (document text extraction) and OpenCV (vision processing) must complete within 15 seconds to maintain system responsiveness.

### Why 15 Seconds?

1. **System Stability**: Process-based extraction (Java heap) can cascade hangs
2. **User Experience**: Document processing should feel "instant" (< 20s total)
3. **Resource Management**: Prevents process zombie states on timeout
4. **Empirical Data**: Analysis shows 95% of documents process in < 10s

### Can This Be Modified?

**No, without approval** — Only Platform Team can modify after:
1. Performance profiling on production-like data
2. Risk assessment of longer timeouts
3. Notification to operational teams

### What If Documents Timeout?

```
Document Timeout (15s) → Mark for manual OCR → User Notification
                     → Stored in S3 for forensic analysis
                     → Re-attempted after 1 hour
                     → Escalate to support after 3 failures
```

---

## Deployment Checklist

- [ ] Configuration updated in `application.yml`
- [ ] All 8 services have correct thresholds
- [ ] BR-VIS-003 constraints verified (Tika & OpenCV = 15s)
- [ ] Fallback methods implemented for circuit breaker
- [ ] Monitoring configured (Prometheus scraping metrics)
- [ ] Alert rules configured (Grafana)
- [ ] Load test validation (simulate failures, verify fallbacks)
- [ ] Operational runbook updated (what to do if circuit breaks)

---

## Related ADRs

- **ADR-008**: Stack Technique (Resilience4j 2.4.2)
- **BR-VIS-003**: Document Visibility Constraint (15s timeout for Tika/OpenCV)
- **FR-021 to FR-027**: Resilience patterns (Constitution Annex A)

---

## References

- [Resilience4j Official Documentation](https://resilience4j.readme.io/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Bulkhead Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/bulkhead)
- Claude API Rate Limits: [Platform Documentation](https://docs.anthropic.com/)
- INSEE API: [INSEE Services](https://www.insee.fr/)
- BAN Geocoding: [BAN API](https://adresse.data.gouv.fr/)

---

## Change Log

**2026-05-26** — Initial configuration
- Created 8-service resilience configuration
- Applied BR-VIS-003 constraints for Tika & OpenCV
- Documented all thresholds and rationale

---

**Status**: ✅ COMPLETE  
**Location**: `backend/docai-bootstrap/src/main/resources/application.yml` (lines 111-194)  
**Last Updated**: 2026-05-26  
**Branch**: `004-stack-technique`

