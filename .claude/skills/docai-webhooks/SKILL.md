---
name: docai-webhooks
description: "Implémente le Module 6.2 DocAI (webhooks fiables avec signature HMAC-SHA256 X-DocAI-Signature, retry 5× backoff exponentiel 30s/1min/5min/15min/1h, Circuit Breaker Resilience4j, DLQ après 5 échecs + alerte dashboard, log de livraison MongoDB consultable, events DOCUMENT_COMPLETED / FRAUD_DETECTED / REVIEW_REQUIRED). Utiliser quand on demande d'implémenter les webhooks, la notification de systèmes externes, la signature HMAC, le retry sur livraison, l'historique des tentatives, ou le pattern WebhookDispatcher dans DocAI."
---

# Module 6.2 — Webhooks Fiables

> **Prérequis :** Module 6.1 (API Keys) terminé.  
> **Durée estimée :** 1 semaine

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/integration/webhook/
├── Webhook.java              // Aggregate (id, tenantId, url, events[], secretHash, active, createdAt)
├── WebhookDelivery.java      // Entity (webhookId, eventType, payload, attemptCount, status, lastAttemptAt)
├── WebhookEvent.java         // Enum (DOCUMENT_COMPLETED, FRAUD_DETECTED, REVIEW_REQUIRED)
└── WebhookDeliveryStatus.java // Enum (PENDING, SUCCESS, FAILED, DEAD_LETTER)
```

### Ports
```
Inbound:
  PORT-IN-WHK-001 → RegisterWebhookUseCase
  PORT-IN-WHK-002 → DeleteWebhookUseCase
  PORT-IN-WHK-003 → ListWebhookDeliveriesUseCase
  PORT-IN-WHK-004 → TestWebhookUseCase               (envoi payload test)

Outbound:
  PORT-OUT-WHK-001 → WebhookDispatcherPort            (HTTP dispatch + retry)
  PORT-OUT-WHK-002 → WebhookRepositoryPort
  PORT-OUT-WHK-003 → WebhookDeliveryRepositoryPort    (audit log)
```

### Adapters
```
docai-adapter-in-rest/
└── WebhookController.java              // CRUD webhooks + historique livraisons

docai-adapter-in-kafka/
└── WebhookDeliveryKafkaConsumer.java   // consomme docai.doc.* → dispatch webhooks enregistrés

docai-adapter-out-http/
└── WebhookDispatcherAdapter.java       // RestClient + Resilience4j Retry + Circuit Breaker

docai-adapter-out-mongodb/
└── MongoWebhookAdapter.java            // collections webhooks + webhook_deliveries
```

---

## Signature HMAC-SHA256

Chaque livraison inclut le header `X-DocAI-Signature` pour permettre au client de vérifier l'authenticité.

```java
// WebhookDispatcherAdapter.java
private String computeHmacSignature(String payload, String secret) {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
    mac.init(keySpec);
    byte[] hash = mac.doFinal(payload.getBytes(UTF_8));
    return "sha256=" + Hex.encodeHexString(hash);
}

// Headers envoyés sur chaque requête webhook
headers.set("X-DocAI-Signature", computeHmacSignature(payload, webhook.secret()));
headers.set("X-DocAI-Event", eventType.name());
headers.set("X-DocAI-Delivery", deliveryId);
headers.set("Content-Type", "application/json");
```

---

## Retry Policy — Backoff Exponentiel

| Tentative | Délai avant retry |
|-----------|-----------------|
| 1 | immédiate |
| 2 | 30 secondes |
| 3 | 1 minute |
| 4 | 5 minutes |
| 5 | 15 minutes |
| DLQ | après 1h (5ème échec) |

```java
// Configuration Resilience4j
RetryConfig retryConfig = RetryConfig.custom()
    .maxAttempts(5)
    .intervalFunction(IntervalFunction.ofExponentialBackoff(
        Duration.ofSeconds(30), 2.0))
    .retryOnException(e -> e instanceof WebhookDeliveryException)
    .build();
```

**Après 5 échecs :**
1. `WebhookDelivery` passe en statut `DEAD_LETTER`
2. Publication sur le topic DLQ `docai.webhook.dlq`
3. Alerte dashboard (SSE) envoyée au tenant
4. Email notification (Amazon SES) envoyé au TENANT_ADMIN

---

## WebhookDeliveryKafkaConsumer

```java
@Component
public class WebhookDeliveryKafkaConsumer extends ResilientKafkaConsumer<Object> {

