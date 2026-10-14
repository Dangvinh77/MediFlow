package com.mediflow.clinical.application.service;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.common.exception.ForbiddenOperationException;

/** Resolves care authority from the signed actor and the authoritative staff directory. */
public final class ClinicalStaffAuthority {

    private final CurrentClinicalActorPort actors;
    private final StaffLookupPort staff;

    public ClinicalStaffAuthority(CurrentClinicalActorPort actors, StaffLookupPort staff) {
        this.actors = actors;
        this.staff = staff;
    }

    public void requireAssignedDoctor(UUID assignedDoctorId, UUID departmentId) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.staffId() == null
                || (!"DOCTOR".equals(actor.role()) && !"ADMIN".equals(actor.role()))) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED",
                    "An authenticated clinical staff identity is required");
        }
        if (!actor.staffId().equals(assignedDoctorId)) {
            throw new ForbiddenOperationException("CLINICAL_DOCTOR_NOT_ASSIGNED",
                    "Only the doctor assigned to this appointment may modify its clinical record");
        }

        Optional<UUID> authoritativeDepartment = staff.departmentOf(actor.staffId());
        if (authoritativeDepartment.isEmpty()) {
            throw new ForbiddenOperationException("CLINICAL_DOCTOR_NOT_ELIGIBLE",
                    "The authenticated staff member is not an eligible doctor");
        }
        if (!authoritativeDepartment.get().equals(departmentId)) {
            throw new ForbiddenOperationException("CLINICAL_DOCTOR_WRONG_DEPARTMENT",
                    "The authenticated doctor is not assigned to the appointment department");
        }
    }
}
