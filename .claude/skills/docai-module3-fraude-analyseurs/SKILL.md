---
name: docai-module3-fraude-analyseurs
description: "Implémente le Module 3.2 DocAI (Analyseurs Avancés fraude : ApacheTikaMetadataAdapter signaux META_EDITOR_SUSPICIOUS/META_DATE_INCONSISTENCY/META_HIDDEN_LAYERS/META_UPSCALE_ARTIFACTS, VisualAnalyzerAdapter JavaCV/OpenCV signaux VISUAL_TEXT_OVERLAY/VISUAL_FONT_INCONSISTENCY/VISUAL_LOGO_DEGRADED/VISUAL_ALIGNMENT_BROKEN, timeout 15s obligatoire BR-VIS-003, fail-safe try-catch sur chaque analyseur, FraudAnalyzerRegistry pattern). Utiliser quand on demande d'implémenter l'analyse de métadonnées PDF, la détection visuelle de falsification, Apache Tika fraude, OpenCV fraude, ou les signaux META_*/VISUAL_*. Prérequis : Module 3.1 Scoring terminé."
---

# DocAI — Module 3.2 Analyseurs Avancés Fraude
## Apache Tika · JavaCV (OpenCV) · Signaux META · VISUAL · Fail-safe

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 5 (Module 3, Phase 3.2)
> **Prérequis :** Module 3.1 Scoring terminé. `FraudAnalyzerRegistry` et `CompositeFraudAnalyzer` en place.
> **Durée estimée :** 2 semaines

---

## 1. Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-VIS-001 | L'analyse Tika est exécutée sur **tous** les documents sans exception | MUST |
| BR-VIS-002 | L'analyse visuelle est exécutée uniquement sur FACTURE, CNI, RIB | MUST |
| BR-VIS-003 | Chaque analyseur visuel a un **timeout de 15 secondes** | MUST |
| BR-VIS-004 | L'échec d'un analyseur visuel n'arrête **jamais** le pipeline (fail-safe) | MUST |
| BR-VIS-005 | L'evidence de chaque signal contient la valeur trouvée et la valeur attendue | MUST |

---

## 2. Signaux Tika — Métadonnées fichier

| Signal | Poids | Détection |
|--------|-------|-----------|
| `META_EDITOR_SUSPICIOUS` | **25** | Logiciel d'édition image détecté (`xmp:CreatorTool` contient "Photoshop", "GIMP", "Inkscape", "Paint") |
| `META_DATE_INCONSISTENCY` | **20** | Date création fichier > 30j après date émission document |
| `META_HIDDEN_LAYERS` | **30** | Couches PDF cachées détectées (`pdf:hasXFA` ou couches masquées) |
| `META_UPSCALE_ARTIFACTS` | **15** | Résolution image < 72 DPI (image upscalée artificiellement) |
| `META_HIGH_REVISION_COUNT` | **10** | > 5 révisions sur une facture simple (`cp:revision`) |

---

## 3. Signaux visuels — JavaCV (OpenCV)

| Signal | Poids | Algorithme de détection |
|--------|-------|------------------------|
| `VISUAL_TEXT_OVERLAY` | **35** | Canny Edge Detection — zone de texte sur fond uniforme non-blanc |
| `VISUAL_FONT_INCONSISTENCY` | **15** | Analyse caractéristiques typographiques — polices multiples dans un champ |
| `VISUAL_LOGO_DEGRADED` | **10** | Résolution logo < 72 DPI vs reste du document > 200 DPI |
| `VISUAL_ALIGNMENT_BROKEN` | **10** | Espacement lignes irrégulier > 20% de la moyenne |

> ⚠️ Utiliser **JavaCV** (`org.bytedeco:opencv:4.9.0`) — pas `org.opencv` directement.

---

## 4. ApacheTikaMetadataAdapter

