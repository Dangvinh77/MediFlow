package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;

/** Frees seats held by registrations nobody paid for in time. */
@Component
@ConditionalOnClinicIntake
public class OutpatientHoldExpiryWorker {
    private static final Logger log = LoggerFactory.getLogger(OutpatientHoldExpiryWorker.class);
    private static final int BATCH_LIMIT = 50;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final OutpatientRegistrationApplicationService service;
    private final Clock clock;

    public OutpatientHoldExpiryWorker(OutpatientRegistrationRepositoryPort registrations,
            OutpatientRegistrationApplicationService service, @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.registrations = registrations;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mediflow.clinical.hold-expiry.poll-interval:30000}")
    public void poll() {
        runOnce();
    }

    /** One bounded pass; a registration that fails is retried on the next pass without blocking the others. */
    public void runOnce() {
        for (UUID registrationId : registrations.findExpiredHeld(clock.instant(), BATCH_LIMIT)) {
            try {
                service.expireHeldReservation(registrationId);
            } catch (RuntimeException failure) {
                log.warn("Could not expire held registration {}: {}", registrationId, failure.getMessage());
            }
        }
    }
}
