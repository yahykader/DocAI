## WireMock Stubs — CI obligatoire (0 appel réseau)

```java
// ExternalApiStubs — dans commons-testing
public class ExternalApiStubs {

    // ── INSEE ────────────────────────────────────────────
    public static void stubInseeSuccess(WireMockServer s, String siret) {
        s.stubFor(get(urlPathMatching(".*/siret/" + siret))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(inseeActiveBody(siret))));
    }
    public static void stubInseeFerme(WireMockServer s, String siret) {
        s.stubFor(get(urlPathMatching(".*/siret/" + siret))
            .willReturn(aResponse().withStatus(200)
                .withBody(inseeFermeBody(siret))));
    }
    public static void stubInseeTimeout(WireMockServer s) {
        s.stubFor(get(urlPathMatching(".*/siret/.*"))
            .willReturn(aResponse().withFixedDelay(10_000))); // > 5s timeout
    }
    public static void stubInseeRateLimit(WireMockServer s) {
        s.stubFor(get(urlPathMatching(".*/siret/.*"))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60")));
    }
    public static void stubInseeNotFound(WireMockServer s, String siret) {
        s.stubFor(get(urlPathMatching(".*/siret/" + siret))
            .willReturn(aResponse().withStatus(404)));
    }

    // ── BAN ─────────────────────────────────────────────
    public static void stubBanSuccess(WireMockServer s, String query) {
        s.stubFor(get(urlPathMatching(".*/look4/address/search.*"))
            .willReturn(aResponse().withStatus(200)
                .withBody(banFoundBody(query, 0.95)))); // score 0.95 → VALID
    }
    public static void stubBanNotFound(WireMockServer s) {
        s.stubFor(get(urlPathMatching(".*/look4/address/search.*"))
            .willReturn(aResponse().withStatus(200)
                .withBody(banEmptyBody()))); // 0 résultats → NOT_FOUND
    }
    public static void stubBanTimeout(WireMockServer s) {
        s.stubFor(get(urlPathMatching(".*/look4/address/search.*"))
            .willReturn(aResponse().withFixedDelay(10_000)));
    }

    // ── RPPS FHIR ───────────────────────────────────────
    public static void stubRppsSuccess(WireMockServer s, String rpps) {
        s.stubFor(get(urlMatching(".*/Practitioner.*identifier=" + rpps + ".*"))
            .willReturn(aResponse().withStatus(200)
                .withBody(fhirBundleActive(rpps, "Médecin"))));
    }
    public static void stubRppsNotFound(WireMockServer s, String rpps) {
        s.stubFor(get(urlMatching(".*/Practitioner.*identifier=" + rpps + ".*"))
            .willReturn(aResponse().withStatus(200)
                .withBody(fhirBundleEmpty()))); // total: 0
    }
    public static void stubRppsTimeout(WireMockServer s) {
        s.stubFor(get(urlMatching(".*/Practitioner.*"))
            .willReturn(aResponse().withFixedDelay(10_000)));
    }
}
```

---

