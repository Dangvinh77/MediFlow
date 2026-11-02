package com.mediflow.billing.application.service;

import java.util.UUID;

import com.mediflow.billing.application.dto.command.PaymentRequestSourcePurpose;
import com.mediflow.billing.application.dto.response.PaymentRequestQueueItem;
import com.mediflow.billing.application.port.in.FindPaymentRequestQueueUseCase;
import com.mediflow.billing.application.port.out.PaymentRequestQueuePort;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/** Queue membership is a read hint, never authority to collect a payment. */
public final class PaymentRequestQueueService implements FindPaymentRequestQueueUseCase {
    private final PaymentRequestQueuePort queue;

    public PaymentRequestQueueService(PaymentRequestQueuePort queue) {
        this.queue = queue;
    }

    @Override
    public PageResult<PaymentRequestQueueItem> find(UUID patientId, UUID careEpisodeId,
            PaymentRequestSourcePurpose purpose, PageQuery page) {
        if (purpose == PaymentRequestSourcePurpose.EXAM) {
            throw new IllegalArgumentException("Only Lab and prescription requests belong to this queue");
        }
        return queue.findQueue(patientId, careEpisodeId, purpose, PageQuery.of(page.page(), page.size()));
    }
}
