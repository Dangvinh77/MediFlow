package com.mediflow.clinical.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

public record ClinicalPrincipal(String subject, String role, UUID staffId, UUID patientId) implements Principal {
    public ClinicalPrincipal(String subject, String role, UUID staffId) {
        this(subject, role, staffId, null);
    }

    @Override
    public String getName() {
        return subject;
    }
}
