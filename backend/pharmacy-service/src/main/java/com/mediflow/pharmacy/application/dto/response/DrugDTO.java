package com.mediflow.pharmacy.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO trả về cho client khi xem / thao tác danh mục thuốc.
 * Mirror đủ model {@code Drug} cộng thêm {@code drugId} và timestamps. DTO chỉ mang dữ liệu,
 * không có quy tắc nghiệp vụ — {@code Drug} (domain) không bao giờ đi thẳng ra ngoài.
 */
public record DrugDTO(
        UUID drugId,
        String drugName,
        String activeIngredient,
        String unit,
        BigDecimal price,
        int stockQuantity,
        LocalDate expiryDate,
        String manufacturer,
        int lowStockThreshold,
        Instant createdAt,
        Instant updatedAt
) {}
