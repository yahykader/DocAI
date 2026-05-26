# Stack Technique Checklist: Module B — Stack & Intégrations

**Purpose**: Valider la qualité, la complétude et la cohérence des exigences du Module B avant `/speckit-tasks` — chaque item est un test unitaire sur les exigences écrites, pas sur l'implémentation.
**Created**: 2026-05-25
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md) · [contracts/application-yml.md](../contracts/application-yml.md)
**ADR vérifiés**: ADR-002 · ADR-003 · ADR-006
**Violations architecture détectées**: 4 High (Tess4J OCR absent, topics Kafka divergents, JitterTtl ambiguïté, Apicurio non mis à jour), 2 Medium, 1 Low — voir résultat `/speckit-architecture-guard-violation-detection` du 2026-05-25

---

## Complétude des Exigences — Stack Technique (FR-001–FR-012)

- [ ] CHK001 — Les versions exactes sont-elles spécifiées pour TOUTES les dépendances critiques dans le spec (Java 21, Spring Boot 4.0.x, Resilience4j 2.x, Avro 1.11.x, Tika 2.9.x, PDFBox 3.x, Tess4J 5.x) ? [Complétude, Spec §FR-001–FR-012]
- [ ] CHK002 — La version de JavaCV est-elle cohérente entre l'entrée checklist (4.9.0) et le plan.md Technical Context (JavaCV 1.5.11) — cette divergence est-elle résolue dans les artefacts planification ? [Conflit, Ambiguïté]
- [ ] CHK003 — La justification du choix Valkey vs Redis est-elle documentée avec la date précise du changement de licence (mai 2024) afin qu'un développeur comprenne le POURQUOI et pas seulement le QUOI ? [Clarté, Spec §FR-006]
- [ ] CHK004 — L'interdiction d'utiliser `org.opencv` directement est-elle associée à la règle de vérification (ArchUnit ou Grep CI) qui la rend testable ? [Mesurabilité, Spec §SC-006, FR-011]
- [ ] CHK005 — La contrainte Spring Boot 4.0.x (pas 3.x) est-elle liée à une vérification automatique dans le CI (`mvn dependency:tree`) ou ArchUnit ? [Mesurabilité, Spec §SC-007, FR-002]
- [ ] CHK006 — L'exigence `maven-avro-plugin` (FR-005b) précise-t-elle la version du plugin et le répertoire exact `src/main/avro/` comme contrainte non négociable (pas une recommandation) ? [Clarté, Spec §FR-005b]

---

## Cohérence des Exigences — Topologie Kafka (ADR-002)

- [ ] CHK007 — Le nom `docai.doc.fraud.detected` dans spec FR-013 est-il en conflit avec `docai.doc.fraud.analyzed` dans la Constitution — cette divergence est-elle résolue par un ADR explicite ou une décision documentée ? [Conflit critique, Spec §FR-013 vs Constitution §Kafka Topics]
- [ ] CHK008 — Le topic `docai.doc.validated` listé dans spec FR-013 est-il absent de la Constitution et du docker-compose — l'omission est-elle intentionnelle et documentée (research.md D7 la signale sans créer de tâche) ? [Lacune, Spec §FR-013]
- [ ] CHK009 — Le topic `docai.outbox.relay` présent dans la Constitution est-il absent de la liste des 8 topics du spec FR-013 — cette exclusion est-elle justifiée ? [Lacune, Spec §FR-013 vs Constitution]
- [ ] CHK010 — La liste des Consumer Groups dans le spec est-elle explicitement énumérée (pas seulement la convention de nommage) avec le total exact des groupes requis ? [Complétude, Spec §FR-016]
- [ ] CHK011 — Les Consumer Groups dans le spec (ex. `docai.extraction.llm.group`, `docai.fraud.analyser.group`) sont-ils cohérents avec ceux déclarés dans application.yml — en particulier `analyser` vs `analysis` ? [Cohérence, Spec §FR-016 vs application.yml §kafka.groups]
- [ ] CHK012 — La règle ArchUnit interdisant `groupId` littéral dans `@KafkaListener` (FR-016b) est-elle formulée comme une exigence testable avec un critère de succès mesurable (build échoue en CI) ? [Mesurabilité, Spec §FR-016b]
- [ ] CHK013 — Le spec définit-il les noms de schémas Avro (namespace + record name) pour chacun des 8 topics ou renvoie-t-il à une convention explicite ? [Lacune, Spec §FR-013]

---

## Clarté des Exigences — Stratégies Cache Valkey (ADR-003)

