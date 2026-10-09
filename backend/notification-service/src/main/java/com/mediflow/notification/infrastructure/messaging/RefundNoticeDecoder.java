package com.mediflow.notification.infrastructure.messaging;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.mediflow.notification.application.dto.command.RefundNoticeCommand;
import com.mediflow.notification.application.port.out.RefundNoticeWirePort;
import org.springframework.stereotype.Component;

@Component
public class RefundNoticeDecoder implements RefundNoticeWirePort {
    private final ObjectMapper mapper;
    public RefundNoticeDecoder(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public RefundNoticeCommand decode(String key, byte[] body) {
        try {
            if (!"payment.refunded".equals(key) || body == null || body.length == 0 || body.length > 1_048_576) throw new IllegalArgumentException();
            var root = mapper.readTree(body); var p = root.path("payload");
            if (!root.isObject() || !p.isObject() || !key.equals(text(root, "eventType")) || !"billing-service".equals(text(root, "producer"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt() || root.path("version").intValue() != 1
                    || !p.path("amount").isNumber() || p.has("sourceRevision") || p.has("supersedesTransactionId")
                    || !"CASHIER_RECORDED_REFUND".equals(text(p, "reason"))) throw new IllegalArgumentException();
            uuid(p, "accountId"); uuid(p, "departmentId"); uuid(p, "careEpisodeId");
            if (!Set.of("ADMISSION", "OUTPATIENT_VISIT").contains(text(p, "careEpisodeType"))
                    || !Instant.parse(text(root, "occurredAt")).equals(Instant.parse(text(p, "completedAt")))) throw new IllegalArgumentException();
            return new RefundNoticeCommand(uuid(root, "eventId"), hash(root), hash(p), text(root, "correlationId"),
                    uuid(p, "refundTransactionId"), uuid(p, "originalTransactionId"), uuid(p, "patientId"),
                    p.path("amount").decimalValue(), text(p, "currency"));
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid Billing completed-refund contract"); }
    }
    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapper.convertValue(node, Map.class))));
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(); return value.textValue();
    }
    private static UUID uuid(JsonNode node, String field) {
        var raw = text(node, field); var value = UUID.fromString(raw);
        if (!raw.equals(value.toString())) throw new IllegalArgumentException(); return value;
    }
}
