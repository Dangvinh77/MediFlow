package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;

/** Append-only business revision entry, including child changes that do not change case status. */
public record SurgeryCaseAuditEntry(
        long revision,
        String changeCode,
        SurgeryStatus previousStatus,
        SurgeryStatus newStatus,
        SurgeryAuditActor actor,
        Instant occurredAt,
        String correlationId) {

    public SurgeryCaseAuditEntry {
        if (revision < 0 || changeCode == null || changeCode.isBlank() || changeCode.length() > 64
                || newStatus == null || actor == null || occurredAt == null
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 128
                || (previousStatus == null && newStatus != SurgeryStatus.REQUESTED)
                || (previousStatus != null && "CASE_CREATED".equals(changeCode))) {
            throw new SurgeryRuleException(
                    "SURGERY_CASE_AUDIT_INVALID", "Bản ghi kiểm toán phiên bản ca mổ không hợp lệ");
        }
        changeCode = changeCode.trim();
        correlationId = correlationId.trim();
    }

    public boolean changesStatus() {
        return previousStatus != null && previousStatus != newStatus;
    }
}
