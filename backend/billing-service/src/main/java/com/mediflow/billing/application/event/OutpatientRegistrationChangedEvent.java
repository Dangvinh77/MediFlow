package com.mediflow.billing.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Exact A.7 envelope emitted by Clinical; raw bytes are retained by the source receipt adapter. */
public record OutpatientRegistrationChangedEvent(UUID eventId, String eventType, int version,
        Instant occurredAt, String correlationId, String producer, RegistrationFact payload) {
    public record RegistrationFact(UUID registrationId, long registrationRevision, UUID patientId,
            UUID doctorId, UUID departmentId, UUID sessionId, UUID roomId, LocalDate sessionDate,
            LocalTime startTime, LocalTime endTime, String visitType, UUID reservationId,
            String reservationStatus, String status, Instant checkedInAt, Integer queueNumber,
            Instant reservationExpiresAt, String priceCode, String sourceType, UUID sourceId,
            String careEpisodeType, UUID careEpisodeId, Cancellation cancellation, Instant changedAt) { }

    public record Cancellation(UUID actorAccountId, UUID actorStaffId, String reason, Instant cancelledAt) { }
}
