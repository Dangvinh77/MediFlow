package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import java.util.Optional;

/** A valid {@code appointment.status.changed} transition that is not the EXAM charge trigger is
 * explicitly not applicable: {@link #decodeApplicable} returns empty, not an error. */
public interface ExamChargeWirePort {
    Optional<ExamChargeCommand> decodeApplicable(String routingKey, byte[] body);
}
