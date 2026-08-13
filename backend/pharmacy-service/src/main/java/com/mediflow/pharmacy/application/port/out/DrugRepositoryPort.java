package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.domain.model.Drug;

import java.util.Optional;
import java.util.UUID;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm thuốc".
 * Application không được tự đụng DB; nó vẽ ra lời hứa này, và
 * {@code DrugPersistenceAdapter} (trong infrastructure) sẽ thực hiện.
 */
public interface DrugRepositoryPort {

    /** Lưu mới hoặc cập nhật. Trả về đối tượng đã có đầy đủ id + timestamps. */
    Drug save(Drug drug);

    /** Tìm một thuốc. Không có thì trả {@link Optional#empty()} — application sẽ ném DrugNotFoundException. */
    Optional<Drug> findById(UUID id);

    /**
     * Bản KHÓA GHI khi đọc — dành riêng cho luồng xuất thuốc.
     * Hai dược sĩ xuất cùng lúc không được vượt kho (BR-D10): application cần
     * nói "đưa tôi bản khóa", nên nhu cầu này được nâng lên thành method của port.
     * Cơ chế khóa (PESSIMISTIC_WRITE) là chuyện của adapter bên infrastructure.
     */
    Optional<Drug> findByIdForUpdate(UUID id);

    /** Tìm theo từ khóa trong tên thuốc, có phân trang. PageQuery/PageResult — không dùng Spring Pageable. */
    PageResult<Drug> search(String keyword, PageQuery page);
}
