package com.mediflow.report.infrastructure.messaging.carefinance;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.messaging.consumer.ReportEventValidationException;

/**
 * Offline V2 decoder harness. It is deliberately not attached to Rabbit routing; only contracts
 * with a source ID and producer fixed by current owner specs are accepted here. Surgery events
 * remain unsupported until D07/D11 lock their canonical projection source identity.
 */
@Component
public class CareFinanceEnvelopeDecoder {

    private static final Map<String, SourceContract> SOURCE_CONTRACTS = Map.ofEntries(
            Map.entry("payment.completed", new SourceContract("billing-service", "transactionId")),
            Map.entry("payment.refunded",
                    new SourceContract("billing-service", "refundTransactionId")),
            Map.entry("settlement.completed",
                    new SourceContract("billing-service", "settlementId")),
            Map.entry("medicalrecord.completed",
                    new SourceContract("clinical-service", "recordId")),
            Map.entry("admission.started", new SourceContract("inpatient-service", "admissionId")),
            Map.entry("admission.closed", new SourceContract("inpatient-service", "admissionId")),
            Map.entry("lab.result.created", new SourceContract("lab-service", "labId")),
            Map.entry("prescription.filled", new SourceContract("pharmacy-service", "dispenseId")));

    private static final TypeReference<LinkedHashMap<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public CareFinanceEnvelopeDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecodedCareFinanceEvent decode(String routingKey, byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw invalid("Care-finance envelope must be a JSON object");
            }

            String eventType = requiredText(root, "eventType");
            if (routingKey == null || !routingKey.equals(eventType)) {
                throw invalid("eventType must match the received routing key");
            }
            SourceContract sourceContract = SOURCE_CONTRACTS.get(eventType);
            if (sourceContract == null) {
                throw invalid("Unsupported Care-finance event type: " + eventType);
            }

            JsonNode versionNode = root.get("version");
            if (versionNode == null || !versionNode.isIntegralNumber()
                    || !versionNode.canConvertToInt() || versionNode.intValue() != 1) {
                throw invalid("Only Care-finance envelope version 1 is supported");
            }

            String producer = requiredText(root, "producer");
            if (!sourceContract.producer().equals(producer)) {
                throw invalid("Producer does not own event type " + eventType);
            }

            UUID eventId = requiredUuid(root, "eventId");
            Instant occurredAt = requiredInstant(root, "occurredAt");
            String correlationId = requiredText(root, "correlationId");
            JsonNode payloadNode = root.get("payload");
            if (payloadNode == null || !payloadNode.isObject()) {
                throw invalid("payload must be a JSON object");
            }
            UUID sourceId = requiredUuid(payloadNode, sourceContract.sourceField());

            Map<String, Object> payload = objectMapper.convertValue(payloadNode, PAYLOAD_TYPE);
            CareFinanceEventMetadata metadata = new CareFinanceEventMetadata(
                    eventId, eventType, 1, occurredAt, correlationId, producer,
                    sourceContract.sourceField(), sourceId);
            return new DecodedCareFinanceEvent(metadata, payload);
        } catch (ReportEventValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ReportEventValidationException("Care-finance envelope is invalid", exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " is required");
        }
        return value.textValue();
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        try {
            return UUID.fromString(requiredText(node, field));
        } catch (IllegalArgumentException exception) {
            throw new ReportEventValidationException(field + " must be a UUID", exception);
        }
    }

    private static Instant requiredInstant(JsonNode node, String field) {
        try {
            return Instant.parse(requiredText(node, field));
        } catch (RuntimeException exception) {
            throw new ReportEventValidationException(field + " must be an ISO-8601 instant", exception);
        }
    }

    private static ReportEventValidationException invalid(String message) {
        return new ReportEventValidationException(message);
    }

    private record SourceContract(String producer, String sourceField) {
    }
}
