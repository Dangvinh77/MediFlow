package com.mediflow.organization.infrastructure.correlation;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Stores the canonical correlation id in the servlet request for propagators. */
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
