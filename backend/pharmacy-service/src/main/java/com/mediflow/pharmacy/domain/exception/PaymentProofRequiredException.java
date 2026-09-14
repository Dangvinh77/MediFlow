package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Raised when manual dispensing has no durable payment proof in pharmacy. */
public class PaymentProofRequiredException extends BusinessRuleException {

    /** Creates the stable payment-gate error. */
    public PaymentProofRequiredException() {
        super("PAYMENT_PROOF_REQUIRED", "Không thể xuất thuốc khi chưa có bằng chứng thanh toán");
    }
}
