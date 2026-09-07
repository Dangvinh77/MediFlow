package com.mediflow.billing.application.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort.DepartmentRevenue;

/**
 * MapStruct mapper: projection tổng hợp {@link DepartmentRevenue} → {@link RevenueByDeptDTO}
 * (backend-spec/06-billing.md §8, §12.1, BR-B10).
 *
 * <p>Hai kiểu có cùng field ({@code departmentId}, {@code totalRevenue}, {@code invoiceCount})
 * nên chỉ cần map theo tên. Tách riêng để {@code RevenueController} (Phần 5/5) không phải tự
 * viết vòng lặp chuyển đổi.
 */
@Mapper(componentModel = "spring")
public interface RevenueMapper {

    RevenueByDeptDTO toDto(DepartmentRevenue projection);

    List<RevenueByDeptDTO> toDtoList(List<DepartmentRevenue> projections);
}
