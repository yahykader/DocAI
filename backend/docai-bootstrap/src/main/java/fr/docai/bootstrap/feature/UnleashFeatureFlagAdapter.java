package fr.docai.bootstrap.feature;

import fr.docai.domain.port.out.FeatureFlagPort;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Fail-safe: any Exception from the Unleash SDK returns false — callers must never see an exception (TASK-SEC-003)
@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private static final Logger log = LoggerFactory.getLogger(UnleashFeatureFlagAdapter.class);

    private final Unleash unleash;

    public UnleashFeatureFlagAdapter(Unleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        try {
            return unleash.isEnabled(flagName);
        } catch (Exception e) {
            log.warn("Unleash unavailable for flag {} — failing safe to false", flagName, e);
            return false;
        }
    }

    @Override
    public boolean isEnabled(String flagName, String tenantId) {
        try {
            // tenantId maps to Unleash userId dimension — used by tenant-scoped gradual rollout strategies
            UnleashContext context = UnleashContext.builder().userId(tenantId).build();
            return unleash.isEnabled(flagName, context);
        } catch (Exception e) {
            log.warn("Unleash unavailable for flag {} (tenantId={}) — failing safe to false", flagName, tenantId, e);
            return false;
        }
    }
}
