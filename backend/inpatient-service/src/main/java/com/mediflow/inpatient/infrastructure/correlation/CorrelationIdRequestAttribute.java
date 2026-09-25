package com.mediflow.inpatient.infrastructure.correlation;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

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
