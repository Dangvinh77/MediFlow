package com.mediflow.billing.application.dto.response;

import java.util.UUID;

/** Result of an administrator-triggered Billing outbox replay. */
public record OutboxReplayResult(UUID eventId, boolean replayed) {
}
