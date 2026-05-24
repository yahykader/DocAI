package fr.docai.domain.user;

import static org.junit.jupiter.api.Assertions.*;

import fr.docai.domain.tenant.TenantId;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void shouldCreateValidUser() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        var user = User.create(userId, "admin@acme-corp.test", tenantId, Role.TENANT_ADMIN, "hashed");

        assertEquals(userId, user.id());
        assertEquals("admin@acme-corp.test", user.email());
        assertEquals(tenantId, user.tenantId());
        assertEquals(Role.TENANT_ADMIN, user.role());
        assertEquals("hashed", user.passwordHash());
        assertNotNull(user.createdAt());
    }

    @Test
    void shouldThrowOnNullId() {
        var tenantId = TenantId.of("acme-corp");
        assertThrows(NullPointerException.class, () ->
            User.create(null, "test@test.com", tenantId, Role.ANALYST, "hash")
        );
    }

    @Test
    void shouldThrowOnNullEmail() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        assertThrows(NullPointerException.class, () ->
            User.create(userId, null, tenantId, Role.ANALYST, "hash")
        );
    }

    @Test
    void shouldThrowOnBlankEmail() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        assertThrows(IllegalArgumentException.class, () ->
            User.create(userId, "  ", tenantId, Role.ANALYST, "hash")
        );
    }

    @Test
    void shouldThrowOnNullTenantId() {
        var userId = UserId.of("user-123");
        assertThrows(NullPointerException.class, () ->
            User.create(userId, "test@test.com", null, Role.ANALYST, "hash")
        );
    }

    @Test
    void shouldThrowOnNullRole() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        assertThrows(NullPointerException.class, () ->
            User.create(userId, "test@test.com", tenantId, null, "hash")
        );
    }

    @Test
    void shouldThrowOnNullPasswordHash() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        assertThrows(NullPointerException.class, () ->
            User.create(userId, "test@test.com", tenantId, Role.ANALYST, null)
        );
    }

    @Test
    void shouldBeEqualForSameId() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        var user1 = User.create(userId, "admin@acme.test", tenantId, Role.TENANT_ADMIN, "hash1");
        var user2 = User.create(userId, "different@acme.test", tenantId, Role.ANALYST, "hash2");

        assertEquals(user1, user2);
    }

    @Test
    void shouldNotBeEqualForDifferentIds() {
        var tenantId = TenantId.of("acme-corp");
        var user1 = User.create(UserId.of("user-1"), "test1@acme.test", tenantId, Role.ANALYST, "hash");
        var user2 = User.create(UserId.of("user-2"), "test2@acme.test", tenantId, Role.ANALYST, "hash");

        assertNotEquals(user1, user2);
    }

    @Test
    void shouldSupportAllRoles() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");

        var adminUser = User.create(userId, "admin@acme.test", tenantId, Role.TENANT_ADMIN, "hash");
        var analystUser = User.create(userId, "analyst@acme.test", tenantId, Role.ANALYST, "hash");
        var viewerUser = User.create(userId, "viewer@acme.test", tenantId, Role.VIEWER, "hash");
        var fraudUser = User.create(userId, "fraud@acme.test", tenantId, Role.FRAUD_REVIEWER, "hash");

        assertEquals(Role.TENANT_ADMIN, adminUser.role());
        assertEquals(Role.ANALYST, analystUser.role());
        assertEquals(Role.VIEWER, viewerUser.role());
        assertEquals(Role.FRAUD_REVIEWER, fraudUser.role());
    }

    @Test
    void shouldBelongToCorrectTenant() {
        var acmeTenant = TenantId.of("acme-corp");
        var betaTenant = TenantId.of("beta-assur");

        var acmeUser = User.create(UserId.of("u1"), "user@acme.test", acmeTenant, Role.ANALYST, "hash");
        var betaUser = User.create(UserId.of("u2"), "user@beta.test", betaTenant, Role.ANALYST, "hash");

        assertEquals(acmeTenant, acmeUser.tenantId());
        assertEquals(betaTenant, betaUser.tenantId());
        assertNotEquals(acmeUser.tenantId(), betaUser.tenantId());
    }
}
