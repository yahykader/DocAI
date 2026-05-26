# Research: Stack Technique & Intégrations DocAI

**Feature**: `specs/003-stack-technique`  
**Date**: 2026-05-25  
**Status**: Complete — all NEEDS CLARIFICATION resolved

---

## D1 — Apicurio Registry : version 2.6.5.Final (pas 2.4.15.Final)

**Decision**: Passer `apicurio.version` de `2.4.15.Final` à **`2.6.5.Final`** dans le POM parent.

**Rationale**: La spec et la constitution exigent Apicurio 2.6. La version actuelle dans le POM (2.4.15.Final) est correcte pour la licence Apache 2.0, mais la 2.6.5.Final apporte le support natif Kafka 3.7 KRaft et améliore la compatibilité Avro 1.11.x. Pas de breaking change pour l'usage SerDe.

**Alternatives considered**: Confluent Schema Registry rejeté (licence restrictive BSL 1.1 depuis 2023).

**Impact**: `pom.xml` parent uniquement — aucun code Java à modifier.

---

## D2 — Génération schémas Avro : `maven-avro-plugin` (pas écriture manuelle)

**Decision**: Configurer `org.apache.avro:avro-maven-plugin:1.11.4` dans le POM de `docai-adapter-out-kafka`. Les fichiers `.avsc` vivent dans `src/main/avro/`. Les classes Java sont auto-générées dans `target/generated-sources/avro/` à chaque build.

**Rationale**: L'écriture manuelle des classes Avro génère des désynchronisations fréquentes entre le schéma registré dans Apicurio et les classes Java. Le plugin garantit la cohérence et permet l'évolution de schéma (champs optionnels avec défaut) sans modifier les classes.

**Alternatives considered**: Protobuf rejeté — trop verbeux pour la sérialisation Kafka interne. Record Java sans schéma rejeté — pas compatible Apicurio Registry.

**Configuration type**:
```xml
<plugin>
  <groupId>org.apache.avro</groupId>
  <artifactId>avro-maven-plugin</artifactId>
  <version>${avro.version}</version>
  <executions>
    <execution>
      <phase>generate-sources</phase>
      <goals><goal>schema</goal></goals>
      <configuration>
        <sourceDirectory>${project.basedir}/src/main/avro/</sourceDirectory>
        <outputDirectory>${project.build.directory}/generated-sources/avro/</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

---

## D3 — Consumer Group IDs : `application.yml` uniquement (FR-016b)

**Decision**: Les Consumer Group IDs sont définis dans `application.yml` comme propriétés nommées et référencés via `${kafka.groups.{module}.{name}}` dans les annotations `@KafkaListener`. La valeur globale `spring.kafka.consumer.group-id: docai-group` est remplacée par des références granulaires par module.

**Rationale**: Un Consumer Group ID hardcodé dans `@KafkaListener(groupId = "...")` rend le rebalancement impossible par profil (dev/staging/prod) et viole FR-016b. La configuration dans `application.yml` permet la surcharge par profil Spring sans modifier le code Java. ArchUnit peut détecter toute annotation `@KafkaListener` avec attribut `groupId` non vide.

**Pattern retenu dans application.yml**:
```yaml
kafka:
  groups:
    upload: docai.upload.processor.group
    extraction:
      llm: docai.extraction.llm.group
      ocr: docai.extraction.ocr.group
    fraud:
      analyser: docai.fraud.analyser.group
    pipeline:
      orchestrator: docai.pipeline.orchestrator.group
    outbox:
      relay: docai.outbox.relay.group
```

**Dans le code Java**:
```java
@KafkaListener(topics = "docai.doc.uploaded", groupId = "${kafka.groups.upload}")
```

---

## D4 — JitterTtl : classe utilitaire dans `docai-commons` (à créer)

**Decision**: Créer le module `docai-commons` (ou sous-package dans `docai-application`) avec la classe `JitterTtl` exposant la méthode statique `withJitter(Duration base)` avec jitter ±10% par défaut.

**Rationale**: ADR-003 impose `JitterTtl.withJitter()` sur tout TTL > 1h. La classe ne doit pas exister dans chaque adaptateur (violation SOLID). La localiser dans `docai-commons` garantit une implémentation unique et testable. `ThreadLocalRandom` est préféré à `Math.random()` pour la thread-safety.

**Implémentation de référence**:
```java
public final class JitterTtl {
    private static final double DEFAULT_JITTER = 0.10; // ±10%
    
    private JitterTtl() {}
    
    public static Duration withJitter(Duration base) {
        return withJitter(base, DEFAULT_JITTER);
    }
    
