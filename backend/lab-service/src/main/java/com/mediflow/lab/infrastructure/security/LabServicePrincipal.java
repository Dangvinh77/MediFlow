package com.mediflow.lab.infrastructure.security;

import java.security.Principal;

public record LabServicePrincipal(String subject) implements Principal {
    @Override public String getName() { return subject; }
}
