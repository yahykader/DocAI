package fr.docai.domain.port.out;

import java.util.Optional;
import java.util.List;

public interface DocumentRepositoryPort {

  void save(DocumentData document);

  Optional<DocumentData> findById(String documentId);

  List<DocumentData> findByTenant(String tenantId, int offset, int limit);

  void delete(String documentId);

  record DocumentData(
      String id,
      String tenantId,
      String fileName,
      String status,
      String documentType,
      long uploadedAt,
      long updatedAt) {}
}
