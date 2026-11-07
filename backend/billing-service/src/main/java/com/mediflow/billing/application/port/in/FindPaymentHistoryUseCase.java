package com.mediflow.billing.application.port.in;

import java.util.UUID;

import com.mediflow.billing.application.dto.response.PaymentHistoryItem;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

public interface FindPaymentHistoryUseCase {
    /** Newest first; a null patient lists every patient. */
    PageResult<PaymentHistoryItem> find(UUID patientId, PageQuery page);
}
