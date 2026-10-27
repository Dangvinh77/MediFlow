package com.mediflow.lab.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

/** Persistence boundary for the lab aggregate. */
public interface LabTestRepositoryPort {
    PageResult<LabTest> findCompletedByPatient(UUID patientId, PageQuery page);

    LabTest save(LabTest labTest);

    Optional<LabTest> findById(UUID id);

    Optional<LabTest> findByIdForUpdate(UUID id);

    boolean existsBySourceOrderId(UUID sourceOrderId);
    Optional<LabTest> findBySourceOrderId(UUID sourceOrderId);

    List<LabTest> findByPatient(UUID patientId);

    List<LabTest> findByRecord(UUID recordId);

    PageResult<LabTest> search(UUID departmentId, LabTestStatus status, PageQuery page);

    PageResult<LabTest> search(UUID departmentId, LabTestStatus status,
                               CareEpisodeType episodeType, UUID episodeId, PageQuery page);
}
