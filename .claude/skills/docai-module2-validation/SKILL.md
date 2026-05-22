---
name: docai-module2-validation
description: "Implémente le Module 2 Phase 2.2 DocAI (validation SIRET Luhn, IBAN modulo 97, InseeApiAdapter OAuth2 SIRENE V3.11 portail-api.insee.fr cache 7j, BanApiAdapter Géoplateforme IGN cache 30j, RppsApiAdapter FHIR ANS cache 7j + mode LOCAL fichier, Anti-Corruption Layer Value Objects domaine SiretValidationResult/AddressValidationResult/RppsValidationResult, fail-open obligatoire, WireMock stubs CI). Utiliser quand on demande de valider un SIRET, un IBAN, une adresse, un RPPS, d'implémenter l'Anti-Corruption Layer APIs externes, ou la stratégie fail-open. Prérequis : Module 2.1 OCR+LLM terminé."
---

# DocAI — Module 2 Validation Métier & APIs Externes (Phase 2.2)

## Règle fondamentale — Validation locale d'abord

```
TOUJOURS : Validation algorithmique locale → si échec → STOP (aucun appel API)
ENSUITE  : Vérification API externe (si local OK) → avec cache Valkey 7j ±6h
```

## Business Rules

| ID | Règle | Sévérité |
|----|-------|---------|
| BR-EXT-010 | SIRET : 14 chiffres + algorithme Luhn | BLOQUANT |
| BR-EXT-011 | IBAN : algorithme modulo 97 (ISO 13616) | BLOQUANT |
| BR-EXT-012 | SIRET Luhn OK → vérification activité INSEE | AVERTISSEMENT |
| BR-EXT-013 | Date émission ne doit pas être dans le futur | BLOQUANT |
| BR-EXT-014 | Montant TTC = HT + TVA (±0.02€) | BLOQUANT |
| BR-EXT-015 | API externe down + cache disponible → fail-open (validation continue) | MUST |
| BR-EXT-016 | API externe down + cache vide → avertissement dans rapport | MUST |

## Domain Services — Validateurs locaux (sans appel API)

```java
// Dans fr.docai.domain.service — aucun import framework

// Validation SIRET — Algorithme Luhn
public class SiretLuhnValidator {

    public ValidationResult validate(String siret) {
        if (siret == null || !siret.matches("\\d{14}")) {
            return ValidationResult.invalid("SIRET", "FORMAT_INVALID",
                "Le SIRET doit contenir exactement 14 chiffres");
        }
        if (!luhnCheck(siret)) {
            return ValidationResult.invalid("SIRET", "LUHN_FAILED",
                "Le SIRET ne passe pas la vérification Luhn");
        }
        return ValidationResult.valid("SIRET", siret, "LOCAL_LUHN");
    }

    private boolean luhnCheck(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}

// Validation IBAN — Modulo 97 (ISO 13616)
public class IbanModulo97Validator {

    public ValidationResult validate(String iban) {
        if (iban == null || iban.isBlank()) {
            return ValidationResult.invalid("IBAN", "FORMAT_INVALID", "IBAN absent");
        }
        String normalized = iban.replaceAll("\\s", "").toUpperCase();
        if (!normalized.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")) {
            return ValidationResult.invalid("IBAN", "FORMAT_INVALID", "Format IBAN invalide");
        }
        if (!modulo97Check(normalized)) {
            return ValidationResult.invalid("IBAN", "MODULO97_FAILED", "IBAN invalide (modulo 97)");
        }
        return ValidationResult.valid("IBAN", maskIban(normalized), "LOCAL_MODULO97");
    }

    private boolean modulo97Check(String iban) {
        // Déplacer les 4 premiers caractères à la fin
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        // Convertir lettres en chiffres (A=10, B=11, ...)
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) digits.append(c - 'A' + 10);
            else digits.append(c);
        }
        // Vérifier modulo 97 = 1
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private String maskIban(String iban) {
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }
}

// Value Object résultat de validation
public record ValidationResult(
    String fieldName,
    ValidationStatus status,    // VALID, INVALID, UNVERIFIED
    String source,              // "LOCAL_LUHN", "INSEE_API", "CACHE", "UNVERIFIED"
    String errorCode,
    String message
) {
    public static ValidationResult valid(String field, String value, String source) { ... }
    public static ValidationResult invalid(String field, String code, String message) { ... }
    public static ValidationResult unverified(String field, String reason) { ... }
}
```

## Anti-Corruption Layer — API INSEE

