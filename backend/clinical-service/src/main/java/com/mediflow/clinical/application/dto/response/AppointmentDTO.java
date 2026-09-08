package com.mediflow.clinical.application.dto.response;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import com.mediflow.clinical.domain.model.AppointmentStatus;

public record AppointmentDTO(
        UUID appointmentId, UUID patientId, UUID doctorId, UUID departmentId,
        LocalDate appointmentDate, @JsonFormat(pattern = "HH:mm") LocalTime appointmentTime,
        AppointmentStatus status, String reason, Instant createdAt, Instant updatedAt
) {}
