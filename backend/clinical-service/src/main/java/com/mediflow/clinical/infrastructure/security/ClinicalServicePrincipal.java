package com.mediflow.clinical.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/** Created only after the internal service credential and exact lookup path are verified. */
public record ClinicalServicePrincipal(String serviceName, UUID actorStaffId, String actorRole) implements Principal {
    public ClinicalServicePrincipal(String serviceName) {
        this(serviceName, null, null);
    }

    @Override public String getName() { return serviceName; }
}
