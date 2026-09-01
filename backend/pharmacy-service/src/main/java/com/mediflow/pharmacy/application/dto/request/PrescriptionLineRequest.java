package com.mediflow.pharmacy.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một dòng thuốc trong đơn — client báo "kê thuốc nào, bao nhiêu, liều dùng ra sao".
 * Không có trường giá: giá phải để server tự chụp từ kho tại thời điểm kê đơn (BR-D8),
 * client không được đặt giá.
 */
public record PrescriptionLineRequest(
        @NotNull UUID drugId,
        @NotNull @Min(1) Integer quantity,
        @Size(max = 255) String dosage
) {}
