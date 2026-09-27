package com.mediflow.lab.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;

class AuthenticatedStaffContextAdapterTest {

    private final AuthenticatedStaffContextAdapter adapter = new AuthenticatedStaffContextAdapter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentStaff_readsIdentityAndRoleFromVerifiedPrincipal() {
        UUID staffId = UUID.randomUUID();
        LabAuthenticatedPrincipal principal = new LabAuthenticatedPrincipal("account-1", staffId, "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(adapter.currentStaff()).contains(new AuthenticatedStaffContext(staffId, "ADMIN"));
    }

    @Test
    void currentStaff_withoutExplicitStaffIdIsEmpty() {
        LabAuthenticatedPrincipal principal = new LabAuthenticatedPrincipal("account-1", null, "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(adapter.currentStaff()).isEmpty();
    }

    @Test
    void currentStaff_withPrincipalAndAuthorityRoleMismatchIsEmpty() {
        UUID staffId = UUID.randomUUID();
        LabAuthenticatedPrincipal principal = new LabAuthenticatedPrincipal("account-1", staffId, "LAB_TECH");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(adapter.currentStaff()).isEmpty();
    }
}
