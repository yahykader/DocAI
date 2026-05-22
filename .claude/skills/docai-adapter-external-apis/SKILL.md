---
name: docai-adapter-external-apis
description: "Implémente les adapters APIs externes DocAI dans docai-adapter-out-external (InseeApiAdapter OAuth2 SIRENE v3 cache 7j, BanApiAdapter Géoplateforme IGN cache 30j, RppsApiAdapter FHIR ANS cache 7j + fallback fichier local, Anti-Corruption Layer complet, fail-open obligatoire, WireMock stubs). Utiliser quand on demande d'implémenter la validation SIRET actif INSEE, la validation adresse BAN, la validation médecin RPPS, l'Anti-Corruption Layer externe, ou les adapters APIs gouvernementales. Prérequis : Module 2.2 Validation terminé (logique métier), commons-multitenancy disponible."
---

# DocAI — Adapter APIs Externes
## INSEE · BAN · RPPS · Anti-Corruption Layer · Fail-open · Cache Valkey

> **Référence :** EXTERNAL_APIS_SPECKIT.md + DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Module 2.2
> **Module Maven :** `docai-adapter-out-external`
> **Prérequis :** Module 2.2 Validation (logique métier) terminé.

---

## 1. Stratégie globale

**Règle fondamentale :** Les validations algorithmiques (Luhn SIRET, modulo 97 IBAN) s'exécutent **EN PREMIER**. Si elles échouent, aucun appel API n'est effectué.

```
SIRET reçu
  ↓
Algorithme Luhn (local, gratuit, immédiat)
  ├── INVALIDE → signal DATA_SIRET_INVALID (poids 40) — pas d'appel API
  └── VALIDE → API INSEE SIRENE (cache Valkey 7j)
                ├── ACTIF → aucun signal
                ├── INACTIF → signal DATA_SIRET_INACTIVE (poids 20, WARNING)
                └── API DOWN + cache → fail-open (validation continue)
                └── API DOWN + cache vide → flag VALIDATION_PARTIAL (WARNING)
```

---

## 2. APIs externes — Résumé

| API | URL | Coût | Rate limit | Cache Valkey |
|-----|-----|------|-----------|--------------|
| **INSEE SIRENE** | `portail-api.insee.fr` | Gratuit | **30 req/min** | 7j ± jitter |
| **BAN Adresse** | `api-adresse.data.gouv.fr` (Géoplateforme IGN) | Gratuit | 50 req/s | 30j ± jitter |
| **RPPS ANS** | API FHIR ANS | Gratuit | Non documenté | 7j ± jitter |

**Important :** Toutes les APIs sont gratuites. La seule contrainte est le **rate limit INSEE (30 req/min)** géré par le cache Valkey.

---

## 3. Anti-Corruption Layer — Structure hexagonale

```
docai-domain/port/out/
  └── ValidationReferencePort (interface)
        ├── validateSiret(String siret) → SiretValidationResult
        ├── validateAddress(String address) → AddressValidationResult
        └── validateRpps(String rpps) → RppsValidationResult

docai-adapter-out-external/
  ├── InseeApiAdapter implements ValidationReferencePort
  ├── BanApiAdapter implements ValidationReferencePort
  └── RppsApiAdapter implements ValidationReferencePort
```

**Règle ACL :** Les Value Objects du domaine (`SiretValidationResult`, `AddressValidationResult`, `RppsValidationResult`) ne contiennent AUCUNE référence aux APIs externes. Si l'INSEE change sa structure JSON → seul l'adapter change.

---

## 4. InseeApiAdapter — OAuth2 + Cache 7j

```java
// InseeApiAdapter — implements la partie SIRET de ValidationReferencePort
@Component
public class InseeApiAdapter {

    private static final String INSEE_OAUTH2_URL =
        "https://portail-api.insee.fr/token";
    private static final String SIRENE_URL =
        "https://portail-api.insee.fr/entreprises/sirene/V3.11/siret/{siret}";

    @CircuitBreaker(name = "insee", fallbackMethod = "failOpen")
    @Retry(name = "insee")
    @TimeLimiter(name = "insee")  // 5s max
    public SiretValidationResult validateSiretActive(String siret) {
        // 1. Vérifier cache Valkey
        String cacheKey = "insee:siret:" + siret;
        String cached = valkey.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.readValue(cached, SiretValidationResult.class);
        }

        // 2. Obtenir token OAuth2 (avec cache du token)
        String token = getOrRefreshToken();

        // 3. Appel API INSEE
        ResponseEntity<InseeResponse> response = restTemplate.exchange(
            SIRENE_URL, HttpMethod.GET,
            new HttpEntity<>(bearerHeaders(token)),
            InseeResponse.class, siret
        );

        // 4. Parser et mapper vers Value Object domaine (ACL)
        SiretValidationResult result = mapToResult(response.getBody());

        // 5. Mettre en cache (TTL 7j ± jitter — ADR-003)
        Duration ttl = JitterTtl.withJitter(Duration.ofDays(7));
        valkey.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), ttl);

        return result;
    }

    // Fail-open : INSEE down + cache vide → WARNING sans bloquer
    public SiretValidationResult failOpen(String siret, Exception e) {
        log.warn("INSEE API down — fail-open siret={}", siret);
        return SiretValidationResult.unverified(
            "INSEE API unavailable — validation skipped",
            "VALIDATION_PARTIAL"
        );
    }

    private String mapSiretStatus(InseeResponse response) {
        // ETATADMINISTRATIFETABLISSEMENT : "A" = actif, "F" = fermé
        return "A".equals(response.getEtablissement()
                                   .getUniteLegale()
                                   .getEtatAdministratifUniteLegale())
            ? "ACTIVE" : "INACTIVE";
    }
}
```

