# Feature Specification: Module C — Persistance & Standards (Référence Transversale)

**Feature Branch**: `005-persistance-standards`  
**Created**: 2026-05-28  
**Status**: Draft  
**Input**: User description: "Module C — Persistance & Standards (Référence Transversale)"

---

## Overview

Module C définit les standards transversaux qui s'appliquent à **tous** les modules de la plateforme DocAI. Ces standards couvrent trois blocs interdépendants : l'observabilité (logs, métriques, alertes), les conventions MongoDB (collections, indexation, migrations), et les contrats API (pagination, versioning). Tout module soumettant un PR doit respecter ces règles sans exception.

---

## Clarifications

### Session 2026-05-28

- Q: La pagination est-elle implémentée dans docai-commons une seule fois et réutilisée dans tous les modules (BR-PAG-008) ? → A: Oui — aucun module ne doit réimplémenter sa propre logique de pagination ; toute duplication constitue un blocage en PR review.
- Q: ADR-010 : l'EXPLAIN PLAN est-il vérifié automatiquement en CI ou manuellement en PR review ? → A: Vérification manuelle via checklist PR (T012) — aucune validation CI automatisée pour cette version.
- Q: Les migrations Mongock sont-elles toutes backward-compatible (BR-MIG-002) ? → A: Oui — la suppression d'un champ est interdite en une seule migration ; toute suppression requiert une approche multi-étapes (dépréciation du champ → période de transition → migration de suppression).
- Q: Le champ lastSyncedAt (ADR-011) est-il présent dans document_summary_views dès la Partie 1 ou uniquement en Partie 5 (Dashboard) ? → A: Le champ est défini dans le schéma dès la Partie 1 (créé par migration Mongock) ; sa valeur est renseignée uniquement en Partie 5 lors de l'implémentation du mécanisme de synchronisation.
- Q: Le versioning /v1/ est-il configuré globalement dans Spring ou via annotation per-controller ? → A: Configuration globale automatique — le préfixe `/v1/` est appliqué par une configuration centrale au niveau application ; aucun contrôleur ne doit porter l'annotation individuellement, la conformité est structurelle.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Observabilité unifiée : logs corrélés et métriques opérationnelles (Priority: P1)

Un ingénieur de garde reçoit une alerte en production. Il ouvre Grafana, filtre par `tenantId` et `traceId` et retrouve l'ensemble de la chaîne d'appels en moins de 2 minutes. Les logs sont en JSON structuré, le PII du client est masqué, et les 14 métriques Micrometer permettent d'identifier le goulot d'étranglement sans avoir à redéployer quoi que ce soit.

**Why this priority**: Sans observabilité homogène, chaque incident nécessite des investigations ad hoc dans des logs hétérogènes. C'est le fondement opérationnel de toute la plateforme.

**Independent Test**: Déployer un seul service (ex. `docai-adapter-in-rest`) configuré selon le standard, déclencher une requête portant des données PII, vérifier que le log produit est en JSON, contient `traceId` + `tenantId`, masque les données sensibles et qu'une métrique Micrometer est incrémentée.

**Acceptance Scenarios**:

1. **Given** un service en staging, **When** une requête HTTP est traitée, **Then** chaque ligne de log produite contient les champs `traceId`, `tenantId`, `level`, `timestamp` en format JSON (aucun texte brut).
2. **Given** un log contenant une donnée PII, **When** le log est écrit, **Then** email, IBAN et numéro de téléphone sont remplacés par `[PII_MASKED]` ; SIRET est remplacé par `[PARTIAL_MASK]`.
3. **Given** un niveau DEBUG configuré, **When** l'application tourne en staging ou prod, **Then** aucun log DEBUG n'est émis (DEBUG réservé au dev local uniquement).
4. **Given** les 14 métriques Micrometer définies, **When** le service démarre, **Then** toutes les métriques sont exposées sur l'endpoint d'observabilité et visibles dans Grafana.
5. **Given** les 6 alertes Grafana configurées, **When** un seuil est franchi, **Then** une notification est déclenchée dans les 60 secondes.

---

### User Story 2 — MongoDB : conventions, indexation sûre et migrations sans interruption (Priority: P1)

Un développeur ajoute une nouvelle collection dans le module Fraud. Il suit les conventions de nommage (`snake_case` pluriel), place `tenantId` en premier dans chaque index, lance l'`EXPLAIN PLAN` sur sa requête critique et soumet son PR avec la migration Mongock. La revue de code valide que `winningPlan.stage = IXSCAN` et que `auto-index-creation` reste désactivé en production.

**Why this priority**: Un index absent ou mal conçu sur MongoDB peut provoquer des `COLLSCAN` à l'échelle multi-tenant, rendant toute la plateforme indisponible. Les migrations non contrôlées risquent la perte de données en production.

