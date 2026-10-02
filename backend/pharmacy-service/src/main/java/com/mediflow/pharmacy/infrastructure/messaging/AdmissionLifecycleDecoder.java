package com.mediflow.pharmacy.infrastructure.messaging;

import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;
import com.mediflow.pharmacy.domain.model.AdmissionLifecycleFact;
import com.mediflow.pharmacy.domain.model.AdmissionLifecycleFact.Kind;

/** Offline decoder of existing Inpatient V1 bytes. Deliberately not a Rabbit listener. */
@Component
public class AdmissionLifecycleDecoder {
    private final ObjectMapper mapper;

    public AdmissionLifecycleDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public AdmissionLifecycleCommand decode(String routingKey, byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject() || !routingKey.equals(text(root, "eventType"))
                    || !"inpatient-service".equals(text(root, "producer"))) {
                throw new IllegalArgumentException("Invalid admission envelope/type/producer");
            }
            JsonNode version = root.get("version");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                    || version.intValue() != 1) {
                throw new IllegalArgumentException("Only Inpatient envelope version 1 is supported");
            }
            UUID eventId = uuid(root, "eventId");
            Instant.parse(text(root, "occurredAt"));
            text(root, "correlationId");
            JsonNode payload = root.get("payload");
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Admission payload is required");
            }
            UUID admissionId = uuid(payload, "admissionId");
            UUID patientId = uuid(payload, "patientId");
            Kind kind;
            UUID departmentId = null;
            Instant effectiveAt;
            if ("admission.started".equals(routingKey)) {
                kind = Kind.STARTED;
                departmentId = uuid(payload, "departmentId");
                uuid(payload, "bedId");
                effectiveAt = Instant.parse(text(payload, "admittedAt"));
                JsonNode emergency = payload.get("emergency");
                if (emergency == null || !emergency.isBoolean()) {
                    throw new IllegalArgumentException("emergency must be boolean");
                }
                UUID overrideId = optionalUuid(payload, "emergencyOverrideId");
                if (!emergency.booleanValue() && overrideId != null) {
                    throw new IllegalArgumentException("Non-emergency start cannot carry an emergency override");
                }
            } else if ("admission.closed".equals(routingKey)) {
                kind = Kind.CLOSED;
                effectiveAt = Instant.parse(text(payload, "closedAt"));
                UUID settlementId = optionalUuid(payload, "settlementId");
                UUID overrideId = optionalUuid(payload, "approvedOverrideId");
                if (settlementId == null && overrideId == null) {
                    throw new IllegalArgumentException("Admission closure requires settlement or override");
                }
            } else {
                throw new IllegalArgumentException("Unsupported admission lifecycle type");
            }
            AdmissionLifecycleFact fact = new AdmissionLifecycleFact(kind, admissionId, patientId,
                    departmentId, effectiveAt, hash(payload));
            return new AdmissionLifecycleCommand(eventId, hash(root), fact);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Inpatient lifecycle event", exception);
        }
    }

    private String hash(JsonNode node) throws Exception {
        // Hash all payload fields, including unknown additive fields; JSON key order is immaterial.
        byte[] canonical = mapper.writeValueAsBytes(mapper.convertValue(node, Map.class));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.textValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : uuid(node, field);
    }
}
