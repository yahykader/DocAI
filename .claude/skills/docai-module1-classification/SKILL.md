---
name: docai-module1-classification
description: "Implémente le Module 1.2 DocAI (Classification IA automatique : ClassificationKafkaConsumer, VisionModelAdapter GPT-4o, FallbackRuleBasedClassifier heuristiques, Circuit Breaker Resilience4j seuil 50%/10 calls, ConfidenceScore 0.0-1.0, seuil ≥ 0.85 CLASSIFIED / ( 0.70 NEEDS_REVIEW, correction manuelle AuditEntry immuable, event DocumentClassified, partition key documentId ADR-002, cache classification Valkey ADR-003). Utiliser quand on demande d'implémenter la classification automatique de documents, le consumer Kafka post-upload, l'appel au modèle de vision IA, le fallback heuristique, la queue de révision manuelle, ou la correction de type documentaire. Prérequis : Module 1.1 (Upload) terminé."
---

# Module 1.2 — Classification Automatique de Documents

> **Prérequis :** Module 1.1 (Upload) terminé. ADR-002 (partition key) et ADR-003 (TTL jitter) lus.  
> **Durée estimée :** 3 semaines  
> **Jalon :** Document uploadé → classifié FACTURE avec score 0.95 en < 5s.

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/document/classification/
├── ClassificationResult.java   // Value Object (documentType, confidenceScore, modelVersion)
├── ConfidenceScore.java        // Value Object (Double 0.0–1.0, exception si hors bornes)
├── ClassificationDecision.java // Enum (CLASSIFIED, LOW_CONFIDENCE, NEEDS_REVIEW)
└── events/
    ├── DocumentClassified.java    // score ≥ 0.70
    ├── DocumentNeedsReview.java   // score < 0.70
    └── ClassificationCorrected.java // correction manuelle
```

**Règles de seuil :**

| Score | Décision | État Document |
|-------|---------|---------------|
| ≥ 0.85 | CLASSIFIED (haute confiance) | CLASSIFIED |
| 0.70–0.84 | CLASSIFIED + flag LOW_CONFIDENCE | CLASSIFIED |
| < 0.70 | NEEDS_REVIEW | NEEDS_REVIEW |
| Circuit Breaker OPEN | Fail-safe | NEEDS_REVIEW |

### Ports
```
Inbound:
  PORT-IN-CLS-001 → ClassifyDocumentUseCase
  PORT-IN-CLS-002 → OverrideClassificationUseCase   (correction manuelle)
  PORT-IN-CLS-003 → GetReviewQueueUseCase            (queue révision)

Outbound:
  PORT-OUT-CLS-001 → ClassificationModelPort         (appel IA ou heuristiques)
  PORT-OUT-CLS-002 → ClassificationRepositoryPort
  PORT-OUT-CLS-003 → ClassificationCachePort         (Valkey ADR-003)
```

### Adapters
```
docai-adapter-in-kafka/
└── ClassificationKafkaConsumer.java    // Consomme docai.doc.uploaded

docai-adapter-in-rest/
└── ClassificationCorrectionController.java  // PUT /v1/documents/{id}/classification

docai-adapter-out-llm/
├── VisionModelAdapter.java             // GPT-4o — implémente ClassificationModelPort
└── FallbackRuleBasedClassifier.java    // Heuristiques — Null Object Pattern

docai-adapter-out-mongodb/
└── ClassificationResultMongoAdapter.java   // Collection classification_results (Mongock V009)

docai-adapter-out-valkey/
└── ValkeyClassificationCacheAdapter.java   // Cache SHA-256 → résultat, TTL 24h ± jitter ADR-003
```

---

## ClassificationKafkaConsumer

```java
@Component
public class ClassificationKafkaConsumer extends ResilientKafkaConsumer<DocumentUploadedEvent> {

    // group-id obligatoire : docai.recognition.classification.group
    @KafkaListener(
        topics = "docai.doc.uploaded",
        groupId = "docai.recognition.classification.group"
    )
    public void consume(ConsumerRecord<String, DocumentUploadedEvent> record,
                        Acknowledgment ack) {
        // processWithIdempotence gère : idempotence Valkey (topic:partition:offset),
        // DLQ après 3 échecs, MDC, TenantContext automatique
        processWithIdempotence(record, () -> {
            DocumentUploadedEvent event = record.value();
            TenantContext.set(event.getTenantId());
            try {
                classifyDocumentUseCase.classify(event.getDocumentId(), event.getTenantId());
            } finally {
                TenantContext.clear();
            }
        });
        ack.acknowledge();
    }
}
```

> `TenantContext.get()` est utilisé dans le use case — jamais `TenantContext.getTenantId()`.
```

---

## ClassifyDocumentUseCase — Flux

