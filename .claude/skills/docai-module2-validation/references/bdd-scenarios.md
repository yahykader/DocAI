## Scénarios BDD complets

```gherkin
# ── BAN ──────────────────────────────────────────────────────────────────

Scenario: Adresse reconnue par la BAN — validation VALID
  Given document FACTURE avec adresse "15 Rue de la Paix 75001 Paris"
  And la BAN retourne un résultat avec score 0.95 (WireMock)
  When la validation adresse s'exécute
  Then AddressValidationResult.isRecognized = true
  And confidenceScore = 0.95
  And aucun signal fraude n'est levé pour l'adresse
  And le résultat est mis en cache Valkey (TTL 30j)

Scenario: Adresse inconnue de la BAN — WARNING non-bloquant
  Given document FACTURE avec adresse "1 Rue Imaginaire 99999 Nulle Part"
  And la BAN retourne 0 résultat (WireMock)
  When la validation adresse s'exécute
  Then AddressValidationResult.isRecognized = false (NOT_FOUND)
  And la validation est WARNING seulement (pas BLOQUANT — DEC-EXT-003)
  And le pipeline continue sans exception

Scenario: BAN down + cache vide — fail-open non-bloquant
  Given aucun cache BAN pour cette adresse
  And la BAN est indisponible (WireMock timeout)
  When la validation s'exécute
  Then AddressValidationResult.unverified() est retourné
  And le flag VALIDATION_PARTIAL est ajouté au rapport

# ── RPPS FHIR ────────────────────────────────────────────────────────────

Scenario: RPPS valide — médecin actif confirmé
  Given document ORDONNANCE avec RPPS "10003456789"
  And l'API FHIR ANS retourne un médecin actif (WireMock)
  When la validation RPPS s'exécute
  Then RppsValidationResult.isActive = true, profession = "Médecin"
  And aucun signal fraude n'est levé
  And le résultat est mis en cache Valkey (TTL 7j)

Scenario: RPPS inconnu — signal fraude BLOQUANT
  Given document ORDONNANCE avec RPPS "00000000001"
  And l'API FHIR ANS retourne 0 résultat (bundle.total = 0)
  When la validation RPPS s'exécute
  Then le signal DATA_RPPS_INVALID est levé (poids 35)
  And RppsValidationResult.isActive = false

Scenario: API FHIR ANS down + cache vide — WARNING dégradé
  Given aucun cache RPPS pour "10003456789"
  And l'API FHIR ANS est indisponible (WireMock timeout)
  When la validation s'exécute
  Then RPPS_UNVERIFIED est retourné (WARNING dégradé, pas BLOQUANT)
  And le pipeline continue

Scenario: Mode LOCAL — swap transparent RPPS
  Given la configuration docai.rpps.mode = LOCAL
  When la validation RPPS s'exécute sur "10003456789"
  Then RppsLocalFileAdapter est utilisé (MongoDB local, 0 appel réseau)
  And le résultat est identique au mode API (même Value Object domaine)
```
