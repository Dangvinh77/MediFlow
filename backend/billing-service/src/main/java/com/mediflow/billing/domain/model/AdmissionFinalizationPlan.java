package com.mediflow.billing.domain.model;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Final-only recognition boundary. Estimates, payments and allocations alone cannot enter it. */
public final class AdmissionFinalizationPlan {
    private final AdmissionLedgerSnapshot.Totals totals;
    private final List<PerformedCharge> performedCharges;
    private final Map<UUID,BigDecimal> earnedByDepartment;
    private final Map<UUID,BigDecimal> depositReleaseByOriginal;

    private AdmissionFinalizationPlan(AdmissionLedgerSnapshot.Totals totals,List<PerformedCharge> performedCharges,
            Map<UUID,BigDecimal> earnedByDepartment,Map<UUID,BigDecimal> depositReleaseByOriginal) {
        this.totals=totals;
        this.performedCharges=List.copyOf(performedCharges);
        this.earnedByDepartment=Map.copyOf(earnedByDepartment);
        this.depositReleaseByOriginal=Map.copyOf(depositReleaseByOriginal);
    }
    public AdmissionLedgerSnapshot.Totals totals(){return totals;}
    public List<PerformedCharge> performedCharges(){return performedCharges;}
    public Map<UUID,BigDecimal> earnedByDepartment(){return earnedByDepartment;}
    public Map<UUID,BigDecimal> depositReleaseByOriginal(){return depositReleaseByOriginal;}

    /** Proof is supplied from Billing's previously accepted producer receipts, never from a client. */
    public record PerformedCharge(UUID chargeId,UUID departmentId,String sourceType,UUID sourceId,
            UUID performedOperationId,String performedFingerprint,Instant performedAt) { }

    public static AdmissionFinalizationPlan prepare(AdmissionLedgerSnapshot snapshot,
            Instant medicalApprovedAt,List<PerformedCharge> proofs) {
        if(snapshot==null || medicalApprovedAt==null || proofs==null || medicalApprovedAt.isAfter(snapshot.observedAt())
                || snapshot.chargeClosedAt()==null || snapshot.chargeClosedAt().isBefore(medicalApprovedAt)
                || !(snapshot.accountStatus()==AccountStatus.CHARGE_CLOSED || snapshot.accountStatus()==AccountStatus.SETTLEMENT_PENDING))
            throw invalid("BILLING_FINAL_SETTLEMENT_NOT_READY");
        var totals=snapshot.reconcile();
        // Insurance and approved debt/waiver need explicit persisted per-charge/approval authority.
        // This paid-in-full path must not distribute them by account department or invent approval.
        if(totals.insuranceAmount().signum()!=0)throw invalid("BILLING_FINAL_INSURANCE_ATTRIBUTION_UNSUPPORTED");
        if(totals.completedReversals().signum()!=0)throw invalid("BILLING_FINAL_REVERSAL_CONTRACT_UNSUPPORTED");
        if(totals.balance().signum()!=0 || totals.unappliedNetPayments().signum()!=0
                || totals.unallocatedDeposit().signum()!=0)throw invalid("BILLING_FINAL_SETTLEMENT_MONEY_UNRESOLVED");
        var byCharge=new HashMap<UUID,PerformedCharge>();
        for(var proof:proofs) {
            if(proof==null || proof.chargeId()==null || proof.departmentId()==null || proof.sourceId()==null || proof.performedOperationId()==null
                    || !("PRESCRIPTION".equals(proof.sourceType()) || "SURGERY".equals(proof.sourceType()))
                    || proof.performedFingerprint()==null || !proof.performedFingerprint().matches("[0-9a-f]{64}")
                    || proof.performedAt()==null || proof.performedAt().isAfter(medicalApprovedAt)
                    || byCharge.putIfAbsent(proof.chargeId(),proof)!=null)throw invalid("BILLING_FINAL_PERFORMED_PROOF_INVALID");
        }
        var earned=new HashMap<UUID,BigDecimal>();
        var posted=snapshot.charges().stream().filter(line->line.status()==ChargeStatus.POSTED).toList();
        if(posted.isEmpty() || posted.size()!=proofs.size())throw invalid("BILLING_FINAL_PERFORMED_PROOF_INCOMPLETE");
        for(var charge:posted) {
            var proof=byCharge.get(charge.id());
            if(proof==null)throw invalid("BILLING_FINAL_PERFORMED_PROOF_INCOMPLETE");
            earned.merge(proof.departmentId(),charge.grossAmount(),BigDecimal::add);
        }
        var allocatedByTransaction=new HashMap<UUID,BigDecimal>();
        snapshot.allocations().forEach(line->allocatedByTransaction.merge(line.transactionId(),line.amount(),BigDecimal::add));
        var released=new HashMap<UUID,BigDecimal>();
        for(var transaction:snapshot.transactions()) {
            if(transaction.status()!=PaymentTransactionStatus.COMPLETED || transaction.classification()!=PaymentClassification.ADMISSION_DEPOSIT)continue;
            var amount=allocatedByTransaction.getOrDefault(transaction.id(),BigDecimal.ZERO);
            if(transaction.type()==PaymentTransactionType.PAYMENT)released.merge(transaction.id(),amount,BigDecimal::add);
            else released.merge(transaction.originalId(),amount.negate(),BigDecimal::add);
        }
        if(released.values().stream().anyMatch(amount->amount.signum()<0))throw invalid("BILLING_FINAL_DEPOSIT_RELEASE_INVALID");
        released.entrySet().removeIf(entry->entry.getValue().signum()==0);
        return new AdmissionFinalizationPlan(totals,proofs,earned,released);
    }
    private static BillingRuleException invalid(String code){return new BillingRuleException(code,code);}
}
