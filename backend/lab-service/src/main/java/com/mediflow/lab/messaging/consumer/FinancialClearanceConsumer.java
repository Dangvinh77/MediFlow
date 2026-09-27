package com.mediflow.lab.messaging.consumer;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;
import com.mediflow.lab.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearanceEnvelope;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearancePayload;

/** Validates Billing's wire event and maps it to the application port. */
@Component
public class FinancialClearanceConsumer {

    private final ReactToFinancialClearanceUseCase useCase;

    public FinancialClearanceConsumer(ReactToFinancialClearanceUseCase useCase) {
        this.useCase = useCase;
    }

    public void consume(FinancialClearanceEnvelope event) {
        FinancialClearancePayload payload = validate(event);
        if (payload.purpose() != ClearancePurpose.LAB_TEST) {
            return;
        }
        validateLabTarget(payload);
        useCase.onFinancialClearance(new FinancialClearanceCommand(
                event.eventId(), event.eventType(), event.version(), event.occurredAt(),
                event.correlationId(), event.producer(), payload.clearanceId(), payload.invoiceId(),
                payload.accountId(), payload.patientId(), payload.careEpisodeType(), payload.careEpisodeId(),
                payload.purpose(), payload.labTestIds(), payload.amount(), payload.currency(),
                payload.expiresAt(), payload.emergencyOverride()));
    }

    private static FinancialClearancePayload validate(FinancialClearanceEnvelope event) {
        if (event == null) {
            throw new IllegalArgumentException("financial.clearance.granted event is required");
        }
        require(event.eventId(), "eventId");
        if (!"financial.clearance.granted".equals(event.eventType())) {
            throw new IllegalArgumentException("financial.clearance.granted eventType is invalid");
        }
        if (event.version() != 1) {
            throw new IllegalArgumentException("financial.clearance.granted version is unsupported");
        }
        require(event.occurredAt(), "occurredAt");
        require(event.correlationId(), "correlationId");
        if (!"billing-service".equals(event.producer())) {
            throw new IllegalArgumentException("financial.clearance.granted producer is invalid");
        }
        FinancialClearancePayload payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("financial.clearance.granted payload is required");
        }
        require(payload.clearanceId(), "payload.clearanceId");
        require(payload.invoiceId(), "payload.invoiceId");
        require(payload.accountId(), "payload.accountId");
        require(payload.patientId(), "payload.patientId");
        require(payload.careEpisodeType(), "payload.careEpisodeType");
        require(payload.careEpisodeId(), "payload.careEpisodeId");
        require(payload.purpose(), "payload.purpose");
        require(payload.amount(), "payload.amount");
        require(payload.currency(), "payload.currency");
        return payload;
    }

    private static void validateLabTarget(FinancialClearancePayload payload) {
        if (payload.labTestIds() == null || payload.labTestIds().isEmpty()
                || payload.labTestIds().stream().anyMatch(Objects::isNull)
                || payload.labTestIds().stream().distinct().count() != payload.labTestIds().size()) {
            throw new IllegalArgumentException("financial.clearance.granted labTestIds must be explicit and unique");
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("financial.clearance.granted " + fieldName + " is required");
        }
    }
}
