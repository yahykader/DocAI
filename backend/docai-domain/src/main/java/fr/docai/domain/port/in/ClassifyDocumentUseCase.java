package fr.docai.domain.port.in;

public interface ClassifyDocumentUseCase {

  Response execute(ClassifyDocumentCommand command);

  record ClassifyDocumentCommand(
      String tenantId,
      String documentId) {}

  record Response(
      String documentId,
      String documentType,
      double confidence,
      long classifiedAt) {}
}
