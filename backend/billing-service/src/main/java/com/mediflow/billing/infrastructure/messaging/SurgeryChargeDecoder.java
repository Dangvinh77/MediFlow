package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;
import com.mediflow.billing.application.port.out.SurgeryChargeWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SurgeryChargeDecoder implements SurgeryChargeWirePort {
    private final ObjectMapper mapper;
    public SurgeryChargeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public SurgeryChargeCommand decode(String key, byte[] body) {
        try {
            if (!"surgery.case.created".equals(key) || body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException();
            var root = mapper.readTree(body); var payload = root.path("payload");
            if (!root.isObject() || !payload.isObject() || !key.equals(text(root, "eventType"))
                    || !"surgery-service".equals(text(root, "producer")) || integer(root, "version") != 1
                    || integer(payload, "caseRevision") != 0 || integer(payload, "sourceRevision") != 1
                    || !"SURGERY".equals(text(payload, "sourceType"))) throw new IllegalArgumentException();
            UUID surgeryCase = uuid(payload, "surgeryCaseId");
            if (!surgeryCase.equals(uuid(payload, "sourceId")) || !code(text(payload, "procedureCode"))
                    || !Set.of("ROUTINE", "URGENT", "EMERGENCY").contains(text(payload, "priority"))) throw new IllegalArgumentException();
            Instant requested = instant(payload, "requestedAt");
            if (instant(root, "occurredAt").isBefore(requested)) throw new IllegalArgumentException();
            var items = payload.get("plannedItems");
            if (items == null || !items.isArray()) throw new IllegalArgumentException();
            var planned = new ArrayList<SurgeryChargeCommand.Item>();
            for (var item : items) {
                if (!item.isObject() || !item.path("quantity").isNumber()) throw new IllegalArgumentException();
                planned.add(new SurgeryChargeCommand.Item(text(item, "itemCode"), text(item, "priceCode"), item.path("quantity").decimalValue()));
            }
            return new SurgeryChargeCommand(uuid(root, "eventId"), hash(root), hash(payload), text(root, "correlationId"),
                    surgeryCase, uuid(payload, "surgeryRequestId"), uuid(payload, "patientId"), uuid(payload, "departmentId"),
                    text(payload, "careEpisodeType"), uuid(payload, "careEpisodeId"), optionalUuid(payload, "admissionId"),
                    optionalUuid(payload, "recordId"), uuid(payload, "requestedBy"), requested, planned);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Invalid Surgery charge contract");
        }
    }
    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }
    private static boolean code(String value) { return value.matches("[A-Za-z0-9._-]{1,64}"); }
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
    private static Instant instant(JsonNode node, String field) { return Instant.parse(text(node, field)); }
    private static long integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException(); return value.longValue();
    }
}
