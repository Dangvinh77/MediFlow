package com.mediflow.clinical.application.dto.request;
import java.time.*;
import java.util.UUID;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
public record DoctorWorkShiftUpdateRequest(@NotNull UUID doctorId, @NotNull UUID departmentId,
        @NotNull UUID roomId, @NotNull LocalDate shiftDate,
        @NotNull @JsonFormat(pattern="HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern="HH:mm") LocalTime endTime,
        @Positive int expectedRevision, @NotBlank @Size(max=500) String reason) {}