**Independent Test**: Créer une collection de test selon le standard, y insérer des documents multi-tenant, exécuter une requête filtrée par `tenantId`, vérifier via `EXPLAIN PLAN` que le plan retenu est `IXSCAN` (jamais `COLLSCAN`). Puis valider qu'une migration Mongock respectant la convention de nommage s'applique sans erreur.

**Acceptance Scenarios**:

1. **Given** une nouvelle collection créée, **When** son nom est vérifié, **Then** il est en `snake_case` pluriel (ex. `fraud_analyses`, `document_summaries`).
2. **Given** un index composite sur une collection multi-tenant, **When** l'ordre des champs est vérifié, **Then** `tenantId` est toujours le premier champ de l'index (ADR-010).
3. **Given** une requête sur une collection, **When** l'`EXPLAIN PLAN` est exécuté, **Then** `winningPlan.stage` est `IXSCAN` (jamais `COLLSCAN`).
4. **Given** un environnement de production, **When** la configuration MongoDB est inspectée, **Then** `auto-index-creation` est `false`.
5. **Given** une migration Mongock, **When** son nom est vérifié, **Then** il respecte la convention `V{NNN}_{module}_{description}` (ex. `V001_fraud_create_indexes`).
6. **Given** les règles BR-MIG-001 à BR-MIG-007, **When** une migration est soumise en PR, **Then** toutes les règles sont satisfaites (idempotente, rollback documenté, durée < 30 s sur jeu de données représentatif).
7. **Given** le Read Model `document_summary_views`, **When** sa structure est vérifiée, **Then** le champ `lastSyncedAt` est présent (ADR-011).
8. **Given** une collection où moins de 20 % des documents sont actifs, **When** l'indexation est définie, **Then** un partial index est utilisé (ADR-010).

---

### User Story 3 — Pagination et versioning API : contrats stables pour les consommateurs (Priority: P2)

Un client API DocAI interroge la liste des documents de son tenant. Il reçoit une réponse paginée structurée (`data`, `page.number`, `page.size`, `page.totalElements`, `page.totalPages`). Il sait que demander plus de 100 éléments par page retourne HTTP 400. Lorsque DocAI publie un changement structurel, il le fait sous `/v2/` en maintenant `/v1/` pendant au moins 6 mois.

**Why this priority**: Une pagination incohérente ou l'absence de versioning force les consommateurs API à adapter leur code à chaque changement, brisant la confiance dans la plateforme.

**Independent Test**: Appeler un endpoint liste en spécifiant `pageSize=100`, vérifier la structure de réponse. Puis appeler avec `pageSize=101`, vérifier HTTP 400. Vérifier que l'URL commence par `/v1/`.

**Acceptance Scenarios**:

1. **Given** un endpoint liste quelconque, **When** une requête est effectuée sans paramètre de pagination, **Then** la réponse contient `data` (tableau) et `page` (objet avec `number`, `size`, `totalElements`, `totalPages`).
2. **Given** un endpoint liste, **When** `pageSize=101` est demandé, **Then** HTTP 400 est retourné avec un message d'erreur explicite.
3. **Given** un endpoint liste, **When** `pageSize=100` est demandé, **Then** la réponse est HTTP 200 avec au plus 100 éléments.
4. **Given** tous les endpoints de la plateforme, **When** leurs URLs sont vérifiées, **Then** elles commencent toutes par `/v1/` (BR-PAG-001 et versioning standard).
5. **Given** un changement structurel (breaking change), **When** il est publié, **Then** il l'est sous `/v2/` et `/v1/` reste opérationnel pendant au moins 6 mois.
6. **Given** la logique de pagination, **When** le code est audité, **Then** elle est implémentée une seule fois dans `docai-commons` (BR-PAG-008 — aucune duplication).

---

### Edge Cases

- Que se passe-t-il si un log est produit avant que le `traceId` soit injecté (ex. au démarrage du service) ? → Le champ `traceId` doit être présent mais peut valoir `"STARTUP"` ou `"UNKNOWN"` ; jamais absent.
- Que se passe-t-il si une migration Mongock échoue à mi-chemin sur un dataset production ? → La règle BR-MIG-004 impose que toute migration soit idempotente et ré-exécutable sans effet de bord.
- Que se passe-t-il si un champ PII apparaît dans un objet imbriqué (ex. `address.email`) ? → Le masquage PII s'applique récursivement sur tous les niveaux de l'objet log.
- Que se passe-t-il si un consommateur API appelle `/v1/` après la date d'obsolescence de 6 mois ? → HTTP 410 Gone avec un corps indiquant l'URL de migration vers `/v2/`.
- Que se passe-t-il si un index partial est demandé mais la condition de sélectivité est incorrecte ? → Le PR est bloqué par la règle ADR-010 : l'`EXPLAIN PLAN` doit être fourni et valider `IXSCAN`.

