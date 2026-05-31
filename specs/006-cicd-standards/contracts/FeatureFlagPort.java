package fr.docai.domain.port.out;

/**
 * Outbound port for feature flag evaluation.
 * Implementations MUST be fail-safe: any exception returns false (flag disabled).
 */
public interface FeatureFlagPort {

    /**
     * Evaluates a feature flag globally (not tenant-specific).
     *
     * @param flagName one of the 6 registered flag names
     * @return true if the flag is enabled; false if disabled or Unleash unreachable
     */
    boolean isEnabled(String flagName);

    /**
     * Evaluates a feature flag for a specific tenant.
     *
     * @param flagName one of the 6 registered flag names
     * @param tenantId the tenant identifier used for per-tenant targeting
     * @return true if the flag is enabled for this tenant; false otherwise
     */
    boolean isEnabled(String flagName, String tenantId);
}
