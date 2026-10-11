package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;

public interface AdmissionDepositRequestWirePort {
    AdmissionDepositRequestCommand decode(String routingKey, byte[] body);
}
