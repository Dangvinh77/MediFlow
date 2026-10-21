package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.OutpatientCancellationCommand;
import com.mediflow.billing.application.port.in.ProcessOutpatientCancellationUseCase;
import com.mediflow.billing.application.port.out.OutpatientCancellationRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.time.Clock;

public final class OutpatientCancellationService implements ProcessOutpatientCancellationUseCase {
    private final OutpatientCancellationRepositoryPort repository;
    private final Clock clock;
    public OutpatientCancellationService(OutpatientCancellationRepositoryPort repository, Clock clock) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.clock = java.util.Objects.requireNonNull(clock);
    }
    @Override public void receive(OutpatientCancellationCommand command) {
        if (command.cancelledAt().isAfter(clock.instant().plusSeconds(5)))
            throw new BillingRuleException("BILLING_OUTPATIENT_CANCELLATION_FUTURE", "BILLING_OUTPATIENT_CANCELLATION_FUTURE");
        repository.receive(command);
    }
}
