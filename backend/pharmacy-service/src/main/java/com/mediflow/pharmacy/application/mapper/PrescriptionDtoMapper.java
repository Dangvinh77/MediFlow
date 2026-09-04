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

    /**
     * Chuyển aggregate đơn thuốc thành response DTO.
     *
     * @param prescription aggregate đơn thuốc
     * @param dispenseStatus trạng thái phiếu xuất tương ứng
     * @param lineDtos các dòng đã bổ sung tên thuốc
     * @return DTO hoàn chỉnh gửi qua HTTP
     */
    @Mapping(target = "status", source = "prescription.status")
    @Mapping(target = "dispenseStatus", source = "dispenseStatus")
    @Mapping(target = "lines", source = "lineDtos")
    PrescriptionDTO toDto(
            Prescription prescription,
            DispenseStatus dispenseStatus,
            List<PrescriptionLineDTO> lineDtos);

    /**
     * Chuyển một dòng domain thành DTO và bổ sung tên thuốc đã nạp.
     *
     * @param line dòng đơn thuốc
     * @param drugName tên thuốc dùng để hiển thị
     * @return DTO của dòng đơn thuốc
     */
    @Mapping(target = "drugName", source = "drugName")
    PrescriptionLineDTO toLineDto(PrescriptionLine line, String drugName);
}