---

## 5. BanApiAdapter — Cache 30j

```java
// BanApiAdapter — Géoplateforme IGN (anciennement api-adresse.data.gouv.fr)
@Component
public class BanApiAdapter {

    private static final String BAN_URL =
        "https://geocodage.ign.fr/look4/address/search?q={query}&limit=1";

    @CircuitBreaker(name = "ban", fallbackMethod = "failOpen")
    @TimeLimiter(name = "ban")  // 5s max
    public AddressValidationResult validateAddress(String address) {
        // Clé cache = hash de l'adresse normalisée
        String cacheKey = "ban:address:" + sha256(address.toLowerCase().trim());
        String cached = valkey.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.readValue(cached, AddressValidationResult.class);
        }

        // Appel BAN
        BanResponse response = restTemplate.getForObject(
            BAN_URL, BanResponse.class, URLEncoder.encode(address, UTF_8)
        );

        AddressValidationResult result;
        if (response != null && !response.getFeatures().isEmpty()) {
            BanFeature feature = response.getFeatures().get(0);
            result = AddressValidationResult.of(
                true,
                feature.getProperties().getLabel(),     // Adresse normalisée
                feature.getProperties().getScore(),      // Score confiance 0–1
                feature.getGeometry().getCoordinates()   // [lon, lat]
            );
        } else {
            // Adresse inconnue de la BAN → WARNING (pas bloquant — DEC-EXT-003)
            result = AddressValidationResult.notFound(address);
        }

        // Cache 30j ± jitter (adresses très stables)
        Duration ttl = JitterTtl.withJitter(Duration.ofDays(30));
        valkey.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), ttl);

        return result;
    }

    // BAN down → WARNING non-bloquant (DEC-EXT-003)
    public AddressValidationResult failOpen(String address, Exception e) {
        log.warn("BAN API down — fail-open address={}", address);
        return AddressValidationResult.unverified();
    }
}
```

---

## 6. RppsApiAdapter — FHIR ANS + Fallback local

```java
// RppsApiAdapter — 2 modes : API FHIR ou fichier local
// Mode configuré par : docai.rpps.mode = API | LOCAL
@Component
@ConditionalOnProperty(name = "docai.rpps.mode", havingValue = "API", matchIfMissing = true)
public class RppsApiAdapter {

    private static final String RPPS_FHIR_URL =
        "https://gateway.api.esante.gouv.fr/fhir/v1/Practitioner?identifier={rpps}";

    @CircuitBreaker(name = "rpps", fallbackMethod = "failOpen")
    @TimeLimiter(name = "rpps")  // 5s max
    public RppsValidationResult validateRpps(String rppsNumber) {
        String cacheKey = "rpps:" + rppsNumber;
        String cached = valkey.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.readValue(cached, RppsValidationResult.class);
        }

        // Appel API FHIR ANS
        FhirBundle bundle = restTemplate.getForObject(
            RPPS_FHIR_URL, FhirBundle.class, rppsNumber
        );

        RppsValidationResult result;
        if (bundle != null && bundle.getTotal() > 0) {
            FhirPractitioner practitioner = bundle.getEntry().get(0).getResource();
            result = RppsValidationResult.of(
                true,
                practitioner.getName().get(0).getFamily(),
                practitioner.getQualification().get(0).getCode().getText(),
                "ACTIVE"
            );
        } else {
            // RPPS inconnu → signal DATA_RPPS_INVALID (poids 35)
            result = RppsValidationResult.notFound(rppsNumber);
        }

        Duration ttl = JitterTtl.withJitter(Duration.ofDays(7));
        valkey.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), ttl);
        return result;
    }

    public RppsValidationResult failOpen(String rppsNumber, Exception e) {
        log.warn("RPPS API down — fail-open rpps={}", rppsNumber);
        return RppsValidationResult.unverified("RPPS_UNVERIFIED");
    }
}

// Mode LOCAL — fallback fichier téléchargé mensuellement depuis ANS
@Component
@ConditionalOnProperty(name = "docai.rpps.mode", havingValue = "LOCAL")
public class RppsLocalFileAdapter {
    // Lit depuis une collection MongoDB chargée mensuellement
    // Même interface RppsValidationResult — swap transparent
}
```

---

## 7. Value Objects domaine (ACL)

