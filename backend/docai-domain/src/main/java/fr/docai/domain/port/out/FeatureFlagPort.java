package fr.docai.domain.port.out;

/**
 * Port for feature flag evaluation. Implementations MUST be fail-safe:
 * any exception from the underlying provider returns {@code false} (flag disabled).
 */
public interface FeatureFlagPort {

    boolean isEnabled(String flagName);

    boolean isEnabled(String flagName, String tenantId);
}
