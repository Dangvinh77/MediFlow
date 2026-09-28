package com.mediflow.surgery.infrastructure.security;

import java.util.UUID;

/** Verified account and optional staff/patient/department identities from JWT claims. */
public record SurgeryAuthenticationDetails(
        UUID accountId,
        UUID staffId,
        UUID patientId,
        UUID departmentId) {
}
