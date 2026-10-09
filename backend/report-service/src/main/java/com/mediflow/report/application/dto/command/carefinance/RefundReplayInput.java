package com.mediflow.report.application.dto.command.carefinance;

import java.time.LocalDate;
import com.mediflow.report.domain.model.CashReceipt;
import com.mediflow.report.domain.model.CashRefund;

/** Preserve the accepted snapshot state; replay never retries a live pending refund. */
public record RefundReplayInput(CashRefund refund, State state, CashReceipt.Classification classification,
        LocalDate originalBusinessDate, String reasonCode) {
    public enum State { PENDING, APPLIED, REJECTED }
    public RefundReplayInput {
        if (refund == null || state == null
                || (state == State.APPLIED && (classification == null || originalBusinessDate == null))
                || (state != State.APPLIED && (classification != null || originalBusinessDate != null))
                || (state == State.REJECTED && (reasonCode == null || reasonCode.isBlank()))) {
            throw new IllegalArgumentException("Invalid frozen refund state");
        }
    }
}
