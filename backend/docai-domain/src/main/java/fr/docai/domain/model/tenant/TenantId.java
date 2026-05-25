package fr.docai.domain.model.tenant;

import java.util.Objects;

public final class TenantId {
    private final String value;

    private TenantId(String value) {
        this.value = Objects.requireNonNull(value, "TenantId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId value cannot be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantId tenantId = (TenantId) o;
        return Objects.equals(value, tenantId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
