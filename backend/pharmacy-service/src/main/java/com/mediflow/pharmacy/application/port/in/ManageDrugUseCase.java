package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;

/**
 * In-port — "bảng công việc quản lý danh mục thuốc" (DRUG).
 * Bên ngoài (web/controller) nhờ service làm 4 việc: thêm thuốc, xem 1 thuốc, tìm kiếm
 * theo tên và điều chỉnh tồn kho. Đây chỉ là hợp đồng — <b>không có code làm thật</b>;
 * {@code PharmacyApplicationService} sẽ hiện thực. Controller (driving adapter) gọi qua
 * in-port này, không bao giờ gọi thẳng application service.
 */
public interface ManageDrugUseCase {

    /** Thêm một loại thuốc mới. Tên/đơn vị bắt buộc, giá không âm, hạn dùng không ở quá khứ (kiểm tra trong {@code Drug.create}). */
    DrugDTO create(CreateDrugRequest rq);

    /** Xem 1 thuốc theo id. Không có thì application ném {@code DrugNotFoundException} → 404 DRUG_NOT_FOUND. */
    DrugDTO getById(UUID id);

    /** Tìm thuốc theo từ khóa trong tên, phân trang bằng PageQuery/PageResult của common (không dùng Spring Pageable). */
    PageResult<DrugDTO> search(String keyword, PageQuery page);

    /**
     * Điều chỉnh tồn kho của một thuốc (nhập thêm / giảm). Cần {@code id} để biết <b>điều chỉnh cho thuốc nào</b>;
     * {@code arq} chỉ mang "điều chỉnh bao nhiêu" ({@code quantity}) và "vì sao" ({@code reason}).
     * Dương = nhập kho, âm = giảm. Quy tắc số lượng &gt; 0 nằm trong {@code Drug.restock}.
     */
    DrugDTO adjustStock(UUID id, AdjustStockRequest arq);
}
