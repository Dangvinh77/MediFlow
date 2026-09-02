package com.mediflow.pharmacy.application.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng thuốc trong kết quả trả về. Nhiều hơn request một chút: có lineId,
 * drugName, unitPrice (giá chụp) và lineTotal (server tính). DTO chỉ mang dữ liệu,
 * không có quy tắc nghiệp vụ.
 */
public record PrescriptionLineDTO(
        UUID lineId, UUID drugId, String drugName,
        int quantity, BigDecimal unitPrice, String dosage, BigDecimal lineTotal
) {}
