package com.mediflow.report.application.mapper;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.FinalSettlementRecognition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BillingFinalSettlementRecognitionMapper {
    private static final Set<String> FIELDS=Set.of("recognitionOperationId","recognitionRevision","supersedesOperationId","settlementId","settlementVersion",
            "supersedesSettlementId","accountId","admissionId","patientId","accountDepartmentId","currency","grossAmount","insuranceAmount","patientLiability",
            "completedPayments","completedRefunds","completedReversals","netAllocatedPayments","balance","outcome","completedAt","finalSettlement",
            "charges","allocations","depositReleases");
    private static final Set<String> CHARGE=Set.of("chargeId","departmentId","sourceType","sourceId","performedOperationId","performedFingerprint","performedAt","earnedAmount");
    private static final Set<String> ALLOCATION=Set.of("allocationId","transactionId","originalTransactionId","chargeId","transactionType","classification","amount");
    private static final Set<String> RELEASE=Set.of("originalTransactionId","receiptDepartmentId","amount");
    private final ZoneId zone;
    public BillingFinalSettlementRecognitionMapper(ZoneId zone){this.zone=java.util.Objects.requireNonNull(zone);}
    public FinalSettlementRecognition map(DecodedCareFinanceEvent event) {
        var metadata=event.metadata();var p=event.payload();exact(p,FIELDS);
        if(!"settlement.recognition.completed".equals(metadata.eventType()) || metadata.version()!=1 || !"billing-service".equals(metadata.producer())
                || !"recognitionOperationId".equals(metadata.sourceField()) || !metadata.sourceId().equals(uuid(p,"recognitionOperationId"))
                || !Boolean.TRUE.equals(p.get("finalSettlement")))throw invalid();
        var completed=Instant.parse(text(p,"completedAt"));if(!completed.equals(metadata.occurredAt()))throw invalid();
        var charges=list(p,"charges",500).stream().map(line->{exact(line,CHARGE);return new FinalSettlementRecognition.Charge(uuid(line,"chargeId"),uuid(line,"departmentId"),
                text(line,"sourceType"),uuid(line,"sourceId"),uuid(line,"performedOperationId"),text(line,"performedFingerprint"),Instant.parse(text(line,"performedAt")),money(line,"earnedAmount"));}).toList();
        var allocations=list(p,"allocations",1000).stream().map(line->{exact(line,ALLOCATION);return new FinalSettlementRecognition.Allocation(uuid(line,"allocationId"),uuid(line,"transactionId"),
                nullableUuid(line,"originalTransactionId"),uuid(line,"chargeId"),text(line,"transactionType"),text(line,"classification"),money(line,"amount"));}).toList();
        var releases=list(p,"depositReleases",1000).stream().map(line->{exact(line,RELEASE);return new FinalSettlementRecognition.DepositRelease(uuid(line,"originalTransactionId"),
                uuid(line,"receiptDepartmentId"),money(line,"amount"));}).toList();
        return new FinalSettlementRecognition(uuid(p,"recognitionOperationId"),integer(p,"recognitionRevision"),nullableUuid(p,"supersedesOperationId"),
                uuid(p,"settlementId"),integer(p,"settlementVersion"),nullableUuid(p,"supersedesSettlementId"),uuid(p,"accountId"),uuid(p,"admissionId"),uuid(p,"patientId"),
                uuid(p,"accountDepartmentId"),text(p,"currency"),new FinalSettlementRecognition.Totals(money(p,"grossAmount"),money(p,"insuranceAmount"),money(p,"patientLiability"),
                money(p,"completedPayments"),money(p,"completedRefunds"),money(p,"completedReversals"),money(p,"netAllocatedPayments"),money(p,"balance")),completed,
                completed.atZone(zone).toLocalDate(),zone.getId(),true,text(p,"outcome"),charges,allocations,releases);
    }
    private static List<Map<String,Object>> list(Map<String,Object> p,String key,int maximum) {
        if(!(p.get(key) instanceof List<?> values) || values.size()>maximum)throw invalid();
        return values.stream().map(value->{if(!(value instanceof Map<?,?> map) || map.keySet().stream().anyMatch(field->!(field instanceof String)))throw invalid();
            @SuppressWarnings("unchecked") var typed=(Map<String,Object>)map;return typed;}).toList();
    }
    private static void exact(Map<String,Object> p,Set<String> fields){if(!fields.equals(p.keySet()))throw invalid();}
    private static String text(Map<String,Object> p,String key){if(!(p.get(key) instanceof String value)||value.isBlank())throw invalid();return value;}
    private static UUID uuid(Map<String,Object> p,String key){String value=text(p,key);UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw invalid();return id;}
    private static UUID nullableUuid(Map<String,Object> p,String key){return p.get(key)==null?null:uuid(p,key);}
    private static BigDecimal money(Map<String,Object> p,String key) {
        var value=p.get(key);if(value instanceof BigDecimal decimal)return decimal;if(value instanceof BigInteger number)return new BigDecimal(number);
        if(value instanceof Integer || value instanceof Long)return new BigDecimal(value.toString());throw invalid();
    }
    private static int integer(Map<String,Object> p,String key) {
        var value=p.get(key);try{if(value instanceof Integer number)return number;if(value instanceof Long number)return Math.toIntExact(number);
            if(value instanceof BigInteger number)return number.intValueExact();}catch(ArithmeticException failure){throw invalid();}throw invalid();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid Billing final recognition V1 contract");}
}
