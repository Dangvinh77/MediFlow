package com.mediflow.surgery.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase.Command;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityChangeWirePort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort.IncomingEvent;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange.ReferenceKind;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SurgeryAuthorityChangeDecoder implements SurgeryAuthorityChangeWirePort {
    private final ObjectMapper mapper;
    public SurgeryAuthorityChangeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public Command decode(String routingKey, byte[] body, Instant receivedAt) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576 || receivedAt == null
                    || !ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE.equals(routingKey)) throw new IllegalArgumentException();
            var root = mapper.readTree(body);
            if (root == null || !root.isObject() || !routingKey.equals(text(root, "eventType"))
                    || !"organization-service".equals(text(root, "producer")) || !root.path("version").isIntegralNumber()
                    || !root.path("version").canConvertToInt() || root.path("version").intValue() != 1) throw new IllegalArgumentException();
            UUID eventId = uuid(root, "eventId"), correlation = uuid(root, "correlationId");
            Instant occurredAt = Instant.parse(text(root, "occurredAt"));
            if (occurredAt.isAfter(receivedAt.plusSeconds(5))) throw new IllegalArgumentException();
            var payload = root.path("payload");
            if (!payload.isObject() || !payload.has("teamRole") || !payload.path("revision").isIntegralNumber()
                    || !payload.path("revision").canConvertToLong() || payload.path("revision").longValue() < 1) throw new IllegalArgumentException();
            var kind = ReferenceKind.valueOf(text(payload, "referenceKind"));
            UUID reference = uuid(payload, "referenceId"), actor = uuid(payload, "actorAccountId");
            SurgeryTeamRole role = payload.path("teamRole").isNull() ? null : SurgeryTeamRole.valueOf(text(payload, "teamRole"));
            String reason = text(payload, "reason");
            var semantic = Map.of("referenceKind", kind.name(), "referenceId", reference.toString(),
                    "teamRole", role == null ? "" : role.name(), "revision", payload.path("revision").longValue(),
                    "actorAccountId", actor.toString(), "reason", reason, "occurredAt", occurredAt.toString());
            var change = new SurgeryAuthorityChange(kind, reference, role, payload.path("revision").longValue(), occurredAt, actor, reason, hash(semantic));
            return new Command(new IncomingEvent(eventId, routingKey, 1, "organization-service", hash(mapper.convertValue(root, Map.class)),
                    routingKey + ":" + eventId, body, receivedAt), change, correlation.toString());
        } catch (Exception invalid) {
            // Do not leak actor reason/raw JSON to broker error logs.
            throw new IllegalArgumentException("Invalid Organization Surgery authority event");
        }
    }
    private String hash(Object value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }
    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException();
        return UUID.fromString(value);
    }
}
