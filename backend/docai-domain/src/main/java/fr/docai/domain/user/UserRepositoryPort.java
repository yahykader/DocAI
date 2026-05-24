package fr.docai.domain.user;

import fr.docai.domain.tenant.TenantId;
import java.util.Optional;

public interface UserRepositoryPort {
    void save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(TenantId tenantId, String email);

    boolean existsByEmail(TenantId tenantId, String email);
}
