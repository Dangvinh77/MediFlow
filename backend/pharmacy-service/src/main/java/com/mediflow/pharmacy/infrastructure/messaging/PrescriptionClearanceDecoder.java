package com.mediflow.pharmacy.infrastructure.messaging;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand;
import com.mediflow.pharmacy.domain.model.CareEpisode;
import com.mediflow.pharmacy.domain.model.PrescriptionClearance;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

/** Consumer-side contract harness only: no listener, binding or fake Billing fixture. */
@Component
public class PrescriptionClearanceDecoder {
    public static final String EVENT_TYPE = "financial.clearance.granted";
    private final ObjectMapper mapper;

    public PrescriptionClearanceDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public PrescriptionClearanceCommand decode(String routingKey, byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject() || !EVENT_TYPE.equals(routingKey)
                    || !EVENT_TYPE.equals(text(root, "eventType"))
                    || !"billing-service".equals(text(root, "producer"))) {
                throw new IllegalArgumentException("Invalid clearance envelope/type/producer");
            }
            var version = root.get("version");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                    || version.intValue() != 1) {
                throw new IllegalArgumentException("Only clearance version 1 is supported");
            }
            UUID eventId = uuid(root, "eventId");
            Instant grantedAt = Instant.parse(text(root, "occurredAt"));
            text(root, "correlationId");
            JsonNode payload = root.get("payload");
            if (payload == null || !payload.isObject()
                    || !"PRESCRIPTION".equals(text(payload, "purpose"))) {
                throw new IllegalArgumentException("Only PRESCRIPTION clearance is accepted");
            }
            // No EXAM, LAB, admission or emergency target may be used as a fallback permission.
            for (String field : new String[]{"appointmentId", "recordId", "admissionId", "surgeryCaseId"}) {
                if (payload.hasNonNull(field)) {
                    throw new IllegalArgumentException("Unrelated clearance target: " + field);
                }
            }
            var labIds = payload.get("labTestIds");
            if (labIds != null && !labIds.isNull() && (!labIds.isArray() || !labIds.isEmpty())) {
                throw new IllegalArgumentException("PRESCRIPTION clearance cannot target lab tests");
            }
            var emergency = payload.get("emergencyOverride");
            if (emergency == null || !emergency.isBoolean() || emergency.booleanValue()) {
                throw new IllegalArgumentException("Financial clearance cannot forge an emergency override");
            }
            var amount = payload.get("amount");
            if (amount == null || !amount.isNumber()) {
                throw new IllegalArgumentException("Clearance amount must be numeric");
            }
            var expiry = payload.get("expiresAt");
            var grant = new PrescriptionClearance(uuid(payload, "clearanceId"), uuid(payload, "invoiceId"),
                    uuid(payload, "accountId"), uuid(payload, "prescriptionId"), uuid(payload, "patientId"),
                    new CareEpisode(CareEpisodeType.valueOf(text(payload, "careEpisodeType")),
                            uuid(payload, "careEpisodeId")),
                    amount.decimalValue(), text(payload, "currency"), text(payload, "paymentMethod"),
                    grantedAt, expiry == null || expiry.isNull() ? null : Instant.parse(text(payload, "expiresAt")),
                    hash(payload));
            return new PrescriptionClearanceCommand(eventId, hash(root), grant);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid prescription clearance event", exception);
        }
    }

    private String hash(JsonNode value) throws Exception {
        byte[] canonical = mapper.writeValueAsBytes(mapper.convertValue(value, Map.class));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.textValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }
}
