package fr.docai.domain.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import fr.docai.domain.model.user.Role;

class RoleTest {

    @Test
    void shouldHaveValidRoleTypes() {
        assertNotNull(Role.TENANT_ADMIN);
        assertNotNull(Role.ANALYST);
        assertNotNull(Role.VIEWER);
        assertNotNull(Role.FRAUD_REVIEWER);
    }

    @Test
    void shouldHaveDescriptions() {
        assertNotNull(Role.TENANT_ADMIN.description());
        assertNotNull(Role.ANALYST.description());
        assertNotNull(Role.VIEWER.description());
        assertNotNull(Role.FRAUD_REVIEWER.description());

        assertFalse(Role.TENANT_ADMIN.description().isBlank());
        assertFalse(Role.ANALYST.description().isBlank());
        assertFalse(Role.VIEWER.description().isBlank());
        assertFalse(Role.FRAUD_REVIEWER.description().isBlank());
    }

    @Test
    void shouldBeEnumValues() {
        var values = Role.values();
        assertEquals(4, values.length);
        assertTrue(arrayContains(values, Role.TENANT_ADMIN));
        assertTrue(arrayContains(values, Role.ANALYST));
        assertTrue(arrayContains(values, Role.VIEWER));
        assertTrue(arrayContains(values, Role.FRAUD_REVIEWER));
    }

    private boolean arrayContains(Role[] array, Role value) {
        for (Role role : array) {
            if (role == value) {
                return true;
            }
        }
        return false;
    }
}
