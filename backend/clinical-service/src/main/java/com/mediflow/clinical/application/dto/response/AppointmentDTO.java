package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mediflow.clinical.domain.model.AppointmentStatus;

public record AppointmentDTO(
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        LocalDate appointmentDate,
        @JsonFormat(pattern = "HH:mm") LocalTime appointmentTime,
        AppointmentStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        short careContractVersion,
        UUID examClearanceId,
        Instant examClearanceAt,
        UUID emergencyOverrideId,
        String examPriceCode,
        Instant checkedInAt,
        Instant examinationStartedAt,
        Instant completedAt,
        UUID cancelledBy,
        String cancellationReason,
        Instant cancelledAt
) {
    public AppointmentDTO(UUID appointmentId, UUID patientId, UUID doctorId, UUID departmentId,
            LocalDate appointmentDate, LocalTime appointmentTime, AppointmentStatus status, String reason,
            Instant createdAt, Instant updatedAt, short careContractVersion, UUID examClearanceId,
            Instant examClearanceAt, UUID emergencyOverrideId, String examPriceCode, Instant checkedInAt,
            Instant examinationStartedAt, Instant completedAt) {
        this(appointmentId, patientId, doctorId, departmentId, appointmentDate, appointmentTime, status, reason,
                createdAt, updatedAt, careContractVersion, examClearanceId, examClearanceAt, emergencyOverrideId,
                examPriceCode, checkedInAt, examinationStartedAt, completedAt, null, null, null);
    }
}
