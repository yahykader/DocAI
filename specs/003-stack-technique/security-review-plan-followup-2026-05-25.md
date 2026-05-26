---
document_type: security-review
review_type: plan
assessment_date: 2026-05-25
codebase_analyzed: DocAI / specs/003-stack-technique (post-correction followup)
total_files_analyzed: 9
total_findings: 5
overall_risk: MODERATE
critical_count: 0
high_count: 0
medium_count: 2
low_count: 1
informational_count: 2
owasp_categories: [A05, A09]
cwe_ids: [CWE-778, CWE-732, CWE-710]
field_summaries:
  document_type: "Always 'security-review'. Allows indexers to skip non-review documents."
  review_type: "Which command generated this document: audit, branch, staged, plan, tasks, or followup."
  assessment_date: "ISO 8601 date the review was performed (YYYY-MM-DD)."
  overall_risk: "Highest severity tier with active findings (CRITICAL, HIGH, MODERATE, LOW, INFORMATIONAL)."
  critical_count: "Number of Critical findings (CVSS 9.0-10.0)."
  high_count: "Number of High findings (CVSS 7.0-8.9)."
  medium_count: "Number of Medium findings (CVSS 4.0-6.9)."
  low_count: "Number of Low findings (CVSS 0.1-3.9)."
  informational_count: "Number of Informational findings."
  owasp_categories: "OWASP Top 10 2025 categories (A01-A10) that have at least one finding."
  cwe_ids: "CWE identifiers referenced in this document."
  finding_id: "Unique finding identifier (SEC-NNN) for cross-referencing and task linkage."
  location: "File path and line number of the vulnerable code (path/to/file.ext:line)."
  owasp_category: "OWASP Top 10 2025 category for this finding (AXX:2025-Name)."
  cwe: "Common Weakness Enumeration identifier with short name (CWE-NNN: Name)."
  cvss_score: "CVSS v3.1 base score (0.0-10.0). 9.0+=Critical, 7.0-8.9=High, 4.0-6.9=Medium, 0.1-3.9=Low."
  spec_kit_task: "Spec-Kit task ID for backlog tracking and remediation follow-up (TASK-SEC-NNN)."
---

# Security Review — Module B Stack Technique (Post-Correction Followup)

**Review type**: Plan / Followup (v2 — après "corriger all")  
**Date**: 2026-05-25  
**Reviewer**: Claude Code (Sonnet 4.6)  
**Previous review**: `specs/003-stack-technique/security-review-plan-2026-05-25.md` (10 findings, HIGH)  
**Risk delta**: HIGH → **MODERATE**

---

## Executive Summary

La correction des 10 findings identifiés lors de la première revue de sécurité a réduit le profil de risque de HIGH à **MODERATE**. Les deux findings HIGH (SEC-001 Apicurio auto-register, SEC-002 Actuator non authentifié) sont résolus. Aucun nouveau finding CRITICAL ou HIGH n'a été introduit.

Cependant, la correction SEC-002 (séparation port management 9091) a créé une **rupture silencieuse du monitoring Prometheus** (SEC-011) : le port cible dans `prometheus.yml` n'a pas été mis à jour, et l'endpoint `prometheus` n'est pas inclus dans la liste d'exposition du port 9091. Sans correction, Prometheus ne scrappe plus rien depuis Spring Boot — la surveillance de sécurité est aveugle en production.

Le deuxième finding MEDIUM (SEC-012) concerne le drift entre `.env.example` et les nouvelles variables d'environnement introduites par les corrections — risque de misconfiguration silencieuse dans les nouveaux environnements.

**Recommandation avant `/speckit-tasks`** : Corriger SEC-011 et SEC-012 (30 minutes).

---

## Artefacts analysés (9 fichiers)

| Fichier | Statut |
|---------|--------|
| `specs/003-stack-technique/plan.md` | Inchangé — conforme |
| `specs/003-stack-technique/spec.md` | Inchangé — conforme |
| `specs/003-stack-technique/research.md` | Gap SEC-013 identifié |
| `specs/003-stack-technique/data-model.md` | Inchangé — conforme |
| `specs/003-stack-technique/contracts/application-yml.md` | Corrigé (SEC-001→010) — 1 gap résiduel (SEC-011) |
| `specs/003-stack-technique/quickstart.md` | Corrigé (SEC-010 guard) |
| `backend/docai-bootstrap/src/main/resources/application.yml` | Corrigé — gap SEC-011 résiduel |
| `prometheus.yml` | **Non mis à jour** — SEC-011 |
| `.env.example` | **Non mis à jour** — SEC-012 |

---

## Statut des findings précédents (SEC-001 → SEC-010)

