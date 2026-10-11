package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand;
import java.util.UUID;

public interface PrescriptionTerminalRepositoryPort {
    void receive(PrescriptionTerminalCommand command);
    void recoverDuringIssuance(UUID prescriptionId);
    com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO get(UUID prescriptionId);
}
