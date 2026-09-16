package com.mediflow.clinical.infrastructure.web;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mediflow.clinical.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.common.security.JwtClaims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Establishes and propagates one UUID correlation id for every HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final ThreadLocalCorrelationIdProvider correlationIds;

    public CorrelationIdFilter(ThreadLocalCorrelationIdProvider correlationIds) {
        this.correlationIds = correlationIds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        UUID correlationId = resolve(request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
        CorrelationIdRequestAttribute.bind(request, correlationId);
        correlationIds.bind(correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, correlationId.toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            correlationIds.clear();
        }
    }

    private UUID resolve(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        String trimmed = value.trim();
        try {
            UUID correlationId = UUID.fromString(trimmed);
            return correlationId.toString().equalsIgnoreCase(trimmed)
                    ? correlationId
                    : UUID.randomUUID();
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }
}
