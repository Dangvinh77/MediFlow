package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

public interface CreateStaffUseCase {

    record CreationResult(Staff staff, boolean replayed) {}

        Staff execute(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    );

    /** Optional keyed variant; existing clients retain the original create contract. */
    default CreationResult executeWithIdempotency(
            UUID actorAccountId,
            String idempotencyKey,
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {
        return new CreationResult(execute(fullName, departmentId, jobTitle, specialization,
                licenseNumber, phoneNumber, email), false);
    }
}
