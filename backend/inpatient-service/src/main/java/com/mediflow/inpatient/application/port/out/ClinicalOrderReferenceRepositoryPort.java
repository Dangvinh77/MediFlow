package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.ClinicalOrderReference;
import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalOrderReferenceRepositoryPort {
    Optional<ClinicalOrderReference> findByTypeAndExternalId(ClinicalOrderType type, UUID externalId);
    List<ClinicalOrderReference> findByAdmissionId(UUID admissionId);
    ClinicalOrderReference save(ClinicalOrderReference reference);
}
