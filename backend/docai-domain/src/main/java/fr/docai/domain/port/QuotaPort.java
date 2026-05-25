package fr.docai.domain.port.out;

public interface QuotaPort {

  boolean canProcessDocument(String tenantId);

  void incrementDocumentCount(String tenantId);

  int getRemainingQuota(String tenantId);

  record QuotaInfo(
      String tenantId,
      int processedCount,
      int monthlyLimit,
      int remaining) {}
}
