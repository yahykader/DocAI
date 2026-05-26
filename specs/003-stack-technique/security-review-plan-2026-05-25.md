---
document_type: security-review
review_type: plan
assessment_date: 2026-05-25
codebase_analyzed: DocAI — specs/003-stack-technique (Module B Stack & Intégrations)
total_files_analyzed: 6
total_findings: 10
overall_risk: HIGH
critical_count: 0
high_count: 2
medium_count: 4
low_count: 2
informational_count: 2
owasp_categories: [A02, A05, A06, A09]
cwe_ids: [CWE-256, CWE-319, CWE-284, CWE-693, CWE-200, CWE-798]
---

# Security Review — Plan: Stack Technique & Intégrations (Module B)

**Date**: 2026-05-25 | **Feature**: `specs/003-stack-technique` | **Risque global**: 🟠 HIGH

---

## Executive Summary

Le plan d'implémentation du Module B est architecturalement solide (hexagonal, ADR-002/003/006 adressés). Cependant, **2 findings HIGH** nécessitent une correction avant implémentation :

1. **SEC-001** — `apicurio.registry.auto-register: true` dans le contrat YAML production-bound expose le Schema Registry à une injection de schéma malveillant (schema poisoning).
2. **SEC-002** — Les endpoints Actuator (`/actuator/health`, `/actuator/circuitbreakers`) sont exposés sans authentification, révélant l'état interne des services (LLM down, CBS open, etc.).

Les 4 findings MEDIUM concernent principalement des URL HTTP (Keycloak, Apicurio) sans TLS forcé au niveau configuration et des credentials hardcodés dans `application.yml`. Ces points doivent être résolus avant la mise en production, mais ne bloquent pas le développement local.

---

## Artefacts Analysés

| Fichier | Statut |
|---------|--------|
| `specs/003-stack-technique/plan.md` | ✅ Lu |
| `specs/003-stack-technique/spec.md` | ✅ Lu |
| `specs/003-stack-technique/research.md` | ✅ Lu |
| `specs/003-stack-technique/data-model.md` | ✅ Lu |
| `specs/003-stack-technique/contracts/application-yml.md` | ✅ Lu |
| `specs/003-stack-technique/quickstart.md` | ✅ Lu |
| `.specify/memory/constitution.md` | ✅ Lu |
| `backend/docai-bootstrap/src/main/resources/application.yml` | ✅ Lu |

---

## Findings de Sécurité

### SEC-001 — Schema Poisoning via Apicurio auto-register (HIGH)

**Location**: `specs/003-stack-technique/contracts/application-yml.md` — BLOC 2  
**OWASP**: A05:2025-Security Misconfiguration  
**CWE**: CWE-693 — Protection Mechanism Failure  
**CVSS**: 7.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N)  
**TASK-SEC**: TASK-SEC-001

**Description**: Le contrat YAML définit `apicurio.registry.auto-register: true`, ce qui permet à tout producteur Kafka authentifié d'enregistrer de nouveaux schémas ou d'évoluer des schémas existants. En production, un acteur malveillant disposant d'accès au réseau Kafka pourrait :

1. Enregistrer un schéma modifié pour `DocumentUploadedEvent` (ex. champ `documentId` nullable)
2. Provoquer des erreurs de désérialisation côté consommateurs
3. Injecter des champs non prévus dans le pipeline de traitement

**Configuration actuelle dans le contrat** :
```yaml
# RISQUE SEC-001
apicurio.registry.auto-register: true   # ← Dangereux en production
apicurio.registry.find-latest: true
```

**Remédiation** :
```yaml
# Environnement dev/local uniquement
apicurio.registry.auto-register: ${APICURIO_AUTO_REGISTER:false}

# Production: false obligatoire — les schémas sont déployés via CI/CD uniquement
# Ajouter dans application-prod.yml:
apicurio.registry.auto-register: false
apicurio.registry.find-latest: false
apicurio.registry.schema-id: ${APICURIO_SCHEMA_ID}  # ID fixe par version
```

