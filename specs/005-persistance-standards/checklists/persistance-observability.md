# Implementation Standards Checklist: Module C — Persistance & Observability

**Purpose**: Validate requirements completeness, clarity, and consistency for Module C implementation standards (docai-persistance-standards + docai-observability)  
**Created**: 2026-05-28  
**Feature**: [plan.md](../plan.md) | [spec.md](../spec.md) | [data-model.md](../data-model.md) | [contracts/pagination-api.md](../contracts/pagination-api.md)  
**Audience**: PR Reviewer + Developer  
**ADR scope**: ADR-010 · ADR-011 · Annex B  

---

## Observabilité — Complétude des exigences

- [ ] CHK001 — Les exigences de logging JSON structuré sont-elles spécifiées avec le comportement conditionnel par profil (local = texte lisible, staging/prod = JSON) et la configuration LogstashEncoder ? [Completeness, plan.md §ÉTAPE 1]
- [ ] CHK002 — L'injection MDC est-elle documentée pour les deux champs obligatoires — traceId (auto-injecté via OTel bridge) et tenantId (via TenantMdcFilter depuis le claim JWT `tenant_id`) — avec le module de placement retenu ? [Clarity, plan.md §ÉTAPE 1]
- [ ] CHK003 — Le comportement de fallback de TenantMdcFilter est-il spécifié de façon non-ambiguë : valeur `"UNAUTHENTICATED"` sur les requêtes pré-auth (OPTIONS, health, tokens invalides), jamais `null` (SEC-002) ? [Completeness, plan.md §ÉTAPE 1]
- [ ] CHK004 — Les 4 patterns de masquage PII sont-ils documentés avec leur regex exacte, le format de sortie différencié ([PII_MASKED] pour email/IBAN/téléphone, [PARTIAL_MASK] pour SIRET), et leur application récursive sur les objets imbriqués ? [Completeness, research.md §Decision 3]
- [ ] CHK005 — La PII Logging Convention (SEC-001) est-elle spécifiée comme règle **structurelle** (interdiction de `StructuredArguments.kv()` pour données PII, usage obligatoire de `PiiLogger.safeKv()`) plutôt que comme discipline ? [Clarity, plan.md §ÉTAPE 1]
- [ ] CHK006 — L'exigence `@ToString.Exclude` sur tous les champs PII des entités est-elle documentée avec un mécanisme d'enforcement (ArchUnit rule ou Checkstyle) au lieu d'une convention pure ? [Completeness, Gap]
- [ ] CHK007 — Les niveaux de log par environnement sont-ils spécifiés de façon testable : DEBUG activé uniquement en local (FR-OBS-004), INFO minimum en staging/prod ? [Clarity, plan.md §ÉTAPE 1]
- [ ] CHK008 — Les 14 métriques Micrometer attendues sont-elles documentées avec leur nom exact, leurs labels/tags, et l'endpoint Prometheus cible (`/actuator/prometheus`) ? [Completeness, Gap]
- [ ] CHK009 — Les 6 seuils d'alerte Grafana sont-ils quantifiés avec leurs conditions de déclenchement (error rate > 1%, CB OPEN, Kafka lag > 1000, Valkey hit < 30%, DLQ > 10, P99 > 500ms), leur sévérité, et leur canal de notification ? [Completeness, Clarity, Gap]
- [ ] CHK010 — Les tests de validation pour logback (JSON valide en profil staging, absence de DEBUG, champs traceId/tenantId présents) sont-ils définis avec leurs assertions mesurables ? [Completeness, plan.md §ÉTAPE 1]

---

## MongoDB — Conventions & ADR-010

- [ ] CHK011 — Annex B liste-t-elle exhaustivement les 15 collections attendues avec leur nom exact en snake_case pluriel, couvrant tous les bounded contexts des modules futurs (V002–V015) ? [Completeness, Annex B]
- [ ] CHK012 — Les conventions de nommage des champs (camelCase, suffixe `*At` pour les dates) sont-elles illustrées par des exemples pour chaque type de champ présent dans le data model actuel ? [Clarity, data-model.md]
- [ ] CHK013 — ADR-010 spécifie-t-il la règle tenantId-en-premier avec des exemples couvrant tous les patterns d'index (composé, unique, partiel), et est-elle référencée de façon cohérente dans plan.md ET data-model.md ? [Consistency, plan.md §ÉTAPE 2, data-model.md]
- [ ] CHK014 — L'exigence EXPLAIN PLAN (winningPlan.stage = IXSCAN) est-elle non-ambiguë — avec la commande exacte à exécuter, le responsable (développeur/CI), la capture de l'output, et le caractère bloquant pour la PR ? [Clarity, research.md §Decision 6]
- [ ] CHK015 — L'exigence BR-MIG-003 (`auto-index-creation: false`) est-elle traçable jusqu'à la ligne de configuration spécifique dans application.yml, avec protection contre toute auto-configuration Spring/Mongock qui pourrait la surcharger ? [Traceability, plan.md §ÉTAPE 2]
- [ ] CHK016 — L'index partiel (sélectivité < 20% sur documents actifs) est-il documenté avec une règle de décision explicite (critère quantifié + méthode de mesure) applicable à toutes les collections présentes et futures ? [Completeness, Gap]
- [ ] CHK017 — ADR-011 spécifie-t-il clairement le cycle de vie complet de `lastSyncedAt` — null jusqu'en Partie 5, quel composant le renseigne (`DashboardProjectionConsumer`), et le SLA de réconciliation (lag > 30s → alerte) ? [Completeness, data-model.md §document_summary_views]

