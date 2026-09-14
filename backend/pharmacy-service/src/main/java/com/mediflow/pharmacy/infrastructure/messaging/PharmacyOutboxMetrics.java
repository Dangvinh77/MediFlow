package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes pending-count and oldest-age gauges for the pharmacy outbox. */
@Component
@ConditionalOnProperty(name = "mediflow.pharmacy.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PharmacyOutboxMetrics {

    private final PharmacyEventOutboxJpaRepository repository;
    private final Clock clock;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    /** Registers gauges and performs an initial refresh. */
    public PharmacyOutboxMetrics(PharmacyEventOutboxJpaRepository repository,
            MeterRegistry meterRegistry, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        Gauge.builder("mediflow.pharmacy.outbox.pending", pending, AtomicLong::doubleValue)
                .description("Pending pharmacy outbox rows").register(meterRegistry);
        Gauge.builder("mediflow.pharmacy.outbox.oldest-age-seconds", oldestAgeSeconds,
                AtomicLong::doubleValue).description("Age of oldest pending outbox row")
                .register(meterRegistry);
    }

    /** Refreshes gauges without holding locks or a broker transaction. */
    @Scheduled(fixedDelayString = "${mediflow.pharmacy.outbox.metrics-refresh-ms:30000}")
    public void refresh() {
        pending.set(repository.countByPublishedAtIsNull());
        oldestAgeSeconds.set(repository.findFirstByPublishedAtIsNullOrderByCreatedAtAsc()
                .map(row -> Math.max(0L, Instant.now(clock).getEpochSecond()
                        - row.getCreatedAt().getEpochSecond()))
                .orElse(0L));
    }
}
