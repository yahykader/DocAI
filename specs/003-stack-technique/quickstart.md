# Quickstart: Implémentation Module B — Stack Technique

**Durée estimée**: 4 étapes × ~2h = ~1 journée  
**Prérequis**: Module 1.A (Setup Projet) terminé, Docker Compose opérationnel  
**Date**: 2026-05-25

---

## Étape 1 — POM parent : versions exactes (BOM)

**Objectif**: Corriger `apicurio.version` et vérifier les 9 versions critiques.

### Fichier à modifier: `backend/pom.xml`

**Correction obligatoire** (Apicurio 2.4 → 2.6) :
```xml
<!-- Avant -->
<apicurio.version>2.4.15.Final</apicurio.version>

<!-- Après -->
<apicurio.version>2.6.5.Final</apicurio.version>
```

**Vérification des 9 versions critiques** (toutes déjà correctes sauf Apicurio) :

| Propriété | Valeur requise | Valeur actuelle | Action |
|-----------|---------------|----------------|--------|
| `resilience4j.version` | `2.3.0` | `2.3.0` | ✅ OK |
| `bucket4j.version` | `8.10.1` | `8.10.1` | ✅ OK |
| `avro.version` | `1.11.4` | `1.11.4` | ✅ OK |
| `apicurio.version` | `2.6.5.Final` | `2.4.15.Final` | ⚠️ Mettre à jour |
| `tess4j.version` | `5.13.0` | `5.13.0` | ✅ OK |
| `pdfbox.version` | `3.0.3` | `3.0.3` | ✅ OK |
| `tika.version` | `2.9.2` | `2.9.2` | ✅ OK |
| `javacv.version` | `1.5.11` | `1.5.11` | ✅ OK |
| `aws-sdk.version` | `2.25.70` | `2.25.70` | ✅ OK |

