package com.mediflow.pharmacy.application.event;

import java.time.Instant;
import java.util.UUID;

/** Event audit thay đổi tồn kho thủ công, routing key {@code stock.adjusted}. */
public record StockAdjustedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID drugId,
        int beforeStock,
        int afterStock,
        int delta,
        String reason
) {
}