**Action plan** : Mettre à jour le contrat YAML pour différencier dev (`auto-register: true`) et production (`auto-register: false`, déploiement via `mvn avro:schema-register` dans le pipeline CI/CD).

---

### SEC-002 — Actuator endpoints sans authentification (HIGH)

**Location**: `backend/docai-bootstrap/src/main/resources/application.yml` — section `management`  
**OWASP**: A05:2025-Security Misconfiguration  
**CWE**: CWE-284 — Improper Access Control  
**CVSS**: 7.2 (AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)  
**TASK-SEC**: TASK-SEC-002

**Description**: Le plan expose `/actuator/health,metrics,prometheus,info` sans authentification. L'endpoint `/actuator/health` incluant les instances Resilience4j révèle :

- Quels services externes (LLM, INSEE, BAN...) sont en état `OPEN` (Circuit Breaker)
- L'état de la connexion Valkey et MongoDB
- Les métriques Prometheus incluant les taux d'erreur par service

Un attaquant peut utiliser ces informations pour cibler des attaques au moment où les services sont déjà fragilisés (amplification d'incident), ou pour cartographier l'infrastructure.

**Configuration actuelle dans application.yml** :
```yaml
# RISQUE SEC-002
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info   # ← Pas de restriction d'accès
```

**Remédiation** :
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info   # health simplifié pour load balancer
  endpoint:
    health:
      show-details: when-authorized   # Détails CB uniquement aux admins
      show-components: when-authorized
  security:
    enabled: true   # Spring Security protège /actuator/* (sauf /health simple)

# Exposer /actuator/metrics et /actuator/prometheus sur un port interne uniquement
# application-prod.yml:
management:
  server:
    port: 9090   # Port interne non exposé publiquement (Prometheus scrape)
```

**Action plan** : Séparer l'exposition des métriques (port interne Prometheus) de l'health check public (load balancer). Configurer Spring Security pour protéger les détails Actuator.

---

### SEC-003 — URL Keycloak et Apicurio en HTTP sans TLS forcé (MEDIUM)

**Location**: `specs/003-stack-technique/contracts/application-yml.md` — BLOC 2 et BLOC 5  
**OWASP**: A02:2025-Cryptographic Failures  
**CWE**: CWE-319 — Cleartext Transmission of Sensitive Information  
**CVSS**: 5.9 (AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N)  
**TASK-SEC**: TASK-SEC-003

**Description**: Les URLs de Keycloak et Apicurio utilisent `http://` comme valeur par défaut :

```yaml
apicurio.registry.url: ${APICURIO_URL:http://localhost:8081}   # HTTP
issuer-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/docai  # HTTP
jwk-set-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/docai/protocol/openid-connect/certs
```

Sans TLS : les tokens JWT transitent en clair entre le service et Keycloak (validation JWKS), et les schémas Avro transitent en clair vers Apicurio. En production sans forçage TLS au niveau application, une mauvaise configuration opérationnelle peut passer inaperçue.

**Remédiation** :
- Ajouter une validation au démarrage Spring Boot qui vérifie que les URLs de production commencent par `https://`
- Documenter dans le quickstart : "en production, `KEYCLOAK_URL` et `APICURIO_URL` DOIVENT utiliser `https://`"
- Envisager un `@Bean EnvironmentPostProcessor` qui refuse le démarrage si `spring.profiles.active=prod` et URLs HTTP détectées

---

### SEC-004 — Credentials MongoDB hardcodés dans application.yml (MEDIUM)

**Location**: `backend/docai-bootstrap/src/main/resources/application.yml` — ligne 11  
**OWASP**: A05:2025-Security Misconfiguration  
**CWE**: CWE-256 — Plaintext Storage of a Password / CWE-798 — Hardcoded Credentials  
**CVSS**: 5.5 (AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N)  
**TASK-SEC**: TASK-SEC-004

**Description**: Le fichier `application.yml` committé contient des credentials MongoDB en clair :
```yaml
uri: mongodb://admin:password@localhost:27017/docai?authSource=admin&replicaSet=rs0
```

Bien que le plan mentionne l'utilisation de `.env` pour les environnements hors local (Constitution Section VII), le fichier actuel commissionne `admin:password` en dur dans git. Si un développeur ne configure pas son `.env` correctement, l'application utilise ces credentials.

**Remédiation** :
```yaml
# Remplacer par des variables d'environnement avec valeur par défaut UNIQUEMENT pour les tests locaux
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://admin:password@localhost:27017/docai?authSource=admin&replicaSet=rs0}
      # ↑ La valeur par défaut est acceptable pour dev local uniquement
      # En production, MONGODB_URI doit pointer vers AWS Secrets Manager (Spring Cloud AWS)
```

Le plan devrait aussi documenter explicitement que `MONGODB_URI` en production est injecté depuis AWS Secrets Manager — cela n'est pas mentionné dans le quickstart du Module B.

---

### SEC-005 — Valkey sans authentification ni TLS dans le contrat (MEDIUM)

**Location**: `specs/003-stack-technique/contracts/application-yml.md` — BLOC 4  
**OWASP**: A02:2025-Cryptographic Failures  
**CWE**: CWE-319 — Cleartext Transmission of Sensitive Information  
**CVSS**: 5.3 (AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N)  
**TASK-SEC**: TASK-SEC-005

**Description**: Le contrat YAML BLOC 4 configure Valkey sans password ni TLS :

```yaml
spring:
  data:
    redis:
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      timeout: 60000ms
      # ← Pas de password, pas de ssl: true
```

Le cache Valkey contient des données sensibles : tokens JWT blacklistés, clés d'idempotence Kafka, résultats d'extraction LLM (potentiellement des données PII). Un accès non authentifié à Valkey permettrait de lire ou modifier ces données.

**Remédiation** :
```yaml
spring:
  data:
    redis:
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      password: ${VALKEY_PASSWORD:}   # Vide en dev local, obligatoire en production
      timeout: 60000ms
      ssl:
        enabled: ${VALKEY_SSL_ENABLED:false}   # false en dev, true en production
```

Documenter dans quickstart production : Valkey DOIT avoir `requirepass` configuré + TLS activé.

---

### SEC-006 — SEC-006 — auto-index-creation: true en dev (MEDIUM)

**Location**: `backend/docai-bootstrap/src/main/resources/application.yml` — ligne 12  
**OWASP**: A05:2025-Security Misconfiguration  
**CWE**: CWE-284 — Improper Access Control  
**CVSS**: 4.3 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:L)  
**TASK-SEC**: TASK-SEC-006

