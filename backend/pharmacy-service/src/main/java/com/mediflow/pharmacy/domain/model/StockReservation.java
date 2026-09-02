package com.mediflow.pharmacy.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

import lombok.Getter;

/**
 * Một dòng giữ chỗ tồn kho: "đơn {prescriptionId} đã giữ {quantity} của thuốc {drugId}".
 *
 * <p>Mục đích (xem spec §7.1): khi bác sĩ kê đơn, ta giữ chỗ ngay để bệnh nhân biết trước
 * lượng thuốc có thể bán — tránh việc trả tiền rồi mới vỡ lẽ hết hàng. Đây là một entity
 * RIÊNG, không phải một cột trên {@code Drug}, nên 1 đơn = nhiều dòng giữ chỗ và trace được.
 *
 * <p>Quy tắc chuyển trạng thái (giống {@code DispenseSlip}):
 * <ul>
 *   <li>{@code RESERVED → FULFILLED} khi xuất thuốc thật (reserved → stock);</li>
 *   <li>{@code RESERVED → RELEASED} khi hủy / giải phóng chủ động;</li>
 *   <li>{@code RESERVED → EXPIRED} khi hết hạn TTL.</li>
 * </ul>
 */
@Getter
public class StockReservation {
    private final UUID reservationId;
    private final UUID drugId;
    private final UUID prescriptionId;
    private final int quantity;
    private ReservationStatus status;
    private final Instant createdAt;
    private Instant expiresAt;
    private Instant updatedAt;

    private StockReservation(UUID reservationId, UUID drugId, UUID prescriptionId, int quantity,
                             ReservationStatus status, Instant createdAt, Instant expiresAt, Instant updatedAt) {
        this.reservationId = reservationId;
        this.drugId = drugId;
        this.prescriptionId = prescriptionId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    /** Tạo giữ chỗ mới — luôn {@code RESERVED}, với {@code expiresAt} (policy TTL). */
    public static StockReservation create(UUID drugId, UUID prescriptionId, int quantity, Instant expiresAt) {
        if (quantity <= 0) {
            throw new StockReservationRuleException("RESERVATION_QUANTITY_INVALID", "Số lượng giữ chỗ phải lớn hơn 0");
        }
        if (expiresAt == null) {
            throw new StockReservationRuleException("RESERVATION_EXPIRY_REQUIRED", "Giữ chỗ phải có hạn hết hiệu lực");
        }
        return new StockReservation(null, drugId, prescriptionId, quantity,
                ReservationStatus.RESERVED, null, expiresAt, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static StockReservation restore(UUID reservationId, UUID drugId, UUID prescriptionId, int quantity,
                                           ReservationStatus status, Instant createdAt, Instant expiresAt, Instant updatedAt) {
        return new StockReservation(reservationId, drugId, prescriptionId, quantity,
                status, createdAt, expiresAt, updatedAt);
    }

    /** RESERVED → FULFILLED (đã xuất thuốc thật). */
    public void markFulfilled() {
        requireReserved();
        this.status = ReservationStatus.FULFILLED;
        this.updatedAt = Instant.now();
    }

    /** RESERVED → RELEASED (trả lại chỗ — hủy / giải phóng chủ động). */
    public void release() {
        requireReserved();
        this.status = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    /** RESERVED → EXPIRED (hết hạn TTL — job release). */
    public void expire() {
        requireReserved();
        this.status = ReservationStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    private void requireReserved() {
        if (status != ReservationStatus.RESERVED) {
            throw new StockReservationRuleException("RESERVATION_INVALID_TRANSITION",
                    "Giữ chỗ không còn ở trạng thái RESERVED");
        }
    }
}
