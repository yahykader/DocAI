---
name: docai-rate-limiting
description: "Implémente le Module 6.3 DocAI (quotas mensuels par plan Starter 500/Pro 10k/Enterprise illimité, réinitialisation 1er du mois UTC, notifications 80% et 95%, overage autorisé et facturé, rate limiting HTTP par tenant et par API Key via Bucket4j + Valkey, endpoint usage temps réel ( 100ms, job QuotaResetScheduler mensuel). Utiliser quand on demande d'implémenter les quotas, le rate limiting avancé, les notifications de dépassement de quota, la réinitialisation mensuelle, l'overage billing, ou l'endpoint /v1/analytics/usage dans DocAI."
---

# Module 6.3 — Rate Limiting Avancé & Quotas

> **Prérequis :** Module 6.1 (API Keys), Module 0.4 (Billing & Plans) terminés.  
> **Durée estimée :** 1 semaine

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/quota/
├── TenantQuota.java          // Aggregate (tenantId, plan, monthlyLimit, currentUsage, periodStart, periodEnd)
├── QuotaUsageEntry.java      // Value Object (tenantId, documentId, incrementAt)
├── QuotaThreshold.java       // Enum (WARN_80, WARN_95, EXCEEDED)
└── events/
    ├── QuotaThresholdReached.java   // (tenantId, threshold, currentUsage, limit)
    └── QuotaResetCompleted.java     // (tenantId, newPeriodStart)
```

### Ports
```
Inbound:
  PORT-IN-QTA-001 → GetUsageUseCase              (endpoint analytics/usage)
  PORT-IN-QTA-002 → IncrementQuotaUseCase        (appelé à chaque document soumis)
  PORT-IN-QTA-003 → RunMonthlyQuotaResetUseCase  (job 1er du mois)

Outbound:
  PORT-OUT-QTA-001 → QuotaRepositoryPort
  PORT-OUT-QTA-002 → QuotaCachePort              (Valkey — usage temps réel)
  PORT-OUT-QTA-003 → QuotaNotificationPort       (email 80% et 95%)
```

### Adapters
```
docai-adapter-in-rest/
└── AnalyticsController.java        // GET /v1/analytics/usage

docai-application/
└── QuotaResetScheduler.java        // @Scheduled cron 1er du mois minuit UTC

docai-adapter-out-valkey/
└── ValkeyQuotaCacheAdapter.java    // Compteur incrémental atomique (INCR)

docai-adapter-out-mongodb/
└── MongoQuotaAdapter.java
```

---

## Plans & Limites

| Plan | Quota mensuel | Comportement dépassement |
|------|-------------|------------------------|
| FREE | 50 documents | Blocage HTTP 429 |
| STARTER | 500 documents | Overage autorisé + facturé |
| PRO | 10 000 documents | Overage autorisé + facturé |
| ENTERPRISE | Illimité | Pas de limite |

> **Overage :** Pour STARTER et PRO, le dépassement n'est **pas bloqué** — le document est accepté,
> l'usage est comptabilisé et facturé au tarif overage (voir Module 7 Billing Stripe).

---

## Rate Limiting HTTP (Bucket4j + Valkey)

Distinct des quotas mensuels — s'applique aux requêtes par seconde/minute.

```yaml
# application.yml
docai:
  rate-limiting:
    by-tenant:
      capacity: 100          # tokens max
      refill-tokens: 10      # tokens rechargés
      refill-period: 1s      # toutes les secondes
    by-api-key:
      capacity: 20
      refill-tokens: 5
      refill-period: 1s
