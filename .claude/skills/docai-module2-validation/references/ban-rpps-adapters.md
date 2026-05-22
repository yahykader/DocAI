# DocAI — BanApiAdapter & RppsApiAdapter (Code complet)

> Référence : EXTERNAL_APIS_SPECKIT.md + DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0

---

## BanApiAdapter

```java
// Module : docai-adapter-out-external
// Package : fr.docai.adapter.out.external.ban

@Component
public class BanApiAdapter implements AddressValidatorPort {

    // URL officielle Géoplateforme IGN (depuis jan 2026 — remplace api-adresse.data.gouv.fr)
    private static final String BAN_URL =
        "https://geocodage.ign.fr/look4/address/search?q={query}&limit=1";

    private final WebClient webClient;
    private final StringRedisTemplate valkey;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "ban", fallbackMethod = "failOpen")
    @TimeLimiter(name = "ban")   // 5s max
    @Override
    public AddressValidationResult validateAddress(String address) {
        // Clé cache = SHA-256 de l'adresse normalisée (minuscule, espaces réduits)
        String normalized = address.toLowerCase().replaceAll("\\s+", " ").trim();
        String cacheKey = "ban:address:" + sha256(normalized);

        // 1. Cache Valkey d'abord
        String cached = valkey.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("BAN cache hit address=[MASKED]");
            return objectMapper.readValue(cached, AddressValidationResult.class);
        }

        // 2. Appel API Géoplateforme IGN
        BanResponse response = webClient.get()
            .uri(BAN_URL, URLEncoder.encode(address, StandardCharsets.UTF_8))
            .retrieve()
            .bodyToMono(BanResponse.class)
            .block(Duration.ofSeconds(5));

        // 3. Mapper vers Value Object domaine (ACL)
        AddressValidationResult result;
        if (response != null && !response.getFeatures().isEmpty()) {
            BanFeature feature = response.getFeatures().get(0);
            double score = feature.getProperties().getScore();
            result = new AddressValidationResult(
                address,
                score > 0.5,                                    // isRecognized
                feature.getProperties().getLabel(),             // normalizedAddress
                score,                                          // confidenceScore
                ValidationSource.API
            );
        } else {
            // Adresse inconnue de la BAN → NOT_FOUND (WARNING seulement — DEC-EXT-003)
            result = AddressValidationResult.notFound(address);
        }

        // 4. Mettre en cache — TTL 30 jours ± jitter (adresses stables — ADR-003)
        Duration ttl = JitterTtl.withJitter(Duration.ofDays(30));
        valkey.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), ttl);

        log.info("BAN validation API hit address=[MASKED] recognized={} score={}",
            result.isRecognized(), result.confidenceScore());
        return result;
    }

    // Fail-open : BAN down → WARNING non-bloquant (DEC-EXT-003)
    public AddressValidationResult failOpen(String address, Exception e) {
        log.warn("BAN API unavailable — fail-open address=[MASKED] reason={}",
            e.getClass().getSimpleName());
        return AddressValidationResult.unverified(address);
    }

    private String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                                       .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

---

## RppsApiAdapter (mode API — défaut)

```java
// Module : docai-adapter-out-external
// Package : fr.docai.adapter.out.external.rpps

@Component
@ConditionalOnProperty(name = "docai.rpps.mode", havingValue = "API", matchIfMissing = true)
public class RppsApiAdapter implements RppsValidatorPort {

    // URL API FHIR ANS (Annuaire Santé)
    private static final String RPPS_URL =
        "https://gateway.api.esante.gouv.fr/fhir/v1/Practitioner?identifier={rpps}&_format=json";

