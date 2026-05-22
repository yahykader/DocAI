---
name: docai-archunit-verify
description: "Vérifie et corrige les violations d'architecture hexagonale dans le code DocAI. Utiliser quand un build CI échoue sur HexagonalArchitectureTest, quand on veut auditer un fichier avant commit, ou quand on ajoute une nouvelle dépendance. Liste les 12 règles ArchUnit et explique comment corriger chaque violation."
---

# DocAI — Vérifier la conformité Architecture

## Lancer les tests ArchUnit

```bash
# Tests architecture sur le domaine
./mvnw test -pl docai-domain -Dtest=HexagonalArchitectureTest

# Tests architecture complets (domaine + application)
./mvnw test -pl docai-domain,docai-application -Dtest=HexagonalArchitectureTest

# Vérification rapide avant push
./mvnw test -Dtest=HexagonalArchitectureTest --no-transfer-progress
```

## Les 12 règles ArchUnit — Description et correction

### Règle 1 — Le domaine est pur Java
**Violation :** Import `org.springframework`, `com.mongodb`, `org.apache.kafka`, `io.lettuce`, `software.amazon`, `jakarta.persistence`, `com.stripe` dans `fr.docai.domain`
**Correction :** Déplacer le code vers `docai-application` ou créer un Port (interface) dans le domaine et l'implémentation dans l'adapter correspondant

### Règle 2 — Les adapters ne s'appellent pas entre eux
**Violation :** Un adapter `fr.docai.adapter.X` importe un adapter `fr.docai.adapter.Y`
**Correction :** Passer par un Port du domaine. L'adapter A appelle un Port → le Use Case orchestre → appelle l'adapter B via un autre Port

### Règle 3 — Les use cases dépendent uniquement du domaine
**Violation :** `fr.docai.application` importe `com.mongodb`, `org.springframework.data`, etc.
**Correction :** Remplacer l'import infrastructure par l'interface Port correspondante

### Règle 4 — Les ports IN sont dans le domaine
**Violation :** Une interface `XxxUseCase` n'est pas dans `fr.docai.domain.port.in`
**Correction :** Déplacer l'interface dans le bon package

### Règle 5 — Les ports OUT sont dans le domaine
**Violation :** Une interface `XxxPort` n'est pas dans `fr.docai.domain.port.out`
**Correction :** Déplacer l'interface dans le bon package

### Règle 6 — Les aggregates sont dans le domaine
**Violation :** Une classe `@AggregateRoot` n'est pas dans `fr.docai.domain.model`
**Correction :** Déplacer la classe dans le bon package

### Règle 7 — Les controllers sont dans adapter.in.rest
**Violation :** Une classe `XxxController` n'est pas dans `fr.docai.adapter.in.rest`
**Correction :** Déplacer le controller dans le bon package

### Règle 8 — Les consumers Kafka sont dans adapter.in.kafka
**Violation :** Une classe `XxxKafkaConsumer` n'est pas dans `fr.docai.adapter.in.kafka`
**Correction :** Déplacer le consumer dans le bon package

### Règle 9 — Les adapters MongoDB sont dans adapter.out.mongodb
**Violation :** Une classe `XxxMongoAdapter` n'est pas dans `fr.docai.adapter.out.mongodb`
**Correction :** Déplacer l'adapter dans le bon package

### Règle 10 — Pas de logique métier dans les controllers
**Violation :** Un `XxxController` importe `XxxRepository`, `XxxMongoAdapter` ou `MongoTemplate`
**Correction :** Le controller appelle uniquement un UseCase. Le UseCase appelle le Repository via un Port.

### Règle 11 — Les Domain Events sont des records
**Violation :** Une classe dans `fr.docai.domain.event` n'est pas un `record`
**Correction :** Transformer la classe en `record` Java (immutable par nature)

### Règle 12 — Pas d'injection par champ (@Autowired)
**Violation :** `@Autowired` sur un champ dans `fr.docai.domain` ou `fr.docai.application`
**Correction :** Supprimer `@Autowired` et ajouter un constructeur avec tous les paramètres

### Règle bonus — Pas de @Transactional dans le domaine
**Violation :** `@Transactional` sur une classe ou méthode dans `fr.docai.domain`
**Correction :** Déplacer la gestion de transaction dans le use case (`fr.docai.application`) ou l'adapter MongoDB

## Audit rapide d'un fichier avant commit

Pour chaque fichier modifié, vérifier :

```
1. Package du fichier → conforme à la structure ?
2. Imports → aucun import framework interdit dans docai-domain ?
3. Classe Event → est-ce un record ?
4. Classe Controller → appelle-t-il directement un Repository ?
5. Injection → par constructeur uniquement ?
```

## Structure des packages — référence rapide

```
fr.docai.domain.model         → Aggregates (@AggregateRoot), Value Objects
fr.docai.domain.port.in       → Interfaces UseCase
fr.docai.domain.port.out      → Interfaces Port (Repository, Storage, Events)
fr.docai.domain.event         → Domain Events (records uniquement)
fr.docai.domain.service       → Domain Services
fr.docai.domain.exception     → Exceptions domaine
fr.docai.application.usecase  → Implémentations UseCase
fr.docai.application.command  → Commands CQRS
fr.docai.application.query    → Queries CQRS
fr.docai.adapter.in.rest      → Controllers Spring MVC
fr.docai.adapter.in.kafka     → Consumers Kafka
fr.docai.adapter.out.mongodb  → Adapters MongoDB
fr.docai.adapter.out.kafka    → Publishers Kafka
fr.docai.adapter.out.valkey   → Adapters Cache
fr.docai.adapter.out.ai       → Adapters LLM + OCR
fr.docai.adapter.out.storage  → Adapter S3
fr.docai.adapter.out.external → Adapters APIs externes
```

## Checklist avant push

- [ ] `./mvnw test -pl docai-domain -Dtest=HexagonalArchitectureTest` → vert
- [ ] Aucun import framework dans `docai-domain`
- [ ] Tous les Domain Events sont des `record`
- [ ] Injection par constructeur uniquement dans domain et application
- [ ] Les controllers n'accèdent pas aux repositories directement
