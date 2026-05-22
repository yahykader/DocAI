---
name: docai-usecase-implement
description: "Implémente un Use Case DocAI dans docai-application. Utiliser quand on demande de créer la logique applicative d'une fonctionnalité (orchestration, validation, appel aux ports). Le use case dépend uniquement du domaine, jamais des adapters ou de Spring directement."
---

# DocAI — Implémenter un Use Case

## Localisation

Module : `docai-application`
Package : `fr.docai.application.usecase`

## Règles

- Implémente l'interface UseCase du domaine (`fr.docai.domain.port.in`)
- Dépend uniquement de `fr.docai.domain` et `fr.docai.application`
- Injection par constructeur uniquement
- Annotés `@Component` ou `@Service` (Spring peut être utilisé ici)
- Valide les Commands via Bean Validation (`jakarta.validation`)
- Publie les Domain Events via le port `EventPublisherPort`
- Ne connaît jamais MongoDB, Kafka, REST — seulement les Ports

## Structure type — complet

```java
@Component
@Transactional   // si opération write
public class MonUseCaseImpl implements MonUseCase {

    private final MonRepository monRepository;          // Port OUT
    private final EventPublisherPort eventPublisher;    // Port OUT — publie via Outbox
    private final MonAutrePort autrePort;               // Port OUT si besoin

    // Injection constructeur UNIQUEMENT
    public MonUseCaseImpl(MonRepository monRepository,
                          EventPublisherPort eventPublisher,
                          MonAutrePort autrePort) {
        this.monRepository = monRepository;
        this.eventPublisher = eventPublisher;
        this.autrePort = autrePort;
    }

    @Override
    @Audited(action = "MON_ACTION", resourceType = "Document")  // AuditEntry automatique
    @QuotaProtected(amount = 1, resource = "documents")          // Si consomme du quota
    public MonResultat execute(MonCommand command) {
        // 1. Récupérer le tenantId depuis TenantContext (extrait du JWT par TenantJwtFilter)
        String tenantId = TenantContext.get();

        // 2. Charger l'aggregate (toujours filtré par tenantId)
        MonAggregate aggregate = monRepository.findById(command.id(), tenantId)
            .orElseThrow(() -> new MonDomainException("Not found: " + command.id()));

        // 3. Logique métier sur l'aggregate
        aggregate.faireQuelqueChose(command.parametre());

        // 4. Sauvegarder + publier via Outbox Pattern (atomique — ADR-002)
        monRepository.save(aggregate);
        eventPublisher.publishViaOutbox(
            "docai.doc.some-event",
            aggregate.getId(),          // partitionKey = documentId (ADR-002)
            new MonDomainEvent(aggregate.getId(), tenantId, Instant.now()),
            tenantId
        );

        return MonResultat.from(aggregate);
    }
}
```

## Command et Query (CQRS)

```java
// fr.docai.application.command (write side)
public record MonCommand(
    @NotNull String tenantId,
    @NotNull String documentId,
    @NotBlank String parametre
) {}

// fr.docai.application.query (read side)
public record MonQuery(String tenantId, String documentId) {}
```

## Multi-tenancy : vérification obligatoire

Chaque use case qui accède à des données DOIT vérifier l'isolation tenant :

```java
MonAggregate aggregate = monRepository.findById(command.documentId())
    .orElseThrow(() -> new DocumentNotFoundException(command.documentId()));

// Vérification tenant obligatoire
if (!aggregate.getTenantId().equals(command.tenantId())) {
    throw new UnauthorizedTenantAccessException(command.tenantId(), command.documentId());
}
```

## Checklist

- [ ] Implémente l'interface UseCase du domaine (pas redéfinit la signature)
- [ ] Injection par constructeur uniquement (jamais `@Autowired` sur un champ)
- [ ] `TenantContext.get()` utilisé pour récupérer le tenantId (pas `command.tenantId()` seul)
- [ ] Isolation tenant : `findById(id, tenantId)` — jamais `findById(id)` seul
- [ ] Domain Event publié via `eventPublisher.publishViaOutbox()` (Outbox Pattern — ADR-002)
- [ ] Clé partition = `documentId` sur les topics pipeline (ADR-002)
- [ ] `@Audited` sur les use cases sensibles (upload, correction, décision fraude, billing)
- [ ] `@QuotaProtected` si l'action consomme du quota (upload uniquement en général)
- [ ] Command/Query dans les bons packages (`application.command` / `application.query`)
- [ ] Test unitaire avec mocks des ports (sans Spring, JUnit 5 pur)
