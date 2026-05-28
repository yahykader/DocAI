package fr.docai.bootstrap.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback converter masking PII in log messages (FR-OBS-003).
 * Email/IBAN/phone → [PII_MASKED] ; SIRET (14 digits) → [PARTIAL_MASK].
 * Registered in logback-spring.xml via {@code <conversionRule>} as "maskedMessage".
 */
public class PiiMaskingConverter extends ClassicConverter {

    private static final Pattern EMAIL =
        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern SIRET = Pattern.compile("\\b\\d{14}\\b");
    // Possessive quantifier {10,30}+ prevents catastrophic backtracking on adversarial input
    private static final Pattern IBAN =
        Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}+\\b");
    private static final Pattern PHONE =
        Pattern.compile("\\b0[67]\\d{8}\\b");

    /** Returns the PII-masked log message; never null (Logback contract). */
    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        return msg != null ? mask(msg) : "";
    }

    /**
     * Applies all PII masking patterns to the input string.
     *
     * @param input raw log message or JSON string
     * @return input with all PII replaced by masking tokens, never null
     */
    public String mask(String input) {
        if (input == null) {
            return "";
        }
        String result = EMAIL.matcher(input).replaceAll("[PII_MASKED]");
        result = SIRET.matcher(result).replaceAll("[PARTIAL_MASK]");
        result = IBAN.matcher(result).replaceAll("[PII_MASKED]");
        result = PHONE.matcher(result).replaceAll("[PII_MASKED]");
        return result;
    }
}