**Configurer `maven-avro-plugin` dans `docai-adapter-out-kafka/pom.xml`** :
```xml
<build>
  <plugins>
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
            <stringType>String</stringType>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**Créer les répertoires** :
```bash
mkdir -p backend/docai-adapter-out-kafka/src/main/avro
```

**Créer un premier schéma Avro de test** (`DocumentUploadedEvent.avsc`) :
```json
{
  "type": "record",
  "name": "DocumentUploadedEvent",
  "namespace": "fr.docai.kafka.avro",
  "fields": [
    {"name": "documentId", "type": "string"},
    {"name": "tenantId", "type": "string"},
    {"name": "s3Key", "type": "string"},
    {"name": "uploadedAt", "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

**Vérification** :
```bash
cd backend
./mvnw clean compile -pl docai-adapter-out-kafka
# → BUILD SUCCESS
# → target/generated-sources/avro/fr/docai/kafka/avro/DocumentUploadedEvent.java généré
```

---

## Étape 2 — Topologie Kafka dans docker-compose (vérification)

**Objectif**: Confirmer les 8 topics présents dans Kafka UI.

**Constat**: La configuration `kafka-init` dans `docker-compose.yml` est déjà conforme ADR-002. Aucune modification requise.

**Lancer l'infrastructure** :
```bash
docker compose up -d kafka kafka-init kafka-ui
docker compose logs -f kafka-init
# → Attendre "Topics created successfully"
```

**Vérification** :
1. Ouvrir http://localhost:8090 (Kafka UI)
2. Aller dans Topics
3. Vérifier la présence des 8 topics :

| Topic attendu | Partitions | Rétention |
|--------------|-----------|-----------|
| `docai.doc.uploaded` | 6 | 7 jours |
| `docai.doc.classified` | 6 | 7 jours |
| `docai.doc.extracted` | 6 | 7 jours |
| `docai.doc.fraud.analyzed` | 6 | 7 jours |
| `docai.doc.completed` | 3 | 30 jours |
| `docai.doc.failed` | 3 | 30 jours |
| `docai.doc.dlq` | 3 | 90 jours |
| `docai.outbox.relay` | 3 | 1 jour |

**Ajouter les Consumer Group IDs dans `application.yml`** :
```bash
# Éditer backend/docai-bootstrap/src/main/resources/application.yml
# Supprimer: spring.kafka.consumer.group-id: docai-group
# Ajouter le bloc BLOC 1 du contrat application-yml.md
```

---

## Étape 3 — Configuration Valkey + classe JitterTtl

**Objectif**: Créer `JitterTtl` dans `docai-commons` et documenter les 9 stratégies de cache.

### 3a — Classe JitterTtl

**Créer** `backend/docai-adapter-out-valkey/src/main/java/fr/docai/adapter/out/valkey/util/JitterTtl.java` :

```java
package fr.docai.adapter.out.valkey.util;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class JitterTtl {
    
    private static final double DEFAULT_JITTER = 0.10;
    
    private JitterTtl() {}
    
    public static Duration withJitter(Duration base) {
        return withJitter(base, DEFAULT_JITTER);
    }
    
    public static Duration withJitter(Duration base, double jitterFactor) {
        if (base == null) throw new NullPointerException("base duration must not be null");
        if (jitterFactor < 0 || jitterFactor > 0.25)   // SEC-010: borne supérieure 25%
            throw new IllegalArgumentException("jitterFactor must be in [0.0, 0.25]");
        if (base.isZero() || base.isNegative()) return base;
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-jitterFactor, jitterFactor);
        return Duration.ofMillis((long) (base.toMillis() * factor));
    }
}
```

**Créer le test** `JitterTtlTest.java` :
```java
// Tests: withJitter retourne une valeur dans [base*0.9, base*1.1]
// Tests: withJitter(Duration.ZERO) retourne Duration.ZERO
// Tests: withJitter(null) lève NullPointerException
// Tests: withJitter(base, 0.30) lève IllegalArgumentException  ← SEC-010
// Tests: withJitter(base, -0.01) lève IllegalArgumentException ← SEC-010
```

### 3b — Configuration Valkey dans application.yml

Ajouter les blocs BLOC 4 et BLOC 5 du contrat `contracts/application-yml.md`.

**Vérification Valkey** :
```bash
docker compose up -d valkey
docker compose exec valkey redis-cli ping
# → PONG
```

**Vérification connexion Spring** :
```bash
cd backend
./mvnw spring-boot:run -pl docai-bootstrap &
curl http://localhost:8080/api/actuator/health | jq '.components.redis'
# → {"status":"UP","details":{"version":"..."}}
```

---

## Étape 4 — Configuration Resilience4j dans application.yml

**Objectif**: Déclarer les 8 instances Resilience4j avec seuils exacts.

### Ajouter le bloc BLOC 3 du contrat `contracts/application-yml.md`

**Points critiques** :
- `tika.timeoutDuration: 15s` — obligatoire BR-VIS-003
- `opencv.timeoutDuration: 15s` — obligatoire BR-VIS-003
- `cancelRunningFuture: true` — annulation du thread async en cas de timeout

**Vérification** :
```bash
cd backend
./mvnw spring-boot:run -pl docai-bootstrap
curl http://localhost:8080/api/actuator/health | jq '.components.circuitBreakers'
# → Toutes les instances doivent apparaître avec state: CLOSED
```

**Vérification ADR-006** (JWKS cache 1h) :
```bash
# Ajouter dans application.yml: spring.security.oauth2.resourceserver.jwt.jwks-cache-ttl: 1h
# Redémarrer et vérifier les logs: "JWKS cache TTL set to PT1H"
```

---

## Vérification finale (toutes étapes)

```bash
# 1. Build complet sans erreur
cd backend && ./mvnw clean compile
# → BUILD SUCCESS

# 2. 8 topics dans Kafka UI
# → http://localhost:8090

# 3. Valkey PING
docker compose exec valkey redis-cli ping
# → PONG

# 4. Application démarrée sans erreur
curl http://localhost:8080/api/actuator/health
# → {"status":"UP"}

# 5. Resilience4j instances chargées
curl http://localhost:8080/api/actuator/health | grep circuitBreaker
# → 7 instances: llm, tika, opencv, insee, ban, rpps, s3

# 6. Aucun groupId hardcodé (vérification grep)
grep -r 'groupId\s*=\s*"docai\.' backend/src/main/java/ 2>/dev/null
# → Aucun résultat attendu
```

---

## Checklist de complétion Module B

- [ ] `apicurio.version` = `2.6.5.Final` dans `backend/pom.xml`
- [ ] `maven-avro-plugin` configuré dans `docai-adapter-out-kafka/pom.xml`
- [ ] `src/main/avro/` créé avec au moins `DocumentUploadedEvent.avsc`
- [ ] `./mvnw clean compile` → BUILD SUCCESS
- [ ] 8 topics visibles dans Kafka UI (http://localhost:8090)
- [ ] Consumer Group IDs dans `application.yml` uniquement (0 occurrence dans `@KafkaListener`)
- [ ] `JitterTtl.java` créé avec tests unitaires
- [ ] Valkey PING → PONG
- [ ] `spring.security.oauth2.resourceserver.jwt.jwks-cache-ttl: 1h` dans `application.yml`
- [ ] 7 instances Resilience4j configurées (llm, tika, opencv, insee, ban, rpps, s3)
- [ ] `application.actuator/health` → `{"status":"UP"}`
