package fr.docai.application.seed;

import fr.docai.domain.tenant.Plan;
import fr.docai.domain.tenant.Tenant;
import fr.docai.domain.tenant.TenantId;
import fr.docai.domain.port.out.TenantRepositoryPort;
import fr.docai.domain.user.Role;
import fr.docai.domain.user.User;
import fr.docai.domain.user.UserId;
import fr.docai.domain.port.out.UserRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedingService {
    private static final Logger LOG = LoggerFactory.getLogger(SeedingService.class);

    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "Test1234!";

    public SeedingService(
            TenantRepositoryPort tenantRepository,
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void seedDevData() {
        LOG.info("Starting dev data seeding...");

        try {
            seedTenants();
            seedUsers();
            LOG.info("Dev data seeding completed successfully");
        } catch (Exception e) {
            LOG.error("Dev data seeding failed - data may be incomplete", e);
            throw e;
        }
    }

    private void seedTenants() {
        List<TenantSeedData> tenantsToSeed = List.of(
            new TenantSeedData("acme-corp", Plan.PRO),
            new TenantSeedData("beta-assur", Plan.STARTER),
            new TenantSeedData("gamma-rh", Plan.STARTER)
        );

        for (TenantSeedData tenantData : tenantsToSeed) {
            try {
                TenantId tenantId = TenantId.of(tenantData.id());
                if (tenantRepository.existsById(tenantId)) {
                    LOG.debug("Tenant already exists: {}", tenantData.id());
                } else {
                    Tenant tenant = Tenant.create(tenantId, tenantData.name(), tenantData.plan());
                    tenantRepository.save(tenant);
                    LOG.info("Created tenant: {} (plan: {})", tenantData.id(), tenantData.plan());
                }
            } catch (Exception e) {
                LOG.error("Failed to seed tenant: {}", tenantData.id(), e);
                throw new RuntimeException("Failed to seed tenant: " + tenantData.id(), e);
            }
        }
    }

    private void seedUsers() {
        List<UserSeedData> usersToSeed = List.of(
            // acme-corp users
            new UserSeedData("admin@acme-corp.test", "acme-corp", Role.TENANT_ADMIN),
            new UserSeedData("analyst@acme-corp.test", "acme-corp", Role.ANALYST),
            new UserSeedData("viewer@acme-corp.test", "acme-corp", Role.VIEWER),
            new UserSeedData("reviewer@acme-corp.test", "acme-corp", Role.FRAUD_REVIEWER),
            // beta-assur users
            new UserSeedData("admin@beta-assur.test", "beta-assur", Role.TENANT_ADMIN),
            new UserSeedData("analyst@beta-assur.test", "beta-assur", Role.ANALYST),
            new UserSeedData("viewer@beta-assur.test", "beta-assur", Role.VIEWER),
            // gamma-rh users
            new UserSeedData("admin@gamma-rh.test", "gamma-rh", Role.TENANT_ADMIN),
            new UserSeedData("analyst@gamma-rh.test", "gamma-rh", Role.ANALYST),
            new UserSeedData("viewer@gamma-rh.test", "gamma-rh", Role.VIEWER)
        );

        for (UserSeedData userData : usersToSeed) {
            try {
                TenantId tenantId = TenantId.of(userData.tenantId());
                if (userRepository.existsByEmail(tenantId, userData.email())) {
                    LOG.debug("User already exists: {}", userData.email());
                } else {
                    String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);
                    User user = User.create(
                        UserId.generate(),
                        userData.email(),
                        tenantId,
                        userData.role(),
                        passwordHash
                    );
                    userRepository.save(user);
                    LOG.info("Created user: {} (tenant: {}, role: {})",
                        userData.email(), userData.tenantId(), userData.role());
                }
            } catch (Exception e) {
                LOG.error("Failed to seed user: {}", userData.email(), e);
                throw new RuntimeException("Failed to seed user: " + userData.email(), e);
            }
        }
    }

    private record TenantSeedData(String id, Plan plan) {
        String name() {
            return id.replace("-", " ").toUpperCase();
        }
    }

    private record UserSeedData(String email, String tenantId, Role role) {
    }
}
