package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.DischargeSummary;

import java.util.Optional;
import java.util.UUID;

public interface DischargeSummaryRepositoryPort {
    Optional<DischargeSummary> findByAdmissionId(UUID admissionId);
    DischargeSummary save(DischargeSummary summary);
}
