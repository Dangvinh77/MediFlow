package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
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
                UUID.randomUUID(), "prescription.created", UUID.randomUUID(), "{}");
        oldest.setCreatedAt(NOW.minusSeconds(42));
        PharmacyEventOutboxJpaEntity oldestQuarantined = new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), "prescription.filled", UUID.randomUUID(), "{}");
        oldestQuarantined.setQuarantinedAt(NOW.minusSeconds(84));
        when(repository.countByPublishedAtIsNullAndQuarantinedAtIsNull()).thenReturn(3L);
        when(repository.findFirstByPublishedAtIsNullAndQuarantinedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Optional.of(oldest));
        when(repository.countByPublishedAtIsNullAndQuarantinedAtIsNotNull()).thenReturn(2L);
        when(repository.findFirstByPublishedAtIsNullAndQuarantinedAtIsNotNullOrderByQuarantinedAtAsc())
                .thenReturn(Optional.of(oldestQuarantined));

        PharmacyOutboxMetrics metrics = new PharmacyOutboxMetrics(
                repository, registry, Clock.fixed(NOW, ZoneOffset.UTC));
        metrics.refresh();

        assertThat(registry.get("mediflow.pharmacy.outbox.pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("mediflow.pharmacy.outbox.oldest-age-seconds")
                .gauge().value()).isEqualTo(42.0);
        assertThat(registry.get("mediflow.pharmacy.outbox.quarantined").gauge().value())
                .isEqualTo(2.0);
        assertThat(registry.get("mediflow.pharmacy.outbox.oldest-quarantined-age-seconds")
                .gauge().value()).isEqualTo(84.0);
    }

    /** Gauges use only retryable rows, excluding quarantined poison events. */
    @Test
    void refresh_usesRetryableRowsOnly() {
        PharmacyEventOutboxJpaRepository repository = mock(PharmacyEventOutboxJpaRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(repository.countByPublishedAtIsNullAndQuarantinedAtIsNull()).thenReturn(1L);
        when(repository.findFirstByPublishedAtIsNullAndQuarantinedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Optional.empty());
        when(repository.countByPublishedAtIsNullAndQuarantinedAtIsNotNull()).thenReturn(0L);
        when(repository.findFirstByPublishedAtIsNullAndQuarantinedAtIsNotNullOrderByQuarantinedAtAsc())
                .thenReturn(Optional.empty());

        PharmacyOutboxMetrics metrics = new PharmacyOutboxMetrics(
                repository, registry, Clock.fixed(NOW, ZoneOffset.UTC));
        metrics.refresh();

        assertThat(registry.get("mediflow.pharmacy.outbox.pending").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("mediflow.pharmacy.outbox.oldest-age-seconds")
                .gauge().value()).isZero();
        assertThat(registry.get("mediflow.pharmacy.outbox.quarantined").gauge().value()).isZero();
        assertThat(registry.get("mediflow.pharmacy.outbox.oldest-quarantined-age-seconds")
                .gauge().value()).isZero();
    }
}
