package com.mediflow.lab.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/** Authenticated account identity with an optional, explicit staff claim. */
public record LabAuthenticatedPrincipal(String accountId, UUID staffId) implements Principal {

    @Override
    public String getName() {
        return accountId;
    }
}
