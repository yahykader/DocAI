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
}
