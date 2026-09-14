package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseClaim;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseRepositoryPort;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacySchedulerLeaseJpaRepository;

/** PostgreSQL proof that scheduler lease ownership and cursor updates are atomic. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.pharmacy.reservation.release-cron=-",
        "mediflow.pharmacy.reservation.reconciliation-cron=-"
})
@Testcontainers(disabledWithoutDocker = true)
class PharmacySchedulerLeasePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationExpiryLeaseRepositoryPort leases;

    @Autowired
    private PharmacySchedulerLeaseJpaRepository leaseRepository;

    /** Removes only the scheduler state created by these tests. */
    @AfterEach
    void clean() {
        leaseRepository.deleteAllInBatch();
    }

    /** A second owner cannot claim a live lease, while the first owner can advance the cursor. */
    @Test
    void twoOwners_onlyOneClaims_andOwnerAdvancesCursor() {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        UUID cursor = UUID.randomUUID();

        ReservationExpiryLeaseClaim first = leases.tryAcquire(
                "reservation-expiry", "a", now, Duration.ofMinutes(5)).orElseThrow();
        assertThat(first.cursor()).isNull();
        assertThat(first.leaseToken()).isNotNull();
        assertThat(leases.tryAcquire("reservation-expiry", "a", now, Duration.ofMinutes(5)))
                .isEmpty();
        assertThat(leases.tryAcquire("reservation-expiry", "b", now, Duration.ofMinutes(5)))
                .isEmpty();
        assertThat(leases.advance("reservation-expiry", "b", first.leaseToken(), cursor, now)).isFalse();
        assertThat(leases.advance("reservation-expiry", "a", first.leaseToken(), cursor, now)).isTrue();
        leases.release("reservation-expiry", "a", first.leaseToken(), now);

        assertThat(leases.tryAcquire("reservation-expiry", "b", now, Duration.ofMinutes(5)))
                .hasValueSatisfying(claim -> {
                    assertThat(claim.cursor()).isEqualTo(cursor);
                    assertThat(claim.leaseToken()).isNotEqualTo(first.leaseToken());
                });
    }

    /** An expired lease is reclaimable without losing its cursor. */
    @Test
    void expiredLease_canBeReclaimed() {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        UUID cursor = UUID.randomUUID();
        ReservationExpiryLeaseClaim first = leases.tryAcquire(
                "reservation-expiry", "a", now, Duration.ofSeconds(1)).orElseThrow();
        assertThat(first.cursor()).isNull();
        assertThat(leases.advance("reservation-expiry", "a", first.leaseToken(), cursor, now)).isTrue();

        Instant later = now.plusSeconds(2);
        assertThat(leases.tryAcquire("reservation-expiry", "b", later, Duration.ofMinutes(5)))
                .hasValueSatisfying(claim -> assertThat(claim.cursor()).isEqualTo(cursor));
    }
}
