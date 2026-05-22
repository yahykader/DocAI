---
name: docai-seeding
description: "Implémente le SeedingService DocAI (profil seed uniquement, jamais en production) : 3 tenants preconfigures, 10 utilisateurs (1 par role par tenant), documents PDF exemples de chaque type, idempotence obligatoire, integration Keycloak Admin API + MongoDB + S3. Utiliser quand on demande le seeding DEV ou staging, les donnees de test preconstruites, le bootstrapping local, ou le peuplement de la base pour les tests k6."
---

# DocAI — SeedingService DEV & Staging
## Profil `seed` uniquement — JAMAIS en production

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Module 2.A (Phase 0.A.5)
> **Prérequis :** Setup projet terminé. Keycloak, MongoDB, S3 (MinIO) démarrés et accessibles.
> **Durée estimée :** 0.5 jour

---

## 1. Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-SEED-001 | Seeding **désactivé en production** — profil `seed` inexistant en prod | MUST |
| BR-SEED-002 | Mots de passe de test jamais utilisés en production | MUST |
| BR-SEED-003 | Seeding **idempotent** : exécuté 2× = résultat identique (pas de doublons) | MUST |
| BR-SEED-004 | Seeding DEV inclut des documents de chaque type supporté (6 types) | MUST |
| BR-SEED-005 | Données de test **jamais commitées** en clair — seule la config JSON est versionnée | MUST |
| BR-SEED-006 | Protection anti-production : `if (activeProfiles.contains("production")) throw` | MUST |

---

## 2. Tenants préconfigurés — 3 tenants

| Tenant | tenant_id | Plan | Quota | Usage | Usage |
|--------|-----------|------|-------|-------|-------|
| ACME Corp | `acme-corp` | **PRO** | 10 000 docs | ~450 | Tests fonctionnels principaux |
| Beta Assurances | `beta-assur` | **STARTER** | 500 docs | ~50 | Tests isolation tenant |
| Gamma RH | `gamma-rh` | **STARTER** | 500 docs | **490** | Tests dépassement quota |

---

## 3. Utilisateurs préconfigurés — 10 utilisateurs

**Tenant acme-corp (4 utilisateurs) :**

| Email | Rôle | Mot de passe |
|-------|------|-------------|
| `admin@acme-corp.test` | TENANT_ADMIN | `Test1234!` |
| `analyst@acme-corp.test` | ANALYST | `Test1234!` |
| `viewer@acme-corp.test` | VIEWER | `Test1234!` |
| `reviewer@acme-corp.test` | FRAUD_REVIEWER | `Test1234!` |

**Tenant beta-assur (4 utilisateurs) :**

| Email | Rôle | Mot de passe |
|-------|------|-------------|
| `admin@beta-assur.test` | TENANT_ADMIN | `Test1234!` |
| `analyst@beta-assur.test` | ANALYST | `Test1234!` |
| `viewer@beta-assur.test` | VIEWER | `Test1234!` |
| `reviewer@beta-assur.test` | FRAUD_REVIEWER | `Test1234!` |

**Tenant gamma-rh (2 utilisateurs) :**

| Email | Rôle | Mot de passe |
|-------|------|-------------|
| `admin@gamma-rh.test` | TENANT_ADMIN | `Test1234!` |
| `analyst@gamma-rh.test` | ANALYST | `Test1234!` |

---

## 4. Structure des fichiers seed

```
src/test/resources/seed/
├── dev/
│   ├── tenants.json              ← 3 tenants préconfigurés
│   ├── users.json                ← 10 utilisateurs avec rôles
│   └── documents/                ← PDF exemples (1 par type)
│       ├── facture-exemple.pdf
│       ├── cni-exemple.pdf
│       ├── rib-exemple.pdf
│       ├── ordonnance-exemple.pdf
│       ├── bulletin-salaire-exemple.pdf
│       └── passeport-exemple.pdf
└── staging/
    ├── tenants.json              ← 2 tenants pour tests k6
    └── users.json
```

---

## 5. Implémentation SeedingService

