package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.PlacementAuthoritySnapshot;
import java.util.Optional;
import java.util.UUID;

public interface PlacementAuthorityRepositoryPort {
    Optional<PlacementAuthoritySnapshot> findByAdmissionId(UUID admissionId);
}
