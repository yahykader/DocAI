# Data Model: Stack Technique & Intégrations

**Feature**: `specs/003-stack-technique`  
**Date**: 2026-05-25

Ce module est une référence de configuration transversale. Les entités ci-dessous sont des **entités de configuration**, pas des entités domaine. Aucune n'est persistée en MongoDB.

---

## Entité 1 — KafkaTopic (configuration)

| Attribut | Type | Valeur / Règle |
|----------|------|---------------|
| `name` | `String` | Convention: `docai.{context}.{event}` |
| `partitions` | `int` | 6 (pipeline) ou 3 (failure/relay) |
| `replicationFactor` | `short` | 1 (dev local), 3 (production) |
| `retentionMs` | `long` | 7j, 30j, 90j ou 1j selon topic |
| `partitionKey` | `String (enum)` | `DOCUMENT_ID` (pipeline) ou `TENANT_ID` (failure) |
| `consumerGroupProperty` | `String` | Référence à la propriété `kafka.groups.*` dans `application.yml` |

**Topics définis (8)** :

| Topic | Partitions | Rétention | Clé partition | Propriété Consumer Group |
|-------|-----------|-----------|--------------|--------------------------|
| `docai.doc.uploaded` | 6 | 7 jours | `documentId` (ADR-002) | `${kafka.groups.upload}` |
| `docai.doc.classified` | 6 | 7 jours | `documentId` (ADR-002) | `${kafka.groups.classification}` |
| `docai.doc.extracted` | 6 | 7 jours | `documentId` (ADR-002) | `${kafka.groups.extraction.llm}` |
| `docai.doc.validated` | 6 | 7 jours | `documentId` (ADR-002) | `${kafka.groups.validation}` |
| `docai.doc.fraud.detected` | 6 | 7 jours | `documentId` (ADR-002) | `${kafka.groups.fraud.analyser}` |
| `docai.doc.completed` | 3 | 30 jours | `documentId` (ADR-002) | `${kafka.groups.pipeline.orchestrator}` |
| `docai.doc.failed` | 3 | 30 jours | `tenantId` (exception ADR-002) | N/A (producteur uniquement) |
| `docai.doc.dlq` | 3 | 90 jours | `tenantId` (exception ADR-002) | N/A (monitoring uniquement) |
| `docai.outbox.relay` | 3 | 1 jour | `documentId` | `${kafka.groups.outbox.relay}` |

> **Note** : Topic `docai.doc.fraud.detected` remplace `docai.doc.fraud.analyzed` du docker-compose existant — alignement à effectuer lors du Module 3.

---

## Entité 2 — CacheStrategy (configuration Valkey)

| Attribut | Type | Règle |
|----------|------|-------|
| `name` | `String` | Identifiant lisible (ex. `extraction-llm`) |
| `keyPattern` | `String` | Template de clé Valkey |
| `baseTtl` | `Duration` | Durée de base avant jitter |
| `jitterEnabled` | `boolean` | `true` si TTL > 1h et non-exception ADR-003 |
| `jitterFactor` | `double` | `0.10` (±10%) si `jitterEnabled=true` |
| `exceptionReason` | `String?` | Raison si TTL fixe (idempotence, JWT, quota, JWKS) |

**9 stratégies définies** :

| # | Nom | Clé Pattern | TTL base | Jitter | Exception |
|---|-----|-------------|---------|--------|-----------|
| 1 | `extraction-llm` | `extraction:{tenantId}:{sha256}` | 24h | ✅ ±10% | — |
| 2 | `insee-siret` | `insee:siret:{siret}` | 7 jours | ✅ ±10% | — |
| 3 | `ban-address` | `ban:address:{sha256(addr)}` | 30 jours | ✅ ±10% | — |
| 4 | `rpps-practitioner` | `rpps:{number}` | 7 jours | ✅ ±10% | — |
| 5 | `quota-api` | `quota:{tenantId}:{year}-{month}` | Fin de mois | ❌ fixe | ADR-001 (précision reset) |
| 6 | `jwks-keycloak` | Géré par Spring Security | 1h | ❌ fixe | ADR-006 |
| 7 | `classification-result` | `class:{sha256}` | 30 min | ✅ ±10% | — |
| 8 | `idempotence-kafka` | `idempotent:{topic}:{partition}:{offset}` | 24h | ❌ fixe | ADR-003 exception |
| 9 | `jwt-blacklist` | `jwt:blacklist:{jti}` | = expiration token | ❌ fixe | ADR-003 exception |

