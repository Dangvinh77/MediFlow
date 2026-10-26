package com.mediflow.billing.application.port.in;
import com.mediflow.billing.application.dto.response.ScheduledPaymentQueueItem;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import java.util.UUID;
public interface FindScheduledPaymentQueueUseCase {
    PageResult<ScheduledPaymentQueueItem> find(UUID patientId, PageQuery page);
}
