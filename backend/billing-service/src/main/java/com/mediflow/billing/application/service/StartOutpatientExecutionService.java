package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand;
import com.mediflow.billing.application.dto.response.OutpatientExecutionStartDTO;
import com.mediflow.billing.application.port.in.StartOutpatientExecutionUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.application.port.out.OutpatientExecutionStartRepositoryPort;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Serializes the authoritative admission receipt with payment and refund on the owned account. */
public final class StartOutpatientExecutionService implements StartOutpatientExecutionUseCase {
    private final FinancialClearanceAuthorityPort authority;
    private final OutpatientExecutionStartRepositoryPort repository;
    private final Clock clock;
    public StartOutpatientExecutionService(FinancialClearanceAuthorityPort authority,
            OutpatientExecutionStartRepositoryPort repository, Clock clock) {
        this.authority = Objects.requireNonNull(authority);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }
    @Override public OutpatientExecutionStartDTO start(UUID clearanceId, StartOutpatientExecutionCommand command,
            String correlationId) {
        if (clearanceId == null || command == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120) throw new IllegalArgumentException("Invalid execution start context");
        UUID accountId = repository.lockAccount(clearanceId).orElseThrow(() -> new ResourceNotFoundException(
                "BILLING_CLEARANCE_NOT_FOUND", "Financial clearance not found"));
        var current = authority.find(clearanceId).orElseThrow(() -> new ResourceNotFoundException(
                "BILLING_CLEARANCE_NOT_FOUND", "Financial clearance not found"));
        require(current.eligible() && current.accountId().equals(accountId)
                && current.patientId().equals(command.patientId())
                && current.careEpisodeType().equals(command.careEpisodeType())
                && current.careEpisodeId().equals(command.careEpisodeId())
                && current.matchesSource(command.sourceType(), command.sourceId())
                && command.sourceId().equals(command.operationId()));
        var previous = repository.find(command.operationId());
        if (previous.isPresent()) {
            var receipt = previous.get();
            require(receipt.clearanceId().equals(clearanceId) && receipt.sourceType().equals(command.sourceType())
                    && receipt.sourceId().equals(command.sourceId()));
            return receipt;
        }
        return repository.save(clearanceId, accountId, command,
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }
    private static void require(boolean valid) {
        if (!valid) throw new DuplicateResourceException("BILLING_CLEARANCE_NOT_EXECUTABLE",
                "Financial clearance is no longer eligible for this service target");
    }
}
