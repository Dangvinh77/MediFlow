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

    /** Danh sách đơn của 1 bệnh nhân — màn hình lịch sử kê đơn. */
    List<Prescription> findByPatient(UUID patientId);
}
