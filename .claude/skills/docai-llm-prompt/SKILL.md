---
name: docai-llm-prompt
description: Implémente un adapter LLM ou OCR dans DocAI (VisionModelAdapter, LlmExtractionAdapter, OcrAdapter). Utiliser quand on demande d'appeler un LLM, d'intégrer OpenAI ou Mistral, d'extraire des données d'un document, ou de classifier une image. Inclut les prompts système et les paramètres API définis dans le SpecKit.
---

# DocAI — Implémenter un Adapter LLM / OCR

## Modèles retenus

| Usage | Modèle principal | Fallback | Timeout |
|-------|-----------------|----------|---------|
| Classification vision | `gpt-4o` (OpenAI) | `mistral-pixtral-large` | 30s |
| Extraction données | `gpt-4o` (OpenAI) | `mistral-large-latest` | 30s |
| OCR PDF texte natif | **PDFBox 3.x** (`PdfBoxOcrAdapter`) | — | 60s |
| OCR image/PDF scanné | **Tess4J 5.x** (`Tess4JOcrAdapter`) | — | 60s |

> ⚠️ Jamais "AWS Textract" — DocAI utilise **PDFBox 3.x + Tess4J 5.x** (on-premise, pas de coût variable).

## Adapter Classification — VisionModelAdapter

### Prompt Système (immuable — ne pas modifier)

```
Tu es un expert en classification de documents administratifs français.
Tu reçois une image ou une page de document et tu dois identifier son type.
Réponds UNIQUEMENT en JSON valide, sans texte avant ou après.
Ne fais jamais de suppositions — si tu n'es pas sûr, utilise AUTRE.
```

### Prompt Utilisateur

```
Analyse ce document et retourne UNIQUEMENT ce JSON :
{
  "type": "FACTURE|CNI|PASSEPORT|RIB|ORDONNANCE|BULLETIN_SALAIRE|CONTRAT|JUSTIFICATIF_DOMICILE|AUTRE",
  "confidence": 0.0 à 1.0,
  "reasoning": "Explication courte (max 50 mots)",
  "indicators": ["indice1", "indice2"]
}
```

### Paramètres API OpenAI

```java
// model : gpt-4o
// max_tokens : 200 (réponse courte — JSON uniquement)
// temperature : 0.0 (déterministe — pas de créativité sur classification)
```

### Implémentation

```java
@Component
public class VisionModelAdapter implements ClassificationModelPort {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    @Override
    @CircuitBreaker(name = "llm", fallbackMethod = "classifyFallback")
    @Retry(name = "llm")
    @Bulkhead(name = "llm")
    @TimeLimiter(name = "llm")
    public ClassificationResult classify(String documentId, byte[] imageBytes) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        OpenAiRequest request = OpenAiRequest.builder()
            .model("gpt-4o")
            .maxTokens(200)
            .temperature(0.1)
            .messages(List.of(
                Message.system(SYSTEM_PROMPT),
                Message.user(List.of(
                    Content.text(USER_PROMPT),
                    Content.image("data:image/jpeg;base64," + base64Image)
                ))
            ))
            .build();

        OpenAiResponse response = openAiClient.complete(request);
        return parseClassificationResponse(documentId, response);
    }

    private ClassificationResult parseClassificationResponse(String documentId, OpenAiResponse response) {
        try {
            String json = response.choices().get(0).message().content();
            ClassificationJson parsed = objectMapper.readValue(json, ClassificationJson.class);
            return new ClassificationResult(
                documentId,
                DocumentType.valueOf(parsed.type()),
                new ConfidenceScore(parsed.confidence()),
                "gpt-4o",
                Instant.now()
            );
        } catch (Exception e) {
            log.error("Failed to parse LLM response documentId={}", documentId, e);
            return ClassificationResult.needsReview(documentId, "PARSE_ERROR");
        }
    }

    // Fallback — jamais bloquer le pipeline
    private ClassificationResult classifyFallback(String documentId, byte[] image, Throwable ex) {
        log.warn("LLM unavailable, fallback applied documentId={} reason={}",
            documentId, ex.getClass().getSimpleName());
        return ClassificationResult.needsReview(documentId, ex.getClass().getSimpleName());
    }
}
```

## Adapter Extraction — LlmExtractionAdapter

### Prompt Système Extraction

```
Tu es un expert en extraction de données de documents administratifs français.
Tu extrais les données structurées d'un document selon son type.
Réponds UNIQUEMENT en JSON valide, sans texte avant ou après.
Si une donnée est absente, utilise null — n'invente jamais.
```

### Prompt Utilisateur Extraction par type

```java
// FACTURE
String FACTURE_PROMPT = """
    Extrais les données de cette facture en JSON :
    {
      "numero": "string|null",
      "date": "YYYY-MM-DD|null",
      "montantHT": number|null,
      "montantTVA": number|null,
      "montantTTC": number|null,
      "siretFournisseur": "string|null",
      "siretClient": "string|null",
      "lignes": [{"description": "string", "quantite": number, "prixUnitaire": number}]
    }
    """;

// CNI
String CNI_PROMPT = """
    Extrais les données de cette CNI en JSON :
    {
      "nom": "[PII_MASKED]",
      "prenom": "[PII_MASKED]",
      "dateNaissance": "[PII_MASKED]",
      "dateExpiration": "YYYY-MM-DD|null",
      "numeroDocument": "string|null"
    }
    """;
// IMPORTANT : les données PII sont masquées dans les logs mais stockées chiffrées en base
```

### Paramètres API Extraction

```java
// model : gpt-4o
// max_tokens : 1000 (réponse plus longue — extraction complète)
// temperature : 0.0 (déterministe — extraction factuelle)
```

## Gestion des erreurs LLM

```java
// Types d'erreurs à gérer
try {
    return openAiClient.complete(request);
} catch (HttpClientErrorException e) {
    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        throw new RetryableException("LLM rate limit", e); // → Retry
    }
    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        throw new NonRetryableException("LLM auth error", e); // → DLQ
    }
    throw e;
} catch (ResourceAccessException e) {
    throw new RetryableException("LLM timeout", e); // → Retry puis fallback
}
```

## Checklist

- [ ] Prompts système et utilisateur conformes aux templates ci-dessus
- [ ] `temperature: 0.0` (classification ET extraction — jamais 0.1)
- [ ] `max_tokens: 200` (classification) ou `1000` (extraction)
- [ ] Parsing JSON avec try/catch → fallback si malformé
- [ ] Données PII marquées `[PII_MASKED]` dans les logs
- [ ] Circuit Breaker + Retry + Bulkhead configurés (voir `docai-resilience`)
- [ ] Test WireMock : succès, timeout, rate limit, réponse JSON malformée
- [ ] Métriques : `docai_extraction_confidence_score`, `docai_llm_call_duration_seconds`