```
1. Vérifier idempotence offset Kafka (commons-kafka)
2. Récupérer fichier depuis S3 (streaming, pas en mémoire complète)
3. Vérifier cache Valkey (clé = SHA-256 du contenu — ADR-003)
     Si hit → retourner résultat en cache (pas d'appel IA)
4. Appeler ClassificationModelPort (Circuit Breaker Resilience4j)
     Si OPEN → FallbackRuleBasedClassifier
5. Parser résultat → ClassificationResult (type + score)
6. Appliquer règle de seuil → ClassificationDecision
7. Mettre en cache (TTL 24h ± 30min jitter — ADR-003)
8. Mettre à jour état Document (CLASSIFIED ou NEEDS_REVIEW)
9. Publier event via Outbox :
     - score ≥ 0.70 → DocumentClassified (topic docai.doc.classified)
     - score < 0.70 → DocumentNeedsReview (topic docai.doc.needs-review)
     partitionKey = documentId (ADR-002 obligatoire)
10. AuditEntry sur chaque classification
```

---

## VisionModelAdapter — Prompt Obligatoire

**Modèle :** `gpt-4o` (OpenAI Vision). Alternative : `mistral-pixtral-large` (Circuit Breaker).

```java
// Prompt système (System Message)
private static final String SYSTEM_PROMPT = """
    Tu es un expert en classification de documents administratifs français.
    Tu reçois une image ou une page de document et tu dois identifier son type.
    Réponds UNIQUEMENT en JSON valide, sans texte avant ou après.
    Ne fais jamais de suppositions — si tu n'es pas sûr, utilise AUTRE.
    """;

// Format de réponse attendu
// {"type": "FACTURE", "confidence": 0.95, "reasoning": "Présence SIRET, TVA, montant HT"}

// Types reconnus (DocumentType enum)
// FACTURE, RIB, CNI, ORDONNANCE, BULLETIN_SALAIRE, AUTRE
```

**Paramètres API :**
```java
model = "gpt-4o"
temperature = 0.0f    // Déterministe — pas de créativité sur la classification
max_tokens = 200
response_format = { "type": "json_object" }
```

---

## FallbackRuleBasedClassifier (Null Object Pattern)

Utilisé quand le Circuit Breaker est OPEN ou le modèle indisponible.

```java
// Heuristiques par mots-clés
FACTURE:           ["SIRET", "montant HT", "TVA", "facture", "N° de facture"]
RIB:               ["IBAN", "BIC", "domiciliation", "relevé d'identité bancaire"]
CNI:               ["carte nationale d'identité", "lieu de naissance", "nationalité française"]
ORDONNANCE:        ["ordonnance", "RPPS", "médicament", "posologie", "prescripteur"]
BULLETIN_SALAIRE:  ["salaire brut", "cotisations", "net à payer", "bulletin de paie"]

// Score calculé : nbMotsClésDetectés / nbMotsClésTotauxDuType
// Si aucun type > 0.30 → AUTRE avec score 0.30 (→ NEEDS_REVIEW automatique)
```

---

## Circuit Breaker Resilience4j

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      classification-model:
        failure-rate-threshold: 50      # OPEN si 50% d'échecs
        minimum-number-of-calls: 10     # Sur les 10 derniers appels
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
```

```java
// VisionModelAdapter.java
@CircuitBreaker(name = "classification-model", fallbackMethod = "fallbackClassify")
public ClassificationResult classify(byte[] documentContent) {
    // Appel GPT-4o
}

public ClassificationResult fallbackClassify(byte[] documentContent, Exception ex) {
    log.warn("Circuit Breaker OPEN — fallback heuristiques : {}", ex.getMessage());
    return fallbackClassifier.classify(documentContent);
    // Le document passera en NEEDS_REVIEW si score < 0.70
}
```

---

## Correction Manuelle

```
PUT /v1/documents/{id}/classification
Rôles : ANALYST, TENANT_ADMIN
Body : { "documentType": "FACTURE", "justification": "..." }

