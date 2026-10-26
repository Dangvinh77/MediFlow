package com.mediflow.billing.application.dto.response;
import java.time.Instant;
import java.util.UUID;
/** Discovery only. Select the registration to obtain a fresh amount and reservation revision. */
public record ScheduledPaymentQueueItem(UUID registrationId, UUID patientId, UUID departmentId,
        Instant createdAt, Instant expiresAt) {}
