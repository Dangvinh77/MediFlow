package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event tiêu thụ: Surgery vừa tạo ca mổ, phát sinh charge <b>dự kiến</b> (routing key
 * {@code surgery.case.created}, do surgery-service phát — CONTRACT-SURGERY-BILLING-01, "Post-case
 * identity bridge"). Đây KHÔNG phải {@code surgery.requested} (giấy giới thiệu trước khi có case);
 * case đã tồn tại, Billing tự tra giá theo {@code priceCode} và tạo charge, Surgery không gửi giá.
 *
 * <p>Khác các event V1 khác trong package này: JSON là <b>envelope lồng</b> (nested) — field
 * nghiệp vụ nằm trong {@code payload}, không phẳng ở gốc (xem
 * {@code surgery-outcomes-v1/surgery.case.created.*.v1.json}, bản sao tại
 * {@code src/test/resources/contracts/surgery-outcomes-v1}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgeryCaseCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public UUID surgeryCaseId() { return payload.surgeryCaseId(); }
    public UUID patientId() { return payload.patientId(); }
    public UUID departmentId() { return payload.departmentId(); }
    public String careEpisodeType() { return payload.careEpisodeType(); }
    public UUID careEpisodeId() { return payload.careEpisodeId(); }
    public List<PlannedItem> plannedItems() { return payload.plannedItems(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID surgeryCaseId,
            UUID surgeryRequestId,
            UUID patientId,
            UUID departmentId,
            String careEpisodeType,
            UUID careEpisodeId,
            UUID admissionId,
            UUID recordId,
            List<PlannedItem> plannedItems,
            Instant requestedAt
    ) {}

    public record PlannedItem(String itemCode, String priceCode, BigDecimal quantity) {}
}
