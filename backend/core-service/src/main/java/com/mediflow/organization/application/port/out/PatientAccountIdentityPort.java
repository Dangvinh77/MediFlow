package com.mediflow.organization.application.port.out;

import java.util.UUID;

/** Authoritative identity for initial PATIENT account issuance, never a patient database query. */
public interface PatientAccountIdentityPort {
    String identityNumber(UUID patientId);

    String email(UUID patientId);

    String displayName(UUID patientId);

    /** Synchronizes account email through Patient's public write use case. */
    void updateEmail(UUID patientId, String email);
}
