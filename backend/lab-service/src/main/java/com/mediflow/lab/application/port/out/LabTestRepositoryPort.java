package com.mediflow.lab.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

/** Persistence boundary for the lab aggregate. */
public interface LabTestRepositoryPort {

    LabTest save(LabTest labTest);

    Optional<LabTest> findById(UUID id);

    Optional<LabTest> findByIdForUpdate(UUID id);

    List<LabTest> findByPatient(UUID patientId);

    List<LabTest> findByRecord(UUID recordId);

    PageResult<LabTest> search(UUID departmentId, LabTestStatus status, PageQuery page);
}
