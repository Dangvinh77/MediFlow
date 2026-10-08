package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event tiêu thụ: ca mổ đã hoàn thành, đối chiếu charge <b>thực tế</b> (routing key
 * {@code surgery.completed}, do surgery-service phát — CONTRACT-SURGERY-BILLING-01, "Performed
 * items and completion"). {@code resultId} bất biến là khóa đối chiếu, độc lập với {@code version}
 * của envelope — xem {@link com.mediflow.billing.domain.model.Charge#reconcilePerformed}.
 *
 * <p>JSON là envelope lồng (nested) giống {@link SurgeryCaseCreatedEvent} — xem
 * {@code surgery-outcomes-v1/surgery.completed.*.v1.json}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgeryCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public UUID surgeryCaseId() { return payload.surgeryCaseId(); }
    public UUID patientId() { return payload.patientId(); }
    public UUID departmentId() { return payload.departmentId(); }
    public UUID resultId() { return payload.resultId(); }
    public List<PerformedItem> performedItems() { return payload.performedItems(); }
    public Instant recordedAt() { return payload.recordedAt(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID surgeryCaseId,
            UUID patientId,
            UUID departmentId,
            UUID admissionId,
            UUID recordId,
            UUID resultId,
            List<PerformedItem> performedItems,
            Instant startedAt,
            Instant completedAt,
            Instant recordedAt
    ) {}

    public record PerformedItem(UUID performedItemId, String itemCode, String priceCode, BigDecimal quantity) {}
}
