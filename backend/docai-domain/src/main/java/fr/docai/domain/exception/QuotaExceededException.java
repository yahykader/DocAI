package fr.docai.domain.exception;

public class QuotaExceededException extends DomainException {

  public QuotaExceededException(String tenantId, int remaining) {
    super("Quota exceeded for tenant: " + tenantId + ". Remaining: " + remaining);
  }
}
