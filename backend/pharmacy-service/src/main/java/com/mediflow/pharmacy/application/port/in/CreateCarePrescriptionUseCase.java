package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;

/** Internal held outpatient creation. No public adapter calls this before authority/wire acceptance. */
public interface CreateCarePrescriptionUseCase {
    UUID createCare(UUID commandId, CreatePrescriptionCommand command);
}
