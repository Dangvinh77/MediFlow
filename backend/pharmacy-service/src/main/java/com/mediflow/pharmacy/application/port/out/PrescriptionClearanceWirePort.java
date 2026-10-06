package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand;
import java.util.Optional;

/** Empty means a fully validated grant for another purpose, not malformed input. */
public interface PrescriptionClearanceWirePort {
    Optional<PrescriptionClearanceCommand> decodeApplicable(String routingKey, byte[] body);
}
