package fr.docai.adapter.out.mongodb.user;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import fr.docai.domain.model.user.Role;

/**
 * MongoDB document for the users collection.
 * Index {tenantId, email} is created exclusively by Mongock V002 (BR-MIG-003).
 */
@Document(collection = "users")
public class UserDocument {
    @Id
    private String id;
    private String email;
    private String tenantId;
    private Role role;
    private String passwordHash;
    private Instant createdAt;

    public UserDocument() {
    }

    public UserDocument(String id, String email, String tenantId, Role role, String passwordHash, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.tenantId = tenantId;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
