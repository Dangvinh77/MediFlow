package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;

import java.util.UUID;

public interface UpdateChecklistItemUseCase {

    SurgeryCommandOutcome update(Command command);

    record Command(UUID surgeryCaseId, UUID checklistItemId,
                   long expectedCaseRevision, long expectedSnapshotRevision, long expectedItemRevision,
                   SurgeryChecklistStatus status, UUID evidenceReferenceId, Long evidenceRevision,
                   String idempotencyKey, SurgeryAuditActor actor, String correlationId) {
        public Command {
            if (surgeryCaseId == null || checklistItemId == null || expectedCaseRevision < 0
                    || expectedSnapshotRevision < 0 || expectedItemRevision < 0 || status == null
                    || idempotencyKey == null || idempotencyKey.isBlank() || actor == null
                    || correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("Checklist update command is invalid");
            }
        }
    }
}
