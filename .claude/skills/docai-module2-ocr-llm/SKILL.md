---
name: docai-module2-ocr-llm
description: "Implémente le Module 2.1 DocAI (Pipeline OCR + LLM : PdfBoxOcrAdapter PDFBox 3.x, Tess4JOcrAdapter Tess4J 5.x, OpenAiLlmAdapter gpt-4o temperature 0.0, MistralLlmAdapter Feature Flag, cache Valkey SHA-256 TTL 24h jitter ADR-003, ADR-004 rawOcrText→S3, schémas extraction 6 types, scores confiance, fallback NEEDS_REVIEW Circuit Breaker, ExtractionKafkaConsumer). Utiliser quand on demande d'implémenter l'OCR, l'extraction LLM, le pipeline d'extraction, les prompts LLM, le cache extraction, ou le consumer Kafka d'extraction. Prérequis : Module 1.2 Classification terminé."
---

# DocAI — Module 2.1 OCR & Extraction LLM
## PDFBox · Tess4J · OpenAI · Cache SHA-256 · ADR-003 · ADR-004

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 5 (Module 2, Phase 2.1)
> **Prérequis :** Module 1.2 Classification terminé. ADR-003 (jitter TTL) et ADR-004 (OCR→S3) compris.
> **Durée estimée :** 3 semaines

---

## 1. Business Rules

| ID | Règle |
|----|-------|
| BR-EXT-001 | L'extraction est déclenchée par réception de `DocumentClassified` via Kafka |
| BR-EXT-002 | Pipeline obligatoire : Prétraitement → OCR → LLM → Parsing → Cache → Persistance |
| BR-EXT-003 | Chaque champ extrait porte un score de confiance individuel (0.0–1.0) |
| BR-EXT-004 | Le score global = moyenne pondérée des champs **obligatoires** uniquement |
| BR-EXT-005 | Schéma d'extraction différent par type de document |
| BR-EXT-006 | Appels LLM protégés par Circuit Breaker + Retry + Bulkhead (voir docai-stack-technique) |
| BR-EXT-007 | Circuit Breaker LLM OPEN → fallback OCR basique → état NEEDS_REVIEW |
| BR-EXT-008 | Cache Valkey consulté avant tout appel LLM (clé = SHA-256 contenu fichier) |

---

## 2. Schémas d'extraction par type

| Type | Champs obligatoires |
|------|---------------------|
| `FACTURE` | émetteur.siret, émetteur.raisonSociale, numéroFacture, dateEmission, montantHT, tauxTVA, montantTVA, montantTTC |
| `CNI` | nom, prénom, dateNaissance, numéroDocument, dateExpiration |
| `PASSEPORT` | nom, prénom, nationalité, numéroPasseport, dateExpiration, MRZ |
| `RIB` | titulaire, IBAN, BIC |
| `ORDONNANCE` | médecin.nom, médecin.RPPS, patient.nom, patient.dateNaissance, datePrescription, médicaments[] |
| `BULLETIN_SALAIRE` | employé.nom, employeur.siret, période, salaireNet, salaireBrut |

---

## 3. Scores de confiance

| Score | Signification |
|-------|--------------|
| 1.0 | Valeur extraite et validée algorithmiquement (SIRET Luhn OK, IBAN modulo 97 OK) |
| 0.9 | Valeur extraite avec format correct (date valide, montant positif) |
| 0.7 | Valeur extraite mais format non vérifié |
| 0.5 | Valeur extraite avec doute (OCR flou, caractères ambigus) |
| 0.0 | Valeur null (champ absent ou illisible) |

**Score global :** Moyenne pondérée des champs obligatoires uniquement.

---

## 4. ADR-004 — Texte OCR → S3 obligatoire

```java
// ExtractionResult — Aggregate
public class ExtractionResult {
    private final String documentId;
    private final String tenantId;
    private final Map<String, ExtractedField> fields;
    private final double globalScore;
    private final ExtractionStatus status;
    private final String rawOcrTextS3Key;   // ← Clé S3 UNIQUEMENT (ADR-004)
    // private final String rawOcrText;     // ← INTERDIT EN BASE (ADR-004)
    private final Instant extractedAt;
}

// Clé S3 : {tenantId}/ocr/{documentId}/raw-text.txt
```

**Pourquoi :** PDF de 200 pages → texte OCR > 4MB → MongoDB transaction échoue. Le texte brut va dans S3, seule la clé est stockée en base.

---

## 5. Pipeline OCR — Adapter PDFBox + Tess4J

