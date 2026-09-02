package com.mediflow.pharmacy.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event domain: "một loại thuốc chạm hoặc xuống dưới ngưỡng tồn kho". Publish sau khi
 * dispense khiến {@code stockQuantity <= lowStockThreshold} (BR-D11), routing key
 * {@code stock.low} — để hệ thống cảnh báo nhập kho.
 *
 * <p>Kiểu "bắn-rồi-quên": lỗi khi gửi tin này KHÔNG được làm rollback một lần xuất đã thành
 * công (spec §12). Ba trường đầu (eventId, occurredAt, correlationId) là envelope chuẩn.
 */
public record StockLowEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID drugId,
        String drugName,
        int currentStock,
        int threshold
) {}
