---
name: docai-contract-testing
description: "Crée des contrats Spring Cloud Contract pour les endpoints publics DocAI. Utiliser quand on demande des contract tests, des tests frontend/backend, la vérification d'un contrat API, ou quand un nouveau endpoint public est créé. Obligatoire pour tous les endpoints de l'API publique (Module 6)."
---

# DocAI — Contract Testing (Spring Cloud Contract)

## Quand utiliser

- Tout nouveau endpoint exposé dans l'API publique (`/v1/documents`, `/v1/analytics`, etc.)
- Chaque endpoint du Module 6 (API Publique) a un contrat obligatoire
- Le frontend **Angular** consomme les stubs WireMock générés automatiquement

## Structure des contrats

```
src/test/resources/contracts/
└── documents/
    ├── should_return_201_when_document_uploaded.groovy
    ├── should_return_400_when_invalid_mime_type.groovy
    ├── should_return_429_when_quota_exceeded.groovy
    └── should_return_404_when_document_not_found.groovy
```

## Template de contrat Groovy

```groovy
// src/test/resources/contracts/documents/should_return_201_when_document_uploaded.groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Doit retourner 201 quand un document valide est uploadé"

    request {
        method POST()
        url '/v1/documents'
        multipart(
            file: named(
                name: value(consumer(regex('.+')), producer('invoice.pdf')),
                content: value(consumer(regex('.+')), producer('PDF content')),
                contentType: value(consumer(regex('application/pdf')), producer('application/pdf'))
            )
        )
        headers {
            header('Authorization', value(
                consumer(regex('Bearer .+')),
                producer('Bearer eyJhbGciOiJSUzI1NiJ9...')
            ))
            header('X-Idempotency-Key', value(
                consumer(regex('[a-f0-9\\-]+')),
                producer('550e8400-e29b-41d4-a716-446655440000')
            ))
        }
    }

    response {
        status 201
        headers {
            contentType('application/json')
        }
        body(
            data: [
                documentId: $(consumer(regex('[a-f0-9\\-]+')), producer('doc-uuid-123')),
                status: 'PENDING',
                fileName: 'invoice.pdf',
                mimeType: 'application/pdf'
            ]
        )
    }
}
```

## Contrat erreur — Quota dépassé

```groovy
Contract.make {
    description "Doit retourner 429 quand le quota mensuel est dépassé"

    request {
        method POST()
        url '/v1/documents'
        headers {
            header('Authorization', value(consumer(regex('Bearer .+')), producer('Bearer token-quota-exceeded')))
        }
    }

    response {
        status 429
        body(
            type: 'https://api.docai.fr/errors/quota-exceeded',
            title: 'Quota Exceeded',
            status: 429,
            detail: $(consumer(regex('.+')), producer('Your plan allows 500 documents/month')),
            errorCode: 'QUOTA-001'
        )
    }
}
```

## Test de base côté producteur (backend)

```java
// Test Spring Cloud Contract côté backend — généré automatiquement
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseContractTest {

    @Autowired protected MockMvc mockMvc;
    @MockBean protected SubmitDocumentUseCase submitUseCase;

    @BeforeEach
    void setup() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        // Mock du use case pour le contrat nominal
        given(submitUseCase.execute(any()))
            .willReturn(new UploadResult("doc-uuid-123", "PENDING", "invoice.pdf", "application/pdf"));

        // Mock quota dépassé (identifié par le token Bearer)
        given(submitUseCase.execute(argThat(cmd -> cmd.tenantId().equals("quota-exceeded-tenant"))))
            .willThrow(new QuotaExceededException("QUOTA-001", "Your plan allows 500 documents/month"));
    }
}
```

## Intégration CI — Job contract-tests dans 01-ci.yml

```yaml
contract-tests:
  name: Contract Tests Frontend/Backend
  runs-on: ubuntu-latest
  needs: unit-tests
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { java-version: '21', distribution: 'temurin', cache: maven }

    - name: Vérification contrats API
      run: |
        ./mvnw spring-cloud-contract:generateTests \
               spring-cloud-contract:run \
               --no-transfer-progress

    - name: Publication stubs WireMock (pour le frontend)
      uses: actions/upload-artifact@v4
      with:
        name: wiremock-stubs
        path: '**/target/stubs/'
```

## Utilisation des stubs côté frontend React

```javascript
// Le frontend télécharge les stubs depuis les artefacts CI et les utilise dans ses tests
// Les stubs sont des fichiers WireMock JSON générés automatiquement depuis les contrats

// jest.setup.js
import { startWireMock } from 'wiremock-node-client';

beforeAll(async () => {
  await startWireMock({
    port: 8099,
    stubsDir: './stubs/docai-backend'
  });
});
```

## Conventions de nommage des contrats

```
should_return_{code}_when_{context}.groovy

Exemples :
  should_return_201_when_document_uploaded.groovy
  should_return_400_when_invalid_mime_type.groovy
  should_return_401_when_jwt_missing.groovy
  should_return_403_when_role_insufficient.groovy
  should_return_404_when_document_not_found.groovy
  should_return_409_when_idempotency_key_already_used.groovy
  should_return_422_when_extraction_confidence_too_low.groovy
  should_return_429_when_quota_exceeded.groovy
  should_return_429_when_rate_limit_exceeded.groovy
```

## Checklist

- [ ] Contrat créé pour chaque endpoint du Module 6 (API publique)
- [ ] Scénario nominal + tous les cas d'erreur (400, 401, 403, 404, 409, 422, 429)
- [ ] `BaseContractTest` configure les mocks correspondant à chaque contrat
- [ ] Stubs WireMock publiés comme artefacts CI (pour le frontend)
- [ ] Job `contract-tests` dans `01-ci.yml` passe en vert
- [ ] Nommage : `should_return_{code}_when_{context}.groovy`
- [ ] Réponses d'erreur conformes RFC 7807 (type, title, status, detail, errorCode)
