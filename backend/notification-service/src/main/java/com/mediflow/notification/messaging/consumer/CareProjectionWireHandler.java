package com.mediflow.notification.messaging.consumer;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.dto.command.AdmissionClosedCommand;
import com.mediflow.notification.application.dto.command.AdmissionDepositRequestedCommand;
import com.mediflow.notification.application.dto.command.AdmissionStartedCommand;
import com.mediflow.notification.application.dto.command.SurgeryCancelledNoticeCommand;
import com.mediflow.notification.application.dto.command.SurgeryReadyCommand;
import com.mediflow.notification.application.port.in.ReactToCareProjectionUseCase;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Nhận các projection event ngoài payment receipt (CONTRACT-CARE-PROJECTIONS-01 "Notification
 * subscriptions"): {@code admission.deposit.requested}, {@code admission.started},
 * {@code admission.closed} (inpatient-service), {@code surgery.ready}, {@code surgery.cancelled}
 * (surgery-service). {@code NotificationEventConsumer} định tuyến tới đây theo routing key —
 * không có xung đột với routing key V1 phẳng nào (khác {@code payment.completed}, nên không cần
 * "đoán hình dạng" như {@link CarePaymentReceiptWireHandler}).
 *
 * <p>Chưa đụng tới {@code deposit.topup.required}/{@code settlement.completed}: Billing chưa có
 * producer fixture thật cho hai event đó (CONTRACT-CARE-BILLING-01 "Still open") — không tự bịa
 * hình dạng wire (AGENTS.md "Do not infer... producer payloads").
 */
@Component
public class CareProjectionWireHandler {
    private final ObjectMapper mapper;
    private final ReactToCareProjectionUseCase projections;
    private final boolean enabled;

    public CareProjectionWireHandler(ObjectMapper mapper, ReactToCareProjectionUseCase projections,
            @Value("${mediflow.notification.care-v1.enabled:false}") boolean enabled) {
        this.mapper = mapper;
        this.projections = projections;
        this.enabled = enabled;
    }

    public void receive(String routingKey, byte[] body) {
        if (!enabled) throw new AmqpRejectAndDontRequeueException("Care V1 notification intake is disabled");
        try {
            JsonNode root = parseEnvelope(routingKey, body);
            JsonNode payload = root.path("payload");
            if (!payload.isObject()) throw new IllegalArgumentException("payload is required");
            String fingerprint = sha256Hex(body);
            UUID eventId = uuid(root, "eventId");
            String correlationId = text(root, "correlationId");
            switch (routingKey) {
                case "admission.deposit.requested" -> {
                    requireProducer(root, "inpatient-service");
                    projections.onAdmissionDepositRequested(new AdmissionDepositRequestedCommand(eventId, fingerprint,
                            correlationId, uuid(payload, "admissionId"), uuid(payload, "patientId"),
                            amount(payload, "suggestedAmount"), optionalText(payload, "reason")));
                }
                case "admission.started" -> {
                    requireProducer(root, "inpatient-service");
                    projections.onAdmissionStarted(new AdmissionStartedCommand(eventId, fingerprint, correlationId,
                            uuid(payload, "admissionId"), uuid(payload, "patientId"), instant(payload, "admittedAt")));
                }
                case "admission.closed" -> {
                    requireProducer(root, "inpatient-service");
                    projections.onAdmissionClosed(new AdmissionClosedCommand(eventId, fingerprint, correlationId,
                            uuid(payload, "admissionId"), uuid(payload, "patientId"), instant(payload, "closedAt")));
                }
                case "surgery.ready" -> {
                    requireProducer(root, "surgery-service");
                    projections.onSurgeryReady(new SurgeryReadyCommand(eventId, fingerprint, correlationId,
                            uuid(payload, "surgeryCaseId"), uuid(payload, "patientId"), instant(payload, "plannedStartAt")));
                }
                case "surgery.cancelled" -> {
                    requireProducer(root, "surgery-service");
                    projections.onSurgeryCancelled(new SurgeryCancelledNoticeCommand(eventId, fingerprint, correlationId,
                            uuid(payload, "surgeryCaseId"), uuid(payload, "patientId"),
                            text(payload, "cancellationStage"), optionalText(payload, "reason"), instant(payload, "cancelledAt")));
                }
                default -> throw new IllegalArgumentException("Unsupported care projection routing key: " + routingKey);
            }
        } catch (NotificationEventConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Conflicting care projection event", conflict);
        } catch (org.springframework.dao.TransientDataAccessException outage) {
            throw outage;
        } catch (Exception malformed) {
            throw new AmqpRejectAndDontRequeueException("Invalid care projection event", malformed);
        }
    }

    private JsonNode parseEnvelope(String routingKey, byte[] body) throws Exception {
        if (body == null || body.length == 0 || body.length > 1_048_576)
            throw new IllegalArgumentException("Care projection body size is invalid");
        JsonNode root = mapper.readTree(body);
        if (!routingKey.equals(text(root, "eventType"))) throw new IllegalArgumentException("eventType/routingKey mismatch");
        if (!root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                || root.path("version").intValue() != 1) throw new IllegalArgumentException("Unsupported envelope version");
        Instant.parse(text(root, "occurredAt"));
        return root;
    }

    private static void requireProducer(JsonNode root, String expected) {
        if (!expected.equals(text(root, "producer"))) throw new IllegalArgumentException("Unexpected producer: " + root.path("producer"));
    }

    private static String text(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IllegalArgumentException(field + " is required");
        return value.textValue();
    }

    private static String optionalText(JsonNode root, String field) {
        var value = root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(JsonNode root, String field) { return UUID.fromString(text(root, field)); }

    private static Instant instant(JsonNode root, String field) { return Instant.parse(text(root, field)); }

    private static BigDecimal amount(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(field + " must be numeric");
        BigDecimal money = value.decimalValue();
        if (money.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
        return money;
    }

    private static String sha256Hex(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }
}
