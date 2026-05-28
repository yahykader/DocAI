package fr.docai.bootstrap.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Verifies logback-spring.xml: JSON encoder configured, MDC fields present (FR-OBS-002/004).
 * T001 — fails until T004 (logback-spring.xml) is created.
 */
class LogbackJsonConfigTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void logbackSpringXmlExists() {
        InputStream stream =
            getClass().getClassLoader().getResourceAsStream("logback-spring.xml");
        assertNotNull(stream,
            "logback-spring.xml must exist in docai-bootstrap/src/main/resources (T004)");
    }

    @Test
    void logbackSpringXmlContainsLogstashEncoder() throws Exception {
        InputStream stream =
            getClass().getClassLoader().getResourceAsStream("logback-spring.xml");
        assertNotNull(stream, "logback-spring.xml must exist before checking encoder");
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("LogstashEncoder"),
            "logback-spring.xml must reference LogstashEncoder for JSON (staging profile)");
    }

    @Test
    void logbackSpringXmlIncludesMdcFields() throws Exception {
        InputStream stream =
            getClass().getClassLoader().getResourceAsStream("logback-spring.xml");
        assertNotNull(stream, "logback-spring.xml must exist before checking MDC fields");
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("traceId"),
            "logback-spring.xml must reference 'traceId' MDC key (FR-OBS-002)");
        assertTrue(content.contains("tenantId"),
            "logback-spring.xml must reference 'tenantId' MDC key (FR-OBS-002)");
    }

    @Test
    void logbackSpringXmlContainsPiiMaskingPatterns() throws Exception {
        InputStream stream =
            getClass().getClassLoader().getResourceAsStream("logback-spring.xml");
        assertNotNull(stream, "logback-spring.xml must exist before checking PII patterns");
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("MaskingJsonGeneratorDecorator"),
            "logback-spring.xml must use MaskingJsonGeneratorDecorator for PII masking (FR-OBS-003)");
        assertTrue(content.contains("[PII_MASKED]"),
            "logback-spring.xml must define [PII_MASKED] mask for email, IBAN, phone");
        assertTrue(content.contains("[PARTIAL_MASK]"),
            "logback-spring.xml must define [PARTIAL_MASK] mask for SIRET");
    }

    @Test
    void mdcFieldsAttachedToEveryLogEvent() {
        MDC.put("traceId", "trace-test-abc");
        MDC.put("tenantId", "acme-corp");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)
            LoggerFactory.getLogger(LogbackJsonConfigTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        testLogger.addAppender(appender);
        testLogger.info("mdc-field-test");
        testLogger.detachAppender(appender);

        assertFalse(appender.list.isEmpty(), "At least one log event must be captured");
        ILoggingEvent event = appender.list.get(0);
        assertTrue(event.getMDCPropertyMap().containsKey("traceId"),
            "traceId must be present in every log event MDC (FR-OBS-002)");
        assertTrue(event.getMDCPropertyMap().containsKey("tenantId"),
            "tenantId must be present in every log event MDC (FR-OBS-002)");
    }

    @Test
    void debugLevelDisabledInNonLocalProfiles() throws Exception {
        InputStream stream =
            getClass().getClassLoader().getResourceAsStream("logback-spring.xml");
        assertNotNull(stream, "logback-spring.xml must exist");
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        // Extract staging/prod profile section
        String stagingProdSection = content.substring(
            content.indexOf("<springProfile name=\"staging,prod\">"),
            content.indexOf("</springProfile>", content.indexOf("<springProfile name=\"staging,prod\"")));

        assertTrue(stagingProdSection.contains("level=\"INFO\""),
            "Staging/prod profile must set root level to INFO (FR-OBS-004)");
        assertTrue(stagingProdSection.contains("<logger name=\"fr.docai\" level=\"INFO\"/>"),
            "Staging/prod profile must set fr.docai logger to INFO, never DEBUG (FR-OBS-004)");
    }
}
