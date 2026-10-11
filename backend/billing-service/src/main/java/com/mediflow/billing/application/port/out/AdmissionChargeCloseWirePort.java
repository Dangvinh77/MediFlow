package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;

public interface AdmissionChargeCloseWirePort {
    CloseAdmissionChargesCommand decode(String routingKey, byte[] bytes);
}
