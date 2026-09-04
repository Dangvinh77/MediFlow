package com.mediflow.pharmacy.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

import lombok.Getter;

/**
 * Aggregate đơn thuốc, chứa ảnh chụp giá và toàn bộ dòng thuốc tại thời điểm kê.
 *
 * <p>Aggregate chịu trách nhiệm tính tổng tiền, ngăn thuốc trùng và bảo vệ vòng đời của đơn.
 * Mọi thay đổi trạng thái phải đi qua các hành vi domain thay vì setter.</p>
 */
@Getter
public class Prescription {

    private final UUID prescriptionId;
    private final UUID recordId;
    private final UUID patientId;
    private final UUID doctorId;
    private final UUID departmentId;
    private final LocalDate prescribedDate;
    private final BigDecimal totalAmount;
    private final List<PrescriptionLine> lines;
    private PrescriptionStatus status;
    private Instant cancelledAt;
    private UUID cancelledBy;
    private String cancellationReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Prescription(
            UUID prescriptionId,
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            UUID departmentId,
            LocalDate prescribedDate,
            BigDecimal totalAmount,
            List<PrescriptionLine> lines,
            PrescriptionStatus status,
            Instant cancelledAt,
            UUID cancelledBy,
            String cancellationReason,
            Instant createdAt,
            Instant updatedAt) {

        this.prescriptionId = prescriptionId;
        this.recordId = recordId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.departmentId = departmentId;
        this.prescribedDate = prescribedDate;
        this.totalAmount = totalAmount;
        this.lines = List.copyOf(lines);
        this.status = status;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Tạo một đơn thuốc mới ở trạng thái {@link PrescriptionStatus#ACTIVE}.
     *
     * @param recordId mã hồ sơ bệnh án
     * @param patientId mã bệnh nhân
     * @param doctorId mã bác sĩ kê đơn
     * @param departmentId mã khoa kê đơn
     * @param prescribedDate ngày kê đơn
     * @param lines các dòng thuốc, không được rỗng hoặc trùng thuốc
     * @return aggregate đơn thuốc mới
     */
    public static Prescription create(
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            UUID departmentId,
            LocalDate prescribedDate,
            List<PrescriptionLine> lines) {

        if (lines == null || lines.isEmpty()) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_EMPTY",
                    "Đơn thuốc phải có ít nhất 1 dòng");
        }

        validateUniqueDrugs(lines);

        return new Prescription(
                null, recordId, patientId, doctorId, departmentId, prescribedDate,
                computeTotalFrom(lines), lines, PrescriptionStatus.ACTIVE,
                null, null, null, null, null);
    }

    /**
     * Dựng lại aggregate từ dữ liệu persistence đầy đủ.
     *
     * @param prescriptionId mã đơn thuốc
     * @param recordId mã hồ sơ bệnh án
     * @param patientId mã bệnh nhân
     * @param doctorId mã bác sĩ kê đơn
     * @param departmentId mã khoa kê đơn
     * @param prescribedDate ngày kê đơn
     * @param totalAmount tổng tiền đã chụp
     * @param lines các dòng thuốc đã lưu
     * @param status trạng thái vòng đời
     * @param cancelledAt thời điểm hủy, nếu có
     * @param cancelledBy người hủy, nếu có
     * @param cancellationReason lý do hủy, nếu có
     * @param createdAt thời điểm tạo
     * @param updatedAt thời điểm cập nhật cuối
     * @return aggregate phản ánh đúng dữ liệu đã lưu
     */
    public static Prescription restore(
            UUID prescriptionId,
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            UUID departmentId,
            LocalDate prescribedDate,
            BigDecimal totalAmount,
            List<PrescriptionLine> lines,
            PrescriptionStatus status,
            Instant cancelledAt,
            UUID cancelledBy,
            String cancellationReason,
            Instant createdAt,
            Instant updatedAt) {

        return new Prescription(
                prescriptionId, recordId, patientId, doctorId, departmentId, prescribedDate,
                totalAmount, lines, status, cancelledAt, cancelledBy, cancellationReason,
                createdAt, updatedAt);
    }