**Description**: La Constitution (Annex B MongoDB) exige explicitement `auto-index-creation: false`. L'actuel `application.yml` a `auto-index-creation: true`. Bien que cela ne soit qu'un risque de dev local, ce paramètre peut :

1. Créer des index non prévus qui exposent des champs non indexés délibérément
2. Créer des conditions de course lors du démarrage avec plusieurs instances

**Remédiation** : Corriger dans `application.yml` :
```yaml
spring:
  data:
    mongodb:
      auto-index-creation: false   # Constitution Annex B — OBLIGATOIRE
```
Et ajouter à la checklist de complétion du Module B.

---

### SEC-007 — Consumer Group IDs prédictibles — énumération Kafka (LOW)

**Location**: `specs/003-stack-technique/contracts/application-yml.md` — BLOC 1  
**OWASP**: A09:2025-Security Logging and Monitoring Failures  
**CWE**: CWE-200 — Exposure of Sensitive Information to Unauthorized Actor  
**CVSS**: 3.1 (AV:N/AC:H/PR:L/UI:N/S:U/C:L/I:N/A:N)  
**TASK-SEC**: TASK-SEC-007

**Description**: Les Consumer Group IDs suivent le pattern `docai.{module}.{name}.group`, ce qui révèle l'architecture interne des modules. Si le broker Kafka est accessible depuis un réseau non sécurisé, un attaquant peut :

