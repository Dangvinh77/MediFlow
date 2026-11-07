package com.mediflow.billing.application.service;

import java.util.UUID;

import com.mediflow.billing.application.dto.response.PaymentHistoryItem;
import com.mediflow.billing.application.port.in.FindPaymentHistoryUseCase;
import com.mediflow.billing.application.port.out.PaymentHistoryPort;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/** Read-only cashier history taken from the ledger, the record of money actually moved. */
public final class PaymentHistoryService implements FindPaymentHistoryUseCase {
    private final PaymentHistoryPort history;

    public PaymentHistoryService(PaymentHistoryPort history) {
        this.history = history;
    }

    @Override
    public PageResult<PaymentHistoryItem> find(UUID patientId, PageQuery page) {
        return history.findCompleted(patientId, page);
    }
}