```

```java
// RateLimitingFilter.java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String tenantId = TenantContext.get();  // Jamais TenantContext.getTenantId()
        String apiKeyId = extractApiKeyId(request); // null si auth JWT

        // Rate limit par tenant (toujours)
        if (!rateLimiter.tryConsume(tenantId)) {
            response.setStatus(429);
            response.setHeader("X-RateLimit-Retry-After",
                rateLimiter.getRetryAfterSeconds(tenantId).toString());
            writeProblemDetail(response, "Rate limit exceeded");
            return;
        }

        // Rate limit additionnel par API Key
        if (apiKeyId != null && !rateLimiter.tryConsume("apikey:" + apiKeyId)) {
            response.setStatus(429);
            writeProblemDetail(response, "API Key rate limit exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## Quota Mensuel — Comptage Atomique Valkey

```java
// ValkeyQuotaCacheAdapter.java
public long incrementAndGet(String tenantId) {
    String key = "quota:" + tenantId + ":" + getCurrentYearMonth();
    // INCR atomique — thread-safe, performances O(1)
    return redisTemplate.opsForValue().increment(key, 1);
}

public long getCurrentUsage(String tenantId) {
    String key = "quota:" + tenantId + ":" + getCurrentYearMonth();
    Object val = redisTemplate.opsForValue().get(key);
    return val == null ? 0L : Long.parseLong(val.toString());
}
```

---

## Notifications Seuils

```java
// IncrementQuotaUseCase.java
public void increment(String tenantId, String documentId) {
    long newUsage = quotaCache.incrementAndGet(tenantId);
    TenantQuota quota = quotaRepository.findByTenantId(tenantId);

    // Persister usage (async, non bloquant)
    quotaRepository.updateUsage(tenantId, newUsage);

    // Vérifier seuils de notification
    long limit = quota.monthlyLimit();
    double percentage = (double) newUsage / limit * 100;

    if (percentage >= 95 && !quota.notifiedAt95()) {
        publishDomainEvent(new QuotaThresholdReached(tenantId, WARN_95, newUsage, limit));
        // → email + alerte dashboard SSE
    } else if (percentage >= 80 && !quota.notifiedAt80()) {
        publishDomainEvent(new QuotaThresholdReached(tenantId, WARN_80, newUsage, limit));
    }

    // Blocage uniquement pour plan FREE
    if (quota.plan() == Plan.FREE && newUsage > limit) {
        throw new QuotaExceededException(tenantId, newUsage, limit);
    }
    // STARTER/PRO : overage comptabilisé, pas bloqué
}
```

---

## QuotaResetScheduler

```java
// QuotaResetScheduler.java
@Component
public class QuotaResetScheduler {

    // 1er de chaque mois à minuit UTC
    @Scheduled(cron = "0 0 0 1 * *")
    public void resetMonthlyQuotas() {
        // 1. Récupérer tous les tenants actifs
        // 2. Pour chaque tenant : archiver usage du mois précédent
        // 3. Remettre compteur Valkey à 0 (nouveau mois)
        // 4. Réinitialiser flags notifiedAt80, notifiedAt95
        // 5. Publier QuotaResetCompleted → transmission au Module 7 Billing pour facturation overage
        log.info("Quota mensuel réinitialisé pour {} tenants", tenantCount);
    }
}
```

---

## Endpoint Usage Temps Réel

```
GET /v1/analytics/usage
Authorization: Bearer {JWT} ou X-API-Key

Response (< 100ms — depuis Valkey cache):
{
  "tenantId": "acme-corp",
  "plan": "PRO",
  "period": {
    "start": "2026-05-01T00:00:00Z",
    "end": "2026-05-31T23:59:59Z",
    "resetAt": "2026-06-01T00:00:00Z"
  },
  "quota": {
    "limit": 10000,
    "used": 3456,
    "remaining": 6544,
    "percentageUsed": 34.56,
    "overageAllowed": true
  },
  "rateLimit": {
    "requestsPerSecond": 100,
    "apiKeyRequestsPerSecond": 20
  }
}
```

---

## Réponse HTTP 429 Standard (RFC 7807)

```json
{
  "type": "https://docs.docai.io/errors/quota-exceeded",
  "title": "Monthly quota exceeded",
  "status": 429,
  "detail": "You have used 10/10 documents this month. Upgrade your plan or wait for reset on 2026-06-01.",
  "instance": "/v1/documents",
  "quotaResetAt": "2026-06-01T00:00:00Z"
}
```

Headers obligatoires :
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1748736000
Retry-After: 864000
```

---

## Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-INT-020 | Quota mensuel réinitialisé le 1er de chaque mois à minuit UTC | MUST |
| BR-INT-021 | Notification à 80% et 95% de consommation | MUST |
| BR-INT-022 | Usage temps réel consultable via `/v1/analytics/usage` (< 100ms) | MUST |
| BR-INT-023 | Plan FREE : dépassement → HTTP 429. STARTER/PRO : overage autorisé + facturé | MUST |
| BR-INT-024 | Rate limiting distinct du quota : par tenant (100 req/s) et par API Key (20 req/s) | MUST |
| BR-INT-025 | Compteurs Valkey atomiques (INCR) — pas de race condition | MUST |
| BR-INT-026 | Flags notifiedAt80/notifiedAt95 remis à false à chaque reset mensuel | MUST |

---

## Tests Obligatoires

```java
@Test
void should_block_free_plan_after_10_documents() { }

@Test
void should_allow_overage_for_starter_plan() {
    // Starter : 501ème document → accepté, pas HTTP 429
}

@Test
void should_send_notification_at_80_percent() { }

@Test
void should_send_notification_at_95_percent_only_once() {
    // Vérifier que la notif 95% n'est envoyée qu'une seule fois par période
}

@Test
void should_reset_quota_on_first_of_month() { }

@Test
void should_return_usage_from_valkey_cache_under_100ms() { }

@Test
void should_rate_limit_by_tenant_independently_from_api_key() { }
```

---

## Commons à Utiliser

- `commons-quota` → `@QuotaProtected` sur `POST /v1/documents` (appelle `IncrementQuotaUseCase`)
- `commons-multitenancy` → isolation tenant sur tous les compteurs
- `docai-adapter-valkey` → `ValkeyQuotaCacheAdapter` (JitterTtl, INCR atomique)
- `commons-api` → headers `X-RateLimit-*` standardisés

---

## Definition of Done

- [ ] Réinitialisation quota mensuelle testée (job planifié)
- [ ] Notifications 80% et 95% testées (envoyées une seule fois par période)
- [ ] Endpoint usage temps réel < 100ms (depuis Valkey)
- [ ] Rate limiting par API Key testé (distinct du rate limiting par tenant)
- [ ] Plan FREE : blocage HTTP 429 au dépassement
- [ ] Plan STARTER/PRO : overage autorisé, comptabilisé pour Billing
- [ ] Headers `X-RateLimit-*` présents sur toutes les réponses 429
- [ ] Compteurs Valkey atomiques (pas de race condition sous charge)

---

## Logs Obligatoires

```
INFO  — Quota incrémenté : tenantId, currentUsage, limit, plan
WARN  — Seuil quota atteint : tenantId, threshold=80%, currentUsage, limit
WARN  — Seuil quota atteint : tenantId, threshold=95%, currentUsage, limit
WARN  — Quota dépassé (FREE plan) : tenantId, currentUsage, limit → HTTP 429
INFO  — Overage autorisé : tenantId, currentUsage, limit, plan=STARTER
INFO  — Quota mensuel réinitialisé : tenantId, newPeriodStart, previousUsage
WARN  — Rate limit déclenché : tenantId, endpoint, retryAfterSeconds
```
