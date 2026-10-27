package com.mediflow.billing.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

/** A current Billing-owned read, not a distributed reservation of money. */
public record FinancialClearanceAuthority(UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
        String purpose, String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID surgeryCaseId,
        Instant grantedAt, Instant expiresAt, Instant revokedAt, boolean ledgerEligible, Instant observedAt,
        UUID appointmentId, UUID recordId, List<UUID> labTestIds, UUID prescriptionId) {
    public FinancialClearanceAuthority {
        labTestIds = labTestIds == null ? List.of() : List.copyOf(labTestIds);
    }
    public FinancialClearanceAuthority(UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
            String purpose, String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID surgeryCaseId,
            Instant grantedAt, Instant expiresAt, Instant revokedAt, boolean ledgerEligible, Instant observedAt) {
        this(clearanceId, invoiceId, accountId, patientId, purpose, careEpisodeType, careEpisodeId, admissionId,
                surgeryCaseId, grantedAt, expiresAt, revokedAt, ledgerEligible, observedAt, null, null, List.of(), null);
    }
    public FinancialClearanceAuthority(UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
            String purpose, String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID surgeryCaseId,
            Instant grantedAt, Instant expiresAt, Instant revokedAt, boolean ledgerEligible, Instant observedAt,
            UUID appointmentId, UUID recordId, List<UUID> labTestIds) {
        this(clearanceId, invoiceId, accountId, patientId, purpose, careEpisodeType, careEpisodeId, admissionId,
                surgeryCaseId, grantedAt, expiresAt, revokedAt, ledgerEligible, observedAt, appointmentId, recordId,
                labTestIds, null);
    }
    public boolean eligible() {
        return ledgerEligible && revokedAt == null && !observedAt.isBefore(grantedAt)
                && (expiresAt == null || observedAt.isBefore(expiresAt));
    }
    public boolean matchesSource(String sourceType, UUID sourceId) {
        if (sourceId == null || !java.util.Objects.equals(purpose, sourceType)) return false;
        return switch (sourceType) {
            case "EXAM" -> sourceId.equals(examSourceId());
            case "LAB_TEST" -> labTestIds.equals(List.of(sourceId)) && appointmentId == null
                    && admissionId == null && surgeryCaseId == null;
            default -> false;
        };
    }
    public UUID sourceId() {
        return switch (purpose) {
            case "EXAM" -> examSourceId();
            case "PRESCRIPTION" -> prescriptionId;
            case "LAB_TEST" -> labTestIds.size() == 1 ? labTestIds.getFirst() : null;
            case "SURGERY" -> surgeryCaseId;
            default -> null;
        };
    }
    private UUID examSourceId() {
        if (!labTestIds.isEmpty() || admissionId != null || surgeryCaseId != null || prescriptionId != null) {
            return null;
        }
        if (appointmentId != null) return recordId == null ? appointmentId : null;
        // Scheduled EXAM clearances bind record_id to the registration, before a medical record exists.
        return recordId != null && "OUTPATIENT_VISIT".equals(careEpisodeType)
                && recordId.equals(careEpisodeId) ? recordId : null;
    }
}
