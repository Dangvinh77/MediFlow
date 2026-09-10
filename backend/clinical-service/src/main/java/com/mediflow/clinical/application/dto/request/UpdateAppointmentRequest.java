package com.mediflow.clinical.application.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

public record UpdateAppointmentRequest(
        @NotNull @FutureOrPresent LocalDate appointmentDate,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime appointmentTime,
        @Size(max = 1000) String reason
) {}
