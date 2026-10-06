package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase.Command;
import java.time.Instant;

public interface SurgeryAuthorityChangeWirePort {
    Command decode(String routingKey, byte[] body, Instant receivedAt);
}
