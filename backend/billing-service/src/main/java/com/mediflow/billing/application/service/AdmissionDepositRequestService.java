package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import com.mediflow.billing.application.port.in.IssueAdmissionDepositRequestUseCase;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestRepositoryPort;
import java.util.UUID;

/** CONTRACT-CARE-BILLING-01 / HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT — one
 * {@code admission.deposit.requested} fact issues one ADMISSION_DEPOSIT request, Inpatient's own
 * {@code suggestedAmount}, no charge attached (a deposit is cash/liability, never an earned charge).
 * Transaction wrapper is infrastructure-owned. Replays never re-amount the original operation. */
public final class AdmissionDepositRequestService implements IssueAdmissionDepositRequestUseCase {
    private final AdmissionDepositRequestRepositoryPort repository;

    public AdmissionDepositRequestService(AdmissionDepositRequestRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public UUID issue(AdmissionDepositRequestCommand command) {
        repository.claimDelivery(command);
        var recorded = repository.lockRecordedSource(command);
        if (recorded.isPresent()) {
            return recorded.get();
        }
        UUID account = repository.openAndLockExactAccount(command);
        return repository.saveRequest(command, account);
    }
}
