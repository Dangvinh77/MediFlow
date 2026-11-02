package com.mediflow.billing.application.port.in;

import java.util.UUID;

import com.mediflow.billing.application.dto.command.PaymentRequestSourcePurpose;
import com.mediflow.billing.application.dto.response.PaymentRequestQueueItem;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

public interface FindPaymentRequestQueueUseCase {
    PageResult<PaymentRequestQueueItem> find(UUID patientId, UUID careEpisodeId,
            PaymentRequestSourcePurpose purpose, PageQuery page);
}
