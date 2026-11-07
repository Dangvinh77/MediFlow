package com.mediflow.billing.application.port.out;

import java.util.UUID;

import com.mediflow.billing.application.dto.response.PaymentHistoryItem;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

public interface PaymentHistoryPort {
    PageResult<PaymentHistoryItem> findCompleted(UUID patientId, PageQuery page);
}
