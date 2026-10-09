package com.mediflow.report.infrastructure.messaging.carefinance;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.messaging.consumer.ReportEventValidationException;
import com.mediflow.report.application.port.out.CareFinanceWirePort;

/**
 * Shared strict V2 decoder. Individual intakes independently gate their approved routing subset;
 * decoding a supported financial envelope does not authorize financial projection/publication.
 */
@Component
public class CareFinanceEnvelopeDecoder implements CareFinanceWirePort {

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
            Map.entry("prescription.filled", new SourceContract("pharmacy-service", "dispenseId")),
            Map.entry("surgery.completed", new SourceContract("surgery-service", "resultId")),
            Map.entry("surgery.cancelled", new SourceContract("surgery-service", "cancellationId")));

    private static final TypeReference<LinkedHashMap<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public CareFinanceEnvelopeDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public DecodedCareFinanceEvent decode(String routingKey, byte[] body) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576) {
                throw invalid("Invalid care-finance envelope size");
            }
            // Financial values must never pass through double. Keep the existing operational
            // representation stable: V11 hashes historical normalized payloads, not raw bytes.
            boolean financial = "payment.completed".equals(routingKey) || "payment.refunded".equals(routingKey)
                    || "settlement.completed".equals(routingKey);
            var treeReader = objectMapper.reader();
            if (financial) {
                treeReader = treeReader.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            }
            JsonNode root = treeReader.readTree(body);
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
            if (correlationId.length() > 120) throw invalid("Correlation identity is too long");
            JsonNode payloadNode = root.get("payload");
            if (payloadNode == null || !payloadNode.isObject()) {
                throw invalid("payload must be a JSON object");
            }
            UUID sourceId = requiredUuid(payloadNode, sourceContract.sourceField());

            Map<String, Object> payload;
            if (financial) {
                payload = objectMapper.readerFor(PAYLOAD_TYPE)
                        .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(payloadNode);
            } else {
                payload = objectMapper.convertValue(payloadNode, PAYLOAD_TYPE);
            }
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
            String text = requiredText(node, field);
            UUID value = UUID.fromString(text);
            if (!value.toString().equals(text)) throw new IllegalArgumentException("Noncanonical UUID");
            return value;
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
