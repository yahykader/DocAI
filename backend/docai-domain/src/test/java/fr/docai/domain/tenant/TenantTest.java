package fr.docai.domain.tenant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TenantTest {

    @Test
    void shouldCreateValidTenant() {
        var tenantId = TenantId.of("acme-corp");
        var tenant = Tenant.create(tenantId, "ACME Corporation", Plan.PRO);

        assertEquals(tenantId, tenant.id());
        assertEquals("ACME Corporation", tenant.name());
        assertEquals(Plan.PRO, tenant.plan());
        assertNotNull(tenant.createdAt());
    }

    @Test
    void shouldThrowOnNullId() {
        assertThrows(NullPointerException.class, () ->
            Tenant.create(null, "ACME", Plan.PRO)
        );
    }

    @Test
    void shouldThrowOnNullName() {
        var tenantId = TenantId.of("acme-corp");
        assertThrows(NullPointerException.class, () ->
            Tenant.create(tenantId, null, Plan.PRO)
        );
    }

    @Test
    void shouldThrowOnBlankName() {
        var tenantId = TenantId.of("acme-corp");
        assertThrows(IllegalArgumentException.class, () ->
            Tenant.create(tenantId, "  ", Plan.PRO)
        );
    }

    @Test
    void shouldThrowOnNullPlan() {
        var tenantId = TenantId.of("acme-corp");
        assertThrows(NullPointerException.class, () ->
            Tenant.create(tenantId, "ACME", null)
        );
    }

    @Test
    void shouldBeEqualForSameId() {
        var tenantId = TenantId.of("acme-corp");
        var tenant1 = Tenant.create(tenantId, "ACME Corp", Plan.PRO);
        var tenant2 = Tenant.create(tenantId, "Different Name", Plan.STARTER);

        assertEquals(tenant1, tenant2);
    }

    @Test
    void shouldNotBeEqualForDifferentIds() {
        var tenant1 = Tenant.create(TenantId.of("acme-corp"), "ACME", Plan.PRO);
        var tenant2 = Tenant.create(TenantId.of("beta-assur"), "Beta", Plan.STARTER);

        assertNotEquals(tenant1, tenant2);
    }

    @Test
    void shouldHaveSameHashCodeForSameId() {
        var tenantId = TenantId.of("acme-corp");
        var tenant1 = Tenant.create(tenantId, "ACME", Plan.PRO);
        var tenant2 = Tenant.create(tenantId, "ACME", Plan.PRO);

        assertEquals(tenant1.hashCode(), tenant2.hashCode());
    }

    @Test
    void shouldSupportAllPlans() {
        var tenantId = TenantId.of("test");

        var starterTenant = Tenant.create(tenantId, "Starter", Plan.STARTER);
        var proTenant = Tenant.create(tenantId, "Pro", Plan.PRO);
        var enterpriseTenant = Tenant.create(tenantId, "Enterprise", Plan.ENTERPRISE);

        assertEquals(Plan.STARTER, starterTenant.plan());
        assertEquals(Plan.PRO, proTenant.plan());
        assertEquals(Plan.ENTERPRISE, enterpriseTenant.plan());
    }
}
