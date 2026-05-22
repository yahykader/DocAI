---
name: docai-module3-fraude-revision
description: "Implémente le Module 3.3 DocAI (Workflow Révision Humaine fraude : state machine PENDING_REVIEW→REVIEWING→APPROVED/REJECTED/ESCALATED, queue révision FRAUD_REVIEWER, ReviewDecision Value Object immuable, endpoints PUT /v1/fraud/{id}/decision, GET /v1/fraud/review-queue paginé, SSE alertes fraude ( 2s score ) 50 via SseNotificationAdapter, isolation tenant SSE, AuditEntry sur chaque décision). Utiliser quand on demande d'implémenter la révision humaine de fraude, la queue de révision, la state machine de révision, les décisions APPROVED/REJECTED/ESCALATED, ou les alertes SSE fraude. Prérequis : Module 3.1 et 3.2 terminés. Module 5.2 SSE terminé."
---

# DocAI — Module 3.3 Workflow Révision Humaine Fraude
## State Machine · Queue · Décisions immuables · SSE alertes

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 5 (Module 3, Phase 3.3)
> **Prérequis :** Modules 3.1 + 3.2 terminés. SSE adapter disponible (docai-sse-realtime skill).
> **Durée estimée :** 2 semaines

---

## 1. Business Rules

| ID | Règle | Priorité |
|----|-------|---------|
| BR-FRD-010 | Documents avec score 51–75 → queue révision obligatoire | MUST |
| BR-FRD-011 | Documents avec score 76–100 → rejetés immédiatement + alerte SSE | MUST |
| BR-FRD-012 | Seul `FRAUD_REVIEWER` peut statuer sur les documents suspects | MUST |
| BR-FRD-013 | La décision de révision est un **Value Object immuable** | MUST |
| BR-FRD-014 | Chaque décision génère un `AuditEntry` immuable | MUST |
| BR-FRD-015 | Score > 50 → alerte SSE envoyée en **< 2 secondes** | MUST |
| BR-FRD-016 | Un tenant ne reçoit que ses propres alertes SSE | MUST |
| BR-FRD-017 | Escalade = passage à un superviseur avec commentaire obligatoire | SHOULD |

---

## 2. State Machine révision

```
FraudAnalysis créée (score 51–75)
        ↓
   PENDING_REVIEW ←── Assignée à la queue FRAUD_REVIEWER
        ↓ (FRAUD_REVIEWER ouvre le dossier)
    REVIEWING    ←── Un seul reviewer à la fois (lock optimiste)
        ↓
   ┌──────────┬──────────┬──────────┐
   ↓          ↓          ↓
APPROVED   REJECTED   ESCALATED  ←── Décision immuable après soumission
                           ↓
                    Superviseur reprend depuis PENDING_REVIEW

Score 76–100 → REJECTED directement (sans REVIEWING)
Score 0–50   → Pas de révision humaine (pipeline continue)
```

---

## 3. Domain Model

```java
// ReviewDecision — Value Object IMMUABLE
public record ReviewDecision(
    String fraudAnalysisId,
    String tenantId,
    String reviewerId,         // userId du FRAUD_REVIEWER
    ReviewOutcome outcome,     // APPROVED, REJECTED, ESCALATED
    String comment,            // Obligatoire pour REJECTED et ESCALATED
    Instant decidedAt          // Immuable — jamais modifiable
) {
    // Validation constructeur
    public ReviewDecision {
        Objects.requireNonNull(fraudAnalysisId);
        Objects.requireNonNull(outcome);
        if ((outcome == ReviewOutcome.REJECTED || outcome == ReviewOutcome.ESCALATED)
                && (comment == null || comment.isBlank())) {
            throw new ReviewCommentRequiredException("Comment required for REJECTED/ESCALATED");
        }
    }
}

// ReviewOutcome — Enum
public enum ReviewOutcome {
    APPROVED,    // Document légitime → pipeline continue
    REJECTED,    // Document frauduleux → rejeté définitivement
    ESCALATED    // Cas complexe → superviseur
}

// ReviewStatus — Enum pour l'état courant de la FraudAnalysis
public enum ReviewStatus {
    PENDING_REVIEW,  // En attente d'un reviewer
    REVIEWING,       // En cours de révision (lockée par un reviewer)
    APPROVED,        // Décision finale
    REJECTED,        // Décision finale
    ESCALATED        // Transmis au superviseur
}
```

---

## 4. Use Cases

