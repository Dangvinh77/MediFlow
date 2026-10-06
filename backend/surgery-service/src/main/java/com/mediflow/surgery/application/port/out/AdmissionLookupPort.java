package com.mediflow.surgery.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface AdmissionLookupPort {
    AdmissionSnapshot findAdmission(UUID admissionId,String correlationId);

    /** Admission revision does not version bed placement or surgery-referral registration. */
    record AdmissionSnapshot(UUID admissionId,UUID patientId,UUID departmentId,UUID sourceRecordId,
            String status,OrganizationLookupPort.ReferenceState state,String sourceRevision,Instant observedAt) {}
}
