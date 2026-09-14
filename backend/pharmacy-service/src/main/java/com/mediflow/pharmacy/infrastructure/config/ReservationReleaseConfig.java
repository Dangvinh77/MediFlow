package com.mediflow.pharmacy.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseRepositoryPort;
import com.mediflow.pharmacy.application.port.out.TransientFailureClassifierPort;
import com.mediflow.pharmacy.application.service.ExpirePrescriptionTransaction;
import com.mediflow.pharmacy.application.service.ReleaseExpiredReservationsService;

/**
 * Wires the reservation-expiry application service from infrastructure-owned configuration.
 *
 * <p>Keeping property binding here prevents Spring configuration annotations from leaking into
 * the application layer while still allowing a deterministic clock and validated batch size in
 * unit tests.</p>
 */
@Configuration
public class ReservationReleaseConfig {

    /**
     * Binds the reservation time-to-live used by prescription creation.
     *
     * <p>The application service deliberately depends on the JDK {@link Duration} value rather
     * than Spring's configuration API.  Exposing the validated property as an infrastructure
     * bean keeps that inward dependency boundary intact and makes the same value available in
     * full Spring Boot integration tests.</p>
     *
     * @param reservationTtl configured reservation lifetime (for example, {@code PT24H})
     * @return reservation lifetime shared by prescription use cases
     */
    @Bean
    public Duration reservationTtl(
            @Value("${mediflow.pharmacy.reservation.ttl:PT24H}") Duration reservationTtl) {
        if (reservationTtl.isZero() || reservationTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "mediflow.pharmacy.reservation.ttl must be positive");
        }
        return reservationTtl;
    }

    /**
     * Creates the scheduler use-case implementation with the configured batch limit.
     *
     * @param reservationRepository repository adapter for reservation candidates
     * @param leaseRepository durable scheduler lease adapter
     * @param expireTransaction transaction boundary for one prescription
     * @param clock shared UTC business clock
     * @param batchSize maximum candidates processed per scheduler run
     * @param leaseOwner unique scheduler instance identity
     * @param leaseDuration lease TTL
     * @param transientFailureClassifier classifier for retryable infrastructure failures
     * @return configured expiry service
     */
    @Bean
    public ReleaseExpiredReservationsService releaseExpiredReservationsService(
            StockReservationRepositoryPort reservationRepository,
            ReservationExpiryLeaseRepositoryPort leaseRepository,
            ExpirePrescriptionTransaction expireTransaction,
            Clock clock,
            @Value("${mediflow.pharmacy.reservation.batch-size:100}") int batchSize,
            @Value("${mediflow.pharmacy.reservation.lease-owner:${HOSTNAME:pharmacy-local}}") String leaseOwner,
            @Value("${mediflow.pharmacy.reservation.lease:PT5M}") Duration leaseDuration,
            TransientFailureClassifierPort transientFailureClassifier) {
        return new ReleaseExpiredReservationsService(
                reservationRepository, expireTransaction, clock, batchSize, leaseRepository,
                leaseOwner, leaseDuration, transientFailureClassifier);
    }
}
