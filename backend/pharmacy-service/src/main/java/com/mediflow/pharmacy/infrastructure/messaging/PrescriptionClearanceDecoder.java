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

/** Strict Billing wire boundary; malformed input is never classified as not applicable. */
@Component
public class PrescriptionClearanceDecoder implements com.mediflow.pharmacy.application.port.out.PrescriptionClearanceWirePort {
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
        return decodeApplicable(routingKey, body)
                .orElseThrow(() -> new IllegalArgumentException("Clearance is not applicable to Pharmacy"));
    }

    @Override
    public java.util.Optional<PrescriptionClearanceCommand> decodeApplicable(String routingKey, byte[] body) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException("Invalid clearance body size");
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
            if (text(root, "correlationId").length() > 128)
                throw new IllegalArgumentException("Correlation too long");
            JsonNode payload = root.get("payload");
            if (payload == null || !payload.isObject()) throw new IllegalArgumentException("Payload required");
            if (!java.util.Set.of("CASH", "TRANSFER").contains(text(payload, "paymentMethod")))
                throw new IllegalArgumentException("Unsupported Billing payment method");
            if (!"PRESCRIPTION".equals(text(payload, "purpose"))) {
                validateOtherPurpose(payload, grantedAt);
                return java.util.Optional.empty();
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
            return java.util.Optional.of(new PrescriptionClearanceCommand(eventId, hash(root), grant));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid prescription clearance event", exception);
        }
    }

    private static void validateOtherPurpose(JsonNode payload, Instant grantedAt) {
        for (String field : new String[]{"clearanceId", "invoiceId", "accountId", "patientId"}) uuid(payload, field);
        UUID episode = uuid(payload, "careEpisodeId"), appointment = optionalUuid(payload, "appointmentId"),
                record = optionalUuid(payload, "recordId"), prescription = optionalUuid(payload, "prescriptionId"),
                admission = optionalUuid(payload, "admissionId"), surgery = optionalUuid(payload, "surgeryCaseId");
        var episodeType = CareEpisodeType.valueOf(text(payload, "careEpisodeType"));
        if (!payload.path("labTestIds").isArray()) throw new IllegalArgumentException("Lab target array required");
        java.util.Set<UUID> labs = new java.util.HashSet<>();
        for (var item : payload.path("labTestIds")) {
            if (!item.isTextual() || !labs.add(parseUuid(item.textValue())))
                throw new IllegalArgumentException("Invalid lab target");
        }
        boolean valid = switch (text(payload, "purpose")) {
            case "EXAM" -> episodeType == CareEpisodeType.OUTPATIENT_VISIT && (appointment != null || record != null)
                    && episode.equals(appointment == null ? record : appointment) && labs.isEmpty()
                    && prescription == null && admission == null && surgery == null;
            case "LAB_TEST" -> episodeType == CareEpisodeType.OUTPATIENT_VISIT && !labs.isEmpty()
                    && appointment == null && prescription == null && admission == null && surgery == null;
            case "ADMISSION_DEPOSIT" -> episodeType == CareEpisodeType.ADMISSION && episode.equals(admission)
                    && appointment == null && record == null && labs.isEmpty() && prescription == null && surgery == null;
            case "SURGERY" -> surgery != null && prescription == null && appointment == null && record == null && labs.isEmpty()
                    && (episodeType == CareEpisodeType.ADMISSION ? episode.equals(admission) : admission == null);
            default -> false;
        };
        if (!valid || !payload.path("emergencyOverride").isBoolean() || payload.path("emergencyOverride").booleanValue()
                || !payload.path("amount").isNumber() || payload.path("amount").decimalValue().signum() < 0
                || payload.path("amount").decimalValue().scale() > 2 || payload.path("amount").decimalValue().precision() > 19
                || !"VND".equals(text(payload, "currency"))
                || !java.util.Set.of("CASH", "TRANSFER").contains(text(payload, "paymentMethod"))
                || payload.hasNonNull("expiresAt") && !Instant.parse(text(payload, "expiresAt")).isAfter(grantedAt)) {
            throw new IllegalArgumentException("Malformed non-prescription clearance");
        }
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        return node.hasNonNull(field) ? uuid(node, field) : null;
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
        return parseUuid(text(node, field));
    }

    private static UUID parseUuid(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Canonical UUID required");
        return UUID.fromString(value);
    }
}