    /**
     * Dựng dữ liệu cũ chưa có trạng thái vòng đời dưới dạng đơn đang hoạt động.
     *
     * @deprecated chỉ giữ để tương thích caller cũ; persistence phải dùng overload đầy đủ
     */
    @Deprecated(forRemoval = false)
    public static Prescription restore(
            UUID prescriptionId,
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            UUID departmentId,
            LocalDate prescribedDate,
            BigDecimal totalAmount,
            List<PrescriptionLine> lines,
            Instant createdAt) {

        return restore(
                prescriptionId, recordId, patientId, doctorId, departmentId, prescribedDate,
                totalAmount, lines, PrescriptionStatus.ACTIVE, null, null, null, createdAt, null);
    }

    /**
     * Tính lại tổng tiền từ các dòng hiện có.
     *
     * @return tổng tiền với hai chữ số thập phân
     */
    public BigDecimal computeTotal() {
        return computeTotalFrom(lines);
    }

    /**
     * Hủy một đơn đang hoạt động.
     *
     * @param actorId mã người thực hiện
     * @param reason lý do hủy có ý nghĩa nghiệp vụ
     * @param now thời điểm hủy
     */
    public void cancel(UUID actorId, String reason, Instant now) {
        requireActive();
        if (actorId == null) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_CANCEL_ACTOR_REQUIRED", "Người hủy đơn thuốc là bắt buộc");
        }
        if (reason == null || reason.isBlank()) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_CANCEL_REASON_REQUIRED", "Lý do hủy đơn thuốc là bắt buộc");
        }
        if (now == null) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_CANCEL_TIME_REQUIRED", "Thời điểm hủy đơn thuốc là bắt buộc");
        }

        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 500) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_CANCEL_REASON_TOO_LONG",
                    "Lý do hủy không được vượt quá 500 ký tự");
        }

        status = PrescriptionStatus.CANCELLED;
        cancelledAt = now;
        cancelledBy = actorId;
        cancellationReason = normalizedReason;
        updatedAt = now;
    }

    /**
     * Kết thúc đơn sau khi xuất toàn bộ thuốc thành công.
     *
     * @param now thời điểm hoàn tất
     */
    public void markFulfilled(Instant now) {
        requireActive();
        status = PrescriptionStatus.FULFILLED;
        updatedAt = requireTimestamp(now);
    }

    /**
     * Kết thúc đơn vì toàn bộ giữ chỗ đã quá TTL.
     *
     * @param now thời điểm hết hạn được ghi nhận
     */
    public void markExpired(Instant now) {
        requireActive();
        status = PrescriptionStatus.EXPIRED;
        updatedAt = requireTimestamp(now);
    }

    /**
     * Kết thúc đơn vì quy trình xuất thuốc thất bại.
     *
     * @param now thời điểm thất bại được ghi nhận
     */
    public void markDispenseFailed(Instant now) {
        requireActive();
        status = PrescriptionStatus.DISPENSE_FAILED;
        updatedAt = requireTimestamp(now);
    }

    /** @return {@code true} khi đơn còn có thể hủy, hết hạn hoặc xuất thuốc */
    public boolean isActive() {
        return status == PrescriptionStatus.ACTIVE;
    }

    /** @return {@code true} khi đơn đã được hủy chủ động */
    public boolean isCancelled() {
        return status == PrescriptionStatus.CANCELLED;
    }

    /** @return {@code true} khi đơn đã được xuất thành công */
    public boolean isFulfilled() {
        return status == PrescriptionStatus.FULFILLED;
    }

    private static BigDecimal computeTotalFrom(List<PrescriptionLine> lines) {
        return lines.stream()
                .map(PrescriptionLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateUniqueDrugs(List<PrescriptionLine> lines) {
        Set<UUID> seenDrugIds = new HashSet<>();
        for (PrescriptionLine line : lines) {
            if (!seenDrugIds.add(line.getDrugId())) {
                throw new PrescriptionRuleException(
                        "PRESCRIPTION_DUPLICATE_DRUG",
                        "Một thuốc chỉ được xuất hiện một lần trong đơn");
            }
        }
    }

    private void requireActive() {
        if (!isActive()) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_INVALID_TRANSITION",
                    "Đơn thuốc không còn ở trạng thái ACTIVE");
        }
    }

    private Instant requireTimestamp(Instant timestamp) {
        if (timestamp == null) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_TRANSITION_TIME_REQUIRED",
                    "Thời điểm chuyển trạng thái đơn thuốc là bắt buộc");
        }
        return timestamp;
    }
}
