package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;

public interface SurgeryChargeWirePort {
    SurgeryChargeCommand decode(String routingKey, byte[] body);
}
