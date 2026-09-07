package com.mediflow.billing.application.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.mediflow.billing.application.dto.response.FeeDTO;
import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.domain.model.Invoice;

/**
 * MapStruct mapper: {@link Invoice} (domain) + danh sách khoản phí → {@link InvoiceDTO} (response).
 *
 * <p>{@code Invoice} không giữ danh sách {@code Fee} bên trong (aggregate chỉ giữ tổng tiền),
 * nên {@code fees} được truyền vào như tham số nguồn phụ — MapStruct 1.5+ hỗ trợ nhiều tham số
 * nguồn. Các field vô hướng còn lại map theo tên từ {@code invoice}; riêng {@code isPaid} phải
 * chỉ định tường minh vì Lombok sinh getter {@code isPaid()} (MapStruct đọc thành property
 * {@code paid}).
 */
@Mapper(componentModel = "spring")
public interface InvoiceDtoMapper {

    /**
     * Hóa đơn + các khoản phí đã map sẵn → DTO đầy đủ.
     *
     * @param invoice aggregate hóa đơn
     * @param fees    danh sách khoản phí thành phần (đã là {@link FeeDTO})
     */
    @Mapping(target = "isPaid", source = "invoice.paid")
    @Mapping(target = "fees", source = "fees")
    InvoiceDTO toDto(Invoice invoice, List<FeeDTO> fees);
}
