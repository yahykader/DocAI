---
name: docai-sse-realtime
description: "Implémente le Module 5.2 DocAI (alertes fraude temps réel via Server-Sent Events, SseNotificationAdapter Spring SseEmitter, AlertKafkaConsumer, isolation tenant SSE, reconnexion Last-Event-ID, keepalive 30s, cleanup emitters morts, limite 50 connexions par tenant). Utiliser quand on demande d'implémenter les alertes temps réel, le SSE, le flux d'événements dashboard, la connexion persistante push, ou GET /v1/dashboard/stream. Prérequis : Module 5.1 (Read Model CQRS) terminé."
---

# Module 5.2 — Alertes Temps Réel (SSE)

> **Prérequis :** Module 5.1 (Read Model CQRS) terminé.  
> **Durée estimée :** 1 semaine  
> **SLA :** Alerte fraude reçue par le client SSE en < 2s après publication Kafka.

---

## Architecture Hexagonale

### Domain
```
docai-domain/dashboard/sse/
├── FraudAlert.java          // record immuable (alertId, documentId, tenantId, fraudScore, riskLevel, occurredAt)
└── SseEvent.java            // Value Object (id, type, data, retry?)
```

Règle domaine : **score > 50 → alerte SSE envoyée**.  
Règle isolation : client SSE reçoit **uniquement** les alertes de son `tenantId` (extrait JWT).

### Ports
```
Inbound:
  PORT-IN-SSE-001 → SubscribeFraudAlertsUseCase

Outbound:
  PORT-OUT-SSE-001 → SseNotificationPort
```

### Adapters
```
docai-adapter-in-rest/
└── SseController.java           // GET /v1/dashboard/stream

docai-adapter-in-kafka/
└── AlertKafkaConsumer.java      // consomme docai.doc.fraud.analyzed → push SSE si score > 50

docai-adapter-out-sse/
└── SseNotificationAdapter.java  // Spring SseEmitter, ConcurrentHashMap tenant → emitters
```

---

## Endpoint

```
GET /v1/dashboard/stream
Headers:
  Authorization: Bearer {JWT}       → isolation tenant automatique (TenantJwtFilter)
  Last-Event-ID: {eventId}          → reprise depuis le dernier event reçu
Response:
  Content-Type: text/event-stream
  Cache-Control: no-cache
  Connection: keep-alive
```

---

## SseNotificationAdapter

```java
@Component
public class SseNotificationAdapter implements SseNotificationPort {

    // Thread-safe : ConcurrentHashMap + CopyOnWriteArrayList
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByTenant
        = new ConcurrentHashMap<>();

    private static final int MAX_CONNECTIONS_PER_TENANT = 50;
    private static final long SSE_TIMEOUT_MS = 60_000L;

    public SseEmitter subscribe(String tenantId) {
        var tenantEmitters = emittersByTenant
            .computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());

        // Limite par tenant
        if (tenantEmitters.size() >= MAX_CONNECTIONS_PER_TENANT) {
            throw new SseCapacityExceededException(tenantId);
        }

        var emitter = new SseEmitter(SSE_TIMEOUT_MS);
        tenantEmitters.add(emitter);

        // Cleanup automatique
        emitter.onCompletion(() -> tenantEmitters.remove(emitter));
        emitter.onTimeout(() -> tenantEmitters.remove(emitter));
        emitter.onError(e -> tenantEmitters.remove(emitter));

        return emitter;
    }

    public void pushAlert(FraudAlert alert) {
        var emitters = emittersByTenant.getOrDefault(alert.tenantId(), List.of());
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .id(alert.alertId())
                    .name("FRAUD_ALERT")
                    .data(alert));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        });
    }
}
```

---

## AlertKafkaConsumer

```java
@Component
public class AlertKafkaConsumer extends ResilientKafkaConsumer<FraudAnalyzedEvent> {

    @KafkaListener(topics = "docai.doc.fraud.analyzed",
                   groupId = "docai.notification.sse.group")  // groupId correct V15.0
    public void onFraudAnalyzed(FraudAnalyzedEvent event) {
        // Règle domaine : score > 50 uniquement
        if (event.fraudScore() > 50) {
            var alert = FraudAlert.from(event);
            sseNotificationPort.pushAlert(alert);
        }
    }
}
```

