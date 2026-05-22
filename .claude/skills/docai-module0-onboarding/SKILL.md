---
name: docai-module0-onboarding
description: Implémente le Module 0 Phase 0.1 et 0.3 DocAI (inscription tenant, invitation équipe, activation compte, révocation). Utiliser quand on demande d'implémenter l'inscription, la création d'un tenant, l'invitation d'un utilisateur, l'activation via token, ou la révocation d'accès.
---

# DocAI — Module 0 Onboarding (Phase 0.1 + 0.3)

## Flow complet inscription → activation

```
POST /v1/public/signup (public — sans JWT)
  → Génère tenant_id slug unique ("ACME Corp" → "acme-corp")
  → Crée utilisateur dans Keycloak + rôle TENANT_ADMIN automatique
  → Initialise tenant MongoDB (plan, quota)
  → Initialise préfixe S3 : acme-corp/
  → Publie TenantCreated via Outbox
  → Envoie email de bienvenue + lien vérification (24h)
  → HTTP 201 avec tenantId
```

## Domain Model

```java
// Tenant — Aggregate
@AggregateRoot
public class Tenant {
    private final TenantId id;           // "acme-corp"
    private final String companyName;    // "ACME Corp"
    private SubscriptionPlan plan;       // FREE, STARTER, PRO, ENTERPRISE
    private TenantStatus status;         // ACTIVE, SUSPENDED, EXPIRED, CANCELED
    private final Instant createdAt;
}

// TenantUser — Aggregate
@AggregateRoot
public class TenantUser {
    private final String userId;         // ID Keycloak
    private final String tenantId;
    private final String email;
    private UserRole role;               // TENANT_ADMIN, ANALYST, VIEWER, FRAUD_REVIEWER
    private UserStatus status;           // ACTIVE, PENDING, REVOKED
}

// InvitationToken — Value Object
public record InvitationToken(
    UUID token,
    String tenantId,
    String invitedEmail,
    UserRole role,
    String invitedBy,
    Instant expiresAt,   // TTL 7 jours
    boolean used
) {}

// Domain Events
public record TenantCreated(String tenantId, String companyName, String adminEmail, Instant occurredAt) {}
public record UserInvited(String tenantId, String invitedEmail, UserRole role, String invitedBy, Instant occurredAt) {}
public record UserActivated(String tenantId, String userId, UserRole role, Instant occurredAt) {}
public record UserRevoked(String tenantId, String userId, String revokedBy, Instant occurredAt) {}
```

## Business Rules

| ID | Règle |
|----|-------|
| BR-ONB-001 | Email unique dans tout le système |
| BR-ONB-002 | tenant_id = slug du nom entreprise ("ACME Corp" → "acme-corp") |
| BR-ONB-003 | Si slug existe → suffixe numérique ("acme-corp-2") |
| BR-ONB-004 | Rôle TENANT_ADMIN attribué automatiquement au souscripteur |
| BR-ONB-005 | Email de bienvenue envoyé dans les 60 secondes |
| BR-ONB-010 | Seul un TENANT_ADMIN peut inviter dans son propre tenant |
| BR-ONB-013 | Rôle TENANT_ADMIN attribuable uniquement par le rôle SYSTEM |
| BR-ONB-014 | Invitation valable 7 jours |
| BR-ONB-016 | Révocation effective immédiatement (invalidation JWT Keycloak) |

## SignupTenantUseCase

```java
@Component
public class SignupTenantUseCaseImpl implements SignupTenantUseCase {

    @Override
    public SignupResult execute(SignupCommand command) {
        // 1. Vérifier unicité email
        if (identityProviderPort.emailExists(command.email())) {
            throw new EmailAlreadyExistsException(command.email());
        }

        // 2. Générer tenant_id slug unique
        String tenantId = generateUniqueSlug(command.companyName());

        // 3. Créer utilisateur Keycloak + rôle TENANT_ADMIN
        String userId = identityProviderPort.createUser(
            new CreateUserCommand(command.email(), command.password(), tenantId)
        );
        identityProviderPort.assignRole(userId, tenantId, UserRole.TENANT_ADMIN);

        // 4. Initialiser tenant MongoDB
        Tenant tenant = new Tenant(TenantId.of(tenantId), command.companyName(),
            SubscriptionPlan.FREE, TenantStatus.ACTIVE, Instant.now());
        tenantRepository.save(tenant);

        // 5. Publier événement via Outbox
        outboxPublisher.publish("Tenant", tenantId, "TenantCreated",
            TenantCreated.of(tenantId, command.companyName(), command.email()), tenantId, tenantId);

        // 6. Envoyer email bienvenue
        emailPort.send(EmailMessage.of(command.email(), "welcome",
            Map.of("tenantName", command.companyName(), "verificationToken", generateVerificationToken()),
            tenantId));

        log.info("Tenant created tenantId={} plan=FREE", tenantId);
        return new SignupResult(tenantId, userId);
    }

    private String generateUniqueSlug(String companyName) {
        String base = companyName.toLowerCase()
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        String slug = base;
        int suffix = 2;
        while (tenantRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
```

