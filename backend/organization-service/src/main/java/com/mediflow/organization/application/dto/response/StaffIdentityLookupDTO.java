package com.mediflow.organization.application.dto.response;

import java.util.UUID;

/**
 * Additive staff identity projection for authenticated service-to-service
 * lookups. This is deliberately separate from the doctor eligibility contract
 * exposed by {@code /staff/{id}/exists}.
 */
public record StaffIdentityLookupDTO(
        boolean exists,
        boolean active,
        String jobTitle,
        UUID departmentId) {

    public static StaffIdentityLookupDTO missing() {
        return new StaffIdentityLookupDTO(false, false, null, null);
    }
}
