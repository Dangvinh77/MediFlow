package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.SurgeryCancellationCommand;
import com.mediflow.billing.application.port.out.SurgeryCancellationWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class SurgeryCancellationDecoder implements SurgeryCancellationWirePort {
    private final ObjectMapper mapper;

    public SurgeryCancellationDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public SurgeryCancellationCommand decode(String key, byte[] body) {
        try {
            if (!"surgery.cancelled".equals(key) || body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException();
            var root = mapper.readTree(body);
            var payload = root.path("payload");
            if (!root.isObject() || !payload.isObject() || !key.equals(text(root, "eventType"))
                    || !"surgery-service".equals(text(root, "producer")) || integer(root, "version") != 1
                    || integer(payload, "sourceRevision") != 1
                    || !"PRE_START_CANCELLATION".equals(text(payload, "reasonCode"))) throw new IllegalArgumentException();
            // V1 has no correction/replacement semantics. Do not hash an unsupported amendment as a new fact.
            for (String field : new String[] {"supersedes", "supersedesEventId", "originalEventId", "replacement",
                    "replacesResultId", "correctionId", "isCorrection"})
                if (root.has(field) || payload.has(field)) throw new IllegalArgumentException();
            var time = Instant.parse(text(payload, "cancelledAt"));
            if (!time.equals(Instant.parse(text(root, "occurredAt"))) || text(payload, "reason").length() > 500)
                throw new IllegalArgumentException();
            return new SurgeryCancellationCommand(uuid(root, "eventId"), hash(root), hash(payload), text(root, "correlationId"),
                    uuid(payload, "surgeryCaseId"), uuid(payload, "surgeryRequestId"), uuid(payload, "cancellationId"),
                    uuid(payload, "patientId"), uuid(payload, "departmentId"), text(payload, "careEpisodeType"),
                    uuid(payload, "careEpisodeId"), optionalUuid(payload, "admissionId"), optionalUuid(payload, "recordId"),
                    integer(payload, "caseRevision"), text(payload, "cancellationStage"), uuid(payload, "cancelledBy"),
                    optionalUuid(payload, "cancelledByStaffId"), time);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid Surgery cancellation contract");
        }
    }

    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }
    private static UUID uuid(JsonNode node, String field) {
        var raw = text(node, field);
        var value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw new IllegalArgumentException();
        return value;
    }
    private static UUID optionalUuid(JsonNode node, String field) { return node.hasNonNull(field) ? uuid(node, field) : null; }
    private static long integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException();
        return value.longValue();
    }
}