```java
// docai-bootstrap/src/main/java/fr/docai/seed/SeedingService.java
// Activé UNIQUEMENT avec le profil Spring "seed"

@Service
@Profile("seed")   // JAMAIS actif sans ce profil explicite
@Slf4j
public class SeedingService implements ApplicationRunner {

    private final IdentityProviderPort identityProvider;    // Keycloak Admin API
    private final TenantRepositoryPort tenantRepository;    // MongoDB
    private final DocumentStoragePort storagePort;           // S3 / MinIO
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        // BR-SEED-006 : Protection absolue contre l'exécution en production
        if (Arrays.asList(environment.getActiveProfiles()).contains("production")) {
            throw new IllegalStateException(
                "SEED ABORTED: seeding is strictly forbidden in production environment!"
            );
        }

        boolean reset = args.containsOption("seed.reset");
        String env = args.getOptionValues("seed.env") != null
            ? args.getOptionValues("seed.env").get(0) : "dev";

        log.info("Starting DocAI seeding env={} reset={}", env, reset);

        if (reset) {
            log.warn("Resetting all seed data — THIS WILL DELETE EXISTING TEST DATA");
            cleanExistingData();
        }

        seedTenants(env);
        seedUsers(env);
        seedDocuments(env);

        log.info("Seeding completed successfully env={}", env);
        log.info("Test login: admin@acme-corp.test / Test1234!");
    }

    // Idempotence (BR-SEED-003) : vérifie l'existence avant de créer
    private void seedTenants(String env) {
        List<TenantSeedConfig> configs = loadTenantConfigs(env);
        for (TenantSeedConfig config : configs) {
            // Si le tenant existe déjà → skip silencieusement
            if (tenantRepository.existsBySlug(config.tenantId())) {
                log.debug("Tenant already exists, skipping tenantId={}", config.tenantId());
                continue;
            }
            Tenant tenant = new Tenant(
                TenantId.of(config.tenantId()),
                config.companyName(),
                Plan.valueOf(config.plan()),
                TenantStatus.ACTIVE,
                Instant.now()
            );
            tenantRepository.save(tenant);
            log.info("Tenant seeded tenantId={} plan={}", config.tenantId(), config.plan());
        }
    }

    private void seedUsers(String env) {
        List<UserSeedConfig> configs = loadUserConfigs(env);
        for (UserSeedConfig config : configs) {
            // Idempotence : skip si l'email existe déjà dans Keycloak
            if (identityProvider.emailExists(config.email())) {
                log.debug("User already exists, skipping email=[SEED_USER]");
                continue;
            }
            String userId = identityProvider.createUser(new CreateUserCommand(
                config.email(), config.password(), config.tenantId()
            ));
            identityProvider.assignRole(userId, config.tenantId(),
                UserRole.valueOf(config.role()));
            log.info("User seeded role={} tenantId={}", config.role(), config.tenantId());
        }
    }

    private void seedDocuments(String env) {
        // Upload 1 document PDF exemple par type dans S3 pour le tenant acme-corp
        String[] docTypes = {"facture", "cni", "rib", "ordonnance",
                             "bulletin-salaire", "passeport"};
        for (String docType : docTypes) {
            String s3Key = "acme-corp/seed/" + docType + "-exemple.pdf";
            // Idempotence : skip si déjà uploadé
            if (storagePort.exists(s3Key)) {
                log.debug("Seed document already exists type={}", docType);
                continue;
            }
            try (InputStream pdf = loadSeedPdf("dev/documents/" + docType + "-exemple.pdf")) {
                storagePort.upload("acme-corp", "seed-" + docType, docType + "-exemple.pdf",
                    pdf.readAllBytes(), "application/pdf");
                log.info("Seed document uploaded type={}", docType);
            }
        }
    }

    private List<TenantSeedConfig> loadTenantConfigs(String env) {
        // Lire depuis src/test/resources/seed/{env}/tenants.json
        String path = "seed/" + env + "/tenants.json";
        return objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream(path),
            new TypeReference<List<TenantSeedConfig>>() {}
        );
    }
}
```

---

## 6. Fichiers de configuration JSON

### tenants.json (DEV)

```json
[
  {
    "tenantId": "acme-corp",
    "companyName": "ACME Corp",
    "plan": "PRO",
    "quotaMonthly": 10000,
    "quotaUsed": 450
  },
  {
    "tenantId": "beta-assur",
    "companyName": "Beta Assurances",
    "plan": "STARTER",
    "quotaMonthly": 500,
    "quotaUsed": 50
  },
  {
    "tenantId": "gamma-rh",
    "companyName": "Gamma RH",
    "plan": "STARTER",
    "quotaMonthly": 500,
    "quotaUsed": 490
  }
]
```

