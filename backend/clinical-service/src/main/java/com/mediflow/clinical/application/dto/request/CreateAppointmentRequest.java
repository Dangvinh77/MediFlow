package com.mediflow.clinical.application.dto.request;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

public record CreateAppointmentRequest(
        @NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID departmentId,
        @NotNull @FutureOrPresent LocalDate appointmentDate,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime appointmentTime,
        @Size(max = 1000) String reason
) {}
