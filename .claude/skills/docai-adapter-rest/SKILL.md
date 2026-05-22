---
name: docai-adapter-rest
description: "Crée un Controller REST dans docai-adapter-in-rest. Utiliser quand on demande un nouvel endpoint HTTP, un controller, ou une route API. Applique les conventions DocAI : RFC 7807, Keycloak JWT, versioning /v1, multi-tenancy automatique via TenantContext."
---

# DocAI — Créer un Adapter REST

## Localisation

Module : `docai-adapter-in-rest`
Package : `fr.docai.adapter.in.rest`

## Règles absolues

- Les controllers n'appellent JAMAIS les repositories directement (vérifié par ArchUnit)
- Toutes les routes sont préfixées `/v1/`
- Authentification via Keycloak JWT — `@PreAuthorize` sur chaque endpoint
- Le `tenantId` est extrait automatiquement depuis `TenantContext` — jamais depuis le body
- Erreurs au format RFC 7807 (`ProblemDetail`) — jamais de String arbitraire
- Réponse 201 pour création, 200 pour lecture, 204 pour suppression sans body

## Structure type d'un Controller

```java
@RestController
@RequestMapping("/v1/mon-ressource")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Mon Ressource", description = "Gestion de la ressource")
public class MonRessourceController {

    private final MonUseCase monUseCase;

    public MonRessourceController(MonUseCase monUseCase) {
        this.monUseCase = monUseCase;
    }

    @PostMapping
    @PreAuthorize("hasRole('ANALYST') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Créer une ressource")
    @ApiResponse(responseCode = "201", description = "Créé avec succès")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "403", description = "Accès refusé")
    public ResponseEntity<MonRessourceResponse> create(
        @Valid @RequestBody MonRessourceRequest request
    ) {
        String tenantId = TenantContext.get(); // Jamais depuis request body — toujours TenantContext.get()
        MonCommand command = new MonCommand(tenantId, request.parametre());
        MonResultat resultat = monUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(MonRessourceResponse.from(resultat));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'VIEWER', 'TENANT_ADMIN')")
    public ResponseEntity<MonRessourceResponse> getById(@PathVariable String id) {
        String tenantId = TenantContext.get(); // Jamais TenantContext.getCurrentTenantId()
        // ...
    }

    // Endpoint liste — toujours paginé (BR-PAG-001)
    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'VIEWER', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<MonRessourceResponse>>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (size > 100) throw new InvalidRequestException("Maximum page size is 100");
        String tenantId = TenantContext.get();
        Page<MonResultat> results = monUseCase.list(tenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.paginated(
            results.getContent().stream().map(MonRessourceResponse::from).toList(),
            buildPageMetadata(results)
        ));
    }
}
```

## Gestion des erreurs — GlobalExceptionHandler

Les erreurs retournent toujours `ProblemDetail` (RFC 7807) :

```java
// Exemple de mapping dans GlobalExceptionHandler
@ExceptionHandler(DocumentNotFoundException.class)
public ProblemDetail handleNotFound(DocumentNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        ex.getMessage()
    );
    problem.setTitle("Document non trouvé");
    problem.setProperty("errorCode", "DOC_NOT_FOUND");
    return problem;
}
```

## DTOs Request/Response

```java
// Request : validation Bean Validation obligatoire
public record MonRessourceRequest(
    @NotBlank @Size(max = 255) String parametre,
    @NotNull MonEnum type
) {}

// Response : méthode factory statique
public record MonRessourceResponse(String id, String parametre, String status) {
    public static MonRessourceResponse from(MonResultat resultat) {
        return new MonRessourceResponse(
            resultat.id(),
            resultat.parametre(),
            resultat.status().name()
        );
    }
}
```

## Conventions de nommage des routes

| Action | Méthode | URL |
|--------|---------|-----|
| Upload document | POST | `/v1/documents` |
| Récupérer | GET | `/v1/documents/{id}` |
| Lister | GET | `/v1/documents?page=0&size=20` |
| Supprimer | DELETE | `/v1/documents/{id}` |
| Action métier | POST | `/v1/documents/{id}/action` |

## Checklist

- [ ] `@RequestMapping` commence par `/v1/`
- [ ] `TenantContext.get()` (pas `getCurrentTenantId()`) — jamais du body
- [ ] `@PreAuthorize` avec rôles DocAI : `TENANT_ADMIN`, `ANALYST`, `VIEWER`, `FRAUD_REVIEWER`, `SYSTEM`
- [ ] DTOs avec `@Valid` et annotations de validation
- [ ] Erreurs en `ProblemDetail` via `GlobalExceptionHandler` (RFC 7807)
- [ ] Endpoints liste : `ApiResponse.paginated()` + `size <= 100` (BR-PAG-001)
- [ ] Endpoints unitaires : `ApiResponse.of()` ou réponse directe
- [ ] `@Tag` et `@Operation` SpringDoc pour la doc API
- [ ] Test d'intégration avec `@WebMvcTest` + JWT mocké Keycloak
