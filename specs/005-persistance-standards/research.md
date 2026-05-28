# Research: Module C — Persistance & Standards

**Branch**: `005-persistance-standards` | **Date**: 2026-05-28

## Decision 1 — Logback JSON Encoder

**Decision**: `logstash-logback-encoder` 8.x (`net.logstash.logback`)

**Rationale**: Standard de facto pour JSON structuré avec Spring Boot. Produit des logs compatibles Grafana Loki / ELK out-of-the-box. `LogstashEncoder` supporte nativement les champs MDC (`traceId`, `tenantId`) et les custom converters pour le masquage PII.

**Alternatives considered**:
- `jackson-databind` custom layout → trop verbeux, pas de support MDC automatique
- `log4j2` JSON layout → conflit de version avec Spring Boot 4 (`log4j2-to-slf4j` bridge requis, complexité inutile)

**Dépendance Maven**:
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

---

## Decision 2 — Injection MDC traceId

**Decision**: OpenTelemetry MDC bridge automatique via `io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0` + `OncePerRequestFilter` pour `tenantId`

**Rationale**: Spring Boot 4 + OpenTelemetry auto-instrumentation injecte automatiquement `traceId` et `spanId` dans le MDC SLF4J à chaque requête (`management.tracing.sampling.probability: 1.0` déjà configuré). Le `tenantId` est extrait du JWT Keycloak (`tenant_id` claim) via un filtre dédié `TenantMdcFilter`.

**Alternatives considered**:
- Filter manuel pour `traceId` → inutile, OTel le fait automatiquement
- `spring-cloud-sleuth` → obsolète depuis Spring Boot 3 (remplacé par Micrometer Tracing)

---

## Decision 3 — Masquage PII

**Decision**: Custom Logback `PatternLayoutEncoder` avec `PiiMaskingConverter` interceptant les messages avant écriture

**Rationale**: Le masquage PII doit être récursif et appliqué AVANT écriture (FR-OBS-007). L'approche la plus fiable est un converter Logback enregistré dans `logback-spring.xml` qui s'applique à tout le message structuré, y compris les objets imbriqués via JSON path.

**Patterns de masquage**:
```
Email   : [\w.+-]+@[\w-]+\.[\w.]+     → [PII_MASKED]
SIRET   : \b\d{14}\b                  → [PARTIAL_MASK] (SIREN visible)
IBAN    : [A-Z]{2}\d{2}[\w\d]{11,30} → [PII_MASKED]
Tél. FR : (0|\+33)[1-9](\d{8})       → [PII_MASKED]
```

**Alternatives considered**:
- `Aspect` Spring AOP sur les méthodes `log.*()` → fragilité, ne couvre pas les logs dans les bibliothèques
- `MDC.put("email", "[PII_MASKED]")` au niveau applicatif → disciplinaire, pas structurel

---

## Decision 4 — Préfixe /v1/ (Global vs Per-Controller)

**Decision**: `WebMvcConfigurer.configurePathMatch()` avec `addPathPrefix("/v1", HandlerTypePredicate.forBasePackage("fr.docai.adapter.in.rest"))`

**Rationale**: `server.servlet.context-path: /api` est déjà en place. `configurePathMatch` cible uniquement les controllers MVC du package REST, laissant Actuator (port 9091 séparé), Swagger et les filtres Spring Security intacts. Conformité **structurelle** : un oubli de `@RequestMapping("/v1")` est impossible puisqu'aucun controller n'en a besoin.

**URLs résultantes**: `http://host:8080/api/v1/{resource}`

**Alternatives considered**:
- `server.servlet.context-path: /api/v1` → affecterait Actuator, Swagger, filtres Security
- `@RequestMapping("/v1")` sur chaque controller → disciplinaire, risque d'oubli (non structurel)
- `spring.webmvc.servlet.path=/v1` → affecte le DispatcherServlet entier, conflits avec `/api`

---

## Decision 5 — Nouveau module docai-commons

**Decision**: Nouveau module Maven `docai-commons` ajouté dans `backend/pom.xml`

**Rationale**: Aucun module commons n'existe dans le parent POM actuel (11 modules listés). La règle BR-PAG-008 impose que la pagination soit implémentée une seule fois. `docai-commons` sera dépendance de `docai-adapter-in-rest` et de tout module exposant une API paginée.

**Position dans le build order**: Dépend uniquement de Spring Web (`spring-boot-starter-web`). Compilé avant `docai-adapter-in-rest` et `docai-application`.

**Alternatives considered**:
- Pagination dans `docai-application` → viole l'intent hexagonal (application ≠ contrat API)
- Pagination dans `docai-adapter-in-rest` → non partageable par d'autres adaptateurs REST futurs
- Ne pas créer de module → impossible, BR-PAG-008 exige une source unique partageable

---

## Decision 6 — Scope de Mongock V001

**Decision**: V001 crée uniquement `documents` + `document_summary_views`. Les 13 autres collections (Annex B) sont créées par les migrations V002–V015 dans leurs modules fonctionnels respectifs (Parties 3 et 4).

**Rationale**: `documents` est transversale (utilisée par tous les modules). `document_summary_views` est requise dès Partie 1 pour le schéma `lastSyncedAt` (ADR-011). Les autres collections appartiennent à leurs bounded contexts et seront créées avec leur module.

**EXPLAIN PLAN attendu pour V001**:
```javascript
db.documents.find({ tenantId: "acme", status: "PENDING" })
            .sort({ createdAt: -1 })
            .explain("executionStats")
// winningPlan.stage : IXSCAN ✅  (index {tenantId:1, status:1, createdAt:-1})
// winningPlan.stage : COLLSCAN ❌ (blocage ADR-010)
```

**Alternatives considered**:
- Créer toutes les 15 collections en V001 → couplage fort avec modules non encore développés
- Créer uniquement `documents` → viole ADR-011 (`lastSyncedAt` requis dès Partie 1)
