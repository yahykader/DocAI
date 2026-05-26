# Feature Specification: Stack Technique & Intégrations DocAI (Référence Transversale)

**Feature Branch**: `004-stack-technique`  
**Created**: 2026-05-25  
**Status**: Draft  
**Source**: MASTER SpecKit Partie 1 — Module 1.B  
**Skill cible**: `docai-stack-technique`

---

## Clarifications

### Session 2026-05-25

- Q: Les schémas Avro sont-ils générés via `maven-avro-plugin` ou écrits manuellement ? → A: `maven-avro-plugin` — auto-génération depuis fichiers `.avsc` dans `src/main/avro/` du module `docai-adapter-out-kafka`.
- Q: Les Consumer Group IDs sont-ils définis dans `application.yml` ou dans `@KafkaListener(groupId = "...")` ? → A: Toujours dans `application.yml` — JAMAIS hardcodés dans les annotations Java.
- Q: ADR-003 — les clés d'idempotence Kafka (`topic:partition:offset`) ont-elles un TTL fixe ou avec jitter ? → A: TTL fixe 24h obligatoire — la précision de déduplication interdit le jitter.
- Q: Resilience4j pour Tika/OpenCV — le timeout 15s est-il via `@TimeLimiter` ou via `Bulkhead` ? → A: `@TimeLimiter(name="tika", timeoutDuration=15s)` — en cas de timeout, le pipeline continue (fail-safe, BR-VIS-003).
- Q: Valkey — le TTL de la clé quota mensuel est-il fixe ? → A: Oui — TTL fixe = durée restante jusqu'au 1er du mois suivant (exception ADR-003, précision de reset requise).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Choix de la stack technique validée (Priority: P1)

En tant que développeur rejoignant l'équipe DocAI, j'ai besoin de connaître avec précision les technologies et versions retenues pour chaque couche du système afin d'éviter tout conflit de version, toute dette technique involontaire, et d'aligner mon environnement de développement dès le premier jour.

**Why this priority**: Sans une référence claire et opposable des versions exactes, chaque développeur risque d'introduire des variantes (ex. Spring Boot 3.x vs 4.x, Redis vs Valkey) qui cassent la cohérence de la plateforme et génèrent des bugs difficiles à tracer. C'est le socle de tout le reste.

**Independent Test**: Un développeur peut configurer son environnement uniquement à partir de ce document et builder le projet sans erreur de compatibilité.

**Acceptance Scenarios**:

1. **Given** un développeur lit la spécification de stack, **When** il configure son `pom.xml` parent, **Then** tous les modules compilent avec Java 21 LTS + Spring Boot 4.0.x sans avertissement de dépendance obsolète.
2. **Given** un développeur cherche à ajouter du cache, **When** il consulte ce document, **Then** il utilise Valkey 8.x (pas Redis) et comprend que le changement de licence Redis de mai 2024 justifie ce choix.
3. **Given** un développeur ajoute un traitement OCR, **When** il consulte ce document, **Then** il utilise Tess4J 5.x + PDFBox 3.x pour le texte et JavaCV 4.9.0 pour la vision (jamais `org.opencv` directement).
4. **Given** un développeur intègre un Schema Registry Kafka, **When** il consulte ce document, **Then** il utilise Apicurio Registry 2.6 (pas Confluent Schema Registry) en cohérence avec la licence open-source retenue.

---

### User Story 2 — Topologie Kafka correctement appliquée (Priority: P1)

En tant qu'architecte ou développeur implémentant un producteur ou consommateur Kafka, j'ai besoin de connaître les 8 topics du pipeline, les règles de clé de partition (ADR-002), et la convention des Consumer Groups, afin de garantir l'ordre de traitement par document et la traçabilité par tenant.

**Why this priority**: Une mauvaise clé de partition sur Kafka (ex. utilisation de `tenantId` sur les topics pipeline au lieu de `documentId`) casse l'ordre des événements par document et génère des conditions de course entre étapes de traitement — un bug critique difficile à corriger en production.

**Independent Test**: Un développeur peut implémenter un producteur et un consommateur Kafka corrects pour n'importe lequel des 8 topics en utilisant uniquement ce document, sans demander à l'architecte.

