package com.mediflow.pharmacy.application.mapper;

import org.mapstruct.Mapper;

import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.domain.model.Drug;

/**
 * MapStruct mapper: {@link Drug} (domain) ↔ {@link DrugDTO} (response).
 *
 * <p>Mọi field của domain đều có mặt trong DTO và ngược lại, nên không cần @Mapping thủ công —
 * MapStruct tự map theo tên. Mapper này nằm trong {@code application} và chỉ đổi hình dạng dữ
 * liệu, không mang quy tắc nghiệp vụ.
 */
@Mapper(componentModel = "spring")
public interface DrugDtoMapper {

    /** Domain → DTO (trả về client). */
    DrugDTO toDto(Drug drug);
}
