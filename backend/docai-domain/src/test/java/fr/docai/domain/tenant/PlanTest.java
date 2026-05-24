package fr.docai.domain.tenant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlanTest {

    @Test
    void shouldHaveValidPlanTypes() {
        assertNotNull(Plan.STARTER);
        assertNotNull(Plan.PRO);
        assertNotNull(Plan.ENTERPRISE);
    }

    @Test
    void shouldHaveDescriptions() {
        assertNotNull(Plan.STARTER.description());
        assertNotNull(Plan.PRO.description());
        assertNotNull(Plan.ENTERPRISE.description());

        assertFalse(Plan.STARTER.description().isBlank());
        assertFalse(Plan.PRO.description().isBlank());
        assertFalse(Plan.ENTERPRISE.description().isBlank());
    }

    @Test
    void shouldBeEnumValues() {
        var values = Plan.values();
        assertEquals(3, values.length);
        assertTrue(arrayContains(values, Plan.STARTER));
        assertTrue(arrayContains(values, Plan.PRO));
        assertTrue(arrayContains(values, Plan.ENTERPRISE));
    }

    private boolean arrayContains(Plan[] array, Plan value) {
        for (Plan plan : array) {
            if (plan == value) {
                return true;
            }
        }
        return false;
    }
}
