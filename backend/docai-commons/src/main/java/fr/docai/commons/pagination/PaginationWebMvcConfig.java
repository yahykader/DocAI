package fr.docai.commons.pagination;

import jakarta.validation.Validator;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers {@link PaginationParamsHandlerMethodArgumentResolver} with Spring MVC (BR-PAG-005).
 * Without this registration the @Min/@Max constraints on PaginationParams are never enforced.
 */
@Configuration
public class PaginationWebMvcConfig implements WebMvcConfigurer {

    private final Validator validator;

    public PaginationWebMvcConfig(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PaginationParamsHandlerMethodArgumentResolver(validator));
    }
}
