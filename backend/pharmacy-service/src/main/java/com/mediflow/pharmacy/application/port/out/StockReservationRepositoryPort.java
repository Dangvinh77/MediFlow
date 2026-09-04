package com.mediflow.pharmacy.application.port.out;

import java.time.Instant;
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
     * Đọc và khóa toàn bộ reservation của một đơn theo thứ tự {@code drugId} ổn định.
     *
     * <p>Use case hủy và hết hạn dùng kết quả này để kiểm tra lại trạng thái sau khi đã lấy khóa,
     * tránh ghi đè một reservation vừa được xuất ở transaction khác.</p>
     *
     * @param prescriptionId mã đơn thuốc
     * @return các reservation đã khóa
     */
    List<StockReservation> findByPrescriptionForUpdate(UUID prescriptionId);

    /**
     * Tất cả giữ chỗ đang {@code RESERVED} của một thuốc — kê đơn dùng để tính
     * số tồn "có thể bán": {@code stock - Σ reserved}.
     */
    List<StockReservation> findReservedByDrug(UUID drugId);

    /**
     * Tìm các đơn có ít nhất một reservation đang giữ đã hết TTL.
     *
     * <p>Đây chỉ là danh sách ứng viên không khóa. Use case phải khóa prescription và đọc lại
     * reservation trước khi chuyển trạng thái.</p>
     *
     * @param now mốc thời gian đánh giá TTL
     * @param limit số đơn tối đa trả về
     * @return danh sách mã đơn không trùng nhau
     */
    List<UUID> findExpiredPrescriptionIds(Instant now, int limit);

    /**
     * Bản KHÓA GHI khi đọc giữ chỗ của một đơn — dành riêng cho luồng dispense
     * (chống tương tranh với job release, BR-D10). Cơ chế khóa (PESSIMISTIC_WRITE)
     * là chuyện của adapter bên infrastructure.
     */
    Optional<StockReservation> findReservedByPrescriptionForUpdate(UUID prescriptionId, UUID drugId);
}
