package com.mediflow.clinical.application.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One visit on a patient's timeline. Carries no clinical content. */
public record TimelineEntryDTO(
        UUID recordId,
        LocalDate examinationDate,
        UUID departmentId,
        UUID doctorId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID responsibleDoctorId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String visitKind,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String disposition,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID previousRecordId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<UUID> treatmentCourseIds
) {}
