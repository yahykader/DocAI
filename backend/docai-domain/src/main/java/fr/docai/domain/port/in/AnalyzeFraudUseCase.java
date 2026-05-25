package fr.docai.domain.port.in;

public interface AnalyzeFraudUseCase {

  Response execute(AnalyzeFraudCommand command);

  record AnalyzeFraudCommand(
      String tenantId,
      String documentId) {}

  record Response(
      String documentId,
      String riskLevel,
      double fraudScore,
      String reason,
      long analyzedAt) {}
}
