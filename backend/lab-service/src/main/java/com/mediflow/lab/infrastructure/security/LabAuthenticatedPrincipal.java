package com.mediflow.lab.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/** Authenticated account identity with an optional, explicit staff claim. */
public record LabAuthenticatedPrincipal(String accountId, UUID staffId, String role, UUID patientId, UUID departmentId)
        implements Principal {
    public LabAuthenticatedPrincipal(String accountId, UUID staffId, String role) {
        this(accountId, staffId, role, null, null);
    }

    public LabAuthenticatedPrincipal(String accountId, UUID staffId, String role, UUID patientId) {
        this(accountId, staffId, role, patientId, null);
    }

    @Override
    public String getName() {
        return accountId;
    }
}
