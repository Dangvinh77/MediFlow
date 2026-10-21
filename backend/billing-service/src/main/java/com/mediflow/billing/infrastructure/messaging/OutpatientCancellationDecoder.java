package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.OutpatientCancellationCommand;
import com.mediflow.billing.application.port.out.OutpatientCancellationWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Strict reader for the V1 Lab and Clinical terminal facts consumed by Billing. */
@Component
public final class OutpatientCancellationDecoder implements OutpatientCancellationWirePort {
    private final ObjectMapper mapper;
    public OutpatientCancellationDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public OutpatientCancellationCommand decode(String routingKey, byte[] body) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576) throw new IllegalArgumentException();
            boolean lab = "lab.request.cancelled".equals(routingKey);
            boolean exam = "appointment.exam.cancelled".equals(routingKey);
            if (!(lab || exam)) throw new IllegalArgumentException();
            var root = mapper.readTree(body);
            var payload = root.path("payload");
            String producer = lab ? "lab-service" : "clinical-service";
            if (!root.isObject() || !payload.isObject() || !routingKey.equals(text(root, "eventType"))
                    || !producer.equals(text(root, "producer")) || integer(root, "version") != 1
                    || !Instant.parse(text(payload, "cancelledAt")).equals(Instant.parse(text(root, "occurredAt"))))
                throw new IllegalArgumentException();
            for (String field : new String[] {"supersedes", "supersedesEventId", "originalEventId", "replacement",
                    "replacesResultId", "correctionId", "isCorrection"})
                if (root.has(field) || payload.has(field)) throw new IllegalArgumentException();
            UUID sourceId;
            UUID sourceOrderId = null;
            UUID recordId = null;
            String priceCode = null;
            String labType = null;
            UUID departmentId;
            String sourceType;
            if (lab) {
                sourceId = uuid(payload, "testId");
                if (!sourceId.equals(uuid(payload, "sourceId")) || !"LAB_TEST".equals(text(payload, "sourceType")))
                    throw new IllegalArgumentException();
                sourceOrderId = uuid(payload, "sourceOrderId");
                recordId = optionalUuid(payload, "recordId");
                departmentId = uuid(payload, "requestingDepartmentId");
                priceCode = text(payload, "priceCode");
                labType = text(payload, "labType");
                sourceType = "LAB_TEST";
            } else {
                sourceId = uuid(payload, "appointmentId");
                if (!sourceId.equals(uuid(payload, "sourceId")) || !sourceId.equals(uuid(payload, "careEpisodeId"))
                        || !"EXAM".equals(text(payload, "sourceType"))
                        || !"OUTPATIENT_VISIT".equals(text(payload, "careEpisodeType")))
                    throw new IllegalArgumentException();
                departmentId = uuid(payload, "departmentId");
                sourceType = "EXAM";
            }
            var reason = text(payload, "reason");
            if (reason.length() > (lab ? 1000 : 500)) throw new IllegalArgumentException();
            return new OutpatientCancellationCommand(uuid(root, "eventId"), hash(root), hash(payload),
                    text(root, "correlationId"), sourceType, sourceId, sourceOrderId, uuid(payload, "patientId"),
                    departmentId, recordId, text(payload, "careEpisodeType"), uuid(payload, "careEpisodeId"),
                    priceCode, labType, uuid(payload, "cancelledBy"), reason, Instant.parse(text(payload, "cancelledAt")));
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid outpatient cancellation contract");
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
        var raw = text(node, field); var value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw new IllegalArgumentException(); return value;
    }
    private static UUID optionalUuid(JsonNode node, String field) { return node.hasNonNull(field) ? uuid(node, field) : null; }
    private static long integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException();
        return value.longValue();
    }
}
