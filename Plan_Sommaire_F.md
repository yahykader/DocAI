# Plan Sommaire — DOCAI_BACKEND_MASTER_SPECKIT_F.md
# Version corrigée & uniformisée

> **Total : 8947 lignes — 7 modules métier — 20 phases — 11 ADR — 7 commons**
> **Structure uniforme : Partie → Module → Phase → Points**

---

## PARTIE 0 — Vision & Description (ligne 51)

- Module 0.A — Présentation du Projet
  - Phase 1 — Le problème résolu
  - Phase 2 — La solution DocAI (pipeline 5 étapes)
  - Phase 3 — Marchés cibles (6 secteurs)
  - Phase 4 — Business Values en chiffres (6 KPIs)

---

## PARTIE 1 — Architecture & Choix Techniques (ligne 112)

- Module 1.A — Architecture & Principes
  - Phase 1 — Architecture Hexagonale (Ports & Adapters)
    - Pourquoi cette architecture
    - Structure Maven Multi-Modules
    - ArchUnit — 12 règles exactes
  - Phase 2 — Principes SOLID appliqués
  - Phase 3 — Catalogue des Design Patterns

- Module 1.B — Stack & Intégrations
  - Phase 1 — Stack Technique (décisions détaillées + pourquoi S3)
  - Phase 2 — Topologie Kafka
    - Schémas Avro de chaque event
    - Consumer Group IDs convention de nommage
  - Phase 3 — Stratégies de Cache Valkey
  - Phase 4 — Résilience Transversale Resilience4j

- Module 1.C — Persistance & Standards
  - Phase 1 — Observabilité Transversale
    - Politique de logs (niveaux et règles obligatoires)
  - Phase 2 — MongoDB
    - Règles de migration Mongock
    - Collections MongoDB
    - Stratégie de pagination globale
    - Convention de nommage collections
    - Stratégie d'indexation
  - Phase 3 — Stratégie de Versioning API

---

## PARTIE 2 — Mise en place & CI/CD (ligne 1009)

