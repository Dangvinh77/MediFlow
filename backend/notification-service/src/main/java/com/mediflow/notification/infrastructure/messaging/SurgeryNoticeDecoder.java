package com.mediflow.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;
import com.mediflow.notification.application.port.out.SurgeryNoticeWirePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Same actual Surgery V1 bytes; retain hashes and exact reminder identity, not clinical text. */
@Component
public class SurgeryNoticeDecoder implements SurgeryNoticeWirePort {
    private final ObjectMapper mapper;
    public SurgeryNoticeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public SurgeryNoticeCommand decode(String key, byte[] body) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576
                    || !Set.of("surgery.ready", "surgery.readiness.invalidated", "surgery.cancelled", "surgery.completed").contains(key))
                throw new IllegalArgumentException();
            var root = mapper.readTree(body);
            if (!root.isObject() || !key.equals(text(root, "eventType")) || !"surgery-service".equals(text(root, "producer"))
                    || integer(root, "version") != 1) throw new IllegalArgumentException();
            var payload = root.path("payload");
            if (!payload.isObject() || integer(payload, "caseRevision") < 1) throw new IllegalArgumentException();
            var context = new SurgeryNoticeCommand.Context(uuid(payload, "surgeryCaseId"), uuid(payload, "surgeryRequestId"),
                    uuid(payload, "patientId"), uuid(payload, "departmentId"), text(payload, "careEpisodeType"),
                    uuid(payload, "careEpisodeId"), optionalUuid(payload, "admissionId"), optionalUuid(payload, "recordId"));
            UUID source, schedule = null; Long scheduleRevision = null; Instant business, planned = null;
            switch (key) {
                case "surgery.ready" -> {
                    source = uuid(payload, "readinessSnapshotId"); schedule = uuid(payload, "scheduleId");
                    scheduleRevision = positive(payload, "scheduleRevision"); uuid(payload, "roomId");
                    planned = instant(payload, "plannedStartAt");
                    if (!instant(payload, "plannedEndAt").isAfter(planned) || !payload.path("reservationConfirmed").isBoolean()
                            || payload.path("reservationConfirmed").booleanValue() || !payload.path("emergencyOverrideUsed").isBoolean()
                            || payload.path("emergencyOverrideUsed").booleanValue()) throw new IllegalArgumentException();
                    business = instant(payload, "readyAt");
                }
                case "surgery.readiness.invalidated" -> {
                    source = uuid(payload, "readinessSnapshotId"); schedule = uuid(payload, "scheduleId");
                    scheduleRevision = positive(payload, "scheduleRevision"); business = instant(payload, "invalidatedAt");
                    if (!Set.of("READINESS_EXPIRED", "ORGANIZATION_AUTHORITY_CHANGED", "FINANCIAL_CLEARANCE_CHANGED", "CONSENT_CHANGED",
                            "CONSENT_REVOKED", "CHECKLIST_CHANGED", "SCHEDULE_REPLACED", "READINESS_RECHECK_FAILED").contains(text(payload, "reasonCode")))
                        throw new IllegalArgumentException();
                }
                case "surgery.cancelled" -> {
                    source = uuid(payload, "cancellationId"); business = instant(payload, "cancelledAt");
                    if (integer(payload, "sourceRevision") != 1 || !Set.of("BEFORE_PREOP", "AFTER_PREOP", "BEFORE_START").contains(text(payload, "cancellationStage"))
                            || !"PRE_START_CANCELLATION".equals(text(payload, "reasonCode"))
                            || !source.equals(UUID.nameUUIDFromBytes(("surgery.cancelled:" + context.surgeryCaseId()).getBytes(StandardCharsets.UTF_8))))
                        throw new IllegalArgumentException();
                    uuid(payload, "cancelledBy"); optionalUuid(payload, "cancelledByStaffId"); text(payload, "reason");
                }
                case "surgery.completed" -> {
                    source = uuid(payload, "resultId"); business = instant(payload, "completedAt");
                    if (integer(payload, "sourceRevision") != 1 || business.isBefore(instant(payload, "startedAt")))
                        throw new IllegalArgumentException();
                }
                default -> throw new IllegalArgumentException();
            }
            if (instant(root, "occurredAt").isBefore(business)) throw new IllegalArgumentException();
            return new SurgeryNoticeCommand(uuid(root, "eventId"), key, hash(root), hash(payload), text(root, "correlationId"),
                    context, source, schedule, scheduleRevision, planned, business);
        } catch (Exception malformed) {
            // No raw-body/parser/domain cause escapes into the Rabbit listener logs.
            throw new IllegalArgumentException("Invalid Surgery notification contract");
        }
    }
    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }
    private String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }
    private UUID uuid(JsonNode node, String field) {
        var raw = text(node, field); UUID value = UUID.fromString(raw);
        if (!value.toString().equals(raw)) throw new IllegalArgumentException();
        return value;
    }
    private UUID optionalUuid(JsonNode node, String field) { return !node.hasNonNull(field) ? null : uuid(node, field); }
    private Instant instant(JsonNode node, String field) { return Instant.parse(text(node, field)); }
    private long integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException();
        return value.longValue();
    }
    private long positive(JsonNode node, String field) {
        long value = integer(node, field); if (value < 1) throw new IllegalArgumentException(); return value;
    }
}
