package com.mediflow.pharmacy.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO trả về cho client khi xem / thao tác danh mục thuốc.
 * Mirror đủ model {@code Drug} cộng thêm {@code drugId} và timestamps. DTO chỉ mang dữ liệu,
 * không có quy tắc nghiệp vụ — {@code Drug} (domain) không bao giờ đi thẳng ra ngoài.
 *
 * @param drugId drug identifier
 * @param drugName display name
 * @param activeIngredient active ingredient
 * @param unit dispensing unit
 * @param price unit price
 * @param stockQuantity on-hand quantity
 * @param expiryDate expiry date
 * @param manufacturer manufacturer name
 * @param lowStockThreshold alert threshold
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
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
