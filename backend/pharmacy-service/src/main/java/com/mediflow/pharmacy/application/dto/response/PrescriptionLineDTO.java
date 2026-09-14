package com.mediflow.pharmacy.application.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng thuốc trong kết quả trả về. Nhiều hơn request một chút: có lineId,
 * drugName, unitPrice (giá chụp) và lineTotal (server tính). DTO chỉ mang dữ liệu,
 * không có quy tắc nghiệp vụ.
 *
 * @param lineId line identifier
 * @param drugId drug identifier
 * @param drugName display name snapshot
 * @param quantity prescribed quantity
 * @param unitPrice captured unit price
 * @param dosage dosage instructions
 * @param lineTotal calculated line total
 */
public record PrescriptionLineDTO(
        UUID lineId, UUID drugId, String drugName,
        int quantity, BigDecimal unitPrice, String dosage, BigDecimal lineTotal
) {}
