---
name: docai-domain-model
description: Crée un composant du domaine DocAI (Aggregate, Value Object, Domain Event, Domain Service, Exception). Utiliser quand on demande de créer ou modifier un élément dans docai-domain. Ne jamais importer Spring, MongoDB, Kafka ou tout framework infrastructure dans ce module.
---

# DocAI — Créer un composant domain

## Règles absolues (vérifiées par ArchUnit)

> **Java 21 LTS obligatoire** — utiliser Records, Sealed Classes, Pattern Matching.

- AUCUN import `org.springframework`, `com.mongodb`, `org.apache.kafka`, `io.lettuce`, `software.amazon`, `jakarta.persistence`, `com.stripe` dans `docai-domain`
- Les Domain Events sont des `record` Java (immuables) — **jamais des classes mutables**
- Les interfaces UseCase finissent par `UseCase` et sont dans `fr.docai.domain.port.in`
- Les interfaces Port finissent par `Port` et sont dans `fr.docai.domain.port.out`
- Les Aggregates sont annotés `@AggregateRoot` et dans `fr.docai.domain.model`
- Injection uniquement par constructeur — jamais `@Autowired` sur un champ
- Toute logique métier dans le domaine, jamais dans les adapters
- Timestamps : toujours `Instant` (jamais `String`, `Date` ou `LocalDateTime`)

## Structure des packages

```
fr.docai.domain/
├── model/       → Aggregates (@AggregateRoot), Value Objects, Enums, Sealed Classes
├── port/
│   ├── in/      → Interfaces UseCase (Inbound Ports)
│   └── out/     → Interfaces Repository, Storage, Events (Outbound Ports)
├── event/       → Domain Events (records Java)
├── service/     → Domain Services (logique métier pure)
└── exception/   → Exceptions domaine typées
```

## Patterns à suivre selon le type demandé

### Aggregate
```java
@AggregateRoot
public class MonAggregate {
    private final MonAggregateId id;   // Value Object typé
    // Constructeur complet, méthodes métier, pas de setter public
    // Lever des DomainEvents via une liste interne
}
```

### Value Object
```java
public record MonValueObject(String valeur) {
    public MonValueObject {
        // Validation dans le compact constructor
        Objects.requireNonNull(valeur, "valeur ne peut pas être null");
        if (valeur.isBlank()) throw new IllegalArgumentException("valeur vide");
    }
}
```

### Domain Event
```java
// Toujours record, jamais class mutable
// Timestamps en Instant (jamais String)
public record DocumentUploaded(
    String eventId,      // UUID
    String documentId,
    String tenantId,
    String mimeType,
    Instant occurredAt   // ← Instant obligatoire (pas String)
) {
    // Factory method
    public static DocumentUploaded of(String documentId, String tenantId, String mimeType) {
        return new DocumentUploaded(UUID.randomUUID().toString(),
            documentId, tenantId, mimeType, Instant.now());
    }
}
```

### Enum avec logique métier (Java 21)
```java
// SignalType — Enum avec poids (pattern Value Object)
public enum SignalType {
    DATA_ARITHMETIC_ERROR(35),
    DATA_SIRET_INVALID(40),
    DATA_IBAN_INVALID(40),
    META_EDITOR_SUSPICIOUS(25),
    META_HIDDEN_LAYERS(30),
    VISUAL_TEXT_OVERLAY(35);

    private final int weight;
    SignalType(int weight) { this.weight = weight; }
    public int weight() { return weight; }
}
```

### Sealed Classes (Java 21 — états domaine)
```java
// Pattern Sealed pour les états d'un document — exhaustivité garantie
public sealed interface DocumentState
    permits PendingState, ClassifiedState, ExtractedState, CompletedState, FailedState {
    String status();
}

public record PendingState()     implements DocumentState { public String status() { return "PENDING"; } }
public record ClassifiedState()  implements DocumentState { public String status() { return "CLASSIFIED"; } }
public record ExtractedState()   implements DocumentState { public String status() { return "EXTRACTED"; } }
public record CompletedState()   implements DocumentState { public String status() { return "COMPLETED"; } }
public record FailedState(String reason) implements DocumentState { public String status() { return "FAILED"; } }

// Pattern Matching (Java 21) — exhaustif, pas de default nécessaire
String message = switch (state) {
    case PendingState p     -> "En attente de classification";
    case ClassifiedState c  -> "Classifié, en attente d'extraction";
    case ExtractedState e   -> "Extrait, en attente de validation fraude";
    case CompletedState c   -> "Pipeline terminé avec succès";
    case FailedState f      -> "Échec : " + f.reason();
};
```

### UseCase (Inbound Port)
```java
// Dans fr.docai.domain.port.in
public interface MonUseCase {
    MonResultat execute(MonCommand command);
}
```

### Port sortant
```java
// Dans fr.docai.domain.port.out
public interface MonRepository {
    Optional<MonAggregate> findById(MonId id);
    void save(MonAggregate aggregate);
}
```

### Exception domaine
```java
public class MonDomainException extends RuntimeException {
    public MonDomainException(String message) { super(message); }
    public MonDomainException(String message, Throwable cause) { super(message, cause); }
}
```

## Checklist avant de livrer

- [ ] Aucun import framework dans le fichier créé
- [ ] Le Domain Event est un `record`
- [ ] Les interfaces UseCase/Port sont dans les bons packages
- [ ] Validation dans le constructeur compact des Value Objects
- [ ] Test unitaire créé dans `docai-domain/src/test/java/` (sans Spring, sans TestContainers)
- [ ] `./mvnw test -pl docai-domain` passe en < 30 secondes
