package com.mediflow.clinical.application.port.out;

import java.util.UUID;

import com.mediflow.clinical.application.dto.authority.ScheduledPaymentSnapshot;

/** Private Billing read and row-locked close used by Clinical's recovery process. */
public interface ScheduledPaymentReconciliationPort {
    ScheduledPaymentSnapshot lookup(UUID paymentOperationId, UUID registrationId, UUID reservationId,
            String correlationId);

    ScheduledPaymentSnapshot closeIfUnpaid(UUID paymentOperationId, UUID registrationId, UUID reservationId,
            long fenceToken, String reason, String correlationId);
}
