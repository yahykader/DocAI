---
name: docai-module0-billing
description: "Implémente le Module 0.4 Billing Fondations DocAI (Feature Flag billing.enabled, plans FREE/Starter/Pro/Enterprise, Aggregate Subscription, cycle de vie TRIAL→ACTIVE→PAST_DUE→CANCELED, compteurs quota Lua atomique, alertes 80%/95%, overage, Stripe Checkout + Customer Portal + webhooks, ADR-009 downgrade). Utiliser quand on demande d'implémenter les plans tarifaires, la souscription, les quotas, le billing, Stripe, le cycle de vie abonnement, ou les emails billing (facturation, quota warning, résiliation). Prérequis : Module 0 Sécurité, Module 0.1 Onboarding terminés."
---

# DocAI — Module 0.4 Billing & Abonnements
## Feature Flag · Plans · Stripe · Quota · Cycle de vie

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 4 (Module 0.4) + Partie 6 (Module 7)
> **Prérequis :** Module 0 Sécurité + Module 0.1 Onboarding + Module 0.2 Auth terminés

---

## 1. Feature Flag — Principe fondamental

**Flag :** `billing.enabled` dans Unleash

```
BILLING_ENABLED = false  →  Tout le monde gratuit, aucun quota
BILLING_ENABLED = true   →  Plans appliqués, Stripe actif, quotas enforced
```

| Situation | `billing.enabled = false` | `billing.enabled = true` |
|-----------|--------------------------|--------------------------|
| Quota mensuel | Illimité pour tous | Selon le plan |
| Stripe | Aucun appel | Actif |
| HTTP 429 sur quota | Impossible | Levé si dépassement |
| Emails billing | Non envoyés | Envoyés |

**Règle :** Le flag doit être vérifiable dans `ActivateSubscriptionUseCase` avant tout appel Stripe.

---

## 2. Plans & Tarification

| Plan | Prix mensuel | Docs inclus | Prix overage | Usage |
|------|-------------|-------------|--------------|-------|
| **FREE** | 0€ | 50 docs/mois | Non disponible | Essai 30 jours |
| **Starter** | **49€/mois** | 500 docs/mois | **0.12€/doc** | PME, comptables |
| **Pro** | **199€/mois** | 5 000 docs/mois | **0.08€/doc** | ETI, cabinets |
| **Enterprise** | Sur devis | Illimité | Tarif négocié | Grands comptes |

