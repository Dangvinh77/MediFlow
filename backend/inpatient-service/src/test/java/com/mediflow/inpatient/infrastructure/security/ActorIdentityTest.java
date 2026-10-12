package com.mediflow.inpatient.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ActorIdentityTest {

    private final ActorIdentity actorIdentity = new ActorIdentity();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentStaffIdReturnsExplicitJwtStaffIdentity() {
        UUID staffId = UUID.randomUUID();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "account-id", "token", java.util.List.of());
        authentication.setDetails(new InpatientAuthenticationDetails(
                "account-id", staffId, null, UUID.randomUUID()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(actorIdentity.currentStaffId()).isEqualTo(staffId);
    }

    @Test
    void currentStaffIdRejectsAuthenticationWithoutExplicitStaffClaim() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "account-id", "token", java.util.List.of());
        authentication.setDetails(new InpatientAuthenticationDetails(
                "account-id", null, null, UUID.randomUUID()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(actorIdentity::currentStaffId)
                .isInstanceOf(AccessDeniedException.class);
    }
}