---

## Entité 3 — ResilienceConfig (configuration Resilience4j)

| Attribut | Type | Règle |
|----------|------|-------|
| `name` | `String` | Nom du service (ex. `llm`, `tika`) |
| `cbFailureRateThreshold` | `float` | % d'échecs déclenchant l'ouverture |
| `cbSlidingWindowSize` | `int` | Nombre d'appels dans la fenêtre glissante |
| `retryMaxAttempts` | `int` | Nombre de tentatives (1 = pas de retry) |
| `retryWaitDuration` | `Duration` | Délai initial entre tentatives |
| `retryBackoffMultiplier` | `double` | Multiplicateur exponentiel (1.0 = fixe) |
| `bulkheadMaxThreads` | `int` | Threads concurrents maximum |
| `timeLimiterTimeout` | `Duration` | Timeout absolu |
| `timeLimiterCancelOnTimeout` | `boolean` | `true` → annulation async (fail-safe) |

**8 instances configurées** (Constitution Annex A) :

| Service | CB (%) | CB (N) | Retry | Wait | Bulkhead | Timeout |
|---------|--------|--------|-------|------|---------|---------|
| `llm` | 50% | 10 | 3× | 1s exp | 20 | 30s |
| `tika` | 50% | 5 | 2× | 1s exp | 5 | **15s** ← BR-VIS-003 |
| `opencv` | 50% | 5 | 1× | — | 5 | **15s** ← BR-VIS-003 |
| `insee` | 60% | 5 | 2× | 2s backoff | 5 | 5s |
| `ban` | 60% | 5 | 2× | 500ms backoff | 5 | 5s |
| `rpps` | 60% | 8 | 2× | 1s fixe | 5 | 5s |
| `s3` | 50% | 10 | 3× | 1s exp | 20 | 30s |
| `kafka` | — | — | — | — | — | — |

---

## Entité 4 — JitterTtl (utilitaire partagé)

```
JitterTtl (classe finale, méthodes statiques)
├── withJitter(Duration base) → Duration
│   └── Applique ±10% de jitter aléatoire
├── withJitter(Duration base, double jitterFactor) → Duration
│   └── Applique ±{jitterFactor*100}% de jitter aléatoire
└── Localisation: docai-commons (pas docai-domain, pas par adaptateur)
```

**Invariants** :
- `withJitter(Duration.ZERO)` → retourne `Duration.ZERO` (pas de division par zéro)
- `withJitter(null)` → `NullPointerException` (fail-fast, pas de null-safe silencieux)
- Le résultat est toujours positif et non-nul
- Thread-safe (utilise `ThreadLocalRandom.current()`)

---

## Relations de dépendance (modules Maven)

```
docai-bootstrap
  └── dépend de → docai-adapter-out-valkey (CacheConfig, JitterTtl)
  └── dépend de → docai-adapter-out-kafka (AvroSerDe, KafkaTopicConfig)
  └── contient → application.yml (Resilience4j, Consumer Group IDs, ADR-006)

docai-adapter-out-kafka
  └── utilise → maven-avro-plugin (génération classes depuis .avsc)
  └── utilise → apicurio 2.6.5.Final (SerDe registry)

docai-adapter-out-valkey
  └── utilise → JitterTtl (depuis docai-commons ou package interne)
  └── implémente → 9 stratégies CacheStrategy
```
