package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.SurgeryCancellationCommand;
import com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort;
import java.time.Clock;
import java.util.UUID;

/** Calls participate in infrastructure-owned transactions; early facts remain durable before ACK. */
public final class SurgeryCancellationService {
    private final SurgeryCancellationRepositoryPort repository;
    private final Clock clock;

    public SurgeryCancellationService(SurgeryCancellationRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void receive(SurgeryCancellationCommand command) {
        if (command.cancelledAt().isAfter(clock.instant().plusSeconds(5)))
            throw new com.mediflow.billing.domain.exception.BillingRuleException(
                    "BILLING_SURGERY_CANCELLATION_TIME_INVALID", "Future cancellation cannot adjust current money");
        repository.claimAndLock(command);
        recover(command.surgeryCaseId());
    }

    public void recover(UUID surgeryCaseId) {
        repository.lockPending(surgeryCaseId).ifPresent(command -> {
            var now = clock.instant();
            if (!repository.apply(command)) repository.defer(surgeryCaseId, now.plusSeconds(60));
        });
    }
}