**Règles plan FREE :**
- FREE disponible 30 jours (période d'essai)
- Après 30j sans plan payant → lecture seule (EXPIRED)
- Emails J-7, J-3, J-0 avant expiration
- Prolongation possible via SYSTEM (`POST /v1/admin/tenants/{id}/extend-trial`)

---

## 3. Domain Model

```java
// Plan — Enum domaine
public enum Plan {
    FREE, STARTER, PRO, ENTERPRISE;

    public int monthlyQuota() {
        return switch (this) {
            case FREE -> 50;
            case STARTER -> 500;
            case PRO -> 5_000;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    public double overagePrice() {
        return switch (this) {
            case FREE -> 0.0;
            case STARTER -> 0.12;
            case PRO -> 0.08;
            case ENTERPRISE -> 0.0;  // Tarif négocié
        };
    }
}

// SubscriptionStatus — Enum domaine
public enum SubscriptionStatus {
    TRIAL,       // Période d'essai (FREE 30 jours)
    ACTIVE,      // Abonnement payant actif
    PAST_DUE,    // Paiement en retard — lecture seule uniquement
    SUSPENDED,   // Suspendu manuellement (SYSTEM)
    CANCELED,    // Résilié — données conservées 90 jours (ADR-009)
    EXPIRED      // Période FREE expirée sans souscription
}

// Subscription — Aggregate
public class Subscription {
    private final SubscriptionId id;
    private final String tenantId;
    private Plan plan;
    private SubscriptionStatus status;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private Instant trialStartedAt;
    private Instant trialEndsAt;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private int documentsUsedCurrentMonth;
    private int overageDocuments;         // Docs facturés en supplément
    private boolean billingEnabled;       // Snapshot du flag au moment création

    // Invariants domaine
    // PAST_DUE → upload impossible
    // CANCELED → lectures seules 90 jours (ADR-009)
    // Downgrade → données conservées, nouveau quota début mois suivant
}
```

**Ports IN :**
- `PORT-IN-BIL-001` — `ActivateSubscriptionUseCase`
- `PORT-IN-BIL-002` — `ChangeSubscriptionPlanUseCase`
- `PORT-IN-BIL-003` — `HandleStripeWebhookUseCase`
- `PORT-IN-BIL-004` — `GetUsageUseCase`
- `PORT-IN-BIL-005` — `CancelSubscriptionUseCase`
- `PORT-IN-BIL-006` — `ExtendTrialUseCase`

**Ports OUT :**
- `PORT-OUT-BIL-001` — `SubscriptionRepositoryPort` (MongoDB)
- `PORT-OUT-BIL-002` — `PaymentGatewayPort` (Stripe)
- `PORT-OUT-BIL-003` — `QuotaPort` (commons-quota)
- `PORT-OUT-BIL-004` — `EmailNotificationPort` (Amazon SES)
- `PORT-OUT-BIL-005` — `FeatureFlagPort` (Unleash)

---

## 4. Cycle de vie abonnement

```
(Signup) → TRIAL (30 jours)
    │
    ├── TENANT_ADMIN souscrit → ACTIVE
    │       │
    │       ├── Stripe invoice.paid → reste ACTIVE
    │       ├── Stripe invoice.payment_failed → PAST_DUE (lecture seule)
    │       │       └── Régularisation → ACTIVE
    │       ├── TENANT_ADMIN résilie → CANCELED (données 90j — ADR-009)
    │       └── Upgrade/Downgrade → ACTIVE (nouveau plan)
    │
    └── Expiration sans souscription → EXPIRED (lecture seule)
            └── TENANT_ADMIN souscrit → ACTIVE
```

**ADR-009 — Downgrade obligatoire :**
1. NE JAMAIS supprimer les documents existants lors d'un downgrade
2. Nouveau quota appliqué au 1er du mois suivant
3. Overage du mois en cours facturé au tarif de l'ancien plan
4. Email d'impact envoyé au TENANT_ADMIN avant confirmation

---

## 5. Compteurs quota — Lua atomique (ADR-001)

```java
// ValkeyUsageCounterAdapter — Script Lua atomique OBLIGATOIRE
@Component
public class ValkeyUsageCounterAdapter implements QuotaPort {

    private static final String QUOTA_SCRIPT = """
        local current = redis.call('GET', KEYS[1])
        if current == false then current = 0 end
        if tonumber(current) >= tonumber(ARGV[1]) then
            return -1  -- Quota dépassé
        end
        redis.call('INCR', KEYS[1])
        local expiry = tonumber(ARGV[2])
        redis.call('EXPIREAT', KEYS[1], expiry)
        return tonumber(current) + 1
    """;

    @Override
    public QuotaResult checkAndConsume(String tenantId, int limit) {
        // Clé : quota:{tenantId}:{year}-{month}
        // TTL : expire le 1er du mois suivant (reset automatique)
        String key = "quota:" + tenantId + ":" + YearMonth.now();
        long firstNextMonth = LocalDate.now().plusMonths(1)
                                       .withDayOfMonth(1)
                                       .atStartOfDay(ZoneOffset.UTC)
                                       .toEpochSecond();

        Long result = valkey.execute(QUOTA_SCRIPT, List.of(key),
                                     String.valueOf(limit),
                                     String.valueOf(firstNextMonth));

        if (result == -1) {
            return QuotaResult.exceeded(); // → HTTP 429
        }
        int used = result.intValue();

        // Alertes automatiques
        double percent = (double) used / limit * 100;
        if (percent >= 95 && percent < 96) {
            notifyQuotaWarning(tenantId, 95, used, limit);
        } else if (percent >= 80 && percent < 81) {
            notifyQuotaWarning(tenantId, 80, used, limit);
        }

        return QuotaResult.allowed(used, limit);
    }
}
```

---

## 6. Stripe — Adapter

```java
// StripePaymentAdapter — implements PaymentGatewayPort
@Component
public class StripePaymentAdapter implements PaymentGatewayPort {

    @Override
    public String createCheckoutSession(String tenantId, Plan plan, String successUrl, String cancelUrl) {
        // Feature Flag OBLIGATOIRE
        if (!featureFlagPort.isEnabled("billing.enabled")) {
            throw new BillingDisabledException("Billing is not enabled");
        }

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(getPriceId(plan))  // Price ID Stripe selon le plan
                    .setQuantity(1L)
                    .build()
            )
            .putMetadata("tenantId", tenantId)
            .putMetadata("plan", plan.name())
            .build();

        Session session = Session.create(params);
        return session.getUrl(); // Rediriger le TENANT_ADMIN vers cette URL
    }

    @Override
    public void handleWebhook(String payload, String signature) {
        // Vérification signature OBLIGATOIRE
        Event event = Webhook.constructEvent(payload, signature, stripeWebhookSecret);

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.paid" -> handleInvoicePaid(event);          // → ACTIVE
            case "invoice.payment_failed" -> handlePaymentFailed(event); // → PAST_DUE
            case "customer.subscription.deleted" -> handleCanceled(event); // → CANCELED
            case "customer.subscription.updated" -> handleUpdated(event);  // Upgrade/downgrade
        }
    }
}
```

---

## 7. Endpoints

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/v1/billing/plans` | ❌ Public | Lister les plans disponibles |
| POST | `/v1/billing/checkout` | `TENANT_ADMIN` | Démarrer Stripe Checkout |
| GET | `/v1/billing/portal` | `TENANT_ADMIN` | Accéder au Customer Portal Stripe |
| GET | `/v1/billing/usage` | `TENANT_ADMIN` | Usage temps réel < 100ms |
| GET | `/v1/billing/subscription` | `TENANT_ADMIN` | Détail abonnement courant |
| POST | `/v1/billing/webhooks/stripe` | ❌ Public (signé) | Events Stripe |
| POST | `/v1/admin/tenants/{id}/extend-trial` | `SYSTEM` | Prolonger période FREE |

---

## 8. Emails billing

| Template | Déclencheur | Destinataire |
|----------|-------------|-------------|
| `trial-ending-7days.html` | J-7 avant fin FREE | TENANT_ADMIN |
| `trial-ending-3days.html` | J-3 avant fin FREE | TENANT_ADMIN |
| `trial-expired.html` | J-0 expiration FREE | TENANT_ADMIN |
| `subscription-activated.html` | Stripe checkout.completed | TENANT_ADMIN |
| `invoice.html` | 1er du mois | TENANT_ADMIN |
| `payment-failed.html` | Stripe invoice.payment_failed | TENANT_ADMIN |
| `quota-warning-80.html` | 80% quota atteint | TENANT_ADMIN |
| `quota-warning-95.html` | 95% quota atteint | TENANT_ADMIN |
| `subscription-canceled.html` | Résiliation | TENANT_ADMIN |

---

## 9. Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-BIL-001 | Si `billing.enabled = false`, aucune règle de quota ni facturation ne s'applique | MUST |
| BR-BIL-002 | Script Lua atomique pour les compteurs quota (ADR-001) | MUST |
| BR-BIL-003 | Signature webhook Stripe vérifiée sur 100% des events | MUST |
| BR-BIL-004 | Idempotence webhooks Stripe (même event 2× → traité 1× — Valkey dedup TTL 24h) | MUST |
| BR-BIL-005 | Downgrade : données conservées, nouveau quota début mois suivant (ADR-009) | MUST |
| BR-BIL-006 | PAST_DUE → upload bloqué, lecture seule autorisée | MUST |
| BR-BIL-007 | CANCELED → données conservées 90 jours (ADR-009) | MUST |
| BR-BIL-008 | Seul TENANT_ADMIN peut souscrire ou changer de plan | MUST |
| BR-BIL-009 | Alertes quota à 80% et 95% → email TENANT_ADMIN + notification in-app | MUST |
| BR-BIL-010 | Overage calculé automatiquement — docs supplémentaires facturés fin de mois | MUST |

---

## 10. NFR

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-BIL-001 | Signature webhook Stripe vérifiée en < 50ms | 100% |
| NFR-BIL-002 | Compteur quota mis à jour après chaque document traité | Temps réel |
| NFR-BIL-003 | Latence endpoint `GET /v1/billing/usage` | **< 100ms** |
| NFR-BIL-004 | Idempotence webhooks Stripe | 100% |

---

## 11. Definition of Done

- [ ] Feature Flag `billing.enabled = false` par défaut en DEV/STAGING
- [ ] Plans FREE/Starter/Pro/Enterprise listés sur `GET /v1/billing/plans`
- [ ] Stripe Checkout testé en mode TEST (paiement simulé → ACTIVE)
- [ ] Customer Portal Stripe accessible depuis le dashboard
- [ ] Webhook Stripe : signature vérifiée, idempotence testée (event 2× → traité 1×)
- [ ] Cycle de vie testé : TRIAL → ACTIVE → PAST_DUE → ACTIVE (régularisation)
- [ ] Cycle de vie testé : ACTIVE → CANCELED (données conservées 90j — ADR-009)
- [ ] Downgrade testé : données Pro encore accessibles après passage Starter (ADR-009)
- [ ] Alertes 80% et 95% déclenchées au bon seuil
- [ ] Atomicité Lua testée (1 000 requêtes simultanées → quota exactement respecté)
- [ ] Endpoint usage `GET /v1/billing/usage` < 100ms
- [ ] Emails billing testés via WireMock SES
- [ ] `billing.enabled = true` → Stripe actif, quotas enforced
- [ ] `billing.enabled = false` → aucune restriction, aucun appel Stripe
