# DocAI — Détails complets des 11 ADR

> Référence : DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 7 (Annexe E)

---

## ADR-001 — Concurrence compteurs quota (Lua atomique)

**Problème :** Sans atomicité, 50 uploads simultanés peuvent tous lire le compteur avant qu'il soit incrémenté → le tenant dépasse son quota sans être bloqué.

**Décision :** Script Lua exécuté en une seule opération atomique sur Valkey.

```lua
-- Script Lua atomique (ValkeyQuotaAdapter)
local current = redis.call('GET', KEYS[1])
if current == false then current = 0 end
if tonumber(current) >= tonumber(ARGV[1]) then
  return -1  -- Quota dépassé
end
redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
return tonumber(current) + 1
```

**Règle :** JAMAIS `GET` puis `INCR` séparés — toujours le script Lua atomique.
**Modules :** Module 1 (Upload), Module 7 (Billing overage)

---

## ADR-002 — Ordering events Kafka par document

**Problème :** Avec `tenantId` comme clé de partition, deux events du même document peuvent aller sur des partitions différentes et être traités dans le mauvais ordre.

**Décision :** Clé de partition = `documentId` (String UUID) sur tous les topics pipeline.

```
Topics pipeline (clé = documentId) :
  docai.doc.uploaded, docai.doc.classified, docai.doc.extracted,
  docai.doc.fraud.analyzed, docai.doc.completed

Exception autorisée (clé = tenantId) :
  docai.doc.dlq, topics analytics, topics notifications
```

**Vérification :** Kafka UI → filtrer par `documentId` → tous les events sur la même partition, ordre chronologique.
**Modules :** TOUS les modules (via `OutboxKafkaProducer`)

---

## ADR-003 — Thundering Herd cache Valkey (jitter TTL)

**Problème :** Sans jitter, 1 000 clés créées simultanément expirent simultanément → pic de charge sur LLM/APIs.

**Décision :** TTL = valeur de base ± 10% aléatoire pour tout TTL > 1h.

```java
// JitterTtl (dans commons-kafka)
public static Duration withJitter(Duration base) {
    long jitterMs = (long)(base.toMillis() * 0.1 * (Math.random() * 2 - 1));
    return base.plusMillis(jitterMs);
}

// Exemples d'utilisation obligatoire
Duration extractionTtl = JitterTtl.withJitter(Duration.ofHours(24)); // 21.6h–26.4h
Duration inseeApiTtl   = JitterTtl.withJitter(Duration.ofDays(7));   // 6.3j–7.7j
```

**Exception :** TTL idempotence (`topic:partition:offset`) = 24h fixe (précision requise).
**Modules :** Module 2 (extraction cache), Module 3 (validation cache), Valkey adapter

---

## ADR-004 — Limite transaction MongoDB 4MB

**Problème :** Le texte OCR brut d'un PDF de 200 pages peut dépasser 4MB → transaction MongoDB échoue.

**Décision :** Le texte OCR brut (`rawOcrText`) est stocké dans S3, jamais dans MongoDB. Seule la clé S3 (`rawOcrTextS3Key`) est persistée en base.

```
Structure MongoDB (Module 2) :
  ExtractionResult {
    documentId, tenantId, extractionMethod, globalScore,
    fields[],
    rawOcrTextS3Key: "acme-corp/ocr/doc-123/raw-text.txt",  ← Clé S3
    // rawOcrText: INTERDIT EN BASE               ← Violation ADR-004
  }
```

**Clé S3 :** `{tenantId}/ocr/{documentId}/raw-text.txt`
**Modules :** Module 2 (OCR + Extraction)

---

## ADR-005 — Rotation des clés de chiffrement PII

**Problème :** Sans rotation des clés KMS, une clé compromise expose l'historique complet des PII.

**Décision :** AWS KMS avec rotation automatique annuelle. Alias : `alias/docai-pii-{environment}`.

```yaml
# Terraform — module KMS
resource "aws_kms_key" "pii" {
  description             = "DocAI PII encryption key"
  enable_key_rotation     = true       # Rotation annuelle automatique
  deletion_window_in_days = 30
}
# Key policy : accès uniquement au rôle IAM application
# CloudTrail logging de chaque utilisation
```

**Procédure rotation manuelle si compromission :**
1. Générer nouvelle clé KMS
2. Mettre à jour secret dans AWS Secrets Manager
3. Rechiffrer les données existantes (script migration)
4. Documenter dans journal de rotation

**Modules :** Module 0.5 (RGPD), adapter MongoDB (Field Level Encryption)

---

## ADR-006 — Fallback Keycloak indisponible (cache JWKS 1h)

**Problème :** Si Keycloak est down et que les clés publiques JWKS ne sont pas en cache, Spring Security ne peut plus valider les JWT → tous les utilisateurs bloqués.

**Décision :** Spring Security OAuth2 Resource Server cache les JWKS en local (TTL 1h, refresh 30 min). Keycloak déployé en 2 instances minimum en production.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/certs
          # Spring Security cache les clés localement 1h
