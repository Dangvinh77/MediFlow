package com.mediflow.pharmacy.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.StockReservation;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm các dòng giữ chỗ tồn kho".
 * Application không được tự đụng DB; {@code StockReservationPersistenceAdapter}
 * (trong infrastructure) sẽ hiện thực, lưu vào bảng STOCK_RESERVATION.
 *
 * <p>Vì sao cần: kê đơn phải xác nhận "còn đủ thuốc có thể bán" (trừ dự trữ), xuất thuốc
 * phải chuyển dự trữ → kho thật, và job TTL phải tìm các dòng hết hạn để trả lại chỗ.
 */
public interface StockReservationRepositoryPort {

    /** Lưu mới hoặc cập nhật. Trả về đối tượng đã có đầy đủ id + timestamps. */
    StockReservation save(StockReservation reservation);

    /** Đọc giữ chỗ theo đơn — dispense cần biết đơn đã giữ những gì (tối đa 1 aggregate per drug). */
    List<StockReservation> findByPrescription(UUID prescriptionId);

    /**
     * Tất cả giữ chỗ đang {@code RESERVED} của một thuốc — kê đơn dùng để tính
     * số tồn "có thể bán": {@code stock - Σ reserved}.
     */
    List<StockReservation> findReservedByDrug(UUID drugId);

    /** Các giữ chỗ đã quá hạn (status = RESERVED và expires_at < now) — job release TTL. */
    List<StockReservation> findExpired();

    /**
     * Bản KHÓA GHI khi đọc giữ chỗ của một đơn — dành riêng cho luồng dispense
     * (chống tương tranh với job release, BR-D10). Cơ chế khóa (PESSIMISTIC_WRITE)
     * là chuyện của adapter bên infrastructure.
     */
    Optional<StockReservation> findReservedByPrescriptionForUpdate(UUID prescriptionId, UUID drugId);
}