## InviteUserUseCase + ActivateUserUseCase

```java
@Component
public class InviteUserUseCaseImpl implements InviteUserUseCase {

    @Override
    @Audited(action = "USER_INVITED", resourceType = "TenantUser")
    public void execute(InviteUserCommand command) {
        // Vérifier isolation tenant (TENANT_ADMIN ne peut inviter que dans son tenant)
        String currentTenant = TenantContext.get();
        if (!currentTenant.equals(command.tenantId())) {
            throw new UnauthorizedTenantAccessException(currentTenant, command.tenantId());
        }

        // Créer utilisateur Keycloak (sans mot de passe)
        String userId = identityProviderPort.createUser(
            CreateUserCommand.invitation(command.email(), command.tenantId())
        );
        identityProviderPort.assignRole(userId, command.tenantId(), command.role());

        // Token d'invitation (UUID, TTL 7 jours, MongoDB TTL index)
        InvitationToken token = new InvitationToken(
            UUID.randomUUID(), command.tenantId(), command.email(),
            command.role(), command.invitedBy(), Instant.now().plus(7, ChronoUnit.DAYS), false
        );
        invitationTokenRepository.save(token);

        // Email invitation
        emailPort.send(EmailMessage.of(command.email(), "invitation", Map.of(
            "tenantName", getTenantName(command.tenantId()),
            "role", command.role().name(),
            "activationLink", buildActivationLink(token.token())
        ), command.tenantId()));
    }
}

@Component
public class ActivateUserUseCaseImpl implements ActivateUserUseCase {

    @Override
    public void execute(ActivateUserCommand command) {
        InvitationToken token = invitationTokenRepository
            .findByToken(command.token())
            .orElseThrow(() -> new InvalidInvitationTokenException(command.token()));

        if (token.expiresAt().isBefore(Instant.now())) {
            throw new InvitationTokenExpiredException(command.token()); // HTTP 410
        }
        if (token.used()) {
            throw new InvitationTokenAlreadyUsedException(command.token());
        }

        // Définir le mot de passe dans Keycloak
        identityProviderPort.setPassword(token.invitedEmail(), command.password());

        // Marquer token comme utilisé
        invitationTokenRepository.markAsUsed(token.token());

        log.info("User activated tenantId={} role={}", token.tenantId(), token.role());
    }
}
```

## Endpoints

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| POST | `/v1/public/signup` | ❌ Public | Créer un compte tenant |
| GET | `/v1/public/verify-email?token=xxx` | ❌ Public | Vérifier email |
| POST | `/v1/team/invite` | ✅ TENANT_ADMIN | Inviter un utilisateur |
| GET | `/v1/team/users` | ✅ TENANT_ADMIN | Lister les utilisateurs |
| PUT | `/v1/team/users/{userId}/role` | ✅ TENANT_ADMIN | Changer le rôle |
| DELETE | `/v1/team/users/{userId}` | ✅ TENANT_ADMIN | Révoquer l'accès |
| GET | `/v1/public/accept-invitation?token=xxx` | ❌ Public | Activer via invitation |

## Collection MongoDB — invitation_tokens

```java
// TTL index MongoDB 7 jours — nettoyage automatique
@Document(collection = "invitation_tokens")
// @Field("expires_at") avec TTL index : db.invitation_tokens.createIndex({expiresAt: 1}, {expireAfterSeconds: 0})
```

## Scénarios BDD clés

```gherkin
Scenario: Inscription réussie
  Given formulaire avec email "alice@acme.com", nom "ACME Corp"
  When alice soumet l'inscription
  Then tenant "acme-corp" est créé, alice est TENANT_ADMIN
  And email de bienvenue envoyé dans les 60s

Scenario: Collision slug — suffixe automatique
  Given "acme-corp" existe déjà
  When nouvelle entreprise "ACME Corp" s'inscrit
  Then tenant_id = "acme-corp-2"

Scenario: Révocation immédiate
  Given bob est connecté avec JWT valide
  When alice (TENANT_ADMIN) révoque bob
  Then bob reçoit HTTP 401 sur sa prochaine requête
```

## Checklist

- [ ] Slug unique avec suffixe numérique en cas de collision
- [ ] Rôle TENANT_ADMIN attribué automatiquement via Keycloak Admin API
- [ ] TTL index MongoDB 7 jours sur `invitation_tokens.expiresAt`
- [ ] Token invitation usage unique (used=true après activation)
- [ ] Révocation effective immédiatement (invalidation JWT Keycloak)
- [ ] `@Audited` sur invitation et révocation
- [ ] Email `welcome`, `invitation`, `invitation-accepted` envoyés
- [ ] Isolation tenant : TENANT_ADMIN ne peut inviter que dans son tenant
- [ ] Test bout-en-bout : signup → verify-email → invite → activate → login
