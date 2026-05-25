package fr.docai.domain.port.out;

import fr.docai.domain.model.tenant.TenantId;
import fr.docai.domain.model.user.User;
import fr.docai.domain.model.user.UserId;

import java.util.Optional;

public interface UserRepositoryPort {
    void save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(TenantId tenantId, String email);

    boolean existsByEmail(TenantId tenantId, String email);
}
