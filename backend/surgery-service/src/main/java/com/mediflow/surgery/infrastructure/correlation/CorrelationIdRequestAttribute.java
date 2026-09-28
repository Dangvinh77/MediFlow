package com.mediflow.surgery.infrastructure.correlation;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** Stores the validated correlation id on the current servlet request. */
public final class CorrelationIdRequestAttribute {

    private static final String ATTRIBUTE = CorrelationIdRequestAttribute.class.getName();

    private CorrelationIdRequestAttribute() {
    }

    public static void bind(HttpServletRequest request, UUID correlationId) {
        request.setAttribute(ATTRIBUTE, correlationId);
    }

    public static UUID read(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof UUID correlationId ? correlationId : null;
    }
}
