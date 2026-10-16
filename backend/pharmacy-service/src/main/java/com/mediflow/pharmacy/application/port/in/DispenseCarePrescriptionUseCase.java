package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.domain.model.DispenseActor;

/** V1 transaction primitive; only the gated outpatient facade may expose it to authenticated HTTP. */
public interface DispenseCarePrescriptionUseCase {
    DispenseDTO execute(UUID prescriptionId, DispenseActor actor, String correlationId);
}
