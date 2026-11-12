package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalLabOrderRepositoryPort {
    ClinicalLabOrder save(ClinicalLabOrder order);

    /** Active orders only: a withdrawn order is invisible to closure, listing and Lab's context lookup. */
    Optional<ClinicalLabOrder> findById(UUID sourceOrderId);

    /** Active orders only. */
    List<ClinicalLabOrder> findByRecordId(UUID recordId);

    void withdraw(UUID sourceOrderId, UUID withdrawnByStaffId, String reason, Instant withdrawnAt);
}
