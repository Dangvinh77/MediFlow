package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;

public record OutpatientRegistrationDTO(
        UUID registrationId,
        int revision,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        UUID roomId,
        UUID sessionId,
        LocalDate sessionDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        OutpatientReceptionMode receptionMode,
        OutpatientVisitType visitType,
        UUID previousRegistrationId,
        OutpatientRegistrationStatus status,
        UUID reservationId,
        int reservationRevision,
        OutpatientReservationStatus reservationStatus,
        Instant reservationExpiresAt,
        Instant checkedInAt,
        Integer queueNumber,
        boolean readyForExam,
        Instant createdAt,
        Instant updatedAt, UUID recordId, FollowUpRecommendationDTO followUpRecommendation) {
    public OutpatientRegistrationDTO(
        UUID registrationId,
        int revision,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        UUID roomId,
        UUID sessionId,
        LocalDate sessionDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        OutpatientReceptionMode receptionMode,
        OutpatientVisitType visitType,
        UUID previousRegistrationId,
        OutpatientRegistrationStatus status,
        UUID reservationId,
        int reservationRevision,
        OutpatientReservationStatus reservationStatus,
        Instant reservationExpiresAt,
        Instant checkedInAt,
        Integer queueNumber,
        boolean readyForExam,
        Instant createdAt,
        Instant updatedAt) { this(registrationId, revision, patientId, doctorId, departmentId, roomId, sessionId, sessionDate, startTime, endTime, receptionMode, visitType, previousRegistrationId, status, reservationId, reservationRevision, reservationStatus, reservationExpiresAt, checkedInAt, queueNumber, readyForExam, createdAt, updatedAt, null, null); }
}