Flux :
1. Vérifier document en état NEEDS_REVIEW (sinon HTTP 409)
2. Enregistrer AuditEntry immuable (userId, oldType, newType, justification, timestamp)
3. Mettre à jour ClassificationResult (type forcé, flaggedAsManual=true)
4. Publier ClassificationCorrected → reprendre le pipeline
5. Invalider cache Valkey (clé SHA-256 contenu)
```

---

## Migration Mongock — V009

```java
// V009_setup_classification_results.java
@ChangeUnit(id = "V009_setup_classification_results", order = "009")
public class V009SetupClassificationResults {
    @Execution
    public void execute(MongoDatabase db) {
        db.createCollection("classification_results");
        // Index unique par document
        createIndex(db, "classification_results",
            Indexes.ascending("documentId"), new IndexOptions().unique(true));
        // Index queue révision
        createIndex(db, "classification_results",
            Indexes.compoundIndex(
                Indexes.ascending("tenantId"),
                Indexes.ascending("needsReview"),
                Indexes.ascending("createdAt")));
    }
}
```

---

## Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-REC-010 | Classification déclenchée automatiquement sur réception `DocumentUploaded` | MUST |
| BR-REC-011 | Score de confiance : flottant 0.0–1.0 (exception si hors bornes) | MUST |
| BR-REC-012 | Score ≥ 0.85 → classification automatique | MUST |
| BR-REC-013 | Score 0.70–0.84 → classification + flag `LOW_CONFIDENCE` | MUST |
| BR-REC-014 | Score < 0.70 → `NEEDS_REVIEW` + queue révision manuelle | MUST |
| BR-REC-015 | Appel modèle protégé Circuit Breaker Resilience4j | MUST |
| BR-REC-016 | CB OPEN → fallback heuristiques → NEEDS_REVIEW (jamais crash) | MUST |
| BR-REC-017 | `DocumentClassified` inclut type, score, modelVersion | MUST |
| BR-REC-018 | partitionKey Kafka = documentId (ADR-002) | MUST |
| BR-REC-019 | Cache résultat : SHA-256 contenu → TTL 24h ± 30min jitter (ADR-003) | MUST |

---

## Scénarios BDD Obligatoires

```gherkin
Feature: Classification automatique de documents

  Scenario: Classification réussie haute confiance
    Given un event DocumentUploaded sur docai.doc.uploaded
    When le modèle retourne type=FACTURE score=0.95
    Then le document passe en état CLASSIFIED
    And l'event DocumentClassified est publié sur docai.doc.classified
    And la clé de partition Kafka est le documentId

  Scenario: Faible confiance → révision manuelle
    Given un document dont le scan est de mauvaise qualité
    When le modèle retourne score=0.55
    Then le document passe en état NEEDS_REVIEW
    And l'event DocumentNeedsReview est publié
    And aucun pipeline d'extraction n'est déclenché

  Scenario: Circuit Breaker OPEN → fallback fail-safe
    Given le Circuit Breaker ClassificationModel est OPEN
    When le consumer tente la classification
    Then le FallbackRuleBasedClassifier est utilisé
    And le document passe en NEEDS_REVIEW (pas de crash pipeline)

  Scenario: Idempotence consumer Kafka
    Given un event DocumentUploaded déjà traité (même offset)
    When le consumer reçoit l'event une seconde fois
    Then la classification n'est pas relancée
    And aucun doublon en base

  Scenario: Correction manuelle par un ANALYST
    Given un document en état NEEDS_REVIEW avec type=AUTRE
    When l'ANALYST corrige le type vers FACTURE avec justification
    Then l'AuditEntry immuable est créée
    And l'event ClassificationCorrected déclenche la reprise du pipeline
```

---

## NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-REC-006 | Latence classification (P95) | < 5s |
| NFR-REC-007 | Précision sur jeu de test labellisé | ≥ 92% |
| NFR-REC-008 | Disponibilité avec fallback actif | ≥ 99.5% |
| NFR-REC-009 | Taux documents routés en révision manuelle | < 8% |

---

## Commons à Utiliser

- `commons-kafka` → `ResilientKafkaConsumer` (retry, DLQ auto, idempotence Valkey, tracing)
- `commons-outbox` → publication `DocumentClassified` avec partitionKey=documentId (ADR-002)
- `commons-multitenancy` → filtre tenant sur `DocumentMongoAdapter`
- `commons-audit` → `@Audited` sur `OverrideClassificationUseCase`
- `commons-testing` → `DocumentTestBuilder`, stubs WireMock modèle vision
- `docai-resilience` → Circuit Breaker + Retry sur `VisionModelAdapter`

---

## Definition of Done

- [ ] CB VisionModel testé : CLOSED → OPEN → HALF_OPEN → CLOSED
- [ ] Fallback testé : modèle indisponible → NEEDS_REVIEW (pas de blocage)
- [ ] Consumer Kafka idempotent : même offset 2× → traitement 1× seulement
- [ ] Clé partition Kafka = documentId vérifiée dans Kafka UI (ADR-002)
- [ ] Correction manuelle auditée (AuditEntry immuable : userId, avant, après, timestamp)
- [ ] Règle score ≤ 0.69 → NEEDS_REVIEW, score = 0.70 → CLASSIFIED
- [ ] Cache classification Valkey : SHA-256 + jitter TTL testé (ADR-003)
- [ ] Métriques : `docai_classification_duration_seconds`, `docai_classification_confidence_score`
- [ ] Couverture domaine ≥ 90%

---

## Logs Obligatoires

```
INFO  — Classification réussie : documentId, tenantId, type, score, modelVersion, durationMs
WARN  — Score faible (< 0.85) : documentId, tenantId, score, action=NEEDS_REVIEW
WARN  — Circuit Breaker OPEN : service=VisionModel, documentId, action=FALLBACK
INFO  — Correction manuelle : documentId, tenantId, oldType, newType, correctedBy=[PII_MASKED]
```
> Jamais de PII dans les logs → `[PII_MASKED]`. Toujours `traceId` + `tenantId`.
