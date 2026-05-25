package fr.docai.adapter.out.mongodb.tenant;

import fr.docai.application.common.exception.RepositoryException;
import fr.docai.domain.model.tenant.Tenant;
import fr.docai.domain.model.tenant.TenantId;
import fr.docai.domain.port.out.TenantRepositoryPort;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class TenantRepositoryAdapter implements TenantRepositoryPort {
    private static final Logger LOG = LoggerFactory.getLogger(TenantRepositoryAdapter.class);
    private final TenantMongoRepository mongoRepository;
    private final TenantMapper mapper;

    public TenantRepositoryAdapter(TenantMongoRepository mongoRepository, TenantMapper mapper) {
        this.mongoRepository = mongoRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Tenant tenant) {
        try {
            TenantDocument document = mapper.toPersistence(tenant);
            mongoRepository.save(document);
        } catch (DataAccessException e) {
            LOG.error("Failed to save tenant: {}", tenant.id(), e);
            throw new RepositoryException(
                "Failed to save tenant: " + tenant.id(),
                "SAVE",
                "Tenant",
                e);
        }
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        try {
            return mongoRepository.findById(id.value())
                .map(mapper::toDomain);
        } catch (DataAccessException e) {
            LOG.error("Failed to find tenant by id: {}", id.value(), e);
            throw new RepositoryException(
                "Failed to find tenant by id: " + id.value(),
                "FIND_BY_ID",
                "Tenant",
                e);
        }
    }

    @Override
    public boolean existsById(TenantId id) {
        try {
            return mongoRepository.existsById(id.value());
        } catch (DataAccessException e) {
            LOG.error("Failed to check tenant existence: {}", id.value(), e);
            throw new RepositoryException(
                "Failed to check tenant existence: " + id.value(),
                "EXISTS_BY_ID",
                "Tenant",
                e);
        }
    }
}
