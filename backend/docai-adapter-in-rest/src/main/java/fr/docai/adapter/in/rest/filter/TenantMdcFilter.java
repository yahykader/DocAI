package fr.docai.adapter.in.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Injects tenant_id JWT claim into MDC as 'tenantId' for every request (FR-OBS-002).
 * Falls back to "UNAUTHENTICATED" when no valid JWT is present (pre-auth, OPTIONS, health).
 * traceId is injected automatically by the OpenTelemetry MDC bridge — not set here.
 */
@Component
public class TenantMdcFilter extends OncePerRequestFilter {

    static final String MDC_TENANT_KEY = "tenantId";
    static final String FALLBACK_TENANT = "UNAUTHENTICATED";

    /** Injects tenantId MDC key then clears it after the filter chain completes. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        MDC.put(MDC_TENANT_KEY, resolveTenantId());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT_KEY);
        }
    }

    private String resolveTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId;
            }
        }
        return FALLBACK_TENANT;
    }
}
