package com.mediflow.organization.application.dto.response;

import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative staff eligibility result consumed by operational services.
 * A department is exposed only for an eligible, active doctor so consumers
 * cannot accidentally use a nurse or inactive staff member as a doctor.
 */
public record StaffLookupDTO(
        boolean exists,
        boolean eligibleDoctor,
        UUID departmentId) {

    public StaffLookupDTO {
        if (!exists && eligibleDoctor) {
            throw new IllegalArgumentException("A missing staff member cannot be an eligible doctor");
        }
        if (eligibleDoctor && departmentId == null) {
            throw new IllegalArgumentException("An eligible doctor must have a department");
        }
        if (!eligibleDoctor && departmentId != null) {
            throw new IllegalArgumentException("An ineligible staff member must not expose a department");
        }
    }

    public static StaffLookupDTO missing() {
        return new StaffLookupDTO(false, false, null);
    }

    public static StaffLookupDTO ineligible() {
        return new StaffLookupDTO(true, false, null);
    }

    public static StaffLookupDTO eligible(UUID departmentId) {
        return new StaffLookupDTO(true, true, Objects.requireNonNull(departmentId));
    }
}
