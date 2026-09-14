package com.mediflow.pharmacy.application.event;

import java.time.Instant;
import java.util.UUID;

/** Event audit thay đổi tồn kho thủ công, routing key {@code stock.adjusted}.
 *
 * @param eventId event identifier
 * @param occurredAt event timestamp
 * @param correlationId request correlation identifier
 * @param actorId audit actor
 * @param drugId drug identifier
 * @param beforeStock stock before adjustment
 * @param afterStock stock after adjustment
 * @param delta applied delta
 * @param reason adjustment reason
 */
public record StockAdjustedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID actorId,
        UUID drugId,
        int beforeStock,
        int afterStock,
        int delta,
        String reason
) {
}
