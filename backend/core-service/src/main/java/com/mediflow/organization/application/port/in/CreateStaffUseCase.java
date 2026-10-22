package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.StaffProfile;

public interface CreateStaffUseCase {

    record CreationResult(Staff staff, boolean replayed) {}

    Staff execute(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email,
            String identityNumber
    );

    /** Keyed retries return the original staff when the complete creation payload matches. */
    default CreationResult executeWithIdempotency(
            UUID actorAccountId,
            String idempotencyKey,
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email,
            String identityNumber) {
        return new CreationResult(execute(fullName, departmentId, jobTitle, specialization,
                licenseNumber, phoneNumber, email, identityNumber), false);
    }
    Staff execute(String fullName, UUID departmentId, JobTitle jobTitle, String specialization, String licenseNumber, String phoneNumber, String email, String identityNumber, StaffProfile profile);
    CreationResult executeWithIdempotency(UUID actorAccountId, String idempotencyKey, String fullName, UUID departmentId, JobTitle jobTitle, String specialization, String licenseNumber, String phoneNumber, String email, String identityNumber, StaffProfile profile);
}
