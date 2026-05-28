package fr.docai.bootstrap.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for PiiMaskingConverter regex patterns (FR-OBS-003).
 * T002 — fails until T005 (PiiMaskingConverter) is created.
 */
class PiiMaskingConverterTest {

    private final PiiMaskingConverter converter = new PiiMaskingConverter();

    @Test
    void emailMasked() {
        String result = converter.mask("Contact: user@example.com for info");
        assertEquals("Contact: [PII_MASKED] for info", result,
            "Email address must be replaced with [PII_MASKED] (FR-OBS-003)");
    }

    @Test
    void siretPartiallyMasked() {
        String result = converter.mask("SIRET: 12345678901234");
        assertEquals("SIRET: [PARTIAL_MASK]", result,
            "14-digit SIRET must be replaced with [PARTIAL_MASK] (FR-OBS-003)");
    }

    @Test
    void ibanMasked() {
        String result = converter.mask("IBAN: FR7614508080000000000000000");
        assertEquals("IBAN: [PII_MASKED]", result,
            "IBAN must be replaced with [PII_MASKED] (FR-OBS-003)");
    }

    @Test
    void phoneMasked() {
        String result = converter.mask("Tel: 0612345678");
        assertEquals("Tel: [PII_MASKED]", result,
            "French phone number (10 digits starting 06/07) must be masked (FR-OBS-003)");
    }

    @Test
    void nestedJsonEmailMasked() {
        String json = "{\"address\":{\"email\":\"test@corp.fr\"}}";
        String result = converter.mask(json);
        assertFalse(result.contains("test@corp.fr"),
            "Email inside nested JSON value must also be masked (FR-OBS-003 recursive)");
    }
}
