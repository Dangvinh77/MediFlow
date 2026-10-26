package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;

public record ConsultationSessionDTO(
        UUID sessionId,
        int revision,
        UUID doctorId,
        UUID departmentId,
        UUID roomId,
        LocalDate sessionDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        List<BreakDTO> breaks,
        int capacity,
        ConsultationSessionStatus status,
        int heldCount,
        int paymentLockedCount,
        int confirmedCount,
        int consumedCount,
        int availableCount,
        Instant observedAt) {

    public ConsultationSessionDTO {
        breaks = List.copyOf(breaks);
    }

    public record BreakDTO(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime endTime) {}
}
