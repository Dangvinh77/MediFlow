package com.mediflow.billing.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Dùng chung cho mọi vi phạm quy tắc nghiệp vụ billing (mã {@code BILLING_*}), map HTTP 422. */
public class BillingRuleException extends BusinessRuleException {
    public BillingRuleException(String code, String message) {
        super(code, message);
    }
}
