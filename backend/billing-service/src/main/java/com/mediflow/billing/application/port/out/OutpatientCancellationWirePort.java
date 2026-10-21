package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.OutpatientCancellationCommand;

public interface OutpatientCancellationWirePort {
    OutpatientCancellationCommand decode(String routingKey, byte[] body);
}
