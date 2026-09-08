package com.mediflow.lab.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.exception.LabRuleException;

/** Published when a lab request is created (routing key {@code lab.request.created}). */
public record LabRequestCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        String labType,
        LocalDate requestedDate
) {

    /** Builds a publishable request event from the persisted aggregate. */
    public static LabRequestCreatedEvent from(LabTest test, String correlationId) {
        Objects.requireNonNull(test, "test không được null");
        if (test.getTestId() == null) {
            throw new LabRuleException("LAB_ID_REQUIRED", "Xét nghiệm phải được lưu trước khi phát event");
        }
        return new LabRequestCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                test.getTestId(),
                test.getPatientId(),
                test.getRecordId(),
                test.getRequestingDepartmentId(),
                test.getLabType(),
                test.getRequestedDate());
    }
}
