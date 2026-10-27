package com.mediflow.lab.infrastructure.security;
import com.mediflow.lab.application.port.out.CurrentPatientIdentityPort;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

@Component
public class CurrentPatientIdentityAdapter implements CurrentPatientIdentityPort {
    @Override public UUID patientId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof LabAuthenticatedPrincipal principal
                && "PATIENT".equals(principal.role())) return principal.patientId();
        return null;
    }
}
