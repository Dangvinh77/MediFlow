package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase.Command;
import java.time.Instant;
import java.util.Optional;

/** Boundary codec: the driving listener need not know domain or adapter types. */
public interface SurgeryClearanceWirePort {
    Command decode(String routingKey, byte[] body, Instant receivedAt);
    Optional<Command> decodeApplicable(String routingKey, byte[] body, Instant receivedAt);
}
