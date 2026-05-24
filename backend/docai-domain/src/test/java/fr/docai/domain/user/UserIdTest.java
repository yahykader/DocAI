package fr.docai.domain.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    void shouldCreateValidUserIdWithString() {
        var userId = UserId.of("user-123");
        assertEquals("user-123", userId.value());
    }

    @Test
    void shouldGenerateUniqueUserIds() {
        var userId1 = UserId.generate();
        var userId2 = UserId.generate();

        assertNotNull(userId1.value());
        assertNotNull(userId2.value());
        assertNotEquals(userId1.value(), userId2.value());
    }

    @Test
    void shouldThrowOnNullValue() {
        assertThrows(NullPointerException.class, () -> UserId.of(null));
    }

    @Test
    void shouldThrowOnBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> UserId.of("  "));
    }

    @Test
    void shouldBeEqualForSameValue() {
        var userId1 = UserId.of("user-123");
        var userId2 = UserId.of("user-123");
        assertEquals(userId1, userId2);
    }

    @Test
    void shouldHaveSameHashCodeForSameValue() {
        var userId1 = UserId.of("user-123");
        var userId2 = UserId.of("user-123");
        assertEquals(userId1.hashCode(), userId2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValues() {
        var userId1 = UserId.of("user-123");
        var userId2 = UserId.of("user-456");
        assertNotEquals(userId1, userId2);
    }
}
