package com.mediflow.clinical.application.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConsultationSessionCreateRequest(
        @NotNull UUID doctorId,
        @NotNull UUID departmentId,
        @NotNull UUID roomId,
        @NotNull LocalDate sessionDate,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull @Valid List<BreakRequest> breaks,
        @NotNull @Min(1) @Max(200) Integer capacity) {
    public record BreakRequest(@NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime) {}
}
