package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand;
import com.mediflow.billing.application.port.out.PrescriptionChargeWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionChargeDecoder implements PrescriptionChargeWirePort {
    private final ObjectMapper mapper;
    private static final Set<String> ENVELOPE = Set.of("eventId", "eventType", "version", "occurredAt", "correlationId", "producer", "payload");
    private static final Set<String> PAYLOAD = Set.of("prescriptionId", "recordId", "admissionId", "patientId", "departmentId",
            "careContext", "careEpisodeType", "careEpisodeId", "sourceType", "sourceId", "priceCode", "items", "totalAmount", "createdAt");

    public PrescriptionChargeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public PrescriptionChargeCommand decode(String key, byte[] body) {
        try {
            if (!"prescription.created".equals(key) || body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException();
            var root = mapper.readTree(body); fields(root, ENVELOPE);
            var p = root.path("payload"); fields(p, PAYLOAD);
            if (!key.equals(text(root, "eventType")) || !"pharmacy-service".equals(text(root, "producer"))
                    || integer(root, "version") != 1 || !"PRESCRIPTION".equals(text(p, "sourceType"))) throw new IllegalArgumentException();
            UUID prescription = uuid(p, "prescriptionId");
            Instant created = Instant.parse(text(p, "createdAt"));
            if (!prescription.equals(uuid(p, "sourceId")) || !created.equals(Instant.parse(text(root, "occurredAt"))))
                throw new IllegalArgumentException();
            var items = p.get("items");
            if (items == null || !items.isArray() || items.size() > 1000) throw new IllegalArgumentException();
            var lines = new ArrayList<PrescriptionChargeCommand.Item>();
            for (var item : items) {
                fields(item, Set.of("drugId", "drugName", "quantity", "unitPrice"));
                lines.add(new PrescriptionChargeCommand.Item(uuid(item, "drugId"), text(item, "drugName"),
                        integer(item, "quantity"), amount(item, "unitPrice")));
            }
            return new PrescriptionChargeCommand(uuid(root, "eventId"), hash(root), hash(p), text(root, "correlationId"),
                    prescription, optionalUuid(p, "recordId"), uuid(p, "patientId"), uuid(p, "departmentId"),
                    text(p, "careContext"), text(p, "careEpisodeType"), uuid(p, "careEpisodeId"), optionalUuid(p, "admissionId"),
                    text(p, "priceCode"), lines, amount(p, "totalAmount"), created, snapshotHash(p));
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid Pharmacy V1 charge contract");
        }
    }
    public PrescriptionTerminalCommand decodeTerminal(String key, byte[] body) {
        try {
            String timeField = switch (key) {
                case "prescription.filled" -> "filledAt";
                case "prescription.cancelled" -> "cancelledAt";
                case "prescription.expired" -> "expiredAt";
                case "prescription.dispense.failed" -> "failedAt";
                default -> throw new IllegalArgumentException();
            };
            if (body == null || body.length == 0 || body.length > 1_048_576) throw new IllegalArgumentException();
            var root = mapper.readTree(body); fields(root, ENVELOPE);
            var p = root.path("payload");
            var allowed = new java.util.HashSet<>(PAYLOAD); allowed.remove("createdAt"); allowed.add(timeField);
            allowed.add("prescription.filled".equals(key) ? "dispenseId" : "reason");
            fields(p, allowed);
            if (!key.equals(text(root, "eventType"))) throw new IllegalArgumentException();
            if ("prescription.filled".equals(key)) uuid(p, "dispenseId");
            else {
                var reason = text(p, "reason");
                if (reason.length() > 500 || reason.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
            }
            var originalDelivery = hash(root); var originalSource = hash(p);
            // Reuse creation's strict context/price/item validation, without changing original hashes.
            var synthetic = (com.fasterxml.jackson.databind.node.ObjectNode)root.deepCopy();
            synthetic.put("eventType", "prescription.created");
            var copy = (com.fasterxml.jackson.databind.node.ObjectNode)synthetic.path("payload");
            String at = text(copy, timeField);
            copy.remove(java.util.List.of(timeField, "reason", "dispenseId")); copy.put("createdAt", at);
            var checked = decode("prescription.created", mapper.writeValueAsBytes(synthetic));
            return new PrescriptionTerminalCommand(key, new PrescriptionChargeCommand(checked.eventId(), originalDelivery,
                    originalSource, checked.correlationId(), checked.prescriptionId(), checked.recordId(), checked.patientId(),
                    checked.departmentId(), checked.careContext(), checked.careEpisodeType(), checked.careEpisodeId(),
                    checked.admissionId(), checked.priceCode(), checked.items(), checked.totalAmount(), checked.createdAt(),
                    checked.snapshotFingerprint()));
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid Pharmacy V1 terminal contract");
        }
    }
    private String snapshotHash(JsonNode payload) throws Exception {
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode)payload.deepCopy();
        copy.remove("createdAt");
        return hash(copy);
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
        var raw = text(node, field); var value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw new IllegalArgumentException(); return value;
    }
    private static UUID optionalUuid(JsonNode node, String field) { return node.hasNonNull(field) ? uuid(node, field) : null; }
    private static int integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw new IllegalArgumentException();
        return value.intValue();
    }
    private static java.math.BigDecimal amount(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(); return value.decimalValue();
    }
}
