package com.mediflow.lab.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

/**
 * Published when results complete a lab test (routing key {@code lab.result.created}).
 * {@code labId} is the canonical event identifier and is sourced from the API's {@code testId}.
 * The extra {@code labType} and {@code performedDate} fields are required by billing, notification
 * and reporting consumers.
 */
public record LabResultCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        String labType,
        LocalDate performedDate,
        List<Result> results,
        String conclusion
) {

    public LabResultCreatedEvent {
        if (results == null || results.isEmpty()) {
            throw new LabRuleException("LAB_RESULT_EMPTY",
                    "Không thể phát event kết quả khi danh sách rỗng");
        }
        results = List.copyOf(results);
    }

    /** Wire-safe result payload; a result value is always serialized as a string. */
    public record Result(
            UUID resultId,
            String indicator,
            String value,
            String unit,
            String referenceRange
    ) {

        public static Result from(LabResult result) {
            Objects.requireNonNull(result, "result không được null");
            return new Result(result.getResultId(), result.getIndicator(), result.getValue(),
                    result.getUnit(), result.getReferenceRange());
        }
    }

    /** Builds a result event from a completed aggregate with a non-empty result set. */
    public static LabResultCreatedEvent from(LabTest test, String correlationId) {
        Objects.requireNonNull(test, "test không được null");
        if (test.getTestId() == null) {
            throw new LabRuleException("LAB_ID_REQUIRED", "Xét nghiệm phải được lưu trước khi phát event");
        }
        if (test.getStatus() != LabTestStatus.COMPLETED) {
            throw new LabRuleException("LAB_RESULT_NOT_COMPLETED",
                    "Chỉ được phát event kết quả cho xét nghiệm đã hoàn tất");
        }
        if (test.getResults().isEmpty()) {
            throw new LabRuleException("LAB_RESULT_EMPTY",
                    "Không thể phát event kết quả khi danh sách rỗng");
        }
        if (test.getPerformedDate() == null) {
            throw new LabRuleException("LAB_PERFORMED_DATE_REQUIRED",
                    "Không thể phát event kết quả khi chưa có ngày thực hiện");
        }
        if (test.getRequestedDate() != null && test.getPerformedDate().isBefore(test.getRequestedDate())) {
            throw new LabRuleException("LAB_DATE_BEFORE_REQUEST",
                    "Ngày thực hiện không được trước ngày yêu cầu");
        }
        return new LabResultCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                test.getTestId(),
                test.getPatientId(),
                test.getRecordId(),
                test.getRequestingDepartmentId(),
                test.getLabType(),
                test.getPerformedDate(),
                test.getResults().stream().map(Result::from).toList(),
                test.getConclusion());
    }
}
