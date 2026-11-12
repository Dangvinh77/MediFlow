package com.mediflow.lab.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;

/** Reads the human reader copied from the verified JWT into the principal. */
@Component
public class CurrentReaderAdapter implements CurrentReaderPort {

    @Override
    public Optional<ReaderIdentity> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LabAuthenticatedPrincipal principal)
                || principal.role() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ReaderIdentity(UUID.fromString(principal.accountId()), principal.staffId(),
                    principal.role(), principal.departmentId()));
        } catch (IllegalArgumentException notAnAccountId) {
            return Optional.empty();
        }
    }
}
