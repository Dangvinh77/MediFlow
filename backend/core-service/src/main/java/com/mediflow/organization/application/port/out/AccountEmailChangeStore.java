package com.mediflow.organization.application.port.out;

import java.util.List;
import java.util.UUID;

public interface AccountEmailChangeStore {
    void record(UUID accountId, UUID changedByAccountId, String oldEmail, String newEmail, String reason);
    void queuePatientProfileSync(UUID accountId, UUID patientId, String email);
    List<PatientEmailSync> claimPatientProfileSyncs(int limit);
    void completePatientProfileSync(UUID syncId);
    void retryPatientProfileSync(UUID syncId);

    record PatientEmailSync(UUID syncId, UUID accountId, UUID patientId, String email) { }
}
