package com.mediflow.pharmacy.application.port.out;

import java.util.Optional;

import java.util.UUID;

import com.mediflow.pharmacy.domain.model.DispenseSlip;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm phiếu xuất".
 * Trạng thái phiếu (PENDING / DISPENSED / FAILED / CANCELLED / EXPIRED) phải được ghi nhận,
 * vì đó là bằng chứng
 * của saga: đã xuất chưa, xuất thất bại vì lý do gì. Application không được tự đụng DB;
 * {@code DispenseSlipPersistenceAdapter} (trong infrastructure) sẽ hiện thực.
 */
public interface DispenseSlipRepositoryPort {

    /**
     * Lưu phiếu — kể cả phiếu ở trạng thái FAILED vẫn phải lưu được (BR-D12).
     * Trả về đối tượng đã có đầy đủ id + timestamps.
     */
    DispenseSlip save(DispenseSlip dispenseSlip);

    /** Đọc 1 phiếu. Không có thì trả {@link Optional#empty()} — application sẽ ném DispenseNotFoundException. */
    Optional<DispenseSlip> findById(UUID id);

    /**
     * Tìm phiếu theo đơn. Cột prescription_id là UNIQUE — mỗi đơn đúng 1 phiếu, nên kết quả tối đa 1.
     * Luồng xuất thuốc dùng method này để lấy phiếu từ prescriptionId.
     */
    Optional<DispenseSlip> findByPrescription(UUID prescriptionId);

    /**
     * Tìm và khóa ghi phiếu xuất của một đơn trước khi chuyển trạng thái.
     *
     * @param prescriptionId mã đơn thuốc
     * @return phiếu đã khóa hoặc rỗng nếu dữ liệu không tồn tại
     */
    Optional<DispenseSlip> findByPrescriptionForUpdate(UUID prescriptionId);
}