```java
@Component
public class InseeApiAdapter implements SiretValidationPort {

    private final WebClient webClient;
    private final ValkeyExtractionCacheAdapter cache;

    @Override
    @CircuitBreaker(name = "insee", fallbackMethod = "siretFallback")
    @Retry(name = "insee")
    public ValidationResult validateSiret(String siret) {
        // 1. Cache d'abord (7 jours ±6h — ADR-003)
        String cacheKey = "siret:validation:" + siret;
        Optional<String> cached = cache.getCachedResponse(cacheKey);
        if (cached.isPresent()) {
            log.info("SIRET validation cache hit siret={} tenantId={}", maskSiret(siret), TenantContext.get());
            return deserialize(cached.get());
        }

        // 2. Appel API INSEE SIRENE V3.11 — URL CORRECTE
        // Host : portail-api.insee.fr (pas api.insee.fr)
        // Chemin : /entreprises/sirene/V3.11/siret/{siret} (V3.11 et non v3)
        InseeResponse response = webClient.get()
            .uri("https://portail-api.insee.fr/entreprises/sirene/V3.11/siret/{siret}", siret)
            .header("Authorization", "Bearer " + getToken())
            .retrieve()
            .onStatus(HttpStatus.NOT_FOUND::equals,
                r -> Mono.error(new SiretNotFoundException(siret)))
            .bodyToMono(InseeResponse.class)
            .block(Duration.ofSeconds(5));

        ValidationResult result = mapToResult(siret, response);

        // 3. Mettre en cache — JitterTtl 7 jours ±6h (ADR-003)
        cache.cacheResponse(cacheKey, serialize(result),
            JitterTtl.withJitter(Duration.ofDays(7)));

        log.info("SIRET validation API hit siret={} status={}", maskSiret(siret), result.status());
        return result;
    }

    // Fallback fail-open (BR-EXT-015 + BR-EXT-016)
    private ValidationResult siretFallback(String siret, Throwable ex) {
        // Vérifier si le cache est disponible (même expiré)
        String cacheKey = "siret:validation:" + siret;
        Optional<String> staleCache = cache.getCachedResponseStale(cacheKey);

        if (staleCache.isPresent()) {
            log.warn("INSEE API unavailable, using stale cache siret={} reason={}",
                maskSiret(siret), ex.getClass().getSimpleName());
            return deserialize(staleCache.get()); // Fail-open avec cache expiré
        }

        log.warn("INSEE API unavailable, no cache siret={} → UNVERIFIED", maskSiret(siret));
        return ValidationResult.unverified("SIRET", "INSEE_UNAVAILABLE"); // BR-EXT-016
    }
}
```

## ValidateExtractionUseCase — Orchestration

```java
@Component
public class ValidateExtractionUseCaseImpl implements ValidateExtractionUseCase {

    private final SiretLuhnValidator siretLuhnValidator;
    private final IbanModulo97Validator ibanValidator;
    private final SiretValidationPort inseeAdapter;
    private final AddressValidationPort banAdapter;

    @Override
    public ValidationReport execute(ValidateExtractionCommand command) {
        List<ValidationResult> results = new ArrayList<>();
        ExtractionResult extraction = command.extraction();

        // SIRET — local d'abord, puis INSEE si Luhn OK
        if (extraction.hasSiret()) {
            ValidationResult luhn = siretLuhnValidator.validate(extraction.siret());
            results.add(luhn);
            if (luhn.isValid()) {
                results.add(inseeAdapter.validateSiret(extraction.siret())); // API uniquement si Luhn OK
            }
        }

        // IBAN — modulo 97 local uniquement
        if (extraction.hasIban()) {
            results.add(ibanValidator.validate(extraction.iban()));
        }

        // Cohérence arithmétique HT + TVA = TTC (±0.02€)
        if (extraction.hasAmounts()) {
            results.add(validateArithmetic(extraction));
        }

        // Date dans le futur
        if (extraction.hasDate()) {
            results.add(validateDate(extraction.emissionDate()));
        }

        return new ValidationReport(command.documentId(), results, Instant.now());
    }
}
```

## Scénarios BDD

```gherkin
Scenario: SIRET invalide Luhn — pas d'appel INSEE
  Given document FACTURE avec SIRET "12345678901234" (Luhn invalide)
  When la validation s'exécute
  Then ValidationResult INVALID code=LUHN_FAILED
  And aucun appel à l'API INSEE n'est effectué

Scenario: INSEE down, cache disponible — fail-open
  Given SIRET "81969482600017" en cache Valkey
  And l'API INSEE est indisponible
  When la validation SIRET s'exécute
  Then le résultat cache est utilisé (VALID)
  And log WARN "using stale cache"

Scenario: IBAN invalide — modulo 97 échoue
  Given document RIB avec IBAN "FR76INVALID"
  When la validation s'exécute
  Then ValidationResult INVALID code=MODULO97_FAILED

Scenario: Arithmétique incohérente — 100 HT + 20 TVA ≠ 130 TTC
  Given montantHT=100, tauxTVA=20%, montantTTC=130
  When la validation s'exécute
  Then ValidationResult INVALID code=ARITHMETIC_ERROR
```

