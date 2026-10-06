package com.mediflow.inpatient.application.service;

import com.mediflow.inpatient.application.dto.response.AdmissionLookupDTO;
import com.mediflow.inpatient.application.port.in.LookupAdmissionAuthorityUseCase;
import com.mediflow.inpatient.application.port.out.AdmissionAuthorityRepositoryPort;
import java.time.Clock;
import java.util.UUID;

public class LookupAdmissionAuthorityService implements LookupAdmissionAuthorityUseCase {
    private final AdmissionAuthorityRepositoryPort admissions;
    private final Clock clock;

    public LookupAdmissionAuthorityService(AdmissionAuthorityRepositoryPort admissions, Clock clock) {
        this.admissions = admissions;
        this.clock = clock;
    }

    @Override
    public AdmissionLookupDTO lookup(UUID admissionId) {
        if (admissionId == null) throw new IllegalArgumentException("Admission ID is required");
        var snapshot = admissions.findById(admissionId).orElse(null);
        if (snapshot == null) return new AdmissionLookupDTO(false, admissionId, null, null,
                null, null, false, null, clock.instant());
        return new AdmissionLookupDTO(true, snapshot.admissionId(), snapshot.patientId(),
                snapshot.departmentId(), snapshot.sourceRecordId(), snapshot.status().name(),
                snapshot.eligible(), Long.toString(snapshot.sourceRevision()), clock.instant());
    }
}
