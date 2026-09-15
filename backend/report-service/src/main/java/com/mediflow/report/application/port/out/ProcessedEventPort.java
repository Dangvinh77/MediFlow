package com.mediflow.report.application.port.out;

import java.util.UUID;

/** Atomic insert-only claim boundary for Rabbit event idempotency. */
public interface ProcessedEventPort {

    /** Returns true only for the consumer that successfully claims this event id. */
    boolean claimIfAbsent(UUID eventId, String routingKey);
}
