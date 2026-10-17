package com.mediflow.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand;
import com.mediflow.notification.application.port.out.PrescriptionNoticeWirePort;

/** Reads the original Pharmacy lifecycle fixtures; stores hashes, not clinical/price details. */
public class PrescriptionNoticeDecoder implements PrescriptionNoticeWirePort {
    private static final Set<String> ENVELOPE = Set.of("eventId", "eventType", "version", "occurredAt",
            "correlationId", "producer", "payload");
    private static final Set<String> SNAPSHOT = Set.of("prescriptionId", "recordId", "patientId", "departmentId",
            "careContext", "careEpisodeType", "careEpisodeId", "sourceType", "sourceId", "priceCode", "items", "totalAmount");
    private final ObjectMapper mapper;

    public PrescriptionNoticeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public PrescriptionNoticeCommand decode(String key, byte[] body) {
        try {
            if (!PrescriptionNoticeCommand.EVENT_TYPES.contains(key) || body == null || body.length == 0
                    || body.length > 1_048_576) throw new IllegalArgumentException();
            var root = mapper.readTree(body);
            fields(root, ENVELOPE);
            if (!key.equals(text(root, "eventType")) || !"pharmacy-service".equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1) throw new IllegalArgumentException();
            String timestamp = switch (key) {
                case "prescription.created" -> "createdAt";
                case "prescription.filled" -> "filledAt";
                case "prescription.cancelled" -> "cancelledAt";
                case "prescription.expired" -> "expiredAt";
                default -> "failedAt";
            };
            var allowed = new HashSet<>(SNAPSHOT);
            allowed.add(timestamp);
            if (key.equals("prescription.filled")) allowed.add("dispenseId");
            else if (!key.equals("prescription.created")) allowed.add("reason");
            var payload = root.path("payload");
            fields(payload, allowed);
            UUID prescription = uuid(payload, "prescriptionId");
            uuid(payload, "recordId"); uuid(payload, "departmentId"); uuid(payload, "careEpisodeId");
            if (!"OUTPATIENT".equals(text(payload, "careContext"))
                    || !"OUTPATIENT_VISIT".equals(text(payload, "careEpisodeType"))
                    || !"PRESCRIPTION".equals(text(payload, "sourceType"))
                    || !prescription.equals(uuid(payload, "sourceId"))
                    || !Instant.parse(text(root, "occurredAt")).equals(Instant.parse(text(payload, timestamp)))) {
                throw new IllegalArgumentException();
            }
            if (text(payload, "priceCode").length() > 64) throw new IllegalArgumentException();
            if (key.equals("prescription.filled")) uuid(payload, "dispenseId");
            else if (!key.equals("prescription.created")) {
                String reason = text(payload, "reason");
                if (reason.length() > 500 || reason.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
            }
            var items = payload.path("items");
            if (!items.isArray() || items.isEmpty() || items.size() > 1000) throw new IllegalArgumentException();
            var drugs = new HashSet<UUID>();
            BigDecimal total = BigDecimal.ZERO;
            for (var item : items) {
                fields(item, Set.of("drugId", "drugName", "quantity", "unitPrice"));
                if (!drugs.add(uuid(item, "drugId")) || text(item, "drugName").length() > 255
                        || !item.path("quantity").isIntegralNumber() || !item.path("quantity").canConvertToInt()
                        || item.path("quantity").intValue() <= 0) throw new IllegalArgumentException();
                total = total.add(money(item, "unitPrice").multiply(BigDecimal.valueOf(item.path("quantity").intValue())));
            }
            BigDecimal stated = money(payload, "totalAmount");
            if (stated.signum() <= 0 || total.compareTo(stated) != 0) throw new IllegalArgumentException();
            return new PrescriptionNoticeCommand(uuid(root, "eventId"), key, hash(root), hash(payload),
                    text(root, "correlationId"), prescription, uuid(payload, "patientId"));
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid Pharmacy outpatient notification contract");
        }
    }

    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }
    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException();
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new IllegalArgumentException(); });
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }
    private static UUID uuid(JsonNode node, String field) {
        String raw = text(node, field);
        UUID value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw new IllegalArgumentException();
        return value;
    }
    private static BigDecimal money(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException();
        BigDecimal amount = value.decimalValue();
        if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                || amount.precision() - amount.scale() > 17) throw new IllegalArgumentException();
        return amount;
    }
}
