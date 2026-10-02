package com.mediflow.pharmacy.application.port.in;

import java.util.Optional;
import java.util.UUID;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.domain.model.DispenseActor;

/** Re-evaluate a business stock failure AFTER a rejected dispense transaction has rolled back. */
public interface RecordCareStockFailureUseCase {
    Optional<DispenseDTO> recordStockFailure(UUID prescriptionId, DispenseActor actor, String correlationId);
}
