package fr.docai.bootstrap.config;

import io.getunleash.DefaultUnleash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnleashConfig {

    @Bean
    public DefaultUnleash unleash(
            @Value("${docai.unleash.app-name}") String appName,
            @Value("${docai.unleash.url}") String url,
            @Value("${docai.unleash.api-token}") String apiToken) {
        // Use fully qualified name to avoid clash with this class name
        io.getunleash.util.UnleashConfig config = io.getunleash.util.UnleashConfig.newConfig()
                .appName(appName)
                .instanceId("docai-backend")
                .unleashAPI(url + "/api")
                .apiKey(apiToken)
                .build();
        return new DefaultUnleash(config);
    }
}
