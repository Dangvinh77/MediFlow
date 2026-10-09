package com.mediflow.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.notification.application.dto.command.SurgeryPaymentNoticeCommand;
import com.mediflow.notification.application.port.out.SurgeryPaymentNoticeWirePort;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SurgeryPaymentNoticeDecoder implements SurgeryPaymentNoticeWirePort {
    private final ObjectMapper mapper;
    public SurgeryPaymentNoticeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public SurgeryPaymentNoticeCommand decode(String key, byte[] body) {
        try {
            if (!"invoice.created".equals(key) || body == null || body.length == 0 || body.length > 1_048_576) throw new IllegalArgumentException();
            var root = mapper.readTree(body); var p = root.path("payload");
            if (!root.isObject() || !p.isObject() || !key.equals(text(root, "eventType")) || !"billing-service".equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt() || root.path("version").intValue() != 1
                    || !"SURGERY".equals(text(p, "purpose")) || !p.path("totalAmount").isNumber()) throw new IllegalArgumentException();
            UUID request = uuid(p, "paymentRequestId"), invoice = uuid(p, "invoiceId"); uuid(p, "accountId"); uuid(p, "departmentId"); uuid(p, "surgeryCaseId");
            if (invoice.equals(request)) throw new IllegalArgumentException();
            UUID episode = uuid(p, "careEpisodeId"), admission = p.hasNonNull("admissionId") ? uuid(p, "admissionId") : null;
            String type = text(p, "careEpisodeType");
            if (!("ADMISSION".equals(type) ? episode.equals(admission) : "OUTPATIENT_VISIT".equals(type) && admission == null)) throw new IllegalArgumentException();
            Instant created = Instant.parse(text(p, "createdAt"));
            if (!Instant.parse(text(root, "occurredAt")).equals(created)
                    || p.hasNonNull("expiresAt") && !Instant.parse(text(p, "expiresAt")).isAfter(created)) throw new IllegalArgumentException();
            return new SurgeryPaymentNoticeCommand(uuid(root, "eventId"), hash(root), hash(p), text(root, "correlationId"), request,
                    uuid(p, "patientId"), p.path("totalAmount").decimalValue(), text(p, "currency"));
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid Surgery payment-request notice contract"); }
    }
    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(); return value.textValue();
    }
    private static UUID uuid(JsonNode node, String field) {
        String raw = text(node, field); var value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw new IllegalArgumentException(); return value;
    }
}
