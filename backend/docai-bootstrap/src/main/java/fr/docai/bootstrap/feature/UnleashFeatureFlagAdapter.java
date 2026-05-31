package fr.docai.bootstrap.feature;

import fr.docai.domain.port.out.FeatureFlagPort;
import io.getunleash.DefaultUnleash;
import io.getunleash.UnleashContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Fail-safe: any exception from the Unleash SDK returns false — callers must never see an exception (TASK-SEC-003)
@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private static final Logger log = LoggerFactory.getLogger(UnleashFeatureFlagAdapter.class);

    private final DefaultUnleash unleash;

    public UnleashFeatureFlagAdapter(DefaultUnleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        try {
            return unleash.isEnabled(flagName);
        } catch (Exception e) {
            log.warn("Unleash unavailable for flag {}", flagName);
            return false;
        }
    }

    @Override
    public boolean isEnabled(String flagName, String tenantId) {
        try {
            UnleashContext context = UnleashContext.builder().userId(tenantId).build();
            return unleash.isEnabled(flagName, context);
        } catch (Exception e) {
            log.warn("Unleash unavailable for flag {} (tenantId={})", flagName, tenantId);
            return false;
        }
    }
}