    @KafkaListener(topics = {
        "docai.doc.completed",
        "docai.doc.fraud.analyzed",
        "docai.doc.review.required"
    }, groupId = "docai.integration.webhook.group")
    public void onDocumentEvent(DocAIEvent event) {
        // 1. Trouver tous les webhooks du tenant abonnés à ce type d'event
        // 2. Pour chaque webhook : créer une WebhookDelivery
        // 3. Dispatcher via WebhookDispatcherPort (avec retry Resilience4j)
        // 4. Logger le résultat dans webhook_deliveries
    }
}
```

---

## Endpoints

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| POST | `/v1/webhooks` | TENANT_ADMIN | Enregistrer un webhook (URL + events) |
| DELETE | `/v1/webhooks/{id}` | TENANT_ADMIN | Supprimer un webhook |
| GET | `/v1/webhooks` | TENANT_ADMIN | Lister les webhooks du tenant |
| GET | `/v1/webhooks/{id}/deliveries` | TENANT_ADMIN | Historique des livraisons (toutes tentatives) |
| POST | `/v1/webhooks/{id}/test` | TENANT_ADMIN | Envoyer un payload test |

---

## Schema WebhookDelivery (MongoDB)

```json
{
  "_id": "del_123",
  "webhookId": "whk_456",
  "tenantId": "acme-corp",
  "eventType": "DOCUMENT_COMPLETED",
  "payload": "{ ... }",
  "attemptCount": 3,
  "status": "SUCCESS",
  "attempts": [
    { "attemptAt": "...", "responseCode": 500, "durationMs": 120 },
    { "attemptAt": "...", "responseCode": 500, "durationMs": 98 },
    { "attemptAt": "...", "responseCode": 200, "durationMs": 85 }
  ],
  "firstAttemptAt": "...",
  "lastAttemptAt": "...",
  "deliveredAt": "..."
}
```

---

## Business Rules

| ID | Règle |
|----|-------|
| BR-INT-010 | Chaque webhook signé HMAC-SHA256 (`X-DocAI-Signature: sha256=...`) |
| BR-INT-011 | Retry 5× avec backoff exponentiel (30s, 1min, 5min, 15min, 1h) |
| BR-INT-012 | Non délivré après 5 tentatives → alerte dashboard + DLQ |
| BR-INT-013 | Chaque livraison (tentative + résultat) auditée en MongoDB |
| BR-INT-014 | Le tenant consulte le log de livraison via API |
| BR-INT-015 | Events supportés : DOCUMENT_COMPLETED, FRAUD_DETECTED, REVIEW_REQUIRED |

---

## Tests Obligatoires

```java
@Test
void should_sign_webhook_with_hmac_sha256() {
    // Vérifier header X-DocAI-Signature côté client simulé (WireMock)
}

@Test
void should_retry_5_times_with_exponential_backoff() {
    // WireMock : 4× HTTP 500, 5× HTTP 200 → SUCCESS après 5 tentatives
}

@Test
void should_send_to_dlq_after_5_failures() {
    // WireMock : 5× HTTP 500 → status DEAD_LETTER + alerte dashboard
}

@Test
void should_log_all_delivery_attempts_in_mongodb() { }

@Test
void should_not_deliver_to_other_tenant_webhook() {
    // Isolation tenant stricte
}
```

---

## Commons à Utiliser

- `commons-kafka` → `ResilientKafkaConsumer` sur `WebhookDeliveryKafkaConsumer`
- `commons-multitenancy` → isolation tenant sur webhooks et livraisons
- `commons-audit` → `@Audited` sur création et suppression webhooks
- `docai-resilience` → Resilience4j Retry + Circuit Breaker sur `WebhookDispatcherAdapter`

---

## Definition of Done

- [ ] Signature HMAC validée côté client (test WireMock)
- [ ] Retry 5× testé avec backoff (endpoint cible simulé en erreur avec WireMock)
- [ ] Log de livraison accessible via `GET /v1/webhooks/{id}/deliveries`
- [ ] Circuit Breaker testé (ouverture après 5 échecs consécutifs)
- [ ] DLQ + alerte dashboard après 5 échecs totaux
- [ ] Isolation tenant : webhook A ne reçoit pas events du tenant B
- [ ] Test webhook (payload de test envoyé sans event réel)
- [ ] Couverture domaine ≥ 90%

---

## Logs Obligatoires

```
INFO  — Webhook créé : tenantId, webhookId, url=[MASKED], events
INFO  — Livraison webhook réussie : webhookId, deliveryId, eventType, attemptCount, durationMs
WARN  — Livraison webhook échouée (tentative N/5) : webhookId, deliveryId, responseCode
ERROR — Webhook en DLQ après 5 échecs : webhookId, deliveryId, lastError
INFO  — Webhook supprimé : tenantId, webhookId
```
> `url` loggué masqué pour éviter de leaker des URLs internes client.
