package com.mediflow.report.domain.model;

import com.mediflow.report.domain.exception.ReportRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A single final-only earned operation, not a cumulative gross delta or cash receipt. */
public record FinalSettlementRecognition(UUID recognitionOperationId,int recognitionRevision,UUID supersedesOperationId,
        UUID settlementId,int settlementVersion,UUID supersedesSettlementId,UUID accountId,UUID admissionId,UUID patientId,
        UUID accountDepartmentId,String currency,Totals totals,Instant completedAt,LocalDate businessDate,String reportZone,
        boolean finalSettlement,String outcome,List<Charge> charges,List<Allocation> allocations,List<DepositRelease> depositReleases) {
    public FinalSettlementRecognition {
        if(recognitionOperationId==null || settlementId==null || recognitionRevision!=1 || settlementVersion!=1
                || supersedesOperationId!=null || supersedesSettlementId!=null || accountId==null || admissionId==null || patientId==null
                || accountDepartmentId==null || !"VND".equals(currency) || totals==null || completedAt==null || businessDate==null || reportZone==null
                || !finalSettlement || !"PAID_IN_FULL".equals(outcome) || charges==null || charges.isEmpty() || charges.size()>500
                || allocations==null || allocations.size()>1000 || depositReleases==null || depositReleases.size()>1000)throw invalid();
        if(!completedAt.atZone(ZoneId.of(reportZone)).toLocalDate().equals(businessDate)
                || businessDate.isBefore(LocalDate.of(2000,1,1)) || businessDate.isAfter(LocalDate.of(2100,12,31)))throw invalid();
        charges=List.copyOf(charges);allocations=List.copyOf(allocations);depositReleases=List.copyOf(depositReleases);
        verifyDetails(totals,completedAt,accountDepartmentId,charges,allocations,depositReleases);
    }
    public record Totals(BigDecimal grossAmount,BigDecimal insuranceAmount,BigDecimal patientLiability,BigDecimal completedPayments,
            BigDecimal completedRefunds,BigDecimal completedReversals,BigDecimal netAllocatedPayments,BigDecimal balance) {
        public Totals {
            grossAmount=money(grossAmount,false);insuranceAmount=money(insuranceAmount,false);patientLiability=money(patientLiability,false);
            completedPayments=money(completedPayments,false);completedRefunds=money(completedRefunds,false);completedReversals=money(completedReversals,false);
            netAllocatedPayments=money(netAllocatedPayments,false);balance=money(balance,false);
            if(insuranceAmount.signum()!=0 || completedReversals.signum()!=0 || balance.signum()!=0
                    || patientLiability.compareTo(grossAmount)!=0 || netAllocatedPayments.compareTo(grossAmount)!=0
                    || completedPayments.subtract(completedRefunds).compareTo(netAllocatedPayments)!=0)throw invalid();
        }
    }
    public record Charge(UUID chargeId,UUID departmentId,String sourceType,UUID sourceId,UUID performedOperationId,
            String performedFingerprint,Instant performedAt,BigDecimal earnedAmount) {
        public Charge {
            if(chargeId==null || departmentId==null || sourceId==null || performedOperationId==null || performedAt==null
                    || !("SURGERY".equals(sourceType)||"PRESCRIPTION".equals(sourceType)) || performedFingerprint==null
                    || !performedFingerprint.matches("[0-9a-f]{64}"))throw invalid();earnedAmount=money(earnedAmount,false);
        }
    }
    public record Allocation(UUID allocationId,UUID transactionId,UUID originalTransactionId,UUID chargeId,
            String transactionType,String classification,BigDecimal amount) {
        public Allocation {
            if(allocationId==null || transactionId==null || chargeId==null || !("PAYMENT".equals(transactionType)||"REFUND".equals(transactionType))
                    || ("PAYMENT".equals(transactionType)!=(originalTransactionId==null)) || transactionId.equals(originalTransactionId)
                    || !("SERVICE_PAYMENT".equals(classification)||"ADMISSION_DEPOSIT".equals(classification)||"SETTLEMENT_PAYMENT".equals(classification)))throw invalid();
            amount=money(amount,true);
        }
        public BigDecimal signedAmount(){return "PAYMENT".equals(transactionType)?amount:amount.negate();}
    }
    public record DepositRelease(UUID originalTransactionId,UUID receiptDepartmentId,BigDecimal amount) {
        public DepositRelease {if(originalTransactionId==null || receiptDepartmentId==null)throw invalid();amount=money(amount,true);}
    }
    public Map<UUID,BigDecimal> earnedByDepartment() {
        var amounts=new HashMap<UUID,BigDecimal>();charges.forEach(line->amounts.merge(line.departmentId(),line.earnedAmount(),BigDecimal::add));return Map.copyOf(amounts);
    }
    /** Only local accepted cash facts can fund recognition. Missing deliveries remain pending. */
    public boolean verifyFunding(List<CashReceipt> receipts,List<CashRefund> refunds) {
        var originals=new HashMap<UUID,CashReceipt>();BigDecimal paid=BigDecimal.ZERO,returned=BigDecimal.ZERO;
        for(var receipt:receipts) {
            if(!accountId.equals(receipt.accountId()) || !patientId.equals(receipt.patientId()) || !admissionId.equals(receipt.careEpisodeId())
                    || !accountDepartmentId.equals(receipt.departmentId()) || !"ADMISSION".equals(receipt.careEpisodeType())
                    || !currency.equals(receipt.currency()) || !reportZone.equals(receipt.reportZone())
                    || receipt.completedAt().isAfter(completedAt) || originals.putIfAbsent(receipt.transactionId(),receipt)!=null)throw invalid();
            paid=paid.add(receipt.amount());
        }
        var refundById=new HashMap<UUID,CashRefund>();var returnsByOriginal=new HashMap<UUID,BigDecimal>();
        for(var refund:refunds) {
            var original=originals.get(refund.originalTransactionId());if(original==null)return false;
            if(refund.completedAt().isAfter(completedAt) || !reportZone.equals(refund.reportZone()) || refundById.putIfAbsent(refund.refundTransactionId(),refund)!=null)throw invalid();
            refund.verifyOriginal(original,returnsByOriginal.getOrDefault(original.transactionId(),BigDecimal.ZERO));
            returnsByOriginal.merge(original.transactionId(),refund.amount(),BigDecimal::add);returned=returned.add(refund.amount());
        }
        if(paid.compareTo(totals.completedPayments())>0 || returned.compareTo(totals.completedRefunds())>0)throw invalid();
        if(paid.compareTo(totals.completedPayments())!=0 || returned.compareTo(totals.completedRefunds())!=0)return false;
        var allocatedByOriginal=new HashMap<UUID,BigDecimal>();var allocatedByRefund=new HashMap<UUID,BigDecimal>();
        for(var allocation:allocations) {
            UUID originalId="PAYMENT".equals(allocation.transactionType())?allocation.transactionId():allocation.originalTransactionId();
            var original=originals.get(originalId);if(original==null)return false;
            if(!allocation.classification().equals(original.classification().name()))throw invalid();
            if("PAYMENT".equals(allocation.transactionType()))allocatedByOriginal.merge(originalId,allocation.amount(),BigDecimal::add);
            else {
                var refund=refundById.get(allocation.transactionId());if(refund==null)return false;
                if(!originalId.equals(refund.originalTransactionId()))throw invalid();
                if(allocatedByRefund.merge(refund.refundTransactionId(),allocation.amount(),BigDecimal::add).compareTo(refund.amount())>0)throw invalid();
            }
        }
        for(var entry:allocatedByOriginal.entrySet())if(entry.getValue().compareTo(originals.get(entry.getKey()).amount())>0)throw invalid();
        for(var release:depositReleases) {
            var original=originals.get(release.originalTransactionId());if(original==null)return false;
            if(original.classification()!=CashReceipt.Classification.ADMISSION_DEPOSIT || !release.receiptDepartmentId().equals(original.departmentId())
                    || release.amount().add(returnsByOriginal.getOrDefault(original.transactionId(),BigDecimal.ZERO)).compareTo(original.amount())>0)throw invalid();
        }
        return true;
    }
    private static void verifyDetails(Totals totals,Instant completed,UUID accountDepartment,List<Charge> charges,List<Allocation> allocations,List<DepositRelease> releases) {
        var byCharge=new HashMap<UUID,Charge>();var sourceProof=new HashMap<String,Charge>();BigDecimal earned=BigDecimal.ZERO;
        for(var line:charges) {
            if(line==null || line.performedAt().isAfter(completed) || byCharge.putIfAbsent(line.chargeId(),line)!=null)throw invalid();
            String source=line.sourceType()+":"+line.sourceId();var previous=sourceProof.putIfAbsent(source,line);
            if(previous!=null && (!previous.departmentId().equals(line.departmentId()) || !previous.performedOperationId().equals(line.performedOperationId())
                    || !previous.performedFingerprint().equals(line.performedFingerprint()) || !previous.performedAt().equals(line.performedAt())))throw invalid();
            earned=earned.add(line.earnedAmount());
        }
        if(earned.compareTo(totals.grossAmount())!=0)throw invalid();
        var ids=new HashSet<UUID>();var transactionClass=new HashMap<UUID,String>();var netByCharge=new HashMap<UUID,BigDecimal>();
        var depositByOriginal=new HashMap<UUID,BigDecimal>();
        var pairs=new HashSet<String>();var originalByCharge=new HashMap<String,BigDecimal>();var returnedByCharge=new HashMap<String,BigDecimal>();
        for(var line:allocations) {
            if(line==null || !ids.add(line.allocationId()) || !byCharge.containsKey(line.chargeId()) || !pairs.add(line.transactionId()+":"+line.chargeId()))throw invalid();
            if("PAYMENT".equals(line.transactionType())) {
                var previous=transactionClass.putIfAbsent(line.transactionId(),line.classification());
                if(previous!=null && !previous.equals(line.classification()))throw invalid();
                originalByCharge.put(line.transactionId()+":"+line.chargeId(),line.amount());
            }
            netByCharge.merge(line.chargeId(),line.signedAmount(),BigDecimal::add);
            if("ADMISSION_DEPOSIT".equals(line.classification()))depositByOriginal.merge(
                    "PAYMENT".equals(line.transactionType())?line.transactionId():line.originalTransactionId(),line.signedAmount(),BigDecimal::add);
        }
        for(var line:allocations)if("REFUND".equals(line.transactionType())) {
            if(!line.classification().equals(transactionClass.get(line.originalTransactionId())))throw invalid();
            String key=line.originalTransactionId()+":"+line.chargeId();
            if(returnedByCharge.merge(key,line.amount(),BigDecimal::add).compareTo(originalByCharge.getOrDefault(key,BigDecimal.ZERO))>0)throw invalid();
        }
        for(var line:charges)if(netByCharge.getOrDefault(line.chargeId(),BigDecimal.ZERO).compareTo(line.earnedAmount())!=0)throw invalid();
        if(depositByOriginal.values().stream().anyMatch(amount->amount.signum()<0))throw invalid();
        depositByOriginal.entrySet().removeIf(entry->entry.getValue().signum()==0);
        var originals=new HashSet<UUID>();
        for(var release:releases) {
            if(release==null || !originals.add(release.originalTransactionId()) || !accountDepartment.equals(release.receiptDepartmentId()))throw invalid();
            var expected=depositByOriginal.remove(release.originalTransactionId());
            if(expected==null || expected.compareTo(release.amount())!=0)throw invalid();
        }
        if(!depositByOriginal.isEmpty())throw invalid();
    }
    private static BigDecimal money(BigDecimal value,boolean positive) {
        if(value==null || value.signum()<0 || (positive&&value.signum()==0) || value.precision()>100 || Math.abs((long)value.scale())>100)throw invalid();
        try{var exact=value.setScale(2,RoundingMode.UNNECESSARY);if(exact.precision()>19)throw invalid();return exact;}
        catch(ArithmeticException failure){throw invalid();}
    }
    private static ReportRuleException invalid(){return new ReportRuleException("REPORT_FINAL_RECOGNITION_INVALID","Invalid final-only recognition evidence");}
}
