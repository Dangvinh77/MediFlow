package com.mediflow.pharmacy.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DispenseRuleException;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

import lombok.Getter;

/**
 * Phiếu theo dõi quá trình xuất thuốc của một đơn.
 *
 * <p>Mỗi đơn có đúng một phiếu. Phiếu mới ở trạng thái {@code PENDING} và chỉ được chuyển một
 * lần sang trạng thái kết thúc tương ứng với kết quả nghiệp vụ.</p>
 */
@Getter
public class DispenseSlip {

    private final UUID dispenseId;
    private final UUID prescriptionId;
    private DispenseStatus status;
    private Instant dispensedAt;
    private UUID dispensedBy;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private DispenseSlip(
            UUID dispenseId,
            UUID prescriptionId,
            DispenseStatus status,
            Instant dispensedAt,
            UUID dispensedBy,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {

        this.dispenseId = dispenseId;
        this.prescriptionId = prescriptionId;
        this.status = status;
        this.dispensedAt = dispensedAt;
        this.dispensedBy = dispensedBy;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Tạo phiếu đang chờ cho một đơn mới.
     *
     * @param prescriptionId mã đơn thuốc
     * @return phiếu chưa có persistence id
     */
    public static DispenseSlip createPending(UUID prescriptionId) {
        return new DispenseSlip(
                null, prescriptionId, DispenseStatus.PENDING,
                null, null, null, null, null);
    }

    /**
     * Dựng lại phiếu xuất từ persistence.
     *
     * @param dispenseId mã phiếu xuất
     * @param prescriptionId mã đơn thuốc
     * @param status trạng thái hiện tại
     * @param dispensedAt thời điểm xuất, nếu có
     * @param dispensedBy người xuất, nếu có
     * @param failureReason lý do kết thúc không thành công, nếu có
     * @param createdAt thời điểm tạo
     * @param updatedAt thời điểm cập nhật cuối
     * @return phiếu phản ánh đúng dữ liệu đã lưu
     */
    public static DispenseSlip restore(
            UUID dispenseId,
            UUID prescriptionId,
            DispenseStatus status,
            Instant dispensedAt,
            UUID dispensedBy,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {

        return new DispenseSlip(
                dispenseId, prescriptionId, status, dispensedAt, dispensedBy,
                failureReason, createdAt, updatedAt);
    }

    /**
     * Đánh dấu phiếu đã xuất thành công.
     *
     * @param actorId mã người hoặc tác nhân hệ thống thực hiện xuất
     * @param timestamp thời điểm xuất
     */
    public void markDispensed(UUID actorId, Instant timestamp) {
        requirePending();
        if (actorId == null || timestamp == null) {
            throw new DispenseRuleException(
                    "DISPENSE_ACTOR_TIME_REQUIRED",
                    "Người thực hiện và thời điểm xuất là bắt buộc");
        }
        status = DispenseStatus.DISPENSED;
        dispensedBy = actorId;
        dispensedAt = timestamp;
        updatedAt = timestamp;
    }

    /**
     * Đánh dấu quy trình xuất thất bại (BR-D12).
     *
     * @param reason nguyên nhân thất bại
     */
    public void markFailed(String reason) {
        requirePending();
        status = DispenseStatus.FAILED;
        failureReason = normalizeReason(reason, "Xuất thuốc thất bại");
        updatedAt = Instant.now();
    }

    /**
     * Kết thúc phiếu vì đơn bị hủy chủ động.
     *
     * @param reason lý do hủy
     * @param timestamp thời điểm hủy
     */
    public void markCancelled(String reason, Instant timestamp) {
        requirePending();
        status = DispenseStatus.CANCELLED;
        failureReason = normalizeReason(reason, "Đơn thuốc đã bị hủy");
        updatedAt = requireTimestamp(timestamp);
    }

    /**
     * Kết thúc phiếu vì toàn bộ giữ chỗ đã hết TTL.
     *
     * @param timestamp thời điểm hết hạn được ghi nhận
     */
    public void markExpired(Instant timestamp) {
        requirePending();
        status = DispenseStatus.EXPIRED;
        failureReason = "Giữ chỗ tồn kho đã hết hạn";
        updatedAt = requireTimestamp(timestamp);
    }

    /** @return {@code true} khi phiếu còn có thể xuất, hủy hoặc hết hạn */
    public boolean isPending() {
        return status == DispenseStatus.PENDING;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new DispenseRuleException(
                    "DISPENSE_INVALID_TRANSITION",
                    "Phiếu xuất không còn ở trạng thái PENDING");
        }
    }

    private String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }

    private Instant requireTimestamp(Instant timestamp) {
        if (timestamp == null) {
            throw new DispenseRuleException(
                    "DISPENSE_TRANSITION_TIME_REQUIRED",
                    "Thời điểm chuyển trạng thái phiếu xuất là bắt buộc");
        }
        return timestamp;
    }
}
