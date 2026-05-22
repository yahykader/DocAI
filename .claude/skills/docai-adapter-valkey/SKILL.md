---
name: docai-adapter-valkey
description: "Crée un Adapter Cache Valkey dans DocAI (Cache-Aside, Write-Through, idempotence, rate limiting). Utiliser quand on demande du cache Redis/Valkey, un TTL, un compteur rate limiting, une clé d'idempotence, ou la stratégie JitterTtl. Applique ADR-003 (jitter obligatoire) et les TTL définis dans le SpecKit."
---

# DocAI — Créer un Adapter Valkey (Cache)

## Localisation

Module : `docai-adapter-out-valkey`
Package : `fr.docai.adapter.out.valkey`

## Stratégies de cache — RÉFÉRENCE OBLIGATOIRE

| Données | Clé Valkey | TTL | Jitter | Invalidation |
|---------|-----------|-----|--------|-------------|
| Résultats extraction LLM | `extraction:{tenantId}:{sha256}` | 24h | ±10% (ADR-003) | Sur correction manuelle |
| Validations SIRET INSEE | `insee:siret:{siret}` | 7j | ±10% (ADR-003) | Manuelle (admin) |
| Validations adresse BAN | `ban:address:{sha256(adresse)}` | **30j** | ±10% (ADR-003) | Rarement |
| Validations RPPS | `rpps:{numero}` | 7j | ±10% (ADR-003) | Rarement |
| Classification | `classification:{sha256}` | 1h | ±10% (ADR-003) | Automatique |
| Clés d'idempotence upload | `idempotency:{X-Idempotency-Key}` | 24h | **Non** (précision) | Aucune |
| Idempotence consumer Kafka | `idempotent:{topic}:{partition}:{offset}` | 24h | **Non** (précision) | Aucune |
| JWT Blacklist | `jwt:blacklist:{jti}` | = durée restante JWT | **Non** | Auto expiry |
| Quota mensuel | `quota:{tenantId}:{year}-{month}` | Reset 1er du mois | **Non** | Job mensuel |
| Token OAuth2 INSEE | `insee:oauth2:token` | Durée token | **Non** | Auto expiry |

## ADR-003 — Jitter obligatoire sur TTL > 1h

```java
// JAMAIS : TTL fixe pour les données LLM/SIRET/profils tenant
redisTemplate.opsForValue().set(key, value, Duration.ofHours(24)); // ❌

// TOUJOURS : TTL avec jitter via commons-kafka
Duration ttl = JitterTtl.withJitter(Duration.ofHours(24)); // ±10% aléatoire
redisTemplate.opsForValue().set(key, value, ttl); // ✅

// Exceptions autorisées (pas de jitter) :
// - Clés d'idempotence : précision requise
// - Rate limiting : fenêtre glissante précise
```

## Pattern Cache-Aside — Résultats LLM

```java
@Component
public class ValkeyExtractionCacheAdapter implements ExtractionCachePort {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "extraction:";

    @Override
    public Optional<ExtractionResult> get(String contentHash) {
        String key = KEY_PREFIX + contentHash;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(cached, ExtractionResult.class));
        } catch (JsonProcessingException e) {
            log.warn("Cache deserialization error key={}", key);
            redisTemplate.delete(key); // Invalider l'entrée corrompue
            return Optional.empty();
        }
    }

    @Override
    public void set(String contentHash, ExtractionResult result) {
        String key = KEY_PREFIX + contentHash;
        try {
            String json = objectMapper.writeValueAsString(result);
            // ADR-003 : jitter obligatoire — 24h ± 30min
            Duration ttl = JitterTtl.withJitter(Duration.ofHours(24));
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            log.error("Cache serialization error key={}", key, e);
            // Ne pas bloquer le pipeline si le cache échoue
        }
    }

    @Override
    public void invalidate(String contentHash) {
        redisTemplate.delete(KEY_PREFIX + contentHash);
    }
}
```

## Pattern Write-Through — Statuts documents

```java
@Component
public class DocumentStatusCacheAdapter implements DocumentStatusCachePort {

    private static final String KEY_PREFIX = "doc:status:";
    private static final Duration TTL = Duration.ofMinutes(5); // Pas de jitter (court)

    @Override
    public void writeThrough(String documentId, String tenantId, String status) {
        // Clé toujours préfixée par tenantId — isolation obligatoire
        String key = KEY_PREFIX + tenantId + ":" + documentId;
        redisTemplate.opsForValue().set(key, status, TTL);
    }

    @Override
    public Optional<String> getStatus(String documentId, String tenantId) {
        String key = KEY_PREFIX + tenantId + ":" + documentId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
}
```

## Pattern Idempotence — Déduplication requêtes

```java
@Component
public class ValkeyIdempotencyAdapter implements IdempotencyPort {

    private static final String KEY_PREFIX = "idem:";
    private static final Duration TTL = Duration.ofHours(24); // Fixe — pas de jitter

    @Override
    public boolean tryAcquire(String idempotencyKey, Duration ttl) {
        String key = KEY_PREFIX + idempotencyKey;
        // setIfAbsent = SET NX — atomique
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(key, "ACQUIRED", ttl != null ? ttl : TTL);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public Optional<String> getCachedResponse(String idempotencyKey) {
        String key = KEY_PREFIX + "response:" + idempotencyKey;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void cacheResponse(String idempotencyKey, String response, Duration ttl) {
        String key = KEY_PREFIX + "response:" + idempotencyKey;
        redisTemplate.opsForValue().set(key, response, ttl != null ? ttl : TTL);
    }
}
```

## Pattern Rate Limiting — Script Lua atomique (ADR-001)

```java
@Component
public class ValkeyRateLimitAdapter implements RateLimitPort {

    // Script Lua — incrémente ET vérifie atomiquement (ADR-001)
    private static final String RATE_LIMIT_SCRIPT = """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """;

    @Override
    public QuotaCheckResult checkAndConsume(String tenantId, int amount) {
        String key = "quota:" + tenantId + ":" + getCurrentMonth();
        Long current = redisTemplate.execute(
            new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class),
            List.of(key),
            String.valueOf(getSecondsUntilEndOfMonth())
        );
        long limit = getTenantLimit(tenantId);
        return buildResult(current, limit);
    }
}
```

## JWT Blacklist — Révocation tokens

```java
@Component
public class ValkeyTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    @Override
    public void blacklist(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "REVOKED", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
```

## Checklist

- [ ] Clé toujours préfixée par `tenantId` — isolation obligatoire
- [ ] `JitterTtl.withJitter()` sur tout TTL > 1h (ADR-003)
- [ ] TTL fixe uniquement pour idempotence et rate limiting
- [ ] Try/catch sur la sérialisation — jamais bloquer le pipeline si cache échoue
- [ ] `setIfAbsent` (SET NX) pour les opérations d'idempotence
- [ ] Script Lua pour les opérations atomiques (quota, rate limit)
- [ ] Test : 100 mises en cache → 100 TTL différents (vérification jitter)
- [ ] Test : isolation tenant — clé tenant A non accessible depuis tenant B
