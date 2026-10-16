package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import java.util.UUID;

/** Public opt-in V1 creation; exact Clinical and identity proofs are mandatory. */
public interface CreateOutpatientPrescriptionUseCase {
    PrescriptionDTO create(UUID commandId, CreatePrescriptionCommand command);
}
