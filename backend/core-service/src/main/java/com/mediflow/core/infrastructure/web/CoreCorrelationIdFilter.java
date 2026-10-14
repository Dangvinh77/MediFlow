package com.mediflow.core.infrastructure.web;

import com.mediflow.common.security.JwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gives both co-hosted contexts the same request correlation ID. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CoreCorrelationIdFilter extends OncePerRequestFilter {

    private final com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider organizationIds;
    private final com.mediflow.patient.infrastructure.correlation.ThreadLocalCorrelationIdProvider patientIds;

    public CoreCorrelationIdFilter(
            com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider organizationIds,
            com.mediflow.patient.infrastructure.correlation.ThreadLocalCorrelationIdProvider patientIds) {
        this.organizationIds = organizationIds;
        this.patientIds = patientIds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID id = resolve(request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
        organizationIds.bind(id);
        patientIds.bind(id);
        com.mediflow.organization.infrastructure.correlation.CorrelationIdRequestAttribute.bind(request, id);
        com.mediflow.patient.infrastructure.correlation.CorrelationIdRequestAttribute.bind(request, id);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, id.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            patientIds.clear();
            organizationIds.clear();
        }
    }

    private UUID resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            String value = raw.trim();
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value) ? parsed : UUID.randomUUID();
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }
}
