package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Strict reader of the actual inpatient-service {@code admission.deposit.requested} producer bytes. */
@Component
public class AdmissionDepositRequestDecoder implements AdmissionDepositRequestWirePort {
    private final ObjectMapper mapper;
    public AdmissionDepositRequestDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public AdmissionDepositRequestCommand decode(String key, byte[] body) {
        try {
            if (!"admission.deposit.requested".equals(key) || body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException();
            var root = mapper.readTree(body); var payload = root.path("payload");
            if (!root.isObject() || !payload.isObject() || !key.equals(text(root, "eventType"))
                    || !"inpatient-service".equals(text(root, "producer")) || integer(root, "version") != 1
                    || !"ADMISSION_DEPOSIT".equals(text(payload, "sourceType"))) throw new IllegalArgumentException();
            var admissionId = uuid(payload, "admissionId");
            if (!admissionId.equals(uuid(payload, "sourceId")) || !admissionId.equals(uuid(payload, "careEpisodeId")))
                throw new IllegalArgumentException();
            if (!payload.path("suggestedAmount").isNumber()) throw new IllegalArgumentException();
            return new AdmissionDepositRequestCommand(uuid(root, "eventId"), hash(root), hash(payload), text(root, "correlationId"),
                    admissionId, uuid(payload, "patientId"), uuid(payload, "departmentId"), text(payload, "careEpisodeType"),
                    admissionId, text(payload, "priceCode"), payload.path("suggestedAmount").decimalValue(),
                    text(payload, "reason"), instant(root, "occurredAt"));
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Invalid Admission deposit request contract");
        }
    }
    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
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
    private static Instant instant(JsonNode node, String field) { return Instant.parse(text(node, field)); }
    private static long integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException(); return value.longValue();
    }
}
