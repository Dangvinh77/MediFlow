package com.mediflow.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.dto.command.RegistrationNoticeCommand;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class RegistrationNoticeDecoder {
    private final ObjectMapper mapper;
    public RegistrationNoticeDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    public RegistrationNoticeCommand decode(byte[] raw) {
        try {
            if (raw == null || raw.length == 0 || raw.length > 1_048_576) throw new IllegalArgumentException();
            JsonNode root = mapper.readTree(raw);
            exactFields(root, Set.of("eventId", "eventType", "version", "occurredAt", "correlationId", "producer", "payload"));
            if (!RegistrationNoticeCommand.EVENT_TYPE.equals(text(root, "eventType"))
                    || !"clinical-service".equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1) throw new IllegalArgumentException();
            Instant.parse(text(root, "occurredAt"));
            var p = root.path("payload");
            exactFields(p, Set.of("registrationId", "registrationRevision", "patientId", "doctorId", "departmentId", "sessionId",
                    "roomId", "sessionDate", "startTime", "endTime", "visitType", "reservationId", "reservationStatus", "status",
                    "checkedInAt", "queueNumber", "reservationExpiresAt", "priceCode", "sourceType", "sourceId", "careEpisodeType",
                    "careEpisodeId", "cancellation", "changedAt"));
            UUID registration = uuid(p, "registrationId");
            if (!registration.equals(uuid(p, "sourceId")) || !registration.equals(uuid(p, "careEpisodeId"))
                    || !"EXAM".equals(text(p, "sourceType")) || !"OUTPATIENT_VISIT".equals(text(p, "careEpisodeType"))
                    || !Set.of("NEW_VISIT", "FOLLOW_UP").contains(text(p, "visitType"))) throw new IllegalArgumentException();
            for (String f : new String[]{"doctorId", "departmentId", "sessionId", "roomId", "reservationId"}) uuid(p, f);
            var revision = p.path("registrationRevision");
            if (!revision.isIntegralNumber() || !revision.canConvertToLong() || revision.longValue() < 1) throw new IllegalArgumentException();
            LocalDate.parse(text(p, "sessionDate"));
            String start = text(p, "startTime"), end = text(p, "endTime");
            if (!start.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") || !end.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                    || !LocalTime.parse(start).isBefore(LocalTime.parse(end))) throw new IllegalArgumentException();
            Instant.parse(text(p, "reservationExpiresAt")); Instant.parse(text(p, "changedAt"));
            if (!p.path("checkedInAt").isNull()) Instant.parse(text(p, "checkedInAt"));
            String status = text(p, "status");
            if (!Set.of("AWAITING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED", "IN_EXAM", "COMPLETED", "RECONCILIATION_REQUIRED").contains(status)
                    || !Set.of("HELD", "PAYMENT_LOCKED", "CONFIRMED", "CONSUMED", "CANCELLED", "EXPIRED")
                    .contains(text(p, "reservationStatus"))) throw new IllegalArgumentException();
            if (!p.path("queueNumber").isNull() && (!p.path("queueNumber").isIntegralNumber()
                    || !p.path("queueNumber").canConvertToInt() || p.path("queueNumber").intValue() < 1))
                throw new IllegalArgumentException();
            if (status.equals("CONFIRMED") && p.path("queueNumber").isNull()) throw new IllegalArgumentException();
            if (status.equals("AWAITING_PAYMENT") && !p.path("queueNumber").isNull()) throw new IllegalArgumentException();
            text(p, "priceCode");
            if (status.equals("CANCELLED")) {
                var c = p.path("cancellation"); exactFields(c, Set.of("actorAccountId", "actorStaffId", "reason", "cancelledAt"));
                uuid(c, "actorAccountId"); if (!c.path("actorStaffId").isNull()) uuid(c, "actorStaffId");
                text(c, "reason"); Instant.parse(text(c, "cancelledAt"));
            } else if (!p.path("cancellation").isNull()) throw new IllegalArgumentException();
            return new RegistrationNoticeCommand(uuid(root, "eventId"), hash(raw), hash(mapper.writeValueAsBytes(canonical(p))),
                    text(root, "correlationId"), registration, revision.longValue(), uuid(p, "patientId"), status);
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid outpatient registration fact", invalid); }
    }

    private static Object canonical(JsonNode node) {
        if (node.isObject()) {
            var result = new TreeMap<String, Object>(); node.fields().forEachRemaining(e -> result.put(e.getKey(), canonical(e.getValue()))); return result;
        }
        if (node.isArray()) { var result = new java.util.ArrayList<Object>(); node.forEach(n -> result.add(canonical(n))); return result; }
        return node;
    }
    private static String hash(byte[] raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
    private static void exactFields(JsonNode node, Set<String> fields) {
        if (!node.isObject() || node.size() != fields.size()) throw new IllegalArgumentException();
        node.fieldNames().forEachRemaining(f -> { if (!fields.contains(f)) throw new IllegalArgumentException(); });
    }
    private static String text(JsonNode node, String field) {
        var value = node.path(field); if (!value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(); return value.textValue();
    }
    private static UUID uuid(JsonNode node, String field) { return UUID.fromString(text(node, field)); }
}
