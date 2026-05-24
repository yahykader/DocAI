package fr.docai.domain.tenant;

import java.util.Optional;

public interface TenantRepositoryPort {
    void save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    boolean existsById(TenantId id);
}
