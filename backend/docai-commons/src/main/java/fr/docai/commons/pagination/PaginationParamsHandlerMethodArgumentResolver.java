package fr.docai.commons.pagination;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Resolves {@link PaginationParams} from request parameters with structural @Valid enforcement.
 * Consumers do not need @Valid — violations are raised here before the controller runs (BR-PAG-005).
 */
public class PaginationParamsHandlerMethodArgumentResolver
        implements HandlerMethodArgumentResolver {

    private final Validator validator;

    /** Creates the resolver. Validator is injected by the WebMvcConfigurer registration. */
    public PaginationParamsHandlerMethodArgumentResolver(Validator validator) {
        this.validator = validator;
    }

    /** Handles any parameter of type {@link PaginationParams}. */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PaginationParams.class.isAssignableFrom(parameter.getParameterType());
    }

    /** Resolves and validates pagination params from the HTTP request. */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        int page = parseIntOrDefault(webRequest.getParameter("page"), 0);
        int size = parseIntOrDefault(webRequest.getParameter("size"), PaginationParams.DEFAULT_SIZE);
        String sort = firstNonNull(webRequest.getParameter("sort"), PaginationParams.DEFAULT_SORT);

        PaginationParams params = new PaginationParams(page, size, sort);
        validate(params);
        return params;
    }

    private void validate(PaginationParams params) {
        Set<ConstraintViolation<PaginationParams>> violations = validator.validate(params);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid integer parameter: '" + value.trim() + "'");
        }
    }

    private String firstNonNull(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
