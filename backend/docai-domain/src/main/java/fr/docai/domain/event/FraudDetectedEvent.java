package fr.docai.domain.event;

public class FraudDetectedEvent extends DomainEvent {

  private final String riskLevel;
  private final double fraudScore;
  private final String reason;

  public FraudDetectedEvent(String documentId, String tenantId, String riskLevel, double fraudScore, String reason) {
    super(documentId, tenantId);
    this.riskLevel = riskLevel;
    this.fraudScore = fraudScore;
    this.reason = reason;
  }

  @Override
  public String getEventType() {
    return "FRAUD_DETECTED";
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public double getFraudScore() {
    return fraudScore;
  }

  public String getReason() {
    return reason;
  }
}
