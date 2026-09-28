package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;

/** Immutable signing/revocation audit fact. */
public record SurgeryConsentAuditEntry(
        SurgeryConsentAction action,
        SurgeryAuditActor recordedBy,
        Instant occurredAt,
        String correlationId,
        String reason) {

    public SurgeryConsentAuditEntry {
        if (action == null || recordedBy == null || occurredAt == null
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 128
                || (action == SurgeryConsentAction.REVOKED && (reason == null || reason.isBlank()))) {
            throw invalid("SURGERY_CONSENT_AUDIT_INVALID");
        }
        correlationId = correlationId.trim();
        reason = reason == null ? null : reason.trim();
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Bản ghi kiểm toán đồng ý phẫu thuật không hợp lệ");
    }
}
