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
            Role role,
            long credentialVersion, boolean mustChangePassword) {
        public VerifiedAccount(UUID accountId, UUID staffId, UUID departmentId, UUID patientId, Role role, long credentialVersion) {
            this(accountId, staffId, departmentId, patientId, role, credentialVersion, false);
        }
        public VerifiedAccount(UUID accountId, UUID staffId, UUID departmentId, UUID patientId, Role role) {
            this(accountId, staffId, departmentId, patientId, role, 0, false);
        }
    }
}
