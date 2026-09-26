package com.mediflow.patient.application.port.out;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.domain.model.Patient;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepositoryPort {

    Optional<Patient> findById(UUID patientId);

    PageResult<Patient> search(String keyword, PageQuery page);

    boolean existsById(UUID patientId);
}
