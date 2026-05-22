---
name: docai-bdd-scenario
description: Écrit les scénarios BDD Gherkin et leur implémentation Cucumber pour un module DocAI. Utiliser quand on demande des tests BDD, des scénarios Gherkin, des step definitions Cucumber, ou la Definition of Done d'un module. Couvre tous les modules du pipeline documentaire.
---

# DocAI — Écrire des Scénarios BDD

## Structure des fichiers

```
src/test/resources/features/
└── {module}/
    └── {fonctionnalite}.feature

src/test/java/fr/docai/bdd/
└── {module}/
    └── {Fonctionnalite}Steps.java
```

## Template Feature — Pattern obligatoire

```gherkin
Feature: {Fonctionnalité principale du module}

  Background:
    Given un tenant "acme-corp" avec le plan "PRO"
    And l'utilisateur authentifié a le rôle "ANALYST"

  Scenario: {Cas nominal — happy path}
    Given {état initial}
    When {action déclenchante}
    And {condition complémentaire si besoin}
    Then {résultat attendu principal}
    And {effet de bord attendu — event Kafka, audit, métrique}

  Scenario: {Cas d'erreur métier}
    Given {état qui provoque l'erreur}
    When {même action}
    Then {exception ou comportement fail-safe}
    And {pas d'effet de bord non voulu}

  Scenario: {Cas limite — résilience}
    Given {service externe indisponible ou Circuit Breaker OPEN}
    When {action déclenchante}
    Then {fallback appliqué — document en NEEDS_REVIEW, jamais bloqué}
```

## Scénarios par module — exemples de référence

### Module 1.1 — Upload Document
```gherkin
  Scenario: Upload réussi avec quota disponible
    Given un tenant avec 450 documents sur 500 (quota PRO)
    When l'utilisateur uploade un PDF de 2MB type "application/pdf"
    Then le document est en état "PENDING"
    And l'event "DocumentUploaded" est publié sur "docai.doc.uploaded"
    And le fichier est stocké dans S3 avec clé "{tenantId}/{documentId}"

  Scenario: Quota dépassé — upload refusé
    Given un tenant avec 500 documents sur 500 (quota épuisé)
    When l'utilisateur tente d'uploader un document
    Then une erreur 429 est retournée avec code "QUOTA_EXCEEDED"
    And aucun fichier n'est stocké dans S3

  Scenario: Idempotence — double soumission
    Given un document déjà uploadé avec idempotency-key "idem-123"
    When le même upload est soumis avec la même idempotency-key
    Then la réponse 200 retourne le document existant
    And aucun doublon n'est créé en base
```

### Module 1.2 — Classification IA
```gherkin
  Scenario: Classification réussie haute confiance
    Given un document en état PENDING sur le topic "docai.doc.uploaded"
    When le modèle retourne type=FACTURE avec score=0.95
    Then le document passe en état "CLASSIFIED"
    And l'event "DocumentClassified" est publié sur "docai.doc.classified"

  Scenario: Score faible — révision manuelle
    Given le modèle retourne score=0.55
    Then le document passe en état "NEEDS_REVIEW"
    And aucun event d'extraction n'est publié

  Scenario: Circuit Breaker OPEN — fail-safe
    Given le Circuit Breaker VisionModel est en état OPEN
    When le consumer tente la classification
    Then aucun appel au modèle n'est effectué
    And le document passe en état "NEEDS_REVIEW" avec motif "CIRCUIT_BREAKER_OPEN"
```

## Step Definitions — Pattern

```java
@CucumberContextConfiguration
@SpringBootTest
@Testcontainers
public class CucumberSpringContext extends AbstractIntegrationTest {}

public class DocumentUploadSteps {

    @Autowired private MockMvc mockMvc;
    @Autowired private DocumentMongoRepository documentRepo;
    @Autowired private KafkaTestConsumer kafkaConsumer;

    private ResultActions result;
    private String tenantId;

    @Given("un tenant {string} avec le plan {string}")
    public void tenantAvecPlan(String tenant, String plan) {
        this.tenantId = tenant;
        // Setup tenant dans MongoDB + Keycloak mock
    }

    @When("l'utilisateur uploade un PDF de {int}MB type {string}")
    public void uploadDocument(int sizeMb, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf",
            contentType, new byte[sizeMb * 1024 * 1024]);
        result = mockMvc.perform(multipart("/v1/documents").file(file)
            .header("Authorization", "Bearer " + mockJwt(tenantId)));
    }

    @Then("le document est en état {string}")
    public void documentEnEtat(String status) throws Exception {
        result.andExpect(status().isCreated());
        String documentId = extractDocumentId(result);
        await().atMost(5, SECONDS).until(() ->
            documentRepo.findById(documentId)
                .map(d -> d.getStatus().equals(status))
                .orElse(false)
        );
    }

    @And("l'event {string} est publié sur {string}")
    public void eventPublie(String eventType, String topic) {
        await().atMost(10, SECONDS).until(() ->
            kafkaConsumer.hasReceived(topic, eventType)
        );
    }
}
```

## Règles BDD DocAI

| ID | Règle |
|----|-------|
| BR-BDD-001 | Chaque module a au moins 3 scénarios : happy path, erreur métier, résilience |
| BR-BDD-002 | Les scénarios utilisent `await()` (Awaitility) pour les assertions asynchrones Kafka |
| BR-BDD-003 | Les tests BDD s'exécutent avec `AbstractIntegrationTest` (TestContainers) |
| BR-BDD-004 | La Definition of Done exige que tous les scénarios BDD passent en CI |
| BR-BDD-005 | Les scénarios PII masquent les données sensibles dans les assertions de logs |

## Checklist

- [ ] Feature file créé dans `src/test/resources/features/{module}/`
- [ ] Step definitions dans `fr.docai.bdd.{module}`
- [ ] Scénarios : happy path + erreur métier + résilience (Circuit Breaker)
- [ ] Assertions Kafka avec `await().atMost(10, SECONDS)`
- [ ] `./mvnw verify -Dcucumber.filter.tags="@{module}"` → vert