### users.json (DEV)

```json
[
  {"email": "admin@acme-corp.test",    "password": "Test1234!", "tenantId": "acme-corp",  "role": "TENANT_ADMIN"},
  {"email": "analyst@acme-corp.test",  "password": "Test1234!", "tenantId": "acme-corp",  "role": "ANALYST"},
  {"email": "viewer@acme-corp.test",   "password": "Test1234!", "tenantId": "acme-corp",  "role": "VIEWER"},
  {"email": "reviewer@acme-corp.test", "password": "Test1234!", "tenantId": "acme-corp",  "role": "FRAUD_REVIEWER"},
  {"email": "admin@beta-assur.test",   "password": "Test1234!", "tenantId": "beta-assur", "role": "TENANT_ADMIN"},
  {"email": "analyst@beta-assur.test", "password": "Test1234!", "tenantId": "beta-assur", "role": "ANALYST"},
  {"email": "viewer@beta-assur.test",  "password": "Test1234!", "tenantId": "beta-assur", "role": "VIEWER"},
  {"email": "reviewer@beta-assur.test","password": "Test1234!", "tenantId": "beta-assur", "role": "FRAUD_REVIEWER"},
  {"email": "admin@gamma-rh.test",     "password": "Test1234!", "tenantId": "gamma-rh",   "role": "TENANT_ADMIN"},
  {"email": "analyst@gamma-rh.test",   "password": "Test1234!", "tenantId": "gamma-rh",   "role": "ANALYST"}
]
```

---

## 7. Commandes d'exécution

```bash
# Seeding DEV standard (après docker compose up)
./mvnw spring-boot:run -pl docai-bootstrap \
  -Dspring-boot.run.profiles=dev,seed \
  -Dspring-boot.run.arguments="--seed.enabled=true --seed.env=dev"

# Reset complet + re-seeder (supprime les données existantes)
./mvnw spring-boot:run -pl docai-bootstrap \
  -Dspring-boot.run.profiles=dev,seed \
  -Dspring-boot.run.arguments="--seed.enabled=true --seed.reset=true --seed.env=dev"

# Seeding staging (pour les tests k6)
./mvnw spring-boot:run -pl docai-bootstrap \
  -Dspring-boot.run.profiles=staging,seed \
  -Dspring-boot.run.arguments="--seed.enabled=true --seed.env=staging"

# Vérification seeding réussi
curl -s -X POST http://localhost:8080/v1/public/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme-corp.test","password":"Test1234!"}' | jq '.access_token'
# → Doit retourner un JWT non-null
```

---

## 8. Configuration application.yml

```yaml
# application-seed.yml — actif UNIQUEMENT avec profil "seed"
docai:
  seed:
    enabled: true
    env: dev
    auto-run: true     # Lance le seeding au démarrage de l'app

# Désactiver les vérifications de sécurité strictes en seed
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Accepter les JWT Keycloak local (self-signed)
          jwk-set-uri: ${KEYCLOAK_URL}/realms/docai/protocol/openid-connect/certs

logging:
  level:
    fr.docai.seed: DEBUG  # Logs détaillés pendant le seeding
```

---

## 9. Definition of Done

- [ ] Profil `seed` absent du Dockerfile et des manifestes Kubernetes prod (BR-SEED-001)
- [ ] Protection anti-production levée si profil `production` actif (BR-SEED-006)
- [ ] Seeding idempotent : exécuté 2× → 0 doublon en base, 0 erreur (BR-SEED-003)
- [ ] 3 tenants créés dans MongoDB avec les bons plans et quotas
- [ ] 10 utilisateurs dans Keycloak avec les bons rôles et `tenant_id` dans le JWT
- [ ] 6 documents PDF exemples (1 par type) uploadés dans S3/MinIO
- [ ] Login `admin@acme-corp.test` / `Test1234!` → JWT valide avec `tenant_id: acme-corp`
- [ ] Login `analyst@beta-assur.test` / `Test1234!` → JWT valide avec `tenant_id: beta-assur`
- [ ] Test isolation : documents `beta-assur` non visibles depuis `acme-corp`
- [ ] `gamma-rh` à 490/500 → prochain upload → HTTP 429 QUOTA_EXCEEDED
- [ ] Seeding staging : 2 tenants + utilisateurs pour les scripts k6
- [ ] `src/test/resources/seed/` versionné dans Git (sauf les PDFs sensibles)
