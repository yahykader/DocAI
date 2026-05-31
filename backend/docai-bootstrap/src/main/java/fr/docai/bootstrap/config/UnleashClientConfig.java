package fr.docai.bootstrap.config;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnleashClientConfig {

    @Bean
    public Unleash unleash(
            @Value("${docai.unleash.app-name}") String appName,
            @Value("${docai.unleash.url}") String url,
            @Value("${docai.unleash.api-token}") String apiToken) {
        UnleashConfig config = UnleashConfig.newConfig()
                .appName(appName)
                .instanceId("docai-backend")
                .unleashAPI(url + "/api")
                .apiKey(apiToken)
                .build();
        return new DefaultUnleash(config);
    }
}