---

## Requirements *(mandatory)*

### Functional Requirements — Bloc 1 : Observabilité

- **FR-OBS-001**: Tout service DOIT produire des logs au format JSON structuré en staging et production ; le texte brut est interdit.
- **FR-OBS-002**: Chaque ligne de log DOIT contenir les champs `traceId`, `tenantId`, `level`, `timestamp`, `service` et `message`. Mapping de provenance : le claim JWT `tenant_id` est injecté dans le MDC Logback sous la clé `tenantId` par `TenantMdcFilter` ; `traceId` est auto-injecté par le bridge OpenTelemetry MDC.
- **FR-OBS-003**: Tout champ contenant une donnée PII DOIT être masqué avant écriture selon la règle différenciée : email, IBAN et numéro de téléphone → `[PII_MASKED]` ; SIRET → `[PARTIAL_MASK]` (identifiant partiel conservé pour audit légal). Le masquage s'applique récursivement (voir FR-OBS-007).
- **FR-OBS-004**: Le niveau DEBUG DOIT être désactivé en staging et production ; seuls ERROR, WARN et INFO sont autorisés hors dev local.
- **FR-OBS-005**: Les 14 métriques Micrometer listées dans le document de référence DOIVENT être exposées par chaque service. *(Périmètre : module observabilité futur — post-Module C ; non implémenté dans les 4 étapes de ce module.)*
- **FR-OBS-006**: Les 6 alertes Grafana DOIVENT être configurées et déclencher une notification dans les 60 secondes suivant le franchissement d'un seuil. *(Périmètre : module observabilité futur — post-Module C ; non implémenté dans les 4 étapes de ce module.)*
- **FR-OBS-007**: Le masquage PII DOIT être récursif (objets imbriqués inclus).

### Functional Requirements — Bloc 2 : MongoDB Standards

- **FR-MDB-001**: Toutes les collections DOIVENT être nommées en `snake_case` pluriel (ex. `document_pages`, `fraud_analyses`).
- **FR-MDB-002**: Les 15 collections définies dans l'Annex B DOIVENT être créées via des migrations Mongock versionnées. V001 (Module C) crée les 2 premières collections (`documents` et `document_summary_views`) ; les 13 collections restantes sont créées par les migrations des modules ultérieurs (V002–V015).
- **FR-MDB-003**: Dans tout index composite, `tenantId` DOIT être le premier champ (ADR-010).
- **FR-MDB-004**: Un `EXPLAIN PLAN` DOIT être produit et joint à chaque PR modifiant une requête MongoDB ; `winningPlan.stage` DOIT valoir `IXSCAN`. La vérification est assurée par checklist manuelle en PR review (T012) — aucune validation CI automatisée pour cette version.
- **FR-MDB-005**: `auto-index-creation` DOIT être `false` en production et staging.
- **FR-MDB-006**: Toute migration DOIT respecter la convention `V{NNN}_{module}_{description}` et les règles BR-MIG-001 à BR-MIG-007 (idempotence, durée < 30 s, rollback documenté, etc.). Toute migration DOIT être backward-compatible (BR-MIG-002) : la suppression d'un champ est interdite en une seule migration — elle requiert une approche multi-étapes (dépréciation du champ → période de transition → migration de suppression).
- **FR-MDB-007**: Le champ `lastSyncedAt` DOIT être présent dans la collection `document_summary_views` (ADR-011). Sa structure est créée dès la Partie 1 via migration Mongock ; sa valeur est renseignée uniquement en Partie 5 lors de l'implémentation du mécanisme de synchronisation.
- **FR-MDB-008**: Lorsque moins de 20 % des documents d'une collection sont actifs, un partial index DOIT être utilisé à la place d'un index total (ADR-010).

### Functional Requirements — Bloc 3 : Pagination & Versioning API

- **FR-PAG-001**: Tout endpoint retournant une liste DOIT implémenter la pagination selon le standard BR-PAG-001 à BR-PAG-008.
- **FR-PAG-002**: La réponse paginée DOIT contenir `data` (tableau) et `page` (objet avec `number`, `size`, `totalElements`, `totalPages`).
- **FR-PAG-003**: Une demande de plus de 100 éléments par page DOIT retourner HTTP 400 avec un message d'erreur explicite.
- **FR-PAG-004**: La logique de pagination DOIT être implémentée une seule fois dans `docai-commons` et réutilisée par tous les modules (BR-PAG-008) ; toute réimplémentation dans un module consommateur est interdite et constitue un blocage en PR review.
- **FR-PAG-005**: Toutes les routes DOIVENT être préfixées par `/v1/`. Ce préfixe DOIT être appliqué via une configuration globale au niveau application (pas par annotation individuelle sur chaque contrôleur) afin de garantir une conformité structurelle et non disciplinaire.
- **FR-PAG-006**: Tout changement structurel (breaking change) DOIT être publié sous `/v2/` et `/v1/` DOIT rester opérationnel pendant au moins 6 mois.
- **FR-PAG-007**: Après la période d'obsolescence de `/v1/`, les appels DOIVENT recevoir HTTP 410 Gone avec indication de l'URL de migration.

