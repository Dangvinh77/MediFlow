package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
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

    private PharmacyEventOutboxJpaEntity event(String routingKey) {
        return new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), routingKey, "{\"eventId\":\"immutable\"}");
    }
}