## Checklist

- [ ] `SiretLuhnValidator` dans `fr.docai.domain.service` (aucun import framework)
- [ ] `IbanModulo97Validator` dans `fr.docai.domain.service`
- [ ] Validation locale AVANT tout appel API (si local KO → STOP)
- [ ] Cache Valkey 7 jours ±6h sur SIRET et adresses (ADR-003)
- [ ] Fail-open testé : API down + cache → validation continue
- [ ] Fail-open testé : API down + pas de cache → UNVERIFIED dans rapport
- [ ] SIRET masqué dans les logs (jamais en clair)
- [ ] IBAN masqué dans les logs `[PII_MASKED]`
- [ ] Tests unitaires Luhn et modulo 97 : cas valides, invalides, limites
- [ ] WireMock stubs : INSEE timeout, 404, 429, succès
- [ ] URL INSEE : `portail-api.insee.fr/entreprises/sirene/V3.11/siret/{siret}` (V3.11 pas v3)
- [ ] `BanApiAdapter` : adresse reconnue → VALID, inconnue → WARNING non-bloquant (DEC-EXT-003)
- [ ] Cache BAN 30j ± jitter (ADR-003)
- [ ] `RppsApiAdapter` : RPPS actif → VALID, inconnu → signal `DATA_RPPS_INVALID` (poids 35)
- [ ] Mode LOCAL `RppsLocalFileAdapter` : swap transparent via `docai.rpps.mode=LOCAL`
- [ ] ACL vérifiée : aucun import `portail-api.insee.fr`, `data.gouv.fr` ou `esante.gouv.fr` dans `docai-domain`
- [ ] Tous les appels testés via WireMock (zéro appel réseau réel en CI)

---

## Anti-Corruption Layer — Structure et Value Objects domaine

> Lire `references/ban-rpps-adapters.md` pour le code complet des adapters BAN et RPPS.

### Pourquoi un ACL ?

Sans ACL, si l'INSEE change sa structure JSON, il faut modifier le code métier. Avec ACL, **seul l'adapter change** — le domaine est protégé.

```
docai-domain/port/out/
  └── ValidationReferencePort (interface)
        ├── validateSiret(String) → SiretValidationResult
        ├── validateAddress(String) → AddressValidationResult
        └── validateRpps(String) → RppsValidationResult

docai-adapter-out-external/
  ├── InseeApiAdapter     implements ValidationReferencePort (partie SIRET)
  ├── BanApiAdapter       implements ValidationReferencePort (partie adresse)
  └── RppsApiAdapter      implements ValidationReferencePort (partie RPPS)
      RppsLocalFileAdapter implements ValidationReferencePort (mode LOCAL)
```

### Value Objects domaine (aucune référence aux APIs externes)

```java
// Dans fr.docai.domain.model/ — Java pur, zéro import infrastructure

// SiretValidationResult
public record SiretValidationResult(
    String siret,
    boolean isActive,
    String denomination,          // Raison sociale (null si données masquées INSEE)
    ValidationSource source,      // CACHE | API | ALGORITHMIC
    String status,                // ACTIVE | INACTIVE | UNVERIFIED | UNKNOWN
    Instant validatedAt
) {
    public static SiretValidationResult active(String siret, String denom) { ... }
    public static SiretValidationResult inactive(String siret) { ... }
    public static SiretValidationResult unverified(String note) { ... }
}

// AddressValidationResult
public record AddressValidationResult(
    String inputAddress,
    boolean isRecognized,
    String normalizedAddress,    // Adresse normalisée par la BAN
    double confidenceScore,      // 0.0–1.0 (score BAN)
    ValidationSource source      // CACHE | API
) {
    // > 0.8 → VALID | 0.5–0.8 → UNVERIFIED | < 0.5 → NOT_FOUND (WARNING non-bloquant)
}

// RppsValidationResult
public record RppsValidationResult(
    String rppsNumber,
    boolean isActive,
    String profession,           // "Médecin", "Infirmier", etc.
    String lastName,             // Nom d'exercice
    ValidationSource source      // CACHE | API | LOCAL_FILE
) {
    public static RppsValidationResult active(String rpps, String profession, String name) { ... }
    public static RppsValidationResult notFound(String rpps) { ... }
    public static RppsValidationResult unverified(String rpps) { ... }
}
```

