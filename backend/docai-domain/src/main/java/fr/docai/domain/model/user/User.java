package fr.docai.domain.model.user;

import java.time.Instant;
import java.util.Objects;

import fr.docai.domain.model.tenant.TenantId;

public class User {
    private final UserId id;
    private final String email;
    private final TenantId tenantId;
    private final Role role;
    private final String passwordHash;
    private final Instant createdAt;

    private User(UserId id, String email, TenantId tenantId, Role role, String passwordHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "User id cannot be null");
        this.email = Objects.requireNonNull(email, "User email cannot be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("User email cannot be blank");
        }
        this.tenantId = Objects.requireNonNull(tenantId, "User tenantId cannot be null");
        this.role = Objects.requireNonNull(role, "User role cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "User passwordHash cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "User createdAt cannot be null");
    }

    public static User create(UserId id, String email, TenantId tenantId, Role role, String passwordHash) {
        return new User(id, email, tenantId, role, passwordHash, Instant.now());
    }

    public static User reconstruct(UserId id, String email, TenantId tenantId, Role role, String passwordHash, Instant createdAt) {
        return new User(id, email, tenantId, role, passwordHash, createdAt);
    }

    public UserId id() {
        return id;
    }

    public String email() {
        return email;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Role role() {
        return role;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", tenantId=" + tenantId +
                ", role=" + role +
                '}';
    }
}
