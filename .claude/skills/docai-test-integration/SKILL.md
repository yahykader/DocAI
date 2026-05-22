---
name: docai-test-integration
description: Crée un test d'intégration DocAI avec TestContainers (MongoDB, Kafka, Valkey, S3 LocalStack). Utiliser quand on demande un test d'intégration, un @SpringBootTest, un AbstractIntegrationTest, ou un test avec des services réels. Applique le pattern commons-testing avec reuse des conteneurs.
---

# DocAI — Créer un Test d'Intégration

## Règle fondamentale — Reuse des conteneurs (ADR-008)

Les conteneurs TestContainers sont partagés entre tous les tests via `withReuse(true)`.
Cela réduit le temps de démarrage de 4 min → 20 sec après le premier run.

## AbstractIntegrationTest — Base obligatoire

```java
// Dans commons-testing — hériter de cette classe pour TOUS les tests d'intégration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0")
        .withReuse(true);                    // ADR-008 : reuse obligatoire

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("apache/kafka:3.7.0"))
        .withReuse(true);

    @Container
    static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:8")
        .withExposedPorts(6379)
        .withReuse(true);

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3"))
        .withServices(S3)
        .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", valkey::getHost);
        registry.add("spring.data.redis.port", () -> valkey.getMappedPort(6379));
        registry.add("docai.s3.endpoint", () -> localstack.getEndpointOverride(S3).toString());
        registry.add("aws.region", localstack::getRegion);
    }
}
```

## Test d'un Use Case — Pattern

```java
@ExtendWith(SpringExtension.class)
public class UploadDocumentUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UploadDocumentUseCase uploadUseCase;
    @Autowired private DocumentMongoRepository documentRepo;
    @Autowired private KafkaTestUtils kafkaTestUtils;

    @Test
    @DisplayName("should upload document and publish DocumentUploaded event")
    void should_upload_document_and_publish_event() {
        // Given
        UploadCommand command = new UploadCommand(
            "acme-corp",           // tenantId
            "test.pdf",            // fileName
            "application/pdf",     // mimeType
            new byte[1024],        // content
            "idem-key-001"         // idempotencyKey
        );

        // When
        UploadResult result = uploadUseCase.execute(command);

        // Then — vérification en base
        Optional<DocumentDocument> doc = documentRepo
            .findByDocumentIdAndTenantId(result.documentId(), "acme-corp");
        assertThat(doc).isPresent();
        assertThat(doc.get().getStatus()).isEqualTo("PENDING");

        // Then — vérification event Kafka (asynchrone)
        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecord<String, ?> record = kafkaTestUtils
                .getSingleRecord("docai.doc.uploaded");
            assertThat(record.key()).isEqualTo(result.documentId());
            assertThat(record.headers().lastHeader("tenant-id"))
                .isNotNull()
                .extracting(h -> new String(h.value()))
                .isEqualTo("acme-corp");
        });
    }
}
```

## Test d'un Controller REST — Pattern

```java
@WebMvcTest(DocumentController.class)
public class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UploadDocumentUseCase uploadUseCase;

    @Test
    void should_return_201_when_upload_successful() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file", "invoice.pdf", "application/pdf", new byte[1024]);
        given(uploadUseCase.execute(any())).willReturn(new UploadResult("doc-123", "PENDING"));

        // When / Then
        mockMvc.perform(multipart("/v1/documents")
                .file(file)
                .header("Authorization", "Bearer " + mockJwt("acme-corp", "ANALYST")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.documentId").value("doc-123"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void should_return_403_when_jwt_missing() throws Exception {
        mockMvc.perform(multipart("/v1/documents")
                .file(new MockMultipartFile("file", new byte[1024])))
            .andExpect(status().isUnauthorized());
    }

    // Helper : JWT mocké avec tenantId et rôle
    private String mockJwt(String tenantId, String role) {
        return Jwts.builder()
            .claim("tenant_id", tenantId)
            .claim("realm_access", Map.of("roles", List.of(role)))
            .signWith(mockSigningKey())
            .compact();
    }
}
```

## Stubs WireMock — APIs externes

```java
// ExternalApiStubs — dans commons-testing
public class ExternalApiStubs {
    public static void stubLlmSuccess(WireMockServer server, String responseJson) {
        server.stubFor(post(urlEqualTo("/classify"))
            .willReturn(okJson(responseJson)));
    }
    public static void stubLlmTimeout(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/classify"))
            .willReturn(aResponse().withFixedDelay(35_000))); // > timeout 30s
    }
    public static void stubLlmRateLimit(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/classify"))
            .willReturn(aResponse().withStatus(429)));
    }
    public static void stubInseeSuccess(WireMockServer server, String siret, String raisonSociale) {
        server.stubFor(get(urlPathEqualTo("/entreprises/sirene/v3/siret/" + siret))
            .willReturn(okJson("{\"etablissement\":{\"uniteLegale\":{\"denominationUniteLegale\":\""
                + raisonSociale + "\"}}}")));
    }
}
```

## DocumentTestBuilder — Données de test

```java
// Pattern Builder pour créer des documents de test
Document doc = DocumentTestBuilder.aDocument()
    .withTenantId("acme-corp")
    .withStatus(DocumentStatus.CLASSIFIED)
    .withType(DocumentType.FACTURE)
    .withFraudScore(25)
    .build();
```

## Checklist

- [ ] Hérite de `AbstractIntegrationTest` (conteneurs partagés)
- [ ] `withReuse(true)` sur chaque conteneur (ADR-008)
- [ ] Assertions Kafka avec `await().atMost(10, SECONDS)`
- [ ] Headers Kafka vérifiés (`tenant-id`, `correlation-id`)
- [ ] WireMock pour tous les appels aux APIs externes (LLM, INSEE, BAN)
- [ ] Test idempotence : même commande exécutée 2× → résultat identique
- [ ] `./mvnw verify` passe en < 5 minutes (reuse des conteneurs)
