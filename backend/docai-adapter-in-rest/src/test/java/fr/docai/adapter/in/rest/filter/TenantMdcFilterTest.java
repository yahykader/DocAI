package fr.docai.adapter.in.rest.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.concurrent.atomic.AtomicReference;

class TenantMdcFilterTest {

    private final TenantMdcFilter filter = new TenantMdcFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        MDC.remove(TenantMdcFilter.MDC_TENANT_KEY);
    }

    @Test
    void noAuthentication_fallsBackToUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilterInternal(request, response, (req, res) ->
            captured.set(MDC.get(TenantMdcFilter.MDC_TENANT_KEY)));

        assertEquals(TenantMdcFilter.FALLBACK_TENANT, captured.get());
    }

    @Test
    void jwtWithTenantId_setsMdcToTenantValue() throws Exception {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("tenant_id")).thenReturn("acme-corp");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<String> captured = new AtomicReference<>();
        filter.doFilterInternal(request, response, (req, res) ->
            captured.set(MDC.get(TenantMdcFilter.MDC_TENANT_KEY)));

        assertEquals("acme-corp", captured.get());
    }

    @Test
    void jwtMissingTenantIdClaim_fallsBackToUnauthenticated() throws Exception {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("tenant_id")).thenReturn(null);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<String> captured = new AtomicReference<>();
        filter.doFilterInternal(request, response, (req, res) ->
            captured.set(MDC.get(TenantMdcFilter.MDC_TENANT_KEY)));

        assertEquals(TenantMdcFilter.FALLBACK_TENANT, captured.get());
    }

    @Test
    void nonJwtPrincipal_fallsBackToUnauthenticated() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymous");
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<String> captured = new AtomicReference<>();
        filter.doFilterInternal(request, response, (req, res) ->
            captured.set(MDC.get(TenantMdcFilter.MDC_TENANT_KEY)));

        assertEquals(TenantMdcFilter.FALLBACK_TENANT, captured.get());
    }

    @Test
    void mdcClearedAfterChain() throws Exception {
        SecurityContextHolder.clearContext();
        filter.doFilterInternal(request, response, (req, res) -> {});

        assertNull(MDC.get(TenantMdcFilter.MDC_TENANT_KEY),
            "tenantId MDC key must be cleared in finally block after chain completes");
    }
}
