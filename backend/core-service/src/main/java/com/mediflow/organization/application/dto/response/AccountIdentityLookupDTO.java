package com.mediflow.organization.application.dto.response;

import java.util.UUID;

/** Minimal account authority projection; never contains credentials or personal account data. */
public record AccountIdentityLookupDTO(
        boolean exists,
        boolean active,
        UUID accountId,
        String role,
        UUID staffId,
        UUID departmentId,
        UUID patientId,
        long credentialVersion, boolean mustChangePassword) {
    public AccountIdentityLookupDTO(boolean exists, boolean active, UUID accountId, String role,
            UUID staffId, UUID departmentId, UUID patientId, long credentialVersion) {
        this(exists, active, accountId, role, staffId, departmentId, patientId, credentialVersion, false);
    }
    public AccountIdentityLookupDTO(boolean exists, boolean active, UUID accountId, String role,
            UUID staffId, UUID departmentId, UUID patientId) {
        this(exists, active, accountId, role, staffId, departmentId, patientId, 0, false);
    }
    public static AccountIdentityLookupDTO missing() {
        return new AccountIdentityLookupDTO(false, false, null, null, null, null, null);
    }
}
