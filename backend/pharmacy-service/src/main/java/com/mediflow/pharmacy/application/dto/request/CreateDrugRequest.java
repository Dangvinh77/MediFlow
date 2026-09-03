package com.mediflow.pharmacy.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request kê khai một loại thuốc mới — mirror đúng model {@code Drug}, không có
 * {@code drugId}/{@code createdAt}/{@code updatedAt} (server tự sinh).
 * Validation ở đây là "tuyến phòng thủ tại biên"; quy tắc thật vẫn chạy trong {@code Drug.create}
 * (tên/đơn vị bắt buộc, giá không âm, hạn dùng không ở quá khứ).
 */
public record CreateDrugRequest(
        @NotBlank @Size(max = 150)
        String drugName,
        @Size(max = 150)
        String activeIngredient,
        @NotBlank @Size(max = 20)
        String unit,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2)
        BigDecimal price,
        @NotNull @Min(0)
        Integer stockQuantity,
        @NotNull @FutureOrPresent
        LocalDate expiryDate,   // hạn sử dụng phải ở tương lai
        @Size(max = 150)
        String manufacturer,
        @Min(0)
        Integer lowStockThreshold
) {}
