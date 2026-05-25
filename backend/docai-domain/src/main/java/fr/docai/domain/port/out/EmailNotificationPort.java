package fr.docai.domain.port.out;

public interface EmailNotificationPort {

  void sendProcessingCompleted(String tenantId, String documentId, String recipientEmail);

  void sendFraudAlert(String tenantId, String documentId, String recipientEmail, String fraudReason);

  void sendExtractionResult(String tenantId, String documentId, String recipientEmail, Object extractedData);

  record EmailRequest(
      String tenantId,
      String to,
      String subject,
      String body) {}
}
