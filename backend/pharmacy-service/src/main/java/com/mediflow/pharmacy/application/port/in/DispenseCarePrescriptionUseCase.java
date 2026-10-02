package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.domain.model.DispenseActor;

/** Internal V1 outpatient command. Not wired to HTTP, Rabbit or the legacy payment workflow. */
public interface DispenseCarePrescriptionUseCase {
    DispenseDTO execute(UUID prescriptionId, DispenseActor actor, String correlationId);
}
