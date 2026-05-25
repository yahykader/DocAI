package fr.docai.domain.event;

public abstract class DomainEvent {

  protected String aggregateId;
  protected String tenantId;
  protected long occurredAt;

  protected DomainEvent(String aggregateId, String tenantId) {
    this.aggregateId = aggregateId;
    this.tenantId = tenantId;
    this.occurredAt = System.currentTimeMillis();
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public long getOccurredAt() {
    return occurredAt;
  }

  public abstract String getEventType();
}
