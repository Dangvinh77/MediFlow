package com.mediflow.billing.application.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.mediflow.billing.application.dto.response.FeeDTO;
import com.mediflow.billing.domain.model.Fee;

/**
 * MapStruct mapper: {@link Fee} (domain) → {@link FeeDTO} (response).
 *
 * <p>Hầu hết field của DTO trùng tên với getter của domain ({@code feeId}, {@code feeType},
 * {@code departmentId}, {@code incurredDate}, {@code amount}) nên tự map theo tên. Riêng
 * {@code isPaid}: Lombok sinh getter {@code isPaid()} nên MapStruct đọc thành property tên
 * {@code paid} — phải chỉ định tường minh, nếu không DTO luôn nhận {@code false}. Các field nội
 * bộ ({@code recordId}, {@code sourceRefId}, {@code invoiceId}, timestamps) không có trong DTO →
 * MapStruct tự bỏ qua. Mapper chỉ đổi hình dạng dữ liệu, không mang quy tắc nghiệp vụ.
 */
@Mapper(componentModel = "spring")
public interface FeeDtoMapper {

    /** Một khoản phí → DTO. */
    @Mapping(target = "isPaid", source = "paid")
    FeeDTO toDto(Fee fee);

    /** Cả danh sách khoản phí → danh sách DTO (dùng khi dựng {@code InvoiceDTO.fees}). */
    List<FeeDTO> toDtoList(List<Fee> fees);
}