    public static Duration withJitter(Duration base, double jitterFactor) {
        if (base == null) throw new NullPointerException("base duration must not be null");
        if (jitterFactor < 0 || jitterFactor > 0.25)   // SEC-010: borne supérieure 25%
            throw new IllegalArgumentException("jitterFactor must be in [0.0, 0.25]");
        if (base.isZero() || base.isNegative()) return base;
        double factor = 1.0 + (ThreadLocalRandom.current().nextDouble(-jitterFactor, jitterFactor));
        return Duration.ofMillis((long) (base.toMillis() * factor));
    }
}
```

**Exceptions à TTL fixe** (ne PAS appeler `withJitter()`) :
- Clés d'idempotence Kafka (`idempotent:{topic}:{partition}:{offset}`) → TTL fixe 24h
- JWT blacklist (`jwt:blacklist:{jti}`) → TTL = `token.getExpiresAt() - now()`
- Cache JWKS Keycloak → TTL 1h fixe (ADR-006, géré par Spring Security)
- Cache quota API → TTL = durée jusqu'au 1er du mois suivant (ADR-001)

---

## D5 — Resilience4j : configuration YAML complète (8 services)

**Decision**: Configurer Resilience4j intégralement dans `application.yml` sous les préfixes `resilience4j.circuitbreaker`, `resilience4j.retry`, `resilience4j.bulkhead`, `resilience4j.timelimiter`. Chaque service nommé (`llm`, `tika`, `opencv`, `insee`, `ban`, `rpps`, `s3`, `kafka`) correspond à un `@Bean` ou à une annotation dans l'adaptateur sortant correspondant.

**Rationale**: La configuration YAML est la seule approche qui permet la surcharge par profil Spring sans recompilation. Les annotations `@CircuitBreaker(name="llm")` restent dans le code Java ; les seuils sont externalisés. Testé au démarrage par Spring Boot Actuator (endpoint `/actuator/health` expose l'état des CB).

**Seuils de référence (Constitution Annex A)** :

| Service (nom YAML) | CB seuil | CB fenêtre | Retry | Bulkhead | Timeout |
|-------------------|---------|-----------|-------|----------|---------|
| `llm` | 50% | 10 calls | 3× exp 1s | 20 threads | 30s |
| `tika` | 50% | 5 calls | 2× backoff 1s | 5 threads | **15s** (BR-VIS-003) |
| `opencv` | 50% | 5 calls | 1× | 5 threads | **15s** (BR-VIS-003) |
| `insee` | 60% | 5 calls | 2× backoff 2s | 5 threads | 5s |
| `ban` | 60% | 5 calls | 2× backoff 500ms | 5 threads | 5s |
| `rpps` | 60% | 8 calls | 2× fixed 1s | 5 threads | 5s |
| `s3` | 50% | 10 calls | 3× exp 1s | 20 threads | 30s |
| `kafka` | N/A | N/A | N/A | N/A | N/A |

**Note BR-VIS-003**: Tika et OpenCV utilisent `@TimeLimiter(name="tika")` — en cas de dépassement du timeout 15s, la méthode retourne un résultat vide (fail-safe), le pipeline continue. Pas de propagation d'exception.

---

## D6 — Valkey : configuration `spring.data.redis` → `spring.data.valkey` (ou maintien Redis-compat)

**Decision**: Valkey 8.x est compatible Redis via le protocole RESP3. L'actuel `spring.data.redis` dans `application.yml` est fonctionnel avec Valkey. Le renommer en commentaire explicatif suffira — pas de changement de clé de configuration nécessaire avec Spring Boot 4.x + Lettuce.

**Rationale**: Spring Boot 4.x n'a pas encore un préfixe `spring.data.valkey` distinct. Lettuce 6.x communique avec Valkey via RESP2/RESP3 de façon transparente. Le changement est documentaire uniquement (commentaire dans `application.yml` indiquant "Valkey 8.x (Redis-compatible)").

**Alternatives considered**: Migration vers Jedis rejetée — Lettuce est non-bloquant et supporte le pipelining requis pour les opérations Lua atomiques (ADR-001).

---

## D7 — Topology Kafka existante : conforme ADR-002

**Decision**: La configuration `kafka-init` dans `docker-compose.yml` est déjà conforme ADR-002 (8 topics avec partitions et rétentions correctes). Aucune modification requise sur ce point.

**Vérification**: 
- 6 topics pipeline avec 6 partitions (documentId comme clé implicite — documenté via commentaire ADR-002)  
- `failed` et `dlq` : 3 partitions (tenantId comme clé — exception ADR-002)
- `outbox.relay` : 3 partitions, 1 jour de rétention
- Toutes les rétentions correspondent à la Constitution (7j, 30j, 90j, 1j)

**Gap identifié** : Le topic `docai.doc.validated` de la spec est absent du docker-compose (le docker-compose a `docai.doc.fraud.analyzed` à la place de `docai.doc.validated` et `docai.doc.fraud.detected`). À aligner lors de l'implémentation des modules correspondants.

---

## D8 — ADR-006 : JWKS cache TTL 1h

**Decision**: Spring Security gère automatiquement le cache JWKS lorsque `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` est configuré. Le TTL par défaut est 5 minutes. Il doit être configuré à 1h via la propriété `spring.security.oauth2.resourceserver.jwt.jwks-cache-ttl: 1h` (Spring Boot 4.x).

**Rationale**: ADR-006 exige explicitement 1h pour survivre à une interruption Keycloak. Le TTL par défaut Spring (5 min) ne satisfait pas l'exigence — à configurer explicitement dans `application.yml`.

**Configuration**:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_URL}/realms/docai/protocol/openid-connect/certs
          jwks-cache-ttl: 1h   # ADR-006: 1h pour survivre à interruption Keycloak
```
