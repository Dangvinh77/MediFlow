package com.mediflow.organization.domain.model;

import java.util.List;

/**
 * Canonical V1 mapping exposed to care services. The current Organization
 * taxonomy has no specialty-specific physician title, so DOCTOR is eligible
 * for physician surgery roles; consumers still require an active staff row.
 */
public final class JobTitleTeamRoleMapping {

    private JobTitleTeamRoleMapping() {
    }

    public static List<String> rolesFor(JobTitle jobTitle) {
        if (jobTitle == null) {
            return List.of();
        }
        return switch (jobTitle) {
            case DOCTOR -> List.of(
                    "PRIMARY_SURGEON",
                    "ASSISTANT_SURGEON",
                    "ANESTHESIOLOGIST");
            case NURSE -> List.of("OR_NURSE");
            case TECHNICIAN, PHARMACIST, CASHIER, MANAGER, ADMINISTRATIVE -> List.of();
        };
    }
}
