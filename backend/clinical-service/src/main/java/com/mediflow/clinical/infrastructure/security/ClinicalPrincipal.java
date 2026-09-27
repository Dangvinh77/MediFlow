package com.mediflow.clinical.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

public record ClinicalPrincipal(String subject, String role, UUID staffId) implements Principal {
    @Override
    public String getName() {
        return subject;
    }
}
