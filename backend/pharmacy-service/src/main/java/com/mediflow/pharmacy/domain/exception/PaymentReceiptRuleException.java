package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Nghiệp vụ không hợp lệ trong vòng đời payment receipt. */
public class PaymentReceiptRuleException extends BusinessRuleException {

    /**
     * @param code mã lỗi ổn định để map ra API/event
     * @param message thông điệp chẩn đoán
     */
    public PaymentReceiptRuleException(String code, String message) {
        super(code, message);
    }
}
