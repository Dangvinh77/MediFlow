package com.mediflow.billing.application.service;
import com.mediflow.billing.application.dto.response.ScheduledPaymentQueueItem;
import com.mediflow.billing.application.port.in.FindScheduledPaymentQueueUseCase;
import com.mediflow.billing.application.port.out.ScheduledPaymentQueuePort;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import java.util.UUID;
public final class FindScheduledPaymentQueueService implements FindScheduledPaymentQueueUseCase {
    private final ScheduledPaymentQueuePort queue;
    public FindScheduledPaymentQueueService(ScheduledPaymentQueuePort queue) { this.queue = queue; }
    @Override public PageResult<ScheduledPaymentQueueItem> find(UUID patientId, PageQuery page) {
        return queue.find(patientId, PageQuery.of(page.page(), page.size()));
    }
}
