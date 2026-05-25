package fr.docai.application.seed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fr.docai.domain.tenant.Plan;
import fr.docai.domain.tenant.Tenant;
import fr.docai.domain.tenant.TenantId;
import fr.docai.domain.port.out.TenantRepositoryPort;
import fr.docai.domain.user.Role;
import fr.docai.domain.user.User;
import fr.docai.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeedingServiceTest {
    @Mock
    private TenantRepositoryPort tenantRepository;
    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private SeedingService seedingService;

    private void setupDefaultMocks() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
    }

    @Test
    void shouldCreateAllTenantsOnFirstRun() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        verify(tenantRepository, times(3)).save(any(Tenant.class));
    }

    @Test
    void shouldCreateAllUsersOnFirstRun() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        verify(userRepository, times(10)).save(any(User.class));
    }

    @Test
    void shouldBeIdempotentWhenTenantsAlreadyExist() {
        when(tenantRepository.existsById(any())).thenReturn(true);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(true);

        seedingService.seedDevData();

        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldCreateOnlyNewTenantsOnSubsequentRuns() {
        when(tenantRepository.existsById(TenantId.of("acme-corp"))).thenReturn(true);
        when(tenantRepository.existsById(TenantId.of("beta-assur"))).thenReturn(false);
        when(tenantRepository.existsById(TenantId.of("gamma-rh"))).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        verify(tenantRepository, times(2)).save(any(Tenant.class));
    }

    @Test
    void shouldCreateOnlyNewUsersOnSubsequentRuns() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(userRepository.existsByEmail(TenantId.of("acme-corp"), "admin@acme-corp.test")).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        verify(userRepository, times(9)).save(any(User.class));
    }

    @Test
    void shouldCreateProPlanForAcmeCorp() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        var tenantCaptor = org.mockito.ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(3)).save(tenantCaptor.capture());

        var tenants = tenantCaptor.getAllValues();
        var acmeCorpTenant = tenants.stream()
            .filter(t -> t.id().value().equals("acme-corp"))
            .findFirst();

        assertTrue(acmeCorpTenant.isPresent(), "ACME Corp tenant should be created");
        assertEquals(Plan.PRO, acmeCorpTenant.get().plan(), "ACME Corp should have PRO plan");
    }

    @Test
    void shouldCreateStarterPlanForBetaAssurAndGammaRh() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        var tenantCaptor = org.mockito.ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(3)).save(tenantCaptor.capture());

        var tenants = tenantCaptor.getAllValues();
        var starterPlanTenants = tenants.stream()
            .filter(t -> t.plan() == Plan.STARTER)
            .count();

        assertEquals(2, starterPlanTenants, "Beta Assur and Gamma RH should have STARTER plan");
    }

    @Test
    void shouldAssignCorrectRolesToUsers() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(10)).save(userCaptor.capture());

        var users = userCaptor.getAllValues();
        var adminUsers = users.stream()
            .filter(u -> u.role() == Role.TENANT_ADMIN)
            .count();
        var analystUsers = users.stream()
            .filter(u -> u.role() == Role.ANALYST)
            .count();
        var viewerUsers = users.stream()
            .filter(u -> u.role() == Role.VIEWER)
            .count();
        var fraudReviewers = users.stream()
            .filter(u -> u.role() == Role.FRAUD_REVIEWER)
            .count();

        assertEquals(3, adminUsers, "Should have 3 TENANT_ADMIN users");
        assertEquals(3, analystUsers, "Should have 3 ANALYST users");
        assertEquals(3, viewerUsers, "Should have 3 VIEWER users");
        assertEquals(1, fraudReviewers, "Should have 1 FRAUD_REVIEWER user");
    }

    @Test
    void shouldAssignUsersToCorrectTenants() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(fr.docai.domain.tenant.TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(10)).save(userCaptor.capture());

        var users = userCaptor.getAllValues();
        var acmeCorpUsers = users.stream()
            .filter(u -> u.tenantId().value().equals("acme-corp"))
            .count();
        var betaAssurUsers = users.stream()
            .filter(u -> u.tenantId().value().equals("beta-assur"))
            .count();
        var gammaRhUsers = users.stream()
            .filter(u -> u.tenantId().value().equals("gamma-rh"))
            .count();

        assertEquals(4, acmeCorpUsers, "ACME Corp should have 4 users");
        assertEquals(3, betaAssurUsers, "Beta Assur should have 3 users");
        assertEquals(3, gammaRhUsers, "Gamma RH should have 3 users");
    }

    @Test
    void shouldFailFastWhenTenantSaveFails() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        doThrow(new RuntimeException("Database error")).when(tenantRepository).save(any());

        assertThrows(RuntimeException.class, seedingService::seedDevData);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldFailFastWhenUserSaveFails() {
        setupDefaultMocks();
        doThrow(new RuntimeException("Database error")).when(userRepository).save(any());

        assertThrows(RuntimeException.class, seedingService::seedDevData);
    }

    @Test
    void shouldEncodeDefaultPasswordBeforeSaving() {
        setupDefaultMocks();

        seedingService.seedDevData();

        verify(passwordEncoder, times(10)).encode("Test1234!");
    }

    @Test
    void shouldCallExistsByEmailWithCorrectTenantId() {
        setupDefaultMocks();

        seedingService.seedDevData();

        verify(userRepository).existsByEmail(TenantId.of("acme-corp"), "admin@acme-corp.test");
        verify(userRepository).existsByEmail(TenantId.of("beta-assur"), "admin@beta-assur.test");
        verify(userRepository).existsByEmail(TenantId.of("gamma-rh"), "admin@gamma-rh.test");
    }

    @Test
    void shouldAssignSpecificRolesToSpecificUsers() {
        setupDefaultMocks();

        seedingService.seedDevData();

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(10)).save(userCaptor.capture());

        var users = userCaptor.getAllValues();
        var acmeAdmin = users.stream()
            .filter(u -> u.email().equals("admin@acme-corp.test"))
            .findFirst();
        var acmeAnalyst = users.stream()
            .filter(u -> u.email().equals("analyst@acme-corp.test"))
            .findFirst();
        var fraudReviewer = users.stream()
            .filter(u -> u.email().equals("reviewer@acme-corp.test"))
            .findFirst();

        assertTrue(acmeAdmin.isPresent());
        assertEquals(Role.TENANT_ADMIN, acmeAdmin.get().role());
        assertTrue(acmeAnalyst.isPresent());
        assertEquals(Role.ANALYST, acmeAnalyst.get().role());
        assertTrue(fraudReviewer.isPresent());
        assertEquals(Role.FRAUD_REVIEWER, fraudReviewer.get().role());
    }

    @Test
    void shouldCreateTenantsInCorrectOrder() {
        setupDefaultMocks();

        seedingService.seedDevData();

        var tenantCaptor = org.mockito.ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(3)).save(tenantCaptor.capture());

        var tenants = tenantCaptor.getAllValues();
        assertEquals(3, tenants.size());
        assertEquals("acme-corp", tenants.get(0).id().value());
        assertEquals("beta-assur", tenants.get(1).id().value());
        assertEquals("gamma-rh", tenants.get(2).id().value());
    }

    @Test
    void shouldNotCreateDuplicateTenants() {
        when(tenantRepository.existsById(TenantId.of("acme-corp"))).thenReturn(true);
        when(tenantRepository.existsById(TenantId.of("beta-assur"))).thenReturn(false);
        when(tenantRepository.existsById(TenantId.of("gamma-rh"))).thenReturn(false);
        when(userRepository.existsByEmail(any(TenantId.class), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        var tenantCaptor = org.mockito.ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(2)).save(tenantCaptor.capture());

        var saved = tenantCaptor.getAllValues();
        assertFalse(saved.stream().anyMatch(t -> t.id().value().equals("acme-corp")));
    }

    @Test
    void shouldNotCreateDuplicateUsers() {
        when(tenantRepository.existsById(any())).thenReturn(false);
        when(userRepository.existsByEmail(any(TenantId.class), any())).thenReturn(false);
        when(userRepository.existsByEmail(TenantId.of("acme-corp"), "admin@acme-corp.test"))
            .thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        seedingService.seedDevData();

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(9)).save(userCaptor.capture());

        var saved = userCaptor.getAllValues();
        assertFalse(saved.stream().anyMatch(u -> u.email().equals("admin@acme-corp.test")));
    }
}
