package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;

import java.util.UUID;

public interface BeginPreopUseCase {

    SurgeryCommandOutcome begin(Command command);

    record Command(UUID surgeryCaseId, long expectedCaseRevision, String idempotencyKey,
                   SurgeryActorIdentity actor, String correlationId) {
        public Command {
            if (surgeryCaseId == null || expectedCaseRevision < 0 || idempotencyKey == null
                    || idempotencyKey.isBlank() || actor == null || correlationId == null
                    || correlationId.isBlank()) {
                throw new IllegalArgumentException("Begin-preop command is invalid");
            }
        }
    }
}