- [ ] CHK014 — Les TTL des stratégies 1–4 dans spec FR-020 (LLM=1h, INSEE=24h, BAN=7j, RPPS=12h) sont-ils cohérents avec la Constitution, data-model.md et application.yml (LLM=24h, INSEE=7j, BAN=30j, RPPS=7j) — ce conflit de 4 valeurs est-il résolu ? [Conflit critique, Spec §FR-020 vs Constitution §Cache Keys]
- [ ] CHK015 — La stratégie "Cache classification SHA-256 : 1h jitter" mentionnée dans l'entrée checklist est-elle cohérente avec spec FR-020 stratégie 7 (30min + jitter) ? [Conflit, Spec §FR-020]
- [ ] CHK016 — La stratégie "Idempotence upload X-Idempotency-Key : 24h FIXE" est-elle documentée dans spec FR-020 (absente des 9 stratégies actuellement listées) ? [Lacune]
- [ ] CHK017 — La localisation canonique de `JitterTtl` est-elle résolue sans ambiguïté dans le spec — data-model.md dit `docai-commons`, plan.md dit `docai-adapter-out-valkey`, spec §Assumptions dit `docai-commons` — laquelle prévaut ? [Conflit, Ambiguïté, Spec §Assumptions vs plan.md §Constraints]
- [ ] CHK018 — Le spec justifie-t-il explicitement le POURQUOI de chacune des 4 exceptions au jitter (idempotence → précision déduplication ; JWT → expiration exacte ; quota → reset 1er du mois ; JWKS → Spring Security géré) ? [Clarté, Spec §FR-018–FR-019]
- [ ] CHK019 — Chacune des 9 stratégies de cache spécifie-t-elle le pattern exact de la clé (ex. `sha256(documentContent)` pour LLM, `siren:{id}` pour INSEE) de façon à permettre une implémentation sans ambiguïté ? [Clarté, Spec §FR-020]

---

## Complétude des Exigences — Résilience Resilience4j (FR-021–FR-027)

- [ ] CHK020 — Le spec définit-il une instance Resilience4j SÉPARÉE pour Tess4J OCR (60s timeout, 10 threads, Retry 3×) distincte de l'instance Apache Tika (15s BR-VIS-003, 5 threads, Retry 2×) — la Constitution Annex A les distingue explicitement mais spec FR-022 les regroupe sous "Tika/OpenCV" ? [Lacune critique, Constitution Annex A vs Spec §FR-022]
- [ ] CHK021 — Les seuils Resilience4j pour INSEE (FR-023), BAN (FR-024), et RPPS (FR-025) sont-ils entièrement spécifiés dans le spec ou délibérément délégués au Module 3 — et si délégués, cette dépendance bloquante est-elle documentée ? [Lacune, Spec §FR-023–FR-025 vs Assumptions]
- [ ] CHK022 — Le comportement fail-safe de Tika/OpenCV après timeout 15s (BR-VIS-003) est-il spécifié avec suffisamment de précision pour guider l'implémentation : quel état retourne le document ? quelle valeur fallback ? quelle métrique est incrémentée ? [Clarté, Spec §FR-022]
- [ ] CHK023 — La propriété `automaticTransitionFromOpenToHalfOpenEnabled: true` et le nombre d'appels `permittedNumberOfCallsInHalfOpenState: 3` sont-ils documentés comme exigences dans le spec ou uniquement dans le contrat application-yml.md ? [Complétude, contracts/application-yml.md §BLOC 3]
- [ ] CHK024 — Le spec définit-il le comportement attendu quand PLUSIEURS services Resilience4j sont simultanément en état OPEN (dégradation progressive, mode dégradé de la plateforme) ? [Couverture, Lacune]
- [ ] CHK025 — La règle "minimum Circuit Breaker + Timeout sur tous les adaptateurs sortants" (SC-004, FR-026) est-elle associée à une règle ArchUnit vérifiable — et cette règle est-elle spécifiée dans le spec avec son critère d'échec ? [Mesurabilité, Spec §SC-004, FR-026]

---

## Mesurabilité — Critères de Succès

- [ ] CHK026 — SC-003 ("0 TTL > 1h sans jitter") est-il vérifiable par analyse statique ou ArchUnit — le spec définit-il comment automatiser cette vérification ? [Mesurabilité, Spec §SC-003]
- [ ] CHK027 — SC-005 ("Circuit Breaker s'ouvre en < 10 appels consécutifs en échec") est-il cohérent avec la configuration fenêtre glissante de 10 appels — ou peut-il se déclencher EXACTEMENT sur le 10e appel seulement ? [Cohérence, Spec §SC-005 vs FR-021]
- [ ] CHK028 — SC-001 ("configurer l'environnement en < 30 min") est-il objectivement mesurable — le spec précise-t-il quelles étapes sont incluses dans ces 30 minutes et dans quel ordre ? [Mesurabilité, Spec §SC-001]
- [ ] CHK029 — SC-008 ("revue de code valide jitter à 100%") est-il une exigence process ou une exigence technique — peut-elle être vérifiée sans intervention humaine systématique ? [Clarté, Spec §SC-008]

---

## Couverture — Edge Cases et Récupération

