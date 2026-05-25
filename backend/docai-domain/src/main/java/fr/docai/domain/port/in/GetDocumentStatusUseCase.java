package fr.docai.domain.port.in;

public interface GetDocumentStatusUseCase {

  Response execute(GetDocumentStatusCommand command);

  record GetDocumentStatusCommand(
      String tenantId,
      String documentId) {}

  record Response(
      String documentId,
      String status,
      String documentType,
      long uploadedAt,
      long updatedAt) {}
}
