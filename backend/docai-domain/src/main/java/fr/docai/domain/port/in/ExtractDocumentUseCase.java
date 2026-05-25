package fr.docai.domain.port.in;

import java.util.Map;

public interface ExtractDocumentUseCase {

  Response execute(ExtractDocumentCommand command);

  record ExtractDocumentCommand(
      String tenantId,
      String documentId,
      String documentType) {}

  record Response(
      String documentId,
      Map<String, Object> extractedData,
      double confidence,
      long extractedAt) {}
}