- [ ] CHK030 — L'edge case "que faire si `JitterTtl.withJitter()` n'est pas disponible" est-il spécifié avec une action concrète (implémentation dans `docai-commons`) ou reste-t-il vague ? [Clarté, Spec §Edge Cases]
- [ ] CHK031 — Le spec couvre-t-il le scénario de Valkey indisponible (cache miss total) pour chacune des 9 stratégies — notamment pour INSEE/BAN/RPPS où le cache est la protection contre le rate limiting ? [Couverture, Lacune]
- [ ] CHK032 — Le spec définit-il les exigences de rollback pour une modification de configuration Resilience4j en production (ex. modification du seuil CB LLM de 50% à 60%) ? [Lacune]
- [ ] CHK033 — Le comportement en cas de Consumer Group ID hardcodé détecté par ArchUnit est-il spécifié au niveau exigence (build fail CI) ou au niveau process (revue de code) ? [Clarté, Spec §FR-016b, Edge Cases]

---

## Cohérence Inter-Artefacts

- [ ] CHK034 — Les URLs de vérification Actuator dans quickstart.md (`http://localhost:8080/api/actuator/health`) sont-elles cohérentes avec le management port configuré (9091 dans application.yml) — l'URL correcte devrait être `http://localhost:9091/actuator/health` ? [Conflit, quickstart.md §Vérifications vs contracts/application-yml.md §BLOC 6]
- [ ] CHK035 — La version Apicurio dans spec FR-005 (2.6) et plan.md (2.6.5.Final) est-elle reflétée dans backend/pom.xml — ou la valeur `2.4.15.Final` toujours présente constitue-t-elle une inconsistance bloquante ? [Conflit, Spec §FR-005 vs pom.xml:64]
- [ ] CHK036 — Le module `docai-commons` référencé dans data-model.md §Entité JitterTtl, research.md §D4, et spec §Assumptions existe-t-il dans les 11 modules Maven du pom.xml parent — ou cette référence est-elle un module fantôme ? [Lacune critique, Spec §Assumptions vs backend/pom.xml §modules]
- [ ] CHK037 — Les seuils Resilience4j dans le spec (FR-021–FR-027) sont-ils identiques à ceux des blocs YAML dans contracts/application-yml.md §BLOC 3 — notamment les valeurs `waitDuration` pour Retry et `maxWaitDuration` pour Bulkhead ? [Cohérence, Spec §FR-021 vs contracts §BLOC 3]
- [ ] CHK038 — Les 5 noms de metrics Micrometer (`docai_document_upload_total`, etc.) sont-ils définis comme exigences formelles dans le spec (FR) ou uniquement dans l'input checklist externe — leur absence du spec constitue-t-elle une lacune ? [Lacune, Spec §Requirements]

---

## Exigences Non Fonctionnelles — Observabilité Micrometer

- [ ] CHK039 — Les seuils d'alerte Grafana (error rate > 1%, Circuit Breaker OPEN, consumer lag > 1000) sont-ils documentés comme exigences non fonctionnelles dans le spec avec les valeurs exactes ? [Lacune, Spec §Requirements]
- [ ] CHK040 — L'impact cardinality des labels `{tenant}` sur la métrique `docai_document_upload_total` est-il documenté — un tenant label sur des milliers de tenants peut créer une explosion de séries temporelles Prometheus ? [Couverture, Performance]
- [ ] CHK041 — L'alerte "Grafana lag configurée pour chaque consumer group" est-elle cohérente avec les 8 groupes actuels dans application.yml ou les 10 groupes du checklist input — quel est le nombre exact ? [Cohérence]
- [ ] CHK042 — Le scraping Prometheus via le port management (9091, `metrics_path: /actuator/prometheus`) est-il documenté comme exigence dans le spec, ou uniquement dans prometheus.yml (artefact infra externe) ? [Complétude, Spec §Requirements]

---

## Hypothèses et Dépendances

- [ ] CHK043 — L'hypothèse "JitterTtl fourni dans `docai-commons`" est-elle validée par l'existence effective du module dans le POM parent — ou représente-t-elle une dépendance sur un module à créer qui n'est pas planifié dans ce Module B ? [Hypothèse non validée, Spec §Assumptions vs pom.xml]
- [ ] CHK044 — L'hypothèse "seuils Resilience4j pour INSEE/BAN/RPPS précisés dans Module 3" est-elle une dépendance bloquante pour les tâches de Module B — et si oui, est-elle tracée comme risque dans le plan ? [Dépendance, Spec §Assumptions vs plan.md §Phase 1]
- [ ] CHK045 — La dépendance "Module 1.A (Setup Projet) terminé" est-elle vérifiable par un critère objectif (ex. `./mvnw clean compile → BUILD SUCCESS`) spécifié dans le spec ? [Dépendance, plan.md §Prérequis]

---

## Notes

- Cocher un item : `[x]`
- Marqueurs : `[Conflit]` = divergence entre artefacts · `[Lacune]` = exigence absente · `[Ambiguïté]` = interprétation multiple · `[Conflit critique]` = blocker pour implémentation
- Les items CHK007, CHK009, CHK014, CHK020, CHK035, CHK036 sont des **bloquants confirmés** par `/speckit-architecture-guard-violation-detection` (2026-05-25)
- Prochaine action recommandée : résoudre les items `[Conflit critique]` avant `/speckit-tasks`
