package com.mediflow.pharmacy.infrastructure.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
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
     * Creates the scheduler use-case implementation with the configured batch limit.
     *
     * @param reservationRepository repository adapter for reservation candidates
     * @param expireTransaction transaction boundary for one prescription
     * @param clock shared UTC business clock
     * @param batchSize maximum candidates processed per scheduler run
     * @return configured expiry service
     */
    @Bean
    public ReleaseExpiredReservationsService releaseExpiredReservationsService(
            StockReservationRepositoryPort reservationRepository,
            ExpirePrescriptionTransaction expireTransaction,
            Clock clock,
            @Value("${mediflow.pharmacy.reservation.batch-size:100}") int batchSize) {
        return new ReleaseExpiredReservationsService(
                reservationRepository, expireTransaction, clock, batchSize);
    }
}
