---
name: docai-fraud-analysis
description: "Implémente le Module 3 Fraude DocAI complet — Phase 3.1 (FraudAnalysis aggregate, signaux données, scoring 0-100, FraudKafkaConsumer), Phase 3.2 (ApacheTikaMetadataAdapter signaux META_*, VisualAnalyzerAdapter JavaCV/OpenCV signaux VISUAL_*, FraudAnalyzerRegistry, CompositeFraudAnalyzer fail-safe, timeout 15s BR-VIS-003), Phase 3.3 (workflow révision humaine, ReviewDecision immuable, state machine PENDING_REVIEW→APPROVED/REJECTED/ESCALATED, SSE alertes score ) 50 en ( 2s, isolation tenant). Utiliser quand on demande d'analyser une fraude, de scorer un document, d'implémenter les signaux fraude, les analyseurs Tika/OpenCV, la queue de révision FRAUD_REVIEWER, ou les alertes SSE fraude."
---

# DocAI — Module 3 Analyse Fraude (Complet)
## Phase 3.1 Scoring · Phase 3.2 Tika+OpenCV · Phase 3.3 Révision+SSE

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 5 (Module 3)
> **Prérequis :** Module 2.2 Validation terminé. `commons-multitenancy` disponible.

---

## Phase 3.1 — Domain Model & Scoring

### Domain Model

```java
// FraudAnalysis — Aggregate IMMUABLE après création
@AggregateRoot
public class FraudAnalysis {
    private final FraudAnalysisId id;
    private final String documentId;
    private final String tenantId;
    private final int score;           // 0-100 (jamais -1 sauf analyse incomplète)
    private final RiskLevel riskLevel; // FAIBLE, MODÉRÉ, ÉLEVÉ, CRITIQUE
    private final List<FraudSignal> signals; // Indices détectés
    private final FraudDecision decision;    // APPROVED, REJECTED, FLAGGED, PENDING_REVIEW
    private final boolean isPartialAnalysis; // true si certains analyseurs ont échoué
    private final Instant analyzedAt;

    // FraudAnalysis est IMMUABLE — pas de setter, pas de modification après création
    // Score -1 = analyse incomplète (distinct de 0 = aucun risque)
}

// RiskLevel — Enum avec seuils
public enum RiskLevel {
    FAIBLE(0, 25),       // Score 0-25
    MODÉRÉ(26, 50),      // Score 26-50
    ÉLEVÉ(51, 75),       // Score 51-75
    CRITIQUE(76, 100);   // Score 76-100

    public static RiskLevel fromScore(int score) {
        return Arrays.stream(values())
            .filter(r -> score >= r.min && score <= r.max)
            .findFirst()
            .orElse(FAIBLE);
    }
}

// FraudSignal — Value Object (indice de fraude)
public record FraudSignal(
    String type,        // "DATA_SIRET_INVALID", "IMAGE_METADATA_SUSPICIOUS"
    String description,
    int weight,         // Poids dans le score global (1-30)
    Map<String, Object> evidence  // Détails de l'indice
) {}

// Signaux définis
// DATA_SIRET_INVALID       — SIRET non trouvé ou invalide (Luhn)         poids: 25
// DATA_IBAN_SUSPICIOUS     — IBAN format invalide ou banque inconnue      poids: 20
// IMAGE_METADATA_SUSPICIOUS — EXIF date/heure incohérente avec document  poids: 15
// AMOUNT_ARITHMETIC_ERROR  — Calcul HT+TVA ≠ TTC                         poids: 30
// DATE_FUTURE             — Date de document dans le futur               poids: 20
// DUPLICATE_DOCUMENT      — Hash contenu identique à doc existant         poids: 40
```

### Business Rules 3.1

| ID | Règle |
|----|-------|
| BR-FRD-001 | Score 0-25 → FAIBLE → APPROVED automatiquement |
| BR-FRD-002 | Score 26-50 → MODÉRÉ → FLAGGED (warning, pas bloqué) |
| BR-FRD-003 | Score 51-75 → ÉLEVÉ → NEEDS_REVIEW (queue révision) |
| BR-FRD-004 | Score 76-100 → CRITIQUE → REJECTED automatiquement |
| BR-FRD-005 | FraudAnalysis est IMMUABLE après création |
| BR-FRD-006 | Score -1 = analyse incomplète (PARTIAL_ANALYSIS) → NEEDS_REVIEW |
| BR-FRD-007 | La révision manuelle est effectuée par FRAUD_REVIEWER uniquement |

### Consumer Kafka

```java
@Component
public class FraudKafkaConsumer extends ResilientKafkaConsumer<DocumentExtractedEvent> {

    private final AnalyzeFraudUseCase analyzeFraudUseCase;

    @KafkaListener(
        topics = "docai.doc.extracted",
        groupId = "docai.fraud.analysis.group"
    )
    public void consume(
        @Payload DocumentExtractedEvent event,
        @Header("tenant-id") String tenantId,
        @Header("correlation-id") String correlationId,
        Acknowledgment ack
    ) {
        MDC.put("tenantId", tenantId);
        MDC.put("correlationId", correlationId);
        try {
            processWithIdempotence(buildRecord(event));
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void handle(DocumentExtractedEvent event, KafkaConsumerContext context) {
        AnalyzeFraudCommand command = new AnalyzeFraudCommand(
            event.documentId(), context.tenantId()
        );
        analyzeFraudUseCase.execute(command);
    }
}
```

### Use Case — Orchestration

