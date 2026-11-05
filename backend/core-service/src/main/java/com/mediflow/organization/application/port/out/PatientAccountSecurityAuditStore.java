package com.mediflow.organization.application.port.out;

import java.util.UUID;

/** Durable, secret-free audit records for patient-scoped credential mail operations. */
public interface PatientAccountSecurityAuditStore {
    void record(Action action, UUID actorAccountId, UUID patientId, UUID targetAccountId,
            UUID correlationId, String operationKey);

    enum Action {
        PATIENT_PASSWORD_RESET,
        PATIENT_MAIL_RETRY
    }
}