```

**Test chaos correspondant :** Arrêter Keycloak 20 min → utilisateurs connectés non bloqués pendant 1h.
**Modules :** Module 0 (Sécurité), docai-security-keycloak skill

---

## ADR-007 — Nettoyage uploads S3 multipart non finalisés

**Problème :** Si une connexion est coupée à 80% d'un upload, les parties S3 restent — invisibles mais facturées indéfiniment.

**Décision :** 2 mécanismes combinés.

```java
// AwsS3StorageAdapter — Structure OBLIGATOIRE
public String upload(InputStream stream, String key) {
    String uploadId = s3.createMultipartUpload(key).uploadId();
    try {
        // Uploader chaque partie séquentiellement
        List<CompletedPart> parts = uploadParts(stream, uploadId, key);
        s3.completeMultipartUpload(key, uploadId, parts);
        return key;
    } catch (Exception e) {
        // OBLIGATOIRE : annuler immédiatement si erreur
        s3.abortMultipartUpload(key, uploadId);
        throw new StorageException("Upload failed", e);
    }
}
```

```hcl
# Terraform — S3 Lifecycle Rule (filet de sécurité 24h)
resource "aws_s3_bucket_lifecycle_configuration" "docai" {
  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"
    abort_incomplete_multipart_upload { days_after_initiation = 1 }
  }
}
```

**Test obligatoire :** Simuler coupure réseau à mi-upload → vérifier `AbortMultipartUpload` appelé.
**Modules :** Module 1 (Upload), Terraform S3

---

## ADR-008 — Mémoire JVM et TestContainers GitHub Actions

**Problème :** Runners GitHub Actions = 7GB RAM. Spring Boot + TestContainers dépassent cette limite → OOM aléatoires.

**Décision :** 3 jobs CI distincts + limites JVM.

```yaml
# 01-ci.yml — 3 jobs séparés (ADR-008)
jobs:
  unit-tests:      # Sans Docker — docai-domain + docai-application
    env: { MAVEN_OPTS: "-Xmx512m" }

  integration-tests: # TestContainers MongoDB + Kafka + Valkey
    env: { MAVEN_OPTS: "-Xmx512m", TESTCONTAINERS_REUSE_ENABLE: "true" }

  bdd-tests:       # Cucumber complet — tous les services
    env: { MAVEN_OPTS: "-Xmx512m", TESTCONTAINERS_REUSE_ENABLE: "true" }
```

```java
// AbstractIntegrationTest (commons-testing) — reuse obligatoire
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7")
        .withReuse(true);  // ADR-008 : reuse
}
```

**Modules :** CI/CD, commons-testing

---

## ADR-009 — Downgrade de plan et données orphelines

**Problème :** Quand un tenant downgrade (Pro → Starter), ses 3 000 documents Pro sont-ils encore visibles ?

**Décision :** Conservation complète en lecture seule. Les données ne sont JAMAIS supprimées lors d'un downgrade.

```
Règles ADR-009 :
  ✅ Historique complet reste lisible après downgrade
  ✅ Nouveau quota s'applique au début du mois suivant le downgrade
  ✅ Les documents en NEEDS_REVIEW restent accessibles pour révision
  ❌ Les données ne sont jamais supprimées lors d'un downgrade
```

**Modules :** Module 7 (Billing), Module 5 (Dashboard — affichage lecture seule post-downgrade)

---

## ADR-010 — Scalabilité index MongoDB grandes collections

**Problème :** À 10M+ documents, les requêtes sans index couvrant font des collection scans → timeouts.

**Décision :** 3 règles obligatoires.

```java
// Règle 1 : EXPLAIN PLAN avant chaque merge
// Commande à exécuter en DEV avant toute PR :
db.documents.find({tenantId: "acme", status: "COMPLETED"})
            .explain("executionStats")
// Vérifier : "IXSCAN" (bon) vs "COLLSCAN" (bloquant)

// Règle 2 : Partial index si actif < 20% de la collection
@Document(collection = "documents")
public class DocumentMongoDocument {
    // Index partiel pour les documents actifs uniquement
    // Mongock migration : createIndex({status:1}, {partialFilterExpression: {status:{$in:["PENDING","CLASSIFIED"]}}})
}

// Règle 3 : Archivage S3 Glacier après rétention
// Terraform Lifecycle Rule : transition après {retention_days}
```

**Index obligatoires :** `{tenantId, status, createdAt}`, `{tenantId, uploadedAt}`, `{contentHash, tenantId}` (déduplication)
**Modules :** TOUS les modules MongoDB

---

## ADR-011 — Cohérence et resynchronisation Read Model CQRS

**Problème :** Si le consumer Kafka du Read Model tombe pendant 10 min, le dashboard affiche des données obsolètes.

**Décision :** `lastSyncedAt` + job de réconciliation + endpoint de reconstruction.

```java
// DashboardProjectionConsumer — met à jour lastSyncedAt sur chaque event
public void handleDocumentCompleted(DocumentCompleted event) {
    documentSummaryViewRepository.updateStatus(
        event.documentId(), event.tenantId(),
        "COMPLETED", Instant.now()  // ← lastSyncedAt
    );
}

// ReadModelReconciliationScheduler — toutes les 5 minutes
@Scheduled(fixedDelay = 300_000)
public void reconcile() {
    Instant threshold = Instant.now().minus(30, SECONDS);
    List<String> lagging = documentRepository.findByLastSyncedAtBefore(threshold);
    lagging.forEach(this::resync);
    if (lagging.size() > 10) {
        alertService.alert("Read Model lag > 30s pour " + lagging.size() + " documents");
    }
}
```

**Métriques Prometheus :**
- `docai_read_model_sync_lag_seconds` — Histogram délai write-side → Read Model
- `docai_read_model_desync_total` — Nombre de désynchronisations
- `docai_read_model_resync_total` — Nombre de resynchronisations

**Modules :** Module 5.1 (Dashboard Read Model), docai-cqrs-readmodel skill
