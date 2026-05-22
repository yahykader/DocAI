---
name: docai-module0-rgpd
description: "Implémente le Module 0.5 RGPD & Privacy DocAI (rétention configurable 30–365j, droit à l'effacement asynchrone (72h, export portabilité JSON S3, chiffrement Field Level Encryption MongoDB KMS, suppression compte individuel, job quotidien RetentionCleanupScheduler, AuditEntries immuables 5 ans). Utiliser quand on demande d'implémenter la politique de rétention, l'effacement des données, l'export RGPD, le chiffrement PII MongoDB, la suppression de compte, ou tout ce qui touche à la conformité RGPD dans DocAI. Obligatoire AVANT de démarrer le Module 1 en production."
---

# Module 0.5 — RGPD & Privacy

> **Prérequis :** Modules 0, 0.1, 0.2, 0.3 (Auth + Profil) terminés. KMS AWS configuré.  
> **Durée estimée :** 1 semaine  
> **Rôle autorisé :** `TENANT_ADMIN` uniquement sur tous les endpoints RGPD.

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/rgpd/
├── RetentionPolicy.java          // Value Object (tenantId, retentionDays 30–365, effectiveFrom)
├── DeletionReport.java           // Aggregate (tenantId, requestedAt, status, itemsDeleted, completedAt)
├── DataExport.java               // Aggregate (tenantId, requestedAt, s3Key, expiresAt, status)
└── events/
    ├── DocumentRetentionExpired.java
    ├── DataErasureRequested.java
    └── DataExportReady.java
```

### Ports
```
Inbound:
  PORT-IN-RGP-001 → ConfigureRetentionPolicyUseCase
  PORT-IN-RGP-002 → RequestDataErasureUseCase
  PORT-IN-RGP-003 → RequestDataExportUseCase
  PORT-IN-RGP-004 → RunRetentionCleanupUseCase (job planifié)
  PORT-IN-RGP-005 → DeleteUserAccountUseCase (effacement individuel)

Outbound:
  PORT-OUT-RGP-001 → DataErasurePort (suppression S3 + MongoDB)
  PORT-OUT-RGP-002 → DataExportPort (génération JSON + S3)
  PORT-OUT-RGP-003 → RetentionPolicyRepositoryPort
  PORT-OUT-RGP-004 → RgpdAuditPort
```

### Adapters
```
docai-adapter-in-rest/
└── RgpdController.java

docai-adapter-out-mongodb/
├── MongoRetentionPolicyAdapter.java
└── MongoDataErasureAdapter.java        // anonymisation PII (jamais suppression physique audit trail)

docai-adapter-out-s3/
└── S3DataErasureAdapter.java           // deleteObject AWS SDK

docai-application/
└── RetentionCleanupScheduler.java      // @Scheduled cron="0 0 2 * * *" (2h UTC)
```

---

## Endpoints

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/v1/rgpd/retention-policy` | Consulter la politique de rétention |
| PUT | `/v1/rgpd/retention-policy` | Configurer la durée (30–365 jours) |
| DELETE | `/v1/rgpd/data` | Demander l'effacement complet (HTTP 202) |
| GET | `/v1/rgpd/deletion-reports` | Historique des suppressions |
| POST | `/v1/rgpd/export` | Demander un export JSON (HTTP 202) |
| GET | `/v1/rgpd/exports` | Historique des exports |
| DELETE | `/v1/profile/account` | Suppression compte individuel |

---

## Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-RGP-001 | Documents supprimés de S3 automatiquement après rétention configurée | MUST |
| BR-RGP-002 | Durée de rétention par défaut : 90 jours (min 30, max 365) | MUST |
| BR-RGP-003 | Effacement supprime : document S3 + extraction + analyse fraude. **Conserve** : audit trail légal, factures | MUST |
| BR-RGP-004 | Demande d'effacement → rapport de suppression consultable | MUST |
| BR-RGP-005 | Champs PII chiffrés MongoDB Field Level Encryption (AWS KMS ADR-005) | MUST |
| BR-RGP-006 | Données stockées exclusivement en eu-west-3 (Paris) | MUST |
| BR-RGP-007 | Export au format JSON via S3 presigned URL (TTL 24h) | MUST |
| BR-RGP-008 | Audit trail RGPD immuable 5 ans (index TTL MongoDB) | MUST |
| BR-RGP-009 | En cas de résiliation : données supprimées dans les 90 jours | MUST |
| BR-RGP-010 | Effacement traité en < 72h (obligation RGPD art. 17) | MUST |

---

## Données PII — Chiffrement Field Level Encryption

| Champ | Collection | Niveau | Protection |
|-------|-----------|--------|-----------|
| nom, prénom | `extraction_results` | Élevé | FLE MongoDB KMS |
| dateNaissance | `extraction_results` | Élevé | FLE MongoDB KMS |
| numeroDocument (CNI/Passeport) | `extraction_results` | Très élevé | FLE MongoDB KMS |
| iban | `extraction_results` | Élevé | FLE MongoDB KMS |
| adresse | `extraction_results` | Modéré | FLE MongoDB KMS |
| numeroRpps | `extraction_results` | Modéré | FLE MongoDB KMS |
| email (invitations) | `invitation_tokens` | Modéré | Hashé après activation |
| Fichier PDF/image | Amazon S3 | Très élevé | SSE-KMS |

### Configuration FLE (application.yml)
```yaml
docai:
  mongodb:
    kms:
      provider: aws
      region: eu-west-3
      key-arn: ${AWS_KMS_KEY_ARN}
      encrypted-fields:
        - collection: extraction_results
          fields: [nom, prenom, dateNaissance, numeroDocument, iban, adresse]
```

