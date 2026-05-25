package fr.docai.domain.port.out;

public interface FeatureFlagPort {

  boolean isEnabled(String featureName, String tenantId);

  boolean isEnabled(String featureName);

  void setEnabled(String featureName, boolean enabled);

  record FeatureConfig(
      String name,
      boolean enabled,
      String tenantId) {}
}
