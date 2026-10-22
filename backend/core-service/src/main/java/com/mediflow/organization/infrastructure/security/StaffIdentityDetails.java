package com.mediflow.organization.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import java.util.UUID;

/** Identity copied only from a locally verified access token. */
public final class StaffIdentityDetails extends WebAuthenticationDetails {
    private final UUID staffId;

    public StaffIdentityDetails(HttpServletRequest request, String staffId) {
        super(request);
        this.staffId = staffId == null ? null : UUID.fromString(staffId);
    }

    public UUID getStaffId() { return staffId; }
}
