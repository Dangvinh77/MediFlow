package com.mediflow.pharmacy.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.Prescription;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm đơn thuốc".
 * Đơn thuốc (kèm các dòng) phải được ghi nhận lại để phục vụ xem lịch sử kê đơn
 * và phục vụ luồng xuất thuốc. Application không được tự đụng DB; nó vẽ ra lời hứa
 * này, và {@code PrescriptionPersistenceAdapter} (trong infrastructure) sẽ hiện thực.
 */
public interface PrescriptionRepositoryPort {

    /** Lưu đơn + các dòng (một aggregate). Trả về đối tượng đã có đầy đủ id + timestamps. */
    Prescription save(Prescription prescription);

    /** Đọc 1 đơn kèm các dòng. Không có thì trả {@link Optional#empty()} — application sẽ ném PrescriptionNotFoundException. */
    Optional<Prescription> findById(UUID id);

    /**
     * Đọc và khóa ghi aggregate đơn thuốc cho một thao tác chuyển trạng thái.
     *
     * <p>Adapter persistence phải dùng khóa bi quan để hủy, hết hạn và xuất thuốc không cùng
     * xử lý một trạng thái {@code ACTIVE} cũ.</p>
     *
     * @param id mã đơn thuốc
     * @return đơn đã khóa hoặc rỗng nếu không tồn tại
     */
    Optional<Prescription> findByIdForUpdate(UUID id);

    /** Danh sách đơn của 1 bệnh nhân — màn hình lịch sử kê đơn. */
    List<Prescription> findByPatient(UUID patientId);
}
