package com.mediflow.report.application.mapper;

import java.math.*;
import java.time.*;
import java.util.*;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.CashRefund;

public class BillingCashRefundMapper {
    private final ZoneId zone;
    public BillingCashRefundMapper(ZoneId zone) { this.zone = Objects.requireNonNull(zone); }
    public CashRefund map(DecodedCareFinanceEvent event) {
        var m = event.metadata(); var p = event.payload();
        if (m.version() != 1 || !"payment.refunded".equals(m.eventType()) || !"billing-service".equals(m.producer())
                || !"refundTransactionId".equals(m.sourceField()) || !m.sourceId().equals(uuid(p, "refundTransactionId"))
                || p.containsKey("sourceRevision") || p.containsKey("supersedesTransactionId")
                || !"CASHIER_RECORDED_REFUND".equals(text(p, "reason"))) throw invalid();
        var raw = p.get("amount");
        if (!(raw instanceof BigDecimal || raw instanceof BigInteger || raw instanceof Integer || raw instanceof Long)) throw invalid();
        var at = Instant.parse(text(p, "completedAt"));
        if (!at.equals(m.occurredAt())) throw invalid();
        return new CashRefund(uuid(p, "refundTransactionId"), uuid(p, "originalTransactionId"), uuid(p, "accountId"),
                uuid(p, "patientId"), uuid(p, "departmentId"), text(p, "careEpisodeType"), uuid(p, "careEpisodeId"),
                new BigDecimal(raw.toString()), text(p, "currency"), at, at.atZone(zone).toLocalDate(), zone.getId());
    }
    private static String text(Map<String, Object> p, String key) {
        if (!(p.get(key) instanceof String s) || s.isBlank()) throw invalid(); return s;
    }
    private static UUID uuid(Map<String, Object> p, String key) {
        var s = text(p, key); var value = UUID.fromString(s); if (!value.toString().equals(s)) throw invalid(); return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported Billing completed cash refund"); }
}
