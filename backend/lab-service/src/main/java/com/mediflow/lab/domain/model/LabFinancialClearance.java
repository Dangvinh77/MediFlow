package com.mediflow.lab.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.exception.LabRuleException;

/** Persisted projection of one Billing clearance target for one Lab test. */
public record LabFinancialClearance(
        UUID clearanceTargetId,
        UUID clearanceId,
        UUID eventId,
        UUID invoiceId,
        UUID accountId,
        UUID testId,
        UUID patientId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        ClearancePurpose purpose,
        BigDecimal amount,
        String currency,
        Instant expiresAt,
        boolean emergencyOverride,
        Instant grantedAt
) {

    public static LabFinancialClearance grant(
            UUID clearanceTargetId, UUID clearanceId, UUID eventId, UUID invoiceId, UUID accountId,
            UUID testId, UUID patientId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
            ClearancePurpose purpose, BigDecimal amount, String currency, Instant expiresAt,
            boolean emergencyOverride, Instant grantedAt) {
        if (clearanceTargetId == null || clearanceId == null || eventId == null || invoiceId == null
                || accountId == null || testId == null || patientId == null || careEpisodeType == null
                || careEpisodeId == null || purpose == null || amount == null || amount.signum() < 0
                || currency == null || !currency.matches("[A-Z]{3}") || grantedAt == null) {
            throw new LabRuleException("LAB_CLEARANCE_TARGET_MISMATCH",
                    "Thông tin quyền thực hiện xét nghiệm không hợp lệ");
        }
        return new LabFinancialClearance(clearanceTargetId, clearanceId, eventId, invoiceId, accountId,
                testId, patientId, careEpisodeType, careEpisodeId, purpose, amount, currency,
                expiresAt, emergencyOverride, grantedAt);
    }

    public boolean isExpiredAt(Instant at) {
        return expiresAt != null && !expiresAt.isAfter(at);
    }
}
