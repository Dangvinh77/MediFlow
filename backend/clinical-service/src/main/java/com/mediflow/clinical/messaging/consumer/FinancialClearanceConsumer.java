package com.mediflow.clinical.messaging.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.mediflow.clinical.application.dto.command.FinancialClearanceCommand;
import com.mediflow.clinical.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClearancePurpose;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearanceEvent;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearancePayload;

@Component
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class FinancialClearanceConsumer {
    private final ReactToFinancialClearanceUseCase useCase;

    public FinancialClearanceConsumer(ReactToFinancialClearanceUseCase useCase) {
        this.useCase = useCase;
    }

    public void consume(FinancialClearanceEvent event) {
        if (event == null || event.eventId() == null || !"financial.clearance.granted".equals(event.eventType())
                || event.version() != 1 || event.occurredAt() == null
                || !StringUtils.hasText(event.correlationId()) || !"billing-service".equals(event.producer())
                || event.payload() == null) {
            throw new IllegalArgumentException("Invalid financial.clearance.granted v1 envelope");
        }
        FinancialClearancePayload payload = event.payload();
        ClearancePurpose purpose = enumValue(ClearancePurpose.class, payload.purpose());
        CareEpisodeType careEpisodeType = enumValue(CareEpisodeType.class, payload.careEpisodeType());
        if (!hasValidSharedPayload(payload, purpose, careEpisodeType)) {
            throw new IllegalArgumentException("Invalid financial.clearance.granted v1 payload");
        }
        if (purpose != ClearancePurpose.EXAM) {
            return;
        }

        useCase.onFinancialClearance(new FinancialClearanceCommand(
                    event.eventId(), event.eventType(), event.version(), event.occurredAt(),
                    event.correlationId(), event.producer(), payload.clearanceId(), payload.invoiceId(),
                    payload.accountId(), payload.patientId(),
                    careEpisodeType, payload.careEpisodeId(), purpose,
                    payload.appointmentId(), payload.recordId(), payload.labTestIds(), payload.prescriptionId(),
                    payload.admissionId(), payload.surgeryCaseId(), payload.amount(), payload.currency(),
                    payload.paymentMethod(), payload.expiresAt(), payload.emergencyOverride()));
    }

    private static boolean hasValidSharedPayload(FinancialClearancePayload payload, ClearancePurpose purpose,
                                                 CareEpisodeType careEpisodeType) {
        return purpose != null && careEpisodeType != null && payload.clearanceId() != null
                && payload.invoiceId() != null && payload.accountId() != null && payload.patientId() != null
                && payload.careEpisodeId() != null && payload.amount() != null && payload.amount().signum() >= 0
                && payload.currency() != null && payload.currency().matches("[A-Z]{3}");
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Enum.valueOf(type, value);
    }
}
