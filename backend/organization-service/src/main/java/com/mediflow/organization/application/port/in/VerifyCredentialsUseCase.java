package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.Role;

public interface VerifyCredentialsUseCase {

    VerifiedAccount execute(String username, String rawPassword);

    record VerifiedAccount(
            UUID accountId,
            UUID staffId,
            UUID departmentId,
            UUID patientId,
            Role role) {
    }
}
