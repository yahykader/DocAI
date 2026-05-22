## Phase 3.2 — Analyseurs Avancés (Tika + JavaCV)

### Ports outbound (domaine pur — aucun import infrastructure)

```java
// Dans fr.docai.domain.port.out/
public interface MetadataAnalyzerPort {
    // PORT-OUT-FRD-005
    List<FraudSignal> analyze(InputStream stream, String mimeType, LocalDate documentEmissionDate);
}

public interface VisualAnalyzerPort {
    // PORT-OUT-FRD-006
    List<FraudSignal> analyze(InputStream stream, DocumentType type);
}
```

### Signaux Tika — Métadonnées fichier

| Signal | Poids | Condition |
|--------|-------|-----------|
| `META_EDITOR_SUSPICIOUS` | **25** | `xmp:CreatorTool` contient "photoshop", "gimp", "inkscape" ou "paint" |
| `META_DATE_INCONSISTENCY` | **20** | Date création fichier > 30j avant date émission document |
| `META_HIDDEN_LAYERS` | **30** | `pdf:hasXFA = true` (couches cachées PDF) |
| `META_UPSCALE_ARTIFACTS` | **15** | `tiff:XResolution` < 72 DPI (image upscalée artificiellement) |
| `META_HIGH_REVISION_COUNT` | **10** | `cp:revision` > 5 pour une facture simple |

### Signaux visuels — JavaCV (OpenCV)

| Signal | Poids | Algorithme |
|--------|-------|------------|
| `VISUAL_TEXT_OVERLAY` | **35** | Canny Edge Detection — texte sur fond uniforme non-blanc |
| `VISUAL_FONT_INCONSISTENCY` | **15** | Polices multiples dans un même champ |
| `VISUAL_LOGO_DEGRADED` | **10** | Résolution logo < 72 DPI vs reste du document > 200 DPI |
| `VISUAL_ALIGNMENT_BROKEN` | **10** | Espacement lignes irrégulier > 20% de la moyenne |

> ⚠️ Utiliser **JavaCV** (`org.bytedeco:opencv:4.9.0`) — jamais `org.opencv` directement.

### ApacheTikaMetadataAdapter

```java
@Component
public class ApacheTikaMetadataAdapter implements MetadataAnalyzerPort {

    private static final Set<String> SUSPICIOUS_TOOLS =
        Set.of("photoshop", "gimp", "inkscape", "paint.net", "pixelmator");

    @CircuitBreaker(name = "tika")
    @TimeLimiter(name = "tika")   // 15s max — BR-VIS-003 OBLIGATOIRE
    @Override
    public List<FraudSignal> analyze(InputStream stream, String mimeType,
                                     LocalDate documentEmissionDate) {
        List<FraudSignal> signals = new ArrayList<>();
        try {
            Metadata metadata = new Metadata();
            new AutoDetectParser().parse(stream, new DefaultHandler(),
                                         metadata, new ParseContext());

            // META_EDITOR_SUSPICIOUS
            String tool = Optional.ofNullable(metadata.get("xmp:CreatorTool"))
                                  .map(String::toLowerCase).orElse("");
            if (SUSPICIOUS_TOOLS.stream().anyMatch(tool::contains)) {
                signals.add(FraudSignal.of(SignalType.META_EDITOR_SUSPICIOUS, 25,
                    Map.of("tool", tool, "expected", "professional PDF generator")));
            }

            // META_DATE_INCONSISTENCY
            String creationDate = metadata.get("meta:creation-date");
            if (creationDate != null && documentEmissionDate != null) {
                LocalDate fileCreated = LocalDate.parse(creationDate.substring(0, 10));
                if (fileCreated.isBefore(documentEmissionDate.minusDays(30))) {
                    signals.add(FraudSignal.of(SignalType.META_DATE_INCONSISTENCY, 20,
                        Map.of("fileCreated", fileCreated, "docDate", documentEmissionDate)));
                }
            }

            // META_HIDDEN_LAYERS
            if ("true".equalsIgnoreCase(metadata.get("pdf:hasXFA"))) {
                signals.add(FraudSignal.of(SignalType.META_HIDDEN_LAYERS, 30,
                    Map.of("hasXFA", true)));
            }

            // META_UPSCALE_ARTIFACTS
            String xRes = metadata.get("tiff:XResolution");
            if (xRes != null && Double.parseDouble(xRes) < 72) {
                signals.add(FraudSignal.of(SignalType.META_UPSCALE_ARTIFACTS, 15,
                    Map.of("dpi", xRes, "expected", ">= 72 DPI")));
            }

        } catch (Exception e) {
            // Fail-safe OBLIGATOIRE (BR-VIS-004) — exception ignorée
            log.warn("Tika analysis failed — fail-safe ignored", e);
            meterRegistry.counter("docai_fraud_analyzer_failure", "analyzer", "tika").increment();
        }
        return signals;
    }
}
```

