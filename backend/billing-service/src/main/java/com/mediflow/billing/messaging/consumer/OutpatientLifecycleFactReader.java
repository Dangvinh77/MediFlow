package com.mediflow.billing.messaging.consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Distinguishes informational V1 facts from the retired flat fee triggers.
 * EXAM is charged at check-in and LAB at request creation; completion facts must
 * never create an additional V0 fee. Invalid or disabled V1 input fails closed. */
final class OutpatientLifecycleFactReader {
    private final ObjectMapper mapper;

    public OutpatientLifecycleFactReader(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public boolean isInformational(String key, byte[] body, boolean issuerEnabled) throws IOException {
        if (body == null || body.length == 0 || body.length > 1_048_576) throw invalid();
        final JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException malformed) {
            throw invalid();
        }
        if (root == null || !root.isObject()) throw invalid();
        boolean versioned = root.has("version") || root.has("payload") || root.has("eventType") || root.has("producer");
        if (!issuerEnabled && !versioned) return false; // explicit V0 compatibility mode
        if (!issuerEnabled || !versioned) throw invalid();
        try {
            var payload = root.path("payload");
            boolean exam = "medicalrecord.created".equals(key);
            String producer = exam ? "clinical-service" : "lab-service";
            if (!key.equals(text(root, "eventType")) || !producer.equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1 || !payload.isObject()
                    || !"OUTPATIENT_VISIT".equals(text(payload, "careEpisodeType"))) throw invalid();
            uuid(root, "eventId");
            text(root, "correlationId");
            Instant.parse(text(root, "occurredAt"));
            uuid(payload, "recordId");
            uuid(payload, "patientId");
            uuid(payload, "departmentId");
            var episodeId = uuid(payload, "careEpisodeId");
            if (exam) {
                var appointmentId = uuid(payload, "appointmentId");
                if (!appointmentId.equals(episodeId) || !appointmentId.equals(uuid(payload, "sourceId"))
                        || !"EXAM".equals(text(payload, "sourceType"))) throw invalid();
            } else {
                uuid(payload, "labId");
                text(payload, "labType");
            }
            return true;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid();
        return value.textValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        var raw = text(node, field);
        var value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw invalid();
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid outpatient lifecycle fact contract or disabled V1 issuer");
    }
}
