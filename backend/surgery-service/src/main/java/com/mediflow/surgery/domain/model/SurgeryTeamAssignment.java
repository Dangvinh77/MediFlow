package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/** Bare Organization-owned staff reference and a Surgery-local role. */
public record SurgeryTeamAssignment(UUID staffId, SurgeryTeamRole role) {

    public SurgeryTeamAssignment {
        if (staffId == null || role == null) {
            throw new SurgeryRuleException(
                    "SURGERY_TEAM_ASSIGNMENT_INVALID", "Phân công ê-kíp phải có nhân viên và vai trò");
        }
    }
}
