# Contract: application.yml — Stack Technique Module B

**Source**: `backend/docai-bootstrap/src/main/resources/application.yml`  
**Date**: 2026-05-25  
**ADRs**: ADR-002, ADR-003, ADR-006

Ce document décrit les blocs de configuration qui DOIVENT être présents dans `application.yml` pour satisfaire les exigences du Module B.

---

## BLOC 1 — Kafka : Consumer Group IDs (FR-016b)

```yaml
# Consumer Group IDs — DÉFINIS ICI UNIQUEMENT (jamais dans @KafkaListener) — FR-016b
kafka:
  groups:
    upload: docai.upload.processor.group
    classification: docai.classification.processor.group
    extraction:
      llm: docai.extraction.llm.group
      ocr: docai.extraction.ocr.group
    validation: docai.validation.processor.group
    fraud:
      analyser: docai.fraud.analyser.group
    pipeline:
      orchestrator: docai.pipeline.orchestrator.group
    outbox:
      relay: docai.outbox.relay.group
```

**Usage dans le code Java** :
```java
// ✅ CORRECT — référence propriété
@KafkaListener(topics = "docai.doc.uploaded", groupId = "${kafka.groups.upload}")

// ❌ INTERDIT (ADR-002 + FR-016b) — valeur littérale
@KafkaListener(topics = "docai.doc.uploaded", groupId = "docai.upload.processor.group")
```

**Remplacement dans application.yml existant** :
```yaml
# Supprimer:
spring.kafka.consumer.group-id: docai-group   # ← trop générique, supprimer

# Remplacer par les groupes ci-dessus + default fallback si nécessaire
```

---

