package com.mediflow.patient.infrastructure.correlation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class CorrelationIdRequestAttribute {
    public static final String NAME = CorrelationIdRequestAttribute.class.getName() + ".value";
    private CorrelationIdRequestAttribute() { }
    public static void bind(HttpServletRequest request, UUID value) { request.setAttribute(NAME, value); }
}