### Key Entities

- **Log structuré**: Unité d'information produite par un service, en JSON, contenant traceId, tenantId, level, timestamp, service, message, et toute donnée de contexte après masquage PII.
- **Métrique Micrometer**: Compteur, gauge ou timer nommé et taggué par service et tenantId, exposé sur l'endpoint d'observabilité.
- **Alerte Grafana**: Règle de seuil définie sur une métrique, déclenchant une notification dans un délai garanti.
- **Collection MongoDB**: Ensemble de documents regroupés sous un nom `snake_case` pluriel, avec stratégie d'indexation `tenantId`-first et `EXPLAIN PLAN` validé.
- **Migration Mongock**: Script de migration de schéma versionné, idempotent, nommé selon `V{NNN}_{module}_{description}`, respectant BR-MIG-001 à 007.
- **Réponse paginée**: Structure standard `{ data: [...], page: { number, size, totalElements, totalPages } }` retournée par tout endpoint liste.
- **Version d'API**: Préfixe de route (`/v1/`, `/v2/`) identifiant un contrat API stable ; un breaking change nécessite un incrément de version avec cohabitation 6 mois.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % des logs émis en staging et production sont au format JSON structuré ; zéro ligne de texte brut détectée sur une période de 7 jours de monitoring.
- **SC-002**: 100 % des lignes de log contiennent les champs `traceId` et `tenantId` lors d'une requête tracée de bout en bout.
- **SC-003**: Zéro donnée PII (email, nom, SIRET, IBAN) apparaît en clair dans les logs sur une période d'audit de 30 jours.
- **SC-004**: Les 14 métriques Micrometer sont toutes visibles dans Grafana dès le démarrage de chaque service. *(Module observabilité futur — post-Module C.)*
- **SC-005**: Les 6 alertes Grafana se déclenchent dans les 60 secondes suivant la simulation d'un seuil critique. *(Module observabilité futur — post-Module C.)*
- **SC-006**: 100 % des requêtes MongoDB sur les collections de production passent par un `IXSCAN` (zéro `COLLSCAN` détecté via les logs de requêtes lentes sur 30 jours).
- **SC-007**: Toutes les migrations Mongock (V001 à V008) s'exécutent en moins de 30 secondes sur un jeu de données représentatif de 1 million de documents.
- **SC-008**: Zéro duplication de la logique de pagination détectée lors d'un audit de code — une seule implémentation dans `docai-commons`.
- **SC-009**: 100 % des endpoints liste respectent la structure de réponse paginée standard lors des tests d'intégration.
- **SC-010**: Une demande avec `pageSize > 100` retourne systématiquement HTTP 400 (validé par tests automatisés sur 100 % des endpoints liste).

---

## Assumptions

- Les standards de ce module s'appliquent à tous les modules présents et futurs de DocAI sans exception ; aucune dérogation n'est accordée sans ADR.
- La liste des 14 métriques Micrometer et des 6 alertes Grafana est définie dans le document `DOCAI_BACKEND_MASTER_SPECKIT_F.md` (Annex correspondante) et fait autorité.
- La liste des 15 collections MongoDB (Annex B) est fixée pour la version 1 ; de nouvelles collections nécessiteront une migration Mongock versionnée.
- Les règles BR-MIG-001 à BR-MIG-007 et BR-PAG-001 à BR-PAG-008 sont considérées comme stables et définies dans le document de référence.
- `docai-commons` est le module partagé qui héberge la logique de pagination réutilisable ; il est créé en Part 2 (Commons) avant tout module consommateur.
- La détection PII couvre : email, nom/prénom, SIRET, IBAN, numéro de téléphone. D'autres types de PII nécessiteraient un avenant.
- ADR-010 et ADR-011 sont ratifiés et non sujets à révision dans le cadre de ce module.
- La stratégie de dépréciation des versions API (`/v1/` → `/v2/`) suit un minimum de 6 mois de cohabitation ; le délai exact peut être ajusté par décision produit.
- Les tests d'intégration valident automatiquement les standards (pagination, format de log, indexation) via les profils CI existants.
