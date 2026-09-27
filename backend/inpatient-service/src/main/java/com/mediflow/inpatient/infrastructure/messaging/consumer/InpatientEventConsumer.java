package com.mediflow.inpatient.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.dto.command.DepositTopupRequiredCommand;
import com.mediflow.inpatient.application.dto.command.FinancialClearanceCommand;
import com.mediflow.inpatient.application.dto.command.LabResultFactCommand;
import com.mediflow.inpatient.application.dto.command.PrescriptionFilledFactCommand;
import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCancelledFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCompletedFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryReadyFactCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.CareEpisodeType;
import com.mediflow.inpatient.domain.model.enums.ClearancePurpose;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import com.mediflow.inpatient.infrastructure.messaging.config.InpatientConsumerConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Converts versioned shared event envelopes into application commands. */
@Component
@ConditionalOnProperty(name = "mediflow.inpatient.messaging.consumers.enabled", havingValue = "true")
public class InpatientEventConsumer {
    private final ObjectMapper objectMapper;
    private final ReactToAdmissionReferralUseCase referrals;
    private final ReactToFinancialClearanceUseCase clearances;
    private final ReactToSettlementUseCase settlements;
    private final ReactToDepositTopupUseCase topups;
    private final ReactToExternalOrderUseCase externalOrders;

    public InpatientEventConsumer(ObjectMapper objectMapper,
                                  ReactToAdmissionReferralUseCase referrals,
                                  ReactToFinancialClearanceUseCase clearances,
                                  ReactToSettlementUseCase settlements,
                                  ReactToDepositTopupUseCase topups,
                                  ReactToExternalOrderUseCase externalOrders) {
        this.objectMapper = objectMapper;
        this.referrals = referrals;
        this.clearances = clearances;
        this.settlements = settlements;
        this.topups = topups;
        this.externalOrders = externalOrders;
    }

