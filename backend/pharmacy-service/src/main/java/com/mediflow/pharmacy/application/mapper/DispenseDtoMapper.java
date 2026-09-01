package com.mediflow.pharmacy.application.mapper;

import org.mapstruct.Mapper;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.domain.model.DispenseSlip;

/**
 * MapStruct mapper: {@link DispenseSlip} (domain) → {@link DispenseDTO} (response).
 *
 * <p>Mọi field của domain đều có mặt trong DTO, MapStruct tự map theo tên. Chỉ một chiều
 * domain → DTO — client không bao giờ gửi ngược về để dựng phiếu (phiếu chỉ do application
 * tạo/xử lý).
 */
@Mapper(componentModel = "spring")
public interface DispenseDtoMapper {

    /** Domain → DTO (trả về client sau khi xuất thuốc). */
    DispenseDTO toDto(DispenseSlip slip);
}
