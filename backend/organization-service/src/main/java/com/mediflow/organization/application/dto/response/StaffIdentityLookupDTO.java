package com.mediflow.organization.application.dto.response;

import java.util.UUID;
import java.util.List;

/**
 * Additive staff identity projection for authenticated service-to-service
 * lookups. This is deliberately separate from the doctor eligibility contract
 * exposed by {@code /staff/{id}/exists}.
 */
public record StaffIdentityLookupDTO(
        boolean exists,
        boolean active,
        String jobTitle,
        UUID departmentId,
        List<String> eligibleTeamRoles) {

    public StaffIdentityLookupDTO(boolean exists, boolean active, String jobTitle,
                                  UUID departmentId) {
        this(exists, active, jobTitle, departmentId, List.of());
    }

    public StaffIdentityLookupDTO {
        eligibleTeamRoles = eligibleTeamRoles == null ? List.of() : List.copyOf(eligibleTeamRoles);
        if (!exists && (!eligibleTeamRoles.isEmpty() || active || jobTitle != null
                || departmentId != null)) {
            throw new IllegalArgumentException("Missing staff cannot expose identity or team roles");
        }
        if (!active && !eligibleTeamRoles.isEmpty()) {
            throw new IllegalArgumentException("Inactive staff cannot expose eligible team roles");
        }
    }

    public static StaffIdentityLookupDTO missing() {
        return new StaffIdentityLookupDTO(false, false, null, null, List.of());
    }
}
