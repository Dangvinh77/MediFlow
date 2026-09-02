package com.mediflow.pharmacy.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionLineDTO;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

import java.util.List;

/**
 * MapStruct mapper: {@link Prescription} (domain) → {@link PrescriptionDTO} (response).
 *
 * <p>Hai trường DTO không nằm trong domain nên được MapStruct lấy từ tham số nguồn phụ
 * (MapStruct 1.5+ hỗ trợ nhiều tham số nguồn):
 * <ul>
 *   <li>{@code dispenseStatus} — trạng thái phiếu xuất hiện tại (service truyền vào).</li>
 *   <li>{@code PrescriptionLineDTO.drugName} — tên thuốc, service nạp từ kho rồi truyền vào.</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface PrescriptionDtoMapper {

    /** Domain → DTO, kèm trạng thái phiếu xuất hiện tại của đơn. */
    @Mapping(target = "dispenseStatus", source = "status")
    @Mapping(target = "lines", source = "lineDtos")
    PrescriptionDTO toDto(Prescription prescription, DispenseStatus status, List<PrescriptionLineDTO> lineDtos);

    /** Một dòng → DTO, kèm tên thuốc (nạp từ kho). */
    @Mapping(target = "drugName", source = "drugName")
    PrescriptionLineDTO toLineDto(PrescriptionLine line, String drugName);
}
