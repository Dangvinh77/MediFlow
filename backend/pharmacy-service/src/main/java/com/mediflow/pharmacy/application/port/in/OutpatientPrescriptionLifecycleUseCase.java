package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import java.util.UUID;

/** Profile-gated outpatient operations; expiry is scheduler-only, not a human HTTP endpoint. */
public interface OutpatientPrescriptionLifecycleUseCase {
    DispenseDTO dispense(UUID prescriptionId, ActorIdentity actor, String correlationId);
    CancelPrescriptionResult cancel(CancelPrescriptionCommand command);
    int expire(UUID prescriptionId, String correlationId);
}