### VisualAnalyzerAdapter (JavaCV)

```java
@Component
public class VisualAnalyzerAdapter implements VisualAnalyzerPort {

    private static final Set<DocumentType> VISUAL_TYPES =
        Set.of(DocumentType.FACTURE, DocumentType.CNI, DocumentType.RIB); // BR-VIS-002

    @CircuitBreaker(name = "opencv")
    @TimeLimiter(name = "opencv")  // 15s max — BR-VIS-003 OBLIGATOIRE
    @Override
    public List<FraudSignal> analyze(InputStream stream, DocumentType type) {
        // BR-VIS-002 : uniquement FACTURE, CNI, RIB
        if (!VISUAL_TYPES.contains(type)) return List.of();

        List<FraudSignal> signals = new ArrayList<>();
        try {
            Mat mat = bufferedImageToMat(rasterizeToPng(stream, 300));

            // Texte superposé — Canny Edge Detection
            Mat edges = new Mat();
            Imgproc.Canny(mat, edges, 100, 200);
            if (detectTextOnUniformBackground(edges)) {
                signals.add(FraudSignal.of(SignalType.VISUAL_TEXT_OVERLAY, 35,
                    Map.of("method", "canny_edge_detection")));
            }

            // Alignement cassé
            double irregularity = computeLineSpacingIrregularity(mat);
            if (irregularity > 0.20) {  // > 20% de la moyenne
                signals.add(FraudSignal.of(SignalType.VISUAL_ALIGNMENT_BROKEN, 10,
                    Map.of("irregularity", irregularity)));
            }

        } catch (Exception e) {
            // Fail-safe OBLIGATOIRE (BR-VIS-004)
            log.warn("Visual analysis failed — fail-safe ignored documentType={}", type, e);
            meterRegistry.counter("docai_fraud_analyzer_failure", "analyzer", "opencv").increment();
        }
        return signals;
    }
}
```

### FraudAnalyzerRegistry + CompositeFraudAnalyzer

```java
// Registry — auto-enregistrement Spring (aucun code à modifier pour ajouter un analyseur)
@Component
public class FraudAnalyzerRegistry {
    private final List<FraudAnalyzerStrategy> analyzers;
    public FraudAnalyzerRegistry(List<FraudAnalyzerStrategy> analyzers) {
        this.analyzers = analyzers;
        log.info("FraudAnalyzerRegistry: {} analyseurs enregistrés", analyzers.size());
    }
    public List<FraudAnalyzerStrategy> getAll() { return analyzers; }
}

// CompositeFraudAnalyzer — agrège tous les analyseurs avec fail-safe global
@Component
public class CompositeFraudAnalyzer {
    public CompositeFraudResult analyzeAll(String documentId, String tenantId) {
        List<FraudSignal> allSignals = new ArrayList<>();
        boolean partial = false;
        for (FraudAnalyzerStrategy analyzer : registry.getAll()) {
            try {
                allSignals.addAll(analyzer.analyze(documentId, tenantId));
            } catch (Exception e) {
                partial = true;
                log.warn("Analyzer {} failed — signal ignored analyzer={}",
                    analyzer.getClass().getSimpleName(), e);
                meterRegistry.counter("docai_fraud_analyzer_failure",
                    "analyzer", analyzer.getClass().getSimpleName()).increment();
            }
        }
        int score = Math.min(100, allSignals.stream().mapToInt(FraudSignal::weight).sum());
        return new CompositeFraudResult(score, allSignals, partial);
    }
}
```

**Business Rules Phase 3.2 :**

| ID | Règle |
|----|-------|
| BR-VIS-001 | Tika exécutée sur **tous** les documents sans exception |
| BR-VIS-002 | Analyse visuelle uniquement sur FACTURE, CNI, RIB |
| BR-VIS-003 | **Timeout 15s OBLIGATOIRE** sur chaque analyseur visuel |
| BR-VIS-004 | Échec d'un analyseur → ignoré, pipeline continue (fail-safe) |

---