| Finding | Sévérité | Statut | Correction appliquée |
|---------|----------|--------|---------------------|
| SEC-001 | HIGH | ✅ RÉSOLU | `${APICURIO_AUTO_REGISTER:false}` |
| SEC-002 | HIGH | ✅ RÉSOLU | Port 9091 + `when_authorized` + `health,info` |
| SEC-003 | MEDIUM | ✅ RÉSOLU | Commentaires HTTPS + `${KEYCLOAK_URL}` env var |
| SEC-004 | MEDIUM | ✅ RÉSOLU | `${MONGODB_URI:...}` |
| SEC-005 | MEDIUM | ✅ RÉSOLU | `${VALKEY_PASSWORD:}` + `${VALKEY_SSL_ENABLED:false}` |
| SEC-006 | MEDIUM | ✅ RÉSOLU | `auto-index-creation: false` |
| SEC-007 | LOW | ✅ RÉSOLU | Note documentaire BLOC 6 |
| SEC-008 | LOW | ✅ RÉSOLU | LLM CB `waitDurationInOpenState: 60s` |
| SEC-009 | INFO | ✅ RÉSOLU | Note sandbox documentaire BLOC 6 |
| SEC-010 | INFO | ✅ RÉSOLU | Guard `jitterFactor ∈ [0.0, 0.25]` + 2 tests |

---

## Nouveaux Findings

---

### SEC-011 — Double rupture Prometheus (Monitoring aveugle)

| Attribut | Valeur |
|----------|--------|
| **Finding ID** | SEC-011 |
| **Sévérité** | MEDIUM |
| **CVSS Score** | 5.8 |
| **OWASP** | A09:2025 — Security Logging and Monitoring Failures |
| **CWE** | CWE-778: Insufficient Logging |
| **Spec-Kit Task** | TASK-SEC-011 |

**Localisation** :
- `backend/docai-bootstrap/src/main/resources/application.yml` lignes 67-68
- `specs/003-stack-technique/contracts/application-yml.md` BLOC 6
- `prometheus.yml` lignes 13-15

**Description** :

La correction SEC-002 a séparé les endpoints Actuator sur le port 9091. Cela a créé deux problèmes simultanés qui rendent le scraping Prometheus silencieusement non-fonctionnel :

**Problème 1** — `prometheus.yml` toujours sur port 8080 :
```yaml
# prometheus.yml — ligne 13-15 (NON mis à jour)
- job_name: 'spring-boot'
  metrics_path: '/actuator/prometheus'
  static_configs:
    - targets: ['localhost:8080']   # ← port applicatif, pas le port management
```
Avec `management.server.port: 9091`, le endpoint `/actuator/prometheus` n'existe plus sur le port 8080.

**Problème 2** — `prometheus` absent de la liste d'exposition sur port 9091 :
```yaml
# application.yml — ligne 67-68
management:
  endpoints:
    web:
      exposure:
        include: health,info   # ← 'prometheus' et 'metrics' manquants
```
Même en corrigeant le port dans `prometheus.yml`, l'endpoint `/actuator/prometheus` n'est pas exposé sur 9091.

**Impact** : Prometheus ne scrappe aucune métrique depuis Spring Boot. Les dashboards Grafana sont vides. Les alertes basées sur les métriques (latence, erreurs, CB ouverts) sont silencieuses. La surveillance de sécurité est aveugle.

**Correction** :

```yaml
# 1. application.yml — exposer prometheus sur port 9091 (interne)
management:
  server:
    port: ${MANAGEMENT_PORT:9091}
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # ← ajouter metrics,prometheus
```

```yaml
# 2. prometheus.yml — corriger la cible vers port 9091
- job_name: 'spring-boot'
  metrics_path: '/api/actuator/prometheus'   # noter le context-path /api
  static_configs:
    - targets: ['localhost:9091']   # ← port management
  scrape_interval: 5s
  scrape_timeout: 5s
```

> **Note** : `server.servlet.context-path: /api` s'applique au port 8080 (applicatif). Le management port 9091 n'hérite PAS du context-path — l'URL correcte sur 9091 est `/actuator/prometheus` (sans `/api`).

---

### SEC-012 — `.env.example` désynchronisé (6 variables orphelines)

| Attribut | Valeur |
|----------|--------|
| **Finding ID** | SEC-012 |
| **Sévérité** | MEDIUM |
| **CVSS Score** | 4.7 |
| **OWASP** | A05:2025 — Security Misconfiguration |
| **CWE** | CWE-732: Incorrect Permission Assignment for Critical Resource |
| **Spec-Kit Task** | TASK-SEC-012 |

**Localisation** : `.env.example` (toutes les lignes pertinentes)

**Description** :

Les corrections SEC-001 à SEC-010 ont introduit ou renommé plusieurs variables d'environnement dans `application.yml`, mais `.env.example` n'a pas été mis à jour. Un développeur configurant un nouvel environnement à partir de `.env.example` obtiendra une application mal configurée sans message d'erreur clair.

