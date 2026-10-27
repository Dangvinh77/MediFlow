package com.mediflow.organization.application.port.out;

import java.util.UUID;

/** Authoritative identity for initial PATIENT account issuance, never a patient database query. */
public interface PatientAccountIdentityPort {
    String identityNumber(UUID patientId);
}