---

## Keepalive (heartbeat)

Envoyer un event `heartbeat` toutes les **30 secondes** pour maintenir les connexions et détecter les clients déconnectés silencieusement.

```java
@Scheduled(fixedDelay = 30_000)
public void sendKeepalive() {
    emittersByTenant.forEach((tenantId, emitters) ->
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        })
    );
}
```

---

## Reconnexion Last-Event-ID

- Stocker les derniers N events en mémoire par tenant (ring buffer, max 100 events, TTL 5 min Valkey)
- À la reconnexion avec `Last-Event-ID` → rejouer les events manqués
- Sans `Last-Event-ID` → flux depuis maintenant uniquement

```java
// SseController.java
@GetMapping(value = "/v1/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
        @AuthenticationPrincipal JwtAuthenticationToken auth) {

    String tenantId = TenantContext.get();  // jamais TenantContext.getTenantId()
    SseEmitter emitter = sseNotificationAdapter.subscribe(tenantId);

    // Replay events manqués si Last-Event-ID fourni
    if (lastEventId != null) {
        sseEventStore.getEventsSince(tenantId, lastEventId)
            .forEach(event -> sendEvent(emitter, event));
    }

    return emitter;
}
```

---

## Business Rules

| ID | Règle |
|----|-------|
| BR-DSH-010 | Les alertes fraude (score > 50) envoyées en SSE < 2s après publication Kafka |
| BR-DSH-011 | Un client SSE ne reçoit que les alertes de son `tenant_id` |
| BR-DSH-012 | La connexion SSE est ré-établie automatiquement (Last-Event-ID) |
| BR-DSH-013 | L'endpoint SSE est protégé par JWT |
| BR-DSH-014 | Maximum 50 connexions SSE simultanées par tenant (au-delà → HTTP 503) |
| BR-DSH-015 | Keepalive toutes les 30s (event type `heartbeat`) |

---

## Métriques Micrometer

```java
// Exposer via Prometheus
Counter.builder("docai.sse.alerts.pushed")
    .tag("tenantId", tenantId)
    .register(meterRegistry);

Gauge.builder("docai.sse.connections.active", emittersByTenant,
    map -> map.values().stream().mapToInt(List::size).sum())
    .register(meterRegistry);
```

---

## Tests Obligatoires

```java
@Test
void should_push_alert_when_fraud_score_above_50() { }

@Test
void should_not_push_alert_when_fraud_score_equals_50() { }

@Test
void should_isolate_sse_by_tenant() {
    // tenantA ne reçoit pas les alertes de tenantB
}

@Test
void should_cleanup_dead_emitters_on_timeout() { }

@Test
void should_return_503_when_tenant_exceeds_50_connections() { }

@Test
void should_replay_missed_events_with_last_event_id() { }
```

---

## Commons à Utiliser

- `commons-kafka` → `ResilientKafkaConsumer` sur `AlertKafkaConsumer`
- `commons-multitenancy` → `TenantContext.get()` dans `SseController`
- `commons-api` → `ProblemDetail` pour HTTP 503 capacité dépassée

---

## Definition of Done

- [ ] SSE testé : event Kafka → alerte reçue par client SSE < 2s
- [ ] Isolation tenant validée (client A ne reçoit pas alertes client B)
- [ ] Reconnexion automatique testée avec `Last-Event-ID`
- [ ] Cleanup automatique des SseEmitters morts (keepalive + onTimeout)
- [ ] Limite 50 connexions par tenant → HTTP 503 testé
- [ ] `FraudAlert` est un record Java immuable
- [ ] Règle score ≤ 50 → pas d'alerte testée (score 50 = pas d'alerte, score 51 = alerte)
- [ ] Métriques `docai.sse.connections.active` exposées Prometheus

---

## Logs Obligatoires

```
INFO  — Client SSE connecté : tenantId, connectionCount
INFO  — Alerte SSE poussée : alertId, tenantId, fraudScore, durationMs
WARN  — Emitter SSE mort nettoyé : tenantId
WARN  — Limite SSE atteinte : tenantId, currentCount=50
ERROR — Échec push SSE : alertId, tenantId, raison
```
> Toujours inclure `traceId` + `tenantId`. Jamais de PII dans les logs.