```java
// ApacheTikaMetadataAdapter — implements MetadataAnalyzerPort
@Component
public class ApacheTikaMetadataAdapter implements MetadataAnalyzerPort {

    private static final Set<String> SUSPICIOUS_TOOLS = Set.of(
        "photoshop", "gimp", "inkscape", "paint.net", "pixelmator"
    );

    @CircuitBreaker(name = "tika")
    @TimeLimiter(name = "tika")  // 15s max (BR-VIS-003)
    @Override
    public List<FraudSignal> analyze(InputStream stream, String mimeType,
                                     LocalDate documentEmissionDate) {
        List<FraudSignal> signals = new ArrayList<>();

        try {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            // Mode streaming — pas de chargement complet en mémoire
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(stream, new DefaultHandler(), metadata, context);

            // Signal : logiciel éditeur suspect
            String creatorTool = metadata.get("xmp:CreatorTool");
            if (creatorTool != null) {
                String tool = creatorTool.toLowerCase();
                if (SUSPICIOUS_TOOLS.stream().anyMatch(tool::contains)) {
                    signals.add(FraudSignal.of(
                        SignalType.META_EDITOR_SUSPICIOUS, 25,
                        Map.of("tool", creatorTool, "expected", "professional PDF generator")
                    ));
                }
            }

            // Signal : date création incohérente
            String creationDate = metadata.get("meta:creation-date");
            if (creationDate != null && documentEmissionDate != null) {
                LocalDate fileCreated = LocalDate.parse(creationDate.substring(0, 10));
                if (fileCreated.isBefore(documentEmissionDate.minusDays(30))) {
                    signals.add(FraudSignal.of(
                        SignalType.META_DATE_INCONSISTENCY, 20,
                        Map.of("fileCreated", fileCreated, "documentEmission", documentEmissionDate)
                    ));
                }
            }

            // Signal : couches PDF cachées
            String hasXfa = metadata.get("pdf:hasXFA");
            if ("true".equalsIgnoreCase(hasXfa)) {
                signals.add(FraudSignal.of(SignalType.META_HIDDEN_LAYERS, 30,
                    Map.of("hasXFA", true)));
            }

            // Signal : résolution suspecte (upscale)
            String xResolution = metadata.get("tiff:XResolution");
            if (xResolution != null && Double.parseDouble(xResolution) < 72) {
                signals.add(FraudSignal.of(SignalType.META_UPSCALE_ARTIFACTS, 15,
                    Map.of("dpi", xResolution, "expected", ">= 72 DPI")));
            }

        } catch (Exception e) {
            // Fail-safe obligatoire (BR-VIS-004) : exception → signal ignoré
            log.warn("Tika analysis failed — fail-safe documentId={}", documentId, e);
            meterRegistry.counter("docai_fraud_analyzer_failure", "analyzer", "tika").increment();
        }

        return signals;
    }
}
```

---

## 5. VisualAnalyzerAdapter (JavaCV)

```java
// VisualAnalyzerAdapter — implements VisualAnalyzerPort
@Component
public class VisualAnalyzerAdapter implements VisualAnalyzerPort {

    @CircuitBreaker(name = "opencv")
    @TimeLimiter(name = "opencv")  // 15s max (BR-VIS-003)
    @Override
    public List<FraudSignal> analyze(InputStream stream, DocumentType type) {
        // Vérifier BR-VIS-002 : uniquement FACTURE, CNI, RIB
        if (!Set.of(DocumentType.FACTURE, DocumentType.CNI, DocumentType.RIB).contains(type)) {
            return List.of();
        }

        List<FraudSignal> signals = new ArrayList<>();

        try {
            // Convertir en image OpenCV (300 DPI via PDFBox rasterization)
            BufferedImage image = rasterizeToPng(stream, 300);
            Mat mat = bufferedImageToMat(image);

            // Détection texte superposé (VISUAL_TEXT_OVERLAY)
            signals.addAll(detectTextOverlay(mat));

            // Détection polices incohérentes (VISUAL_FONT_INCONSISTENCY)
            signals.addAll(detectFontInconsistency(mat));

            // Détection logo dégradé (VISUAL_LOGO_DEGRADED)
            signals.addAll(detectLogoQuality(mat));

            // Détection alignement cassé (VISUAL_ALIGNMENT_BROKEN)
            signals.addAll(detectAlignmentIssues(mat));

        } catch (Exception e) {
            // Fail-safe obligatoire (BR-VIS-004)
            log.warn("Visual analysis failed — fail-safe documentType={}", type, e);
            meterRegistry.counter("docai_fraud_analyzer_failure", "analyzer", "opencv").increment();
        }

        return signals;
    }

    // Détection texte superposé — Canny Edge Detection
    private List<FraudSignal> detectTextOverlay(Mat mat) {
        Mat edges = new Mat();
        Imgproc.Canny(mat, edges, 100, 200);
        // Chercher zones texte sur fond de couleur uniforme non-blanc (clone stamp)
        // Zone > 50px × 20px avec fond uniforme sous texte → VISUAL_TEXT_OVERLAY
        // ... implémentation JavaCV
        return signals;
    }

    private List<FraudSignal> detectAlignmentIssues(Mat mat) {
        // Extraire lignes de texte avec coordonnées Y
        // Calculer espacement moyen entre lignes
        // Si écart > 20% de la moyenne → ligne ajoutée manuellement
        // ... implémentation JavaCV
        return signals;
    }
}
```

