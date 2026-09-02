package com.mediflow.pharmacy.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lệnh điều chỉnh tồn kho.
 * <p>Khác với {@code CreateDrugRequest}: {@code quantity} ở đây <b>âm được</b> — dương là nhập kho,
 * âm là điều chỉnh giảm (kiểm kê, sai lệch). Quy tắc dương/âm thật xử lý trong application service,
 * không phải ở DTO này.
 */
public record AdjustStockRequest(
        @NotNull
        Integer quantity,
        @Size(max = 255)
        String reason
) {}
