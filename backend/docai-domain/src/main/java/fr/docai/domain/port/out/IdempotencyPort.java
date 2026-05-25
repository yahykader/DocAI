package fr.docai.domain.port.out;

import java.util.Optional;

public interface IdempotencyPort {

  void recordRequest(String idempotencyKey, String tenantId, Object request);

  Optional<IdempotencyRecord> findByKey(String idempotencyKey);

  void updateResult(String idempotencyKey, Object result);

  record IdempotencyRecord(
      String key,
      String tenantId,
      Object request,
      Object result,
      long createdAt,
      long expiresAt) {}
}
