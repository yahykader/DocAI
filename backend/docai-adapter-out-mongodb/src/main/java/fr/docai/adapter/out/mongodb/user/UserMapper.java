package fr.docai.adapter.out.mongodb.user;

import fr.docai.domain.model.tenant.TenantId;
import fr.docai.domain.model.user.User;
import fr.docai.domain.model.user.UserId;

import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserDocument toPersistence(User domain) {
        return new UserDocument(
            domain.id().value(),
            domain.email(),
            domain.tenantId().value(),
            domain.role(),
            domain.passwordHash(),
            domain.createdAt()
        );
    }

    public User toDomain(UserDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("UserDocument cannot be null");
        }
        if (document.getId() == null || document.getId().isBlank()) {
            throw new IllegalArgumentException("UserDocument id cannot be null or blank");
        }
        if (document.getEmail() == null || document.getEmail().isBlank()) {
            throw new IllegalArgumentException("UserDocument email cannot be null or blank");
        }
        if (document.getTenantId() == null || document.getTenantId().isBlank()) {
            throw new IllegalArgumentException("UserDocument tenantId cannot be null or blank");
        }
        if (document.getRole() == null) {
            throw new IllegalArgumentException("UserDocument role cannot be null");
        }
        if (document.getPasswordHash() == null || document.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("UserDocument passwordHash cannot be null or blank");
        }
        if (document.getCreatedAt() == null) {
            throw new IllegalArgumentException("UserDocument createdAt cannot be null");
        }

        return User.reconstruct(
            UserId.of(document.getId()),
            document.getEmail(),
            TenantId.of(document.getTenantId()),
            document.getRole(),
            document.getPasswordHash(),
            document.getCreatedAt()
        );
    }
}
