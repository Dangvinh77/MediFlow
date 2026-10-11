package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand;
import com.mediflow.billing.application.port.in.ApplyPrescriptionTerminalUseCase;
import com.mediflow.billing.application.port.out.PrescriptionTerminalRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.time.Clock;

/** An operational failure creates a refund obligation, never a fabricated completed refund. */
public final class PrescriptionTerminalService implements ApplyPrescriptionTerminalUseCase {
    private final PrescriptionTerminalRepositoryPort repository;
    private final Clock clock;
    public PrescriptionTerminalService(PrescriptionTerminalRepositoryPort repository, Clock clock) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.clock = java.util.Objects.requireNonNull(clock);
    }
    @Override public void apply(PrescriptionTerminalCommand command) {
        if (command.snapshot().createdAt().isAfter(clock.instant().plusSeconds(5)))
            throw new BillingRuleException("BILLING_PRESCRIPTION_TERMINAL_FUTURE", "BILLING_PRESCRIPTION_TERMINAL_FUTURE");
        repository.receive(command);
    }
}
