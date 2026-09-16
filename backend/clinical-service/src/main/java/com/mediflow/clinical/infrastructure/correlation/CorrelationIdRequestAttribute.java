package com.mediflow.clinical.infrastructure.correlation;

import java.util.UUID;

import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/** Stores the canonical correlation id where request context propagators can carry it. */
public final class CorrelationIdRequestAttribute {

    public static final String NAME = CorrelationIdRequestAttribute.class.getName() + ".value";

    private CorrelationIdRequestAttribute() {
    }

    public static void bind(HttpServletRequest request, UUID correlationId) {
        request.setAttribute(NAME, correlationId);
    }

    public static UUID read(ServletRequestAttributes attributes) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getRequest().getAttribute(NAME);
        return value instanceof UUID correlationId ? correlationId : null;
    }
}
