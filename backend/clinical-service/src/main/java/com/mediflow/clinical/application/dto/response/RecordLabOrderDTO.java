package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A Lab order of one record with its live Lab status; {@code testId} is absent until Lab has received it. */
public record RecordLabOrderDTO(UUID sourceOrderId, UUID testId, String labType, String status, Instant orderedAt) {
}
