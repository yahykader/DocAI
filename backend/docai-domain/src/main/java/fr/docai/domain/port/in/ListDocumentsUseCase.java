package fr.docai.domain.port.in;

import java.util.List;

public interface ListDocumentsUseCase {

  Response execute(ListDocumentsCommand command);

  record ListDocumentsCommand(
      String tenantId,
      int page,
      int pageSize,
      String statusFilter) {}

  record DocumentSummary(
      String documentId,
      String fileName,
      String status,
      long uploadedAt) {}

  record Response(
      List<DocumentSummary> documents,
      int totalCount,
      int page,
      int pageSize) {}
}
