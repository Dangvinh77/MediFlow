package com.mediflow.surgery.application.dto;

import java.util.UUID;

/** Internal command identity. Driving adapters alone construct its verified actor. */
public record SurgeryLifecycleCommand(UUID surgeryCaseId, long expectedCaseRevision, long expectedScheduleRevision,
        String idempotencyKey, SurgeryActorIdentity actor, String correlationId) {
    public SurgeryLifecycleCommand {
        if (surgeryCaseId == null || expectedCaseRevision < 0 || expectedScheduleRevision < 1 || actor == null
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 128) {
            throw new IllegalArgumentException("Invalid lifecycle command");
        }
    }
}
