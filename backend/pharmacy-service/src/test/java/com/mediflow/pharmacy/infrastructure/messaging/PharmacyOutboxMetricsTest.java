package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies outbox gauges expose pending count and oldest age without a database container. */
class PharmacyOutboxMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    /** Gauge refresh publishes deterministic values derived from the injected clock. */
    @Test
    void refresh_updatesPendingAndOldestAge() {
        PharmacyEventOutboxJpaRepository repository = mock(PharmacyEventOutboxJpaRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PharmacyEventOutboxJpaEntity oldest = new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), "prescription.created", "{}");
        oldest.setCreatedAt(NOW.minusSeconds(42));
        when(repository.countByPublishedAtIsNull()).thenReturn(3L);
        when(repository.findFirstByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(Optional.of(oldest));

        PharmacyOutboxMetrics metrics = new PharmacyOutboxMetrics(
                repository, registry, Clock.fixed(NOW, ZoneOffset.UTC));
        metrics.refresh();

        assertThat(registry.get("mediflow.pharmacy.outbox.pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("mediflow.pharmacy.outbox.oldest-age-seconds")
                .gauge().value()).isEqualTo(42.0);
    }
}