---

## Mongock — Business Rules BR-MIG

- [ ] CHK018 — BR-MIG-001 (1 migration = 1 classe `@ChangeUnit`) est-il assorti d'un mécanisme d'enforcement automatique (règle ArchUnit, Checkstyle) ou repose-t-il uniquement sur la discipline individuelle ? [Clarity, plan.md §ÉTAPE 2]
- [ ] CHK019 — BR-MIG-002 (backward-compatible) définit-il explicitement ce qu'est une migration compatible dans MongoDB schema-less — incluant le pattern expand/contract pour les renommages et la durée minimale de cohabitation ? [Clarity, Gap]
- [ ] CHK020 — BR-MIG-003 (pas d'`@Indexed` dans le code) est-il assorti d'une règle ArchUnit interdisant `@Indexed` sur toutes les classes `@Document`, ou est-ce une convention advisory sans enforcement ? [Completeness, Annex B]
- [ ] CHK021 — BR-MIG-004 (`@RollbackExecution` obligatoire) est-il spécifié avec le guard SEC-004 (`estimatedDocumentCount() == 0` avant tout `drop()`), et les conditions de rollback sûr (environnement frais uniquement) sont-elles documentées ? [Completeness, plan.md §ÉTAPE 2]
- [ ] CHK022 — BR-MIG-005 (DDL uniquement) définit-il explicitement ce qui constitue une "logique métier" interdite dans une migration (exemples : transformations de données, appels API, règles domaine) ? [Clarity, Gap]
- [ ] CHK023 — BR-MIG-006 (testée en staging avant prod) est-il lié à un gate CI/CD automatisé garantissant l'ordonnancement, ou est-ce une exigence de processus sans enforcement technique ? [Completeness, Gap]
- [ ] CHK024 — BR-MIG-007 (startup bloqué si migration échouée) est-il spécifié avec la propriété de configuration Mongock qui active ce comportement, et est-il couvert par un test d'intégration (`MongockStartupIT`) ? [Clarity, plan.md §ÉTAPE 2]
- [ ] CHK025 — V001 est-elle entièrement spécifiée avec : `@ChangeUnit(id, order, author)`, les 2 collections créées, tous les index (tenantId-first), le guard rollback, et les 5 tests d'intégration attendus ? [Completeness, plan.md §ÉTAPE 2]
- [ ] CHK026 — La convention de nommage `V{NNN}_{module}_{description}` est-elle définie avec des contraintes de longueur maximale, les caractères autorisés, et un mécanisme d'enforcement (Checkstyle ou validator custom) ? [Clarity, Gap]

---

## Pagination — Business Rules BR-PAG

- [ ] CHK027 — Les paramètres de pagination (page, size, sort) sont-ils définis comme optionnels avec valeurs par défaut dans le contrat, et le spec définit-il ce qui constitue un "endpoint liste" auquel ce standard s'applique ? [Clarity, contracts/pagination-api.md]
- [ ] CHK028 — BR-PAG-002 est-il lié à un format de réponse HTTP 400 exact dans le contrat de pagination, incluant le message d'erreur littéral ("Page size must not exceed 100 elements") ? [Completeness, contracts/pagination-api.md §Erreur]
- [ ] CHK029 — Les constantes DEFAULT_SIZE = 20 et DEFAULT_SORT = "createdAt,desc" sont-elles spécifiées dans `docai-commons` de façon à ce que tout module consommateur les hérite sans duplication (BR-PAG-008) ? [Traceability, plan.md §ÉTAPE 3]
- [ ] CHK030 — Les champs `totalElements` et `totalPages` de `PageInfo` sont-ils définis comme **obligatoires** dans le contrat (jamais null), avec la formule de calcul explicite (`totalPages = ceil(totalElements / size)`) ? [Completeness, data-model.md §ApiResponse]
- [ ] CHK031 — La règle BR-PAG-008 (pagination dans commons uniquement, jamais réimplémentée) est-elle appliquée via `PaginationParamsHandlerMethodArgumentResolver` — enforcement structurel qui rend toute omission de `@Valid` impossible ? [Completeness, plan.md §ÉTAPE 3 SEC-003]
- [ ] CHK032 — Le champ `data` de `ApiResponse` est-il explicitement spécifié comme **tableau non-null** (liste vide si aucun résultat), avec le comportement de sérialisation Jackson pour les records Java 21 ? [Clarity, contracts/pagination-api.md]
- [ ] CHK033 — BR-PAG-006 (tri par défaut createdAt,desc) s'applique-t-il uniformément à tous les endpoints liste, ou des surcharges par domaine (ex: tri différent pour `document_summary_views`) sont-elles documentées ? [Completeness, Gap]
- [ ] CHK034 — BR-PAG-007 (champs de tri documentés dans OpenAPI) est-il spécifié avec le format d'annotation retenu (ex: `@Parameter description`) et la responsabilité de chaque controller consommateur ? [Clarity, Gap]

---

## Versioning API

- [ ] CHK035 — L'enforcement structurel du préfixe `/v1/` (via `WebMvcConfigurer.configurePathMatch` ciblant `fr.docai.adapter.in.rest`) est-il documenté avec les exclusions explicites (Actuator sur port 9091, Swagger, filtres Security) ? [Completeness, plan.md §ÉTAPE 4]
- [ ] CHK036 — La définition de "breaking change" est-elle documentée exhaustivement — listant quelles modifications déclenchent une montée en /v2/ (ex: suppression de champ, changement de type, nouveau champ obligatoire) ? [Clarity, contracts/pagination-api.md §Versioning]
- [ ] CHK037 — La période de cohabitation minimale /v1/ après lancement de /v2/ (≥ 6 mois) est-elle traçable via un mécanisme concret (sunset date tracking, alerte automatique) ou reste-t-elle une politique documentaire ? [Completeness, Gap]
- [ ] CHK038 — Les headers de dépréciation (Deprecation, Sunset, Link) sont-ils spécifiés avec leur format exact, la condition de déclenchement, et le composant responsable de leur injection (filtre, intercepteur Spring) ? [Completeness, contracts/pagination-api.md]
- [ ] CHK039 — L'exigence HTTP 404 pour les requêtes sans préfixe `/v1/` (ex: `GET /api/documents`) est-elle spécifiée comme comportement attendu dans les tests de versioning (`ApiVersioningConfigTest#withoutPrefixReturns404`) ? [Clarity, plan.md §ÉTAPE 4]
- [ ] CHK040 — Le comportement du trailing slash (Spring MVC 6.1+: non-match par défaut — SEC-006) est-il documenté pour prévenir des différences de comportement entre versions de Spring Boot ? [Completeness, plan.md §ÉTAPE 4]
- [ ] CHK041 — L'exigence d'auto-génération du spec OpenAPI en CI est-elle spécifiée avec le job CI concerné, le chemin de l'artefact produit, et si le spec est validé (schema check) ou publié comme release artifact ? [Completeness, Gap]

---

## Cohérence & Dépendances inter-modules

- [ ] CHK042 — La PII Logging Convention (SEC-001) est-elle planifiée pour être ajoutée à la Constitution (section VI) **avant** que les modules métier (Parties 3–4) soient implémentés, conformément à la note du plan ? [Traceability, plan.md §ÉTAPE 1]
- [ ] CHK043 — L'ordre de build du module `docai-commons` (compilé avant `docai-adapter-in-rest`) est-il spécifié dans le parent POM et dans les dépendances Maven documentées ? [Completeness, plan.md §ÉTAPE 3]
- [ ] CHK044 — Les tests d'intégration spécifiés utilisent-ils tous `TestContainers.withReuse(true)` conformément à ADR-008, avec mention explicite dans chaque clause de test du plan ? [Consistency, plan.md §ÉTAPE 2]
- [ ] CHK045 — La règle BR-PAG-008 (zéro duplication pagination) est-elle définie comme critère **bloquant en PR review**, avec un mécanisme qui permet à un reviewer de détecter une violation sans devoir inspecter manuellement chaque module ? [Completeness, plan.md §Constitution Check]

---

## Notes

- Items marqués `[Gap]` : exigences absentes des artefacts actuels (plan.md, research.md, data-model.md, contracts/) — à ajouter avant implémentation.
- CHK008, CHK009 (14 métriques / 6 alertes) : non couverts par les 4 étapes du Module C actuel — appartiennent aux modules d'observabilité futurs ; ces items sont inclus comme référence de standard.
- CHK016 (partial index) : critère de sélectivité < 20% documenté dans ADR-010 mais sans règle de décision quantifiée dans les artefacts disponibles.
- Marquer complété avec `[x]` ; ajouter commentaires inline si clarification nécessaire.
