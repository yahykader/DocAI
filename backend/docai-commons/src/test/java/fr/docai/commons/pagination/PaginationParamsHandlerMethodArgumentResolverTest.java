package fr.docai.commons.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

class PaginationParamsHandlerMethodArgumentResolverTest {

    private Validator validator;
    private PaginationParamsHandlerMethodArgumentResolver resolver;
    private MethodParameter parameter;
    private NativeWebRequest webRequest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        validator = mock(Validator.class);
        resolver = new PaginationParamsHandlerMethodArgumentResolver(validator);
        parameter = mock(MethodParameter.class);
        webRequest = mock(NativeWebRequest.class);
        when(validator.validate(any())).thenReturn(Set.of());
    }

    @Test
    void defaultsApplied_whenParamsAbsent() throws Exception {
        when(webRequest.getParameter("page")).thenReturn(null);
        when(webRequest.getParameter("size")).thenReturn(null);
        when(webRequest.getParameter("sort")).thenReturn(null);

        PaginationParams result = (PaginationParams) resolver.resolveArgument(
            parameter, mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertEquals(0, result.page());
        assertEquals(PaginationParams.DEFAULT_SIZE, result.size());
        assertEquals(PaginationParams.DEFAULT_SORT, result.sort());
    }

    @Test
    void invalidIntegerForPage_returnsBadRequest() {
        when(webRequest.getParameter("page")).thenReturn("abc");
        when(webRequest.getParameter("size")).thenReturn(null);
        when(webRequest.getParameter("sort")).thenReturn(null);

        assertThrows(ResponseStatusException.class, () ->
            resolver.resolveArgument(
                parameter, mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sizeExceedingMax_throwsConstraintViolation() throws Exception {
        when(webRequest.getParameter("page")).thenReturn(null);
        when(webRequest.getParameter("size")).thenReturn("101");
        when(webRequest.getParameter("sort")).thenReturn(null);

        ConstraintViolation violation = mock(ConstraintViolation.class);
        doReturn(Set.of(violation)).when(validator).validate(any());

        assertThrows(ConstraintViolationException.class, () ->
            resolver.resolveArgument(
                parameter, mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class)));
    }

    @Test
    void validParams_passedThrough() throws Exception {
        when(webRequest.getParameter("page")).thenReturn("2");
        when(webRequest.getParameter("size")).thenReturn("50");
        when(webRequest.getParameter("sort")).thenReturn("updatedAt,asc");

        PaginationParams result = (PaginationParams) resolver.resolveArgument(
            parameter, mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertEquals(2, result.page());
        assertEquals(50, result.size());
        assertEquals("updatedAt,asc", result.sort());
    }

    @Test
    void supportsParameter_trueForPaginationParams() {
        doReturn(PaginationParams.class).when(parameter).getParameterType();
        assertTrue(resolver.supportsParameter(parameter));
    }

    @Test
    void supportsParameter_falseForOtherTypes() {
        doReturn(String.class).when(parameter).getParameterType();
        assertFalse(resolver.supportsParameter(parameter));
    }
}
