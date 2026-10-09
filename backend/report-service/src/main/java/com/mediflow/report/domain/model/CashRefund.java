package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.UUID;
import com.mediflow.report.domain.exception.ReportRuleException;

/** Completed cash outflow; classification and department must match the exact original receipt. */
public record CashRefund(UUID refundTransactionId, UUID originalTransactionId, UUID accountId, UUID patientId,
        UUID departmentId, String careEpisodeType, UUID careEpisodeId, BigDecimal amount, String currency,
        Instant completedAt, LocalDate businessDate, String reportZone) {
    public CashRefund {
        if (refundTransactionId == null || originalTransactionId == null || refundTransactionId.equals(originalTransactionId)
                || accountId == null || patientId == null || departmentId == null || careEpisodeId == null
                || !("ADMISSION".equals(careEpisodeType) || "OUTPATIENT_VISIT".equals(careEpisodeType))
                || amount == null || amount.signum() <= 0 || !"VND".equals(currency) || completedAt == null
                || businessDate == null || reportZone == null) throw invalid("CASH_REFUND_INVALID");
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (amount.precision() > 19 || !completedAt.atZone(ZoneId.of(reportZone)).toLocalDate().equals(businessDate))
                throw invalid("CASH_REFUND_INVALID");
        } catch (ArithmeticException | DateTimeException invalid) { throw invalid("CASH_REFUND_INVALID"); }
    }
    public void verifyOriginal(CashReceipt original, BigDecimal alreadyRefunded) {
        if (!originalTransactionId.equals(original.transactionId()) || !accountId.equals(original.accountId())
                || !patientId.equals(original.patientId()) || !departmentId.equals(original.departmentId())
                || !careEpisodeType.equals(original.careEpisodeType()) || !careEpisodeId.equals(original.careEpisodeId())
                || !currency.equals(original.currency()) || !reportZone.equals(original.reportZone())
                || completedAt.isBefore(original.completedAt())) throw invalid("CASH_REFUND_ORIGINAL_MISMATCH");
        if (alreadyRefunded == null || alreadyRefunded.signum() < 0 || alreadyRefunded.add(amount).compareTo(original.amount()) > 0)
            throw invalid("CASH_REFUND_EXCEEDS_ORIGINAL");
    }
    private static ReportRuleException invalid(String code) { return new ReportRuleException(code, "Invalid cash refund evidence"); }
}
