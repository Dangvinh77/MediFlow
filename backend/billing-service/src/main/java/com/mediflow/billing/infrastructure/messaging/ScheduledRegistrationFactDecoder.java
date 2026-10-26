package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Strict decoder for the one new source fact; the original envelope bytes go unchanged to the receipt. */
@Component
public final class ScheduledRegistrationFactDecoder implements com.mediflow.billing.application.port.out.ScheduledRegistrationWirePort {
    public static final String ROUTING_KEY = "outpatient.registration.changed";
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId", "eventType", "version", "occurredAt", "correlationId", "producer", "payload");
    private static final Set<String> FACT_FIELDS = Set.of("registrationId", "registrationRevision", "patientId",
            "doctorId", "departmentId", "sessionId", "roomId", "sessionDate", "startTime", "endTime",
            "visitType", "reservationId", "reservationStatus", "status", "checkedInAt", "queueNumber",
            "reservationExpiresAt", "priceCode", "sourceType", "sourceId", "careEpisodeType", "careEpisodeId",
            "cancellation", "changedAt");
    private static final Set<String> CANCELLATION_FIELDS = Set.of("actorAccountId", "actorStaffId", "reason", "cancelledAt");
    private final ObjectMapper mapper;

    public ScheduledRegistrationFactDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    public OutpatientRegistrationChangedEvent decode(String routingKey, byte[] raw) {
        if (!ROUTING_KEY.equals(routingKey) || raw == null || raw.length == 0) throw new IllegalArgumentException("Unsupported registration fact");
        try {
            JsonNode root = mapper.readTree(raw);
            exactFields(root, ENVELOPE_FIELDS);
            JsonNode payload = root.get("payload");
            exactFields(payload, FACT_FIELDS);
            JsonNode cancellation = payload.get("cancellation");
            if (cancellation != null && !cancellation.isNull()) exactFields(cancellation, CANCELLATION_FIELDS);
            return mapper.treeToValue(root, OutpatientRegistrationChangedEvent.class);
        } catch (IOException | RuntimeException malformed) {
            throw new IllegalArgumentException("Malformed outpatient registration event", malformed);
        }
    }

    private static void exactFields(JsonNode object, Set<String> expected) {
        if (object == null || !object.isObject()) throw new IllegalArgumentException("Expected a JSON object");
        Set<String> actual = new java.util.HashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unexpected or missing contract fields");
    }
}
