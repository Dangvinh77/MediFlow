package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Sự thật "đã đủ điều kiện tài chính để tiến hành" mà Billing cấp cho đúng một mục đích + một
 * target cụ thể — nguồn phát sự kiện {@code financial.clearance.granted} theo
 * CONTRACT-CARE-BILLING-01 và backend-spec/care-finance-v2/06-billing.md §3, §8. Đây là phần domain
 * cho HANDOFF đang OPEN (backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md):
 * Clinical/Lab đang chờ Billing phát sự kiện này để gỡ chặn khám/xét nghiệm theo đúng thanh toán.
 *
 * <p>Quy tắc lõi (khớp {@code ck_clearance_target}): mỗi {@code purpose} chỉ được cấp quyền đúng
 * một loại target tương ứng, các trường target khác phải rỗng/null — không được suy luận hộ.
 */
@Getter
public class FinancialClearance {

    private final UUID clearanceId;
    private final UUID accountId;
    private final UUID paymentRequestId;
    private final UUID invoiceId;
    private final UUID patientId;
    private final ClearancePurpose purpose;
    private final CareEpisodeType careEpisodeType;
    private final UUID careEpisodeId;
    private final UUID appointmentId;
    private final UUID recordId;
    private final List<UUID> labTestIds;
    private final UUID prescriptionId;
    private final UUID admissionId;
    private final UUID surgeryCaseId;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentMethod;
    private final boolean emergencyOverride;
    private final Instant expiresAt;
    private final Instant grantedAt;
    private Instant revokedAt;

    private FinancialClearance(UUID clearanceId, UUID accountId, UUID paymentRequestId, UUID invoiceId,
                                UUID patientId, ClearancePurpose purpose, CareEpisodeType careEpisodeType,
                                UUID careEpisodeId, UUID appointmentId, UUID recordId,
                                List<UUID> labTestIds, UUID prescriptionId, UUID admissionId,
                                UUID surgeryCaseId, BigDecimal amount, String currency,
                                String paymentMethod, boolean emergencyOverride, Instant expiresAt,
                                Instant grantedAt, Instant revokedAt) {
        this.clearanceId = clearanceId;
        this.accountId = accountId;
        this.paymentRequestId = paymentRequestId;
        this.invoiceId = invoiceId;
        this.patientId = patientId;
        this.purpose = purpose;
        this.careEpisodeType = careEpisodeType;
        this.careEpisodeId = careEpisodeId;
        this.appointmentId = appointmentId;
        this.recordId = recordId;
        this.labTestIds = labTestIds == null ? List.of() : List.copyOf(labTestIds);
        this.prescriptionId = prescriptionId;
        this.admissionId = admissionId;
        this.surgeryCaseId = surgeryCaseId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.emergencyOverride = emergencyOverride;
        this.expiresAt = expiresAt;
        this.grantedAt = grantedAt;
        this.revokedAt = revokedAt;
    }

    /**
     * Cấp một clearance mới. Ném {@code BILLING_CLEARANCE_TARGET_MISMATCH} nếu target không khớp
     * đúng purpose (CONTRACT-CARE-BILLING-01 §"financial.clearance.granted" rule 1).
     */
    public static FinancialClearance grant(UUID accountId, UUID paymentRequestId, UUID invoiceId,
                                            UUID patientId, ClearancePurpose purpose,
                                            CareEpisodeType careEpisodeType, UUID careEpisodeId,
                                            UUID appointmentId, UUID recordId, List<UUID> labTestIds,
                                            UUID prescriptionId, UUID admissionId, UUID surgeryCaseId,
                                            BigDecimal amount, String currency, String paymentMethod,
                                            boolean emergencyOverride, Instant expiresAt, Instant grantedAt) {
        requireMatchingTarget(purpose, appointmentId, recordId, labTestIds, prescriptionId, admissionId,
                surgeryCaseId);
        if (amount == null || amount.signum() < 0) {
            throw new BillingRuleException("BILLING_CLEARANCE_INVALID_AMOUNT",
                    "Số tiền clearance không được âm");
        }
        return new FinancialClearance(null, accountId, paymentRequestId, invoiceId, patientId, purpose,
                careEpisodeType, careEpisodeId, appointmentId, recordId, labTestIds, prescriptionId,
                admissionId, surgeryCaseId, amount, currency, paymentMethod, emergencyOverride,
                expiresAt, grantedAt, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc cấp. */
    public static FinancialClearance restore(UUID clearanceId, UUID accountId, UUID paymentRequestId,
                                              UUID invoiceId, UUID patientId, ClearancePurpose purpose,
                                              CareEpisodeType careEpisodeType, UUID careEpisodeId,
                                              UUID appointmentId, UUID recordId, List<UUID> labTestIds,
                                              UUID prescriptionId, UUID admissionId, UUID surgeryCaseId,
                                              BigDecimal amount, String currency, String paymentMethod,
                                              boolean emergencyOverride, Instant expiresAt,
                                              Instant grantedAt, Instant revokedAt) {
        return new FinancialClearance(clearanceId, accountId, paymentRequestId, invoiceId, patientId,
                purpose, careEpisodeType, careEpisodeId, appointmentId, recordId, labTestIds,
                prescriptionId, admissionId, surgeryCaseId, amount, currency, paymentMethod,
                emergencyOverride, expiresAt, grantedAt, revokedAt);
    }

    public void revoke(Instant at) {
        if (revokedAt != null) {
            throw new BillingRuleException("BILLING_CLEARANCE_ALREADY_REVOKED",
                    "Clearance đã bị thu hồi trước đó");
        }
        this.revokedAt = at;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    private static void requireMatchingTarget(ClearancePurpose purpose, UUID appointmentId, UUID recordId,
                                               List<UUID> labTestIds, UUID prescriptionId,
                                               UUID admissionId, UUID surgeryCaseId) {
        boolean matches = switch (purpose) {
            case EXAM -> appointmentId != null || recordId != null;
            case LAB_TEST -> labTestIds != null && !labTestIds.isEmpty();
            case PRESCRIPTION -> prescriptionId != null;
            case ADMISSION_DEPOSIT -> admissionId != null;
            case SURGERY -> surgeryCaseId != null;
        };
        if (!matches) {
            throw new BillingRuleException("BILLING_CLEARANCE_TARGET_MISMATCH",
                    "Target không khớp với purpose " + purpose);
        }
    }
}