---

## BanApiAdapter — Résumé (détail dans references/ban-rpps-adapters.md)

```java
// BanApiAdapter — API Géoplateforme IGN (anciennement api-adresse.data.gouv.fr)
// URL : https://geocodage.ign.fr/look4/address/search?q={query}&limit=1
// Cache Valkey : ban:address:{sha256(adresse)} — TTL 30j ± jitter (ADR-003)
// Resilience4j : CircuitBreaker 50%/10calls, timeout 5s

// Comportement :
// score > 0.8 → AddressValidationResult.isRecognized = true (VALID)
// score 0.5–0.8 → isRecognized = true mais confidenceScore bas (UNVERIFIED)
// score < 0.5 → isRecognized = false (NOT_FOUND — WARNING non-bloquant, DEC-EXT-003)
// API down + cache vide → AddressValidationResult.unverified() — JAMAIS une exception

@CircuitBreaker(name = "ban", fallbackMethod = "failOpen")
@TimeLimiter(name = "ban")  // 5s max
public AddressValidationResult validateAddress(String address) { ... }

public AddressValidationResult failOpen(String address, Exception e) {
    log.warn("BAN API down — fail-open address=[MASKED]");
    return AddressValidationResult.unverified(address);
}
```

**Cache :** TTL **30 jours** (adresses très stables) — `JitterTtl.withJitter(Duration.ofDays(30))`

---

## RppsApiAdapter — Résumé (détail dans references/ban-rpps-adapters.md)

```java
// RppsApiAdapter — API FHIR ANS
// URL : https://gateway.api.esante.gouv.fr/fhir/v1/Practitioner?identifier={rpps}
// Cache Valkey : rpps:{numero} — TTL 7j ± jitter (ADR-003)
// Resilience4j : CircuitBreaker 60%/8calls, timeout 5s

// 2 modes configurables (swap Spring sans changer le code domaine) :
// docai.rpps.mode=API   → RppsApiAdapter (défaut DEV/staging)
// docai.rpps.mode=LOCAL → RppsLocalFileAdapter (MongoDB local, refresh mensuel)

@ConditionalOnProperty(name = "docai.rpps.mode", havingValue = "API", matchIfMissing = true)
@Component
public class RppsApiAdapter implements RppsValidatorPort {
    // FHIR Bundle → RppsValidationResult (ACL)
    // bundle.total > 0 + profession active → isActive = true
    // bundle.total = 0 → notFound → signal DATA_RPPS_INVALID (poids 35)
    // API down + cache vide → unverified (RPPS_UNVERIFIED — WARNING dégradé)
}
```

---

## Stratégie Fail-Open par API

| API | Sévérité règle | API down + cache disponible | API down + cache vide |
|-----|---------------|----------------------------|----------------------|
| **INSEE** (SIRET actif) | WARNING | ✅ Retour cache | ✅ UNVERIFIED + flag VALIDATION_PARTIAL |
| **BAN** (adresse) | WARNING | ✅ Retour cache | ✅ UNVERIFIED (non-bloquant — DEC-EXT-003) |
| **RPPS** (médecin actif) | BLOQUANT | ✅ Retour cache | ⚠️ Retour UNVERIFIED (déclassé WARNING) + flag RPPS_UNVERIFIED |

**Justification (DEC-EXT-003) :** Bloquer un document légitime parce qu'une API gouvernementale est en maintenance est plus dommageable que laisser passer avec un flag VALIDATION_PARTIAL.

---

## Clés cache Valkey — Référence complète

| API | Clé | TTL | Jitter |
|-----|-----|-----|--------|
| INSEE OAuth2 token | `insee:oauth2:token` | Durée token (7j) | Non |
| INSEE SIRET | `insee:siret:{siret}` | **7 jours** | ±6h (ADR-003) |
| BAN Adresse | `ban:address:{sha256(adresse)}` | **30 jours** | ±3j (ADR-003) |
| RPPS | `rpps:{numero}` | **7 jours** | ±6h (ADR-003) |

---

---

## WireMock Stubs & BDD Scénarios
> Lire **references/wiremock-stubs.md** pour tous les stubs (INSEE success/timeout/429/404, BAN success/notFound/timeout, RPPS FHIR success/notFound/timeout).
> Lire **references/bdd-scenarios.md** pour les scénarios BDD BAN + RPPS complets.

**Règle CI :** Zéro appel réseau réel — tous les appels testés via WireMock uniquement.
