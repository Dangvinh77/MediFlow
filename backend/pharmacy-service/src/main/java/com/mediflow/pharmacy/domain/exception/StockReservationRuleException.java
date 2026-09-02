package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/**
 * Quy tắc nghiệp vụ của giữ chỗ tồn kho (STOCK_RESERVATION).
 * Kế thừa {@link BusinessRuleException} của common → HTTP 422 qua GlobalExceptionHandler.
 */
public class StockReservationRuleException extends BusinessRuleException {
    public StockReservationRuleException(String code, String message) {
        super(code, message);
    }
}
