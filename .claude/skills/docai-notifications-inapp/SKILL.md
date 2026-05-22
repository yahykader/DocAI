---
name: docai-notifications-inapp
description: "Implémente le Module 5.3 DocAI (Centre de Notifications In-App : historique persistant collection MongoDB notifications TTL 90j, types FRAUD_ALERT/QUOTA_WARNING_80/QUOTA_WARNING_95/QUOTA_EXCEEDED/ PAYMENT_FAILED, NotificationKafkaConsumer, marquage lu/non-lu, GET /v1/notifications paginé, PUT /read, PUT /read-all, GET /unread-count, badge SSE temps réel, isolation tenant+userId, feature flag notifications.inapp.enabled). Utiliser quand on demande d'implémenter le centre de notifications, l'historique des alertes, le badge non lues, le marquage de notifications comme lues, ou la persistance des alertes fraude et quota pour les utilisateurs non connectés. Prérequis : Module 5.2 (SSE) terminé."
---

# Module 5.3 — Centre de Notifications In-App

> **Prérequis :** Module 5.2 (SSE temps réel) terminé.  
> **Durée estimée :** 3 jours  
> **Feature Flag :** `notifications.inapp.enabled` (défaut : true)

> **Différence clé avec SSE :** Le SSE pousse les alertes en temps réel mais **ne les persiste pas**.  
> Le Centre de Notifications conserve l'**historique complet** même si l'utilisateur était déconnecté.

---

## Architecture Hexagonale

### Domain Model
```
docai-domain/notification/
├── Notification.java           // Aggregate
├── NotificationType.java       // Enum (voir types ci-dessous)
└── events/                     // Pas de Domain Events propres — consomme les events existants
```

**Types de notifications :**

| Type | Déclencheur | Score/Seuil |
|------|-------------|-------------|
| `FRAUD_ALERT` | `docai.doc.fraud.analyzed` | fraudScore > 50 |
| `QUOTA_WARNING_80` | Event quota Valkey | usage ≥ 80% limite |
| `QUOTA_WARNING_95` | Event quota Valkey | usage ≥ 95% limite |
| `QUOTA_EXCEEDED` | Event quota Valkey | usage > limite (plan FREE) |
| `PAYMENT_FAILED` | Webhook Stripe `invoice.payment_failed` | — |

### Ports
```
Inbound:
  PORT-IN-NOT-001 → ListNotificationsUseCase
  PORT-IN-NOT-002 → MarkNotificationReadUseCase
  PORT-IN-NOT-003 → MarkAllNotificationsReadUseCase
  PORT-IN-NOT-004 → GetUnreadCountUseCase

Outbound:
  PORT-OUT-NOT-001 → NotificationRepositoryPort
  PORT-OUT-NOT-002 → SseNotificationPort          (push badge unread-count via SSE existant)
```

### Adapters
```
docai-adapter-in-kafka/
└── NotificationKafkaConsumer.java    // Consomme docai.doc.fraud.analyzed + events quota

docai-adapter-in-rest/
└── NotificationController.java       // 4 endpoints REST

docai-adapter-out-mongodb/
└── NotificationMongoAdapter.java     // Collection notifications (Mongock V019)
```

---

## Collection MongoDB — `notifications`

| Champ | Type | Description |
|-------|------|-------------|
| `_id` | UUID | Identifiant notification |
| `tenantId` | String | Isolation tenant |
| `userId` | String | Destinataire (null = tous utilisateurs du tenant) |
| `type` | String | FRAUD_ALERT, QUOTA_WARNING_80, etc. |
| `title` | String | Titre court |
| `message` | String | Message détaillé |
| `resourceId` | String | ID du document ou ressource concernée |
| `read` | Boolean | Lu ou non lu (défaut : false) |
| `readAt` | DateTime | Date de lecture (null si non lue) |
| `createdAt` | DateTime | Index TTL — expire après **90 jours** |

---

## NotificationKafkaConsumer

