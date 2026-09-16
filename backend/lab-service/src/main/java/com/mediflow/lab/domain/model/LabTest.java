package com.mediflow.lab.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.lab.domain.exception.LabRuleException;

/** Aggregate root for a lab test and its results. */
public final class LabTest {

    private final UUID testId;
    private final UUID recordId;
    private final UUID patientId;
    private final UUID requestingDepartmentId;
    private final String labType;
    private final LocalDate requestedDate;
    private LocalDate performedDate;
    private LabTestStatus status;
    private String conclusion;
    private boolean paid;
    private List<LabResult> results;
    private final Instant createdAt;
    private Instant updatedAt;

    private LabTest(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                    String labType, LocalDate requestedDate, LocalDate performedDate,
                    LabTestStatus status, String conclusion, boolean paid, List<LabResult> results,
                    Instant createdAt, Instant updatedAt) {
        this.testId = testId;
        this.recordId = recordId;
        this.patientId = patientId;
        this.requestingDepartmentId = requestingDepartmentId;
        this.labType = labType;
        this.requestedDate = requestedDate;
        this.performedDate = performedDate;
        this.status = status;
        this.conclusion = conclusion;
        this.paid = paid;
        this.results = immutableResults(results);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Creates a pending test request with an identity and creation timestamps. */
    public static LabTest create(UUID recordId, UUID patientId, UUID requestingDepartmentId,
                                 String labType, LocalDate requestedDate) {
        require(recordId, "LAB_RECORD_REQUIRED", "Hồ sơ xét nghiệm là bắt buộc");
        require(patientId, "LAB_PATIENT_REQUIRED", "Bệnh nhân xét nghiệm là bắt buộc");
        require(requestingDepartmentId, "LAB_DEPARTMENT_REQUIRED", "Khoa chỉ định xét nghiệm là bắt buộc");
        if (labType == null || labType.isBlank()) {
            throw new LabRuleException("LAB_TYPE_REQUIRED", "Loại xét nghiệm không được để trống");
        }
        require(requestedDate, "LAB_REQUEST_DATE_REQUIRED", "Ngày yêu cầu xét nghiệm là bắt buộc");
        Instant now = Instant.now();
        return new LabTest(UUID.randomUUID(), recordId, patientId, requestingDepartmentId, labType,
                requestedDate, null, LabTestStatus.PENDING, null, false, List.of(), now, now);
    }

    /** Rehydrates an aggregate from persistence without re-running creation invariants. */
    public static LabTest restore(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                                  String labType, LocalDate requestedDate, LocalDate performedDate,
                                  LabTestStatus status, String conclusion, boolean paid,
                                  List<LabResult> results, Instant createdAt, Instant updatedAt) {
        return new LabTest(testId, recordId, patientId, requestingDepartmentId, labType, requestedDate,
                performedDate, status, conclusion, paid, results, createdAt, updatedAt);
    }

    /**
     * Stores a non-empty result set and completes a pending or in-progress test atomically.
     * A finished test cannot be amended.
     */
    public void recordResults(List<LabResult> results, String conclusion, LocalDate performedDate) {
        if (isFinal()) {
            throw new LabRuleException("LAB_ALREADY_FINISHED", "Xét nghiệm đã kết thúc");
        }
        if (results == null || results.isEmpty() || results.stream().anyMatch(Objects::isNull)) {
            throw new LabRuleException("LAB_RESULT_EMPTY", "Kết quả xét nghiệm không được để trống");
        }
        if (performedDate == null) {
            throw new LabRuleException("LAB_PERFORMED_DATE_REQUIRED", "Ngày thực hiện xét nghiệm là bắt buộc");
        }
        if (requestedDate != null && performedDate.isBefore(requestedDate)) {
            throw new LabRuleException("LAB_DATE_BEFORE_REQUEST",
                    "Ngày thực hiện không được trước ngày yêu cầu");
        }
        this.results = immutableResults(results);
        this.conclusion = conclusion;
        this.performedDate = performedDate;
        this.status = LabTestStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /** Changes lifecycle only along the allowed state machine. Completion requires results. */
    public void changeStatus(LabTestStatus next) {
        if (next == null || status == null || !isValidTransition(status, next)) {
            throw new LabRuleException("LAB_INVALID_TRANSITION",
                    "Không thể chuyển trạng thái xét nghiệm từ " + status + " sang " + next);
        }
        if (next == LabTestStatus.COMPLETED && results.isEmpty()) {
            throw new LabRuleException("LAB_RESULT_EMPTY",
                    "Không thể hoàn tất xét nghiệm khi chưa có kết quả");
        }
        if (next == LabTestStatus.COMPLETED && performedDate == null) {
            throw new LabRuleException("LAB_PERFORMED_DATE_REQUIRED",
                    "Không thể hoàn tất xét nghiệm khi chưa có ngày thực hiện");
        }
        if (next == LabTestStatus.COMPLETED && requestedDate != null
                && performedDate.isBefore(requestedDate)) {
            throw new LabRuleException("LAB_DATE_BEFORE_REQUEST",
                    "Ngày thực hiện không được trước ngày yêu cầu");
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    /** Marks payment received. Repeating the event is harmless. */
    public void markPaid() {
        this.paid = true;
        this.updatedAt = Instant.now();
    }

    public boolean isFinal() {
        return status == LabTestStatus.COMPLETED || status == LabTestStatus.CANCELLED;
    }

    private static boolean isValidTransition(LabTestStatus from, LabTestStatus to) {
        return switch (from) {
            case PENDING -> to == LabTestStatus.IN_PROGRESS || to == LabTestStatus.CANCELLED;
            case IN_PROGRESS -> to == LabTestStatus.COMPLETED || to == LabTestStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private static List<LabResult> immutableResults(List<LabResult> results) {
        return results == null ? List.of() : List.copyOf(results);
    }

    private static <T> void require(T value, String code, String message) {
        if (value == null) {
            throw new LabRuleException(code, message);
        }
    }

    public UUID getTestId() {
        return testId;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getRequestingDepartmentId() {
        return requestingDepartmentId;
    }

    public String getLabType() {
        return labType;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public LocalDate getPerformedDate() {
        return performedDate;
    }

    public LabTestStatus getStatus() {
        return status;
    }

    public String getConclusion() {
        return conclusion;
    }

    public boolean isPaid() {
        return paid;
    }

    public List<LabResult> getResults() {
        return results;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
