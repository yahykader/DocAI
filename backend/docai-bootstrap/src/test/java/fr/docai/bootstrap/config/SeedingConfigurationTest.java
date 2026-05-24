package fr.docai.bootstrap.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SeedingConfigurationTest {

    @Test
    void shouldCreateValidPasswordEncoder() {
        var encoder = new SeedingConfiguration.PasswordEncoderAdapter(new BCryptPasswordEncoder());
        assertNotNull(encoder);
    }

    @Test
    void shouldEncodePasswordWithBCrypt() {
        var bcrypt = new BCryptPasswordEncoder();
        var adapter = new SeedingConfiguration.PasswordEncoderAdapter(bcrypt);
        var rawPassword = "Test1234!";

        var encoded = adapter.encode(rawPassword);

        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded);
        assertTrue(bcrypt.matches(rawPassword, encoded));
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        var adapter = new SeedingConfiguration.PasswordEncoderAdapter(new BCryptPasswordEncoder());
        var password = "Test1234!";

        var hash1 = adapter.encode(password);
        var hash2 = adapter.encode(password);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2, "BCrypt should generate different hashes each time");
    }

    @Test
    void shouldVerifyEncodedPasswordMatches() {
        var bcrypt = new BCryptPasswordEncoder();
        var adapter = new SeedingConfiguration.PasswordEncoderAdapter(bcrypt);
        var password = "MySecurePassword123!";

        var hash = adapter.encode(password);

        assertTrue(bcrypt.matches(password, hash));
        assertFalse(bcrypt.matches("WrongPassword", hash));
    }

    @Test
    void shouldHandleEmptyPassword() {
        var adapter = new SeedingConfiguration.PasswordEncoderAdapter(new BCryptPasswordEncoder());
        var emptyPassword = "";

        var encoded = adapter.encode(emptyPassword);

        assertNotNull(encoded);
        assertNotEquals(emptyPassword, encoded);
    }

    @Test
    void shouldHandleSpecialCharactersInPassword() {
        var bcrypt = new BCryptPasswordEncoder();
        var adapter = new SeedingConfiguration.PasswordEncoderAdapter(bcrypt);
        var specialPassword = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        var encoded = adapter.encode(specialPassword);

        assertNotNull(encoded);
        assertTrue(bcrypt.matches(specialPassword, encoded));
    }
}
