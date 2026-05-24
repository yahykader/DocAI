package fr.docai.domain.tenant;

import java.time.Instant;
import java.util.Objects;

public class Tenant {
    private final TenantId id;
    private final String name;
    private final Plan plan;
    private final Instant createdAt;

    private Tenant(TenantId id, String name, Plan plan, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Tenant id cannot be null");
        this.name = Objects.requireNonNull(name, "Tenant name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tenant name cannot be blank");
        }
        this.plan = Objects.requireNonNull(plan, "Tenant plan cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Tenant createdAt cannot be null");
    }

    public static Tenant create(TenantId id, String name, Plan plan) {
        return new Tenant(id, name, plan, Instant.now());
    }

    static Tenant reconstruct(TenantId id, String name, Plan plan, Instant createdAt) {
        return new Tenant(id, name, plan, createdAt);
    }

    public TenantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Plan plan() {
        return plan;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return Objects.equals(id, tenant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Tenant{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", plan=" + plan +
                '}';
    }
}
