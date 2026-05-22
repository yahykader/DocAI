---
name: docai-billing-stripe
description: Implémente le Module 7 Billing DocAI (abonnements Stripe, plans Starter/Pro/Enterprise, cycle de vie FREE→PAID, webhooks Stripe, feature flag billing.enabled). Utiliser quand on demande d'implémenter la facturation, les abonnements Stripe, les plans tarifaires, ou la gestion du cycle de vie commercial.
---

# DocAI — Module 7 Billing & Abonnements

## ⚠️ Feature Flag obligatoire

```java
// Toujours vérifier billing.enabled avant d'exécuter la logique Stripe
if (!featureFlagPort.isEnabled("billing.enabled", tenantId)) {
    return BillingResult.disabled(); // Gratuit — pas d'erreur
}
```

## Plans tarifaires

| Plan | Quota | Rate limit | Prix | Overage |
|------|-------|-----------|------|---------|
| FREE (trial) | 50 docs/mois | 100 req/min | Gratuit 30 jours | N/A |
| Starter | 500 docs/mois | 100 req/min | À définir | 0.12€/doc |
| Pro | 10 000 docs/mois | 1 000 req/min | À définir | 0.08€/doc |
| Enterprise | Illimité | Sur devis | À définir | Inclus |

## Cycle de vie commercial

```
Inscription → FREE (14 jours)
  J-7 → Email "trial-ending-7days"
  J-3 → Email "trial-ending-3days"
  J0  → Email "trial-expired" → accès lecture seule
        
Souscription Pro/Starter :
  TENANT_ADMIN choisit plan → Stripe Checkout
  → Paiement OK → "subscription-activated" email
  → BILLING_ENABLED flag → quota activé selon plan
  
Paiement échoué :
  → Email "payment-failed" avec lien mise à jour CB
  → 3 tentatives (J+1, J+4, J+7)
  → Après 3 échecs → suspension compte
```

## Domain Model

```java
// Subscription — Aggregate
@AggregateRoot
public class Subscription {
    private final SubscriptionId id;
    private final String tenantId;
    private SubscriptionPlan plan;          // FREE, STARTER, PRO, ENTERPRISE
    private SubscriptionStatus status;      // TRIALING, ACTIVE, PAST_DUE, CANCELED
    private String stripeSubscriptionId;    // ID Stripe
    private String stripeCustomerId;        // Customer Stripe
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;       // Date renouvellement
    private Instant trialEndAt;             // Fin période gratuite
    private int documentsUsedThisMonth;
    private int documentsIncluded;          // Quota selon le plan
}

// SubscriptionPlan — Enum
public enum SubscriptionPlan {
    FREE(50, 100, Duration.ofDays(30)),
    STARTER(500, 100, null),
    PRO(10_000, 1_000, null),
    ENTERPRISE(Integer.MAX_VALUE, Integer.MAX_VALUE, null);

    public final int quotaPerMonth;
    public final int ratePerMinute;
    public final Duration trialDuration;
}
```

## Adapter Stripe — Webhook Handler

```java
@RestController
@RequestMapping("/v1/webhooks/stripe")
public class StripeWebhookController {

    private final String webhookSecret;
    private final HandleStripeWebhookUseCase webhookUseCase;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String signature
    ) {
        // Vérification signature Stripe — obligatoire
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        webhookUseCase.handle(event.getType(), event.getData());
        return ResponseEntity.ok().build();
    }
}

// Events Stripe à gérer
@Component
public class HandleStripeWebhookUseCaseImpl implements HandleStripeWebhookUseCase {

    @Override
    public void handle(String eventType, EventDataObjectDeserializer data) {
        switch (eventType) {
            case "invoice.payment_succeeded"  -> handlePaymentSucceeded(data);
            case "invoice.payment_failed"     -> handlePaymentFailed(data);
            case "customer.subscription.deleted" -> handleSubscriptionCanceled(data);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(data);
            default -> log.debug("Unhandled Stripe event type={}", eventType);
        }
    }

    private void handlePaymentSucceeded(EventDataObjectDeserializer data) {
        // Activer/renouveler l'abonnement
        // Envoyer email "subscription-activated" ou "invoice"
        // Réinitialiser le compteur documents du mois
    }

    private void handlePaymentFailed(EventDataObjectDeserializer data) {
        // Marquer subscription PAST_DUE
        // Envoyer email "payment-failed"
        // Si 3ème échec → suspendre compte
    }
}
```

## Downgrade plan — ADR-009 (conservation données)

```java
// ADR-009 : En cas de downgrade, les données existantes sont conservées en lecture seule
// (jamais supprimées — le client peut upgrader et retrouver ses données)
@Component
public class DowngradePlanUseCaseImpl implements DowngradePlanUseCase {

    @Override
    public void execute(DowngradePlanCommand command) {
        Subscription sub = subscriptionRepository.findByTenantId(command.tenantId());

        // ADR-009 : données en lecture seule pendant la période de grace
        if (sub.getDocumentsUsedThisMonth() > command.newPlan().getQuotaPerMonth()) {
            // Excès → lecture seule jusqu'au prochain mois
            sub.setReadOnly(true);
            log.info("Plan downgraded with data preserved tenantId={} newPlan={}",
                command.tenantId(), command.newPlan());
        }

        sub.downgradeTo(command.newPlan());
        subscriptionRepository.save(sub);
    }
}
```

## Emails du cycle de vie

| Template | Déclencheur | Contenu clé |
|----------|------------|-------------|
| `trial-ending-7days` | J-7 avant fin FREE | Date expiration, lien choix plan |
| `trial-ending-3days` | J-3 avant fin FREE | Urgence, comparatif plans |
| `trial-expired` | Expiration FREE | Accès lecture seule, lien upgrade |
| `subscription-activated` | Paiement OK | Plan souscrit, quota, date renouvellement |
| `invoice` | 1er du mois | PDF facture en pièce jointe |
| `payment-failed` | Stripe payment_failed | Lien mise à jour CB, date suspension |
| `quota-warning-80` | 80% quota atteint | Usage, date renouvellement, lien upgrade |
| `quota-warning-95` | 95% quota atteint | Urgence, option overage |

## Checklist

- [ ] Feature flag `billing.enabled=false` en DEV et STAGING (BR-FF-002)
- [ ] Vérification flag avant toute logique Stripe
- [ ] Signature webhook Stripe vérifiée avant traitement
- [ ] Emails automatiques sur chaque transition d'état
- [ ] ADR-009 : données conservées en lecture seule au downgrade
- [ ] Job planifié : vérification expiration trial (quotidien)
- [ ] Job planifié : réinitialisation quota mensuel (1er du mois)
- [ ] Test : paiement échoué 3× → suspension compte
- [ ] Test : downgrade → données existantes accessibles en lecture
- [ ] Métrique : `docai_billing_subscription_total{plan, status}`
