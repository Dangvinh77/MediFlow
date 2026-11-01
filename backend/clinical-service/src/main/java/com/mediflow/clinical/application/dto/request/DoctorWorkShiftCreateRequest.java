package com.mediflow.clinical.application.dto.request;
import java.time.*;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;
public record DoctorWorkShiftCreateRequest(@NotNull UUID doctorId, @NotNull UUID departmentId,
        @NotNull UUID roomId, @NotNull LocalDate shiftDate,
        @NotNull @JsonFormat(pattern="HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern="HH:mm") LocalTime endTime) {}
