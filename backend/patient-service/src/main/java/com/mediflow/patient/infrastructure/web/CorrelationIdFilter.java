package com.mediflow.patient.infrastructure.web;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.patient.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.patient.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private final ThreadLocalCorrelationIdProvider correlationIds;

    public CorrelationIdFilter(ThreadLocalCorrelationIdProvider correlationIds) {
        this.correlationIds = correlationIds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID id = resolve(request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
        correlationIds.bind(id);
        CorrelationIdRequestAttribute.bind(request, id);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, id.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            correlationIds.clear();
        }
    }

    private UUID resolve(String raw) {
        if (raw == null || raw.isBlank()) return UUID.randomUUID();
        try {
            UUID value = UUID.fromString(raw.trim());
            return value.toString().equalsIgnoreCase(raw.trim()) ? value : UUID.randomUUID();
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }
}
