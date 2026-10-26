package com.mediflow.clinical.messaging.consumer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.event.OutpatientEligibilityFact;
import com.mediflow.clinical.messaging.consumer.payload.OutpatientFinancialEligibilityEvent;

/** Strict v1 wire decoder: unknown fields, duplicate keys, and trailing JSON are rejected. */
@Component
public class OutpatientEligibilityEventDecoder {
    private static final String EVENT_TYPE = "outpatient.financial.eligibility.granted";
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId", "eventType", "version", "occurredAt", "correlationId", "producer", "payload");
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "paymentOperationId", "paymentRequestId", "registrationId", "reservationId", "fenceToken",
            "patientId", "sourceType", "sourceId", "careEpisodeType", "careEpisodeId", "transactionId",
            "clearanceId", "amount", "currency", "completedAt", "basis", "quoteId");

    private final ObjectMapper mapper;

    public OutpatientEligibilityEventDecoder(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public OutpatientFinancialEligibilityEvent decode(byte[] rawBody) throws IOException {
        if (rawBody == null || rawBody.length == 0 || rawBody.length > 1_048_576) {
            throw new IllegalArgumentException("Eligibility event body must be 1 byte through 1 MiB");
        }
        JsonNode root;
        try (JsonParser parser = mapper.getFactory().createParser(rawBody)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            root = mapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("Eligibility event body must contain one JSON object");
            }
        }
        requireExactFields(root, ENVELOPE_FIELDS, "envelope");
        requireExactFields(root.get("payload"), PAYLOAD_FIELDS, "payload");
        OutpatientFinancialEligibilityEvent event = mapper.treeToValue(root, OutpatientFinancialEligibilityEvent.class);
        validate(event);
        return event;
    }

    private static void requireExactFields(JsonNode node, Set<String> expected, String section) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Eligibility event " + section + " must be an object");
        }
        java.util.Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Eligibility event " + section + " fields do not match v1");
        }
    }

    private static void validate(OutpatientFinancialEligibilityEvent event) {
        if (event == null || event.eventId() == null || !EVENT_TYPE.equals(event.eventType())
                || event.version() != 1 || event.occurredAt() == null
                || !StringUtils.hasText(event.correlationId()) || event.correlationId().length() > 120
                || event.correlationId().chars().anyMatch(Character::isISOControl)
                || !"billing-service".equals(event.producer())) {
            throw new IllegalArgumentException("Invalid outpatient eligibility v1 envelope");
        }
        OutpatientEligibilityFact fact = event.payload();
        if (fact == null || fact.paymentOperationId() == null || fact.paymentRequestId() == null
                || fact.registrationId() == null || fact.reservationId() == null || fact.fenceToken() < 1
                || fact.patientId() == null || !"EXAM".equals(fact.sourceType())
                || !fact.registrationId().equals(fact.sourceId())
                || !"OUTPATIENT_VISIT".equals(fact.careEpisodeType())
                || !fact.registrationId().equals(fact.careEpisodeId())
                || fact.clearanceId() == null || fact.completedAt() == null || !"VND".equals(fact.currency())
                || !StringUtils.hasText(fact.amount())) {
            throw new IllegalArgumentException("Invalid outpatient eligibility source binding");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(fact.amount());
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Eligibility amount must be a decimal string", malformed);
        }
        if (amount.scale() > 2 || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Eligibility amount has an invalid decimal representation");
        }
        if ("PATIENT_PAYMENT".equals(fact.basis())) {
            if (amount.signum() <= 0 || fact.transactionId() == null) {
                throw new IllegalArgumentException("Patient payment eligibility requires a positive transaction");
            }
        } else if ("ZERO_LIABILITY".equals(fact.basis())) {
            if (amount.signum() != 0 || fact.transactionId() != null || fact.quoteId() == null) {
                throw new IllegalArgumentException("Zero-liability eligibility requires a zero quote and no transaction");
            }
        } else {
            throw new IllegalArgumentException("Eligibility basis is unsupported");
        }
    }
}
