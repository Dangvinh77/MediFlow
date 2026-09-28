package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;

/** Append-only state transition facts owned by the case aggregate. */
public record SurgeryStateChange(
        SurgeryStatus previousStatus,
        SurgeryStatus newStatus,
        SurgeryAuditActor actor,
        String reason,
        Instant occurredAt,
        String correlationId) {

    public SurgeryStateChange {
        if (newStatus == null || actor == null || reason == null || reason.isBlank()
                || occurredAt == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 128) {
            throw new SurgeryRuleException(
                    "SURGERY_STATE_CHANGE_INVALID", "Bản ghi lịch sử trạng thái không hợp lệ");
        }
        reason = reason.trim();
        correlationId = correlationId.trim();
    }

    /** Compatibility accessor for reports that need a human account ID. */
    public java.util.UUID performedBy() {
        return actor.actorType() == SurgeryActorType.HUMAN ? actor.accountId() : null;
    }
}
