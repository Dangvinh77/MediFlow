package com.mediflow.notification.messaging.consumer;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.mediflow.notification.application.dto.command.CarePaymentReceiptCommand;
import com.mediflow.notification.application.port.in.ReactToCarePaymentReceiptUseCase;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One existing queue reader dispatches here; never start a competing listener on notification.q. */
@Component
public class CarePaymentReceiptWireHandler {
    private final ObjectMapper mapper;
    private final ReactToCarePaymentReceiptUseCase receipts;
    private final boolean enabled;
    public CarePaymentReceiptWireHandler(ObjectMapper mapper, ReactToCarePaymentReceiptUseCase receipts,
            @Value("${mediflow.notification.care-v1.enabled:false}") boolean enabled) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.receipts = receipts; this.enabled = enabled;
    }
    public void receive(String routingKey, byte[] body) {
        if (!enabled) throw new AmqpRejectAndDontRequeueException("Care V1 notification intake is disabled");
        CarePaymentReceiptCommand command;
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576)
                throw new IllegalArgumentException("Receipt body size is invalid");
            JsonNode root = mapper.readTree(body);
            if (!"payment.completed".equals(routingKey) || !routingKey.equals(text(root, "eventType"))
                    || !"billing-service".equals(text(root, "producer")) || !root.path("version").isIntegralNumber()
                    || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1) throw new IllegalArgumentException("Unsupported receipt envelope");
            Instant.parse(text(root, "occurredAt"));
            var payload = root.path("payload");
            if (!payload.isObject()) throw new IllegalArgumentException("Receipt payload is required");
            uuid(payload, "invoiceId"); uuid(payload, "paymentRequestId"); uuid(payload, "accountId"); uuid(payload, "departmentId");
            uuid(payload, "careEpisodeId");
            String episode = text(payload, "careEpisodeType");
            String classification = text(payload, "classification");
            if (!java.util.Set.of("OUTPATIENT_VISIT", "ADMISSION").contains(episode)
                    || !"SERVICE_PAYMENT".equals(classification) && !"ADMISSION".equals(episode))
                throw new IllegalArgumentException("Receipt classification/episode mismatch");
            Instant.parse(text(payload, "completedAt"));
            if (!java.util.Set.of("CASH", "TRANSFER").contains(text(payload, "paymentMethod")))
                throw new IllegalArgumentException("Only completed cash receipts are supported");
            var amount = payload.get("totalAmount");
            if (amount == null || !amount.isNumber()) throw new IllegalArgumentException("Receipt amount must be numeric");
            BigDecimal money = amount.decimalValue();
            if (money.stripTrailingZeros().scale() > 2 || money.precision() - money.scale() > 17)
                throw new IllegalArgumentException("Invalid receipt money precision");
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(mapper.convertValue(root, Map.class))));
            command = new CarePaymentReceiptCommand(uuid(root, "eventId"), fingerprint, text(root, "correlationId"),
                    uuid(payload, "transactionId"), uuid(payload, "patientId"), money, text(payload, "currency"), classification);
        } catch (Exception malformed) {
            throw new AmqpRejectAndDontRequeueException("Invalid care payment receipt", malformed);
        }
        // Database/outage exceptions are deliberately outside the malformed-payload catch: retry them.
        try { receipts.receive(command); }
        catch (com.mediflow.notification.domain.exception.NotificationEventConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Conflicting care payment receipt", conflict);
        }
    }
    private static String text(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.textValue();
    }
    private static UUID uuid(JsonNode root, String field) { return UUID.fromString(text(root, field)); }
}
