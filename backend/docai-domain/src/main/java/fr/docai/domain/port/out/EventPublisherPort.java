package fr.docai.domain.port.out;

public interface EventPublisherPort {

  void publish(DomainEvent event);

  void publishAsync(DomainEvent event);

  record DomainEvent(
      String eventType,
      String aggregateId,
      String tenantId,
      long timestamp,
      Object payload) {}
}
