package com.mediflow.organization.domain.model;

import com.mediflow.common.exception.BusinessRuleException;
import java.time.Instant;
import java.util.UUID;

/** An explicit qualification decision; job title alone never creates one. */
public record SurgicalCapability(UUID staffId, SurgicalTeamRole teamRole, UUID departmentId,
                                 boolean active, Instant validFrom, Instant validUntil,
                                 long revision, Instant updatedAt) {
    public SurgicalCapability {
        if (staffId == null || teamRole == null || departmentId == null || updatedAt == null
                || revision < 1 || validFrom == null || validUntil == null
                || !validUntil.isAfter(validFrom)) {
            throw new BusinessRuleException("ORG_CAPABILITY_INVALID", "Invalid surgical capability decision");
        }
    }

    public boolean covers(Staff staff, Department department, Instant startsAt, Instant endsAt) {
        return active && teamRole.accepts(staff) && department != null && department.isActive()
                && staffId.equals(staff.getStaffId())
                && department.getDepartmentType() == DepartmentType.CLINICAL
                && departmentId.equals(staff.getDepartmentId())
                && departmentId.equals(department.getDepartmentId())
                && !startsAt.isBefore(validFrom) && !endsAt.isAfter(validUntil)
                && endsAt.isAfter(startsAt);
    }
}
