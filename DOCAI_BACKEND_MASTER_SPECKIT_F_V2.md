# DocAI — Plan de Découpage Complet Backend
## Micro-tâches de 1 jour maximum · Référence de développement

> **Ce fichier est un plan de travail** qui se superpose au `DOCAI_BACKEND_MASTER_SPECKIT_F.md`.
> Il ne remplace pas le speckit technique — il découpe chaque section en tâches de 1 jour max.
> **Chaque micro-tâche = 1 PR + 1 critère de done.**
> **Référence technique complète :** `DOCAI_BACKEND_MASTER_SPECKIT_F.md`

---

## Sommaire

- [PARTIE 1 — Setup (12 micro-tâches)](#partie-1--setup)
- [PARTIE 2 — Commons (7 micro-tâches)](#partie-2--commons)
- [PARTIE 3 — Fondations Métier (20 micro-tâches)](#partie-3--fondations-métier)
- [PARTIE 4 — Pipeline de Traitement (70 micro-tâches)](#partie-4--pipeline-de-traitement)
- [PARTIE 5 — Produit (33 micro-tâches)](#partie-5--produit)
- [Récapitulatif Global](#récapitulatif-global)

---

## Ordre de développement obligatoire

| Partie | Prérequis | Durée |
|--------|-----------|-------|
| **1 — Setup** | Aucun | ~2 semaines |
| **2 — Commons** | Partie 1 validée | ~2 semaines |
| **3 — Fondations Métier** | Partie 2 terminée | ~4 semaines |
| **4 — Pipeline** | Partie 3 validée | ~14 semaines |
| **5 — Produit** | Partie 4 fonctionnelle | ~7 semaines |

**Règle absolue : ne pas démarrer une partie si la précédente n'est pas validée.**

---

# PARTIE 1 — SETUP

> **12 micro-tâches — ~2 semaines**
> Prérequis : aucun.
> Critère de passage : CI verte, tous les services UP, premier build vert.

---

## 0.A — Création du Projet (5 micro-tâches)

### 0.A.1 — Structure Maven (1j)

Créer le POM parent et les 10 modules Maven (`docai-domain`, `docai-application`, `docai-adapter-in-rest`, `docai-adapter-in-kafka`, `docai-adapter-out-mongodb`, `docai-adapter-out-kafka`, `docai-adapter-out-valkey`, `docai-adapter-out-ai`, `docai-adapter-out-storage`, `docai-adapter-out-external`, `docai-bootstrap`).

**Critère de done :** `./mvnw clean compile` passe sans erreur.

---

### 0.A.2 — Docker Compose Infrastructure (1j)

Configurer et démarrer tous les services locaux : MongoDB 7 (Replica Set), Kafka 3.7 (KRaft), Kafka UI, Apicurio Schema Registry, Valkey 8, Keycloak 26, Prometheus, Grafana, Grafana Tempo, OpenTelemetry Collector. Création des topics Kafka (8 topics avec partitions et rétention corrects).

**Critère de done :** `docker compose ps` → tous les services healthy. Les 11 interfaces accessibles (Swagger, Grafana, Kafka UI, Keycloak, etc.).

---

### 0.A.3 — Amazon S3 + Configuration .env (0.5j)

Configurer le bucket S3 de développement. Créer `.env.example` (toutes les variables documentées). Configurer `.env` local (AWS credentials, MongoDB URI, Keycloak secret, etc.). Vérifier que `.env` est dans `.gitignore`.

**Critère de done :** Upload test réussi via SDK AWS S3 vers le bucket dev.

---

### 0.A.4 — Keycloak Realm (0.5j)

Importer `realm-docai.json` : realm `docai`, 3 clients (`docai-backend`, `docai-frontend`, `docai-admin`), 5 rôles (`TENANT_ADMIN`, `ANALYST`, `VIEWER`, `FRAUD_REVIEWER`, `SYSTEM`), 5 utilisateurs de test, Protocol Mapper `tenant_id` dans les JWT.

**Critère de done :** Login `admin@acme-corp.test` / `Test1234!` réussi. JWT retourné contient le claim `tenant_id`.

---

### 0.A.5 — Seeding DEV + Validation Globale (0.5j)

Implémenter `SeedingService` (profil `seed` uniquement, jamais en production). Créer 3 tenants, 10 utilisateurs, documents exemples de chaque type. Seeding idempotent (exécuté 2× = résultat identique).

**Critère de done :** `curl /actuator/health` → UP. Connexion `admin@acme-corp.test` retourne un JWT valide.

---

## 0.B — Standards & Qualité (3 micro-tâches)

### 0.B.1 — Conventions de Nommage + Checkstyle (0.5j)

Configurer Checkstyle avec les conventions du projet (longueur méthode ≤ 20 lignes, paramètres ≤ 4, complexité cyclomatique ≤ 10). Documenter les conventions de nommage obligatoires (UseCase, Adapter, Controller, Consumer, Value Object, Aggregate). Configurer la convention de commits (Conventional Commits).

**Critère de done :** `./mvnw checkstyle:check` passe. Convention de commits documentée dans `CONTRIBUTING.md`.

---

### 0.B.2 — Seuils Qualité SonarCloud + 12 Règles ArchUnit (0.5j)

Configurer SonarCloud (organisation, projet, token). Quality Gate : couverture globale ≥ 80%, couverture domaine ≥ 90%, 0 bug, 0 vulnérabilité, duplication ≤ 3%. Implémenter `HexagonalArchitectureTest` avec les 12 règles ArchUnit (domaine pur Java, adapters indépendants, use cases dépendent uniquement du domaine, etc.). PIT Mutation Testing configuré (seuil 85% sur `docai-domain`).

**Critère de done :** SonarCloud analyse réussie. Les 12 règles ArchUnit passent. `./mvnw test -pl docai-domain` → coverage ≥ 90%.

---

### 0.B.3 — Feature Flags Unleash (1j)

Déployer Unleash (open source, auto-hébergé). Définir les 6 flags : `billing.enabled` (false), `fraud.v2.enabled` (false), `extraction.mistral.enabled` (false), `dashboard.search.enabled` (false), `notifications.inapp.enabled` (true), `maintenance.mode` (false). Implémenter `FeatureFlagPort` dans le domaine et `UnleashFeatureFlagAdapter`.

**Critère de done :** `billing.enabled = false` en DEV/STAGING vérifié. Kill switch `maintenance.mode` testé (active → toutes les soumissions bloquées).

---

## 0.C — CI/CD Pipeline (4 micro-tâches)

### 0.C.1 — GitHub Actions : Tests (1j)

Créer `01-ci.yml` avec 3 jobs distincts (ADR-008 — limite JVM 512m) : `unit-tests` (domain + application, sans Docker, 2–4 min), `integration-tests` (adapters avec TestContainers, 8–15 min), `bdd-tests` (Cucumber complet, 10–20 min). Configurer `MAVEN_OPTS=-Xmx512m`, TestContainers reuse.

**Critère de done :** Premier build GitHub Actions vert sur `develop`. Les 3 jobs s'exécutent en parallèle sans OOM sur 5 runs consécutifs.

---

### 0.C.2 — SonarCloud + Docker Multi-Stage + Trivy (1j)

Créer job `sonarcloud` dans `01-ci.yml` (après les 3 jobs tests). Créer `02-docker.yml` : build image multi-stage JRE 21 Alpine (3 stages : dependencies, build, runtime). Configurer scan Trivy (vulnérabilité CRITICAL → pipeline arrêté). Push vers GHCR.

**Critère de done :** Quality Gate SonarCloud bloque les PR. Image Docker < 300MB. Trivy scan vert (0 CRITICAL).

---

### 0.C.3 — Déploiement Staging + Production (1j)

Créer `03-deploy-staging.yml` (push sur `develop` → déploiement automatique, health check post-deploy, rollback si KO). Créer `04-deploy-production.yml` (tag `v*.*.*` → approbation manuelle requise dans 1h, zero-downtime RollingUpdate, rollback automatique). Configurer les GitHub Environments (`staging`, `production`) avec approbateurs.

**Critère de done :** Déploiement staging automatique déclenché sur push `develop`. Zero-downtime validé : déploiement pendant charge simulée → 0 requête en erreur.

---

### 0.C.4 — Terraform + Kubernetes + Health Checks + Documentation (2j)

**Jour 1 — Terraform + Kubernetes :**
Modules Terraform (S3 avec SSE-KMS + Lifecycle Rules, MongoDB Atlas, Keycloak realm, Kafka Cloud). Manifestes Kubernetes (`deployment.yaml` RollingUpdate maxUnavailable=0, `hpa.yaml` min 2 replicas, `configmap.yaml`, `ingress.yaml` TLS 1.3, `service.yaml`). Secrets via AWS Secrets Manager CSI Driver.

**Jour 2 — Health Checks + Documentation :**
Configurer Liveness Probe (`/actuator/health/liveness`) et Readiness Probe (`/actuator/health/readiness`). Implémenter `S3HealthIndicator` custom (HeadBucket, cache 30s). Créer `05-documentation.yml` (OpenAPI spec → GitHub Pages sur tag).

**Critère de done :** `terraform plan` valide en staging. Health checks configurés et testés (arrêt MongoDB → readiness KO, liveness OK). Documentation publiée sur GitHub Pages.

---

# PARTIE 2 — COMMONS

> **7 micro-tâches — ~2 semaines**
> Prérequis : Partie 1 validée.
> Critère de passage : tous les commons publiés, couverture ≥ 90% sur chacun.
> **Ordre strict : implémenter dans l'ordre 0.D.1 à 0.D.7.**

---

### 0.D.1 — commons-multitenancy (2j)

`TenantContext` (ThreadLocal, `set()`, `get()`, `clear()`). `TenantNotSetException`. `TenantJwtFilter` (extrait `tenant_id` du JWT Keycloak, injecte dans TenantContext). `IdentityProviderPort` (interface Keycloak : createUser, assignRole, revokeUser, authenticate, refreshToken, blacklistJwt). `TokenBlacklistPort` (Valkey : blacklist, isBlacklisted). `KeycloakIdentityAdapter` et `ValkeyTokenBlacklistAdapter`.

**Critère de done :** Couverture ≥ 90%. `TenantContext` thread-safe testé. Filtre JWT testé avec token valide et invalide.

---

### 0.D.2 — commons-api (1j)

`ApiResponse<T>` (enveloppe standard, `of()`, `paginated()`). `PageMetadata` (number, size, totalElements, totalPages, first, last). `ProblemDetail` (RFC 7807 : type, title, status, detail, instance, traceId, timestamp). `IdempotencyPort` (tryAcquire, getCachedResponse, cacheResponse). `GlobalExceptionHandler` (gestion centralisée de toutes les exceptions domaine → ProblemDetail).

**Critère de done :** Couverture ≥ 90%. ProblemDetail conforme RFC 7807 vérifié. GlobalExceptionHandler testé pour chaque type d'exception.

---

### 0.D.3 — commons-audit (1j)

`AuditEvent` (record immuable : id, tenantId, userId, action, resourceType, resourceId, metadata, occurredAt, ipAddress, isSupportAccess). `AuditPort` (record async, findByTenant, findByDocument). Annotation `@Audited` (action, resourceType). Aspect AOP `AuditAspect` (intercepte les méthodes annotées, construit AuditEvent, appelle AuditPort de façon asynchrone). `AuditMongoAdapter` (TTL index 5 ans).

**Critère de done :** Couverture ≥ 90%. `@Audited` testé : méthode annotée → AuditEntry créé en base avec tous les champs. Async vérifié (méthode ne bloque pas).

---

### 0.D.4 — commons-outbox (2j)

`OutboxMessage` (record : id, aggregateType, aggregateId, eventType, payload, tenantId, partitionKey, createdAt, status). `OutboxStatus` enum (PENDING, PUBLISHED, FAILED). `OutboxRepository` (save, findPending, markPublished, markFailed, deletePublishedOlderThan). `OutboxEventPublisher` (interface use case → outbox). `OutboxMongoAdapter` (implémentation MongoDB). `OutboxRelayScheduler` (@Scheduled 1s, batch 100, publication Kafka, markPublished atomique).

**Critère de done :** Couverture ≥ 90%. Transaction atomique testée (Document + OutboxMessage dans même ClientSession). Relay testé : message PENDING → publié sur Kafka → PUBLISHED.

---

### 0.D.5 — commons-quota (1j)

`QuotaStatus` enum (ALLOWED, QUOTA_WARNING_80, QUOTA_WARNING_95, QUOTA_EXCEEDED). `QuotaCheckResult` (status, currentUsage, limit, remaining, resetAt). `QuotaPort` (checkAndConsume atomique via script Lua, getCurrentUsage, reset). Annotation `@QuotaProtected` (amount, resource). Aspect AOP `QuotaAspect`. `ValkeyQuotaAdapter` (script Lua atomique INCR + GET en une opération — ADR-001).

**Critère de done :** Couverture ≥ 90%. Atomicité Lua testée (race condition : 100 requêtes simultanées → quota exactement respecté). Alertes 80% et 95% testées.

---

### 0.D.6 — commons-kafka (2j)

`ResilientKafkaConsumer<T>` (abstraite : `handle()` à implémenter, `processWithIdempotence()`, `sendToDlq()`, `isAlreadyProcessed()` via Valkey, `markAsProcessed()`). `KafkaConsumerContext` (tenantId, correlationId, traceId, attempt). `KafkaEventPublisher` (publishViaOutbox, clé = partitionKey ADR-002). `JitterTtl` (withJitter ±10%, fixed pour rate limiting et idempotence). Headers Kafka propagés (tenant-id, correlation-id, event-type, trace-id, schema-version).

**Critère de done :** Couverture ≥ 90%. Idempotence testée (event reçu 2× → handle() appelé 1× seulement). DLQ testé (3 échecs → event envoyé sur `docai.doc.dlq`). Jitter : 100 TTL consécutifs → 100 valeurs différentes.

---

### 0.D.7 — commons-testing (1j)

`AbstractIntegrationTest` (TestContainers : MongoDB + Kafka + Valkey + LocalStack S3, mode reuse activé). Test Data Builders pour chaque aggregate (`DocumentTestBuilder`, `TenantTestBuilder`, `FraudAnalysisTestBuilder`, etc.). WireMock stubs de base (OpenAI, Mistral, INSEE, BAN, RPPS, Keycloak). Helper `KafkaTestHelper` (publish event, await event, assert event).

**Critère de done :** `AbstractIntegrationTest` démarré en < 60s (reuse activé). Tous les builders fonctionnels. WireMock stubs documentés et réutilisables dans tous les modules.

---

# PARTIE 3 — FONDATIONS MÉTIER

> **20 micro-tâches — ~4 semaines**
> Prérequis : Partie 2 terminée (tous les commons disponibles).
> **Ordre strict : 0.1 → 0.2 → 0.3 → 0.4 → 0.5 → 0.6.**

---

## Phase 0.1 — Signup & Multi-Tenancy (3 micro-tâches)

### 0.1.A — Domaine + SignupTenantUseCase (1j)

Aggregate `Tenant` (tenantId, companyName, plan FREE, status PENDING_VERIFICATION, slugId, createdAt). Value Objects `TenantId`, `CompanyName`, `SlugId` (slug unique avec suffixe numérique en cas de collision). Domain Events `TenantCreated`, `EmailVerificationRequested`. Ports IN (`SignupTenantUseCase`, `VerifyEmailUseCase`, `ResendVerificationUseCase`). Ports OUT (`TenantRepositoryPort`, `IdentityProviderPort`, `EmailNotificationPort`, `InvitationTokenRepositoryPort`). Zéro Spring, zéro infrastructure.

**Critère de done :** Couverture domaine ≥ 90%. `SlugId` testé : collision → suffixe numérique `-2`, `-3`, etc.

---

### 0.1.B — Adapters OUT (1j)

`TenantMongoAdapter` (persistance Tenant, unicité slug). `KeycloakIdentityAdapter` — création utilisateur + attribution rôle `TENANT_ADMIN` via API Admin Keycloak (Resource Owner Password Credentials). `InvitationTokenMongoAdapter` (TTL index MongoDB 7 jours). Migrations Mongock V001 (`tenants` collection + indexes), V002 (`invitation_tokens` + TTL index).

**Critère de done :** Création tenant persistée en MongoDB. Utilisateur TENANT_ADMIN créé dans Keycloak avec rôle attribué. TTL index 7j vérifié.

---

### 0.1.C — Adapter IN REST + Email + Tests BDD (1j)

`AuthController` : `POST /v1/public/signup`, `GET /v1/public/verify-email?token=xxx`, `POST /v1/public/resend-verification`. `AmazonSesEmailAdapter` : envoi email bienvenue (template `welcome.html`) + email vérification (template `email-verification.html`) en mode sandbox SES. Scénarios BDD : inscription → email envoyé → vérification → connexion possible, email déjà existant → HTTP 409, slug collision → suffixe auto.

**Critère de done :** Flow complet testé de bout en bout. Email envoyé via WireMock SES. Rôle `TENANT_ADMIN` attribué automatiquement.

---

## Phase 0.2 — Login, Logout & Session (3 micro-tâches)

### 0.2.A — Domaine + LoginUseCase + LogoutUseCase (1j)

Value Objects `JwtBlacklist` (tokenId, expiresAt), `PasswordResetToken` (token UUID, email, expiresAt, used). Domain Events `UserLoggedIn`, `UserLoggedOut`, `PasswordResetRequested`. Ports IN (`LoginUseCase`, `LogoutUseCase`). Ports OUT (`IdentityProviderPort` — commons-multitenancy, `TokenBlacklistPort` — commons-multitenancy). Règle brute-force : 5 tentatives → blocage 15 min (Valkey). Message erreur identique pour email inconnu et mauvais MDP (sécurité anti-énumération).

**Critère de done :** Règle brute-force testée (5 tentatives → HTTP 429, TTL 15 min Valkey vérifié). Message erreur 401 identique dans tous les cas d'échec.

---

### 0.2.B — RefreshToken + ForgotPassword + ResetPassword (1j)

`RefreshTokenUseCase` (rotation à chaque renouvellement via Keycloak, ancien refresh_token invalidé). `ForgotPasswordUseCase` (token UUID TTL 1h, réponse HTTP 200 même si email inconnu). `ResetPasswordUseCase` (token usage unique → HTTP 410 si déjà utilisé). Adapters : `ValkeyTokenBlacklistAdapter` (blacklist JWT avec TTL = durée restante du JWT), `PasswordResetTokenMongoAdapter`. Migration Mongock V003 (`password_reset_tokens` + TTL index 1h).

**Critère de done :** Rotation refresh token testée (ancien token → HTTP 401 après rotation). Token réinitialisation usage unique vérifié (2ème utilisation → HTTP 410). JWT blacklisté → HTTP 401 sur requête suivante.

---

### 0.2.C — Adapter IN REST + Email + Tests BDD (1j)

5 endpoints : `POST /v1/public/auth/login`, `POST /v1/auth/logout`, `POST /v1/auth/refresh`, `POST /v1/public/auth/forgot-password`, `POST /v1/public/auth/reset-password`. Emails SES : réinitialisation MDP (lien 1h), confirmation changement (date/heure/IP), notification blocage compte. AuditEntry sur chaque login et logout (userId, IP, timestamp). Scénarios BDD complets.

**Critère de done :** Tous les scénarios BDD passent. AuditEntry créé pour login et logout. Email envoyé même si email inconnu (vérifié via WireMock SES).

---

## Phase 0.3 — Gestion Équipe (2 micro-tâches)

### 0.3.A — Domaine + InviteUser + ActivateUser (1j)

Value Object `InvitationToken` (UUID, TTL 7j, usage unique). Ports IN (`InviteUserUseCase`, `ActivateUserUseCase`). Règles : seul TENANT_ADMIN invite, dans son propre tenant uniquement, rôles assignables = ANALYST/VIEWER/FRAUD_REVIEWER (jamais TENANT_ADMIN), invitation valable 7j. `KeycloakIdentityAdapter` : création utilisateur sans MDP + attribution rôle. `InvitationTokenMongoAdapter` : TTL index 7j. AuditEntry sur chaque invitation.

**Critère de done :** Règle isolation tenant testée (TENANT_ADMIN ne peut pas inviter dans un autre tenant → HTTP 403). Token d'invitation créé avec TTL 7j. AuditEntry créé avec tous les champs.

---

### 0.3.B — RevokeUser + REST + Email + Tests BDD (1j)

`RevokeUserUseCase` (invalidation JWT Keycloak immédiate via API Admin, blacklist JWT actif dans Valkey). Adapter REST : `POST /v1/team/invite`, `GET /v1/team/users`, `PUT /v1/team/users/{userId}/role`, `DELETE /v1/team/users/{userId}`, `GET /v1/public/accept-invitation?token=xxx`. Emails : invitation (lien 7j + tenant + rôle), confirmation activation, notification révocation. Scénarios BDD : invitation → activation → connexion, expiration 8j → HTTP 410, révocation → JWT précédent HTTP 401 immédiat.

**Critère de done :** Révocation immédiate testée (bob connecté → alice révoque → prochaine requête bob HTTP 401). Token expiré rejeté. Flow complet invitation → activation → connexion validé.

---

## Phase 0.4 — Profil Utilisateur & 2FA (3 micro-tâches)

### 0.4.A — Domaine + ChangePassword + UpdateProfile (1j)

Aggregate `UserProfile` (userId, displayName, preferredLanguage). Ports IN (`UpdateProfileUseCase`, `ChangePasswordUseCase`). Règle : changement MDP invalide toutes les sessions actives (logout global Keycloak via Admin API). `KeycloakIdentityAdapter` : modification profil + changement MDP. AuditEntry sur chaque modification sensible. Email de confirmation de changement MDP (date/heure/IP).

**Critère de done :** Changement MDP invalide toutes les sessions actives (testé : JWT précédent → HTTP 401 après changement). AuditEntry créé. Email de confirmation envoyé.

---

### 0.4.B — Activation 2FA + TOTP (1j)

`Activate2FAUseCase` (génération secret TOTP, retour QR code URI). `Verify2FAUseCase` (validation code TOTP 6 chiffres, fenêtre ±30s). `Disable2FAUseCase` (MDP requis pour désactiver). Adapter Keycloak OTP. Historique connexions (10 dernières sessions : IP, date, user-agent, statut). Stocké dans `user_sessions` MongoDB, TTL index 90j.

**Critère de done :** QR code URI valide (scannable par Google Authenticator). Code TOTP valide → accès. Code invalide → HTTP 401. Historique 10 dernières sessions accessible.

---

### 0.4.C — Adapter REST + Suppression Compte + Tests BDD (1j)

Endpoints profil : `GET /v1/profile`, `PUT /v1/profile`, `PUT /v1/profile/password`, `POST /v1/profile/2fa/activate`, `POST /v1/profile/2fa/verify`, `DELETE /v1/profile/2fa`, `GET /v1/profile/sessions`. `DELETE /v1/profile/account` (MDP + texte confirmation "SUPPRIMER MON COMPTE", anonymisation immédiate dans Keycloak + MongoDB, vérification dernier TENANT_ADMIN → HTTP 409). Scénarios BDD.

**Critère de done :** Suppression compte : anonymisation immédiate dans Keycloak, données personnelles → "Utilisateur supprimé" dans MongoDB, documents du tenant conservés. Dernier TENANT_ADMIN ne peut pas supprimer son compte.

---

## Phase 0.5 — RGPD & Rétention (4 micro-tâches)

### 0.5.A — Domaine + ConfigureRetention (1j)

Value Objects `RetentionPolicy` (retentionDays 30–365, effectiveFrom). Aggregates `DeletionReport` (tenantId, requestedAt, status, itemsDeleted, completedAt), `DataExport` (tenantId, requestedAt, s3Key, expiresAt, status). Domain Events `DocumentRetentionExpired`, `DataErasureRequested`, `DataExportReady`. Ports IN (`ConfigureRetentionPolicyUseCase`, `RunRetentionCleanupUseCase`, `RequestDataErasureUseCase`, `RequestDataExportUseCase`). Ports OUT (`DataErasurePort`, `DataExportPort`, `RetentionPolicyRepositoryPort`, `RgpdAuditPort`).

**Critère de done :** Validation retentionDays (< 30 ou > 365 → exception domaine). AuditEntries RGPD configurées avec TTL 5 ans (obligation légale).

---

### 0.5.B — Job Rétention Quotidien (1j)

`RetentionCleanupScheduler` (@Scheduled 2h00 UTC, toujours exécuté même si 0 documents expirés). `S3DataErasureAdapter` (deleteObject S3 pour chaque fichier). `MongoDataErasureAdapter` (suppression ExtractionResults + anonymisation AuditEntries : userId → "[ANONYMIZED]", contenu → effacé). AuditEntry RGPD créé pour chaque document supprimé (documentId anonymisé, tenantId, date, raison). Migrations Mongock V004 (`retention_policies` + `deletion_reports`).

**Critère de done :** Job testé avec documents expirés réels (TTL dépassé). 100% des documents expirés supprimés dans les 24h (NFR-RGP-001). Fichier S3 supprimé + ExtractionResult MongoDB supprimé + AuditEntry RGPD créé.

---

### 0.5.C — Effacement & Export Données (1j)

`RequestDataErasureUseCase` (HTTP 202, traitement asynchrone, suppression tous fichiers S3 + données MongoDB du tenant, conservation AuditEntries anonymisées, email confirmation avec rapport). `RequestDataExportUseCase` (génération JSON complet du tenant → upload S3, URL présignée 24h, email avec lien). `DataExportGeneratorAdapter` (sérialisation JSON, upload S3 multipart). Migration Mongock V005 (`data_exports`).

**Critère de done :** Effacement : tous les fichiers S3 supprimés, ExtractionResults MongoDB supprimés, AuditEntries anonymisées (pas supprimées). Export : JSON généré, URL présignée 24h fonctionnelle, email envoyé. Traitement asynchrone → HTTP 202 immédiat.

---

### 0.5.D — Chiffrement KMS + REST + Tests BDD (1j)

Field Level Encryption MongoDB sur champs PII dans `extraction_results` (`nom`, `prenom`, `dateNaissance`, `numeroDocument`, `IBAN`) via AWS KMS. Chiffrement SSE-KMS S3 activé sur le bucket (vérification AWS Console). 6 endpoints REST : `GET/PUT /v1/rgpd/retention-policy`, `DELETE /v1/rgpd/data`, `GET /v1/rgpd/deletion-reports`, `POST /v1/rgpd/export`, `GET /v1/rgpd/exports`. `DELETE /v1/profile/account` (effacement individuel). Scénarios BDD RGPD complets.

**Critère de done :** Lecture directe MongoDB sans l'application → champs PII illisibles (chiffrés). SSE-KMS S3 vérifié. Test droit à l'effacement bout en bout. Test export données bout en bout. Données stockées en eu-west-3 (NFR-RGP-004).

---

## Phase 0.6 — Billing & Abonnements (5 micro-tâches)

### 0.6.A — Domaine (1j)

Aggregate `Subscription` (tenantId, plan, status, stripeCustomerId, stripeSubscriptionId, currentPeriodStart, currentPeriodEnd, trialEndsAt). Value Object `UsageCounter` (docsProcessed, docsIncluded, overageCount, periodStart). Enums `Plan` (FREE, STARTER, PRO, ENTERPRISE), `SubscriptionStatus` (TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELED, EXPIRED). Domain Events `SubscriptionActivated`, `PaymentFailed`, `QuotaThresholdReached`. Ports IN (`ActivateSubscriptionUseCase`, `GetUsageUseCase`, `HandleStripeWebhookUseCase`, `ChangeSubscriptionPlanUseCase`). Ports OUT (`PaymentGatewayPort`, `SubscriptionRepositoryPort`, `UsageCounterPort`, `EmailNotificationPort`). Zéro Stripe dans le domaine.

**Critère de done :** Couverture domaine ≥ 90%. Transitions de statut testées (TRIAL → ACTIVE, ACTIVE → PAST_DUE, PAST_DUE → ACTIVE, ACTIVE → CANCELED). Invariants domaine : plan FREE = 50 docs max, TRIAL = 30 jours.

---

### 0.6.B — Stripe Checkout + Customer Portal (1j)

`ActivateSubscriptionUseCase` (création Customer Stripe + Checkout Session, redirect vers Stripe). `ChangeSubscriptionPlanUseCase` (upgrade/downgrade via Customer Portal Stripe). `StripePaymentAdapter` (SDK Stripe, mode TEST obligatoire en DEV/STAGING, mode LIVE uniquement en PROD — Feature Flag `BILLING_ENABLED`). `SubscriptionMongoAdapter`. Migration Mongock V006 (`subscriptions`).

**Critère de done :** Stripe Checkout testé en mode TEST (paiement simulé → subscription créée). Customer Portal accessible. `BILLING_ENABLED = false` → aucune restriction, aucun appel Stripe.

---

### 0.6.C — Webhooks Stripe (1j)

`HandleStripeWebhookUseCase` : `invoice.paid` → ACTIVE, `invoice.payment_failed` → PAST_DUE (lecture seule uniquement, plus de soumissions), `customer.subscription.deleted` → CANCELED (données conservées 90j). Signature webhook Stripe vérifiée sur 100% des events (rejet si signature invalide → log ERROR + HTTP 400). Idempotence : event Stripe reçu 2× → traité 1× seulement (Valkey dedup, clé = Stripe event ID, TTL 24h). Emails billing (relance paiement échoué, confirmation régularisation, confirmation résiliation).

**Critère de done :** Idempotence testée (même event 2× → traité 1×). Signature invalide → rejetée (WireMock Stripe). Cycle PAST_DUE → régularisation → ACTIVE testé. Email relance envoyé.

---

### 0.6.D — Compteurs Quota + Alertes (1j)

`ValkeyUsageCounterAdapter` (script Lua atomique : `INCR` + `GET` en une seule opération — ADR-001, reset mensuel automatique le 1er du mois). `GetUsageUseCase` (usage temps réel depuis Valkey). Alertes automatiques : 80% → email TENANT_ADMIN + notification in-app, 95% → email urgence, dépassement → compteur overage incrémenté (docs facturés à la fin du mois). Métrique `docai_quota_usage{tenant, percent}`.

**Critère de done :** Atomicité Lua testée (race condition : 1000 requêtes simultanées → quota exactement respecté). Alertes 80% et 95% déclenchées au bon seuil. Overage calculé correctement. Endpoint usage temps réel < 100ms (NFR-BIL-003).

---

### 0.6.E — Adapter REST + Cycle de Vie + Tests BDD (1j)

6 endpoints : `GET /v1/billing/plans` (public), `POST /v1/billing/checkout` (TENANT_ADMIN), `GET /v1/billing/portal` (TENANT_ADMIN), `GET /v1/billing/usage` (TENANT_ADMIN), `GET /v1/billing/subscription` (TENANT_ADMIN), `POST /v1/billing/webhooks/stripe` (public, signé Stripe). Scénarios BDD complets : FREE → STARTER, overage facturé, ACTIVE → PAST_DUE → ACTIVE (régularisation), ACTIVE → CANCELED (données 90j). Emails transactionnels billing (activation, quota 80%/95%, facture, paiement échoué, résiliation).

**Critère de done :** Cycle de vie complet testé (TRIAL → ACTIVE → PAST_DUE → ACTIVE → CANCELED). Overage calculé et persisté correctement. Customer Portal Stripe accessible. Endpoint usage temps réel < 100ms.

---

# PARTIE 4 — PIPELINE DE TRAITEMENT

> **70 micro-tâches — ~14 semaines**
> Prérequis : Partie 3 validée.
> **Ordre strict : Module 1 → Module 2 → Module 3 → Module 4.**

---

## Module 1 — Reconnaissance de Documents

### Phase 1.1 — Upload & Validation (10 micro-tâches)

#### 1.1.A — Domaine (1j)

Aggregate `Document` (documentId, tenantId, fileName, mimeType, s3Key, status, contentHash, idempotencyKey, uploadedAt). Value Objects `DocumentId` (UUID v4), `ContentHash` (SHA-256), `DocumentStatus` (PENDING, CLASSIFIED, EXTRACTED, ANALYZED, COMPLETED, FAILED, NEEDS_REVIEW). Domain Event `DocumentUploaded`. Ports IN (`SubmitDocumentUseCase`, `GetDocumentUseCase`, `ListDocumentsUseCase`). Ports OUT (`StoragePort`, `DocumentRepositoryPort`, `OutboxEventPublisher`, `QuotaPort`). Zéro infrastructure.

**Critère de done :** Couverture domaine ≥ 90%. State Machine testée : transitions valides et invalides. `ContentHash` : même fichier = même hash, fichiers différents = hash différents.

---

#### 1.1.B — Adapter OUT S3 (1j)

`AwsS3StorageAdapter` : upload multipart, clé `{tenantId}/{year}/{month}/{documentId}/{filename}`. Calcul SHA-256 en streaming pendant l'upload (pas de double lecture). URL présignée 1h. Gestion erreurs S3 (retry 3×, timeout 30s). Testé avec LocalStack TestContainers.

**Critère de done :** Upload 20MB testé. Clé S3 correctement formée. URL présignée valide 1h. Erreur S3 → exception domaine typée (pas d'exception AWS exposée).

---

#### 1.1.C — Adapter OUT MongoDB (1j)

`DocumentMongoAdapter` : persistance aggregate Document. Migrations Mongock V007 (collection `documents` + indexes : `{tenantId, status, createdAt}`, `{contentHash, tenantId}` pour déduplication, `{tenantId, uploadedAt}` pour listing).

**Critère de done :** CRUD Document testé. Index de déduplication par contentHash fonctionnel. EXPLAIN PLAN sur les requêtes de listing validé (< 50ms).

---

#### 1.1.D — Adapter OUT Outbox (1j)

Transaction atomique MongoDB : `Document` (état PENDING) + `OutboxMessage` (event `DocumentUploaded`, partitionKey = documentId) dans le même `ClientSession`. Si S3 OK mais transaction MongoDB échoue → rollback complet (fichier S3 orphelin nettoyé par job). Aucun event perdu garanti.

**Critère de done :** Transaction testée (simulation échec MongoDB après S3 → Document absent + OutboxMessage absent). Event `DocumentUploaded` correctement formé (tous les champs Avro présents).

---

#### 1.1.E — Adapter OUT Quota (1j)

`ValkeyQuotaAdapter` (commons-quota) : vérification quota mensuel avant stockage S3. `@QuotaProtected` sur `SubmitDocumentUseCase`. Script Lua atomique. Réponse avec `remaining`, `limit`, `resetAt`. Rejet HTTP 429 avec `ProblemDetail` incluant la date de renouvellement.

**Critère de done :** Quota dépassé → HTTP 429 avec date de renouvellement correcte. Atomicité testée (race condition : dernier document disponible → 1 seul passe). Compteur incrémenté uniquement si upload réussi.

---

#### 1.1.F — SubmitDocumentUseCase (1j)

Orchestration complète en ordre strict : vérification idempotence (`X-Idempotency-Key` → Valkey SETNX 24h) → validation format (PDF, PNG, JPEG, TIFF, WEBP) + taille (≤ 20MB) → vérification quota → calcul SHA-256 → upload S3 → persist Document + outbox (transaction atomique) → retour `documentId + PENDING`. Si doublon SHA-256 dans le tenant → retourner documentId existant (pas de re-upload).

**Critère de done :** Orchestration complète testée (toutes les étapes dans l'ordre). Idempotence : 2ème appel même clé → HTTP 200 + même documentId. Doublon SHA-256 → documentId existant retourné.

---

#### 1.1.G — Read Model CQRS Documents (1j)

`GetDocumentUseCase` (isolation tenant stricte : tenantId du JWT ≠ tenantId document → HTTP 403). `ListDocumentsUseCase` (Read Model `document_summary_views`, filtres : status, type, riskLevel, dateFrom, dateTo — pagination commons-api). Migration Mongock V008 (collection `document_summary_views` + partial indexes sur statuts actifs ADR-010).

**Critère de done :** Isolation tenant testée (tenant A ne voit pas documents tenant B → HTTP 403). EXPLAIN PLAN sur listing < 100ms avec 10 000 documents. Pagination correcte (first, last, totalPages).

---

#### 1.1.H — Adapter IN REST (1j)

`DocumentController` : `POST /v1/documents` (multipart/form-data, header `X-Idempotency-Key`), `GET /v1/documents/{id}`, `GET /v1/documents` (paramètres pagination + filtres). `GlobalExceptionHandler` : HTTP 400 (format invalide, code `DOC-001`), HTTP 413 (taille dépassée, code `DOC-002`), HTTP 415 (type MIME non supporté, code `DOC-003`), HTTP 429 (quota, code `QUOTA-001`), HTTP 409 (doublon hash). Swagger annotations sur tous les endpoints.

**Critère de done :** Tous les codes erreur testés avec le bon format ProblemDetail RFC 7807. Multipart testé jusqu'à 20MB. Header `X-Idempotency-Key` absent → erreur explicite.

---

#### 1.1.I — Schéma Avro + Outbox Relay (1j)

Schéma Avro `DocumentUploadedEvent` enregistré dans Apicurio Registry (validation backward compatibility). `OutboxRelayScheduler` (commons-outbox) : @Scheduled 1s, batch 100 messages PENDING → publication Kafka topic `docai.doc.uploaded` (clé = documentId — ADR-002), headers obligatoires (tenant-id, correlation-id, trace-id, schema-version), markPublished atomique. Job nettoyage : suppression OutboxMessages PUBLISHED > 24h.

**Critère de done :** Schéma visible dans Apicurio UI. Event publié sur Kafka avec clé = documentId (vérifié dans Kafka UI). Headers présents sur chaque message. Relay testé : latence PENDING → PUBLISHED < 2s.

---

#### 1.1.J — Tests BDD Bout en Bout (1j)

Scénarios complets : upload PDF 2MB → HTTP 201 + documentId + event Kafka publié sur `docai.doc.uploaded`, idempotence (même clé 2× → HTTP 200 + même documentId), quota dépassé → HTTP 429 + date renouvellement, format `.docx` → HTTP 400 code `DOC-001`, fichier > 20MB → HTTP 413 code `DOC-002`, doublon SHA-256 → documentId existant, isolation tenant (tenant B ne voit pas documents tenant A).

**Critère de done :** Tous les scénarios BDD passent avec TestContainers. Event Kafka publié vérifié avec `KafkaTestHelper`. Coverage module ≥ 80%.

---

### Phase 1.2 — Classification (10 micro-tâches)

#### 1.2.A — Domaine (1j)

Aggregate `ClassificationResult` (documentId, tenantId, documentType, confidenceScore, modelVersion, lowConfidence, needsReview, classifiedAt). Value Objects `DocumentType` (FACTURE, RIB, CNI, ORDONNANCE, BULLETIN_SALAIRE, CONTRAT, INCONNU), `ConfidenceScore` (0.0–1.0, validation constructeur). Domain Event `DocumentClassified`. Ports IN (`ClassifyDocumentUseCase`, `OverrideClassificationUseCase`, `GetClassificationUseCase`). Ports OUT (`ClassificationResultRepositoryPort`, `ClassificationModelPort`).

**Critère de done :** `ConfidenceScore` < 0.0 ou > 1.0 → exception domaine. Règle NEEDS_REVIEW si confidence < 0.7 testée. Tous les types documentaires ont une valeur enum correspondante.

---

#### 1.2.B — Adapter OUT ClassificationRepository (1j)

`ClassificationResultMongoAdapter`. Migration Mongock V009 (collection `classification_results` + indexes : `{documentId}` unique, `{tenantId, documentType, confidenceScore}`, `{tenantId, needsReview, createdAt}` pour la queue de révision).

**Critère de done :** CRUD ClassificationResult testé. Index needsReview fonctionnel. EXPLAIN PLAN sur requête queue révision < 50ms.

---

#### 1.2.C — Analyseur Heuristiques Locales (1j)

`HeuristicClassificationAdapter` (implémente `ClassificationModelPort`) : détection par mots-clés spécifiques à chaque type (FACTURE : SIRET, montant HT, TVA ; RIB : IBAN, BIC, domiciliation ; CNI : carte nationale, lieu de naissance ; ORDONNANCE : ordonnance, RPPS, médicament ; BULLETIN_SALAIRE : salaire brut, cotisations, net à payer). Score de confiance calculé selon nombre de mots-clés détectés. Détection langue (FR uniquement supporté).

**Critère de done :** Chaque type testé avec un document représentatif (mocks WireMock). INCONNU retourné si aucun type détecté avec confidence > 0.3. Testé sans accès réseau.

---

#### 1.2.D — ClassifyDocumentUseCase (1j)

Orchestration : récupère document depuis S3 (streaming, pas en mémoire complète) → heuristiques → confidence score → si < 0.7 : `needsReview = true` + état Document `NEEDS_REVIEW`. Publie `DocumentClassified` via outbox (partitionKey = documentId — ADR-002). Met à jour état Document dans MongoDB. AuditEntry sur chaque classification. Métrique `docai_classification_confidence_score{doc_type}` histogram.

**Critère de done :** Seuil 0.7 testé (confidence 0.69 → NEEDS_REVIEW, confidence 0.70 → CLASSIFIED). Event `DocumentClassified` publié sur Kafka. État Document mis à jour dans MongoDB.

---

#### 1.2.E — Adapter IN Kafka ClassificationConsumer (1j)

`ClassificationKafkaConsumer` étend `ResilientKafkaConsumer` (commons-kafka). Consomme `docai.doc.uploaded`, group-id `docai.recognition.classification.group`. Idempotence Valkey (clé `idempotency:{topic}:{partition}:{offset}`, TTL 24h fixe). DLQ après 3 échecs (`docai.doc.dlq`). Extraction headers Kafka (tenant-id → TenantContext, correlation-id, trace-id → MDC pour logs).

**Critère de done :** Consumer démarre et consomme correctement. Idempotence : event reçu 2× → `handle()` appelé 1× seulement. Après 3 échecs → event dans `docai.doc.dlq`. TenantContext correctement propagé.

---

#### 1.2.F — Gestion NEEDS_REVIEW (1j)

Queue manuelle `document_review_queue` (read model : documents avec `needsReview = true`, triés par date ASC). `OverrideClassificationUseCase` (ANALYST ou TENANT_ADMIN : forcer le type manuellement + justification obligatoire). AuditEntry immuable sur chaque override. Email optionnel à l'ANALYST qui a uploadé le document (notification que sa classification nécessite révision).

**Critère de done :** Queue paginée accessible aux ANALYST et TENANT_ADMIN. Override testé : type forcé persisté + AuditEntry créé. Isolation tenant : ANALYST voit uniquement les documents de son tenant.

---

#### 1.2.G — Schéma Avro + Publication (1j)

Schéma Avro `DocumentClassifiedEvent` (eventId, documentId, tenantId, documentType, confidenceScore, modelVersion, lowConfidence, needsReview, occurredAt). Enregistrement Apicurio Registry. Publication topic `docai.doc.classified` via OutboxKafkaProducer (clé = documentId).

**Critère de done :** Schéma enregistré et visible dans Apicurio UI. Compatibilité backward vérifiée. Event publié sur `docai.doc.classified` avec clé = documentId (vérifié Kafka UI).

---

#### 1.2.H — Endpoints REST Classification (1j)

`GET /v1/documents/{id}/classification` (résultat classification avec confidenceScore, documentType, modelVersion), `GET /v1/documents/review-queue` (ANALYST : liste NEEDS_REVIEW paginée, filtres type/date), `POST /v1/documents/{id}/classification/override` (ANALYST/TENANT_ADMIN : nouveau type + justification). Swagger annotations complètes.

**Critère de done :** Tous les endpoints testés (auth, isolation tenant, pagination). Override : VIEWER → HTTP 403. Classification non trouvée → HTTP 404.

---

#### 1.2.I — Métriques Classification (1j)

`docai_classification_confidence_score{doc_type}` histogram (distribution des scores par type). `docai_classification_needs_review_total{doc_type}` counter. `docai_classification_duration_seconds{doc_type}` histogram. `docai_classification_override_total` counter. Alerte Grafana : si needs_review rate > 20% sur 1h → Slack (modèle de classification à revoir).

**Critère de done :** Métriques exposées sur `/actuator/prometheus`. Alerte Grafana configurée et testée (simulation taux élevé → notification Slack).

---

#### 1.2.J — Tests BDD (1j)

Scénarios : FACTURE → confidence 0.92 → CLASSIFIED, document ambigü → confidence 0.45 → NEEDS_REVIEW, consumer idempotent (event reçu 2× → traité 1×), 3 échecs consumer → DLQ, override ANALYST → nouveau type persisté + AuditEntry, VIEWER tente override → HTTP 403, isolation tenant queue révision.

**Critère de done :** Tous les scénarios BDD passent. Event Kafka `DocumentClassified` publié sur le bon topic. Coverage module ≥ 80%.

---

## Module 2 — Extraction d'Informations

### Phase 2.1 — OCR & LLM (10 micro-tâches)

#### 2.1.A — Domaine (1j)

Aggregate `ExtractionResult` (documentId, tenantId, extractionMethod, globalScore, fields[], rawOcrTextS3Key — pas rawOcrText en base ADR-004, extractedAt). Value Object `ExtractedField` (fieldName, value, confidenceScore 0.0–1.0). `ExtractionStatus` enum. Domain Event `ExtractionCompleted`. Ports IN (`ExtractDocumentUseCase`). Ports OUT (`OcrPort`, `LlmPort`, `ExtractionCachePort`, `StoragePort`, `ExtractionResultRepositoryPort`).

**Critère de done :** Invariant ADR-004 : rawOcrText absent du domaine (uniquement rawOcrTextS3Key). Score global = moyenne pondérée champs obligatoires uniquement. `ExtractedField` avec value null → confidenceScore = 0.0.

---

#### 2.1.B — Adapter OUT PDFBox (1j)

`PdfBoxOcrAdapter` (implémente `OcrPort`) : extraction texte natif PDF (Apache PDFBox 3.x, sans OCR si PDF texte). Détection si PDF texte (PDDocument.getPage(0).extractText() non vide) ou scanné. Si scanné → délègue à `Tess4JOcrAdapter`. Stocke rawOcrText dans S3 (`{tenantId}/ocr/{documentId}/raw-text.txt`), retourne `rawOcrTextS3Key`. Streaming : ne charge pas le PDF entier en mémoire.

**Critère de done :** PDF texte natif extrait < 5s. PDF scanné détecté et délégué. rawOcrText stocké dans S3 (jamais en MongoDB). Testé avec PDFs réels de 1, 10, 50 pages.

---

#### 2.1.C — Adapter OUT Tess4J (1j)

`Tess4JOcrAdapter` (implémente `OcrPort`) : OCR images (PNG, JPEG, TIFF, WEBP) + PDFs scannés via Tess4J 5.x. Langue FR configurée (`tessdata/fra.traineddata`). Pré-processing : augmentation contraste, correction rotation automatique. Bulkhead Resilience4j : 10 threads max, timeout 60s. Métriques `docai_ocr_duration_seconds` histogram.

**Critère de done :** OCR image PNG testée (document réel ou WireMock). Bulkhead : 11ème appel simultané → mis en attente ou rejeté proprement. Résultat stocké dans S3.

---

#### 2.1.D — Adapter OUT OpenAI (1j)

`OpenAiLlmAdapter` (implémente `LlmPort`) : prompts spécifiques par type (FACTURE, RIB, CNI, ORDONNANCE, BULLETIN_SALAIRE — voir speckit), temperature 0.0, `response_format: json_object`. Resilience4j : CircuitBreaker (seuil 50% / 10 calls), Retry 3× backoff exponentiel, Bulkhead 20 threads max, timeout 30s. WireMock stubs pour DEV.

**Critère de done :** Prompt FACTURE testé avec texte OCR exemple → JSON structuré retourné. Circuit Breaker OPEN testé (simulation timeouts successifs). Retry testé (1er appel timeout → 2ème OK → résultat retourné).

---

#### 2.1.E — Adapter OUT Mistral (1j)

`MistralLlmAdapter` (implémente `LlmPort`) : provider alternatif, mêmes prompts qu'OpenAI, Feature Flag `extraction.mistral.enabled`. Swap transparent : si flag activé → Mistral utilisé, sinon → OpenAI. Mêmes mécanismes Resilience4j. WireMock stubs Mistral.

**Critère de done :** Swap OpenAI ↔ Mistral via Feature Flag testé. Comportement identique (mêmes prompts, même format JSON). WireMock stubs Mistral fonctionnels.

---

#### 2.1.F — Adapter OUT Cache Valkey (1j)

`ValkeyExtractionCacheAdapter` (implémente `ExtractionCachePort`) : clé = SHA-256 du fichier (pas du documentId), TTL 24h ± 30min jitter (ADR-003, `JitterTtl.withJitter(Duration.ofHours(24))`). Cache hit → skip OCR + LLM entièrement. Métrique `docai_cache_hit_total{module="extraction"}` counter.

**Critère de done :** Jitter : 100 mises en cache → 100 TTL différents (aucune valeur identique). Cache hit : 0 appel OCR et LLM (vérifié avec WireMock 0 calls). Cache miss → OCR + LLM exécutés.

---

#### 2.1.G — ExtractDocumentUseCase (1j)

Orchestration : cache hit → retour immédiat. Cache miss → détection type PDF (PDFBox ou Tess4J) → extraction LLM avec prompt du type classifié → calcul confidenceScore par champ → score global → cache → persist ExtractionResult + rawOcrTextS3Key. Circuit Breaker LLM OPEN → fallback OCR basique sans LLM → état `NEEDS_REVIEW` + signal `LLM_UNAVAILABLE`. Publication `ExtractionCompleted` via outbox.

**Critère de done :** Cache hit < 200ms. Circuit Breaker LLM OPEN → fallback correct → NEEDS_REVIEW. Score global = moyenne pondérée champs obligatoires uniquement.

---

#### 2.1.H — Adapter IN Kafka ExtractionConsumer (1j)

`ExtractionKafkaConsumer` étend `ResilientKafkaConsumer`. Consomme `docai.doc.classified`, group-id `docai.extraction.extraction.group`. Idempotence Valkey (TTL 24h fixe). DLQ après 3 échecs. Extraction tenant-id + correlation-id + trace-id depuis headers Kafka → propagation MDC logs + TenantContext.

**Critère de done :** Consumer démarre et traite les events du topic `docai.doc.classified`. Idempotence testée. DLQ après 3 échecs testée. Trace-id propagé dans les logs (vérifié avec Grafana Tempo).

---

#### 2.1.I — ExtractionResultMongoAdapter + Migrations (1j)

`ExtractionResultMongoAdapter` : persistance avec Field Level Encryption sur champs PII (nom, prenom, dateNaissance, numeroDocument, IBAN) via AWS KMS. Migration Mongock V010 (collection `extraction_results` + indexes : `{documentId}` unique, `{tenantId, extractedAt}`, `{tenantId, globalScore}`). Publication `ExtractionCompleted` → topic `docai.doc.extracted` (schéma Avro `DocumentExtractedEvent`).

**Critère de done :** Lecture directe MongoDB → champs PII illisibles (chiffrés). Lecture via application → champs PII déchiffrés. Schéma Avro `DocumentExtractedEvent` enregistré dans Apicurio.

---

#### 2.1.J — Tests BDD (1j)

Scénarios : cache hit → 0 appel LLM (WireMock 0 calls vérifiés), Circuit Breaker OPEN → NEEDS_REVIEW, PDF texte natif < 10s P95, image scannée < 25s P95, 100 TTL cache différents (jitter ADR-003), WireMock OpenAI timeout → retry → fallback, Field Level Encryption : lecture directe MongoDB → PII illisibles.

**Critère de done :** Tous les scénarios BDD passent. Coverage module ≥ 80%. NFR-EXT-001 et NFR-EXT-002 validés avec tests de performance.

---

### Phase 2.2 — Validation Métier & APIs Externes (10 micro-tâches)

#### 2.2.A — Domaine (1j)

Value Object `ValidationResult` (field, status VALID/INVALID/UNVERIFIED, source LOCAL/INSEE/BAN/RPPS, signalWeight). Aggregate `ValidationReport` (documentId, tenantId, results[], globalStatus, validatedAt). Domain Event `ValidationCompleted`. Ports IN (`ValidateExtractionUseCase`). Ports OUT (`SiretValidatorPort`, `IbanValidatorPort`, `AddressValidatorPort`, `RppsValidatorPort`, `ValidationResultRepositoryPort`). Règle fondamentale : validations locales TOUJOURS exécutées en premier.

**Critère de done :** Couverture domaine ≥ 90%. Règle fail-open testée (API externe indisponible + cache vide → UNVERIFIED, pas d'exception). Global status = VALID uniquement si 0 résultat INVALID.

---

#### 2.2.B — Domain Services Luhn + Modulo 97 (1j)

`SiretLuhnValidator` (domain service pur) : 14 chiffres obligatoires, algorithme Luhn (poids alternés 1/2 sur les 13 premiers chiffres, clé de contrôle = dernier chiffre). `IbanModulo97Validator` (ISO 13616) : réarrangement 4 premiers caractères en fin + remplacement lettres par chiffres (A=10, B=11...) + modulo 97 = 1. Aucun appel externe.

**Critère de done :** SIRET valides et invalides testés (cas limites : 00000000000000, longueur incorrecte). IBAN valides FR et invalides testés. Performances : 10 000 validations < 100ms.

---

#### 2.2.C — Domain Services Locaux (1j)

`DateCoherenceValidator` : date émission pas dans le futur (tolérance 24h), date échéance > date émission, date émission < 10 ans (document très ancien → UNVERIFIED). `MontantTtcValidator` : HT + TVA calculée = TTC ± 0.02€ (arrondi flottant). `DocumentStructureValidator` : champs obligatoires présents selon type (FACTURE : montantTTC obligatoire, RIB : IBAN obligatoire, CNI : dateNaissance obligatoire).

**Critère de done :** Dates incohérentes → INVALID avec signalWeight correct. Montant TTC tolérance ±0.02€ testée (0.01€ → VALID, 0.03€ → INVALID). Champs obligatoires absents → INVALID.

---

#### 2.2.D — InseeApiAdapter (1j)

`InseeApiAdapter` (implémente `SiretValidatorPort`) : API SIRENE v3 (`https://api.insee.fr/api-sirene/3.11/siret/{siret}`), OAuth2 client_credentials (token TTL 7j, cache Valkey). Cache Valkey 7j ± 6h jitter (ADR-003) : clé = `insee:{siret}`. Resilience4j : CircuitBreaker (seuil 60% / 5 calls), Retry 3× backoff, Bulkhead 5 threads max, timeout 5s. Fail-open : cache disponible → retour cache sans appel API.

**Critère de done :** SIRET actif → VALID. SIRET radié → INVALID avec signalWeight. API indisponible + cache → fail-open (validation continue). API indisponible + cache vide → UNVERIFIED (pas d'exception). WireMock stubs pour tous les cas.

---

#### 2.2.E — BanApiAdapter (1j)

`BanApiAdapter` (implémente `AddressValidatorPort`) : API Géoplateforme IGN (`https://api-adresse.data.gouv.fr/search/`), cache Valkey 7j ± 6h jitter. Resilience4j même config que INSEE. Score de similarité : result.score > 0.8 → VALID, 0.5–0.8 → UNVERIFIED, < 0.5 → INVALID (adresse introuvable). WireMock stubs.

**Critère de done :** Adresse valide → score > 0.8 → VALID. Adresse inventée → score < 0.5 → INVALID. Fail-open testé (API down + cache → retour cache). WireMock 0 appels si validation locale déjà KO.

---

#### 2.2.F — RppsFileAdapter (1j)

`RppsFileAdapter` (implémente `RppsValidatorPort`) : fichier local RPPS (`rpps-current.csv`, mise à jour hebdomadaire via @Scheduled lundi 3h00 UTC). Chargement en mémoire dans `HashMap<String, RppsEntry>` au démarrage (lookup O(1)). Fallback API FHIR ANS (`https://gateway.api.esante.gouv.fr/fhir/v1/Practitioner`) si RPPS absent du fichier local. Cache Valkey 7j pour résultats API FHIR.

**Critère de done :** Lookup local O(1) testé (10 000 lookups < 10ms). Médecin présent dans fichier → VALID. Médecin absent → fallback API FHIR → résultat correct. Mise à jour hebdomadaire testée.

---

#### 2.2.G — ValidateExtractionUseCase (1j)

Chain of Responsibility dans l'ordre : locaux d'abord (Luhn → Modulo 97 → Dates → Montants → Structure) → si locaux tous VALID : APIs externes (INSEE → BAN → RPPS selon type document). Règle : si local KO → signal fraude immédiat + aucun appel API externe. Agrégation résultats → ValidationReport. Persistance + publication event.

**Critère de done :** SIRET Luhn KO → 0 appel INSEE (vérifié WireMock 0 calls). Chain of Responsibility ordre respecté. ValidationReport complet avec tous les résultats.

---

#### 2.2.H — Adapter IN Kafka ValidationConsumer (1j)

`ValidationKafkaConsumer` étend `ResilientKafkaConsumer`. Consomme `docai.doc.extracted`, group-id `docai.extraction.validation.group`. Idempotence. DLQ après 3 échecs. Enrichit le `DocumentExtractedEvent` avec les résultats de validation avant publication vers le topic suivant.

**Critère de done :** Consumer traite les events `docai.doc.extracted`. Résultats validation inclus dans l'event publié. Idempotence testée.

---

#### 2.2.I — ValidationResultMongoAdapter + Migrations (1j)

`ValidationResultMongoAdapter`. Migration Mongock V011 (collection `validation_results` + indexes). Métriques : `docai_validation_api_call_total{api, status}` counter, `docai_validation_local_fail_total{rule}` counter, `docai_validation_duration_seconds{source}` histogram.

**Critère de done :** Métriques exposées sur `/actuator/prometheus`. EXPLAIN PLAN sur requêtes validation < 50ms. Schéma Avro enrichi enregistré dans Apicurio.

---

#### 2.2.J — Tests BDD (1j)

Scénarios : SIRET Luhn KO → pas appel INSEE (WireMock 0 calls), API INSEE down + cache → fail-open, API INSEE down + cache vide → UNVERIFIED dans rapport, IBAN invalide → signal fraude poids 40, montant TTC incohérent (> ±0.02€) → INVALID, médecin RPPS inexistant → signal poids 50, SIRET radié INSEE → INVALID + signal.

**Critère de done :** Tous les scénarios BDD passent. WireMock vérifié pour chaque scénario API (nombre d'appels correct). Coverage module ≥ 80%.

---

## Module 3 — Détection de Fraude

### Phase 3.1 — Scoring Multi-Signaux (10 micro-tâches)

#### 3.1.A — Domaine Core (1j)

Aggregate `FraudAnalysis` (documentId, tenantId, signals[], fraudScore, riskLevel, approved, needsReview, analyzedAt). Value Objects `FraudSignal` (type SignalType, weight int, evidence String), `FraudScore` (0–100, invariant), `RiskLevel` enum (FAIBLE < 30, MOYEN 30–60, ELEVE 60–85, CRITIQUE > 85). Domain Event `FraudAnalyzed`. Ports IN (`AnalyzeFraudUseCase`). Ports OUT (`FraudAnalysisRepositoryPort`, analyseurs).

**Critère de done :** Score 0–100 invariant (< 0 ou > 100 → exception). RiskLevel calculé depuis score (seuils exacts). Un signal CRITIQUE (weight ≥ 85) → score ≥ 85 directement.

---

#### 3.1.B — Registry Pattern (1j)

`SignalType` enum (toutes les catégories : DATA_SIRET_INVALID weight 40, DATA_IBAN_INVALID weight 40, META_EDITED_WITH_PHOTOSHOP weight 60, META_CREATION_DATE_RECENT weight 30, STRUCTURE_LOW_RESOLUTION weight 25, DATA_RPPS_INVALID weight 50, DATA_AMOUNT_INCOHERENT weight 35, etc.). `FraudAnalyzerStrategy` interface (`analyze(document) → List<FraudSignal>`). `FraudAnalyzerRegistry` (`Map<SignalType, FraudAnalyzerStrategy>`, auto-enregistrement Spring `@Component`). Null Object Pattern : analyseur indisponible → liste vide (pas d'exception).

**Critère de done :** Registry auto-enregistrement testé (nouveau `@Component` FraudAnalyzerStrategy → automatiquement dans le Registry). Null Object : analyseur lance exception → signal ignoré, analyse continue.

---

#### 3.1.C — Analyseur Métadonnées Apache Tika (1j)

`MetadataFraudAnalyzer` : Apache Tika 2.x en mode streaming (pas de chargement complet). Signaux : logiciel éditeur Photoshop/GIMP/Pixelmator → weight 60, logiciel éditeur non standard → weight 20, date création métadonnée < 24h pour document "ancien" (date émission > 30j) → weight 30, dates métadonnées incohérentes (création > modification) → weight 25, timezone suspecte (non-européenne pour document français) → weight 15.

**Critère de done :** Document créé avec Photoshop → signal META_EDITED_WITH_PHOTOSHOP détecté. PDF sans métadonnées → aucun signal (pas d'exception). Streaming : fichier 20MB analysé < 3s.

---

#### 3.1.D — Analyseur Données Extraction (1j)

`DataConsistencyFraudAnalyzer` : signaux depuis `ValidationResult` du même document. SIRET_INVALID → weight 40, IBAN_INVALID → weight 40, RPPS_INVALID → weight 50, montants incohérents → weight 35, dates incohérentes → weight 30, adresse introuvable BAN → weight 20. Règle : si `ValidationResult` absent (extraction en cours) → analyser avec données disponibles + UNVERIFIED.

**Critère de done :** ValidationResult INVALID → signal correspondant avec bon weight. ValidationResult absent → 0 signal DATA (pas d'exception). Dépendance sur ValidationResult correctement gérée.

---

#### 3.1.E — Analyseur Structure Document (1j)

`DocumentStructureFraudAnalyzer` : résolution image < 150 DPI → weight 25 (document photocopié suspect), artefacts de compression JPEG excessif (qualité < 40%) → weight 30, zones de texture différentes dans le même PDF (collage de zones) → weight 40 via Apache PDFBox analyse graphique, texte sur fond de couleur uniforme différente du reste → weight 20.

**Critère de done :** Image basse résolution → signal STRUCTURE_LOW_RESOLUTION. Document non suspect → 0 signal structure. Analyse < 5s pour document 10 pages.

---

#### 3.1.F — Analyseur Cohérence Temporelle (1j)

`TemporalCoherenceFraudAnalyzer` : date modification S3 < date émission document (fichier modifié avant sa date officielle) → weight 35, document présenté comme "ancien" (date émission > 1 an) mais métadonnées créées < 30j → weight 40, incohérence timezone S3 vs timezone document → weight 15, date upload < date émission document → weight 25.

**Critère de done :** Date modification S3 < date émission → signal TEMPORAL_INCOHERENCE. Document ancien mais métadonnées récentes → signal TEMPORAL_RECENT_METADATA. Comparaison dates correcte (timezone gérée).

---

#### 3.1.G — FraudScoreCalculator (1j)

Domain Service pur : agrégation pondérée de tous les signaux (somme des weights). Si somme > 100 → plafonné à 100. Un signal weight ≥ 85 → score = max(somme, 85). Si 0 signal → score = 0, riskLevel = FAIBLE. Calcul déterministe (mêmes signaux = même score, toujours). Tests mutation PIT ≥ 85% sur ce service.

**Critère de done :** Calcul déterministe testé (même entrée → même sortie, 100 fois). Plafonnement 100 testé. Signal weight 85 seul → score = 85 → riskLevel CRITIQUE. PIT mutation score ≥ 85%.

---

#### 3.1.H — AnalyzeFraudUseCase (1j)

Orchestration avec Virtual Threads Java 21 : exécution parallèle de tous les analyseurs disponibles dans le Registry. Agrégation de tous les signaux retournés. `FraudScoreCalculator` → score + riskLevel. Si score > 50 → `needsReview = true`. État Document mis à jour. Publication `FraudAnalyzed` via outbox → topic `docai.doc.fraud.analyzed`.

**Critère de done :** Exécution parallèle testée (4 analyseurs → exécutés simultanément). Un analyseur en échec → signal ignoré, analyse complète (Null Object). Score > 50 → needsReview = true. Event publié avec tous les champs.

---

#### 3.1.I — Adapter IN Kafka + Adapter OUT MongoDB (1j)

`FraudKafkaConsumer` étend `ResilientKafkaConsumer`. Consomme `docai.doc.extracted`, group-id `docai.fraud.analysis.group`. Idempotence Valkey. DLQ après 3 échecs. `FraudAnalysisMongoAdapter`. Migration Mongock V012 (collection `fraud_analyses` + indexes : `{documentId}` unique, `{tenantId, riskLevel, analyzedAt}`, `{tenantId, needsReview, analyzedAt}`). Schéma Avro `FraudAnalyzedEvent` enregistré Apicurio.

**Critère de done :** Consumer traite les events. FraudAnalysis persistée avec tous les signaux. Schéma Avro visible dans Apicurio. Event `FraudAnalyzed` publié sur `docai.doc.fraud.analyzed`.

---

#### 3.1.J — Tests BDD (1j)

Scénarios : document créé avec Photoshop → signal META_EDITED_WITH_PHOTOSHOP weight 60, SIRET invalide → signal DATA_SIRET_INVALID weight 40, score agrégé = somme pondérée (vérifié manuellement), score > 100 → plafonné à 100, riskLevel CRITIQUE si score > 85, un analyseur en échec → analyse partielle (pas d'exception), event `FraudAnalyzed` publié avec tous les champs.

**Critère de done :** Tous les scénarios BDD passent. Coverage module ≥ 80%. PIT mutation ≥ 85% sur FraudScoreCalculator.

---

### Phase 3.2 — Révision Humaine (5 micro-tâches)

#### 3.2.A — Domaine (1j)

Aggregate `HumanReview` (reviewId, documentId, tenantId, assignedTo, decision, justification, reviewedAt). Value Object `ReviewDecision` (APPROVED, REJECTED, NEEDS_MORE_INFO). Domain Events `FraudReviewCompleted`, `FraudReviewAssigned`. Ports IN (`GetReviewQueueUseCase`, `SubmitReviewDecisionUseCase`, `AssignReviewUseCase`). Ports OUT (`HumanReviewRepositoryPort`, `FraudAnalysisRepositoryPort`). Règle : justification obligatoire pour REJECTED.

**Critère de done :** REJECTED sans justification → exception domaine. ReviewDecision immuable après création. APPROVED → Document état COMPLETED, REJECTED → Document état FAILED.

---

#### 3.2.B — GetReviewQueueUseCase (1j)

Liste paginée des documents `needsReview = true`, triés par riskLevel DESC (CRITIQUE en premier) puis date ASC. Read Model `fraud_review_queue` (dénormalisé : documentId, tenantId, riskLevel, fraudScore, documentType, uploadedAt, signals[]). Migration Mongock V013 (collection `fraud_review_queue` + indexes). `DashboardProjectionConsumer` met à jour ce read model sur chaque event `FraudAnalyzed`.

**Critère de done :** CRITIQUE apparaît avant ELEVE dans la liste. Pagination correcte. Isolation tenant (FRAUD_REVIEWER ne voit que son tenant).

---

#### 3.2.C — SubmitReviewDecisionUseCase (1j)

Règle : seul FRAUD_REVIEWER (ou TENANT_ADMIN) peut soumettre une décision. Justification obligatoire pour REJECTED (min 20 caractères). Mise à jour état Document (APPROVED → COMPLETED, REJECTED → FAILED). AuditEntry immuable (reviewerId, decision, justification, documentId, tenantId, reviewedAt). Publication `FraudReviewCompleted` via outbox. `HumanReviewMongoAdapter` + Migration Mongock V014 (`human_reviews`).

**Critère de done :** VIEWER → HTTP 403. REJECTED sans justification → HTTP 400. AuditEntry créé avec tous les champs. Document état mis à jour. Event publié.

---

#### 3.2.D — Adapter IN REST (1j)

`GET /v1/fraud/review-queue` (FRAUD_REVIEWER, paginé, filtres riskLevel/type/date), `GET /v1/fraud/review/{id}` (détail document + signaux + scores + historique), `POST /v1/fraud/review/{id}/decision` (APPROVED/REJECTED/NEEDS_MORE_INFO + justification), `PUT /v1/fraud/review/{id}/assign` (auto-assignation au FRAUD_REVIEWER connecté). Swagger annotations complètes.

**Critère de done :** Tous les endpoints testés (auth, rôles, isolation tenant). Queue triée correctement (CRITIQUE en premier). Décision persistée et état Document mis à jour.

---

#### 3.2.E — Tests BDD (1j)

Scénarios : FRAUD_REVIEWER approuve → COMPLETED + event publié, FRAUD_REVIEWER rejette avec justification → FAILED + AuditEntry, ANALYST tente décision → HTTP 403, REJECTED sans justification → HTTP 400, AuditEntry créé avec tous les champs, isolation tenant (reviewer ne voit que son tenant), queue triée CRITIQUE avant ELEVE.

**Critère de done :** Tous les scénarios BDD passent. Coverage module ≥ 80%.

---

## Module 4 — Orchestration & Pipeline

### Phase 4.1 — Pipeline Kafka & DLQ (10 micro-tâches)

#### 4.1.A — OutboxRelayScheduler (1j)

@Scheduled toutes les secondes (fixedDelay = 1000ms). Batch 100 messages PENDING ORDER BY createdAt ASC. Pour chaque message : publication Kafka avec clé = `partitionKey` (documentId ADR-002), headers obligatoires (tenant-id, correlation-id, trace-id, event-type, schema-version). `markPublished()` atomique après confirmation Kafka. Si Kafka indisponible → messages restent PENDING (réessayés au prochain batch). Job nettoyage @Scheduled quotidien : suppression messages PUBLISHED > 24h.

**Critère de done :** Latence PENDING → PUBLISHED < 2s. Kafka indisponible → messages conservés en base (pas de perte). Nettoyage quotidien testé.

---

#### 4.1.B — OutboxKafkaProducer (1j)

Sérialisation Avro + Apicurio Registry (schéma lookup par type d'event). `enable.idempotence=true`, `acks=all`, `retries=3`. Clé de partition = `partitionKey` du message (documentId pour pipeline, tenantId pour analytics ADR-002). Headers Kafka propagés sur chaque message. Métriques `docai_outbox_published_total` counter, `docai_outbox_pending_size` gauge.

**Critère de done :** Tous les events sur le même documentId vont sur la même partition Kafka (vérifié Kafka UI). Headers présents sur 100% des messages. `enable.idempotence` → pas de doublons même en cas de retry Kafka.

---

#### 4.1.C — Idempotence Consumers (1j)

Clé Valkey `idempotency:{topic}:{partition}:{offset}`, TTL 24h fixe (pas de jitter — précision requise). `isAlreadyProcessed()` vérification atomique avant `handle()`. `markAsProcessed()` après commit offset Kafka. Si déjà traité → skip silencieux (pas d'exception, pas de log ERROR). Log DEBUG uniquement.

**Critère de done :** Event reçu 2× (même topic/partition/offset) → `handle()` appelé 1× seulement. Testé avec 100 events dupliqués simultanément → exactement 100 traitements (pas 200). TTL 24h vérifié.

---

#### 4.1.D — Retry Exponentiel (1j)

`RetryService` : 3 tentatives avec backoff exponentiel (30s → 2min → 10min). Champ `retryable=true/false` dans les events d'échec (erreur technique = retryable, erreur métier = non-retryable). Si `retryable=false` → DLQ immédiat sans retry. Métrique `docai_retry_attempt_total{stage, attempt}` counter. Log WARN à chaque retry avec raison + attempt number.

**Critère de done :** 3 échecs retryables → DLQ. Erreur non-retryable → DLQ immédiat (0 retry). Backoff exponentiel vérifié (timings corrects). Métrique incrémèntée à chaque tentative.

---

#### 4.1.E — DLQ Management (1j)

`DlqMonitorConsumer` (consomme `docai.doc.dlq`, group-id `docai.pipeline.dlq.group`). Stockage `dead_letter_events` MongoDB : documentId, tenantId, failedStage, errorCode, errorMessage, retryable, payload (JSON brut), arrivedAt. Migration Mongock V015 (collection `dead_letter_events` + TTL index 90j + indexes `{tenantId, failedStage, arrivedAt}`). Métrique `docai_dlq_size` gauge (total events DLQ non résolus).

**Critère de done :** Event DLQ stocké avec tous les champs. TTL 90j vérifié sur index MongoDB. Métrique `docai_dlq_size` correcte. Consumer idempotent (event DLQ reçu 2× → stocké 1×).

---

#### 4.1.F — Saga Choreography Compensation (1j)

Compensation sur échec via events : `DocumentFailed` publié → `SagaCompensationConsumer` écoute → selon le stage qui a échoué : suppression fichier S3 orphelin si classification échoue (document jamais traité → S3 nettoyé), mise à jour Read Model `document_summary_views`, notification TENANT_ADMIN si dépassement seuil échecs. Métrique `docai_saga_compensation_total{stage}` counter.

**Critère de done :** Classification échoue après S3 upload → fichier S3 supprimé (vérifié LocalStack). Read Model mis à jour (document FAILED visible dans le dashboard). Log INFO avec documentId, stage, compensation effectuée.

---

#### 4.1.G — State Machine Documents (1j)

`DocumentStateMachine` (domain service) : transitions contrôlées définies explicitement. Transitions valides : `PENDING → CLASSIFIED → EXTRACTED → ANALYZED → COMPLETED`, `PENDING → FAILED`, `CLASSIFIED → FAILED`, `EXTRACTED → FAILED`, `ANALYZED → FAILED`, `* → NEEDS_REVIEW`. Toute transition non listée → `InvalidDocumentStateTransitionException`. `@Audited` sur chaque transition.

**Critère de done :** Toutes les transitions valides testées. Transitions invalides → exception (15 cas limites testés). AuditEntry créé à chaque transition. ArchUnit vérifie que StateMachine est dans `domain.service`.

---

#### 4.1.H — RetryDlq + AbandonDlq UseCases (1j)

`RetryDlqUseCase` : prend un event DLQ, le remet en PENDING dans l'outbox (re-publication sur le bon topic). Règle : maximum 3 retries manuels (au-delà → forcer ABANDONED). `AbandonDlqUseCase` : marque `status=ABANDONED`, AuditEntry immuable (qui, pourquoi, quand), notification TENANT_ADMIN par email. Accessible rôle SYSTEM ou TENANT_ADMIN uniquement.

**Critère de done :** Retry manuel → event republié sur Kafka → consommé → document reprend le traitement. 4ème retry manuel → rejeté (HTTP 409). ABANDONED → AuditEntry créé + email TENANT_ADMIN.

---

#### 4.1.I — Migrations + Schémas Avro Restants (1j)

Migrations Mongock : V016 (audit_entries si pas encore créée, TTL index 5 ans RGPD), V017 (outbox_messages cleanup index). Schémas Avro manquants enregistrés dans Apicurio : `DocumentCompletedEvent`, `DocumentFailedEvent` (avec champ `retryable`). Validation compatibilité backward de tous les schémas existants après ajout nouveaux champs.

**Critère de done :** Tous les schémas Avro visibles dans Apicurio UI. Compatibilité backward vérifiée (nouveau champ optionnel ne casse pas les consumers existants). Toutes les migrations Mongock passent dans l'ordre.

---

#### 4.1.J — Tests BDD Pipeline Complet (1j)

Scénarios : event Kafka "perdu" (simulation) → retry → DLQ après 3 échecs, event reçu 2× → traité 1× (idempotence), saga compensation : upload → classification échoue → fichier S3 supprimé, State Machine transition invalide → exception + pas de persist, pipeline complet upload → COMPLETED < 30s (TestContainers toute la stack), DLQ manual retry → document reprend le traitement.

**Critère de done :** Tests E2E pipeline complet passent. S3 nettoyé après saga compensation vérifié (LocalStack). Coverage module ≥ 80%.

---

### Phase 4.2 — Monitoring Pipeline (5 micro-tâches)

#### 4.2.A — Métriques Micrometer (1j)

Toutes les métriques pipeline exposées sur `/actuator/prometheus` : `docai_document_processing_duration_seconds{module}` histogram, `docai_kafka_consumer_lag{topic, group}` gauge, `docai_circuit_breaker_state{service}` gauge (0=CLOSED, 1=OPEN, 2=HALF_OPEN), `docai_dlq_size` gauge, `docai_saga_compensation_total{stage}` counter, `docai_retry_attempt_total{stage, attempt}` counter, `docai_outbox_pending_size` gauge, `docai_pipeline_stuck_total{status}` counter.

**Critère de done :** Toutes les métriques visibles dans Prometheus. Dashboards Grafana créés pour chaque métrique. Métriques correctement taguées (tenant non exposé pour éviter cardinalité infinie).

---

#### 4.2.B — Alertes Grafana (1j)

Alertes configurées avec runbooks : lag Kafka > 1000 messages / 5 min → Slack (runbook : vérifier consumers), Circuit Breaker OPEN (LLM, OCR, INSEE) → PagerDuty immédiat (runbook : vérifier service externe), DLQ > 50 messages → Slack (runbook : vérifier logs erreurs), error rate pipeline > 1% / 5 min → PagerDuty, documents bloqués > 30 min → Slack, Valkey cache hit ratio < 30% → Slack.

**Critère de done :** Chaque alerte testée (simulation condition → notification reçue dans Slack/PagerDuty). Chaque alerte a un runbook associé dans le wiki. Alertes désactivées en DEV (uniquement STAGING et PROD).

---

#### 4.2.C — Endpoints Admin DLQ (1j)

`GET /v1/admin/dlq` (liste events DLQ paginée, filtres : stage, errorCode, date, tenantId — rôle SYSTEM ou TENANT_ADMIN pour son propre tenant). `POST /v1/admin/dlq/{id}/retry` (rejouer manuellement, max 3 retries). `DELETE /v1/admin/dlq/{id}` (abandonner avec justification obligatoire). Rate limiting strict : max 10 req/min (protège contre les loops de retry).

**Critère de done :** Endpoints accessibles uniquement aux rôles autorisés. Retry → event republié sur Kafka. Abandon → status ABANDONED + AuditEntry. Rate limiting testé (11ème requête → HTTP 429).

---

#### 4.2.D — PipelineHealthScheduler (1j)

@Scheduled toutes les 5 minutes. Requête : documents avec `updatedAt < now - 30 min AND status IN (PENDING, CLASSIFIED, EXTRACTED, ANALYZED)`. Pour chaque document bloqué : log WARN (documentId, tenantId, status, bloqué depuis X min), incrément métrique `docai_pipeline_stuck_total{status}`. Si > 5 documents bloqués simultanément → alerte Slack immédiate. Auto-récupération : si document bloqué > 2h → forcer retry (republier l'event correspondant au dernier statut).

**Critère de done :** Scheduler détecte un document bloqué dans les 5 min. Log WARN avec tous les champs. Auto-récupération testée (document bloqué 2h → retry automatique → traitement reprend).

---

#### 4.2.E — Tests Integration Pipeline Complet (1j)

Test E2E TestContainers complet avec toute la stack (MongoDB + Kafka + Valkey + LocalStack S3 + WireMock pour LLM et APIs externes). Scénarios : upload PDF → classification → extraction → fraude → COMPLETED avec vérification de chaque statut intermédiaire, vérification que chaque event Kafka est dans le bon ordre sur la même partition, vérification que toutes les métriques sont incrémentées, durée totale pipeline < 30s.

**Critère de done :** Test E2E passe en < 30s. Chaque statut Document vérifié en base. Chaque event Kafka vérifié (bon topic, bon ordre, bonne partition). Toutes les métriques correctement incrémentées.

---

# PARTIE 5 — PRODUIT

> **33 micro-tâches — ~7 semaines**
> Prérequis : Partie 4 fonctionnelle (pipeline complet de bout en bout).
> **Ordre : Module 5 → Module 6.**

---

## Module 5 — Dashboard & Reporting

### Phase 5.1 — Read Model CQRS & Analytics (10 micro-tâches)

#### 5.1.A — Domaine Read Model (1j)

`DocumentSummaryView` (dénormalisé : documentId, tenantId, status, type, riskLevel, fraudScore, extractionScore, fileName, uploadedAt, updatedAt, `lastSyncedAt` — champ obligatoire ADR-011). Ports IN (`GetDashboardSummaryUseCase`, `GetDocumentListUseCase`, `GetAnalyticsUseCase`). Règle ADR-011 : `lastSyncedAt` mis à jour à chaque projection, utilisé pour la réconciliation.

**Critère de done :** `lastSyncedAt` présent et obligatoire dans le schéma. Pas de champs PII dans le Read Model (documenté explicitement).

---

#### 5.1.B — DashboardProjectionConsumer (1j)

Consomme tous les topics pipeline (`docai.doc.uploaded`, `docai.doc.classified`, `docai.doc.extracted`, `docai.doc.fraud.analyzed`, `docai.doc.completed`, `docai.doc.failed`), group-id `docai.dashboard.projection.group`. Pour chaque event : upsert `document_summary_views` avec les champs correspondants + `lastSyncedAt = now()`. Idempotence (event rejoué → upsert idempotent, pas de doublon). Isolation tenant stricte.

**Critère de done :** Projection correcte après chaque event (statuts et scores mis à jour). `lastSyncedAt` mis à jour à chaque projection. Upsert idempotent (event reçu 2× → document_summary_views mis à jour 1× seulement).

---

#### 5.1.C — ReadModelReconciliationScheduler (1j)

@Scheduled toutes les 5 min. Requête : `writeSide.updatedAt > readModel.lastSyncedAt + 30s`. Pour chaque divergence : resync depuis la write-side (relecture Document + FraudAnalysis + ExtractionResult → mise à jour `document_summary_views`). Incrément `docai_read_model_desync_total` counter. Log WARN (documentId, writeSideUpdatedAt, readModelLastSyncedAt, lagSeconds). Alerte Grafana si > 10 désync en 5 min → Slack.

**Critère de done :** Scheduler détecte une désynchronisation dans les 5 min. Après consumer arrêté 10 min → scheduler rattrape tous les documents désynchronisés. Alerte Grafana testée.

---

#### 5.1.D — GetDashboardSummaryUseCase (1j)

KPIs depuis Read Model (pas de requête sur la write-side) : total documents ce mois, répartition par status, répartition par riskLevel, répartition par type de document, taux de détection fraude (score > 50 / total), durée moyenne traitement (P50, P95). Cache Valkey 2 min (TTL court → données fraîches). Isolation tenant stricte sur toutes les requêtes.

**Critère de done :** KPIs corrects après chaque event pipeline. Cache Valkey 2 min testé (2ème appel dans les 2 min → cache hit, 0 requête MongoDB). Isolation tenant testée.

---

#### 5.1.E — GetDocumentListUseCase (1j)

Lecture paginée `document_summary_views`. Filtres : status (multi-select), type (multi-select), riskLevel (multi-select), dateFrom/dateTo, searchText (sur fileName). Tri configurable (uploadedAt DESC par défaut). Réponse `ApiResponse<Page<DocumentSummaryView>>` (commons-api PageMetadata). Isolation tenant stricte.

**Critère de done :** Listing avec 10 000 documents < 100ms (EXPLAIN PLAN validé). Filtres combinés testés. Pagination correcte (totalElements, totalPages, first, last).

---

#### 5.1.F — Migrations Mongock + EXPLAIN PLAN (1j)

Migration Mongock V018 (collection `document_summary_views`). Indexes composites : `{tenantId, status, createdAt}`, `{tenantId, riskLevel, createdAt}`, `{tenantId, type, createdAt}`, `{tenantId, updatedAt}` (pour réconciliation ADR-011), `{tenantId, fileName, createdAt}` (pour searchText). Partial indexes sur statuts actifs uniquement (excluent COMPLETED et FAILED anciens — ADR-010). EXPLAIN PLAN validé pour chaque requête dashboard (< 100ms avec 100 000 documents).

**Critère de done :** EXPLAIN PLAN sur chaque requête dashboard < 100ms avec 100 000 documents (jeu de données de test). Partial indexes réduisent la taille des indexes de 60%+ (vérifié).

---

#### 5.1.G — GetAnalyticsUseCase (1j)

`GET /v1/analytics` : KPIs sur période configurable (7j/30j/90j/custom, max 365j). Agrégation MongoDB pipeline (`$match` tenant + période, `$group` par statut/riskLevel/type, `$sort`). `GET /v1/analytics/fraud-trends` : évolution du score fraude moyen par semaine sur la période, distribution riskLevel par semaine. Cache Valkey 5 min (données analytiques moins temps-réel).

**Critère de done :** Requêtes analytics < 500ms avec 1 000 000 documents (EXPLAIN PLAN validé). Cache 5 min testé. Isolation tenant stricte.

---

#### 5.1.H — Endpoint Admin Rebuild Read Model (1j)

`POST /v1/admin/read-model/rebuild` (rôle SYSTEM uniquement). Reconstruction sans downtime : rejoue events Kafka des 7 derniers jours dans collection temporaire `document_summary_views_rebuild` → swap atomique `renameCollection` (MongoDB opération atomique). Log progression toutes les 1000 docs. Métriques : `docai_readmodel_rebuild_duration_seconds`, `docai_readmodel_rebuild_docs_total`. Requêtes en cours pendant le rebuild → toujours servies depuis l'ancienne collection.

**Critère de done :** Rebuild sans downtime testé (requêtes pendant rebuild → réponses correctes depuis ancienne collection). Swap atomique → aucune requête en erreur. Log progression visible.

---

#### 5.1.I — Adapter REST Dashboard (1j)

`GET /v1/dashboard/summary` (KPIs temps réel), `GET /v1/documents` (liste paginée avec filtres, accessible aussi ANALYST et VIEWER), `GET /v1/analytics` (TENANT_ADMIN), `GET /v1/analytics/fraud-trends` (TENANT_ADMIN). Tous protégés JWT + isolation tenant. Pagination standard (commons-api). Swagger annotations complètes.

**Critère de done :** Tous les endpoints testés (auth, rôles, isolation tenant). VIEWER peut lister ses documents (lecture seule). ANALYST peut voir la liste et les détails.

---

#### 5.1.J — Tests BDD (1j)

Scénarios : projection correcte après chaque event pipeline (uploadé → PENDING visible, classifié → CLASSIFIED visible, etc.), réconciliation après consumer arrêté 10 min → tous documents rattrapés dans les 5 min, EXPLAIN PLAN < 100ms avec 100 000 documents, rebuild sans downtime (requêtes servies pendant rebuild), isolation tenant (tenant A ne voit pas dashboard tenant B).

**Critère de done :** Tous les scénarios BDD passent. Performance validée (NFR-DSH-001 à NFR-DSH-003). Coverage module ≥ 80%.

---

### Phase 5.2 — Alertes Temps Réel SSE (5 micro-tâches)

#### 5.2.A — Domaine + Ports (1j)

Port IN (`SubscribeFraudAlertsUseCase`). Port OUT (`SseNotificationPort`). Value Object `FraudAlert` (alertId, documentId, tenantId, fraudScore, riskLevel, documentType, occurredAt). Règle : score > 50 → alerte SSE. Règle isolation : client SSE reçoit uniquement les alertes de son `tenant_id` (extrait du JWT). Règle reconnexion : `Last-Event-ID` pour reprise depuis le dernier event reçu.

**Critère de done :** Isolation tenant documentée et vérifiée dans les règles domaine. `FraudAlert` immuable (record Java). Règle score > 50 testée (score 50 → pas d'alerte, score 51 → alerte).

---

#### 5.2.B — SseNotificationAdapter (1j)

Spring `SseEmitter` (timeout 60s). Map `ConcurrentHashMap<tenantId, CopyOnWriteArrayList<SseEmitter>>` (thread-safe). Keepalive toutes les 30s (event type `heartbeat`, data `ping`). Cleanup automatique des emitters morts via callbacks `onCompletion()` et `onTimeout()`. Limite : 50 connexions SSE simultanées par tenant (au-delà → HTTP 503). Buffer Valkey des 100 derniers events SSE par tenant (TTL 5 min) pour reconnexion `Last-Event-ID`.

**Critère de done :** Keepalive toutes les 30s testé. Emitter mort → retiré automatiquement de la Map. Limite 50 connexions par tenant testée (51ème → HTTP 503). Buffer Valkey pour reconnexion fonctionnel.

---

#### 5.2.C — AlertKafkaConsumer (1j)

`AlertKafkaConsumer` (consomme `docai.doc.fraud.analyzed`, group-id `docai.notification.alert.group`). Si fraudScore > 50 : construction `FraudAlert` → `SseNotificationPort.push(tenantId, alert)` → envoi aux emitters du tenant uniquement (pas aux autres tenants). Isolation tenant garantie au niveau du push SSE.

**Critère de done :** Event Kafka fraudScore > 50 → alerte SSE reçue par client du bon tenant en < 2s. Event fraudScore ≤ 50 → aucune alerte SSE. Client tenant A ne reçoit pas alertes tenant B (testé avec 2 connexions simultanées).

---

#### 5.2.D — Endpoint SSE (1j)

`GET /v1/dashboard/stream` (JWT obligatoire, extraction tenantId du JWT). Header `Last-Event-ID` : reconnexion → rejoue les alertes du buffer Valkey depuis le dernier eventId reçu. Chaque event SSE a un `id` unique (UUID). Format : `id: {uuid}\nevent: fraud-alert\ndata: {json}\n\n`. Rate limiting : max 5 connexions SSE simultanées par `userId` (évite les abus).

**Critère de done :** Connexion sans JWT → HTTP 401. Reconnexion avec Last-Event-ID → alertes manquées rejouées (testées avec simulation déconnexion). Format SSE correctement parsé côté client.

---

#### 5.2.E — Tests BDD (1j)

Scénarios : event Kafka fraudScore > 50 → alerte SSE reçue < 2s (mesuré), event fraudScore = 50 → pas d'alerte, client tenant A ne reçoit pas alertes tenant B (2 connexions simultanées testées), keepalive toutes les 30s reçu, reconnexion avec Last-Event-ID → alertes manquées rejouées, emitter mort → retiré automatiquement + pas d'erreur.

**Critère de done :** Latence < 2s vérifiée (BR-DSH-010). Isolation tenant stricte. Coverage module ≥ 80%.

---

### Phase 5.3 — Centre de Notifications In-App (3 micro-tâches)

#### 5.3.A — Domaine + NotificationConsumer (1j)

Collection `notifications` (id, tenantId, userId, type, title, message, resourceId, read, readAt, createdAt). Types : FRAUD_ALERT (score > 50), QUOTA_WARNING_80, QUOTA_WARNING_95, QUOTA_EXCEEDED, PAYMENT_FAILED. `NotificationKafkaConsumer` (consomme `docai.doc.fraud.analyzed` + events quota depuis `docai.doc.completed`) → crée notification persistante pour chaque déclencheur. TTL index MongoDB 90j.

**Critère de done :** Notification FRAUD_ALERT créée pour chaque event fraudScore > 50. Notification QUOTA_WARNING_80 créée au bon seuil. TTL 90j configuré sur index MongoDB.

---

#### 5.3.B — NotificationMongoAdapter + Endpoints REST (1j)

`NotificationMongoAdapter`. Migration Mongock V019 (collection `notifications` + indexes : `{tenantId, userId, read, createdAt}` pour listing, `{tenantId, userId, createdAt}` TTL 90j). Endpoints : `GET /v1/notifications` (paginé, filtres type/read/dateFrom), `PUT /v1/notifications/{id}/read`, `PUT /v1/notifications/read-all`, `GET /v1/notifications/unread-count`. Isolation tenant + userId stricte.

**Critère de done :** Un utilisateur ne voit que ses propres notifications (isolation userId). Marquage lu → `read=true` + `readAt=now()`. `unread-count` correct après marquage.

---

#### 5.3.C — Compteur Non Lues SSE + Tests BDD (1j)

Push SSE du badge non lues : sur `GET /v1/dashboard/stream`, envoyer un event `unread-count` avec le nombre actuel à la connexion, puis à chaque changement (nouvelle notification ou marquage lu). Scénarios BDD : notification créée à chaque fraude > 50, notification créée aux seuils quota corrects, marquage lu met à jour badge SSE en temps réel, marquage read-all → badge = 0, isolation tenant+userId stricte, TTL 90j vérifié.

**Critère de done :** Tous les scénarios BDD passent. Badge SSE mis à jour en temps réel (< 1s). Coverage module ≥ 80%.

---

## Module 6 — Intégrations & API Publique

### Phase 6.1 — API Publique & API Keys (10 micro-tâches)

#### 6.1.A — Domaine API Keys (1j)

Aggregate `ApiKey` (id, tenantId, name, hashedKey, salt, scope, createdAt, lastUsedAt, revokedAt). `ApiKeyScope` enum (READ, WRITE, ADMIN). Domain Events `ApiKeyCreated`, `ApiKeyRevoked`. Ports IN (`CreateApiKeyUseCase`, `RevokeApiKeyUseCase`, `ListApiKeysUseCase`, `ValidateApiKeyUseCase`). Règles : max 10 clés actives par tenant, clé jamais exposée après création, hash SHA-256 + sel unique.

**Critère de done :** Couverture domaine ≥ 90%. Règle max 10 clés actives testée (11ème → exception domaine). Hash SHA-256 + sel : même clé + sel différent → hash différent.

---

#### 6.1.B — CreateApiKeyUseCase (1j)

Génération clé aléatoire 32 bytes (SecureRandom). Sel unique 16 bytes (SecureRandom). Hash SHA-256(`clé + sel`). Stockage `{hashedKey, salt}` en MongoDB (jamais la clé en clair). Retour de la valeur en clair une seule fois dans la réponse (jamais recalculable ensuite). AuditEntry création (tenantId, keyId, scope, createdBy — PII masqué). Règle : si tenant a déjà 10 clés actives → HTTP 409.

**Critère de done :** Clé créée : lecture MongoDB → haché (jamais en clair). 2ème requête pour récupérer la valeur → impossible (HTTP 404). AuditEntry créé. 11ème clé → HTTP 409.

---

#### 6.1.C — RevokeApiKeyUseCase + ListApiKeysUseCase (1j)

`RevokeApiKeyUseCase` : softDelete (revokedAt = now()), invalidation cache Valkey immédiate (`DEL clé`). Prochaine requête avec cette clé révoquée → HTTP 401 (< 100ms, depuis cache Valkey). AuditEntry révocation. `ListApiKeysUseCase` : retourne id, name, scope, createdAt, lastUsedAt, revokedAt (jamais le hashedKey). Filtre : actives uniquement ou toutes.

**Critère de done :** Révocation → cache Valkey invalidé → HTTP 401 sur prochaine requête < 100ms. Liste ne contient jamais le hash. AuditEntry révocation créé.

---

#### 6.1.D — ApiKeyMongoAdapter + ValkeyApiKeyCacheAdapter (1j)

`ApiKeyMongoAdapter` : persistance + Migration Mongock V020 (collection `api_keys` + indexes : `{hashedKey}` unique, `{tenantId, revokedAt, createdAt}`). `ValkeyApiKeyCacheAdapter` : cache `{hashedKey} → {tenantId, scope, keyId}`, TTL 15 min. Sur création → pas de pre-cache (première utilisation → lookup MongoDB → hydrate Valkey). Sur révocation → `DEL` Valkey immédiat.

**Critère de done :** CRUD ApiKey testé. Cache hit : lookup Valkey uniquement (0 requête MongoDB). Cache miss : lookup MongoDB → hydrate Valkey. Révocation : DEL Valkey immédiat vérifié.

---

#### 6.1.E — ApiKeyAuthFilter (1j)

Spring Security `OncePerRequestFilter`. Détecte header `X-API-Key`. Hash SHA-256(`clé + sel`) → lookup Valkey → si miss : lookup MongoDB → si trouvé : hydrate Valkey. Injecte `tenantId` + `scope` dans `SecurityContext`. Vérifie scope vs endpoint requis (READ endpoint → READ scope suffisant, WRITE endpoint → WRITE ou ADMIN scope requis). Met à jour `lastUsedAt` asynchrone (ne pas bloquer la requête).

**Critère de done :** Clé valide → accès autorisé avec bon scope. Clé révoquée → HTTP 401 < 100ms. Scope READ sur endpoint WRITE → HTTP 403. `lastUsedAt` mis à jour asynchrone (requête pas bloquée).

---

#### 6.1.F — Rate Limiting API Keys (1j)

Bucket4j + Valkey : bucket par `apiKeyId` (pas par IP), mêmes limites que par tenant selon plan (FREE : 100 req/min, STARTER : 500 req/min, PRO : 2000 req/min, ENTERPRISE : illimité). Headers réponse : `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. HTTP 429 si dépassé (ProblemDetail : `RATE-001`, message avec reset time).

**Critère de done :** Rate limiting testé aux limites (FREE : 101ème req → HTTP 429). Headers présents sur chaque réponse. ProblemDetail format correct. Bucket per keyId (pas globalement par tenant).

---

#### 6.1.G — Endpoints API Publique (1j)

10 endpoints complets : `POST /v1/documents` (WRITE scope, multipart), `GET /v1/documents/{id}` (READ), `GET /v1/documents` (READ, paginé + filtres), `GET /v1/documents/{id}/extraction` (READ), `GET /v1/documents/{id}/fraud` (READ), `POST /v1/documents/{id}/reprocess` (WRITE, renvoie le document en PENDING et republication event), `GET /v1/analytics` (READ). Scope vérifié sur chaque endpoint. Isolation tenant stricte (tenantId extrait du JWT ou de la clé API).

**Critère de done :** Tous les endpoints accessibles via JWT (frontend) ET via API Key (clients B2B). Scope READ sur endpoint WRITE → HTTP 403. Isolation tenant testée.

---

#### 6.1.H — Endpoints Gestion API Keys (1j)

`POST /v1/api-keys` (TENANT_ADMIN, ADMIN scope, corps : name + scope), `GET /v1/api-keys` (TENANT_ADMIN, liste paginée actives + révoquées), `DELETE /v1/api-keys/{id}` (TENANT_ADMIN, révocation avec confirmation). Swagger annotations : descriptions, exemples de requête/réponse, codes erreur (400, 401, 403, 409). Warning dans la réponse de création : "Cette clé ne sera plus jamais affichée."

**Critère de done :** Création → clé affichée une seule fois. 2ème GET → clé absente de la réponse. Révocation → HTTP 204. ANALYST tente création → HTTP 403.

---

#### 6.1.I — Documentation OpenAPI 3.1 (1j)

SpringDoc annotations sur tous les endpoints : `@Operation` (summary, description), `@ApiResponse` (chaque code HTTP avec `@Schema`), `@Parameter` (chaque paramètre avec exemple), exemples JSON inline dans les annotations. Spec OpenAPI publiée sur GitHub Pages via job `05-documentation.yml`. `swagger-cli validate` passe (0 warning). Versioning API : header `Deprecation` et `Sunset` prêts pour future dépréciation `/v1/`.

**Critère de done :** Swagger UI accessible sur `/swagger-ui.html`. Spec OpenAPI publiée sur GitHub Pages. `swagger-cli validate` → 0 erreur, 0 warning. Time-to-first-call < 1h objectif testé avec un développeur externe (ou simulation).

---

#### 6.1.J — Contract Testing + Tests BDD (1j)

Spring Cloud Contract : contrats pour tous les endpoints publics (`POST /v1/documents`, `GET /v1/documents/{id}`, `GET /v1/documents/{id}/extraction`, `GET /v1/documents/{id}/fraud`, `GET /v1/analytics`). Stubs WireMock générés automatiquement et publiés artifact CI. Scénarios BDD : clé révoquée → HTTP 401 < 100ms (cache Valkey), scope READ tente WRITE → HTTP 403, rate limit atteint → HTTP 429 avec reset time, hash SHA-256 vérifié (valeur en clair non stockée), 10 clés actives → 11ème → HTTP 409.

**Critère de done :** Tous les contrats passent. Stubs WireMock publiés dans l'artifact CI. Tous les scénarios BDD passent. Coverage module ≥ 80%.

---

### Phase 6.2 — Webhooks Fiables (5 micro-tâches)

#### 6.2.A — Domaine (1j)

Aggregate `WebhookEndpoint` (id, tenantId, url HTTPS, secretHash, events[], status ACTIVE/PAUSED, createdAt). Aggregate `WebhookDelivery` (id, endpointId, eventType, payload, attempts[], status PENDING/DELIVERED/FAILED/ABANDONED, hmacSignature, createdAt). Domain Events `WebhookRegistered`, `WebhookDeliveryCompleted`, `WebhookDeliveryFailed`. Ports IN/OUT. Règles : URL HTTPS obligatoire (HTTP rejeté), max 5 endpoints par tenant, secret jamais exposé après création.

**Critère de done :** Couverture domaine ≥ 90%. URL HTTP → exception domaine. 6ème endpoint → exception. Secret : stocké hashé (SHA-256 + sel), jamais récupérable.

---

#### 6.2.B — RegisterWebhookUseCase + WebhookEndpointMongoAdapter (1j)

`RegisterWebhookUseCase` : validation URL HTTPS, hash secret, création endpoint. CRUD complet. Endpoints REST : `POST /v1/webhooks` (TENANT_ADMIN), `GET /v1/webhooks` (liste actifs + paused), `DELETE /v1/webhooks/{id}` (révocation → statut PAUSED puis DELETED). Migration Mongock V021 (collections `webhook_endpoints` + `webhook_deliveries`). AuditEntry sur création et révocation.

**Critère de done :** URL HTTP → HTTP 400. 6ème endpoint → HTTP 409. Secret hashé en base (jamais en clair). Révocation → status PAUSED immédiat (plus d'envois).

---

#### 6.2.C — DeliverWebhookUseCase (1j)

Signature HMAC-SHA256 calculée sur le payload JSON brut : `X-DocAI-Signature: sha256=HMAC(secret_en_clair, payload)`. Retry 5× avec backoff exponentiel (30s → 1min → 5min → 15min → 1h). AuditEntry (WebhookDelivery) pour chaque tentative (timestamp, HTTP status retourné par le client, durée, body réponse tronqué 500 chars). Après 5 échecs → status FAILED → notification dashboard (mettre à jour `fraud_review_queue` ou dashboard avec badge d'alerte).

**Critère de done :** Signature HMAC correcte (vérifiable côté client avec le secret). Retry backoff exponentiel testé (timings corrects). 5 échecs → status FAILED + notification. AuditEntry créé pour chaque tentative.

---

#### 6.2.D — WebhookDeliveryConsumer + WebhookHttpAdapter (1j)

`WebhookDeliveryConsumer` (consomme `docai.doc.completed` + `docai.doc.fraud.analyzed`, group-id `docai.integration.webhook.group`). Récupère les endpoints ACTIVE du tenant → pour chaque endpoint : crée `WebhookDelivery` + appelle `DeliverWebhookUseCase`. `WebhookHttpAdapter` : appel HTTP POST vers l'URL du client (timeout 10s), vérifie réponse 2xx → DELIVERED, non-2xx → retry. Virtual Threads Java 21 pour appels parallèles (jusqu'à 5 endpoints par tenant simultanément).

**Critère de done :** Consumer traite les events `docai.doc.completed`. Pour chaque endpoint actif du tenant → delivery créée et envoyée. Virtual Threads : 5 endpoints → appels simultanés. Endpoint répond 500 → retry déclenché.

---

#### 6.2.E — Tests BDD (1j)

Scénarios : livraison OK (endpoint répond 200) → status DELIVERED en 1 tentative, endpoint répond 500 → retry 5× → status FAILED → alerte dashboard, signature HMAC correcte (vérifiée avec HMAC calculé côté test), idempotence (même event `docai.doc.completed` reçu 2× → livré 1× seulement), URL HTTP à l'enregistrement → HTTP 400, max 5 endpoints par tenant (6ème → HTTP 409), endpoint PAUSED → pas d'envoi.

**Critère de done :** Tous les scénarios BDD passent. HMAC vérifié avec le bon secret. Idempotence testée. Coverage module ≥ 80%.

---

# Récapitulatif Global

| Partie | Micro-tâches | Durée estimée |
|--------|-------------|---------------|
| **1 — Setup** | 12 | ~2 semaines |
| **2 — Commons** | 7 | ~2 semaines |
| **3 — Fondations Métier** | 20 | ~4 semaines |
| **4 — Pipeline** | 70 | ~14 semaines |
| **5 — Produit** | 33 | ~7 semaines |
| **TOTAL** | **142 micro-tâches** | **~33 semaines** |

---

## Règles d'Or

| Règle | Description |
|-------|-------------|
| **1 tâche = 1 PR** | Chaque micro-tâche fait l'objet d'une PR dédiée |
| **Critère de done obligatoire** | Pas de merge sans critère de done validé |
| **Ordre strict** | Jamais démarrer une tâche sans que ses prérequis soient mergés |
| **Tests avant tout** | Chaque tâche inclut ses tests (unitaires + BDD si applicable) |
| **Commons en premier** | Ne jamais réimplémenter ce qui est dans les commons |
| **Speckit comme référence** | Toujours consulter `DOCAI_BACKEND_MASTER_SPECKIT_F.md` pour les détails techniques |

---

*DocAI — Plan de Découpage Backend V2 — Mai 2026*
*142 micro-tâches · Chaque tâche ≤ 1 jour · Référence : DOCAI_BACKEND_MASTER_SPECKIT_F.md*
