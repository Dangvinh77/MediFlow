package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.CaptureSurgeryCompletionCommand;

public interface SurgeryCompletionWirePort {
    CaptureSurgeryCompletionCommand decode(String routingKey, byte[] originalBytes);
}
