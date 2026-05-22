---
name: docai-feature-flag
description: Implémente un Feature Flag Unleash dans un Use Case DocAI. Utiliser quand on demande d'ajouter un flag, de protéger une nouvelle fonctionnalité, d'activer progressivement un module, ou d'implémenter un kill switch. Applique le pattern FeatureFlagPort et les conventions DocAI.
---

# DocAI — Implémenter un Feature Flag

## Flags existants — ne pas dupliquer

| Flag | Valeur défaut | Contrôle | Module |
|------|--------------|----------|--------|
| `billing.enabled` | false | Facturation Stripe | Module 7 |
| `fraud.v2.enabled` | false | Nouveau scoring fraude | Module 3 |
| `extraction.mistral.enabled` | false | Swap OpenAI → Mistral | Module 2 |
| `dashboard.search.enabled` | false | Recherche full-text | Module 5.4 |
| `notifications.inapp.enabled` | true | Notifications in-app | Module 5.3 |
| `maintenance.mode` | false | Kill switch global | Tous |

## Port — interface domaine

```java
// Dans fr.docai.domain.port.out
public interface FeatureFlagPort {
    boolean isEnabled(String flagName);
    boolean isEnabled(String flagName, String tenantId);  // Activation par tenant
}
```

## Adapter Unleash

```java
@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private final Unleash unleash;

    public UnleashFeatureFlagAdapter(Unleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        return unleash.isEnabled(flagName);
    }

    @Override
    public boolean isEnabled(String flagName, String tenantId) {
        UnleashContext context = UnleashContext.builder()
            .userId(tenantId)          // Unleash utilise userId pour le targeting tenant
            .build();
        return unleash.isEnabled(flagName, context);
    }
}
```

## Utilisation dans un Use Case

```java
@Component
public class ProcessBillingUseCaseImpl implements ProcessBillingUseCase {

    private final FeatureFlagPort featureFlagPort;
    private final StripePort stripePort;

    @Override
    public BillingResult execute(BillingCommand command) {
        // Vérification du flag avant d'exécuter la logique
        if (!featureFlagPort.isEnabled("billing.enabled", command.tenantId())) {
            log.info("Billing disabled by feature flag tenantId={}", command.tenantId());
            return BillingResult.disabled();  // Comportement par défaut — pas d'erreur
        }

        // Logique de facturation
        return stripePort.charge(command.amount(), command.tenantId());
    }
}
```

## Kill switch global — maintenance.mode

```java
// Dans TenantJwtFilter ou un filtre dédié
@Component
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final FeatureFlagPort featureFlagPort;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        if (featureFlagPort.isEnabled("maintenance.mode")) {
            res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"title\":\"Maintenance en cours\",\"status\":503}");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

## Procédure d'activation progressive

```
Étape 1 → Activer pour les tenants internes DocAI uniquement (test réel)
Étape 2 → Activer pour 10% des tenants aléatoirement (canary release)
Étape 3 → Surveiller métriques Grafana 48h
Étape 4 → Si OK → 50% des tenants
Étape 5 → Si OK → 100% des tenants
Étape 6 → Supprimer le flag du code (nettoyage technique — max 3 mois)
```

## Procédure d'urgence — désactivation immédiate

```
1. Aller dans Unleash UI → désactiver le flag
2. Effet immédiat — aucun redéploiement nécessaire
3. Tous les tenants → comportement par défaut
4. Ouvrir incident dans #incidents-production Slack
5. Corriger le bug → réactiver progressivement
```

## Configuration Unleash

```yaml
# application.yml
unleash:
  app-name: docai-backend
  url: ${UNLEASH_URL:http://localhost:4242/api}
  api-token: ${UNLEASH_API_TOKEN}
  environment: ${SPRING_PROFILES_ACTIVE:dev}
  fetch-toggles-interval: 10s   # Refresh toutes les 10 secondes
```

## Test du Feature Flag

```java
@Test
void should_skip_billing_when_flag_disabled() {
    // Given
    given(featureFlagPort.isEnabled("billing.enabled", "acme-corp")).willReturn(false);

    // When
    BillingResult result = useCase.execute(new BillingCommand("acme-corp", 100));

    // Then
    assertThat(result.isDisabled()).isTrue();
    verify(stripePort, never()).charge(any(), any()); // Stripe jamais appelé
}

@Test
void should_process_billing_when_flag_enabled() {
    given(featureFlagPort.isEnabled("billing.enabled", "acme-corp")).willReturn(true);
    given(stripePort.charge(100, "acme-corp")).willReturn(BillingResult.success("ch_123"));

    BillingResult result = useCase.execute(new BillingCommand("acme-corp", 100));

    assertThat(result.isSuccess()).isTrue();
}
```

## Checklist

- [ ] Flag déclaré dans Unleash avec valeur par défaut `false`
- [ ] `FeatureFlagPort` injecté par constructeur dans le Use Case
- [ ] Comportement par défaut si flag `false` (jamais d'exception)
- [ ] `billing.enabled` → `false` en DEV et STAGING (BR-FF-002)
- [ ] Test : flag désactivé → logique non exécutée
- [ ] Test : flag activé → logique exécutée normalement
- [ ] Flag nettoyé du code après 3 mois d'activation à 100% (BR-FF-003)
