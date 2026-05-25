package fr.docai.domain.port.in;

public interface ReviewFraudDecisionUseCase {

  Response execute(ReviewFraudDecisionCommand command);

  record ReviewFraudDecisionCommand(
      String tenantId,
      String documentId,
      String decision,
      String reviewNotes) {}

  record Response(
      String documentId,
      String previousDecision,
      String newDecision,
      long reviewedAt) {}
}
