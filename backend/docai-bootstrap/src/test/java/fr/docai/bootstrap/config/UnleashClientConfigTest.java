package fr.docai.bootstrap.config;

import io.getunleash.Unleash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnleashClientConfigTest {

    @Test
    void unleashBean_isCreated_withValidProperties() {
        var config = new UnleashClientConfig();
        Unleash bean = config.unleash("docai-backend", "http://localhost:4242", "test-token");
        assertThat(bean).isNotNull();
    }
}
