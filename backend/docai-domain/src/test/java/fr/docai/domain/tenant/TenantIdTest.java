package fr.docai.domain.tenant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void shouldCreateValidTenantId() {
        var tenantId = TenantId.of("acme-corp");
        assertEquals("acme-corp", tenantId.value());
    }

    @Test
    void shouldThrowOnNullValue() {
        assertThrows(NullPointerException.class, () -> TenantId.of(null));
    }

    @Test
    void shouldThrowOnBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> TenantId.of("  "));
    }

    @Test
    void shouldThrowOnEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> TenantId.of(""));
    }

    @Test
    void shouldBeEqualForSameValue() {
        var tenantId1 = TenantId.of("acme-corp");
        var tenantId2 = TenantId.of("acme-corp");
        assertEquals(tenantId1, tenantId2);
    }

    @Test
    void shouldHaveSameHashCodeForSameValue() {
        var tenantId1 = TenantId.of("acme-corp");
        var tenantId2 = TenantId.of("acme-corp");
        assertEquals(tenantId1.hashCode(), tenantId2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValues() {
        var tenantId1 = TenantId.of("acme-corp");
        var tenantId2 = TenantId.of("beta-assur");
        assertNotEquals(tenantId1, tenantId2);
    }

    @Test
    void shouldReturnValueInToString() {
        var tenantId = TenantId.of("acme-corp");
        assertEquals("acme-corp", tenantId.toString());
    }
}
