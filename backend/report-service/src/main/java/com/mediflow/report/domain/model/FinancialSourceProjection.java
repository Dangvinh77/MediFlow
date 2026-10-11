package com.mediflow.report.domain.model;

import com.mediflow.report.domain.exception.ReportRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/** Pure finite rebuild from accepted sources. Does not assert upstream coverage or publish a report. */
public final class FinancialSourceProjection {
    private static final int MAX_INPUTS=10_000,MAX_DEPARTMENTS=100;
    private FinancialSourceProjection(){ }
    public record Day(LocalDate businessDate,UUID departmentId,FinancialAmounts amounts){ }
    private record Scope(LocalDate date,UUID department){ }
    private record AccountContext(UUID patientId,String episodeType,UUID episodeId,UUID departmentId){ }
    private record Flows(BigDecimal cash,BigDecimal refund,BigDecimal earned){
        private static final Flows ZERO=new Flows(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        Flows plus(Flows other){return new Flows(cash.add(other.cash),refund.add(other.refund),earned.add(other.earned));}
    }
    public static List<Day> rebuild(LocalDate from,LocalDate to,String reportZone,List<CashReceipt> receiptInputs,
            List<CashRefund> refundInputs,List<FinalSettlementRecognition> finalInputs,List<SettlementReceivableSnapshot> balanceInputs) {
        if(from==null || to==null || from.getYear()<2000 || to.getYear()>2100 || ChronoUnit.DAYS.between(from,to)<0
                || ChronoUnit.DAYS.between(from,to)>=366 || reportZone==null || receiptInputs==null || refundInputs==null || finalInputs==null || balanceInputs==null
                || (long)receiptInputs.size()+refundInputs.size()+finalInputs.size()+balanceInputs.size()>MAX_INPUTS)throw invalid();
        final ZoneId zone;
        try { zone=ZoneId.of(reportZone); } catch (java.time.DateTimeException error) { throw invalid(); }
        var receipts=unique(receiptInputs,CashReceipt::transactionId);var refunds=unique(refundInputs,CashRefund::refundTransactionId);
        var finals=unique(finalInputs,FinalSettlementRecognition::recognitionOperationId);var balances=unique(balanceInputs,SettlementReceivableSnapshot::settlementId);
        var departments=new HashSet<UUID>();var flows=new HashMap<Scope,Flows>();var liability=new HashMap<Scope,BigDecimal>();var receivable=new HashMap<Scope,BigDecimal>();
        var contexts=new HashMap<UUID,AccountContext>();
        var returnsByOriginal=new HashMap<UUID,BigDecimal>();var receiptsByAccount=new HashMap<UUID,List<CashReceipt>>();var refundsByAccount=new HashMap<UUID,List<CashRefund>>();
        for(var receipt:receipts.values()) {
            requireZone(reportZone,receipt.reportZone(),receipt.currency());departments.add(receipt.departmentId());
            requireContext(contexts,receipt.accountId(),new AccountContext(receipt.patientId(),receipt.careEpisodeType(),receipt.careEpisodeId(),receipt.departmentId()));
            receiptsByAccount.computeIfAbsent(receipt.accountId(),key->new ArrayList<>()).add(receipt);
            var scope=new Scope(receipt.businessDate(),receipt.departmentId());flows.merge(scope,new Flows(receipt.amount(),BigDecimal.ZERO,BigDecimal.ZERO),Flows::plus);
            if(receipt.classification()==CashReceipt.Classification.ADMISSION_DEPOSIT)liability.merge(scope,receipt.amount(),BigDecimal::add);
        }
        for(var refund:refunds.values()) {
            requireZone(reportZone,refund.reportZone(),refund.currency());var original=receipts.get(refund.originalTransactionId());
            if(original==null)throw unresolved();refund.verifyOriginal(original,returnsByOriginal.getOrDefault(original.transactionId(),BigDecimal.ZERO));
            returnsByOriginal.merge(original.transactionId(),refund.amount(),BigDecimal::add);
            refundsByAccount.computeIfAbsent(refund.accountId(),key->new ArrayList<>()).add(refund);
            var scope=new Scope(refund.businessDate(),refund.departmentId());flows.merge(scope,new Flows(BigDecimal.ZERO,refund.amount(),BigDecimal.ZERO),Flows::plus);
            if(original.classification()==CashReceipt.Classification.ADMISSION_DEPOSIT)liability.merge(scope,refund.amount().negate(),BigDecimal::add);
        }
        var finalByAccount=new HashMap<UUID,FinalSettlementRecognition>();var chargeIds=new HashSet<UUID>();
        for(var fact:finals.values()) {
            requireZone(reportZone,fact.reportZone(),fact.currency());
            requireContext(contexts,fact.accountId(),new AccountContext(fact.patientId(),"ADMISSION",fact.admissionId(),fact.accountDepartmentId()));
            if(finalByAccount.putIfAbsent(fact.accountId(),fact)!=null)throw invalid();
            if(!fact.verifyFunding(receiptsByAccount.getOrDefault(fact.accountId(),List.of()),refundsByAccount.getOrDefault(fact.accountId(),List.of())))throw unresolved();
            for(var charge:fact.charges()) {
                if(!chargeIds.add(charge.chargeId()))throw invalid();departments.add(charge.departmentId());
                flows.merge(new Scope(fact.businessDate(),charge.departmentId()),new Flows(BigDecimal.ZERO,BigDecimal.ZERO,charge.earnedAmount()),Flows::plus);
            }
            for(var release:fact.depositReleases())liability.merge(new Scope(fact.businessDate(),release.receiptDepartmentId()),release.amount().negate(),BigDecimal::add);
        }
        var revisionsByAccount=new HashMap<UUID,TreeMap<Integer,SettlementReceivableSnapshot>>();
        for(var balance:balances.values()) {
            requireZone(reportZone,balance.reportZone(),balance.currency());departments.add(balance.departmentId());
            requireContext(contexts,balance.accountId(),new AccountContext(balance.patientId(),"ADMISSION",balance.admissionId(),balance.departmentId()));
            var revisions=revisionsByAccount.computeIfAbsent(balance.accountId(),key->new TreeMap<>());
            if(revisions.putIfAbsent(balance.settlementVersion(),balance)!=null)throw invalid();
        }
        for(var revisions:revisionsByAccount.values()) {
            SettlementReceivableSnapshot previous=null;
            for(var balance:revisions.values()) {
                if(previous==null && balance.settlementVersion()!=1)throw unresolved();
                if(previous!=null){if(balance.settlementVersion()!=previous.settlementVersion()+1)throw unresolved();previous.requireSuccessor(balance);}
                var finalFact=finalByAccount.get(balance.accountId());
                if(finalFact!=null && (!finalFact.admissionId().equals(balance.admissionId()) || !finalFact.patientId().equals(balance.patientId())
                        || !finalFact.accountDepartmentId().equals(balance.departmentId()) || balance.reconciledAt().isAfter(finalFact.completedAt())))throw invalid();
                var scope=new Scope(balance.reconciledAt().atZone(zone).toLocalDate(),balance.departmentId());
                receivable.merge(scope,balance.receivable().subtract(previous==null?BigDecimal.ZERO:previous.receivable()),BigDecimal::add);previous=balance;
            }
            var finalFact=finalByAccount.get(previous.accountId());
            if(finalFact!=null)receivable.merge(new Scope(finalFact.businessDate(),previous.departmentId()),previous.receivable().negate(),BigDecimal::add);
        }
        if(departments.size()>MAX_DEPARTMENTS)throw invalid();
        var orderedDepartments=departments.stream().sorted(Comparator.comparing(UUID::toString)).toList();var rows=new ArrayList<Day>();
        var liabilityStocks=opening(liability,from);var receivableStocks=opening(receivable,from);
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)) {
            FinancialAmounts hospital=new FinancialAmounts(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
            var scopeRows=new ArrayList<Day>();
            for(var department:orderedDepartments) {
                var scope=new Scope(day,department);var flow=flows.getOrDefault(scope,Flows.ZERO);
                var held=liabilityStocks.merge(department,liability.getOrDefault(scope,BigDecimal.ZERO),BigDecimal::add);
                var owed=receivableStocks.merge(department,receivable.getOrDefault(scope,BigDecimal.ZERO),BigDecimal::add);
                if(held.signum()<0 || owed.signum()<0)throw invalid();
                var amounts=new FinancialAmounts(flow.cash(),held,flow.earned(),flow.refund(),owed);scopeRows.add(new Day(day,department,amounts));
                hospital=new FinancialAmounts(hospital.cashReceived().add(amounts.cashReceived()),hospital.depositLiability().add(amounts.depositLiability()),
                        hospital.earnedRevenue().add(amounts.earnedRevenue()),hospital.refunds().add(amounts.refunds()),hospital.outstandingReceivable().add(amounts.outstandingReceivable()));
            }
            rows.add(new Day(day,null,hospital));rows.addAll(scopeRows);
        }
        return List.copyOf(rows);
    }
    private static Map<UUID,BigDecimal> opening(Map<Scope,BigDecimal> changes,LocalDate from) {
        var opening=new HashMap<UUID,BigDecimal>();changes.forEach((scope,amount)->{if(scope.date().isBefore(from))opening.merge(scope.department(),amount,BigDecimal::add);});return opening;
    }
    private static <T> Map<UUID,T> unique(List<T> inputs,Function<T,UUID> key) {
        var result=new HashMap<UUID,T>();for(var input:inputs){if(input==null)throw invalid();var old=result.putIfAbsent(key.apply(input),input);if(old!=null && !old.equals(input))throw invalid();}return result;
    }
    private static void requireZone(String zone,String actualZone,String currency){if(!zone.equals(actualZone)||!"VND".equals(currency))throw invalid();}
    private static void requireContext(Map<UUID,AccountContext> contexts,UUID account,AccountContext context) {
        var previous=contexts.putIfAbsent(account,context);
        if(previous!=null && !previous.equals(context))throw invalid();
    }
    private static ReportRuleException invalid(){return new ReportRuleException("REPORT_FINANCIAL_SOURCE_CONFLICT","Conflicting financial rebuild sources");}
    private static ReportRuleException unresolved(){return new ReportRuleException("REPORT_FINANCIAL_SOURCE_UNRESOLVED","Complete original financial sources are required");}
}
