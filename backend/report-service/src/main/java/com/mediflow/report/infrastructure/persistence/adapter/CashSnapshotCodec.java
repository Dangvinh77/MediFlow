package com.mediflow.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.domain.model.CashReceipt;

/** Versioned accepted receipt inputs, NOT reconstructed raw Billing events. V12 hashes stay stable. */
public final class CashSnapshotCodec {
    public static final int SNAPSHOT_VERSION = 1;
    public static final int PROJECTOR_VERSION = 1;
    private static final Set<String> FIELDS = Set.of("transactionId", "invoiceId", "paymentRequestId",
            "accountId", "patientId", "departmentId", "careEpisodeType", "careEpisodeId", "classification",
            "amount", "currency", "paymentMethod", "completedAt", "businessDate", "reportZone");
    private final ObjectMapper mapper;

    public CashSnapshotCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public String snapshot(CashReceipt receipt) { return json(factFields(receipt)); }
    public String fingerprint(CashReceipt receipt) { return evidenceFingerprint(factFields(receipt)); }

    public Map<String, Object> factFields(CashReceipt receipt) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("transactionId", receipt.transactionId().toString());
        fields.put("invoiceId", receipt.invoiceId() == null ? null : receipt.invoiceId().toString());
        fields.put("paymentRequestId", receipt.paymentRequestId().toString());
        fields.put("accountId", receipt.accountId().toString());
        fields.put("patientId", receipt.patientId().toString());
        fields.put("departmentId", receipt.departmentId().toString());
        fields.put("careEpisodeType", receipt.careEpisodeType());
        fields.put("careEpisodeId", receipt.careEpisodeId().toString());
        fields.put("classification", receipt.classification().name());
        fields.put("amount", receipt.amount());
        fields.put("currency", receipt.currency());
        fields.put("paymentMethod", receipt.paymentMethod());
        fields.put("completedAt", receipt.completedAt().toString());
        fields.put("businessDate", receipt.businessDate().toString());
        fields.put("reportZone", receipt.reportZone());
        return fields;
    }

    public CashReceipt decode(UUID transactionId, String snapshot, String fingerprint,
            int snapshotVersion, int projectorVersion) {
        if (snapshotVersion != SNAPSHOT_VERSION || projectorVersion != PROJECTOR_VERSION) {
            throw new IllegalArgumentException("Unsupported cash snapshot/projector version");
        }
        try {
            JsonNode node = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(snapshot);
            var fields = new java.util.HashSet<String>();
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Cash snapshot object required");
            node.fieldNames().forEachRemaining(fields::add);
            if (!FIELDS.equals(fields)) throw new IllegalArgumentException("Invalid cash snapshot fields");
            if (!node.get("amount").isNumber()) throw new IllegalArgumentException("Cash amount must be numeric");
            var receipt = new CashReceipt(uuid(node, "transactionId"),
                    node.get("invoiceId").isNull() ? null : uuid(node, "invoiceId"), uuid(node, "paymentRequestId"),
                    uuid(node, "accountId"), uuid(node, "patientId"), uuid(node, "departmentId"),
                    text(node, "careEpisodeType"), uuid(node, "careEpisodeId"),
                    CashReceipt.Classification.valueOf(text(node, "classification")), node.get("amount").decimalValue(),
                    text(node, "currency"), text(node, "paymentMethod"), Instant.parse(text(node, "completedAt")),
                    LocalDate.parse(text(node, "businessDate")), text(node, "reportZone"));
            if (!receipt.transactionId().equals(transactionId)) throw new IllegalArgumentException("Cash snapshot identity mismatch");
            if (!fingerprint(receipt).equals(fingerprint)) throw new IllegalArgumentException("Cash snapshot fingerprint mismatch");
            return receipt;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Malformed cash snapshot", exception);
        }
    }

    public String json(Object value) {
        try { return mapper.writeValueAsString(normalize(value)); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Cannot encode cash snapshot", exception); }
    }

    public String evidenceFingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException("Cash evidence must not pass through floating-point numbers");
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
            if (decimal.precision() > 1000 || decimal.scale() < -1000 || decimal.scale() > 1000) {
                throw new IllegalArgumentException("Cash evidence number exceeds fingerprint bounds");
            }
            return decimal.setScale(Math.max(0, decimal.scale()));
        }
        if (value instanceof Map<?, ?> values) {
            var result = new LinkedHashMap<String, Object>();
            values.forEach((key, item) -> result.put((String) key, normalize(item)));
            return result;
        }
        if (value instanceof List<?> values) return values.stream().map(CashSnapshotCodec::normalize).toList();
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (!node.get(field).isTextual() || node.get(field).textValue().isBlank()) {
            throw new IllegalArgumentException("Cash snapshot text required: " + field);
        }
        return node.get(field).textValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Canonical cash UUID required: " + field);
        return id;
    }
}
