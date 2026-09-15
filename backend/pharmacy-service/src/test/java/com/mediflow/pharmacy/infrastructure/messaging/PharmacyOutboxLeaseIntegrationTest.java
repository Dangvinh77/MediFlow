package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL verification for multi-replica outbox claim and lease ownership. */
@DataJpaTest
@Import({PharmacyOutboxClaimService.class, PharmacyOutboxLeaseIntegrationTest.ClockConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PharmacyOutboxLeaseIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PharmacyEventOutboxJpaRepository repository;

    @Autowired
    private PharmacyOutboxClaimService claimService;

    /** Keeps each test independent while respecting the outbox table's immutable payload. */
    @AfterEach
    void cleanRows() {
        repository.deleteAllInBatch();
    }

    /** Two concurrent replicas claim disjoint rows with PostgreSQL SKIP LOCKED semantics. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoDispatchers_eachRowOwnedOncePerLease() throws Exception {
        List<UUID> eventIds = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> saveEvent("prescription.created-" + index).getEventId())
                .toList();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<PharmacyEventOutboxJpaEntity>> first = executor.submit(
                    () -> claimAfterStart(ready, start, "replica-a"));
            Future<List<PharmacyEventOutboxJpaEntity>> second = executor.submit(
                    () -> claimAfterStart(ready, start, "replica-b"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<UUID> firstIds = ids(first.get(10, TimeUnit.SECONDS));
            Set<UUID> secondIds = ids(second.get(10, TimeUnit.SECONDS));

            Set<UUID> claimedIds = new HashSet<>(firstIds);
            claimedIds.addAll(secondIds);
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            assertThat(claimedIds).containsExactlyInAnyOrderElementsOf(eventIds);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /** An expired lease can be reclaimed by another replica without changing event identity. */
    @Test
    void expiredLease_isReclaimableByAnotherOwner() {
        PharmacyEventOutboxJpaEntity event = saveEvent("prescription.filled");
        assertThat(claimService.claim(1, "replica-a")).hasSize(1);

        event.setLockedAt(NOW.minusSeconds(60));
        repository.saveAndFlush(event);

        assertThat(claimService.claim(1, "replica-b"))
                .singleElement()
                .extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .isEqualTo(event.getEventId());
        assertThat(repository.findById(event.getEventId()).orElseThrow().getLockedBy())
                .isEqualTo("replica-b");
    }

    /** A foreign replica cannot mark a leased row as published. */
    @Test
    void ownerGuard_preventsForeignPublish() {
        PharmacyEventOutboxJpaEntity event = saveEvent("prescription.created");
        claimService.claim(1, "replica-a");

        assertThat(repository.markPublishedIfOwned(event.getEventId(), "replica-b", NOW)).isZero();
        assertThat(repository.findById(event.getEventId()).orElseThrow().getPublishedAt()).isNull();
    }

    /** A leased critical predecessor prevents another replica from claiming its follower. */
    @Test
    void leasedPredecessor_blocksFollowerOnAnotherReplica() {
        UUID aggregateId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity predecessor = saveEvent(
                "prescription.created", aggregateId, NOW.minusSeconds(1));
        PharmacyEventOutboxJpaEntity follower = saveEvent(
                "prescription.filled", aggregateId, NOW);

        assertThat(claimService.claim(1, "replica-a"))
                .extracting(PharmacyEventOutboxJpaEntity::getEventId)
                .containsExactly(predecessor.getEventId());
        assertThat(claimService.claim(1, "replica-b")).isEmpty();
        assertThat(repository.findById(follower.getEventId()).orElseThrow().getLockedBy()).isNull();
    }

    private PharmacyEventOutboxJpaEntity saveEvent(String routingKey) {
        return saveEvent(routingKey, UUID.randomUUID(), NOW);
    }

    private PharmacyEventOutboxJpaEntity saveEvent(
            String routingKey, UUID aggregateId, Instant createdAt) {
        PharmacyEventOutboxJpaEntity event = new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), routingKey, aggregateId, "{\"eventId\":\"immutable\"}");
        event.setCreatedAt(createdAt);
        event.setAvailableAt(NOW);
        return repository.saveAndFlush(event);
    }

    private List<PharmacyEventOutboxJpaEntity> claimAfterStart(
            CountDownLatch ready, CountDownLatch start, String owner) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for concurrent claim start");
        }
        return claimService.claim(2, owner);
    }

    private Set<UUID> ids(List<PharmacyEventOutboxJpaEntity> rows) {
        return rows.stream().map(PharmacyEventOutboxJpaEntity::getEventId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    /** Supplies a deterministic business clock to the claim service. */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