## BLOC 2 — Kafka : Schema Registry Apicurio (FR-005)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      auto-offset-reset: earliest
      max-poll-records: 100
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.apicurio.registry.serde.avro.AvroKafkaDeserializer
    producer:
      acks: all
      retries: 3
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.apicurio.registry.serde.avro.AvroKafkaSerializer
    properties:
      apicurio.registry.url: ${APICURIO_URL:http://localhost:8081}   # SEC-003: utiliser https:// en production
      apicurio.registry.auto-register: ${APICURIO_AUTO_REGISTER:false}   # SEC-001: false par défaut — true via .env local uniquement
      apicurio.registry.find-latest: true
      # NB: "schema.registry.url" est la clé Confluent — utiliser apicurio.registry.url
```

---

## BLOC 3 — Resilience4j : 8 services (FR-021 à FR-027)

```yaml
resilience4j:
  # Circuit Breaker
  circuitbreaker:
    instances:
      llm:
        failureRateThreshold: 50
        slidingWindowSize: 10
        waitDurationInOpenState: 60s   # SEC-008: 60s (30s trop court pour LLM — aligné sur INSEE/BAN)
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
      tika:
        failureRateThreshold: 50
        slidingWindowSize: 5
        waitDurationInOpenState: 30s
      opencv:
        failureRateThreshold: 50
        slidingWindowSize: 5
        waitDurationInOpenState: 30s
      insee:
        failureRateThreshold: 60
        slidingWindowSize: 5
        waitDurationInOpenState: 60s
      ban:
        failureRateThreshold: 60
        slidingWindowSize: 5
        waitDurationInOpenState: 60s
      rpps:
        failureRateThreshold: 60
        slidingWindowSize: 8
        waitDurationInOpenState: 60s
      s3:
        failureRateThreshold: 50
        slidingWindowSize: 10
        waitDurationInOpenState: 30s

  # Retry
  retry:
    instances:
      llm:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
      tika:
        maxAttempts: 2
        waitDuration: 1s
        enableExponentialBackoff: true
      opencv:
        maxAttempts: 1  # Pas de retry — timeout 15s suffit (BR-VIS-003)
      insee:
        maxAttempts: 2
        waitDuration: 2s
      ban:
        maxAttempts: 2
        waitDuration: 500ms
      rpps:
        maxAttempts: 2
        waitDuration: 1s
      s3:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2

  # Bulkhead (ThreadPool)
  bulkhead:
    instances:
      llm:
        maxConcurrentCalls: 20
        maxWaitDuration: 0
      tika:
        maxConcurrentCalls: 5
        maxWaitDuration: 0
      opencv:
        maxConcurrentCalls: 5
        maxWaitDuration: 0
      insee:
        maxConcurrentCalls: 5
        maxWaitDuration: 0
      ban:
        maxConcurrentCalls: 5
        maxWaitDuration: 0
      rpps:
        maxConcurrentCalls: 5
        maxWaitDuration: 0
      s3:
        maxConcurrentCalls: 20
        maxWaitDuration: 0

  # TimeLimiter — timeout absolu (BR-VIS-003 : tika + opencv = 15s)
  timelimiter:
    instances:
      llm:
        timeoutDuration: 30s
        cancelRunningFuture: true
      tika:
        timeoutDuration: 15s   # BR-VIS-003 — OBLIGATOIRE
        cancelRunningFuture: true
      opencv:
        timeoutDuration: 15s   # BR-VIS-003 — OBLIGATOIRE
        cancelRunningFuture: true
      insee:
        timeoutDuration: 5s
        cancelRunningFuture: true
      ban:
        timeoutDuration: 5s
        cancelRunningFuture: true
      rpps:
        timeoutDuration: 5s
        cancelRunningFuture: true
      s3:
        timeoutDuration: 30s
        cancelRunningFuture: true
```

---

## BLOC 4 — Valkey : 9 stratégies de cache (FR-017 à FR-020)

```yaml
spring:
  data:
    redis:  # Valkey 8.x (Redis-compatible via RESP3 — Lettuce 6.x)
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      password: ${VALKEY_PASSWORD:}   # SEC-005: vide en local, obligatoire en production
      timeout: 60000ms
      ssl:
        enabled: ${VALKEY_SSL_ENABLED:false}   # SEC-005: true en production
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

# Cache TTL reference (documentation — les TTL réels sont appliqués via JitterTtl.withJitter() dans le code)
docai:
  cache:
    # Stratégie 1: extraction LLM — 24h + jitter ±10% (ADR-003)
    extraction-llm-ttl: 24h
    # Stratégie 2: INSEE SIRET — 7 jours + jitter ±10%
    insee-siret-ttl: 7d
    # Stratégie 3: BAN adresse — 30 jours + jitter ±10%
    ban-address-ttl: 30d
    # Stratégie 4: RPPS praticien — 7 jours + jitter ±10%
    rpps-practitioner-ttl: 7d
    # Stratégie 5: quota API — fixe (fin de mois) — exception ADR-003 (ADR-001)
    # Stratégie 6: JWKS Keycloak — fixe 1h — géré Spring Security (ADR-006)
    # Stratégie 7: résultat classification — 30 min + jitter ±10%
    classification-result-ttl: 30m
    # Stratégie 8: idempotence Kafka — fixe 24h — exception ADR-003
    idempotence-ttl: 24h
    # Stratégie 9: JWT blacklist — fixe = expiration token — exception ADR-003
```

---

## BLOC 5 — ADR-006 : JWKS cache 1h (FR-027)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/docai
          jwk-set-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/docai/protocol/openid-connect/certs
          jwks-cache-ttl: 1h   # ADR-006 — OBLIGATOIRE (défaut Spring = 5min, insuffisant)
```

---

## BLOC 6 — Actuator : sécurité (SEC-002)

```yaml
# SEC-002: show-details: when_authorized — jamais "always" en production
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # SEC-011: inclure prometheus sur port interne 9091
  endpoint:
    health:
      show-details: when_authorized   # SEC-002: protéger les détails internes
      show-components: when_authorized
  server:
    port: ${MANAGEMENT_PORT:9091}   # SEC-002: port interne séparé du port applicatif (8080)
  metrics:
    export:
      prometheus:
        enabled: true   # Prometheus scrape via port 9091 — non exposé au public
```

> **Note SEC-015** : `show-details: when_authorized` sur le port 9091 (management) n'hérite pas du Spring Security configuré pour le port 8080 (applicatif). En pratique, ce réglage est effectivement `never` sur le port management — comportement plus restrictif que prévu, acceptable en sécurité mais à ne pas tester comme `always`. Pour exposer les détails health en interne (CI/CD health checks), utiliser le port 9091 avec `show-details: always` dans le profil `local` uniquement.

> **Note SEC-007** : Les Consumer Group IDs (ex. `docai.upload.processor.group`) sont prévisibles par conception — ils s'appuient sur le réseau interne Kafka (non exposé publiquement) et l'authentification Keycloak au niveau applicatif.

> **Note SEC-009** : Les bibliothèques de traitement documentaire (Tika, Tess4J, PDFBox, JavaCV) traitent des fichiers potentiellement hostiles. Activer un `SecurityManager` ou un sandbox OS-level lors de l'implémentation des adaptateurs (Module Upload, Étape 3+).

---

## Règles de validation (ArchUnit)

Les règles suivantes DOIVENT être ajoutées aux tests ArchUnit existants :

1. **FR-016b** : `@KafkaListener` avec attribut `groupId` non-vide et non-commençant par `${` → FAIL
2. **FR-011** : Import de `org.opencv` (pas `org.bytedeco`) dans tout module → FAIL
3. **FR-007** : Import de `org.springframework.data.redis` dans `docai-domain` → FAIL
4. **ADR-003** : Appel direct `Duration.ofHours(N)` dans un composant de cache sans `JitterTtl.withJitter()` wrapper → WARNING (pas détectable statiquement, vérification en revue de code)
