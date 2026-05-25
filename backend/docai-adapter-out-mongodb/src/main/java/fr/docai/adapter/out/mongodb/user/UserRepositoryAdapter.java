package fr.docai.adapter.out.mongodb.user;

import fr.docai.application.common.exception.RepositoryException;
import fr.docai.domain.model.user.User;
import fr.docai.domain.model.user.UserId;
import fr.docai.domain.port.out.UserRepositoryPort;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {
    private static final Logger LOG = LoggerFactory.getLogger(UserRepositoryAdapter.class);
    private final UserMongoRepository mongoRepository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserMongoRepository mongoRepository, UserMapper mapper) {
        this.mongoRepository = mongoRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(User user) {
        try {
            UserDocument document = mapper.toPersistence(user);
            mongoRepository.save(document);
        } catch (DataAccessException e) {
            LOG.error("Failed to save user: {}", user.id(), e);
            throw new RepositoryException(
                "Failed to save user: " + user.id(),
                "SAVE",
                "User",
                e);
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        try {
            return mongoRepository.findById(id.value())
                .map(mapper::toDomain);
        } catch (DataAccessException e) {
            LOG.error("Failed to find user by id: {}", id.value(), e);
            throw new RepositoryException(
                "Failed to find user by id: " + id.value(),
                "FIND_BY_ID",
                "User",
                e);
        }
    }

    @Override
    public Optional<User> findByEmail(fr.docai.domain.model.tenant.TenantId tenantId, String email) {
        try {
            return mongoRepository.findByTenantIdAndEmail(tenantId.value(), email)
                .map(mapper::toDomain);
        } catch (DataAccessException e) {
            LOG.error("Failed to find user by email: {} in tenant: {}", email, tenantId.value(), e);
            throw new RepositoryException(
                "Failed to find user by email: " + email + " in tenant: " + tenantId.value(),
                "FIND_BY_EMAIL",
                "User",
                e);
        }
    }

    @Override
    public boolean existsByEmail(fr.docai.domain.model.tenant.TenantId tenantId, String email) {
        try {
            return mongoRepository.existsByTenantIdAndEmail(tenantId.value(), email);
        } catch (DataAccessException e) {
            LOG.error("Failed to check user existence by email: {} in tenant: {}", email, tenantId.value(), e);
            throw new RepositoryException(
                "Failed to check user existence by email: " + email + " in tenant: " + tenantId.value(),
                "EXISTS_BY_EMAIL",
                "User",
                e);
        }
    }
}
