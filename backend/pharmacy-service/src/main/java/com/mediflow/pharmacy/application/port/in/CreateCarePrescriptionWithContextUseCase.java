package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort.Observation;
import java.util.UUID;

/** Stock transaction must recheck the preflight proof, including after all mutation lock waits. */
public interface CreateCarePrescriptionWithContextUseCase {
    UUID createCare(UUID commandId, CreatePrescriptionCommand command, Observation context);
    UUID createCare(UUID commandId, CreatePrescriptionCommand command, Observation context,
            com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort.Observation identities);
}