---

## Durée de Rétention — Flow

```
Document soumis → Pipeline traité → Résultats disponibles
    │
    ▼ (après retentionDays, défaut 90j)
    ├── Fichier S3 supprimé (deleteObject)
    ├── ExtractionResult supprimé MongoDB
    ├── Document metadata anonymisé (stats conservées)
    └── FraudAnalysis conservée anonymisée (5 ans légal)
```

---

## RetentionCleanupScheduler

```java
// docai-application/scheduler/RetentionCleanupScheduler.java
@Component
public class RetentionCleanupScheduler {

    @Scheduled(cron = "0 0 2 * * *")  // Tous les jours à 2h UTC
    public void runDailyCleanup() {
        // 1. Récupérer tous les tenants avec rétention configurée
        // 2. Pour chaque tenant : trouver documents expirés (createdAt < now - retentionDays)
        // 3. Supprimer S3 + MongoDB
        // 4. Mettre à jour DeletionReport
        // 5. Envoyer email confirmation si effacement manuel demandé
    }
}
```

---

## Nommage Obligatoire (Annexe B)

| Classe | Module | Description |
|--------|--------|-------------|
| `ConfigureRetentionPolicyUseCase` | Application | Modifie la politique de rétention |
| `RequestDataErasureUseCase` | Application | Lance l'effacement asynchrone |
| `RunRetentionCleanupUseCase` | Application | Job quotidien 2h UTC |
| `S3DataErasureAdapter` | Adapter OUT | deleteObject AWS S3 |
| `MongoDataErasureAdapter` | Adapter OUT | Anonymisation PII MongoDB |
| `DataExportGeneratorAdapter` | Adapter OUT | JSON export + presigned URL |
| `RetentionPolicy` | Domain | Value Object (retentionDays 30–365) |
| `DeletionReport` | Domain | Aggregate statut effacement |
| `DataExport` | Domain | Aggregate export portabilité |

---

## Commons à Utiliser (ne pas réimplémenter)

- `commons-audit` → `@Audited` sur `RequestDataErasureUseCase`, `RunRetentionCleanupUseCase`
- `commons-multitenancy` → isolation tenant sur toutes les suppressions et exports
- `commons-api` → `ProblemDetail` pour erreurs RGPD (RFC 7807)

---

## Scénarios BDD Obligatoires

```gherkin
Feature: RGPD & Privacy

  Scenario: Rétention automatique — suppression après expiration
    Given le tenant "alpha-corp" a une rétention configurée à 90 jours
    And un document soumis il y a 91 jours
    When le job de rétention quotidien s'exécute
    Then le fichier S3 est supprimé
    And l'ExtractionResult est supprimé de MongoDB
    And le document metadata est anonymisé (statistiques conservées)

  Scenario: Droit à l'effacement — suppression complète
    Given le TENANT_ADMIN de "beta-corp" demande l'effacement
    When "DELETE /v1/rgpd/data" est appelé
    Then la réponse est HTTP 202
    And tous les fichiers S3 de "beta-corp" sont supprimés
    And tous les ExtractionResults sont supprimés
    And les AuditEntries sont anonymisées (userId masqué)
    And un email de confirmation est envoyé

  Scenario: Export portabilité
    Given le TENANT_ADMIN de "gamma-corp" demande un export
    When "POST /v1/rgpd/export" est appelé
    Then la réponse est HTTP 202
    And un fichier JSON est généré de façon asynchrone
    And un email avec lien S3 signé (24h) est envoyé

  Scenario: Chiffrement PII au repos
    Given un document avec CNI "123456789012"
    When l'extraction est persistée en MongoDB
    Then le champ "numeroDocument" est stocké chiffré (FLE)
    And une lecture directe en base ne révèle pas la valeur en clair
```

---

## NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-RGP-001 | Job rétention : 100% documents expirés supprimés en < 24h | 100% |
| NFR-RGP-002 | Droit à l'effacement traité en < 72h | 100% |
| NFR-RGP-003 | Export données disponible en < 24h | 100% |
| NFR-RGP-004 | Données stockées en eu-west-3 uniquement | 100% |
| NFR-RGP-005 | Chiffrement S3 SSE-KMS activé | 100% |

---

## Definition of Done

- [ ] Job rétention quotidien testé (documents expirés supprimés S3 + MongoDB)
- [ ] Droit à l'effacement end-to-end (suppression asynchrone + email confirmation)
- [ ] Export données testé (JSON + lien S3 signé envoyé par email)
- [ ] Field Level Encryption MongoDB activé sur tous les champs PII
- [ ] Chiffrement S3 SSE-KMS activé (vérifié AWS Console)
- [ ] Données en eu-west-3 (vérifié en configuration)
- [ ] AuditEntries RGPD conservées 5 ans (TTL index MongoDB vérifié)
- [ ] Rapport de suppression généré et consultable
- [ ] Anonymisation AuditEntries testée (PII masquées après effacement)
- [ ] Couverture domaine ≥ 90%

---

## Logs Obligatoires

```
INFO  — Document supprimé (rétention) : documentId, tenantId, s3Key — PAS le contenu
INFO  — Demande effacement reçue : tenantId, requestedBy=[PII_MASKED], scope
INFO  — Export données généré : tenantId, s3ExportKey, expiresAt
ERROR — Échec suppression S3 : documentId, s3Key, raison
WARN  — Déchiffrement PII : userId=[PII_MASKED], documentId, action
```
> Jamais de PII dans les logs → `[PII_MASKED]`. Toujours `traceId` + `tenantId`.
