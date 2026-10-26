package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScheduleManagementScopeChangedEvent(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        Payload payload) {

    public static final String ROUTING_KEY = "organization.schedule-management-scope.changed";

    public record Payload(UUID accountId, long revision, List<UUID> departmentIds,
            UUID actorAccountId, String reason) {
        public Payload {
            departmentIds = List.copyOf(departmentIds);
        }
    }
}
