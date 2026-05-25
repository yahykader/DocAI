package fr.docai.domain.port.out;

import java.util.Optional;

import fr.docai.domain.model.tenant.Tenant;
import fr.docai.domain.model.tenant.TenantId;

public interface TenantRepositoryPort {
    void save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    boolean existsById(TenantId id);
}