- Module 2.A — Setup Projet (0.A — ligne 1020)
  - Phase 1 — Prérequis système
  - Phase 2 — Structure projet (arborescence complète)
  - Phase 3 — docker-compose.yml (infrastructure locale)
  - Phase 4 — .env.example (variables d'environnement)
  - Phase 5 — Commandes de démarrage
  - Phase 6 — Vérification de l'installation
  - Phase 7 — Gestion des environnements DEV / STAGING / PRODUCTION
  - Phase 8 — Données de test & Seeding
  - Phase 9 — Configuration Keycloak realm-docai.json
  - Phase 10 — application.yml complet

- Module 2.B — Standards & Qualité (0.B — ligne 2006)
  - Phase 1 — Feature Flags (stratégie de déploiement progressif)
  - Phase 2 — Templates Emails Amazon SES
  - Phase 3 — Definition of Ready (DoR)
  - Phase 4 — Pull Request Template
  - Phase 5 — Definition of Done CI/CD

- Module 2.C — CI/CD Pipeline (0.C — ligne 2043)
  - Phase 1 — Vue d'ensemble du pipeline
  - Phase 2 — Déclencheurs par branche
  - Phase 3 — Stratégie de branches GitFlow
  - Phase 4 — Secrets & Variables GitHub
  - Phase 5 — Quality Gates (seuils de blocage)
  - Phase 6 — Dockerfile multi-stage
  - Phase 7 — GitHub Actions Workflows complets
  - Phase 8 — sonar-project.properties
  - Phase 9 — Contract Testing Frontend/Backend
  - Phase 10 — Infrastructure as Code Terraform
  - Phase 11 — Kubernetes manifestes
  - Phase 12 — Health Checks détaillés

---

## PARTIE 3 — Commons (ligne 3341)

- Module 3.A — Composants Réutilisables (0.D — ligne 3353)
  - Phase 1 — commons-multitenancy (TenantContext, MongoTenantFilter)
  - Phase 2 — commons-api (GlobalExceptionHandler, ProblemDetail RFC 7807)
  - Phase 3 — commons-audit (@Audited, AuditEntry append-only)
  - Phase 4 — commons-kafka (ResilientKafkaConsumer, DLQ)
  - Phase 5 — commons-outbox (OutboxMessage, OutboxRelay)
  - Phase 6 — commons-quota (@QuotaProtected, script Lua atomique)
  - Phase 7 — commons-testing (AbstractIntegrationTest, TestBuilders)
  - Interfaces Java — Signatures obligatoires

---

## PARTIE 4 — Fondations Métier (ligne 3696)

- Module 0 — Sécurité & Multi-Tenancy (ligne 3706)
  - Configuration Keycloak (realm, clients, PKCE, durées token)
  - Business Rules Sécurité (BR-SEC-001 à 008)
  - Architecture Hexagonale Module 0
  - Rate Limiting Bucket4j + Valkey
  - RFC 7807 + catalogue erreurs
  - Scénarios BDD Sécurité
  - Phase 0.1 — Inscription Tenant (ligne 3892)
    - Flow inscription automatique (10 étapes)
    - Business Rules BR-ONB
    - Use Cases UC-ONB
    - Endpoints
    - Emails transactionnels
    - Architecture Hexagonale
    - Definition of Done
  - Phase 0.2 — Login, Logout & Session (ligne 3990)
    - Flow login/logout/refresh
    - Business Rules BR-AUTH
    - Use Cases UC-AUTH
    - Endpoints
    - JWT Blacklist Valkey
    - Emails transactionnels
    - Architecture Hexagonale
    - Definition of Done
  - Phase 0.3 — Gestion Équipe TENANT_ADMIN (ligne 4208)
    - Flow invitation & activation
    - Business Rules BR-ONB
    - Use Cases UC-ONB
    - Endpoints
    - Emails transactionnels
    - Architecture Hexagonale
    - Definition of Done
  - Phase 0.4 — Profil & Sécurité du Compte (ligne 4376)
    - Changement mot de passe
    - Modification profil
    - Changement email (vérification obligatoire)
    - Historique de connexion
    - 2FA TOTP
    - Business Rules BR-PRF
    - Endpoints
    - Architecture Hexagonale
    - Definition of Done
  - Phase 0.5 — Accès Support Client / Impersonation (ligne 4652)
    - Flow consentement TENANT_ADMIN
    - Token UUID TTL 2h READ_ONLY
    - Business Rules BR-SUP
    - Endpoints
    - Audit trail visible
  - Definition of Done Module 0 complet (ligne 4713)

- Module 0.5 — RGPD & Privacy (ligne 4735) ← corrigé (était Module 0.3)
  - Principes RGPD appliqués à DocAI
  - Business Rules BR-RGP-001 à 010
  - Données PII identifiées + chiffrement FLE
  - Durée de rétention (30–365 jours)
  - Use Cases RGPD
  - Scénarios BDD RGPD
  - Architecture Hexagonale
  - Suppression compte individuel

---

## PARTIE 5 — Pipeline de Traitement (ligne 5073)

- Module 1 — Reconnaissance de Documents (ligne 5085)
  - Phase 1.1 — Upload & Validation (ligne 5089)
    - Flow upload (10 étapes)
    - Business Rules BR-REC
    - Aggregate Document + State Machine
    - Idempotence X-Idempotency-Key
    - Quota atomique Lua ADR-001
    - S3 multipart + AbortMultipartUpload ADR-007
    - Hash SHA-256 streaming
    - Outbox Pattern
    - Endpoints
    - Scénarios BDD
    - Architecture Hexagonale
    - Mongock V001
    - Definition of Done
  - Phase 1.2 — Classification Automatique IA (ligne 5336)
    - Flow classification
    - Business Rules BR-REC
    - Seuils confiance (≥0.85 / 0.70–0.84 / <0.70)
    - VisionModelAdapter GPT-4o
    - FallbackRuleBasedClassifier
    - Circuit Breaker Resilience4j
    - Cache SHA-256 ADR-003
    - Correction manuelle
    - Endpoints
    - Scénarios BDD
    - Architecture Hexagonale
    - Mongock V009
    - Definition of Done

- Module 2 — Extraction d'Informations (ligne 5530)
  - Phase 2.1 — Pipeline OCR & Extraction LLM (ligne 5534)
    - Flow extraction
    - Business Rules BR-EXT
    - Types de documents + champs extraits
    - Prompts LLM obligatoires
    - Endpoints
    - Scénarios BDD
    - Architecture Hexagonale
    - Definition of Done
  - Phase 2.2 — Validation Métier & APIs Externes (ligne 5876)
    - SIRET algorithme Luhn
    - IBAN modulo 97
    - API INSEE (SIRET/SIREN)
    - API BAN (adresses)
    - API RPPS (professionnels santé)
    - Stratégie fail-open + cache Valkey 7j
    - Business Rules BR-VAL
    - Scénarios BDD
    - Definition of Done
  - Phase 2.3 — Correction Manuelle & Audit (ligne 6006)
    - Flow correction
    - AuditEntry immuable
    - Revalidation automatique
    - Invalidation cache Valkey
    - Business Rules BR-COR
    - Endpoints
    - Definition of Done

- Module 3 — Détection de Fraude (ligne 6070)
  - Phase 3.1 — Scoring de Base & Signaux Données (ligne 6074)
    - Scoring 0–100
    - Signaux fraude données
    - Business Rules BR-FRD
    - Architecture Hexagonale
    - Scénarios BDD
    - Definition of Done
  - Phase 3.2 — Analyseurs Avancés : Apache Tika + Visuel (ligne 6251)
    - Analyse métadonnées Tika
    - Analyse visuelle
    - Business Rules BR-FRD
    - Definition of Done
  - Phase 3.3 — Workflow Révision Humaine (ligne 6377)
    - Queue révision FRAUD_REVIEWER
    - Flow décision APPROVED/REJECTED
    - Business Rules BR-FRD
    - Endpoints
    - Scénarios BDD
    - Definition of Done

- Module 4 — Orchestration & Pipeline (ligne 6468)
  - Phase 4.1 — Pipeline Kafka & Idempotence (ligne 6472)
    - Topics + consumer groups
    - Idempotence par offset Valkey
    - OutboxPoller
    - Business Rules BR-PPL
    - Definition of Done
  - Phase 4.2 — Retry, DLQ & Reprise sur Échec (ligne 6556)
    - Retry exponentiel
    - Dead Letter Queue
    - Replay admin
    - Business Rules BR-PPL
    - Definition of Done
  - Phase 4.3 — Saga & Compensation (ligne 6627)
    - 7 scénarios d'échec
    - State machine PENDING→COMPLETED
    - Compensations
    - Business Rules BR-PPL
    - Scénarios BDD
    - Definition of Done

---

## PARTIE 6 — Produit & Monétisation (ligne 6778)

- Module 5 — Dashboard & Reporting (ligne 6790)
  - Phase 5.1 — Read Model CQRS & Analytics (ligne 6796)
    - DashboardProjectionConsumer
    - ReadModelReconciliationScheduler
    - KPIs & métriques
    - Endpoints
    - Business Rules BR-DSH
    - Definition of Done
  - Phase 5.2 — Alertes Temps Réel SSE (ligne 6922)
    - SseEmitter Spring
    - AlertKafkaConsumer
    - Keepalive 30s
    - Last-Event-ID reconnexion
    - Limite 50 connexions/tenant
    - Business Rules BR-DSH
    - Definition of Done
  - Phase 5.3 — Centre de Notifications In-App (ligne 6958)
    - Historique persistant MongoDB TTL 90j
    - Types FRAUD_ALERT / QUOTA_WARNING / PAYMENT_FAILED
    - Marquage lu/non-lu
    - Badge SSE temps réel
    - Feature Flag
    - Business Rules BR-NOT
    - Definition of Done
  - Phase 5.4 — Fonctionnalités Avancées Backlog v2 (ligne 7237) ← remis à sa place
    - Export CSV/Excel
    - Recherche full-text
    - Filtres avancés

- Module 6 — Intégrations & API Publique (ligne 7018)
  - Phase 6.1 — API Publique & API Keys (ligne 7022)
    - Hash SHA-256 + sel
    - Scopes READ/WRITE/ADMIN
    - Révocation immédiate Valkey
    - SpringDoc OpenAPI 3.1
    - Business Rules BR-INT
    - Endpoints
    - Definition of Done
  - Phase 6.2 — Webhooks Fiables (ligne 7120)
    - Signature HMAC-SHA256
    - Retry 5× backoff exponentiel
    - Circuit Breaker Resilience4j
    - DLQ + alerte dashboard
    - Log livraisons MongoDB
    - Business Rules BR-INT
    - Definition of Done
  - Phase 6.3 — Rate Limiting Avancé & Quotas (ligne 7202)
    - Quotas plans Starter/Pro/Enterprise
    - Réinitialisation 1er du mois UTC
    - Notifications 80% et 95%
    - Overage autorisé + facturé
    - Bucket4j + Valkey
    - Business Rules BR-INT
    - Definition of Done

- Module 7 — Billing & Abonnements (ligne 7366)
  - Phase 7.1 — Plans & Tarification
    - Flow proposition plan TENANT_ADMIN
    - Billing Feature Flag
    - Plans & tarification modèle hybride
    - Business Rules BR-BIL
  - Phase 7.2 — Cycle de vie & Stripe
    - Cycle de vie abonnement
    - Intégration Stripe
    - Calcul facture mensuelle
    - Webhooks Stripe
  - Phase 7.3 — Use Cases & BDD
    - Use Cases Billing
    - Scénarios BDD Billing
    - Emails transactionnels Billing
    - Architecture Hexagonale Module 7

---

## PARTIE 7 — Annexes (ligne 7799)

- Module 7.A — Roadmap & Checklists
  - Phase 1 — Roadmap Globale Backend (Annexe A)
  - Phase 2 — Production Readiness Checklist (Annexe C)
  - Phase 3 — Rotation des Secrets Applicatifs
  - Phase 4 — Chaos Engineering (scénarios de panne)
  - Phase 5 — Publication OpenAPI (portail développeur)

- Module 7.B — ADR — Décisions Architecturales (Annexe E — ligne 8047)
  - ADR-001 — Concurrence compteurs quota (Lua atomique)
  - ADR-002 — Ordering events Kafka par document
  - ADR-003 — Thundering Herd cache Valkey (jitter TTL)
  - ADR-004 — Limite transaction MongoDB 4MB
  - ADR-005 — Rotation clés chiffrement PII
  - ADR-006 — Fallback Keycloak indisponible (cache JWKS 1h)
  - ADR-007 — Nettoyage uploads S3 multipart non finalisés
  - ADR-008 — Mémoire JVM et TestContainers GitHub Actions
  - ADR-009 — Downgrade plan et données orphelines
  - ADR-010 — Scalabilité index MongoDB grandes collections
  - ADR-011 — Cohérence et resynchronisation Read Model CQRS

- Module 7.C — Standards Opérationnels (Annexe F)
  - Phase 1 — Stratégie Sauvegarde & Disaster Recovery
  - Phase 2 — Politique Dépendances & Mises à jour
  - Phase 3 — Politique Branches Hotfixes
  - Phase 4 — SLA documenté et publié
  - Phase 5 — Glossaire Métier
  - Phase 6 — Guide Onboarding Développeur
  - Phase 7 — Contacts & Responsabilités

- Module 7.D — Tests & i18n (Annexe G)
  - Phase 1 — Stratégie Tests de Charge k6
  - Phase 2 — Internationalisation i18n (Backlog v2)