```java
// SiretValidationResult — Value Object domaine (pas de référence INSEE)
public record SiretValidationResult(
    String siret,
    boolean isActive,
    String denomination,
    String status,          // ACTIVE, INACTIVE, UNVERIFIED, UNKNOWN
    String validationNote   // Informations complémentaires
) {
    public static SiretValidationResult active(String siret, String denomination) { ... }
    public static SiretValidationResult inactive(String siret) { ... }
    public static SiretValidationResult unverified(String note, String status) { ... }
}

// AddressValidationResult
public record AddressValidationResult(
    boolean isValid,
    String normalizedAddress,
    double confidence,       // 0.0–1.0
    double[] coordinates,    // [longitude, latitude]
    String status            // FOUND, NOT_FOUND, UNVERIFIED
) {}

// RppsValidationResult
public record RppsValidationResult(
    String rppsNumber,
    boolean isActive,
    String lastName,
    String profession,       // "Médecin", "Pharmacien", etc.
    String status            // ACTIVE, NOT_FOUND, UNVERIFIED
) {}
```

---

## 8. WireMock Stubs (tests CI)

```java
// ExternalApiStubs — dans commons-testing
public class ExternalApiStubs {

    // INSEE — Succès
    public static void stubInseeSuccess(WireMockServer server, String siret) {
        server.stubFor(get(urlPathMatching("/entreprises/sirene/V3.11/siret/" + siret))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(inseeSuccessResponse(siret))));
    }

    // INSEE — Timeout (test fail-open)
    public static void stubInseeTimeout(WireMockServer server) {
        server.stubFor(get(urlPathMatching("/entreprises/sirene/V3.11/siret/.*"))
            .willReturn(aResponse().withFixedDelay(10_000))); // > timeout 5s
    }

    // INSEE — 429 Rate limit
    public static void stubInseeRateLimit(WireMockServer server) {
        server.stubFor(get(urlPathMatching("/entreprises/sirene/V3.11/siret/.*"))
            .willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "60")));
    }

    // BAN — Succès
    public static void stubBanSuccess(WireMockServer server, String address) {
        server.stubFor(get(urlPathMatching("/look4/address/search.*"))
            .willReturn(aResponse().withStatus(200)
                .withBody(banSuccessResponse(address))));
    }

    // RPPS — FHIR Succès
    public static void stubRppsSuccess(WireMockServer server, String rpps) {
        server.stubFor(get(urlPathMatching("/fhir/v1/Practitioner.*identifier=" + rpps))
            .willReturn(aResponse().withStatus(200)
                .withBody(fhirBundleResponse(rpps))));
    }
}
```

---

## 9. BDD Scénarios

```gherkin
Scenario: SIRET valide Luhn + actif INSEE
  Given un document FACTURE avec SIRET "12345678901234"
  And l'algorithme Luhn valide le SIRET
  And l'API INSEE retourne le SIRET comme actif (WireMock)
  Then aucun signal fraude n'est levé pour ce SIRET
  And le résultat est mis en cache Valkey (TTL 7j)

Scenario: INSEE down + cache disponible → fail-open
  Given un SIRET "12345678901234" en cache Valkey
  And l'API INSEE est indisponible (WireMock timeout)
  When la validation s'exécute
  Then le résultat est retourné depuis le cache
  And aucune erreur n'est levée (fail-open)

Scenario: INSEE down + cache vide → WARNING VALIDATION_PARTIAL
  Given aucun cache Valkey pour le SIRET "99999999999999"
  And l'API INSEE est indisponible (WireMock timeout)
  When la validation s'exécute
  Then le résultat est UNVERIFIED avec flag VALIDATION_PARTIAL
  And le pipeline continue sans blocage

Scenario: Adresse BAN introuvable → WARNING non-bloquant
  Given un document avec adresse "1 Rue Imaginaire 99999 Nulle Part"
  And la BAN ne reconnaît pas cette adresse
  Then le résultat est NOT_FOUND (WARNING, pas BLOQUANT)
  And le document continue dans le pipeline
```

---

## 10. Definition of Done

- [ ] `InseeApiAdapter` : SIRET actif → passe, SIRET fermé → WARNING DATA_SIRET_INACTIVE
- [ ] Cache Valkey INSEE : 2ème appel même SIRET → hit Redis, 0 appel API (WireMock vérifié)
- [ ] Circuit Breaker INSEE : 5 erreurs 503 → OPEN, requête suivante → fail-open
- [ ] Rate limit 429 INSEE → retry après délai `Retry-After`
- [ ] `BanApiAdapter` : adresse connue → normalisée, inconnue → WARNING (non bloquant)
- [ ] Cache BAN 30j ± jitter configuré
- [ ] `RppsApiAdapter` : RPPS actif → valide, RPPS inconnu → signal DATA_RPPS_INVALID (poids 35)
- [ ] Mode LOCAL `RppsLocalFileAdapter` : swap transparent via configuration
- [ ] ACL vérifiée : aucun import INSEE/BAN/RPPS dans `docai-domain`
- [ ] Tous les appels testés avec WireMock (aucun appel réseau réel en CI)
- [ ] Fail-open validé sur chaque API (API down + cache → validation continue)
- [ ] EXPLAIN PLAN sur requêtes cache Valkey (ADR-010)
