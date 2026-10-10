package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event tiêu thụ: xuất viện được duyệt y khoa (routing key {@code discharge.medically.approved}, do
 * inpatient-service phát). CONTRACT-CARE-BILLING-01 "Deposit, top-up and settlement": khóa nhận phí
 * mới và bắt đầu đối chiếu cuối cùng; KHÔNG đóng admission — Inpatient chỉ đóng sau settlement hoặc
 * một ngoại lệ nợ/miễn đã duyệt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DischargeMedicallyApprovedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public UUID admissionId() { return payload.admissionId(); }
    public UUID patientId() { return payload.patientId(); }
    public UUID summaryId() { return payload.summaryId(); }
    public UUID approvedBy() { return payload.approvedBy(); }
    public Instant approvedAt() { return payload.approvedAt(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID admissionId,
            UUID patientId,
            UUID summaryId,
            UUID approvedBy,
            Instant approvedAt
    ) {}
}