```java
@Component
public class AnalyzeFraudUseCaseImpl implements AnalyzeFraudUseCase {

    private final FraudAnalysisRepository repository;
    private final List<FraudAnalyzer> analyzers;   // Strategy Pattern
    private final EventPublisherPort eventPublisher;

    @Override
    public FraudAnalysisResult execute(AnalyzeFraudCommand command) {
        List<FraudSignal> allSignals = new ArrayList<>();
        boolean isPartial = false;

        // Exécuter chaque analyseur — fail-safe si un analyseur échoue
        for (FraudAnalyzer analyzer : analyzers) {
            try {
                List<FraudSignal> signals = analyzer.analyze(command.documentId(), command.tenantId());
                allSignals.addAll(signals);
            } catch (Exception e) {
                log.warn("Analyzer {} failed documentId={} — partial analysis",
                    analyzer.getClass().getSimpleName(), command.documentId(), e);
                isPartial = true;
            }
        }

        // Score -1 si analyse totalement incomplète (aucun analyseur n'a répondu)
        int score = isPartial && allSignals.isEmpty() ? -1 : computeScore(allSignals);
        RiskLevel riskLevel = score == -1 ? null : RiskLevel.fromScore(score);
        FraudDecision decision = computeDecision(score, riskLevel);

        FraudAnalysis analysis = new FraudAnalysis(
            FraudAnalysisId.generate(), command.documentId(), command.tenantId(),
            score, riskLevel, allSignals, decision, isPartial, Instant.now()
        );

        repository.save(analysis);
        eventPublisher.publish(FraudAnalyzedEvent.of(analysis));

        log.info("Fraud analysis completed documentId={} tenantId={} score={} riskLevel={} signals={}",
            command.documentId(), command.tenantId(), score, riskLevel, allSignals.size());

        return FraudAnalysisResult.from(analysis);
    }

    private FraudDecision computeDecision(int score, RiskLevel riskLevel) {
        if (score == -1) return FraudDecision.PENDING_REVIEW; // Analyse incomplète
        return switch (riskLevel) {
            case FAIBLE   -> FraudDecision.APPROVED;
            case MODÉRÉ   -> FraudDecision.FLAGGED;
            case ÉLEVÉ    -> FraudDecision.PENDING_REVIEW;
            case CRITIQUE -> FraudDecision.REJECTED;
        };
    }
}
```

### Analyseurs — Strategy Pattern (Phase 3.1)

```java
public interface FraudAnalyzer {
    List<FraudSignal> analyze(String documentId, String tenantId);
    String getName();
}

// Exemples d'analyseurs
@Component public class SiretValidationAnalyzer implements FraudAnalyzer { ... }
@Component public class IbanValidationAnalyzer implements FraudAnalyzer { ... }
@Component public class ArithmeticConsistencyAnalyzer implements FraudAnalyzer { ... }
@Component public class ImageMetadataAnalyzer implements FraudAnalyzer { ... }
@Component public class DuplicateDocumentAnalyzer implements FraudAnalyzer { ... }
```

### Scénarios BDD 3.1

```gherkin
Feature: Analyse de fraude

  Scenario: Document légitime — score faible
    Given un document FACTURE extrait avec SIRET valide et montants cohérents
    When l'analyse fraude s'exécute
    Then le score est entre 0 et 25
    And la décision est APPROVED
    And l'event "FraudAnalyzed" est publié

  Scenario: SIRET invalide — score élevé
    Given un document FACTURE avec SIRET "00000000000000"
    When l'analyse fraude s'exécute
    Then le signal "DATA_SIRET_INVALID" est détecté avec poids 25
    And la décision est PENDING_REVIEW ou REJECTED

  Scenario: Analyse partielle — analyseur indisponible
    Given l'analyseur SIRET est indisponible (timeout)
    When l'analyse fraude s'exécute
    Then isPartialAnalysis est true
    And le document passe en NEEDS_REVIEW
    And le score n'est pas -1 si d'autres analyseurs ont répondu
```

---

---

## Phase 3.2 — Analyseurs Avancés (Tika + JavaCV)
> Lire **references/phase-3-2-analyseurs.md** pour l'implémentation complète.

**Résumé :**  (signaux META_EDITOR_SUSPICIOUS/META_DATE_INCONSISTENCY/META_HIDDEN_LAYERS/META_UPSCALE_ARTIFACTS) +  JavaCV (signaux VISUAL_TEXT_OVERLAY/VISUAL_FONT_INCONSISTENCY/VISUAL_LOGO_DEGRADED/VISUAL_ALIGNMENT_BROKEN) +  auto-enregistrement +  fail-safe global.

**Règles clés :**
- BR-VIS-001 : Tika sur **tous** les documents
- BR-VIS-002 : Visuel uniquement FACTURE, CNI, RIB
- BR-VIS-003 : **Timeout 15s OBLIGATOIRE** ( + )
- BR-VIS-004 : Fail-safe — exception ignorée, pipeline continue
- Utiliser  (JavaCV), jamais 

---

## Phase 3.3 — Révision Humaine & SSE
> Lire **references/phase-3-3-revision-sse.md** pour l'implémentation complète.

**Résumé :** State machine PENDING_REVIEW→REVIEWING→APPROVED/REJECTED/ESCALATED +  immuable (comment obligatoire pour REJECTED/ESCALATED) + endpoints  (paginé score DESC) + SSE alertes score > 50 en < 2s via  →  + isolation tenant.

**Règles clés :**
- BR-FRD-015 : alerte SSE en **< 2 secondes** pour score > 50
- BR-FRD-016 : isolation SSE (tenant A ne reçoit pas alertes tenant B)
- Limite : 50 connexions SSE max par tenant
- Seul  peut statuer (ANALYST → HTTP 403)
-  immuable : tentative modification → HTTP 409

---

## Checklist complète (3 phases)
> Lire **references/checklist.md** pour la checklist complète.

