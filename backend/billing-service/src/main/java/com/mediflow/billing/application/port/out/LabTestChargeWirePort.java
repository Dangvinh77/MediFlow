package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;

public interface LabTestChargeWirePort {
    LabTestChargeCommand decode(String routingKey, byte[] body);
}
