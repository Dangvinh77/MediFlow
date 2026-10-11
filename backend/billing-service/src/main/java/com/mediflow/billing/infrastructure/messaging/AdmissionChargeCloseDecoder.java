package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads the original five-field Inpatient V1 fact, without compatibility fallback. */
public final class AdmissionChargeCloseDecoder implements com.mediflow.billing.application.port.out.AdmissionChargeCloseWirePort {
    public static final String ROUTING_KEY = "discharge.medically.approved";
    private static final Set<String> ENVELOPE = Set.of("eventId", "eventType", "version", "occurredAt",
            "correlationId", "producer", "payload");
    private static final Set<String> PAYLOAD = Set.of("admissionId", "patientId", "summaryId", "approvedBy", "approvedAt");
    private final ObjectMapper mapper;

    public AdmissionChargeCloseDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public CloseAdmissionChargesCommand decode(String routingKey, byte[] bytes) {
        try {
            if (!ROUTING_KEY.equals(routingKey) || bytes == null || bytes.length == 0 || bytes.length > 16_384) {
                throw new IllegalArgumentException();
            }
            var root = mapper.readTree(bytes);
            exactFields(root, ENVELOPE);
            var payload = root.get("payload");
            exactFields(payload, PAYLOAD);
            if (!ROUTING_KEY.equals(text(root, "eventType")) || !"inpatient-service".equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1) throw new IllegalArgumentException();
            var approvedIso = text(payload, "approvedAt");
            var approved = Instant.parse(approvedIso);
            if (!approved.equals(Instant.parse(text(root, "occurredAt")))) throw new IllegalArgumentException();
            return new CloseAdmissionChargesCommand(uuid(root, "eventId"), uuid(payload, "admissionId"),
                    uuid(payload, "patientId"), uuid(payload, "summaryId"), uuid(payload, "approvedBy"),
                    approved, approvedIso, text(root, "correlationId"), hash(payload), hash(root));
        } catch (Exception invalid) {
            // Never include raw clinical/financial input or serializer diagnostics in the error.
            throw new IllegalArgumentException("Invalid Inpatient medical-discharge V1 contract");
        }
    }

    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }

    private static void exactFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || node.size() != fields.size()) throw new IllegalArgumentException();
        node.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) throw new IllegalArgumentException(); });
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
}