**Variables orphelines / incorrectes dans `.env.example`** :

| Variable dans `.env.example` | Statut | Correction |
|------------------------------|--------|------------|
| `REDIS_HOST=localhost` | ❌ Orpheline | → `VALKEY_HOST=localhost` |
| `REDIS_PORT=6379` | ❌ Orpheline | → `VALKEY_PORT=6379` |
| `REDIS_PASSWORD=` | ❌ Orpheline | → `VALKEY_PASSWORD=` |
| `KAFKA_BROKERS=localhost:9092` | ❌ Orpheline | → `KAFKA_BOOTSTRAP_SERVERS=localhost:9092` |
| `KAFKA_SCHEMA_REGISTRY=http://localhost:8081` | ❌ Obsolète | → `APICURIO_URL=http://localhost:8081` |
| `KAFKA_CONSUMER_GROUP=docai-group` | ❌ Obsolète | Supprimer (remplacé par `kafka.groups.*`) |

**Variables manquantes dans `.env.example`** :

| Variable | Valeur locale | Notes |
|----------|--------------|-------|
| `VALKEY_SSL_ENABLED=false` | false | true en production |
| `APICURIO_AUTO_REGISTER=true` | true | false en production (SEC-001) |
| `MANAGEMENT_PORT=9091` | 9091 | Port management Actuator |
| `KEYCLOAK_URL=http://localhost:8180` | localhost:8180 | Utilisé dans application.yml |
| `OTLP_ENDPOINT=http://localhost:4317` | localhost:4317 | OTEL tracing endpoint |

**Risque spécifique** : `APICURIO_AUTO_REGISTER` manquant dans `.env.example` signifie que sans un `.env` explicite, la valeur `false` (défaut production-safe) s'applique — mais un développeur ne saura pas qu'il doit passer `true` pour le développement local. Ce cas inverse sera le plus courant.

---

### SEC-013 — `research.md:D4` JitterTtl sans garde de borne (divergence de référence)

| Attribut | Valeur |
|----------|--------|
| **Finding ID** | SEC-013 |
| **Sévérité** | LOW |
| **CVSS Score** | 2.5 |
| **OWASP** | A05:2025 — Security Misconfiguration |
| **CWE** | CWE-710: Improper Adherence to Coding Standards |
| **Spec-Kit Task** | TASK-SEC-013 |

**Localisation** : `specs/003-stack-technique/research.md:96-101`

**Description** :

La correction SEC-010 a ajouté la garde `jitterFactor ∈ [0.0, 0.25]` dans l'implémentation de référence de `quickstart.md`. Cependant, l'implémentation dans `research.md:D4` n'a pas été mise à jour :

```java
// research.md:D4 — ANCIENNE implémentation (sans garde SEC-010)
public static Duration withJitter(Duration base, double jitterFactor) {
    double factor = 1.0 + (ThreadLocalRandom.current().nextDouble(-jitterFactor, jitterFactor));
    return Duration.ofMillis((long) (base.toMillis() * factor));
}
```

Un développeur copiant l'implémentation depuis `research.md` (souvent la première source consultée) obtiendra une version sans validation des bornes. La divergence entre les deux sources de référence est une source de confusion et de régression.

**Correction** : Mettre à jour `research.md:D4` avec la version complète (incluant les gardes `null`, durée négative, et `jitterFactor`).

---

### SEC-014 — `spring.profiles.active: local` hardcodé

| Attribut | Valeur |
|----------|--------|
| **Finding ID** | SEC-014 |
| **Sévérité** | INFORMATIONAL |
| **CVSS Score** | 0.0 |
| **OWASP** | A05:2025 — Security Misconfiguration |
| **CWE** | CWE-16: Configuration |

**Localisation** : `backend/docai-bootstrap/src/main/resources/application.yml:5`

**Description** :

```yaml
spring:
  profiles:
    active: local   # ← hardcodé
```

En CI/CD, un pipeline qui ne surcharge pas `SPRING_PROFILES_ACTIVE` activera silencieusement le profil `local` (avec des valeurs de dev). La pratique recommandée Spring Boot est d'utiliser `${SPRING_PROFILES_ACTIVE:local}` pour permettre la surcharge sans ambiguïté.