    private final WebClient webClient;
    private final StringRedisTemplate valkey;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "rpps", fallbackMethod = "failOpen")
    @TimeLimiter(name = "rpps")  // 5s max
    @Override
    public RppsValidationResult validateRpps(String rppsNumber) {
        // 1. Vérification format local (11 chiffres — avant tout appel API)
        if (rppsNumber == null || !rppsNumber.matches("\\d{11}")) {
            return RppsValidationResult.invalid(rppsNumber, "RPPS_FORMAT_INVALID");
        }

        // 2. Cache Valkey d'abord
        String cacheKey = "rpps:" + rppsNumber;
        String cached = valkey.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("RPPS cache hit rpps=[MASKED]");
            return objectMapper.readValue(cached, RppsValidationResult.class);
        }

        // 3. Appel API FHIR ANS
        FhirBundle bundle = webClient.get()
            .uri(RPPS_URL, rppsNumber)
            .header("Accept", "application/fhir+json")
            .retrieve()
            .bodyToMono(FhirBundle.class)
            .block(Duration.ofSeconds(5));

        // 4. Mapper vers Value Object domaine (ACL — pas de FHIR dans le domaine)
        RppsValidationResult result;
        if (bundle != null && bundle.getTotal() > 0) {
            FhirPractitioner practitioner = bundle.getEntry().get(0).getResource();
            String lastName = extractLastName(practitioner);
            String profession = extractProfession(practitioner);
            result = RppsValidationResult.active(rppsNumber, profession, lastName);
        } else {
            // RPPS inconnu → signal DATA_RPPS_INVALID (poids 35, BLOQUANT)
            result = RppsValidationResult.notFound(rppsNumber);
        }

        // 5. Cache TTL 7 jours ± jitter (ADR-003)
        Duration ttl = JitterTtl.withJitter(Duration.ofDays(7));
        valkey.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), ttl);

        log.info("RPPS validation API hit rpps=[MASKED] active={} profession={}",
            result.isActive(), result.profession());
        return result;
    }

    // Fail-open : API FHIR ANS down → WARNING dégradé (pas BLOQUANT)
    public RppsValidationResult failOpen(String rppsNumber, Exception e) {
        // Vérifier si cache stale disponible
        String cacheKey = "rpps:" + rppsNumber;
        String stale = getStaleCache(cacheKey);
        if (stale != null) {
            log.warn("RPPS API unavailable — stale cache used rpps=[MASKED]");
            return objectMapper.readValue(stale, RppsValidationResult.class);
        }
        log.warn("RPPS API unavailable — fail-open RPPS_UNVERIFIED rpps=[MASKED]");
        return RppsValidationResult.unverified(rppsNumber);
    }

    private String extractLastName(FhirPractitioner practitioner) {
        return practitioner.getName().stream()
            .filter(n -> "usual".equals(n.getUse()))
            .findFirst()
            .map(FhirHumanName::getFamily)
            .orElse("UNKNOWN");
    }

    private String extractProfession(FhirPractitioner practitioner) {
        return practitioner.getQualification().stream()
            .findFirst()
            .map(q -> q.getCode().getText())
            .orElse("UNKNOWN");
    }
}
```

---

## RppsLocalFileAdapter (mode LOCAL)

```java
// Activé via : docai.rpps.mode=LOCAL
// Swap transparent : même interface RppsValidatorPort, même Value Object domaine

@Component
@ConditionalOnProperty(name = "docai.rpps.mode", havingValue = "LOCAL")
public class RppsLocalFileAdapter implements RppsValidatorPort {

    // Données chargées depuis MongoDB (refresh mensuel — job @Scheduled lundi 3h UTC)
    // Collection : rpps_practitioners (indexée sur rppsNumber)
    // Avantages : 0 appel réseau, 0 rate limit, requêtes instantanées
    // Inconvénient : données potentiellement en retard de 1 mois max

    @Override
    public RppsValidationResult validateRpps(String rppsNumber) {
        Optional<RppsPractitionerDocument> entry =
            rppsPractitionerRepository.findByRppsNumber(rppsNumber);

        if (entry.isEmpty()) {
            return RppsValidationResult.notFound(rppsNumber);
        }

        RppsPractitionerDocument doc = entry.get();
        return doc.isActive()
            ? RppsValidationResult.active(rppsNumber, doc.getProfession(), doc.getLastName())
            : RppsValidationResult.inactive(rppsNumber);
    }
}
```

---

## Resilience4j — application.yml (partie APIs externes)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      ban:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      rpps:
        sliding-window-size: 8
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s

  timelimiter:
    instances:
      ban:
        timeout-duration: 5s
      rpps:
        timeout-duration: 5s

  retry:
    instances:
      ban:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
      rpps:
        max-attempts: 2
        wait-duration: 1s
        enable-exponential-backoff: false
```
