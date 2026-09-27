package com.mediflow.inpatient.application.port.out;

import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.AdmissionSearchQuery;
import com.mediflow.inpatient.domain.model.Admission;

import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepositoryPort {
    Optional<Admission> findById(UUID admissionId);
    Optional<Admission> findByIdForUpdate(UUID admissionId);
    Optional<Admission> findByAdmissionRequestId(UUID requestId);
    Admission save(Admission admission);
    PageResult<Admission> search(AdmissionSearchQuery query);
}
