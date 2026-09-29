package com.mediflow.surgery.infrastructure.messaging;

import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Sends committed outbox bytes outside a database transaction and records the broker outcome. */
public class SurgeryOutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SurgeryOutboxDispatcher.class);

    private final TransactionTemplate transactions;
    private final SurgeryOutboxPort outbox;
    private final SurgeryEventPublisherPort publisher;
    private final SurgeryClockPort clock;
    private final Duration lease;
    private final int batchSize;

    public SurgeryOutboxDispatcher(
            TransactionTemplate transactions,
            SurgeryOutboxPort outbox,
            SurgeryEventPublisherPort publisher,
            SurgeryClockPort clock,
            Duration lease,
            int batchSize) {
        if (transactions == null || outbox == null || publisher == null || clock == null
                || lease == null || lease.isZero() || lease.isNegative()
                || lease.compareTo(Duration.ofMinutes(10)) > 0 || batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Surgery outbox dispatcher settings are invalid");
        }
        this.transactions = transactions;
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.lease = lease;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${mediflow.surgery.messaging.producer.poll-interval-ms:1000}")
    public void dispatchAvailable() {
        for (int sent = 0; sent < batchSize; sent++) {
            if (!dispatchOne()) return;
        }
    }

    /** Claims and commits first, performs network I/O second, then records the outcome separately. */
    public boolean dispatchOne() {
        Instant claimedAt = clock.now();
        Optional<SurgeryOutboxPort.Delivery> claimed = transactions.execute(
                status -> outbox.claimNext(claimedAt, lease));
        if (claimed == null || claimed.isEmpty()) return false;

        SurgeryOutboxPort.Delivery delivery = claimed.orElseThrow();
        try {
            publisher.publish(new SurgeryEventPublisherPort.OutgoingMessage(
                    delivery.eventId(), delivery.eventType(), delivery.eventVersion(),
                    delivery.correlationId(), delivery.payload()));
            Instant confirmedAt = clock.now();
            boolean recorded = Boolean.TRUE.equals(transactions.execute(status ->
                    outbox.markPublished(delivery.eventId(), delivery.attemptToken(), confirmedAt)));
            if (!recorded) {
                LOGGER.warn("Surgery outbox confirm lost its lease eventId={}", delivery.eventId());
            }
        } catch (SurgeryEventPublishException exception) {
            recordFailure(delivery, exception.getMessage(), exception.returned());
        } catch (RuntimeException exception) {
            recordFailure(delivery, safeReason(exception), false);
        }
        return true;
    }

    private void recordFailure(SurgeryOutboxPort.Delivery delivery, String reason, boolean returned) {
        String boundedReason = reason == null || reason.isBlank()
                ? "PUBLISH_FAILED" : reason.substring(0, Math.min(reason.length(), 900));
        Instant failedAt = clock.now();
        boolean recorded = Boolean.TRUE.equals(transactions.execute(status -> returned
                ? outbox.markReturned(delivery.eventId(), delivery.attemptToken(), boundedReason, failedAt)
                : outbox.retry(delivery.eventId(), delivery.attemptToken(), boundedReason, failedAt)));
        LOGGER.warn("Surgery outbox publish failed eventId={} returned={} stateRecorded={}",
                delivery.eventId(), returned, recorded);
    }

    private static String safeReason(RuntimeException exception) {
        String reason = exception.getClass().getSimpleName();
        return reason.isBlank() ? "PUBLISH_FAILED" : reason;
    }
}
