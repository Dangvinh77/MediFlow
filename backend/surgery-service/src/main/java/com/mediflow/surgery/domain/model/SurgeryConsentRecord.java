package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One immutable signed consent; re-consent creates a new record after revocation. */
public record SurgeryConsentRecord(
        UUID consentId,
        UUID surgeryCaseId,
        SurgeryConsentType consentType,
        UUID signerId,
        SurgeryConsentSignerType signerType,
        UUID evidenceDocumentId,
        List<SurgeryConsentAuditEntry> auditHistory) {

    public SurgeryConsentRecord {
        if (consentId == null || surgeryCaseId == null || consentType == null || signerId == null
                || signerType == null || auditHistory == null || auditHistory.isEmpty()) {
            throw invalid("SURGERY_CONSENT_INVALID");
        }
        if (auditHistory.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_CONSENT_AUDIT_INVALID");
        }
        auditHistory = List.copyOf(auditHistory);
        SurgeryConsentAuditEntry first = auditHistory.getFirst();
        if (first.action() != SurgeryConsentAction.SIGNED) {
            throw invalid("SURGERY_CONSENT_SIGNING_AUDIT_REQUIRED");
        }
        Instant previous = first.occurredAt();
        for (int index = 1; index < auditHistory.size(); index++) {
            SurgeryConsentAuditEntry entry = auditHistory.get(index);
            if (entry.action() != SurgeryConsentAction.REVOKED || entry.occurredAt().isBefore(previous)) {
                throw invalid("SURGERY_CONSENT_AUDIT_ORDER_INVALID");
            }
            previous = entry.occurredAt();
        }
        if (auditHistory.size() > 2) {
            throw invalid("SURGERY_CONSENT_ALREADY_REVOKED");
        }
    }

    public static SurgeryConsentRecord sign(UUID consentId, UUID caseId, SurgeryConsentType type,
                                          UUID signerId, SurgeryConsentSignerType signerType,
                                          UUID evidenceDocumentId, SurgeryAuditActor recordedBy,
                                          Instant at, String correlationId) {
        SurgeryConsentAuditEntry signed = new SurgeryConsentAuditEntry(
                SurgeryConsentAction.SIGNED, recordedBy, at, correlationId, null);
        return new SurgeryConsentRecord(consentId, caseId, type, signerId, signerType,
                evidenceDocumentId, List.of(signed));
    }

    public SurgeryConsentRecord revoke(SurgeryAuditActor actor, Instant at,
                                       String correlationId, String reason) {
        if (!isActive()) {
            throw invalid("SURGERY_CONSENT_NOT_ACTIVE");
        }
        List<SurgeryConsentAuditEntry> updated = new ArrayList<>(auditHistory);
        updated.add(new SurgeryConsentAuditEntry(SurgeryConsentAction.REVOKED,
                actor, at, correlationId, reason));
        return new SurgeryConsentRecord(consentId, surgeryCaseId, consentType,
                signerId, signerType, evidenceDocumentId, updated);
    }

    public boolean isActive() {
        return auditHistory.getLast().action() == SurgeryConsentAction.SIGNED;
    }

    public Instant signedAt() {
        return auditHistory.getFirst().occurredAt();
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Bản ghi đồng ý phẫu thuật không hợp lệ");
    }
}
