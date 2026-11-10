package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;

public record OutpatientRegistrationFact(
        UUID registrationId,
        int registrationRevision,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        UUID sessionId,
        UUID roomId,
        LocalDate sessionDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        OutpatientVisitType visitType,
        UUID reservationId,
        OutpatientReservationStatus reservationStatus,
        OutpatientRegistrationStatus status,
        Instant checkedInAt,
        Integer queueNumber,
        Instant reservationExpiresAt,
        String priceCode,
        String sourceType,
        UUID sourceId,
        String careEpisodeType,
        UUID careEpisodeId,
        CancellationFact cancellation,
        Instant changedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) RescheduleFact reschedule) {

    public OutpatientRegistrationFact(UUID registrationId, int registrationRevision, UUID patientId, UUID doctorId, UUID departmentId,
            UUID sessionId, UUID roomId, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, OutpatientVisitType visitType,
            UUID reservationId, OutpatientReservationStatus reservationStatus, OutpatientRegistrationStatus status, Instant checkedInAt,
            Integer queueNumber, Instant reservationExpiresAt, String priceCode, String sourceType, UUID sourceId, String careEpisodeType,
            UUID careEpisodeId, CancellationFact cancellation, Instant changedAt) {
        this(registrationId, registrationRevision, patientId, doctorId, departmentId, sessionId, roomId, sessionDate, startTime, endTime,
                visitType, reservationId, reservationStatus, status, checkedInAt, queueNumber, reservationExpiresAt, priceCode, sourceType,
                sourceId, careEpisodeType, careEpisodeId, cancellation, changedAt, null);
    }

    /** Present only on the revision that moved the registration. */
    public record RescheduleFact(UUID actorAccountId, UUID actorStaffId, String reason, UUID previousSessionId, UUID previousDoctorId,
            UUID previousRoomId, LocalDate previousSessionDate, @JsonFormat(pattern = "HH:mm") LocalTime previousStartTime, Instant rescheduledAt) {
    }

    public record CancellationFact(UUID actorAccountId, UUID actorStaffId, String reason, Instant cancelledAt) {
    }
}
