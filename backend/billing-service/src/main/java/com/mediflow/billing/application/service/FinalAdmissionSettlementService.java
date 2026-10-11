package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementCompletedPayload;
import com.mediflow.billing.application.port.in.FinalizeAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.FinalAdmissionSettlementStorePort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AdmissionFinalizationPlan;
import com.mediflow.common.exception.ResourceNotFoundException;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Supplier;

/** Only supported paid-in-full finalization; it cannot invent insurance, debt or waiver authority. */
public final class FinalAdmissionSettlementService implements FinalizeAdmissionUseCase {
    private final FinalAdmissionSettlementStorePort store;
    private final AdmissionLedgerReadPort ledger;
    private final LedgerEventPort events;
    private final Supplier<UUID> ids;
    public FinalAdmissionSettlementService(FinalAdmissionSettlementStorePort store,AdmissionLedgerReadPort ledger,LedgerEventPort events) {
        this(store,ledger,events,UUID::randomUUID);
    }
    public FinalAdmissionSettlementService(FinalAdmissionSettlementStorePort store,AdmissionLedgerReadPort ledger,LedgerEventPort events,Supplier<UUID> ids) {
        this.store=store;this.ledger=ledger;this.events=events;this.ids=ids;
    }
    @Override public FinalSettlementRecognitionPayload finalizeAdmission(FinalizeAdmissionCommand command) {
        var replay=store.lockAndFindReplay(command);if(replay.isPresent())return replay.get();
        var evidence=store.lockAndReadEvidence(command.accountId());
        var snapshot=ledger.read(command.accountId()).orElseThrow(()->new ResourceNotFoundException("BILLING_ACCOUNT_NOT_FOUND","Billing account not found"));
        if(!command.accountId().equals(snapshot.accountId()))throw new BillingRuleException("BILLING_FINAL_SETTLEMENT_CONTEXT_MISMATCH","Exact account evidence required");
        var plan=AdmissionFinalizationPlan.prepare(snapshot,evidence.medicalApprovedAt(),evidence.performedCharges());
        validateAllocationEvidence(snapshot,evidence.allocations());
        var amounts=plan.totals();
        var recognizedIds=plan.performedCharges().stream().map(AdmissionFinalizationPlan.PerformedCharge::chargeId).collect(java.util.stream.Collectors.toSet());
        // Fully returned VOIDED lines have no earned operation; retain their cash in account totals,
        // not as unexplained allocations against a charge absent from this recognition.
        var recognizedAllocations=evidence.allocations().stream().filter(line->recognizedIds.contains(line.chargeId())).toList();
        var charges=plan.performedCharges().stream().sorted(Comparator.comparing(AdmissionFinalizationPlan.PerformedCharge::chargeId))
                .map(proof->new FinalSettlementRecognitionPayload.ChargeRecognition(proof.chargeId(),proof.departmentId(),proof.sourceType(),proof.sourceId(),
                        proof.performedOperationId(),proof.performedFingerprint(),proof.performedAt(),snapshot.charges().stream()
                        .filter(line->line.id().equals(proof.chargeId())).findFirst().orElseThrow().grossAmount())).toList();
        var releases=plan.depositReleaseByOriginal().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry->new FinalSettlementRecognitionPayload.DepositRelease(entry.getKey(),snapshot.departmentId(),entry.getValue())).toList();
        var payload=new FinalSettlementRecognitionPayload(ids.get(),1,null,ids.get(),1,null,snapshot.accountId(),snapshot.admissionId(),snapshot.patientId(),
                snapshot.departmentId(),snapshot.currency(),amounts.grossAmount(),amounts.insuranceAmount(),amounts.patientLiability(),amounts.completedPayments(),
                amounts.completedRefunds(),amounts.completedReversals(),amounts.netAllocatedPayments(),amounts.balance(),"PAID_IN_FULL",snapshot.observedAt(),true,
                charges,recognizedAllocations,releases);
        store.save(command,payload);
        // Both facts and final account state commit in the same caller transaction, all remain HELD.
        events.appendHeld(command.accountId(),new LedgerIntegrationEvent(ids.get(),"settlement.completed",1,payload.completedAt(),
                payload.settlementId().toString(),"billing-service",new SettlementCompletedPayload(payload.settlementId(),payload.admissionId(),payload.accountId(),
                payload.patientId(),payload.accountDepartmentId(),payload.grossAmount(),payload.insuranceAmount(),payload.patientLiability(),payload.completedPayments(),
                payload.completedRefunds(),payload.balance(),payload.outcome(),payload.completedAt())));
        events.appendHeld(command.accountId(),new LedgerIntegrationEvent(ids.get(),"settlement.recognition.completed",1,payload.completedAt(),
                payload.recognitionOperationId().toString(),"billing-service",payload));
        return payload;
    }
    private static void validateAllocationEvidence(com.mediflow.billing.domain.model.AdmissionLedgerSnapshot snapshot,
            java.util.List<FinalSettlementRecognitionPayload.AllocationEvidence> evidence) {
        if(evidence.size()!=snapshot.allocations().size())throw invalidEvidence();
        var transactions=new java.util.HashMap<UUID,com.mediflow.billing.domain.model.AdmissionLedgerSnapshot.TransactionLine>();
        snapshot.transactions().forEach(transaction->transactions.put(transaction.id(),transaction));
        var expected=new java.util.HashMap<String,java.math.BigDecimal>();
        snapshot.allocations().forEach(line->expected.put(line.transactionId()+":"+line.chargeId(),line.amount()));
        var ids=new java.util.HashSet<UUID>();
        for(var allocation:evidence) {
            if(allocation.allocationId()==null || !ids.add(allocation.allocationId()))throw invalidEvidence();
            var transaction=transactions.get(allocation.transactionId());
            var amount=expected.remove(allocation.transactionId()+":"+allocation.chargeId());
            if(transaction==null || amount==null || allocation.amount()==null || allocation.amount().compareTo(amount)!=0
                    || !java.util.Objects.equals(transaction.originalId(),allocation.originalTransactionId())
                    || !transaction.type().name().equals(allocation.transactionType())
                    || !transaction.classification().name().equals(allocation.classification()))throw invalidEvidence();
        }
        if(!expected.isEmpty())throw invalidEvidence();
    }
    private static BillingRuleException invalidEvidence(){return new BillingRuleException("BILLING_FINAL_ALLOCATION_EVIDENCE_INVALID","Exact original allocation evidence required");}
}
