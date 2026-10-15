package com.mediflow.lab.application.port.out;

import com.mediflow.lab.application.dto.context.LabClinicalOrderContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import java.util.Optional;
import java.util.UUID;

/** Reads exact, persisted order authority from Clinical before Lab starts its write transaction. */
public interface LabClinicalOrderContextPort {
    Optional<LabClinicalOrderContext> findById(UUID sourceOrderId, String correlationId,
            AuthenticatedStaffContext actor);
}
