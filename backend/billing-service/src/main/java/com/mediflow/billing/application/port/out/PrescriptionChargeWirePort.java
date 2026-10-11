package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;

public interface PrescriptionChargeWirePort {
    PrescriptionChargeCommand decode(String routingKey, byte[] body);
    com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand decodeTerminal(String routingKey, byte[] body);
}
