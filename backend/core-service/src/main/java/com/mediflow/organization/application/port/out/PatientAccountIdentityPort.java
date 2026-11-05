package com.mediflow.organization.application.port.out;

import java.util.UUID;

/** Authoritative identity for initial PATIENT account issuance, never a patient database query. */
public interface PatientAccountIdentityPort {
    String identityNumber(UUID patientId);

    String email(UUID patientId);

    /** Reads the authoritative profile email as stored, without issuance eligibility rules. */
    String profileEmail(UUID patientId);

    String displayName(UUID patientId);

    /** Rejects missing or archived patient profiles before a patient account command. */
    void requireActivePatient(UUID patientId);

    /** Synchronizes account email through Patient's public write use case. */
    void updateEmail(UUID patientId, String email);
}
