package com.mediflow.inpatient.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.dto.command.DepositTopupRequiredCommand;
import com.mediflow.inpatient.application.dto.command.FinancialClearanceCommand;
import com.mediflow.inpatient.application.dto.command.LabResultFactCommand;
import com.mediflow.inpatient.application.dto.command.PrescriptionFilledFactCommand;
import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCaseCreatedCommand;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
        if (isLegacyFlatLabResult(envelope) || isLegacyFlatPrescriptionFilled(envelope)) {
            return;
        }

        String eventType = text(envelope, "eventType");
        validateProducer(eventType, text(envelope, "producer"));
        UUID eventId = uuid(envelope, "eventId");
        int version = integer(envelope, "version");
        Instant occurredAt = instant(envelope, "occurredAt");
        String correlationId = text(envelope, "correlationId");
        JsonNode payload = requiredObject(envelope, "payload");

        switch (eventType) {
            case "admission.requested" -> referrals.onAdmissionRequested(new AdmissionRequestedCommand(
                    eventId, version, occurredAt, correlationId,
                    uuid(payload, "admissionRequestId"), uuid(payload, "recordId"),
                    uuid(payload, "patientId"), uuid(payload, "departmentId"),
                    uuid(payload, "requestedBy"), text(payload, "diagnosisSummary"),
                    enumValue(payload, "priority", AdmissionPriority.class),
                    booleanValue(payload, "emergency"), instant(payload, "requestedAt")));
            case "financial.clearance.granted" -> onFinancialClearance(
                    payload, eventId, version, occurredAt, correlationId);
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
            case "lab.result.created" -> onLabResultCreated(
                    payload, eventId, version, occurredAt, correlationId);
            case "prescription.filled" -> externalOrders.onExternalOrderFact(new PrescriptionFilledFactCommand(
                    eventId, version, correlationId, uuid(payload, "prescriptionId"), uuid(payload, "admissionId"),
                    uuid(payload, "patientId"), instant(payload, "filledAt"), occurredAt));
            case "surgery.case.created", "surgery.ready", "surgery.completed", "surgery.cancelled" ->
                    onSurgeryEvent(eventType, payload, eventId, version, occurredAt, correlationId);
            default -> throw new AmqpException("Unsupported inpatient event type: " + eventType);
        }
    }

    private void onFinancialClearance(JsonNode payload, UUID eventId, int version,
                                      Instant occurredAt, String correlationId) {
        ClearancePurpose purpose = enumValue(payload, "purpose", ClearancePurpose.class);
        if (purpose != ClearancePurpose.ADMISSION_DEPOSIT) {
            return;
        }

        clearances.onFinancialClearance(new FinancialClearanceCommand(
                eventId, version, occurredAt, correlationId,
                uuid(payload, "clearanceId"), uuid(payload, "invoiceId"), uuid(payload, "accountId"),
                uuid(payload, "patientId"), enumValue(payload, "careEpisodeType", CareEpisodeType.class),
                uuid(payload, "careEpisodeId"), purpose, uuid(payload, "admissionId"),
                decimal(payload, "amount"), text(payload, "currency"),
                text(payload, "paymentMethod"), optionalInstant(payload, "expiresAt"),
                booleanValue(payload, "emergencyOverride")));
    }

    private void onLabResultCreated(JsonNode payload, UUID eventId, int version,
                                    Instant occurredAt, String correlationId) {
        JsonNode episodeTypeNode = required(payload, "careEpisodeType");
        if (!episodeTypeNode.isTextual() || episodeTypeNode.asText().isBlank()) {
            throw new AmqpException("lab.result.created careEpisodeType must be a supported string");
        }

        CareEpisodeType episodeType;
        try {
            episodeType = CareEpisodeType.valueOf(episodeTypeNode.asText());
        } catch (IllegalArgumentException exception) {
            throw new AmqpException("lab.result.created has an unsupported careEpisodeType", exception);
        }
        if (episodeType == CareEpisodeType.OUTPATIENT_VISIT) {
            return;
        }

        externalOrders.onExternalOrderFact(new LabResultFactCommand(
                eventId, version, correlationId, uuid(payload, "labId"),
                uuid(payload, "careEpisodeId"), uuid(payload, "patientId"),
                integer(payload, "resultVersion"), text(payload, "conclusion"), occurredAt));
    }

    private void onSurgeryEvent(String eventType, JsonNode payload, UUID eventId, int version,
                                Instant occurredAt, String correlationId) {
        UUID surgeryCaseId = uuid(payload, "surgeryCaseId");
        UUID surgeryRequestId = uuid(payload, "surgeryRequestId");
        UUID patientId = uuid(payload, "patientId");
        UUID departmentId = uuid(payload, "departmentId");
        UUID careEpisodeId = uuid(payload, "careEpisodeId");
        optionalUuid(payload, "recordId");
        int caseRevision = integer(payload, "caseRevision");
        String fingerprint = payloadFingerprint(payload);
        switch (eventType) {
            case "surgery.case.created" -> {
                integer(payload, "sourceRevision");
                text(payload, "procedureCode");
                instant(payload, "requestedAt");
            }
            case "surgery.ready" -> {
                uuid(payload, "scheduleId");
                uuid(payload, "readinessSnapshotId");
                integer(payload, "scheduleRevision");
                instant(payload, "readyAt");
            }
            case "surgery.completed" -> {
                uuid(payload, "resultId");
                integer(payload, "sourceRevision");
                instant(payload, "completedAt");
            }
            case "surgery.cancelled" -> {
                uuid(payload, "cancellationId");
                integer(payload, "sourceRevision");
                text(payload, "cancellationStage");
                text(payload, "reason");
                instant(payload, "cancelledAt");
            }
            default -> throw new AmqpException("Unsupported inpatient surgery event type: " + eventType);
        }
        CareEpisodeType episodeType = enumValue(payload, "careEpisodeType", CareEpisodeType.class);
        if (episodeType == CareEpisodeType.OUTPATIENT_VISIT) {
            JsonNode admissionNode = payload.get("admissionId");
            if (admissionNode != null && !admissionNode.isNull()) {
                throw new AmqpException(eventType + " outpatient episode must not contain admissionId");
            }
            return;
        }

        UUID admissionId = uuid(payload, "admissionId");
        if (!admissionId.equals(careEpisodeId)) {
            throw new AmqpException(eventType + " careEpisodeId must equal admissionId");
        }
        if ("surgery.case.created".equals(eventType)) {
            externalOrders.onSurgeryCaseCreated(new SurgeryCaseCreatedCommand(
                    eventId, version, correlationId, surgeryCaseId,
                    surgeryRequestId, admissionId, patientId, departmentId,
                    caseRevision, integer(payload, "sourceRevision"),
                    text(payload, "procedureCode"), instant(payload, "requestedAt"),
                    fingerprint, occurredAt));
        } else if ("surgery.ready".equals(eventType)) {
            externalOrders.onExternalOrderFact(new SurgeryReadyFactCommand(
                    eventId, version, correlationId, surgeryCaseId, admissionId,
                    uuid(payload, "scheduleId"), uuid(payload, "readinessSnapshotId"),
                    instant(payload, "readyAt"), occurredAt,
                    surgeryRequestId, patientId, departmentId, caseRevision,
                    integer(payload, "scheduleRevision"), fingerprint));
        } else if ("surgery.completed".equals(eventType)) {
            externalOrders.onExternalOrderFact(new SurgeryCompletedFactCommand(
                    eventId, version, correlationId, surgeryCaseId, admissionId,
                    uuid(payload, "resultId"), optionalText(payload, "complicationsSummary"),
                    instant(payload, "completedAt"), occurredAt,
                    surgeryRequestId, patientId, departmentId, caseRevision,
                    integer(payload, "sourceRevision"), fingerprint));
        } else {
            externalOrders.onExternalOrderFact(new SurgeryCancelledFactCommand(
                    eventId, version, correlationId, surgeryCaseId, admissionId,
                    uuid(payload, "cancellationId"), text(payload, "cancellationStage"),
                    text(payload, "reason"), instant(payload, "cancelledAt"), occurredAt,
                    surgeryRequestId, patientId, departmentId, caseRevision,
                    integer(payload, "sourceRevision"), fingerprint));
        }
    }

    private static String payloadFingerprint(JsonNode payload) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(payload, canonical);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void appendCanonical(JsonNode node, StringBuilder target) {
        if (node.isObject()) {
            target.append('{');
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) {
                target.append(name).append(':');
                appendCanonical(node.get(name), target);
                target.append(';');
            }
            target.append('}');
        } else if (node.isArray()) {
            target.append('[');
            node.forEach(value -> {
                appendCanonical(value, target);
                target.append(';');
            });
            target.append(']');
        } else {
            target.append(node.toString());
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

    private static void validateProducer(String eventType, String producer) {
        String expectedProducer = switch (eventType) {
            case "admission.requested" -> "clinical-service";
            case "financial.clearance.granted", "settlement.completed", "deposit.topup.required" -> "billing-service";
            case "lab.result.created" -> "lab-service";
            case "prescription.filled" -> "pharmacy-service";
            case "surgery.case.created", "surgery.ready", "surgery.completed", "surgery.cancelled" ->
                    "surgery-service";
            default -> throw new AmqpException("Unsupported inpatient event type: " + eventType);
        };
        if (!expectedProducer.equals(producer)) {
            throw new AmqpException("Inpatient event producer does not match event type " + eventType
                    + "; expected " + expectedProducer);
        }
    }

    private static boolean isLegacyFlatLabResult(JsonNode event) {
        if (!isUnversionedFlatEvent(event)) {
            return false;
        }
        return isUuid(event, "eventId") && isInstant(event, "occurredAt")
                && isNonBlankText(event, "correlationId") && isUuid(event, "labId")
                && isUuid(event, "patientId") && isUuid(event, "recordId")
                && isLocalDate(event, "performedDate")
                && event.path("results").isArray() && !event.path("results").isEmpty();
    }

    private static boolean isLegacyFlatPrescriptionFilled(JsonNode event) {
        JsonNode totalAmount = event.path("totalAmount");
        JsonNode dispensedItems = event.path("dispensedItems");
        return isUnversionedFlatEvent(event)
                && isUuid(event, "eventId") && isInstant(event, "occurredAt")
                && isNonBlankText(event, "correlationId") && isUuid(event, "prescriptionId")
                && isUuid(event, "recordId") && isUuid(event, "patientId")
                && isUuid(event, "departmentId") && totalAmount.isNumber()
                && dispensedItems.isArray() && !dispensedItems.isEmpty();
    }

    private static boolean isUnversionedFlatEvent(JsonNode event) {
        return event.isObject() && !event.has("eventType") && !event.has("version")
                && !event.has("payload") && !event.has("producer");
    }

    private static boolean isUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return false;
        }
        try {
            UUID.fromString(value.asText());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isInstant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return false;
        }
        try {
            Instant.parse(value.asText());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isLocalDate(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return false;
        }
        try {
            LocalDate.parse(value.asText());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isNonBlankText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            throw new AmqpException("Required inpatient event field is missing: " + field);
        }
        return value;
    }

    private static JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isObject()) {
            throw new AmqpException("Inpatient event field must be an object: " + field);
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

    private static UUID optionalUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : uuid(node, field);
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