```java
// SubmitReviewDecisionUseCase
@UseCase
public class SubmitReviewDecisionUseCaseImpl implements SubmitReviewDecisionUseCase {

    @Override
    @Audited(action = "FRAUD_REVIEW_DECISION", resourceType = "FraudAnalysis")
    @PreAuthorize("hasRole('FRAUD_REVIEWER')")
    public void execute(SubmitReviewDecisionCommand command) {
        String tenantId = TenantContext.get();
        String reviewerId = SecurityContextHolder.getContext()
                                                 .getAuthentication().getName();

        // Vérifier que la FraudAnalysis appartient au tenant
        FraudAnalysis analysis = fraudAnalysisRepository
            .findById(command.fraudAnalysisId(), tenantId)
            .orElseThrow(() -> new FraudAnalysisNotFoundException(command.fraudAnalysisId()));

        // Vérifier l'état (doit être PENDING_REVIEW ou REVIEWING)
        if (!analysis.isReviewable()) {
            throw new InvalidReviewStateException("Analysis is not in a reviewable state");
        }

        // Créer la décision immuable
        ReviewDecision decision = new ReviewDecision(
            command.fraudAnalysisId(),
            tenantId,
            reviewerId,
            command.outcome(),
            command.comment(),
            Instant.now()
        );

        // Appliquer la décision sur l'aggregate
        analysis.applyDecision(decision);
        fraudAnalysisRepository.save(analysis);

        // Publier l'event domain
        String topic = command.outcome() == ReviewOutcome.APPROVED
            ? "docai.doc.completed" : "docai.doc.failed";
        eventPublisher.publish(topic, analysis.getDocumentId(),
                               new ReviewDecisionSubmitted(decision), tenantId);

        log.info("Review decision submitted fraudAnalysisId={} outcome={} reviewer=[PII_MASKED]",
                 command.fraudAnalysisId(), command.outcome());
    }
}

// GetReviewQueueUseCase
@UseCase
public class GetReviewQueueUseCaseImpl implements GetReviewQueueUseCase {

    @Override
    @PreAuthorize("hasRole('FRAUD_REVIEWER')")
    public Page<FraudAnalysisSummary> execute(Pageable pageable, ReviewQueueFilter filter) {
        String tenantId = TenantContext.get();

        // Queue triée par score DESC (plus urgents en premier)
        return fraudAnalysisRepository.findPendingReview(
            tenantId,
            filter.riskLevel(),
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                           Sort.by("score").descending())
        );
    }
}
```

---

## 5. SSE Alertes fraude (score > 50)

```java
// AlertKafkaConsumer — écoute les analyses fraude et push SSE
@Component
public class AlertKafkaConsumer extends ResilientKafkaConsumer<FraudAnalyzed> {

    @KafkaListener(
        topics = "docai.doc.fraud.analyzed",
        groupId = "docai.notification.sse.group"
    )
    public void consume(ConsumerRecord<String, FraudAnalyzed> record, Acknowledgment ack) {
        processWithIdempotence(record, () -> {
            FraudAnalyzed event = record.value();

            // Seuil : score > 50 → alerte SSE (BR-FRD-015)
            if (event.getFraudScore() > 50) {
                FraudAlert alert = FraudAlert.of(
                    event.getDocumentId(),
                    event.getTenantId(),
                    event.getFraudScore(),
                    RiskLevel.fromScore(event.getFraudScore())
                );

                // Push SSE au tenant concerné UNIQUEMENT (BR-FRD-016)
                sseNotificationPort.push(event.getTenantId(), alert);

                log.info("Fraud alert SSE pushed tenantId={} score={} documentId={}",
                    event.getTenantId(), event.getFraudScore(), event.getDocumentId());
            }
        });
        ack.acknowledge();
    }
}

// SseNotificationAdapter — push aux emitters du bon tenant uniquement
@Component
public class SseNotificationAdapter implements SseNotificationPort {

    // Map thread-safe : tenantId → liste des connexions SSE actives
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters
        = new ConcurrentHashMap<>();

    @Override
    public void push(String tenantId, FraudAlert alert) {
        List<SseEmitter> tenantEmitters = emitters.get(tenantId);
        if (tenantEmitters == null || tenantEmitters.isEmpty()) return;

        String payload = objectMapper.writeValueAsString(alert);
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : tenantEmitters) {
            try {
                emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name("fraud-alert")
                    .data(payload));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        // Nettoyer les emitters morts
        tenantEmitters.removeAll(deadEmitters);
    }

    // Endpoint SSE : GET /v1/dashboard/stream
    public SseEmitter connect(String tenantId) {
        // Limite 50 connexions par tenant
        List<SseEmitter> tenantEmitters = emitters.computeIfAbsent(
            tenantId, k -> new CopyOnWriteArrayList<>());

        if (tenantEmitters.size() >= 50) {
            throw new TooManySseConnectionsException("Max 50 SSE connections per tenant");
        }

        SseEmitter emitter = new SseEmitter(60_000L); // Timeout 60s
        tenantEmitters.add(emitter);

        // Keepalive 30s
        emitter.onCompletion(() -> tenantEmitters.remove(emitter));
        emitter.onTimeout(() -> tenantEmitters.remove(emitter));

        return emitter;
    }
}
```

