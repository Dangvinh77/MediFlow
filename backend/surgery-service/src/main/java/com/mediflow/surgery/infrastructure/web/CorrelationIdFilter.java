package com.mediflow.surgery.infrastructure.web;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
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

/** Establishes one canonical UUID correlation id for every HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        UUID correlationId = resolve(request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
        CorrelationIdRequestAttribute.bind(request, correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, correlationId.toString());
        filterChain.doFilter(request, response);
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
