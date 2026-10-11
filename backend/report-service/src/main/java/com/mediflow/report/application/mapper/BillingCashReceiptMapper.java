package com.mediflow.report.application.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.CashReceipt;

/** Acceptance of the two actual Billing ledger V1 receipt fixtures, never legacy invoice revenue. */
public class BillingCashReceiptMapper {
    private final ZoneId reportZone;

    public BillingCashReceiptMapper(ZoneId reportZone) {
        this.reportZone = Objects.requireNonNull(reportZone);
    }

    public CashReceipt map(DecodedCareFinanceEvent event) {
        var metadata = event.metadata();
        var payload = event.payload();
        if (!java.util.Set.of("transactionId", "invoiceId", "paymentRequestId", "accountId", "patientId", "departmentId",
                "careEpisodeType", "careEpisodeId", "classification", "totalAmount", "currency", "paymentMethod", "completedAt",
                "prescriptionId", "labTestIds").containsAll(payload.keySet())) throw invalid("unknown receipt fields");
        if (metadata.version() != 1 || !"payment.completed".equals(metadata.eventType())
                || !"billing-service".equals(metadata.producer())
                || !"transactionId".equals(metadata.sourceField())
                || !metadata.sourceId().equals(uuid(payload, "transactionId"))) {
            throw invalid("envelope/source");
        }
        // The immutable PAYMENT transaction is the operation; invoice/request may have many receipts.
        // No correction/replacement shape or source revision is synthesized from envelope version.
        if (payload.containsKey("sourceRevision") || payload.containsKey("supersedesTransactionId")) {
            throw invalid("unsupported correction contract");
        }
        validateOptionalTargets(payload);
        Object rawAmount = payload.get("totalAmount");
        if (!(rawAmount instanceof BigDecimal || rawAmount instanceof BigInteger
                || rawAmount instanceof Integer || rawAmount instanceof Long)) {
            throw invalid("totalAmount must be an exact JSON number");
        }
        var at = java.time.Instant.parse(text(payload, "completedAt"));
        if (metadata.occurredAt().isBefore(at)) {
            throw invalid("receipt cannot precede completion");
        }
        return new CashReceipt(uuid(payload, "transactionId"), optionalUuid(payload, "invoiceId"),
                uuid(payload, "paymentRequestId"), uuid(payload, "accountId"), uuid(payload, "patientId"),
                uuid(payload, "departmentId"), text(payload, "careEpisodeType"), uuid(payload, "careEpisodeId"),
                CashReceipt.Classification.valueOf(text(payload, "classification")),
                new BigDecimal(rawAmount.toString()), text(payload, "currency"), text(payload, "paymentMethod"),
                at, at.atZone(reportZone).toLocalDate(), reportZone.getId());
    }

    /** Validate producer evidence without turning these optional targets into care permission. */
    private static void validateOptionalTargets(Map<String, Object> payload) {
        UUID prescription = optionalUuid(payload, "prescriptionId");
        Object rawTests = payload.get("labTestIds");
        if (rawTests != null && !(rawTests instanceof java.util.List<?>)) throw invalid("labTestIds");
        var tests = rawTests == null ? java.util.List.of() : (java.util.List<?>) rawTests;
        if (tests.size() > 1000) throw invalid("labTestIds bound");
        var ids = new java.util.HashSet<UUID>();
        for (Object raw : tests) {
            if (!(raw instanceof String value)) throw invalid("labTestIds identity");
            UUID id = UUID.fromString(value);
            if (!value.equals(id.toString()) || !ids.add(id)) throw invalid("labTestIds identity");
        }
        if (prescription != null && !tests.isEmpty()) throw invalid("mixed operational targets");
        if ("ADMISSION_DEPOSIT".equals(payload.get("classification")) && (prescription != null || !tests.isEmpty()))
            throw invalid("deposit cannot carry service targets");
    }

    private static String text(Map<String, Object> payload, String key) {
        if (!(payload.get(key) instanceof String value) || value.isBlank()) {
            throw invalid(key);
        }
        return value;
    }

    private static UUID uuid(Map<String, Object> payload, String key) {
        String value = text(payload, key);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) {
            throw invalid(key);
        }
        return id;
    }

    private static UUID optionalUuid(Map<String, Object> payload, String key) {
        return payload.get(key) == null ? null : uuid(payload, key);
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("Unsupported Billing cash receipt: " + field);
    }
}
