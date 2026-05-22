---
name: docai-module2-correction
description: "Implémente le Module 2 Phase 2.3 DocAI (correction manuelle des champs extraits, AuditEntry immuable, revalidation automatique, invalidation cache Valkey). Utiliser quand on demande d'implémenter la correction d'un champ extrait, l'audit d'une modification, ou la revalidation après correction."
---

# DocAI — Module 2 Correction Manuelle & Audit (Phase 2.3)

## Business Rules

| ID | Règle |
|----|-------|
| BR-EXT-020 | Un champ peut être corrigé manuellement par un ANALYST |
| BR-EXT-021 | Chaque correction génère un AuditEntry immuable (userId, fieldName, avant, après, timestamp) |
| BR-EXT-022 | Après correction, la validation complète est relancée automatiquement |
| BR-EXT-023 | La correction invalide le cache Valkey associé au document |
| BR-EXT-024 | Historique complet des corrections accessible via `GET /v1/documents/{id}/audit` |

## Domain Model

```java
// ExtractionCorrection — Value Object (immuable)
public record ExtractionCorrection(
    UUID correctionId,
    String documentId,
    String tenantId,
    String fieldName,          // "montantTTC", "siret", "iban"
    String previousValue,      // Valeur avant correction
    String correctedValue,     // Valeur après correction
    String correctedBy,        // userId — jamais loggué en clair
    String justification,      // Raison de la correction
    Instant correctedAt        // Timestamp immuable
) {}

// Domain Event
public record ExtractionCorrected(
    String documentId,
    String tenantId,
    String fieldName,
    String correctedBy,
    Instant occurredAt
) {}
```

## CorrectExtractionUseCase

```java
@Component
public class CorrectExtractionUseCaseImpl implements CorrectExtractionUseCase {

    private final ExtractionResultRepository extractionRepository;
    private final ExtractionCachePort extractionCache;
    private final ValidateExtractionUseCase validateUseCase;
    private final AuditPort auditPort;
    private final EventPublisherPort eventPublisher;

    @Override
    @Audited(action = "EXTRACTION_CORRECTED", resourceType = "ExtractionResult")
    public CorrectionResult execute(CorrectExtractionCommand command) {
        String tenantId = TenantContext.get();

        // Charger l'extraction existante
        ExtractionResult extraction = extractionRepository
            .findByDocumentIdAndTenantId(command.documentId(), tenantId)
            .orElseThrow(() -> new ExtractionNotFoundException(command.documentId()));

        // Capturer la valeur avant modification
        String previousValue = extraction.getFieldValue(command.fieldName());

        // Appliquer la correction
        extraction.correctField(command.fieldName(), command.correctedValue());
        extractionRepository.save(extraction);

        // AuditEntry immuable — BR-EXT-021
        auditPort.record(AuditEvent.builder()
            .action("EXTRACTION_CORRECTED")
            .resourceType("ExtractionResult")
            .resourceId(command.documentId())
            .tenantId(tenantId)
            .userId(command.correctedBy())
            .metadata(Map.of(
                "fieldName", command.fieldName(),
                "previousValue", maskIfPii(command.fieldName(), previousValue),
                "correctedValue", maskIfPii(command.fieldName(), command.correctedValue()),
                "justification", command.justification()
            ))
            .build());

        // Invalider le cache Valkey — BR-EXT-023
        String contentHash = extraction.getContentHash();
        extractionCache.invalidate(contentHash);
        log.info("Extraction cache invalidated documentId={} tenantId={}", command.documentId(), tenantId);

        // Relancer la validation automatiquement — BR-EXT-022
        ValidationReport newReport = validateUseCase.execute(
            new ValidateExtractionCommand(command.documentId(), tenantId, extraction)
        );

        // Publier l'événement de correction
        eventPublisher.publish(ExtractionCorrected.of(
            command.documentId(), tenantId, command.fieldName(),
            command.correctedBy(), Instant.now()
        ));

        log.info("Extraction field corrected documentId={} tenantId={} field={} correctedBy=[PII_MASKED]",
            command.documentId(), tenantId, command.fieldName());

        return new CorrectionResult(command.documentId(), newReport);
    }

    // Masquer les données PII dans l'audit
    private String maskIfPii(String fieldName, String value) {
        Set<String> piiFields = Set.of("nom", "prenom", "dateNaissance", "iban", "rpps", "numeroDocument");
        return piiFields.contains(fieldName) ? "[PII_MASKED]" : value;
    }
}
```

