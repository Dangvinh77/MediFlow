package com.mediflow.clinical.infrastructure.security;

import java.security.Principal;

/** Created only after the internal service credential and exact lookup path are verified. */
public record ClinicalServicePrincipal(String serviceName) implements Principal {
    @Override public String getName() { return serviceName; }
}
