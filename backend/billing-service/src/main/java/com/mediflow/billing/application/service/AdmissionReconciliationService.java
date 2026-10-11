package com.mediflow.billing.application.service;

import java.util.UUID;
import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementReconciledPayload;
import com.mediflow.billing.application.port.in.ReconcileAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.AdmissionReconciliationStorePort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.common.exception.ResourceNotFoundException;

/** Persisted arithmetic only: does not allocate cash, grant debt/waiver or finalize an admission. */
public final class AdmissionReconciliationService implements ReconcileAdmissionUseCase {
    private final AdmissionLedgerReadPort ledger;
    private final AdmissionReconciliationStorePort store;
    private final LedgerEventPort events;
    private final java.util.function.Supplier<UUID> ids;
    public AdmissionReconciliationService(AdmissionLedgerReadPort ledger,
            AdmissionReconciliationStorePort store, LedgerEventPort events) {
        this(ledger, store, events, UUID::randomUUID);
    }
    public AdmissionReconciliationService(AdmissionLedgerReadPort ledger,
            AdmissionReconciliationStorePort store, LedgerEventPort events, java.util.function.Supplier<UUID> ids) {
        this.ledger = ledger; this.store = store; this.events = events; this.ids = ids;
    }
    @Override public SettlementReconciledPayload reconcile(ReconcileAdmissionCommand command) {
        var replay = store.lockAndFindReplay(command);
        if (replay.isPresent()) return replay.get();
        var revision = store.lockAndFindRevision(command.accountId());
        var snapshot = ledger.read(command.accountId()).orElseThrow(() -> new ResourceNotFoundException(
                "BILLING_ACCOUNT_NOT_FOUND", "Billing account not found"));
        if (!command.accountId().equals(snapshot.accountId())
                || !(snapshot.accountStatus() == AccountStatus.CHARGE_CLOSED
                    || snapshot.accountStatus() == AccountStatus.SETTLEMENT_PENDING)
                || (revision.previousAt() != null && snapshot.observedAt().isBefore(revision.previousAt()))) {
            throw new BillingRuleException("BILLING_RECONCILIATION_NOT_READY", "Account is not ready for reconciliation");
        }
        var totals = snapshot.reconcile();
        var payload = new SettlementReconciledPayload(ids.get(), revision.version(), revision.previousId(),
                snapshot.accountId(), snapshot.admissionId(), snapshot.patientId(), snapshot.departmentId(), snapshot.currency(),
                totals.grossAmount(), totals.insuranceAmount(), totals.patientLiability(), totals.completedPayments(),
                totals.completedRefunds(), totals.completedReversals(), totals.netAllocatedPayments(), totals.balance(),
                totals.estimatedOutcome().name(), snapshot.observedAt(), false);
        store.save(command, payload);
        events.appendHeld(snapshot.accountId(), new LedgerIntegrationEvent(ids.get(), "settlement.reconciled", 1,
                payload.reconciledAt(), payload.settlementId().toString(), "billing-service", payload));
        return payload;
    }
}