## Controller

```java
@RestController
@RequestMapping("/v1/documents/{documentId}/extraction")
public class ExtractionCorrectionController {

    @PatchMapping("/fields/{fieldName}")
    @PreAuthorize("hasRole('ANALYST') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Corriger un champ extrait")
    public ResponseEntity<CorrectionResponse> correctField(
        @PathVariable String documentId,
        @PathVariable String fieldName,
        @Valid @RequestBody FieldCorrectionRequest request
    ) {
        String tenantId = TenantContext.get();
        CorrectExtractionCommand command = new CorrectExtractionCommand(
            documentId, tenantId, fieldName,
            request.correctedValue(), request.justification(),
            getCurrentUserId()
        );
        CorrectionResult result = correctExtractionUseCase.execute(command);
        return ResponseEntity.ok(CorrectionResponse.from(result));
    }

    @GetMapping("/corrections")
    @PreAuthorize("hasRole('ANALYST') or hasRole('VIEWER') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Historique des corrections d'un document")
    public ResponseEntity<ApiResponse<List<CorrectionSummary>>> getCorrectionHistory(
        @PathVariable String documentId
    ) {
        List<AuditEvent> corrections = auditPort.findByDocument(documentId);
        return ResponseEntity.ok(ApiResponse.of(
            corrections.stream()
                .filter(e -> "EXTRACTION_CORRECTED".equals(e.action()))
                .map(CorrectionSummary::from)
                .collect(Collectors.toList())
        ));
    }
}
```

## Endpoints

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| PATCH | `/v1/documents/{id}/extraction/fields/{fieldName}` | ANALYST, TENANT_ADMIN | Corriger un champ |
| GET | `/v1/documents/{id}/extraction/corrections` | ANALYST, VIEWER, TENANT_ADMIN | Historique corrections |
| GET | `/v1/documents/{id}/audit` | ANALYST, TENANT_ADMIN | Audit complet du document |

## Scénarios BDD

```gherkin
Feature: Correction manuelle des données extraites

  Scenario: Correction d'un montant TTC erroné
    Given un document FACTURE avec montantTTC extrait à 1200 (incorrect)
    And alice a le rôle ANALYST
    When alice corrige montantTTC à 1250 avec justification "Montant OCR incorrect"
    Then le champ montantTTC est mis à jour à 1250
    And un AuditEntry immuable est créé avec avant=1200, après=1250
    And le cache Valkey du document est invalidé
    And la validation est relancée automatiquement

  Scenario: Correction d'un champ PII — valeur masquée dans l'audit
    Given un document CNI avec nom extrait incorrectement
    When alice corrige le champ "nom"
    Then dans l'AuditEntry, previousValue=[PII_MASKED] et correctedValue=[PII_MASKED]
    And jamais de données PII dans les logs

  Scenario: Historique corrections accessible
    Given un document avec 3 corrections effectuées
    When GET /v1/documents/{id}/extraction/corrections
    Then 3 corrections retournées avec userId=[PII_MASKED], fieldName, timestamp
```

## Checklist

- [ ] AuditEntry créé AVANT de modifier la valeur (capture previousValue)
- [ ] Données PII masquées dans l'AuditEntry (`[PII_MASKED]`)
- [ ] Cache Valkey invalidé après chaque correction (BR-EXT-023)
- [ ] Revalidation automatique déclenchée après correction (BR-EXT-022)
- [ ] `ExtractionCorrected` event publié via Outbox Pattern
- [ ] `@Audited` sur `CorrectExtractionUseCase`
- [ ] Logs : fieldName loggué, valeurs PII JAMAIS loggués
- [ ] Endpoint `GET /v1/documents/{id}/audit` accessible ANALYST et TENANT_ADMIN
- [ ] Test : correction d'un champ PII → AuditEntry avec [PII_MASKED]
- [ ] Test : après correction → validation relancée → nouveau rapport accessible
