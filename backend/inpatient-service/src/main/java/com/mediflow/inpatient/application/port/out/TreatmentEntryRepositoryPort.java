package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.TreatmentEntry;

import java.util.Optional;
import java.util.UUID;

public interface TreatmentEntryRepositoryPort {
    Optional<TreatmentEntry> findByAdmissionAndEntryId(UUID admissionId, UUID entryId);
    TreatmentEntry save(TreatmentEntry entry);
}
