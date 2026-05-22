## Phase 3.3 — Révision Humaine & SSE

### State Machine

```
FraudAnalysis (score 51–75)  →  PENDING_REVIEW
                                      ↓ FRAUD_REVIEWER ouvre le dossier
                                  REVIEWING  (1 reviewer à la fois)
                                      ↓
                     ┌────────────┬──────────────┐
                 APPROVED      REJECTED       ESCALATED
                    (pipeline continue) (rejeté) (superviseur)

Score 76–100 → REJECTED directement (sans passage REVIEWING)
Score 0–50   → Pas de révision
```

### ReviewDecision — Value Object IMMUABLE

```java
// Dans fr.docai.domain.model/ — Java pur, zéro framework
public record ReviewDecision(
    String fraudAnalysisId,
    String tenantId,
    String reviewerId,
    ReviewOutcome outcome,   // APPROVED | REJECTED | ESCALATED
    String comment,          // Obligatoire si REJECTED ou ESCALATED
    Instant decidedAt        // Immuable après création
) {
    public ReviewDecision {
        Objects.requireNonNull(fraudAnalysisId);
        Objects.requireNonNull(outcome);
        if ((outcome == ReviewOutcome.REJECTED || outcome == ReviewOutcome.ESCALATED)
                && (comment == null || comment.isBlank())) {
            throw new ReviewCommentRequiredException(
                "Comment required for REJECTED/ESCALATED");
        }
    }
}

public enum ReviewOutcome { APPROVED, REJECTED, ESCALATED }
public enum ReviewStatus  { PENDING_REVIEW, REVIEWING, APPROVED, REJECTED, ESCALATED }
```

### Endpoints REST

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| GET | `/v1/fraud/review-queue` | `FRAUD_REVIEWER` | Queue paginée, triée score DESC |
| GET | `/v1/fraud/{id}` | `FRAUD_REVIEWER` | Détail analyse + signaux |
| POST | `/v1/fraud/{id}/start-review` | `FRAUD_REVIEWER` | Lock optimiste → REVIEWING |
| PUT | `/v1/fraud/{id}/decision` | `FRAUD_REVIEWER` | Soumettre APPROVED/REJECTED/ESCALATED |
| GET | `/v1/dashboard/stream` | JWT | SSE alertes temps réel |

### SSE Alertes fraude (score > 50)

```java
// AlertKafkaConsumer — groupe docai.notification.sse.group
// Consomme docai.doc.fraud.analyzed
// Si fraudScore > 50 → push SSE au tenant concerné UNIQUEMENT (isolation tenant)

@Component
public class AlertKafkaConsumer extends ResilientKafkaConsumer<FraudAnalyzed> {

    @KafkaListener(topics = "docai.doc.fraud.analyzed",
                   groupId = "docai.notification.sse.group")
    public void consume(ConsumerRecord<String, FraudAnalyzed> record, Acknowledgment ack) {
        processWithIdempotence(record, () -> {
            FraudAnalyzed event = record.value();
            if (event.getFraudScore() > 50) {           // BR-FRD-015
                sseNotificationPort.push(               // BR-FRD-016 : tenant isolé
                    event.getTenantId(),
                    FraudAlert.of(event.getDocumentId(), event.getFraudScore())
                );
            }
        });
        ack.acknowledge();
    }
}

// SseNotificationAdapter — ConcurrentHashMap tenantId → emitters
// Limite : 50 connexions max par tenant
// Keepalive 30s — cleanup emitters morts automatique
// SseEmitter timeout = 60s
```

**Business Rules Phase 3.3 :**

| ID | Règle |
|----|-------|
| BR-FRD-010 | Score 51–75 → queue révision obligatoire |
| BR-FRD-011 | Score 76–100 → REJECTED immédiat + alerte SSE |
| BR-FRD-012 | Seul `FRAUD_REVIEWER` peut statuer |
| BR-FRD-013 | `ReviewDecision` immuable après création |
| BR-FRD-014 | Chaque décision génère un `AuditEntry` immuable |
| BR-FRD-015 | Score > 50 → alerte SSE en **< 2 secondes** |
| BR-FRD-016 | Isolation SSE : un tenant ne reçoit que ses alertes |

### Scénarios BDD 3.3

```gherkin
Scenario: Décision APPROVED — document légitime
  Given carol est FRAUD_REVIEWER du tenant "acme-corp"
  And le document "doc-xyz" a un score de 65 (PENDING_REVIEW)
  When carol soumet la décision APPROVED
  Then le statut passe à APPROVED
  And un AuditEntry immuable est créé
  And un event DocumentCompleted est publié

Scenario: Alerte SSE — score > 50
  Given alice est connectée en SSE sur /v1/dashboard/stream (tenant acme-corp)
  When un document obtient un score fraude de 75
  Then alice reçoit l'alerte en < 2 secondes
  And dave (tenant beta-assur) ne reçoit PAS cette alerte (isolation)

Scenario: Décision immuable — tentative modification
  Given une ReviewDecision REJECTED existe pour "doc-abc"
  When un reviewer tente de changer la décision à APPROVED
  Then HTTP 409 Conflict
  And la ReviewDecision originale est inchangée
```

---

