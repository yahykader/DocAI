---
name: docai-module5-dashboard-avance
description: "Implémente le Module 5.4 DocAI Backlog v2 (fonctionnalités avancées dashboard : export CSV/Excel documents, recherche full-text MongoDB Atlas Search ou index text, filtres avancés combinés multi-critères, endpoint GET /v1/dashboard/export paginé avec format CSV/XLSX, GET /v1/documents/search full-text). Utiliser quand on demande d'implémenter l'export de documents, la recherche full-text, les filtres avancés dashboard, ou le téléchargement CSV/Excel des résultats. Prérequis : Module 5.1 Read Model CQRS terminé."
---

# DocAI — Module 5.4 Dashboard Avancé (Backlog v2)
## Export CSV/Excel · Recherche Full-Text · Filtres Avancés

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 6 (Module 5, Phase 5.4)
> **Prérequis :** Module 5.1 Read Model CQRS terminé. Feature Flag `dashboard.search.enabled`.
> **Note :** Fonctionnalités Backlog v2 — à implémenter après les modules 5.1, 5.2, 5.3.

---

## 1. Feature Flag

```
dashboard.search.enabled = false  (par défaut — activation progressive)
```

Tous les endpoints de ce module sont conditionnés par ce flag. Si désactivé → HTTP 503 avec message "Feature not available yet".

---

## 2. Export CSV/Excel

### Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-EXP-001 | Export limité à 10 000 documents maximum par requête | MUST |
| BR-EXP-002 | Export généré de façon asynchrone pour > 1 000 documents | MUST |
| BR-EXP-003 | Fichier exporté stocké dans S3, lien présigné 1h envoyé par email | MUST |
| BR-EXP-004 | Isolation tenant : un tenant ne peut exporter que ses propres documents | MUST |
| BR-EXP-005 | Export contient les champs : id, type, status, riskLevel, score, createdAt | MUST |

### Endpoint

```java
// POST /v1/dashboard/export — Lance l'export
@PostMapping("/v1/dashboard/export")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ANALYST')")
public ResponseEntity<ExportJobResponse> requestExport(
    @Valid @RequestBody ExportRequest request
) {
    // request.format = "CSV" ou "XLSX"
    // request.filters = mêmes filtres que le dashboard (status, type, dateFrom, dateTo)
    // request.maxItems <= 10_000
    String jobId = exportDocumentsUseCase.execute(new ExportDocumentsCommand(
        TenantContext.get(), request.format(), request.filters(), request.maxItems()
    ));

    return ResponseEntity.accepted()
        .body(new ExportJobResponse(jobId, "Export started — you'll receive an email when ready"));
}

// GET /v1/dashboard/export/{jobId}/status — Vérifier l'état de l'export
@GetMapping("/v1/dashboard/export/{jobId}/status")
public ResponseEntity<ExportJobStatus> getExportStatus(@PathVariable String jobId) { ... }
```

### Use Case — Export asynchrone

```java
@UseCase
public class ExportDocumentsUseCaseImpl implements ExportDocumentsUseCase {

    @Override
    @Async("exportTaskExecutor")
    public String execute(ExportDocumentsCommand command) {
        String jobId = UUID.randomUUID().toString();
        String tenantId = command.tenantId();

        try {
            // 1. Récupérer les documents depuis le Read Model
            List<DocumentSummaryView> documents = documentSummaryRepository
                .findForExport(tenantId, command.filters(),
                               PageRequest.of(0, command.maxItems()));

            // 2. Générer le fichier
            byte[] fileBytes = switch (command.format()) {
                case "CSV"  -> csvExporter.export(documents);
                case "XLSX" -> xlsxExporter.export(documents);
                default     -> throw new UnsupportedExportFormatException(command.format());
            };

            // 3. Uploader dans S3
            String s3Key = "%s/exports/%s/documents-export.%s"
                .formatted(tenantId, jobId, command.format().toLowerCase());
            storagePort.upload(new ByteArrayInputStream(fileBytes), s3Key);

            // 4. Générer URL présignée (1h)
            String downloadUrl = storagePort.generatePresignedUrl(s3Key, Duration.ofHours(1));

            // 5. Envoyer email avec lien
            emailPort.send(EmailMessage.exportReady(tenantId, downloadUrl, documents.size()));

        } catch (Exception e) {
            log.error("Export failed jobId={} tenantId={}", jobId, tenantId, e);
            // Notifier l'échec
        }

        return jobId;
    }
}
```

### CSV Exporter

```java
@Component
public class CsvDocumentExporter {

    private static final String[] HEADERS = {
        "ID", "Type", "Status", "Risk Level", "Fraud Score",
        "Created At", "Processed At", "File Name"
    };

    public byte[] export(List<DocumentSummaryView> documents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // BOM UTF-8 pour Excel
            writer.print('\uFEFF');
            writer.println(String.join(",", HEADERS));

            for (DocumentSummaryView doc : documents) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\"%n",
                    doc.documentId(), doc.type(), doc.status(), doc.riskLevel(),
                    doc.fraudScore(), doc.createdAt(), doc.processedAt(), doc.fileName()
                );
            }
        }
        return out.toByteArray();
    }
}
```