**Correction** :
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
```

---

### SEC-015 — Comportement `show-details: when_authorized` sur port management non documenté

| Attribut | Valeur |
|----------|--------|
| **Finding ID** | SEC-015 |
| **Sévérité** | INFORMATIONAL |
| **CVSS Score** | 0.0 |
| **OWASP** | A05:2025 — Security Misconfiguration |
| **CWE** | CWE-16: Configuration |

**Localisation** : `backend/docai-bootstrap/src/main/resources/application.yml:71-72`

**Description** :

Lorsque `management.server.port` est différent de `server.port`, Spring Boot crée un serveur embarqué séparé pour les endpoints management. Ce serveur secondaire **n'est pas protégé par Spring Security par défaut** (Spring Security ne s'applique qu'au contexte web principal).

La configuration actuelle :
```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
```

Sur le port 9091, sans SecurityFilterChain explicite pour le management context, `when_authorized` s'évalue en l'absence de contexte de sécurité. Le comportement réel Spring Boot 4.x dans ce cas est : affichage des détails uniquement si un `SecurityContext` est présent — ce qui n'est jamais le cas sur le management port non protégé. Résultat effectif : `show-details: never` sur le port 9091.

Ce comportement est correct du point de vue sécurité (plus restrictif que souhaité), mais devrait être documenté pour éviter la confusion lors du debugging.

---

## Patterns sécurisés confirmés (inchangés depuis v1)

Ces patterns ont été correctement introduits par les corrections et restent conformes :

| Pattern | Localisation | Statut |
|---------|-------------|--------|
| Apicurio `auto-register: false` par défaut | `application.yml:40` | ✅ Secure by default |
| MongoDB URI externalisée via `${MONGODB_URI}` | `application.yml:10` | ✅ No hardcoded credentials |
| `auto-index-creation: false` | `application.yml:11` | ✅ Constitution compliant |
| Valkey SSL + password via env vars | `application.yml:15-18` | ✅ Production-ready template |
| Consumer Group IDs dans `application.yml` uniquement | `application.yml:96-109` | ✅ ADR-002 compliant |
| Resilience4j config externalisée | `application.yml:111-217` | ✅ No hardcoded thresholds |
| `jwks-cache-ttl: 1h` | `application.yml:50` | ✅ ADR-006 compliant |
| LLM CB `waitDurationInOpenState: 60s` | `application.yml:118` | ✅ SEC-008 fixed |
| JitterTtl guard `[0.0, 0.25]` | `quickstart.md:157-158` | ✅ SEC-010 fixed |
| Actuator port management séparé (9091) | `application.yml:64` | ✅ SEC-002 architecture correcte |

---

## Plan de remédiation (avant `/speckit-tasks`)

### Priorité 1 — Immédiat (15 minutes)

**SEC-011 — Correction Prometheus** :

```yaml
# application.yml et contracts/application-yml.md BLOC 6
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # ajouter metrics,prometheus
```

```yaml
# prometheus.yml
- job_name: 'spring-boot'
  metrics_path: '/actuator/prometheus'   # sans /api (management port n'hérite pas du context-path)
  static_configs:
    - targets: ['localhost:9091']   # ← port 9091 (management)
```

### Priorité 2 — Immédiat (15 minutes)

**SEC-012 — Mise à jour `.env.example`** :

```bash
# Remplacer dans .env.example :
REDIS_HOST → VALKEY_HOST
REDIS_PORT → VALKEY_PORT
REDIS_PASSWORD → VALKEY_PASSWORD
KAFKA_BROKERS → KAFKA_BOOTSTRAP_SERVERS
KAFKA_SCHEMA_REGISTRY → APICURIO_URL
Supprimer KAFKA_CONSUMER_GROUP

# Ajouter :
VALKEY_SSL_ENABLED=false
APICURIO_AUTO_REGISTER=true   # true en local dev, false en production
MANAGEMENT_PORT=9091
KEYCLOAK_URL=http://localhost:8180
OTLP_ENDPOINT=http://localhost:4317
```

### Priorité 3 — Lors implémentation (5 minutes)

**SEC-013** — Mettre à jour `research.md:D4` avec l'implémentation complète incluant les gardes.

**SEC-014** — Externaliser `spring.profiles.active` dans `application.yml`.

**SEC-015** — Ajouter une note documentaire dans `contracts/application-yml.md` BLOC 6 sur le comportement `when_authorized` sur management port.

---

## Tableau de risque résiduel

| Finding | Sévérité | Impact si non corrigé |
|---------|----------|----------------------|
| SEC-011 | MEDIUM | Prometheus aveugle — pas d'alertes métriques en production |
| SEC-012 | MEDIUM | Misconfiguration silencieuse dans nouveaux environnements |
| SEC-013 | LOW | Implémentation JitterTtl sans garde si copiée depuis research.md |
| SEC-014 | INFO | Profile `local` en CI/CD si variable non définie |
| SEC-015 | INFO | `show-details: when_authorized` effectivement `never` sur port 9091 |

**Conclusion** : Le risque est **MODERATE**. Les 2 findings MEDIUM (SEC-011, SEC-012) sont des corrections de configuration rapides (~30 min). Aucun finding ne bloque la génération des tâches `/speckit-tasks`, mais SEC-011 doit être résolu avant le démarrage de l'application.
