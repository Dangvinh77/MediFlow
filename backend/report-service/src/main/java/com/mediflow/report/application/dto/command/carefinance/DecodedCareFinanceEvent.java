package com.mediflow.report.application.dto.command.carefinance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Offline-decoded event for the V2 projector boundary; it is not a live Rabbit delivery. */
public record DecodedCareFinanceEvent(
        CareFinanceEventMetadata metadata,
        Map<String, Object> payload) {

    public DecodedCareFinanceEvent {
        if (metadata == null || payload == null) {
            throw new IllegalArgumentException("Care-finance metadata and payload are required");
        }
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
