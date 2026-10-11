package com.mediflow.report.application.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.SettlementReceivableSnapshot;

public final class BillingSettlementSnapshotMapper {
    private static final Set<String> FIELDS = Set.of("settlementId", "settlementVersion", "supersedesSettlementId",
            "accountId", "admissionId", "patientId", "departmentId", "currency", "grossAmount", "insuranceAmount",
            "patientLiability", "completedPayments", "completedRefunds", "completedReversals", "netAllocatedPayments",
            "balance", "estimatedOutcome", "reconciledAt", "finalSettlement");
    private final ZoneId zone;
    public BillingSettlementSnapshotMapper(ZoneId zone) { this.zone = java.util.Objects.requireNonNull(zone); }
    public SettlementReceivableSnapshot map(DecodedCareFinanceEvent event) {
        var metadata = event.metadata(); var p = event.payload();
        if (!"settlement.reconciled".equals(metadata.eventType()) || metadata.version() != 1
                || !"billing-service".equals(metadata.producer()) || !"settlementId".equals(metadata.sourceField())
                || !FIELDS.equals(p.keySet()) || !Boolean.FALSE.equals(p.get("finalSettlement"))) throw invalid();
        var id = uuid(p, "settlementId"); var time = Instant.parse(text(p, "reconciledAt"));
        if (!id.equals(metadata.sourceId()) || !time.equals(metadata.occurredAt())) throw invalid();
        return new SettlementReceivableSnapshot(id, revision(p),
                p.get("supersedesSettlementId") == null ? null : uuid(p, "supersedesSettlementId"),
                uuid(p, "accountId"), uuid(p, "admissionId"), uuid(p, "patientId"), uuid(p, "departmentId"), text(p, "currency"),
                decimal(p, "grossAmount"), decimal(p, "insuranceAmount"), decimal(p, "patientLiability"),
                decimal(p, "completedPayments"), decimal(p, "completedRefunds"), decimal(p, "completedReversals"),
                decimal(p, "netAllocatedPayments"), decimal(p, "balance"), SettlementReceivableSnapshot.Outcome.valueOf(text(p, "estimatedOutcome")), time, false, zone.getId());
    }
    private static int revision(Map<String, Object> p) {
        var value = p.get("settlementVersion");
        try {
            if (value instanceof Integer number) return number;
            if (value instanceof Long number) return Math.toIntExact(number);
            if (value instanceof BigInteger number) return number.intValueExact();
        } catch (ArithmeticException error) { throw invalid(); }
        throw invalid();
    }
    private static BigDecimal decimal(Map<String, Object> p, String key) {
        var value = p.get(key);
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof BigInteger integer) return new BigDecimal(integer);
        if (value instanceof Integer || value instanceof Long) return new BigDecimal(value.toString());
        throw invalid();
    }
    private static String text(Map<String, Object> p, String key) {
        if (p.get(key) instanceof String value && !value.isBlank()) return value;
        throw invalid();
    }
    private static UUID uuid(Map<String, Object> p, String key) {
        String value = text(p, key); UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw invalid();
        return id;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid Billing reconciliation contract"); }
}
