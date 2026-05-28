# API Contract: Pagination & Versioning Standard

**Module**: `docai-commons` | **Version**: v1 | **Date**: 2026-05-28

## Paramètres de requête (tous les endpoints liste)

| Paramètre | Type | Défaut | Maximum | Obligatoire |
|-----------|------|--------|---------|-------------|
| `page` | integer | `0` | — | Non |
| `size` | integer | `20` | `100` | Non |
| `sort` | string | `createdAt,desc` | — | Non |

---

## Réponse succès — HTTP 200

```json
{
  "data": [
    { "...": "objet métier du module" }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

**Règles invariantes**:
- `data` est toujours un tableau (jamais `null` — tableau vide si aucun résultat)
- `page.number` est 0-based
- `page.totalElements` est scopé au `tenantId` du JWT appelant (jamais cross-tenant)
- `page.totalPages = ceil(totalElements / size)`

---

## Erreur — HTTP 400 (size > 100)

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Page size must not exceed 100 elements",
  "timestamp": "2026-05-28T10:00:00Z",
  "path": "/api/v1/documents"
}
```

---

## Versioning des URLs

**Pattern de base**: `http://host:8080/api/v1/{resource}`

| Version | Statut | Maintenu jusqu'à |
|---------|--------|-----------------|
| `/v1/` | Actif | Indéfini (version courante) |
| `/v2/` | Futur | Uniquement sur breaking change |

**Politique breaking change**: `/v1/` maintenu ≥ 6 mois après mise en ligne de `/v2/`.
Après obsolescence: HTTP 410 Gone avec corps indiquant l'URL de migration.

**HTTP 410 après obsolescence**:
```json
{
  "status": 410,
  "error": "Gone",
  "message": "API v1 has been deprecated. Please migrate to v2.",
  "migrationUrl": "https://api.docai.fr/api/v2/documents"
}
```

**Headers de dépréciation** (à ajouter dès annonce de v2):
```
Deprecation: true
Sunset: Sat, 31 Jan 2027 00:00:00 GMT
Link: <https://api.docai.fr/api/v2/documents>; rel="successor-version"
```

---

## Règles d'implémentation pour les modules consommateurs

Tout module REST exposant un endpoint liste DOIT respecter ces règles (BR-PAG-008) :

1. Accepter `PaginationParams` comme paramètre de méthode controller (`@Valid @ModelAttribute`)
2. Retourner `ApiResponse<T>` (jamais `List<T>` directement — viole le contrat)
3. Déléguer la validation à `PaginationParams` — HTTP 400 automatique si `size > 100`
4. **NE PAS réimplémenter la logique de pagination** — tout doublon est un blocage en PR review (BR-PAG-008)

**Exemple d'utilisation correcte** dans un controller consommateur:
```java
@GetMapping("/documents")
public ApiResponse<DocumentDto> listDocuments(
    @Valid @ModelAttribute PaginationParams params,
    @AuthenticationPrincipal JwtToken jwt
) {
    Page<DocumentDto> page = documentService.findByTenant(jwt.getTenantId(), params);
    return new ApiResponse<>(page.getContent(), PageInfo.from(page));
}
```