1. Lister les Consumer Groups via `kafka-consumer-groups.sh --list`
2. Déduire les modules actifs et leur état (lag, offset)
3. Potentiellement rejoindre un groupe (si autorisation absente)

**Remédiation** : Documenter dans le contrat que l'accès à Kafka doit être restreint par ACL en production (`kafka.authorizer.class.name`). Ajouter une note de sécurité dans `quickstart.md` pour la configuration ACL Kafka en production.

---

### SEC-008 — CircuitBreaker `waitDurationInOpenState` trop court pour LLM (LOW)

**Location**: `specs/003-stack-technique/contracts/application-yml.md` — BLOC 3 (CB `llm`)  
**OWASP**: A05:2025-Security Misconfiguration  
**CWE**: CWE-400 — Uncontrolled Resource Consumption  
**CVSS**: 3.7 (AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:N/A:L)  
**TASK-SEC**: TASK-SEC-008

**Description**: `waitDurationInOpenState: 30s` pour le Circuit Breaker LLM signifie qu'après 10 appels en échec, le CB s'ouvre et se referme après 30 secondes. Si le service LLM est en surcharge (ex. rate limit 429), 30 secondes est insuffisant — le CB va alterner OPEN/HALF-OPEN rapidement, générant des appels répétés à chaque demi-ouverture.

**Remédiation** :
```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm:
        waitDurationInOpenState: 60s   # Minimum 60s pour respecter les retry windows LLM
        permittedNumberOfCallsInHalfOpenState: 1   # Sonde unique avant réouverture complète
        slowCallDurationThreshold: 25s   # Appels LLM > 25s comptent comme lents
        slowCallRateThreshold: 80         # 80% d'appels lents → ouverture aussi
```

---

### SEC-009 — Libraries de traitement documentaire : surface d'attaque non bornée (INFORMATIONAL)

**Location**: `specs/003-stack-technique/plan.md` — Technical Context (Tess4J, PDFBox, Tika, JavaCV)  
**OWASP**: A06:2025-Vulnerable and Outdated Components  
**CWE**: CWE-20 — Improper Input Validation  
**CVSS**: N/A (informational)  
**TASK-SEC**: TASK-SEC-009

**Description**: Les libraries Tess4J, PDFBox 3.x, Apache Tika 2.9.2 et JavaCV 1.5.11 traitent des documents fournis par des utilisateurs externes. Ces libraries ont un historique de vulnérabilités liées à :

- **PDFBox** : Parsing de PDFs malformés pouvant provoquer OOM ou parsing attacks
- **Tika** : Détection de type MIME vulnérable à la manipulation d'en-tête de fichier
- **Tess4J** : Wraps Tesseract C++ — vulnérabilités natives (buffer overflows historiques)
- **JavaCV** : Wraps OpenCV natif — surface C++ avec vulnérabilités potentielles

Le plan ne mentionne pas de politique de sandboxing (isolation de processus, quotas mémoire) pour le traitement documentaire.

