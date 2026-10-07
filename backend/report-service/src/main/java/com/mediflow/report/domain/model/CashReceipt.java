package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Gross completed receipt evidence only; it supplies no earned revenue or liability balance. */
public record CashReceipt(UUID transactionId, UUID invoiceId, UUID paymentRequestId,
        UUID accountId, UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId,
        Classification classification, BigDecimal amount, String currency, String paymentMethod,
        Instant completedAt, LocalDate businessDate, String reportZone) {

    public enum Classification { SERVICE_PAYMENT, ADMISSION_DEPOSIT }

    public CashReceipt {
        if (transactionId == null || paymentRequestId == null || accountId == null || patientId == null
                || departmentId == null || careEpisodeId == null || classification == null
                || amount == null || amount.signum() <= 0 || completedAt == null || businessDate == null
                || reportZone == null || currency == null || !currency.matches("[A-Z]{3}")
                || !("CASH".equals(paymentMethod) || "TRANSFER".equals(paymentMethod))
                || !("ADMISSION".equals(careEpisodeType) || "OUTPATIENT_VISIT".equals(careEpisodeType))
                || (classification == Classification.ADMISSION_DEPOSIT && !"ADMISSION".equals(careEpisodeType))) {
            throw invalid();
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (amount.precision() > 19
                    || !completedAt.atZone(java.time.ZoneId.of(reportZone)).toLocalDate().equals(businessDate)) {
                throw invalid();
            }
        } catch (ArithmeticException | java.time.DateTimeException exception) {
            throw invalid();
        }
    }

    private static ReportRuleException invalid() {
        return new ReportRuleException("CASH_RECEIPT_INVALID", "Incomplete or unsupported completed cash receipt");
    }
}
