package fr.docai.domain.user;

import java.util.Objects;
import java.util.UUID;

public final class UserId {
    private final String value;

    private UserId(String value) {
        this.value = Objects.requireNonNull(value, "UserId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserId value cannot be blank");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return Objects.equals(value, userId.value);
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