**Recommandation** : Documenter dans les specs des Modules 1-3 que le traitement documentaire doit s'exécuter avec :
- Limite mémoire JVM dédiée (`-Xmx256m` pour workers de traitement)
- Timeout impératif (BR-VIS-003 pour Tika/OpenCV est un bon premier garde-fou)
- Validation du type MIME avant traitement (ne pas faire confiance à l'extension de fichier)
- Dépendabot configuré pour mises à jour hebdomadaires de ces libraries

---

### SEC-010 — JitterTtl : absence de borne maximale sur le TTL résultant (INFORMATIONAL)

**Location**: `specs/003-stack-technique/research.md` — D4 (JitterTtl implémentation)  
**OWASP**: A05:2025-Security Misconfiguration  
**CWE**: CWE-400 — Uncontrolled Resource Consumption  
**CVSS**: N/A (informational)  
**TASK-SEC**: TASK-SEC-010

**Description**: L'implémentation proposée de `JitterTtl.withJitter()` n'impose pas de borne maximale sur le TTL résultant. Avec un `jitterFactor` non contrôlé (si un développeur passe 0.5 au lieu de 0.10), le TTL peut être multiplié par 1.5, étendant la durée de cache indéfiniment pour des données devenant stale (ex. données RPPS d'un praticien révoqué).

**Recommandation** :
```java
public static Duration withJitter(Duration base, double jitterFactor) {
    if (jitterFactor < 0 || jitterFactor > 0.25) {
        throw new IllegalArgumentException("jitterFactor must be in [0.0, 0.25] — ADR-003");
    }
    // ...
}
```

Cela borne le jitter à ±25% maximum et prévient les usages accidentels hors spec (ADR-003 spécifie ±10%).

---

## Patterns Sécurisés Confirmés

✅ **ADR-002 conforme** — Kafka `documentId` comme clé de partition sur les topics pipeline  
✅ **ADR-006 adressé** — `jwks-cache-ttl: 1h` explicitement dans le contrat (vs défaut Spring 5min)  
✅ **FR-016b renforcé** — Consumer Group IDs dans `application.yml` (jamais `@KafkaListener`)  
✅ **TimeLimiter fail-safe** — `cancelRunningFuture: true` + pipeline continue sur timeout BR-VIS-003  
✅ **JitterTtl thread-safe** — `ThreadLocalRandom.current()` au lieu de `Math.random()`  
✅ **JWT blacklist TTL fixe** — Expiration exacte du token, pas de jitter (ADR-003 exception correcte)  
✅ **Idempotence TTL 24h fixe** — Précision de déduplication garantie (ADR-003 exception correcte)  
✅ **Quota TTL fixe** — Reset au 1er du mois (ADR-001 + ADR-003 exception documentée)  
✅ **Kafka auto-create-topics: false** — docker-compose déjà correctement configuré  
✅ **maven-avro-plugin** — Génération auto-évite la désynchronisation manuelle des schémas

---

## Action Plan — Résumé des Remédiations

| Finding | Sévérité | Action | Moment |
|---------|---------|--------|--------|
| SEC-001 Apicurio auto-register | 🔴 HIGH | Différencier dev/prod dans contrat YAML | Avant implémentation Étape 1 |
| SEC-002 Actuator sans auth | 🔴 HIGH | Port séparé + `show-details: when-authorized` | Avant implémentation Étape 4 |
| SEC-003 URLs HTTP Keycloak/Apicurio | 🟠 MEDIUM | Validation au démarrage + doc quickstart | Étape 4 (application.yml) |
| SEC-004 MongoDB creds hardcodés | 🟠 MEDIUM | Variable d'env avec default dev uniquement | Étape 3 (déjà acceptable) |
| SEC-005 Valkey sans auth/TLS | 🟠 MEDIUM | Ajouter `password` + `ssl.enabled` dans contrat | Étape 3 |
| SEC-006 auto-index-creation: true | 🟠 MEDIUM | Corriger `false` dans application.yml | Étape 1 (trivial) |
| SEC-007 Consumer Group énumération | 🟡 LOW | Documenter ACL Kafka pour production | Quickstart production |
| SEC-008 CB LLM waitDuration 30s | 🟡 LOW | Augmenter à 60s + slowCallThreshold | Étape 4 |
| SEC-009 Libraries doc processing | ℹ️ INFO | Sandboxing + Dépendabot + validation MIME | Specs Modules 1-3 |
| SEC-010 JitterTtl sans borne max | ℹ️ INFO | Ajouter garde-fou `jitterFactor ≤ 0.25` | Étape 3 (JitterTtl.java) |

**Findings bloquants avant implémentation** : SEC-001, SEC-002  
**Findings à adresser en parallèle** : SEC-005, SEC-006 (trivials)  
**Findings pour production uniquement** : SEC-003, SEC-004, SEC-007

---

## Memory Hub INDEX.md Row

```text
| specs/003-stack-technique/security-review-plan-2026-05-25.md | plan | 2026-05-25 | HIGH | C:0 H:2 M:4 L:2 | A02,A05,A06,A09 |
```
