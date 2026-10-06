package com.mediflow.surgery.infrastructure.messaging;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase.Command;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort.IncomingEvent;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;

/** Strict wire decoder; valid grants for other purposes are explicitly not applicable. */
@Component
public class SurgeryClearanceDecoder implements com.mediflow.surgery.application.port.out.SurgeryClearanceWirePort {
    private final ObjectMapper mapper;
    public SurgeryClearanceDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    public Command decode(String routingKey, byte[] body, Instant receivedAt) {
        return decodeApplicable(routingKey, body, receivedAt)
                .orElseThrow(() -> new IllegalArgumentException("Clearance is not applicable to Surgery"));
    }

    public java.util.Optional<Command> decodeApplicable(String routingKey, byte[] body, Instant receivedAt) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576 || receivedAt == null) throw new IllegalArgumentException("Invalid body size/time");
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject() || !"financial.clearance.granted".equals(routingKey)
                    || !routingKey.equals(text(root, "eventType")) || !"billing-service".equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1) throw new IllegalArgumentException("Unsupported Surgery clearance envelope");
            var payload = root.path("payload");
            if (!payload.isObject()) throw new IllegalArgumentException("Payload required");
            validateCommon(root, payload);
            if (!"SURGERY".equals(text(payload, "purpose"))) {
                validateOtherPurpose(payload);
                return java.util.Optional.empty();
            }
            for (String field : new String[]{"appointmentId", "recordId", "prescriptionId"}) {
                if (payload.hasNonNull(field)) throw new IllegalArgumentException("Unrelated clearance target " + field);
            }
            if (!payload.path("labTestIds").isArray() || !payload.path("labTestIds").isEmpty()
                    || !payload.path("emergencyOverride").isBoolean() || payload.path("emergencyOverride").booleanValue())
                throw new IllegalArgumentException("Surgery clearance cannot forge an emergency or lab permission");
            if (!payload.path("amount").isNumber()) throw new IllegalArgumentException("Numeric amount required");
            Instant grantedAt = Instant.parse(text(root, "occurredAt"));
            var semantic = mapper.createObjectNode();
            semantic.set("payload", payload); semantic.put("grantedAt", grantedAt.toString());
            UUID clearanceId = uuid(payload, "clearanceId");
            var clearance = new SurgeryFinancialClearance(clearanceId, uuid(payload, "invoiceId"), uuid(payload, "accountId"),
                    uuid(payload, "patientId"), uuid(payload, "surgeryCaseId"), CareEpisodeType.valueOf(text(payload, "careEpisodeType")),
                    uuid(payload, "careEpisodeId"), optionalUuid(payload, "admissionId"), payload.path("amount").decimalValue(),
                    text(payload, "currency"), text(payload, "paymentMethod"), grantedAt,
                    payload.hasNonNull("expiresAt") ? Instant.parse(text(payload, "expiresAt")) : null, hash(semantic));
            UUID eventId = uuid(root, "eventId");
            // Full envelope dedupe by eventId; immutable clearance identity is checked separately in storage.
            byte[] canonical = mapper.writeValueAsBytes(mapper.convertValue(root, Map.class));
            return java.util.Optional.of(new Command(new IncomingEvent(eventId, routingKey, 1, "billing-service", hash(root),
                    routingKey + ":" + eventId, canonical, receivedAt), clearance, text(root, "correlationId")));
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid Surgery clearance", invalid); }
    }

    private static void validateCommon(JsonNode root, JsonNode payload) {
        uuid(root,"eventId");
        if (text(root,"correlationId").length() > 128) throw new IllegalArgumentException("Correlation too long");
        Instant granted = Instant.parse(text(root,"occurredAt"));
        for (String field : new String[]{"clearanceId","invoiceId","accountId","patientId","careEpisodeId"}) uuid(payload,field);
        CareEpisodeType.valueOf(text(payload,"careEpisodeType"));
        if (!payload.path("amount").isNumber() || payload.path("amount").decimalValue().signum() <= 0
                || payload.path("amount").decimalValue().scale() > 2
                || payload.path("amount").decimalValue().precision() > 19
                || !text(payload,"currency").matches("[A-Z]{3}")
                || !java.util.Set.of("CASH","TRANSFER").contains(text(payload,"paymentMethod"))
                || !payload.path("emergencyOverride").isBoolean() || payload.path("emergencyOverride").booleanValue()) {
            throw new IllegalArgumentException("Invalid financial evidence");
        }
        if (payload.hasNonNull("expiresAt") && !Instant.parse(text(payload,"expiresAt")).isAfter(granted))
            throw new IllegalArgumentException("Invalid grant interval");
    }

    private static void validateOtherPurpose(JsonNode payload) {
        UUID appointment = optionalUuid(payload,"appointmentId"), record = optionalUuid(payload,"recordId"),
                prescription = optionalUuid(payload,"prescriptionId"), admission = optionalUuid(payload,"admissionId"),
                surgery = optionalUuid(payload,"surgeryCaseId"), episode = uuid(payload,"careEpisodeId");
        var episodeType = CareEpisodeType.valueOf(text(payload,"careEpisodeType"));
        if (!payload.path("labTestIds").isArray()) throw new IllegalArgumentException("Lab target array required");
        java.util.Set<UUID> labs = new java.util.HashSet<>();
        for (JsonNode lab : payload.path("labTestIds")) {
            if (!lab.isTextual() || !labs.add(parseUuid(lab.textValue()))) throw new IllegalArgumentException("Invalid lab target");
        }
        boolean valid = switch (text(payload,"purpose")) {
            case "EXAM" -> episodeType == CareEpisodeType.OUTPATIENT_VISIT && (appointment != null || record != null)
                    && episode.equals(appointment == null ? record : appointment) && labs.isEmpty()
                    && prescription == null && admission == null && surgery == null;
            case "LAB_TEST" -> episodeType == CareEpisodeType.OUTPATIENT_VISIT && !labs.isEmpty()
                    && appointment == null && prescription == null && admission == null && surgery == null;
            case "PRESCRIPTION" -> episodeType == CareEpisodeType.OUTPATIENT_VISIT && prescription != null
                    && appointment == null && record == null && labs.isEmpty() && admission == null && surgery == null;
            case "ADMISSION_DEPOSIT" -> episodeType == CareEpisodeType.ADMISSION && episode.equals(admission)
                    && appointment == null && record == null && labs.isEmpty() && prescription == null && surgery == null;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Malformed non-Surgery clearance");
    }
    private String hash(JsonNode root) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(mapper.convertValue(root, Map.class))));
    }
    private static String text(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.textValue();
    }
    private static UUID uuid(JsonNode root, String field) { return parseUuid(text(root, field)); }
    private static UUID parseUuid(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Canonical UUID required");
        return UUID.fromString(value);
    }
    private static UUID optionalUuid(JsonNode root, String field) { return root.hasNonNull(field) ? uuid(root, field) : null; }
}