---

## 6. Enregistrement dans FraudAnalyzerRegistry

```java
// Les analyseurs s'auto-enregistrent via @Component + Spring DI
// FraudAnalyzerRegistry les collecte automatiquement

@Component
public class FraudAnalyzerRegistry {

    private final List<FraudAnalyzerStrategy> analyzers;

    public FraudAnalyzerRegistry(List<FraudAnalyzerStrategy> analyzers) {
        this.analyzers = analyzers;
        log.info("FraudAnalyzerRegistry initialized with {} analyzers", analyzers.size());
        // À l'ajout de MetadataFraudAnalyzer et VisualFraudAnalyzer :
        // → Registry les inclut automatiquement sans modification de code
    }
}

// CompositeFraudAnalyzer — agrège tous les analyseurs (fail-safe global)
@Component
public class CompositeFraudAnalyzer {

    public FraudAnalysisResult analyzeAll(Document document) {
        List<FraudSignal> allSignals = new ArrayList<>();

        for (FraudAnalyzerStrategy analyzer : registry.getAll()) {
            try {
                // Chaque analyseur enveloppé en try-catch (BR-VIS-004)
                List<FraudSignal> signals = analyzer.analyze(document);
                allSignals.addAll(signals);
            } catch (Exception e) {
                log.warn("Analyzer {} failed — ignored signal", analyzer.getClass().getSimpleName(), e);
                meterRegistry.counter("docai_fraud_analyzer_failure",
                    "analyzer", analyzer.getClass().getSimpleName()).increment();
                // isPartialAnalysis = true si au moins 1 analyseur a échoué
            }
        }

        int score = Math.min(100, allSignals.stream().mapToInt(FraudSignal::weight).sum());
        return FraudAnalysisResult.of(score, allSignals, isPartialAnalysis);
    }
}
```

---

## 7. Ports hexagonaux

```java
// Dans docai-domain/port/out/
public interface MetadataAnalyzerPort {
    List<FraudSignal> analyze(InputStream stream, String mimeType, LocalDate documentEmissionDate);
}

public interface VisualAnalyzerPort {
    List<FraudSignal> analyze(InputStream stream, DocumentType type);
}

// PORT-OUT-FRD-005 — MetadataAnalyzerPort (ApacheTikaMetadataAdapter)
// PORT-OUT-FRD-006 — VisualAnalyzerPort (VisualAnalyzerAdapter)
```

---

## 8. Definition of Done

- [ ] `ApacheTikaMetadataAdapter` testé avec PDF créé par Photoshop → signal `META_EDITOR_SUSPICIOUS` (poids 25)
- [ ] Tika testé avec PDF dont date création < date émission → signal `META_DATE_INCONSISTENCY`
- [ ] `VisualAnalyzerAdapter` testé avec facture dont montant modifié → signal `VISUAL_TEXT_OVERLAY`
- [ ] Fail-safe validé : chaque analyseur peut lever RuntimeException → pipeline continue
- [ ] Timeout 15s respecté (BR-VIS-003) — PDF 50 pages analysé ou abandonné dans les délais
- [ ] BR-VIS-002 respecté : analyse visuelle uniquement sur FACTURE, CNI, RIB
- [ ] Analyse Tika sur tous les types (BR-VIS-001)
- [ ] Registry auto-enregistrement : nouveau `@Component FraudAnalyzerStrategy` → inclus automatiquement
- [ ] `isPartialAnalysis = true` si au moins 1 analyseur a échoué
- [ ] Métriques : `docai_fraud_analyzer_failure{analyzer}` comptabilisées
- [ ] Performance : Tika < 1s, Visual < 8s
