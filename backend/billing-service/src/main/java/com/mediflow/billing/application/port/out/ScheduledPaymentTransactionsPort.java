package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Short local Billing transactions surrounding the Clinical network calls. */
public interface ScheduledPaymentTransactionsPort {
    ScheduledPaymentOperation prepare(UUID paymentRequestId, ScheduledPaymentCommand command, UUID actorAccountId,
                                      String idempotencyKey, String commandFingerprint, String correlationId, Instant now);
    ScheduledPaymentOperation recordLease(UUID operationId, ScheduledPaymentLease lease, Instant now);
    ScheduledPaymentOperation complete(UUID operationId, String correlationId, Instant now);
    ScheduledPaymentResult closeAcquireRejected(UUID operationId, String reason, Instant now);
    ScheduledPaymentResult closeIfUnpaid(UUID operationId, ClosePaymentCommand command, Instant now);
    ScheduledPaymentResult currentResult(UUID operationId);
    ScheduledPaymentOperation findOperation(UUID operationId);
    List<ScheduledPaymentOperation> recoverable(int limit);
    void markResolutionApplied(UUID operationId, Instant now);

    record ClosePaymentCommand(UUID registrationId, UUID reservationId, long fenceToken, String reason) { }
}