**Acceptance Scenarios**:

1. **Given** un développeur implémente un producteur sur `docai.doc.uploaded`, **When** il envoie un message, **Then** la clé de partition utilisée est `documentId` (ADR-002).
2. **Given** un développeur implémente le topic `docai.doc.failed`, **When** il envoie un message d'échec, **Then** la clé de partition est `tenantId` (exception documentée à ADR-002).
3. **Given** un développeur crée un Consumer Group, **When** il le nomme et le configure, **Then** il respecte la convention `docai.{module}.{name}.group` définie dans `application.yml` (jamais en attribut `groupId` de `@KafkaListener`).
4. **Given** une revue d'architecture vérifie la topologie Kafka, **When** elle compare avec ce document, **Then** les 8 topics sont présents avec les bonnes clés de partition et conventions de nommage.

---

### User Story 3 — Stratégies de cache Valkey sans Thunder Herd (Priority: P2)

En tant que développeur implémentant une couche de cache pour les appels externes (INSEE, BAN, RPPS, LLM...), j'ai besoin de connaître les 9 stratégies de cache définies et la règle de jitter obligatoire (ADR-003), afin d'éviter l'effet Thunder Herd lors d'expiration simultanée de milliers d'entrées de cache.

**Why this priority**: Un TTL fixe sur des millions d'entrées de cache peut provoquer un effondrement simultané vers les APIs externes lors de l'expiration — le jitter ±10% est la protection obligatoire contre ce phénomène (ADR-003).

**Independent Test**: Un développeur peut implémenter correctement n'importe laquelle des 9 stratégies de cache en utilisant uniquement ce document et sans consulter d'autres sources.

**Acceptance Scenarios**:

1. **Given** un développeur implémente le cache INSEE avec TTL 24h, **When** il code le TTL, **Then** il utilise `JitterTtl.withJitter(24h, 0.10)` (±10%) jamais un TTL fixe hardcodé.
2. **Given** un développeur implémente la déduplication d'idempotence Kafka, **When** il définit le TTL, **Then** il utilise un TTL fixe (pas de jitter) car l'exactitude de l'idempotence l'exige.
3. **Given** un développeur implémente la blacklist JWT, **When** il définit le TTL, **Then** il utilise un TTL fixe calé sur l'expiration exacte du token (pas de jitter).
4. **Given** une revue de code examine un nouveau composant de cache, **When** le TTL est supérieur à 1h, **Then** la revue valide la présence du jitter ou la justification explicite de l'exception.

---

### User Story 4 — Résilience Resilience4j configurée par service (Priority: P2)

En tant que développeur implémentant un appel vers un service externe (LLM, INSEE, BAN, RPPS, S3, Kafka, OCR/Vision), j'ai besoin de connaître les seuils exacts de Circuit Breaker, Retry, Bulkhead et Timeout pour chaque service, afin de garantir la stabilité de la plateforme en cas de défaillance partielle.

**Why this priority**: Des seuils de résilience incorrects (trop laxistes : cascade de pannes ; trop stricts : faux positifs fréquents) dégradent la disponibilité de la plateforme. Les seuils sont définis une fois pour l'ensemble des modules.

**Independent Test**: Un développeur peut configurer les annotations ou beans Resilience4j d'un service donné en lisant uniquement ce document, sans décider lui-même des seuils.

**Acceptance Scenarios**:

1. **Given** un développeur configure l'appel LLM (Claude/GPT-4o), **When** il définit le Circuit Breaker, **Then** le seuil est 50% d'échecs sur 10 appels glissants.
2. **Given** un développeur configure l'appel LLM, **When** il définit le Retry, **Then** il utilise 3 tentatives avec backoff exponentiel démarrant à 1s.
3. **Given** un développeur configure l'appel LLM, **When** il définit le Bulkhead, **Then** le pool est limité à 20 threads concurrents.
4. **Given** un développeur configure l'appel LLM, **When** il définit le Timeout, **Then** la limite est 30s via `@TimeLimiter`. Pour Tika/OpenCV (BR-VIS-003), le timeout est 15s via `@TimeLimiter(name="tika")` et la réponse fall-safe permet au pipeline de continuer.
5. **Given** une revue ArchUnit s'exécute, **When** elle vérifie les adaptateurs sortants, **Then** tous les appels externes sont décorés avec au moins Circuit Breaker + Timeout.

