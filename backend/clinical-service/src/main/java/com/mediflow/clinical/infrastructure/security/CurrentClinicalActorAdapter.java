package com.mediflow.clinical.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;

@Component
public class CurrentClinicalActorAdapter implements CurrentClinicalActorPort {
    @Override
    public ActorIdentity current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
        if (authentication.getPrincipal() instanceof ClinicalPrincipal principal) {
            return new ActorIdentity(principal.staffId(), principal.role());
        }
        return new ActorIdentity(null, role);
    }
}