```java
// PdfBoxOcrAdapter — implements OcrPort
@Component
public class PdfBoxOcrAdapter implements OcrPort {

    @Override
    public OcrResult extract(InputStream stream, String mimeType, String documentId, String tenantId) {
        if ("application/pdf".equals(mimeType)) {
            String text = extractNativeText(stream);
            if (!text.isBlank()) {
                // PDF texte natif — pas besoin d'OCR
                String s3Key = storeInS3(text, tenantId, documentId);
                return OcrResult.success(s3Key, "PDFBOX_NATIVE");
            }
        }
        // PDF scanné ou image → déléguer à Tess4J
        return tess4jOcrAdapter.extract(stream, mimeType, documentId, tenantId);
    }

    private String extractNativeText(InputStream stream) {
        try (PDDocument doc = Loader.loadPDF(stream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc).trim();
        }
    }
}

// Tess4JOcrAdapter — implements OcrPort
@Component
public class Tess4JOcrAdapter implements OcrPort {

    @CircuitBreaker(name = "ocr")
    @Bulkhead(name = "ocr")
    @TimeLimiter(name = "ocr")  // 60s max
    public OcrResult extract(InputStream stream, ...) {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tessdata");
        tesseract.setLanguage("fra");           // Français
        tesseract.setPageSegMode(3);            // Auto page segmentation
        tesseract.setOcrEngineMode(1);          // LSTM only

        // Preprocessing : augmentation contraste, 300 DPI
        BufferedImage preprocessed = preprocess(ImageIO.read(stream));
        String text = tesseract.doOCR(preprocessed);

        String s3Key = storeInS3(text, tenantId, documentId);
        return OcrResult.success(s3Key, "TESS4J_OCR");
    }
}
```

---

## 6. LLM Extraction — Prompts obligatoires

### Prompt système commun

```
Tu es un expert en extraction d'informations de documents administratifs français.
Le texte fourni est extrait par OCR — il peut contenir des erreurs de reconnaissance.
Extrait uniquement les informations demandées. Si un champ est absent ou illisible,
utilise null. Ne jamais inventer une valeur. Réponds UNIQUEMENT en JSON valide.
```

### Prompt FACTURE (exemple)

```
Extrait les informations de cette facture française et retourne ce JSON :
{
  "emetteur": { "siret": "string|null", "raisonSociale": "string|null", "adresse": "string|null" },
  "numeroFacture": "string|null",
  "dateEmission": "YYYY-MM-DD|null",
  "montantHT": "number|null",
  "tauxTVA": "number|null",
  "montantTVA": "number|null",
  "montantTTC": "number|null",
  "destinataire": { "siret": "string|null", "raisonSociale": "string|null" }
}
Texte OCR : {{ocrText}}
```

**Paramètres API OpenAI :**
- model : `gpt-4o`
- temperature : **0.0** (extraction factuelle — aucune créativité)
- response_format : `{ type: "json_object" }`
- max_tokens : 1000

```java
// OpenAiLlmAdapter — implements LlmPort
@Component
public class OpenAiLlmAdapter implements LlmPort {

    @CircuitBreaker(name = "llm", fallbackMethod = "fallbackExtract")
    @Retry(name = "llm")
    @Bulkhead(name = "llm")
    @TimeLimiter(name = "llm")  // 30s max
    public ExtractionResponse extract(String ocrText, DocumentType type) {
        String prompt = loadPrompt(type).replace("{{ocrText}}", ocrText);

        ChatResponse response = chatClient.call(
            new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(prompt)
            ),
            OpenAiChatOptions.builder()
                .withModel("gpt-4o")
                .withTemperature(0.0f)
                .withResponseFormat(new ResponseFormat("json_object"))
                .build()
            )
        );

        return parseResponse(response.getResult().getOutput().getContent());
    }

    // Fallback : Circuit Breaker OPEN → NEEDS_REVIEW
    public ExtractionResponse fallbackExtract(String ocrText, DocumentType type, Exception e) {
        log.warn("Circuit Breaker LLM OPEN documentType={} fallback=PARTIAL", type);
        return ExtractionResponse.partial(); // → NEEDS_REVIEW
    }
}
```

---

## 7. Cache Valkey — SHA-256 (ADR-003)

```java
// ValkeyExtractionCacheAdapter — implements ExtractionCachePort
@Component
public class ValkeyExtractionCacheAdapter implements ExtractionCachePort {

    // Clé = SHA-256 du contenu fichier (pas du documentId)
    // Même fichier soumis 2× par 2 tenants → même clé mais résultats séparés
    private String buildKey(String contentHash, String tenantId) {
        return "extraction:" + tenantId + ":" + contentHash;
    }

    @Override
    public Optional<ExtractionResult> get(String contentHash, String tenantId) {
        String key = buildKey(contentHash, tenantId);
        String cached = valkey.opsForValue().get(key);
        if (cached == null) {
            meterRegistry.counter("docai_cache_miss_total", "module", "extraction").increment();
            return Optional.empty();
        }
        meterRegistry.counter("docai_cache_hit_total", "module", "extraction").increment();
        return Optional.of(objectMapper.readValue(cached, ExtractionResult.class));
    }

    @Override
    public void put(String contentHash, String tenantId, ExtractionResult result) {
        String key = buildKey(contentHash, tenantId);
        // ADR-003 : jitter ±10% sur TTL 24h
        Duration ttl = JitterTtl.withJitter(Duration.ofHours(24)); // ~21.6h–26.4h
        valkey.opsForValue().set(key, objectMapper.writeValueAsString(result), ttl);
    }
}
```

