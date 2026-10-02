package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;

import java.util.UUID;

/** Cancels a case before the operation starts; cancellation facts are not published by this use case. */
public interface CancelSurgeryUseCase {

    SurgeryCommandOutcome cancel(Command command);

    record Command(UUID surgeryCaseId, long expectedCaseRevision, String reason,
                   String idempotencyKey, SurgeryActorIdentity actor, String correlationId) {
        public Command {
            if (surgeryCaseId == null || expectedCaseRevision < 0 || reason == null
                    || reason.isBlank() || reason.length() > 1_000
                    || idempotencyKey == null || idempotencyKey.isBlank()
                    || idempotencyKey.length() > 160
                    || actor == null || correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("Cancel surgery command is invalid");
            }
            reason = reason.trim();
        }
    }
}