---

### Edge Cases

- Que se passe-t-il si un développeur utilise Spring Boot 3.x par erreur ? → Incompatibilité avec les APIs Spring Security 6 et Spring Data 4 — doit être détecté par le CI (Checkstyle ou ArchUnit).
- Comment gérer un nouveau service externe non listé dans la topologie Resilience4j ? → Utiliser les seuils LLM comme valeurs par défaut conservatives, en attendant un ADR dédié.
- Que faire si Valkey 8.x n'est pas disponible dans l'environnement cible ? → Valkey est Redis-compatible ; une instance Redis 7.x peut être utilisée temporairement en développement local, jamais en production.
- Que se passe-t-il si `JitterTtl.withJitter()` n'est pas disponible dans la version de la librairie cache retenue ? → L'implémentation doit fournir un utilitaire équivalent dans `docai-commons`.
- Que se passe-t-il si Tika dépasse le timeout `@TimeLimiter` de 15s (BR-VIS-003) ? → Le pipeline continue en mode fail-safe — le document est marqué "extraction visuelle indisponible" mais n'est pas bloqué ; aucune exception ne remonte au thread appelant.
- Que se passe-t-il si un Consumer Group ID est hardcodé dans `@KafkaListener` ? → Violation détectée par ArchUnit (`@KafkaListener` avec `groupId` littéral interdit) — build échoue en CI.

---

## Requirements *(mandatory)*

### Functional Requirements — BLOC 1 : Stack Technique

