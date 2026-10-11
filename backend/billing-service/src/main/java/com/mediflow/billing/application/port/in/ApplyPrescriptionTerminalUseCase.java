package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand;

public interface ApplyPrescriptionTerminalUseCase {
    void apply(PrescriptionTerminalCommand command);
}
