package com.mediflow.billing.infrastructure.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.billing.infrastructure.persistence.repository.BillingEventOutboxJpaRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Operational gauges for pending and quarantined Billing integration events. */
@Component
@ConditionalOnProperty(name = {
        "mediflow.billing.outbox.enabled",
        "mediflow.billing.outbox.metrics-enabled"
}, havingValue = "true", matchIfMissing = true)
public class BillingOutboxMetrics {

    private final BillingEventOutboxJpaRepository repository;
    private final Clock clock;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong quarantined = new AtomicLong();
    private final AtomicLong oldestQuarantinedAgeSeconds = new AtomicLong();

    public BillingOutboxMetrics(BillingEventOutboxJpaRepository repository,
                                MeterRegistry meterRegistry,
                                Clock clock) {
        this.repository = repository;
        this.clock = clock;
        Gauge.builder("mediflow.billing.outbox.pending", pending, AtomicLong::doubleValue)
                .register(meterRegistry);
        Gauge.builder("mediflow.billing.outbox.oldest-age-seconds", oldestAgeSeconds,
                AtomicLong::doubleValue).register(meterRegistry);
        Gauge.builder("mediflow.billing.outbox.quarantined", quarantined,
                AtomicLong::doubleValue).register(meterRegistry);
        Gauge.builder("mediflow.billing.outbox.oldest-quarantined-age-seconds",
                oldestQuarantinedAgeSeconds, AtomicLong::doubleValue).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${mediflow.billing.outbox.metrics-refresh-ms:30000}")
    public void refresh() {
        Instant now = Instant.now(clock);
        pending.set(repository.countByPublishedAtIsNullAndQuarantinedAtIsNull());
        oldestAgeSeconds.set(repository
                .findFirstByPublishedAtIsNullAndQuarantinedAtIsNullOrderByCreatedAtAsc()
                .map(row -> Math.max(0, now.getEpochSecond() - row.getCreatedAt().getEpochSecond()))
                .orElse(0L));
        quarantined.set(repository.countByPublishedAtIsNullAndQuarantinedAtIsNotNull());
        oldestQuarantinedAgeSeconds.set(repository
                .findFirstByPublishedAtIsNullAndQuarantinedAtIsNotNullOrderByQuarantinedAtAsc()
                .map(row -> Math.max(0, now.getEpochSecond() - row.getQuarantinedAt().getEpochSecond()))
                .orElse(0L));
    }
}