```java
@Component
public class NotificationKafkaConsumer extends ResilientKafkaConsumer<Object> {

    @KafkaListener(
        topics = {"docai.doc.fraud.analyzed", "docai.quota.threshold.reached"},
        groupId = "docai.notification.inapp.group"
    )
    public void onEvent(DocAIEvent event) {
        if (!featureFlagPort.isEnabled("notifications.inapp.enabled")) return;

        Notification notification = switch (event.type()) {
            case "FraudAnalyzed" -> {
                FraudAnalyzedEvent e = (FraudAnalyzedEvent) event;
                if (e.fraudScore() <= 50) yield null;  // Seuil : > 50
                yield Notification.fraudAlert(e.tenantId(), e.documentId(), e.fraudScore());
            }
            case "QuotaThresholdReached" -> {
                QuotaThresholdEvent e = (QuotaThresholdEvent) event;
                yield Notification.quotaWarning(e.tenantId(), e.threshold(), e.currentUsage(), e.limit());
            }
            default -> null;
        };

        if (notification != null) {
            notificationRepository.save(notification);
            // Push badge SSE (unread-count) en temps réel
            long unreadCount = notificationRepository.countUnread(notification.tenantId(), notification.userId());
            sseNotificationPort.pushUnreadCount(notification.tenantId(), unreadCount);
        }
    }
}
```

---

## Endpoints REST

| Méthode | Endpoint | Rôle | Description |
|---------|----------|------|-------------|
| GET | `/v1/notifications` | Tous rôles | Liste paginée (filtres: type, read, dateFrom) |
| PUT | `/v1/notifications/{id}/read` | Tous rôles | Marquer une notification lue |
| PUT | `/v1/notifications/read-all` | Tous rôles | Marquer toutes les notifications lues |
| GET | `/v1/notifications/unread-count` | Tous rôles | Nombre de notifications non lues |

**Isolation :** Chaque utilisateur voit **uniquement ses propres notifications** (filtre `tenantId` + `userId`).  
Exception : `TENANT_ADMIN` peut voir les notifications de toute son équipe (filtre `tenantId` uniquement).

**Réponse GET /v1/notifications :**
```json
{
  "data": [
    {
      "id": "notif-uuid",
      "type": "FRAUD_ALERT",
      "title": "Alerte fraude détectée",
      "message": "Document facture_oct.pdf — Score: 87/100",
      "resourceId": "doc-uuid",
      "read": false,
      "createdAt": "2026-05-21T10:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 5, "totalPages": 1 }
}
```

---

## Badge SSE — Compteur Non Lues Temps Réel

À chaque changement (nouvelle notification ou marquage lu), envoyer un event SSE de type `unread-count` sur le stream existant `GET /v1/dashboard/stream`.

```java
// Format event SSE
id: {uuid}
event: unread-count
data: {"count": 3}
```

**Cas d'envoi :**
1. À la connexion SSE → envoyer le count actuel immédiatement
2. Nouvelle notification créée → push `unread-count` mis à jour
3. Marquage lu → push `unread-count` décrémenté
4. Marquage read-all → push `unread-count: 0`

---

## Migration Mongock — V019

```java
@ChangeUnit(id = "V019_setup_notifications_collection", order = "019")
public class V019SetupNotificationsCollection {

    @Execution
    public void execute(MongoDatabase db) {
        db.createCollection("notifications");

        // Index de listing : tenant + user + non lues + date
        createIndex(db, "notifications",
            Indexes.compoundIndex(
                Indexes.ascending("tenantId"),
                Indexes.ascending("userId"),
                Indexes.ascending("read"),
                Indexes.descending("createdAt")));

        // Index TTL — expiration automatique après 90 jours
        createIndex(db, "notifications",
            Indexes.ascending("createdAt"),
            new IndexOptions().expireAfter(90L, TimeUnit.DAYS));
    }
}
```

---

## Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-NOT-001 | Chaque alerte fraude (score > 50) génère une notification persistante | MUST |
| BR-NOT-002 | Alertes quota (80%, 95%, dépassement) génèrent des notifications | MUST |
| BR-NOT-003 | Notification marquable lue / non lue | MUST |
| BR-NOT-004 | Compteur non lues en temps réel via SSE (< 1s) | MUST |
| BR-NOT-005 | Isolation stricte tenant + userId | MUST |
| BR-NOT-006 | Notifications conservées 90 jours (TTL MongoDB) | SHOULD |
| BR-NOT-007 | TENANT_ADMIN peut voir les notifications de toute son équipe | SHOULD |
| BR-NOT-008 | Feature Flag `notifications.inapp.enabled` protège le module | MUST |

---

## Scénarios BDD Obligatoires

```gherkin
Feature: Centre de Notifications In-App

  Scenario: Notification FRAUD_ALERT créée automatiquement
    Given un event FraudAnalyzed avec fraudScore=87 pour le document "doc-123"
    When le NotificationKafkaConsumer traite l'event
    Then une notification FRAUD_ALERT est persistée en MongoDB pour le tenant concerné
    And le badge unread-count est mis à jour via SSE en < 1s

  Scenario: Fraude sous le seuil — pas de notification
    Given un event FraudAnalyzed avec fraudScore=50
    When le consumer traite l'event
    Then aucune notification n'est créée (seuil > 50, pas ≥)

  Scenario: Marquage lu — badge mis à jour
    Given 3 notifications non lues pour l'utilisateur "alice"
    When alice marque une notification comme lue
    Then le badge SSE push "unread-count: 2"
    And la notification a read=true et readAt=now()

  Scenario: Marquage read-all
    Given 5 notifications non lues
    When alice exécute PUT /v1/notifications/read-all
    Then toutes les notifications ont read=true
    And le badge SSE push "unread-count: 0"

  Scenario: Isolation tenant + userId
    Given une notification pour l'utilisateur "alice" du tenant "acme-corp"
    When "bob" du même tenant consulte ses notifications
    Then "bob" ne voit pas les notifications d'alice
    When un utilisateur de "other-corp" consulte ses notifications
    Then il ne voit aucune notification de "acme-corp"

  Scenario: TTL 90 jours
    Given une notification créée il y a 91 jours
    When le job TTL MongoDB s'exécute
    Then la notification est automatiquement supprimée
```

---

## Commons à Utiliser

- `commons-kafka` → `ResilientKafkaConsumer` sur `NotificationKafkaConsumer`
- `commons-multitenancy` → isolation tenant + userId sur toutes les requêtes
- `commons-api` → `ApiResponse<T>` pour la liste paginée (BR-PAG-001)
- `docai-feature-flag` → `notifications.inapp.enabled` kill switch

---

## Definition of Done

- [ ] Notification FRAUD_ALERT créée automatiquement (score > 50, pas score = 50)
- [ ] Notification QUOTA_WARNING créée aux seuils 80% et 95% (une seule fois par période)
- [ ] Marquage lu/non lu fonctionnel (read=true + readAt mis à jour)
- [ ] Badge SSE unread-count mis à jour en < 1s après changement
- [ ] Isolation tenant + userId testée (alice ne voit pas notifications de bob)
- [ ] TTL 90 jours configuré et vérifié (index MongoDB correctement créé)
- [ ] Feature Flag `notifications.inapp.enabled` testé (flag off → aucune notification créée)
- [ ] Migration Mongock V019 appliquée et idempotente
- [ ] EXPLAIN PLAN sur requête listing validé (pas de COLLSCAN)
- [ ] Coverage module ≥ 80%

---

## Logs Obligatoires

```
INFO  — Notification créée : notificationId, tenantId, userId=[PII_MASKED], type, resourceId
INFO  — Notification marquée lue : notificationId, tenantId, userId=[PII_MASKED]
INFO  — Read-all : tenantId, userId=[PII_MASKED], count
INFO  — Badge SSE poussé : tenantId, unreadCount
WARN  — Feature flag désactivé : notifications.inapp.enabled=false, event ignoré
```
> Jamais de PII dans les logs → `[PII_MASKED]`. Toujours `traceId` + `tenantId`.
