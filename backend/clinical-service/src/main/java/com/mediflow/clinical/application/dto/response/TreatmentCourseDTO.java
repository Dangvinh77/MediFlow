package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TreatmentCourseDTO(
        UUID courseId,
        UUID patientId,
        UUID departmentId,
        String problem,
        String openingReason,
        UUID responsibleDoctorId,
        String status,
        List<UUID> recordIds,
        Instant openedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate lastVisitDate,
        boolean reviewDue,
        @JsonInclude(JsonInclude.Include.NON_NULL) String closureDecision,
        @JsonInclude(JsonInclude.Include.NON_NULL) String closureNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant closedAt) {
    public TreatmentCourseDTO {
        recordIds = List.copyOf(recordIds);
    }
}
