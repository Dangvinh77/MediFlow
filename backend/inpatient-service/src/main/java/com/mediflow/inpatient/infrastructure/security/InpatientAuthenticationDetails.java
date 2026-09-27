package com.mediflow.inpatient.infrastructure.security;

import java.util.UUID;

/** Authenticated identities from explicit JWT claims; sub remains the account ID. */
public record InpatientAuthenticationDetails(
        String accountId,
        UUID staffId,
        UUID patientId,
        UUID departmentId) {
}