    @RabbitListener(queues = InpatientConsumerConfiguration.INPATIENT_QUEUE,
            containerFactory = "inpatientListenerContainerFactory")
    @Transactional
    public void receive(Message message) {
        JsonNode envelope = parse(message.getBody());
        UUID eventId = uuid(envelope, "eventId");
        String eventType = text(envelope, "eventType");
        int version = integer(envelope, "version");
        Instant occurredAt = instant(envelope, "occurredAt");
        String correlationId = text(envelope, "correlationId");
        JsonNode payload = required(envelope, "payload");

        switch (eventType) {
            case "admission.requested" -> referrals.onAdmissionRequested(new AdmissionRequestedCommand(
                    eventId, version, occurredAt, correlationId,
                    uuid(payload, "admissionRequestId"), uuid(payload, "recordId"),
                    uuid(payload, "patientId"), uuid(payload, "departmentId"),
                    uuid(payload, "requestedBy"), text(payload, "diagnosisSummary"),
                    enumValue(payload, "priority", AdmissionPriority.class),
                    booleanValue(payload, "emergency"), instant(payload, "requestedAt")));
            case "financial.clearance.granted" -> clearances.onFinancialClearance(new FinancialClearanceCommand(
                    eventId, version, occurredAt, correlationId,
                    uuid(payload, "clearanceId"), uuid(payload, "invoiceId"), uuid(payload, "accountId"),
                    uuid(payload, "patientId"), enumValue(payload, "careEpisodeType", CareEpisodeType.class),
                    uuid(payload, "careEpisodeId"), enumValue(payload, "purpose", ClearancePurpose.class),
                    uuid(payload, "admissionId"), decimal(payload, "amount"), text(payload, "currency"),
                    text(payload, "paymentMethod"), optionalInstant(payload, "expiresAt"),
                    booleanValue(payload, "emergencyOverride")));
            case "settlement.completed" -> settlements.onSettlementCompleted(new SettlementCompletedCommand(
                    eventId, version, occurredAt, correlationId,
                    uuid(payload, "settlementId"), uuid(payload, "admissionId"), uuid(payload, "accountId"),
                    decimal(payload, "grossAmount"), decimal(payload, "insuranceAmount"),
                    decimal(payload, "patientLiability"), decimal(payload, "completedPayments"),
                    decimal(payload, "completedRefunds"), decimal(payload, "balance"),
                    enumValue(payload, "outcome", SettlementOutcome.class), instant(payload, "completedAt")));
            case "deposit.topup.required" -> topups.onDepositTopupRequired(new DepositTopupRequiredCommand(
                    eventId, version, occurredAt, correlationId, uuid(payload, "accountId"),
                    uuid(payload, "admissionId"), decimal(payload, "currentBalance"),
                    decimal(payload, "requestedAmount"), text(payload, "reason")));
            case "lab.result.created" -> externalOrders.onExternalOrderFact(new LabResultFactCommand(
                    eventId, uuid(payload, "labTestId"), uuid(payload, "admissionId"),
                    uuid(payload, "patientId"), integer(payload, "resultVersion"),
                    text(payload, "conclusion"), occurredAt));
            case "prescription.filled" -> externalOrders.onExternalOrderFact(new PrescriptionFilledFactCommand(
                    eventId, uuid(payload, "prescriptionId"), uuid(payload, "admissionId"),
                    uuid(payload, "patientId"), instant(payload, "filledAt"), occurredAt));
            case "surgery.ready" -> externalOrders.onExternalOrderFact(new SurgeryReadyFactCommand(
                    eventId, uuid(payload, "surgeryCaseId"), uuid(payload, "admissionId"),
                    uuid(payload, "scheduleId"), uuid(payload, "readinessSnapshotId"),
                    instant(payload, "readyAt"), occurredAt));
            case "surgery.completed" -> externalOrders.onExternalOrderFact(new SurgeryCompletedFactCommand(
                    eventId, uuid(payload, "surgeryCaseId"), uuid(payload, "admissionId"),
                    uuid(payload, "resultId"), optionalText(payload, "complicationsSummary"),
                    instant(payload, "completedAt"), occurredAt));
            case "surgery.cancelled" -> externalOrders.onExternalOrderFact(new SurgeryCancelledFactCommand(
                    eventId, uuid(payload, "surgeryCaseId"), uuid(payload, "admissionId"),
                    text(payload, "cancellationStage"), text(payload, "reason"),
                    instant(payload, "cancelledAt"), occurredAt));
            default -> throw new AmqpException("Unsupported inpatient event type: " + eventType);
        }
    }

    private JsonNode parse(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw new AmqpException("Inpatient event envelope must be a JSON object");
            }
            return node;
        } catch (IOException exception) {
            throw new AmqpException("Inpatient event payload is not valid JSON", exception);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            throw new AmqpException("Required inpatient event field is missing: " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new AmqpException("Required inpatient event field is invalid: " + field);
        }
        return value.asText();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new AmqpException("Inpatient event field must be an integer: " + field);
        }
        return value.intValue();
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isBoolean()) {
            throw new AmqpException("Inpatient event field must be a boolean: " + field);
        }
        return value.booleanValue();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : text(node, field);
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException exception) {
            throw new AmqpException("Inpatient event field must be a UUID: " + field, exception);
        }
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (RuntimeException exception) {
            throw new AmqpException("Inpatient event field must be an ISO instant: " + field, exception);
        }
    }

    private static Instant optionalInstant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : instant(node, field);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isNumber()) {
            throw new AmqpException("Inpatient event field must be numeric: " + field);
        }
        return value.decimalValue();
    }

    private static <T extends Enum<T>> T enumValue(JsonNode node, String field, Class<T> type) {
        try {
            return Enum.valueOf(type, text(node, field));
        } catch (IllegalArgumentException exception) {
            throw new AmqpException("Inpatient event field has an unsupported value: " + field, exception);
        }
    }
}
