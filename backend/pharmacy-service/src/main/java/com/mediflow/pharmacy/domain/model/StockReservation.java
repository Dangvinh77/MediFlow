package com.mediflow.pharmacy.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

import lombok.Getter;

/**
 * Một dòng giữ chỗ tồn kho cho một thuốc thuộc một đơn thuốc.
 *
 * <p>Giữ chỗ không làm thay đổi tồn kho thật. Chỉ trạng thái {@code RESERVED} được trừ khỏi
 * số lượng có thể bán; vì vậy {@code RELEASED} và {@code EXPIRED} tự động trả lại khả năng bán
 * mà không cộng vào {@code DRUG.stock_quantity}.</p>
 */
@Getter
public class StockReservation {

    private final UUID reservationId;
    private final UUID drugId;
    private final UUID prescriptionId;
    private final int quantity;
    private ReservationStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant updatedAt;
    private ReservationReleaseReason releaseReason;
    private Instant releasedAt;
    private UUID releasedBy;

    private StockReservation(
            UUID reservationId,
            UUID drugId,
            UUID prescriptionId,
            int quantity,
            ReservationStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant updatedAt,
            ReservationReleaseReason releaseReason,
            Instant releasedAt,
            UUID releasedBy) {

        this.reservationId = reservationId;
        this.drugId = drugId;
        this.prescriptionId = prescriptionId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
        this.releaseReason = releaseReason;
        this.releasedAt = releasedAt;
        this.releasedBy = releasedBy;
    }

    /**
     * Tạo một giữ chỗ mới ở trạng thái {@code RESERVED}.
     *
     * @param drugId mã thuốc được giữ
     * @param prescriptionId mã đơn sở hữu giữ chỗ
     * @param quantity số lượng giữ, phải lớn hơn 0
     * @param expiresAt thời điểm TTL kết thúc
     * @return giữ chỗ mới chưa có persistence id
     */
    public static StockReservation create(
            UUID drugId,
            UUID prescriptionId,
            int quantity,
            Instant expiresAt) {

        if (quantity <= 0) {
            throw new StockReservationRuleException(
                    "RESERVATION_QUANTITY_INVALID", "Số lượng giữ chỗ phải lớn hơn 0");
        }
        if (expiresAt == null) {
            throw new StockReservationRuleException(
                    "RESERVATION_EXPIRY_REQUIRED", "Giữ chỗ phải có hạn hết hiệu lực");
        }

        return new StockReservation(
                null, drugId, prescriptionId, quantity, ReservationStatus.RESERVED,
                null, expiresAt, null, null, null, null);
    }

    /**
     * Dựng lại giữ chỗ từ dữ liệu persistence đầy đủ.
     *
     * @param reservationId mã giữ chỗ
     * @param drugId mã thuốc
     * @param prescriptionId mã đơn thuốc
     * @param quantity số lượng giữ
     * @param status trạng thái hiện tại
     * @param createdAt thời điểm tạo
     * @param expiresAt thời điểm hết TTL
     * @param updatedAt thời điểm cập nhật cuối
     * @param releaseReason lý do giải phóng, nếu có
     * @param releasedAt thời điểm giải phóng, nếu có
     * @param releasedBy người giải phóng, nếu có
     * @return giữ chỗ phản ánh đúng dữ liệu đã lưu
     */
    public static StockReservation restore(
            UUID reservationId,
            UUID drugId,
            UUID prescriptionId,
            int quantity,
            ReservationStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant updatedAt,
            ReservationReleaseReason releaseReason,
            Instant releasedAt,
            UUID releasedBy) {

        return new StockReservation(
                reservationId, drugId, prescriptionId, quantity, status, createdAt,
                expiresAt, updatedAt, releaseReason, releasedAt, releasedBy);
    }

    /**
     * Dựng dữ liệu cũ chưa có audit release.
     *
     * @deprecated persistence mới phải dùng overload đầy đủ
     */
    @Deprecated(forRemoval = false)
    public static StockReservation restore(
            UUID reservationId,
            UUID drugId,
            UUID prescriptionId,
            int quantity,
            ReservationStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant updatedAt) {

        return restore(
                reservationId, drugId, prescriptionId, quantity, status, createdAt,
                expiresAt, updatedAt, null, null, null);
    }

