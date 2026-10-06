package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.AdmissionAuthoritySnapshot;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionAuthorityRepositoryPort {
    Optional<AdmissionAuthoritySnapshot> findById(UUID admissionId);
}
