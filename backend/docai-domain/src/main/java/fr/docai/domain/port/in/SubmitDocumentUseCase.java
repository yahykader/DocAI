package fr.docai.domain.port.in;

public interface SubmitDocumentUseCase {

  Response execute(SubmitDocumentCommand command);

  record SubmitDocumentCommand(
      String tenantId,
      String fileName,
      byte[] fileContent,
      String mimeType) {}

  record Response(
      String documentId,
      long createdAt) {}
}
