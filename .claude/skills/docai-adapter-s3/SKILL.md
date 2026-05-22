---
name: docai-adapter-s3
description: Crée un Adapter S3 dans DocAI (upload documents, presigned URLs, stockage OCR). Utiliser quand on demande de stocker un fichier, générer une URL présignée, ou implémenter ADR-004 (texte OCR brut → S3, jamais MongoDB). Applique les conventions DocAI de nommage des clés S3 et la résilience Resilience4j.
---

# DocAI — Créer un Adapter S3

## Localisation

Module : `docai-adapter-out-storage`
Package : `fr.docai.adapter.out.storage`

## ADR-004 — Texte OCR brut → S3, JAMAIS MongoDB (OBLIGATOIRE)

```
INTERDIT dans MongoDB extraction_results :
  rawOcrText: "texte de 4-5MB..."   ❌ — fait échouer la transaction (limite 4MB)

OBLIGATOIRE :
  rawOcrTextS3Key: "acme-corp/2026/doc-001/ocr.txt"  ✅
```

**Flux obligatoire pour le texte OCR :**
1. OCR produit le texte brut
2. Uploader dans S3 : `{tenantId}/{year}/{documentId}/ocr.txt`
3. Récupérer la clé S3 retournée
4. Stocker uniquement la clé dans MongoDB

## Convention de nommage des clés S3

```
Documents originaux  : {tenantId}/documents/{documentId}/{filename}
Texte OCR brut       : {tenantId}/ocr/{documentId}/raw-text.txt        ← ADR-004
Pages PDF (images)   : {tenantId}/pages/{documentId}/page-{n}.png
Exports              : {tenantId}/exports/{exportId}/{filename}
```

## Port — interface domaine

```java
// Dans fr.docai.domain.port.out
public interface DocumentStoragePort {
    String upload(String tenantId, String documentId,
                  String filename, byte[] content, String contentType);
    String uploadOcrText(String tenantId, String documentId, String ocrText);
    byte[] download(String s3Key);
    String generatePresignedUrl(String s3Key, Duration expiry);
    void delete(String s3Key);
}
```

## Adapter S3 — Implémentation

```java
@Component
public class S3DocumentStorageAdapter implements DocumentStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private static final Logger log = LoggerFactory.getLogger(S3DocumentStorageAdapter.class);

    public S3DocumentStorageAdapter(
        S3Client s3Client,
        S3Presigner s3Presigner,
        @Value("${docai.s3.bucket}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    @Override
    @CircuitBreaker(name = "s3", fallbackMethod = "uploadFallback")
    public String upload(String tenantId, String documentId,
                         String filename, byte[] content, String contentType) {
        // Convention de nommage obligatoire
        String s3Key = tenantId + "/documents/" + documentId + "/" + filename;

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType(contentType)
            .contentLength((long) content.length)
            // Métadonnées obligatoires pour la traçabilité
            .metadata(Map.of(
                "tenant-id", tenantId,
                "document-id", documentId,
                "uploaded-at", Instant.now().toString()
            ))
            .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
        log.info("Document uploaded to S3 key={} tenantId={} sizeBytes={}",
            s3Key, tenantId, content.length);
        return s3Key;
    }

    @Override
    public String uploadOcrText(String tenantId, String documentId, String ocrText) {
        // ADR-004 : texte OCR toujours dans S3, jamais MongoDB
        // Convention clé : {tenantId}/ocr/{documentId}/raw-text.txt
        String s3Key = tenantId + "/ocr/" + documentId + "/raw-text.txt";

        byte[] content = ocrText.getBytes(StandardCharsets.UTF_8);
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType("text/plain; charset=utf-8")
            .contentLength((long) content.length)
            .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
        log.info("OCR text uploaded to S3 key={} tenantId={} sizeBytes={}",
            s3Key, tenantId, content.length);
        return s3Key; // Retourner la clé à stocker dans MongoDB
    }

    // ADR-007 — Upload multipart avec AbortMultipartUpload obligatoire en cas d'erreur
    public String uploadMultipart(String tenantId, String documentId,
                                   String filename, InputStream stream, String contentType) {
        String s3Key = tenantId + "/documents/" + documentId + "/" + filename;
        String uploadId = s3Client.createMultipartUpload(b ->
            b.bucket(bucketName).key(s3Key).contentType(contentType)).uploadId();
        try {
            List<CompletedPart> parts = uploadParts(stream, s3Key, uploadId);
            s3Client.completeMultipartUpload(b ->
                b.bucket(bucketName).key(s3Key).uploadId(uploadId)
                 .multipartUpload(u -> u.parts(parts)));
            return s3Key;
        } catch (Exception e) {
            // ADR-007 : AbortMultipartUpload OBLIGATOIRE en cas d'erreur
            s3Client.abortMultipartUpload(b ->
                b.bucket(bucketName).key(s3Key).uploadId(uploadId));
            throw new StorageUnavailableException("Multipart upload failed and aborted", e);
        }
    }

    @Override
    public String generatePresignedUrl(String s3Key, Duration expiry) {
        // URLs présignées valables 1h par défaut (configurable)
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(expiry != null ? expiry : Duration.ofHours(1))
            .getObjectRequest(r -> r.bucket(bucketName).key(s3Key))
            .build();

        return s3Presigner.presignGetObject(presignRequest)
            .url()
            .toString();
    }

    @Override
    public byte[] download(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build();

        ResponseBytes<GetObjectResponse> response =
            s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    @Override
    public void delete(String s3Key) {
        s3Client.deleteObject(b -> b.bucket(bucketName).key(s3Key));
        log.info("S3 object deleted key={}", s3Key);
    }

    // Fallback — Circuit Breaker S3 ouvert (rare mais possible)
    private String uploadFallback(String tenantId, String documentId,
                                   String filename, byte[] content,
                                   String contentType, Throwable ex) {
        log.error("S3 upload failed, document queued for retry tenantId={} documentId={}",
            tenantId, documentId, ex);
        throw new StorageUnavailableException("S3 indisponible", ex);
    }
}
```

