package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.SurgeryCancellationCommand;

public interface SurgeryCancellationWirePort {
    SurgeryCancellationCommand decode(String routingKey, byte[] body);
}
