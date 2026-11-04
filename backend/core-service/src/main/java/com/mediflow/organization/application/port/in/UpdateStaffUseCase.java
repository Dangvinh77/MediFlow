package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

import java.util.UUID;

import com.mediflow.organization.domain.model.StaffProfile;

public interface UpdateStaffUseCase {

    Staff execute(
            UUID staffId,
            String fullName,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    );
    Staff execute(UUID staffId, String fullName, JobTitle jobTitle, String specialization, String licenseNumber, String phoneNumber, String email, StaffProfile profile);

    default Staff execute(UUID staffId, String fullName, JobTitle jobTitle, String specialization,
            String licenseNumber, String phoneNumber, String email, StaffProfile profile,
            String identityNumber, String identityChangeReason) {
        if (identityNumber != null) throw new UnsupportedOperationException("Staff identity correction is not configured");
        return execute(staffId, fullName, jobTitle, specialization, licenseNumber, phoneNumber, email, profile);
    }

    default Staff execute(UUID staffId, String fullName, JobTitle jobTitle, String specialization,
            String licenseNumber, String phoneNumber, String email, StaffProfile profile,
            String identityNumber, String identityChangeReason, UUID changedByAccountId) {
        return execute(staffId, fullName, jobTitle, specialization, licenseNumber, phoneNumber, email,
                profile, identityNumber, identityChangeReason);
    }
}