## Configuration AWS

```yaml
# application.yml
docai:
  s3:
    bucket: ${S3_BUCKET_NAME}
    presigned-url-expiry: 3600s   # URLs présignées valables 1h

# application-dev.yml — LocalStack pour les tests locaux
docai:
  s3:
    bucket: docai-documents-dev
    endpoint: http://localhost:4566  # LocalStack
```

## Test avec LocalStack (TestContainers)

```java
@Test
void should_upload_document_and_return_s3_key() {
    // Given — LocalStack configuré via AbstractIntegrationTest
    byte[] content = "test document content".getBytes();

    // When
    String s3Key = storageAdapter.upload("acme-corp", "doc-123",
        "invoice.pdf", content, "application/pdf");

    // Then
    assertThat(s3Key).isEqualTo("acme-corp/documents/doc-123/invoice.pdf");

    // Vérifier que le fichier est bien dans S3
    byte[] downloaded = storageAdapter.download(s3Key);
    assertThat(downloaded).isEqualTo(content);
}

@Test
void should_store_ocr_text_in_s3_not_mongodb() {
    // ADR-004 — texte OCR → S3
    String ocrText = "A".repeat(5_000_000); // 5MB — invalide en MongoDB

    String s3Key = storageAdapter.uploadOcrText("acme-corp", "doc-123", ocrText);

    assertThat(s3Key).contains("ocr.txt");
    assertThat(s3Key).startsWith("acme-corp/");
    // Vérifier que la clé est courte (pas le texte lui-même)
    assertThat(s3Key.length()).isLessThan(200);
}
```

## Checklist

- [ ] Clé OCR : `{tenantId}/ocr/{documentId}/raw-text.txt` (pas `{year}` dans le chemin)
- [ ] Clé document : `{tenantId}/documents/{documentId}/{filename}`
- [ ] Métadonnées `tenant-id` et `document-id` sur chaque objet
- [ ] Texte OCR brut toujours dans S3 via `uploadOcrText()` (ADR-004)
- [ ] Seule la clé S3 `rawOcrTextS3Key` stockée dans MongoDB
- [ ] **ADR-007 :** `AbortMultipartUpload` dans le bloc `catch` des uploads multipart
- [ ] URLs présignées avec expiry configurable (défaut 1h)
- [ ] Circuit Breaker `@CircuitBreaker(name = "s3")` sur les appels S3
- [ ] Test LocalStack avec `withReuse(true)` (ADR-008) : upload, download, presigned URL
- [ ] Test ADR-004 : texte 5MB → dans S3, transaction MongoDB réussit
