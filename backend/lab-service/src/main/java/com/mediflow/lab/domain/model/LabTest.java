package com.mediflow.lab.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.exception.LabResultFinalizedException;

/** Aggregate root for a lab test and its results. */
public final class LabTest {

    private final UUID testId;
    private final UUID recordId;
    private final UUID patientId;
    private final UUID requestingDepartmentId;
    private final String labType;
    private final LocalDate requestedDate;
    private final int careContractVersion;
    private final CareEpisodeType careEpisodeType;
    private final UUID careEpisodeId;
    private final UUID sourceOrderId;
    private final String priceCode;
    private LocalDate performedDate;
    private LabTestStatus status;
    private String conclusion;
    private boolean paid;
    private UUID clearanceId;
    private Instant clearanceGrantedAt;
    private UUID emergencyOverrideId;
    private int resultVersion;
    private List<LabResult> results;
    private final Instant createdAt;
    private Instant updatedAt;

    private LabTest(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                    String labType, LocalDate requestedDate, LocalDate performedDate,
                    LabTestStatus status, String conclusion, boolean paid, List<LabResult> results,
                    Instant createdAt, Instant updatedAt, int careContractVersion,
                    CareEpisodeType careEpisodeType, UUID careEpisodeId, UUID sourceOrderId,
                    String priceCode, UUID clearanceId, Instant clearanceGrantedAt,
                    UUID emergencyOverrideId, int resultVersion) {
        this.testId = testId;
        this.recordId = recordId;
        this.patientId = patientId;
        this.requestingDepartmentId = requestingDepartmentId;
        this.labType = labType;
        this.requestedDate = requestedDate;
        this.careContractVersion = careContractVersion;
        this.careEpisodeType = careEpisodeType;
        this.careEpisodeId = careEpisodeId;
        this.sourceOrderId = sourceOrderId;
        this.priceCode = priceCode;
        this.performedDate = performedDate;
        this.status = status;
        this.conclusion = conclusion;
        this.paid = paid;
        this.results = immutableResults(results);
        this.clearanceId = clearanceId;
        this.clearanceGrantedAt = clearanceGrantedAt;
        this.emergencyOverrideId = emergencyOverrideId;
        this.resultVersion = resultVersion;
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
                requestedDate, null, LabTestStatus.PENDING, null, false, List.of(), now, now,
                0, null, null, null, null, null, null, null, 0);
    }

    /** Creates a V2 test with explicit charge identity and a payment-gated initial state. */
    public static LabTest createV2(UUID recordId, UUID patientId, UUID requestingDepartmentId,
                                   UUID sourceOrderId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
                                   String labType, String priceCode, LocalDate requestedDate) {
        require(recordId, "LAB_RECORD_REQUIRED", "Hồ sơ xét nghiệm là bắt buộc");
        require(patientId, "LAB_PATIENT_REQUIRED", "Bệnh nhân xét nghiệm là bắt buộc");
        require(requestingDepartmentId, "LAB_DEPARTMENT_REQUIRED", "Khoa chỉ định xét nghiệm là bắt buộc");
        if (careEpisodeType == null || careEpisodeId == null) {
            throw new LabRuleException("LAB_EPISODE_REQUIRED", "Đợt điều trị của xét nghiệm là bắt buộc");
        }
        if (priceCode == null || priceCode.isBlank()) {
            throw new LabRuleException("LAB_PRICE_CODE_REQUIRED", "Mã giá xét nghiệm là bắt buộc");
        }
        if (priceCode.length() > 64) {
            throw new LabRuleException("LAB_PRICE_CODE_REQUIRED", "Mã giá xét nghiệm không hợp lệ");
        }
        if (labType == null || labType.isBlank()) {
            throw new LabRuleException("LAB_TYPE_REQUIRED", "Loại xét nghiệm không được để trống");
        }
        require(requestedDate, "LAB_REQUEST_DATE_REQUIRED", "Ngày yêu cầu xét nghiệm là bắt buộc");
        Instant now = Instant.now();
        return new LabTest(UUID.randomUUID(), recordId, patientId, requestingDepartmentId, labType,
                requestedDate, null, LabTestStatus.AWAITING_PAYMENT, null, false, List.of(), now, now,
                1, careEpisodeType, careEpisodeId, sourceOrderId, priceCode, null, null, null, 0);
    }

    /** Rehydrates an aggregate from persistence without re-running creation invariants. */
    public static LabTest restore(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                                  String labType, LocalDate requestedDate, LocalDate performedDate,
                                  LabTestStatus status, String conclusion, boolean paid,
                                  List<LabResult> results, Instant createdAt, Instant updatedAt) {
        return new LabTest(testId, recordId, patientId, requestingDepartmentId, labType, requestedDate,
                performedDate, status, conclusion, paid, results, createdAt, updatedAt,
                0, null, null, null, null, null, null, null, 0);
    }

    /** Rehydrates all V2 fields while retaining version-0 compatibility rows. */
    public static LabTest restore(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                                  String labType, LocalDate requestedDate, LocalDate performedDate,
                                  LabTestStatus status, String conclusion, boolean paid,
                                  List<LabResult> results, Instant createdAt, Instant updatedAt,
                                  int careContractVersion, CareEpisodeType careEpisodeType,
                                  UUID careEpisodeId, UUID sourceOrderId, String priceCode,
                                  UUID clearanceId, Instant clearanceGrantedAt, UUID emergencyOverrideId,
                                  int resultVersion) {
        return new LabTest(testId, recordId, patientId, requestingDepartmentId, labType, requestedDate,
                performedDate, status, conclusion, paid, results, createdAt, updatedAt,
                careContractVersion, careEpisodeType, careEpisodeId, sourceOrderId, priceCode,
                clearanceId, clearanceGrantedAt, emergencyOverrideId, resultVersion);
    }

    /** Adds the one LAB_TEST clearance matching this exact test and episode. */
    public void grantClearance(LabFinancialClearance clearance) {
        if (careContractVersion != 1 || clearance == null || clearance.purpose() != ClearancePurpose.LAB_TEST
                || !Objects.equals(testId, clearance.testId())
                || !Objects.equals(patientId, clearance.patientId())
                || careEpisodeType != clearance.careEpisodeType()
                || !Objects.equals(careEpisodeId, clearance.careEpisodeId())) {
            throw new LabRuleException("LAB_CLEARANCE_TARGET_MISMATCH",
                    "Quyền thanh toán không khớp xét nghiệm hoặc đợt điều trị");
        }
        if (clearanceId != null) {
            if (clearanceId.equals(clearance.clearanceId())) {
                return;
            }
            throw new LabRuleException("LAB_CLEARANCE_TARGET_MISMATCH",
                    "Xét nghiệm đã gắn với quyền thanh toán khác");
        }
        if (status != LabTestStatus.AWAITING_PAYMENT) {
            throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION",
                    "Chỉ xét nghiệm đang chờ thanh toán mới nhận được quyền thực hiện");
        }
        this.clearanceId = clearance.clearanceId();
        this.clearanceGrantedAt = clearance.grantedAt();
        this.status = LabTestStatus.READY;
        this.updatedAt = Instant.now();
    }

    /** Starts V2 work using a valid exact-target clearance or a complete audited override. */
    public void start(LabFinancialClearance clearance, LabEmergencyOverride override, Instant at) {
        if (careContractVersion == 0) {
            if (clearance != null || override != null) {
                throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION",
                        "Xét nghiệm tương thích không nhận dữ liệu quyền V2");
            }
            changeStatus(LabTestStatus.IN_PROGRESS);
            return;
        }
        if (at == null) {
            throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION", "Thời điểm bắt đầu là bắt buộc");
        }
        if (status == LabTestStatus.READY && clearance != null && !clearance.isExpiredAt(at)) {
            requireMatchingClearance(clearance);
            this.status = LabTestStatus.IN_PROGRESS;
            this.updatedAt = at;
            return;
        }
        if ((status == LabTestStatus.AWAITING_PAYMENT || status == LabTestStatus.READY) && override != null
                && (clearance == null || clearance.isExpiredAt(at))) {
            requireMatchingOverride(override);
            if (override.approvedAt().isAfter(at)) {
                throw new LabRuleException("LAB_OVERRIDE_INVALID", "Thời điểm duyệt cấp cứu không hợp lệ");
            }
            this.emergencyOverrideId = override.overrideId();
            this.status = LabTestStatus.IN_PROGRESS;
            this.updatedAt = at;
            return;
        }
        if (clearance != null && clearance.isExpiredAt(at)) {
            throw new LabRuleException("LAB_CLEARANCE_EXPIRED", "Quyền thực hiện xét nghiệm đã hết hạn");
        }
        if (status == LabTestStatus.AWAITING_PAYMENT || status == LabTestStatus.READY) {
            throw new LabRuleException("LAB_CLEARANCE_REQUIRED",
                    "Cần quyền LAB_TEST còn hiệu lực hoặc phê duyệt cấp cứu để bắt đầu");
        }
        throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION", "Không thể bắt đầu xét nghiệm ở trạng thái hiện tại");
    }

    /** Cancels an active test through the V2 command instead of the generic status endpoint. */
    public void cancel(String reason, UUID cancelledBy) {
        if (careContractVersion == 1 && (reason == null || reason.isBlank() || reason.length() > 1000
                || cancelledBy == null)) {
            throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION", "Lý do và người hủy là bắt buộc");
        }
        if (isFinal()) {
            throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION", "Xét nghiệm đã kết thúc");
        }
        this.status = LabTestStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /**
     * Stores a non-empty result set and completes a pending or in-progress test atomically.
     * A finished test cannot be amended.
     */
    public void recordResults(List<LabResult> results, String conclusion, LocalDate performedDate) {
        if (isFinal()) {
            if (careContractVersion == 1) {
                throw new LabResultFinalizedException();
            }
            throw new LabRuleException("LAB_ALREADY_FINISHED", "Xét nghiệm đã kết thúc");
        }
        if (careContractVersion == 1 && (status != LabTestStatus.IN_PROGRESS
                || (clearanceId == null && emergencyOverrideId == null))) {
            throw new LabRuleException("LAB_CLEARANCE_REQUIRED",
                    "Cần bắt đầu xét nghiệm bằng quyền hợp lệ hoặc phê duyệt cấp cứu trước khi nhập kết quả");
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
        if (careContractVersion == 1) {
            this.resultVersion++;
        }
        this.updatedAt = Instant.now();
    }

    /** Changes lifecycle only along the allowed state machine. Completion requires results. */
    public void changeStatus(LabTestStatus next) {
        if (careContractVersion == 1) {
            throw new LabRuleException("LAB_INVALID_STATUS_TRANSITION",
                    "Xét nghiệm V2 phải dùng lệnh bắt đầu, hủy hoặc nhập kết quả chuyên biệt");
        }
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
            case AWAITING_PAYMENT, READY -> false;
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

    private void requireMatchingClearance(LabFinancialClearance clearance) {
        if (!Objects.equals(clearanceId, clearance.clearanceId())
                || clearance.purpose() != ClearancePurpose.LAB_TEST
                || !Objects.equals(testId, clearance.testId())
                || !Objects.equals(patientId, clearance.patientId())
                || careEpisodeType != clearance.careEpisodeType()
                || !Objects.equals(careEpisodeId, clearance.careEpisodeId())) {
            throw new LabRuleException("LAB_CLEARANCE_TARGET_MISMATCH",
                    "Quyền thanh toán không khớp xét nghiệm hoặc đợt điều trị");
        }
    }

    private void requireMatchingOverride(LabEmergencyOverride override) {
        if (!Objects.equals(testId, override.testId()) || !Objects.equals(patientId, override.patientId())
                || careEpisodeType != override.careEpisodeType()
                || !Objects.equals(careEpisodeId, override.careEpisodeId())) {
            throw new LabRuleException("LAB_OVERRIDE_INVALID",
                    "Phê duyệt cấp cứu không khớp xét nghiệm hoặc đợt điều trị");
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

    public int getCareContractVersion() { return careContractVersion; }

    public CareEpisodeType getCareEpisodeType() { return careEpisodeType; }

    public UUID getCareEpisodeId() { return careEpisodeId; }

    public UUID getSourceOrderId() { return sourceOrderId; }

    public String getPriceCode() { return priceCode; }

    public UUID getClearanceId() { return clearanceId; }

    public Instant getClearanceGrantedAt() { return clearanceGrantedAt; }

    public UUID getEmergencyOverrideId() { return emergencyOverrideId; }

    public int getResultVersion() { return resultVersion; }

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
