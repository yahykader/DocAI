package fr.docai.bootstrap.feature;

import io.getunleash.DefaultUnleash;
import io.getunleash.UnleashContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// TASK-SEC-003: fail-safe contract — Unleash unavailable must never propagate an exception
@ExtendWith(MockitoExtension.class)
class UnleashFeatureFlagAdapterTest {

    @Mock
    private DefaultUnleash unleash;

    @InjectMocks
    private UnleashFeatureFlagAdapter adapter;

    @Test
    void isEnabled_returnsFalse_whenUnleashThrows() {
        when(unleash.isEnabled(anyString())).thenThrow(new RuntimeException("Unleash unavailable"));

        assertThat(adapter.isEnabled("billing.enabled")).isFalse();
        assertThatNoException().isThrownBy(() -> adapter.isEnabled("billing.enabled"));
    }

    @Test
    void isEnabled_withTenantId_returnsFalse_whenUnleashThrows() {
        when(unleash.isEnabled(anyString(), any(UnleashContext.class)))
                .thenThrow(new RuntimeException("Unleash unavailable"));

        assertThat(adapter.isEnabled("billing.enabled", "tenant-acme")).isFalse();
        assertThatNoException().isThrownBy(() -> adapter.isEnabled("billing.enabled", "tenant-acme"));
    }

    @Test
    void isEnabled_returnsTrue_whenFlagEnabled() {
        when(unleash.isEnabled("billing.enabled")).thenReturn(true);

        assertThat(adapter.isEnabled("billing.enabled")).isTrue();
    }

    @Test
    void isEnabled_withTenantId_returnsTrue_whenFlagEnabled() {
        when(unleash.isEnabled(anyString(), any(UnleashContext.class))).thenReturn(true);

        assertThat(adapter.isEnabled("fraud.v2.enabled", "tenant-acme")).isTrue();
    }
}