---

## 6. Endpoints REST

```java
@RestController
@RequestMapping("/v1/fraud")
@PreAuthorize("hasRole('FRAUD_REVIEWER')")
public class FraudReviewController {

    // Queue de révision (paginée, triée score DESC)
    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<List<FraudAnalysisSummary>>> getReviewQueue(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) RiskLevel riskLevel
    ) { ... }

    // Détail d'une analyse fraude
    @GetMapping("/{id}")
    public ResponseEntity<FraudAnalysisDetail> getFraudAnalysis(@PathVariable String id) { ... }

    // Soumettre une décision
    @PutMapping("/{id}/decision")
    public ResponseEntity<Void> submitDecision(
        @PathVariable String id,
        @Valid @RequestBody ReviewDecisionRequest request
    ) { ... }

    // Assigner le document en cours de révision (lock optimiste)
    @PostMapping("/{id}/start-review")
    public ResponseEntity<Void> startReview(@PathVariable String id) { ... }
}

// SSE endpoint (pas de @PreAuthorize sur la méthode — JWT dans le header)
@GetMapping("/v1/dashboard/stream")
public SseEmitter stream(HttpServletRequest request) {
    String tenantId = TenantContext.get();  // Extrait du JWT
    return sseNotificationAdapter.connect(tenantId);
}
```

---

## 7. MongoDB — Collection `fraud_analyses`

```
{tenantId, reviewStatus, score}  — Index pour la queue (triée DESC)
{tenantId, riskLevel, createdAt} — Compound pour filtres
{documentId}                     — Unique (immuabilité)
```

---

## 8. BDD Scénarios

```gherkin
Scenario: Décision APPROVED sur document suspect
  Given carol est connectée avec le rôle FRAUD_REVIEWER du tenant "acme-corp"
  And le document "doc-xyz" a un score fraude de 65 (état PENDING_REVIEW)
  When carol soumet la décision APPROVED avec commentaire
  Then le statut du document passe à APPROVED
  And un AuditEntry immuable est créé avec userId, outcome, timestamp
  And le document est retiré de la queue de révision
  And un event "DocumentCompleted" est publié sur Kafka

Scenario: Alerte SSE fraude — score > 50
  Given alice est connectée en SSE sur /v1/dashboard/stream
  When un document du tenant "acme-corp" obtient un score fraude de 75
  Then alice reçoit l'alerte SSE "fraud-alert" en moins de 2 secondes
  And dave (tenant "beta-assur") ne reçoit pas cette alerte (isolation tenant)

Scenario: Décision immuable — tentative de modification
  Given une ReviewDecision REJECTED a été soumise pour "doc-abc"
  When un FRAUD_REVIEWER tente de modifier la décision à APPROVED
  Then la réponse est HTTP 409 (Conflict)
  And la ReviewDecision originale est inchangée
  And un AuditEntry enregistre la tentative
```

---

## 9. Definition of Done

- [ ] State machine validée : PENDING_REVIEW → REVIEWING → APPROVED/REJECTED/ESCALATED
- [ ] Transitions invalides → exception domaine (tentative hors état → HTTP 409)
- [ ] `ReviewDecision` immuable après création (tentative modification → exception)
- [ ] AuditEntry créé pour chaque décision (userId masqué, outcome, timestamp)
- [ ] Alerte SSE : event Kafka fraudScore > 50 → alerte reçue < 2s (BR-FRD-015)
- [ ] Isolation SSE : tenant A ne reçoit pas alertes tenant B (2 connexions simultanées testées)
- [ ] Keepalive SSE 30s fonctionnel
- [ ] Cleanup emitters morts automatique
- [ ] Limite 50 connexions SSE par tenant (51ème → HTTP 503)
- [ ] Queue paginée triée par score DESC
- [ ] Seul `FRAUD_REVIEWER` accède aux endpoints (ANALYST → HTTP 403)
- [ ] Comment obligatoire pour REJECTED et ESCALATED