- **FR-001**: Le système DOIT utiliser **Java 21 LTS** comme runtime JVM sur toutes les applications backend.
- **FR-002**: Le système DOIT utiliser **Spring Boot 4.0.x** (pas Spring Boot 3.x) pour bénéficier des API Spring Framework 7.
- **FR-003**: Le système DOIT utiliser **Spring Security 6** pour l'authentification et l'autorisation (OAuth2/OIDC avec Keycloak 26).
- **FR-004**: Le système DOIT utiliser **Kafka 3.7 en mode KRaft** (sans Zookeeper) pour le messaging asynchrone.
- **FR-005**: Le système DOIT utiliser **Apicurio Registry 2.6** (pas Confluent Schema Registry) comme registre de schémas Avro/Protobuf.
- **FR-005b**: Les schémas Avro DOIVENT être **auto-générés via `maven-avro-plugin`** depuis les fichiers `.avsc` placés dans `src/main/avro/` du module `docai-adapter-out-kafka` — aucune classe Java Avro n'est écrite manuellement.
- **FR-006**: Le système DOIT utiliser **Valkey 8.x** (pas Redis) comme cache distribué — changement de licence Redis intervenu en mai 2024.
- **FR-007**: Le système DOIT utiliser **MongoDB 7.0** (avec Replica Set pour les transactions ACID) comme base de données principale.
- **FR-008**: Le système DOIT utiliser **Amazon S3 SDK v2** pour les interactions avec le stockage objet.
- **FR-009**: Le système DOIT utiliser **Resilience4j 2.x** pour tous les patterns de résilience (Circuit Breaker, Retry, Bulkhead, Timeout).
- **FR-010**: Les fonctionnalités OCR texte DOIVENT utiliser **Tess4J 5.x** + **PDFBox 3.x**.
- **FR-011**: Les fonctionnalités de vision documentaire (analyse visuelle) DOIVENT utiliser **JavaCV 4.9.0** — jamais `org.opencv` directement.
- **FR-012**: Les appels LLM vision DOIVENT passer par le modèle **OpenAI GPT-4o** (via l'adaptateur `docai-adapter-out-ai`).

### Functional Requirements — BLOC 2 : Topologie Kafka

- **FR-013**: Le système DOIT exposer exactement **8 topics Kafka** pour le pipeline documentaire :
  1. `docai.doc.uploaded` — document reçu, clé = `documentId`
  2. `docai.doc.classified` — classification terminée, clé = `documentId`
  3. `docai.doc.extracted` — extraction terminée, clé = `documentId`
  4. `docai.doc.validated` — validation terminée, clé = `documentId`
  5. `docai.doc.fraud.detected` — alerte fraude, clé = `documentId`
  6. `docai.doc.completed` — pipeline terminé, clé = `documentId`
  7. `docai.doc.failed` — erreur pipeline, clé = `tenantId` *(exception ADR-002)*
  8. `docai.doc.dlq` — Dead Letter Queue, clé = `tenantId` *(exception ADR-002)*
- **FR-014**: Tous les topics pipeline (sauf `failed` et `dlq`) DOIVENT utiliser **`documentId` comme clé de partition** (ADR-002) pour garantir l'ordre de traitement par document.
- **FR-015**: Les topics `docai.doc.failed` et `docai.doc.dlq` DOIVENT utiliser **`tenantId` comme clé de partition** pour regrouper les erreurs par tenant et faciliter le support.
- **FR-016**: Tous les Consumer Groups DOIVENT respecter la convention de nommage : **`docai.{module}.{name}.group`** (ex. `docai.extraction.llm.group`, `docai.fraud.analyser.group`).
- **FR-016b**: Les Consumer Group IDs DOIVENT être définis **exclusivement dans `application.yml`** (propriété `spring.kafka.consumer.group-id` ou surcharge par profil) — JAMAIS hardcodés dans les annotations `@KafkaListener(groupId = "...")`. Toute valeur littérale en annotation constitue une violation ArchUnit.

### Functional Requirements — BLOC 3 : Cache Valkey (ADR-003)

- **FR-017**: Tout TTL de cache **supérieur à 1 heure** DOIT inclure un **jitter aléatoire de ±10%** via `JitterTtl.withJitter()` pour éviter l'effet Thunder Herd.
- **FR-018**: Les **clés d'idempotence Kafka** (`topic:partition:offset`) DOIVENT utiliser un **TTL fixe de 24h** (pas de jitter) — la précision exacte de la déduplication interdit toute variation de TTL.
- **FR-019**: La **blacklist JWT** DOIT utiliser un **TTL fixe** calé sur l'expiration exacte du token (pas de jitter).
- **FR-020**: Le système DOIT implémenter les **9 stratégies de cache** suivantes :
  1. **Cache extraction LLM** — clé = `sha256(documentContent)`, TTL = 1h + jitter ±10%
  2. **Cache INSEE (SIREN/SIRET)** — clé = `siren:{id}`, TTL = 24h + jitter ±10%
  3. **Cache BAN (adresses)** — clé = `ban:{address_hash}`, TTL = 7j + jitter ±10%
  4. **Cache RPPS (professionnels santé)** — clé = `rpps:{id}`, TTL = 12h + jitter ±10%
  5. **Cache quota API** — clé = `quota:{tenantId}:{window}`, TTL = durée restante jusqu'au 1er du mois calendaire suivant (fixe — exception ADR-003, précision de reset obligatoire)
  6. **Cache session Keycloak (JWKS)** — TTL = 1h (fixe, ADR-006)
  7. **Cache résultat classification** — clé = `class:{sha256}`, TTL = 30min + jitter ±10%
  8. **Idempotence Kafka** — clé = `idem:{topic}:{partition}:{offset}`, TTL = 24h (fixe)
  9. **Blacklist JWT** — clé = `jwt:blacklist:{jti}`, TTL = expiration token (fixe)

### Functional Requirements — BLOC 4 : Résilience Resilience4j

- **FR-021**: Les appels vers les **services LLM** (Claude, GPT-4o) DOIVENT être configurés avec :
  - Circuit Breaker : seuil 50% d'échecs sur fenêtre de 10 appels
  - Retry : 3 tentatives, backoff exponentiel démarrant à 1s
  - Bulkhead : 20 threads concurrents maximum
  - Timeout : 30 secondes
- **FR-022**: Les appels vers **Tika/OpenCV** (traitement visuel, BR-VIS-003) DOIVENT être configurés avec :
  - Circuit Breaker : seuil 50% d'échecs sur fenêtre de 5 appels
  - Timeout : 15 secondes via **`@TimeLimiter(name="tika", timeoutDuration=15s)`** — en cas de dépassement, le pipeline DOIT continuer (comportement fail-safe, pas de propagation de l'exception bloquante)
- **FR-023**: Les appels vers **INSEE** DOIVENT être configurés avec Circuit Breaker + Retry + Timeout adaptés à la criticité du service.
- **FR-024**: Les appels vers **BAN** DOIVENT être configurés avec Circuit Breaker + Retry + Timeout adaptés.
- **FR-025**: Les appels vers **RPPS** DOIVENT être configurés avec Circuit Breaker + Retry + Timeout adaptés.
- **FR-026**: Tous les adaptateurs sortants (`docai-adapter-out-*`) DOIVENT décorer leurs appels avec au minimum **Circuit Breaker + Timeout**.
- **FR-027**: Le cache JWKS Keycloak DOIT être configuré avec un TTL de **1h en `application.yml`** (ADR-006) — pas de TTL dynamique pour ce cache.

### Key Entities

- **Topic Kafka** : Nom, clé de partition (documentId ou tenantId), Consumer Groups associés, schéma Avro.
- **Stratégie de cache** : Nom, pattern de clé, TTL, règle de jitter (avec ou sans), cas d'usage.
- **Configuration Resilience4j** : Service cible, seuil CB (% sur N appels), stratégie retry, taille bulkhead, timeout.
- **ADR (Architecture Decision Record)** : Identifiant (ADR-002, ADR-003, ADR-006), décision, justification, règles non négociables.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un développeur peut configurer son environnement complet et compiler tous les modules en moins de **30 minutes** en suivant uniquement ce document.
- **SC-002**: **100% des topics Kafka** du pipeline utilisent la bonne clé de partition (documentId ou tenantId selon la règle ADR-002) — vérifiable par revue d'architecture ou test d'intégration.
- **SC-003**: **0 TTL de cache supérieur à 1h** sans jitter ±10% dans la base de code — vérifiable par analyse statique ou ArchUnit.
- **SC-004**: **100% des adaptateurs sortants** sont décorés avec au minimum Circuit Breaker + Timeout — vérifiable par ArchUnit.
- **SC-005**: En cas de défaillance d'un service LLM, le Circuit Breaker s'ouvre en moins de **10 appels consécutifs en échec** et protège le reste du système.
- **SC-006**: **0 occurrence** de `org.opencv` direct dans la base de code (JavaCV obligatoire) — vérifiable par Grep/ArchUnit.
- **SC-007**: **0 occurrence** de Spring Boot 3.x ou Redis (hors commentaire) dans les dépendances Maven — vérifiable par `mvn dependency:tree`.
- **SC-008**: La revue de code de tout nouveau composant cache valide la présence ou l'absence justifiée du jitter avec un taux d'approbation de **100%**.

---

## Assumptions

- Les développeurs travaillent sur un environnement local avec Docker Compose (infrastructure déjà définie dans `CLAUDE.md`).
- Keycloak 26 est le seul fournisseur d'identité retenu — pas de migration vers un autre IdP prévue en v1.
- Les seuils Resilience4j pour INSEE, BAN, RPPS non détaillés dans ce module seront précisés dans les specs des modules correspondants (Module 3 — Intégrations Externes) ; les développeurs utiliseront les seuils LLM par défaut en attendant.
- `JitterTtl.withJitter()` sera fourni comme utilitaire dans le module `docai-commons` — les modules n'implémentent pas leur propre jitter.
- La compatibilité Valkey 8.x / Redis 7.x est maintenue pour les environnements de développement local uniquement ; la production utilise exclusivement Valkey.
- ADR-002, ADR-003 et ADR-006 sont des décisions définitives non sujettes à révision pour la v1 — tout écart doit faire l'objet d'un nouvel ADR approuvé par l'architecte.
- OpenAI GPT-4o est le modèle retenu pour la vision documentaire ; l'adaptateur `docai-adapter-out-ai` abstrait le fournisseur pour permettre une future substitution sans impact sur les use cases.
