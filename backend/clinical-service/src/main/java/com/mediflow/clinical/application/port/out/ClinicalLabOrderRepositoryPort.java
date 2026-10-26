package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalLabOrderRepositoryPort {
    ClinicalLabOrder save(ClinicalLabOrder order);
    Optional<ClinicalLabOrder> findById(UUID sourceOrderId);
    List<ClinicalLabOrder> findByRecordId(UUID recordId);
}
