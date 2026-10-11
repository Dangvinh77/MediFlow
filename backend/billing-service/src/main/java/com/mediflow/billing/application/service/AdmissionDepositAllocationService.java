package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;
import com.mediflow.billing.application.port.in.AllocateAdmissionDepositUseCase;
import com.mediflow.billing.application.port.out.AdmissionDepositAllocationStorePort;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.model.AdmissionDepositAllocation;
import com.mediflow.common.exception.ResourceNotFoundException;
import java.util.UUID;

/** Signed cashier intent is checked against persisted ledger budgets, never client supplied prices. */
public final class AdmissionDepositAllocationService implements AllocateAdmissionDepositUseCase {
    private final AdmissionDepositAllocationStorePort store;
    private final AdmissionLedgerReadPort ledger;
    private final LedgerEventPort events;
    private final java.util.function.Supplier<UUID> ids;

    public AdmissionDepositAllocationService(AdmissionDepositAllocationStorePort store,
            AdmissionLedgerReadPort ledger, LedgerEventPort events) {
        this(store, ledger, events, UUID::randomUUID);
    }

    public AdmissionDepositAllocationService(AdmissionDepositAllocationStorePort store,
            AdmissionLedgerReadPort ledger, LedgerEventPort events, java.util.function.Supplier<UUID> ids) {
        this.store = store; this.ledger = ledger; this.events = events;
        this.ids = ids;
    }

    @Override public DepositAllocatedPayload allocate(AllocateAdmissionDepositCommand command) {
        var replay = store.lockAndFindReplay(command);
        if (replay.isPresent()) return replay.get();
        store.lockMedicallyClosedAccount(command.accountId());
        var snapshot = ledger.read(command.accountId()).orElseThrow(() -> new ResourceNotFoundException(
                "BILLING_ACCOUNT_NOT_FOUND", "Billing account not found"));
        if (!command.accountId().equals(snapshot.accountId())) throw new IllegalArgumentException("Deposit account differs");
        var planned = AdmissionDepositAllocation.plan(snapshot, command.originalTransactionId(), command.amounts());
        var lines = planned.lines().stream().map(line -> new DepositAllocatedPayload.DepositAllocationLine(
                ids.get(), line.chargeId(), line.amount())).toList();
        var payload = new DepositAllocatedPayload(ids.get(), snapshot.accountId(), snapshot.admissionId(),
                snapshot.patientId(), command.originalTransactionId(), snapshot.currency(), planned.amount(), lines,
                snapshot.observedAt(), false);
        store.save(command, payload);
        events.appendHeld(snapshot.accountId(), new LedgerIntegrationEvent(ids.get(), "deposit.allocated", 1,
                payload.allocatedAt(), payload.allocationOperationId().toString(), "billing-service", payload));
        return payload;
    }
}
