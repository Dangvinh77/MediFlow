package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import jakarta.persistence.EntityManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL integration coverage for retry quarantine, replay and retention boundaries. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PharmacyOutboxRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PharmacyEventOutboxJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    /** Keeps repository rows isolated without deleting any unrelated service data. */
    @AfterEach
    void cleanRows() {
        repository.deleteAllInBatch();
    }

    /** The final configured attempt quarantines the row and removes it from claimable results. */
    @Test
    void markFailureAtMaxAttempts_quarantinesRow() {
        PharmacyEventOutboxJpaEntity event = event("prescription.created");
        event.setAttempts(1);
        event.setLockedBy("replica-a");
        event.setLockedAt(NOW);
        repository.saveAndFlush(event);

        assertThat(repository.markFailureIfOwned(
                event.getEventId(), "replica-a", "broker timeout", NOW.plusSeconds(30), 2))
                .isOne();

        entityManager.clear();
        PharmacyEventOutboxJpaEntity stored = repository.findById(event.getEventId()).orElseThrow();
        assertThat(stored.getAttempts()).isEqualTo(2);
        assertThat(stored.getQuarantinedAt()).isNotNull();
        assertThat(stored.getLockedBy()).isNull();
        assertThat(repository.findClaimable(NOW, NOW.minusSeconds(30), 10)).isEmpty();
    }

    /** Replay clears quarantine and preserves the event id and payload for redelivery. */
    @Test
    void replay_clearsQuarantineWithoutChangingIdentity() {
        PharmacyEventOutboxJpaEntity event = event("prescription.filled");
        event.setAttempts(5);
        event.setQuarantinedAt(NOW);
        event.setLastError("permanent-looking broker error");
        repository.saveAndFlush(event);

        assertThat(repository.replay(event.getEventId(), NOW.plusSeconds(1))).isOne();

        entityManager.clear();
        PharmacyEventOutboxJpaEntity replayed = repository.findById(event.getEventId()).orElseThrow();
        assertThat(replayed.getEventId()).isEqualTo(event.getEventId());
        assertThat(replayed.getPayload()).isEqualTo(event.getPayload());
        assertThat(replayed.getQuarantinedAt()).isNull();
        assertThat(replayed.getLastError()).isNull();
        assertThat(replayed.getAttempts()).isZero();
        assertThat(replayed.getAvailableAt()).isEqualTo(NOW.plusSeconds(1));
    }

    /** Retention removes only published rows and leaves both pending and quarantined rows intact. */
    @Test
    void retention_deletesPublishedRowsOnly() {
        PharmacyEventOutboxJpaEntity published = event("prescription.created");
        published.setPublishedAt(NOW.minusSeconds(10));
        PharmacyEventOutboxJpaEntity pending = event("prescription.filled");
        pending.setCreatedAt(NOW.minusSeconds(20));
        PharmacyEventOutboxJpaEntity quarantined = event("prescription.cancelled");
        quarantined.setQuarantinedAt(NOW.minusSeconds(30));
        repository.saveAllAndFlush(java.util.List.of(published, pending, quarantined));

        assertThat(repository.deletePublishedBefore(NOW)).isOne();
        entityManager.clear();
        assertThat(repository.findById(published.getEventId())).isEmpty();
        assertThat(repository.findById(pending.getEventId())).isPresent();
        assertThat(repository.findById(quarantined.getEventId())).isPresent();
    }

    /** Retryable metrics queries exclude quarantined rows while retaining the oldest pending row. */
    @Test
    void retryableMetricsQueries_excludeQuarantinedRows() {
        PharmacyEventOutboxJpaEntity pending = event("prescription.created");
        pending.setCreatedAt(NOW.minusSeconds(20));
        PharmacyEventOutboxJpaEntity quarantined = event("prescription.failed");
        quarantined.setCreatedAt(NOW.minusSeconds(40));
        quarantined.setQuarantinedAt(NOW.minusSeconds(10));
        repository.saveAllAndFlush(java.util.List.of(pending, quarantined));

        assertThat(repository.countByPublishedAtIsNullAndQuarantinedAtIsNull()).isOne();
        assertThat(repository.findFirstByPublishedAtIsNullAndQuarantinedAtIsNullOrderByCreatedAtAsc())
                .get().extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .isEqualTo(pending.getEventId());
        assertThat(repository.countByPublishedAtIsNullAndQuarantinedAtIsNotNull()).isOne();
        assertThat(repository
                .findFirstByPublishedAtIsNullAndQuarantinedAtIsNotNullOrderByQuarantinedAtAsc())
                .get().extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .isEqualTo(quarantined.getEventId());
    }

    /** A retrying critical event blocks only later events of the same aggregate. */
    @Test
    void findClaimable_retryBackoffPreservesAggregateOrder() {
        UUID prescriptionId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity created = event("prescription.created");
        created.setAggregateId(prescriptionId);
        created.setCreatedAt(NOW.minusSeconds(20));
        created.setAvailableAt(NOW.plusSeconds(30));
        PharmacyEventOutboxJpaEntity filled = event("prescription.filled");
        filled.setAggregateId(prescriptionId);
        filled.setCreatedAt(NOW.minusSeconds(10));
        filled.setAvailableAt(NOW);
        PharmacyEventOutboxJpaEntity independent = event("prescription.created");
        independent.setCreatedAt(NOW.minusSeconds(5));
        independent.setAvailableAt(NOW);
        repository.saveAllAndFlush(java.util.List.of(created, filled, independent));

        assertThat(repository.findClaimable(NOW, NOW.minusSeconds(30), 10))
                .extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .containsExactly(independent.getEventId());
    }

    /** A quarantined predecessor blocks later critical events until an operator replays it. */
    @Test
    void findClaimable_quarantinedPredecessorPreservesAggregateOrder() {
        UUID prescriptionId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity created = event("prescription.created");
        created.setAggregateId(prescriptionId);
        created.setCreatedAt(NOW.minusSeconds(20));
        created.setQuarantinedAt(NOW.minusSeconds(10));
        PharmacyEventOutboxJpaEntity filled = event("prescription.filled");
        filled.setAggregateId(prescriptionId);
        filled.setCreatedAt(NOW.minusSeconds(5));
        filled.setAvailableAt(NOW);
        PharmacyEventOutboxJpaEntity independent = event("prescription.created");
        independent.setAggregateId(UUID.randomUUID());
        independent.setCreatedAt(NOW.minusSeconds(1));
        independent.setAvailableAt(NOW);
        repository.saveAllAndFlush(java.util.List.of(created, filled, independent));

        assertThat(repository.findClaimable(NOW, NOW.minusSeconds(30), 10))
                .extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .containsExactly(independent.getEventId());

        assertThat(repository.replay(created.getEventId(), NOW)).isOne();
        entityManager.clear();
        assertThat(repository.findClaimable(NOW, NOW.minusSeconds(30), 10))
                .extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .contains(created.getEventId())
                .doesNotContain(filled.getEventId());
    }

    private PharmacyEventOutboxJpaEntity event(String routingKey) {
        return new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), routingKey, UUID.randomUUID(), "{\"eventId\":\"immutable\"}");
    }
}