---

## 8. ExtractDocumentUseCase — Orchestration complète

```java
@UseCase
public class ExtractDocumentUseCaseImpl implements ExtractDocumentUseCase {

    @Override
    @Audited(action = "DOCUMENT_EXTRACTED", resourceType = "Document")
    public ExtractionResult extract(ExtractDocumentCommand command) {
        String tenantId = TenantContext.get();

        // 1. Vérifier le cache (SHA-256 du contenu)
        String contentHash = command.contentHash();
        Optional<ExtractionResult> cached = cachePort.get(contentHash, tenantId);
        if (cached.isPresent()) {
            return cached.get(); // Cache hit < 200ms
        }

        // 2. Télécharger le fichier depuis S3
        InputStream stream = storagePort.download(command.s3Key());

        // 3. OCR (PDFBox texte natif ou Tess4J si scanné)
        OcrResult ocr = ocrPort.extract(stream, command.mimeType(),
                                        command.documentId(), tenantId);

        // 4. Extraction LLM (avec Circuit Breaker)
        String ocrText = storagePort.downloadText(ocr.rawOcrTextS3Key()); // Lire depuis S3
        ExtractionResponse llmResult = llmPort.extract(ocrText, command.documentType());

        // 5. Calculer les scores de confiance
        Map<String, ExtractedField> fields = calculateConfidenceScores(llmResult, command.documentType());
        double globalScore = calculateGlobalScore(fields, command.documentType());

        // 6. Construire l'aggregate
        ExtractionResult result = new ExtractionResult(
            command.documentId(), tenantId, fields, globalScore,
            llmResult.isPartial() ? ExtractionStatus.NEEDS_REVIEW : ExtractionStatus.COMPLETED,
            ocr.rawOcrTextS3Key()  // ADR-004 : clé S3, pas le texte
        );

        // 7. Persister + cache + event
        repositoryPort.save(result);
        cachePort.put(contentHash, tenantId, result);
        eventPublisher.publish("docai.doc.extracted", command.documentId(),
                               new ExtractionCompleted(result), tenantId);

        return result;
    }
}
```

---

## 9. ExtractionKafkaConsumer

```java
@Component
public class ExtractionKafkaConsumer extends ResilientKafkaConsumer<DocumentClassified> {

    @KafkaListener(
        topics = "docai.doc.classified",
        groupId = "docai.extraction.extraction.group"
    )
    public void consume(ConsumerRecord<String, DocumentClassified> record,
                        Acknowledgment ack) {
        processWithIdempotence(record, () -> {
            DocumentClassified event = record.value();
            TenantContext.set(event.getTenantId());
            try {
                extractDocumentUseCase.extract(
                    new ExtractDocumentCommand(event.getDocumentId(), event.getTenantId(),
                                              event.getS3Key(), event.getMimeType(),
                                              event.getContentHash(), event.getDocumentType())
                );
            } finally {
                TenantContext.clear();
            }
        });
        ack.acknowledge();
    }
}
```

---

## 10. Definition of Done

- [ ] Consumer Kafka `docai.doc.classified` idempotent (même offset 2× → ignoré)
- [ ] PDF texte natif → PDFBox (pas Tess4J) — vérifié en test
- [ ] PDF scanné → Tess4J OCR (langue FR configurée)
- [ ] rawOcrText dans S3 (jamais en MongoDB — ADR-004 vérifié)
- [ ] Cache Valkey : hit → 0 appel LLM (vérifié WireMock 0 calls)
- [ ] Jitter TTL : 100 mises en cache → 100 TTL différents (ADR-003)
- [ ] Circuit Breaker LLM OPEN → fallback → NEEDS_REVIEW
- [ ] Prompts testés pour chaque type de document (FACTURE, CNI, RIB, ORDONNANCE, BULLETIN_SALAIRE)
- [ ] Score global = moyenne pondérée champs obligatoires uniquement
- [ ] Metrics : `docai_extraction_duration_seconds`, `docai_cache_hit_total`
- [ ] Latence cache hit < 200ms, latence complète < 15s (p95)
- [ ] Feature Flag `extraction.mistral.enabled` → swap OpenAI ↔ Mistral testé
- [ ] Tests WireMock : simulation OpenAI timeout → retry → fallback