    /** Chuyển giữ chỗ đang hiệu lực sang trạng thái đã xuất thuốc. */
    public void markFulfilled() {
        requireReserved();
        status = ReservationStatus.FULFILLED;
        updatedAt = Instant.now();
    }

    /**
     * Giải phóng chủ động một giữ chỗ đang hiệu lực.
     *
     * <p>Runtime chỉ được giải phóng trước hạn vì đơn bị hủy, thanh toán thất bại, xuất thuốc
     * thất bại hoặc ADMIN override. {@code TTL_EXPIRED} phải đi qua {@link #expire(Instant)};
     * {@code LEGACY_MIGRATION} chỉ dành cho migration dữ liệu và bị từ chối tại domain.</p>
     *
     * @param reason nguyên nhân nghiệp vụ được phép giải phóng chủ động
     * @param actorId người thực hiện; bắt buộc với hủy đơn và ADMIN override, có thể
     *                {@code null} với bù trừ tự động
     * @param now thời điểm giải phóng
     */
    public void release(
            ReservationReleaseReason reason,
            UUID actorId,
            Instant now) {

        requireReserved();
        if (reason == null) {
            throw new StockReservationRuleException(
                    "RESERVATION_RELEASE_REASON_REQUIRED",
                    "Lý do giải phóng giữ chỗ là bắt buộc");
        }
        if (reason == ReservationReleaseReason.TTL_EXPIRED
                || reason == ReservationReleaseReason.LEGACY_MIGRATION) {
            throw new StockReservationRuleException(
                    "RESERVATION_RELEASE_REASON_INVALID",
                    "Lý do này không được sử dụng để giải phóng giữ chỗ tại runtime");
        }
        if (now == null) {
            throw new StockReservationRuleException(
                    "RESERVATION_RELEASE_TIME_REQUIRED",
                    "Thời điểm giải phóng giữ chỗ là bắt buộc");
        }
        if ((reason == ReservationReleaseReason.PRESCRIPTION_CANCELLED
                || reason == ReservationReleaseReason.ADMIN_OVERRIDE)
                && actorId == null) {
            throw new StockReservationRuleException(
                    "RESERVATION_RELEASE_ACTOR_REQUIRED",
                    "Người thực hiện là bắt buộc khi hủy hoặc override giữ chỗ");
        }

        status = ReservationStatus.RELEASED;
        releaseReason = reason;
        releasedBy = actorId;
        releasedAt = now;
        updatedAt = now;
    }

    /**
     * Kết thúc một giữ chỗ đã quá TTL.
     *
     * @param now thời điểm hệ thống đánh giá TTL
     */
    public void expire(Instant now) {
        requireReserved();
        if (now == null) {
            throw new StockReservationRuleException(
                    "RESERVATION_EXPIRY_TIME_REQUIRED",
                    "Thời điểm kiểm tra hết hạn là bắt buộc");
        }
        if (!isExpiredAt(now)) {
            throw new StockReservationRuleException(
                    "RESERVATION_NOT_EXPIRED", "Giữ chỗ chưa hết hạn");
        }

        status = ReservationStatus.EXPIRED;
        releaseReason = ReservationReleaseReason.TTL_EXPIRED;
        releasedBy = null;
        releasedAt = now;
        updatedAt = now;
    }

    /**
     * Kiểm tra TTL theo thời điểm do caller cung cấp để batch dùng cùng một mốc thời gian.
     *
     * @param now thời điểm cần so sánh
     * @return {@code true} khi {@code expiresAt <= now}
     */
    public boolean isExpiredAt(Instant now) {
        return now != null && !expiresAt.isAfter(now);
    }

    /** @return {@code true} khi giữ chỗ vẫn làm giảm số tồn có thể bán */
    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    private void requireReserved() {
        if (!isReserved()) {
            throw new StockReservationRuleException(
                    "RESERVATION_INVALID_TRANSITION",
                    "Giữ chỗ không còn ở trạng thái RESERVED");
        }
    }
}
