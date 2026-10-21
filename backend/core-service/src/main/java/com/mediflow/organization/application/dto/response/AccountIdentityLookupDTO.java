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
        long credentialVersion) {
    public AccountIdentityLookupDTO(boolean exists, boolean active, UUID accountId, String role,
            UUID staffId, UUID departmentId, UUID patientId) {
        this(exists, active, accountId, role, staffId, departmentId, patientId, 0);
    }
    public static AccountIdentityLookupDTO missing() {
        return new AccountIdentityLookupDTO(false, false, null, null, null, null, null);
    }
}