---

## 3. Recherche Full-Text

### Option 1 — MongoDB Atlas Search (production)

```java
// SearchDocumentsUseCase — via MongoDB Atlas Search
@UseCase
public class SearchDocumentsUseCaseImpl implements SearchDocumentsUseCase {

    @Override
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ANALYST', 'VIEWER')")
    public Page<DocumentSummaryView> search(SearchDocumentsCommand command) {
        String tenantId = TenantContext.get();

        // Atlas Search avec isolation tenant obligatoire
        Aggregation pipeline = Aggregation.newAggregation(
            Aggregation.stage("""
                {
                  "$search": {
                    "index": "docai_documents_search",
                    "compound": {
                      "must": [{
                        "text": {
                          "query": "%s",
                          "path": ["fileName", "extractedFields.emetteur.raisonSociale",
                                   "extractedFields.numéroFacture"]
                        }
                      }],
                      "filter": [{
                        "equals": { "path": "tenantId", "value": "%s" }
                      }]
                    }
                  }
                }
            """.formatted(command.query(), tenantId)),
            Aggregation.skip((long) command.page() * command.size()),
            Aggregation.limit(command.size())
        );

        return mongoTemplate.aggregate(pipeline, "document_summary_views",
                                       DocumentSummaryView.class);
    }
}
```

### Option 2 — MongoDB Text Index (self-hosted)

```java
// Migration Mongock pour index texte
@ChangeUnit(id = "V020_documents_add_text_search_index", order = "020")
public class V020DocumentsAddTextSearchIndex {

    @Execution
    public void execute(MongoDatabase db) {
        // Index texte sur les champs de recherche
        db.getCollection("document_summary_views").createIndex(
            Indexes.compoundIndex(
                Indexes.text("fileName"),
                Indexes.text("extractedData")
            ),
            new IndexOptions().name("idx_text_search")
        );
    }
}
```

### Endpoint

```java
// GET /v1/documents/search?q={query}&page=0&size=20
@GetMapping("/v1/documents/search")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ANALYST', 'VIEWER')")
public ResponseEntity<ApiResponse<List<DocumentSummaryView>>> search(
    @RequestParam String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    if (!featureFlagPort.isEnabled("dashboard.search.enabled")) {
        return ResponseEntity.status(503).body(ApiResponse.error("Feature not available yet"));
    }

    if (q == null || q.length() < 2) {
        throw new InvalidSearchQueryException("Query must be at least 2 characters");
    }

    Page<DocumentSummaryView> results = searchDocumentsUseCase.search(
        new SearchDocumentsCommand(q, page, size)
    );

    return ResponseEntity.ok(ApiResponse.paginated(results.getContent(),
                                                   buildPageMetadata(results)));
}
```

---

## 4. Filtres Avancés

```java
// DocumentFilterRequest — combinaison de filtres
public record DocumentFilterRequest(
    List<DocumentStatus> statuses,       // Multi-valeurs
    List<DocumentType> types,            // Multi-valeurs
    List<RiskLevel> riskLevels,          // Multi-valeurs
    LocalDate dateFrom,
    LocalDate dateTo,
    Double minScore,
    Double maxScore,
    Boolean hasCorrections,              // A eu des corrections manuelles
    Boolean isPartialAnalysis            // Analyse fraude partielle
) {}

// GET /v1/documents?statuses=COMPLETED,NEEDS_REVIEW&riskLevels=ELEVE,CRITIQUE&dateFrom=2026-01-01
@GetMapping("/v1/documents")
public ResponseEntity<ApiResponse<List<DocumentSummaryView>>> listWithFilters(
    @ModelAttribute DocumentFilterRequest filters,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "createdAt,desc") String sort
) { ... }
```

---

## 5. Definition of Done

- [ ] Feature Flag `dashboard.search.enabled` contrôle l'accès aux features
- [ ] Export CSV : BOM UTF-8, headers corrects, isolation tenant
- [ ] Export XLSX : format Excel compatible
- [ ] Export asynchrone pour > 1 000 documents
- [ ] Fichier exporté dans S3, lien présigné 1h, email envoyé à la fin
- [ ] Limite 10 000 documents par export respectée
- [ ] Recherche full-text : isolation tenant vérifiée (tenant A ne voit pas résultats tenant B)
- [ ] Requête < 2 caractères → HTTP 400
- [ ] Filtres avancés : combinaison multi-critères fonctionnelle
- [ ] Pagination respectée sur tous les endpoints (BR-PAG-001 à 008)
