package fr.docai.adapter.in.rest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Applies /v1 prefix to all controllers in the REST adapter package (C-04).
 * Excludes Actuator (port 9091) and Swagger endpoints automatically — they are on different
 * ports or paths not matched by the base-package predicate.
 * Breaking changes must introduce /v2 and maintain /v1 for 6 months (versioning policy).
 */
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    private static final String REST_BASE_PACKAGE = "fr.docai.adapter.in.rest";
    private static final String API_VERSION_PREFIX = "/v1";

    /** Prepends /v1 to all controllers in the REST adapter package. */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
            API_VERSION_PREFIX,
            clazz -> clazz.getPackageName().startsWith(REST_BASE_PACKAGE)
                && !clazz.isAnnotationPresent(
                    org.springframework.boot.autoconfigure.SpringBootApplication.class)
        );
    }
}
