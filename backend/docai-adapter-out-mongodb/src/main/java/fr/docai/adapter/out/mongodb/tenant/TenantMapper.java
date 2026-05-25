package fr.docai.adapter.out.mongodb.tenant;

import org.springframework.stereotype.Component;

import fr.docai.domain.model.tenant.Tenant;
import fr.docai.domain.model.tenant.TenantId;

@Component
public class TenantMapper {
    public TenantDocument toPersistence(Tenant domain) {
        return new TenantDocument(
            domain.id().value(),
            domain.name(),
            domain.plan(),
            domain.createdAt()
        );
    }

    public Tenant toDomain(TenantDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("TenantDocument cannot be null");
        }
        if (document.getId() == null || document.getId().isBlank()) {
            throw new IllegalArgumentException("TenantDocument id cannot be null or blank");
        }
        if (document.getName() == null || document.getName().isBlank()) {
            throw new IllegalArgumentException("TenantDocument name cannot be null or blank");
        }
        if (document.getPlan() == null) {
            throw new IllegalArgumentException("TenantDocument plan cannot be null");
        }
        if (document.getCreatedAt() == null) {
            throw new IllegalArgumentException("TenantDocument createdAt cannot be null");
        }

        return Tenant.reconstruct(
            TenantId.of(document.getId()),
            document.getName(),
            document.getPlan(),
            document.getCreatedAt()
        );
    }
}
