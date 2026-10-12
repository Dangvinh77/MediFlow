package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.application.port.out.ExamChargeWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Strict reader of the actual clinical-service {@code appointment.status.changed} producer bytes.
 * Clinical reuses this routing key for every appointment transition; only the normal check-in
 * (PENDING -> AWAITING_PAYMENT) is the EXAM charge trigger. Any other well-formed transition is
 * valid but not applicable here, same spirit as Surgery's non-SURGERY clearance handling. */
@Component
public class ExamChargeDecoder implements ExamChargeWirePort {
    private final ObjectMapper mapper;
    public ExamChargeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public Optional<ExamChargeCommand> decodeApplicable(String key, byte[] body) {
        try {
            if (!"appointment.status.changed".equals(key) || body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException();
            var root = mapper.readTree(body); var payload = root.path("payload");
            if (!root.isObject() || !payload.isObject() || !key.equals(text(root, "eventType"))
                    || !"clinical-service".equals(text(root, "producer")) || integer(root, "version") != 1
                    || !"EXAM".equals(text(payload, "sourceType"))) throw new IllegalArgumentException();
            var appointmentId = uuid(payload, "appointmentId");
            if (!appointmentId.equals(uuid(payload, "sourceId")) || !appointmentId.equals(uuid(payload, "careEpisodeId")))
                throw new IllegalArgumentException();
            var eventId = uuid(root, "eventId");
            var correlationId = text(root, "correlationId");
            var patientId = uuid(payload, "patientId");
            var departmentId = uuid(payload, "departmentId");
            var careEpisodeType = text(payload, "careEpisodeType");
            var priceCode = text(payload, "priceCode");
            var changedAt = instant(payload, "changedAt");
            var oldStatus = text(payload, "oldStatus");
            var newStatus = text(payload, "newStatus");
            if (!"AWAITING_PAYMENT".equals(newStatus)) {
                return Optional.empty(); // a different, valid appointment transition: no EXAM charge here
            }
            if (!"PENDING".equals(oldStatus) || payload.hasNonNull("recordId")) throw new IllegalArgumentException();
            return Optional.of(new ExamChargeCommand(eventId, hash(root), hash(payload), correlationId,
                    appointmentId, patientId, departmentId, careEpisodeType, appointmentId, priceCode, changedAt));
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Invalid Exam charge contract");
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
