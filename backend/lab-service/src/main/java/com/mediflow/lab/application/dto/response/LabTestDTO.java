package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabTestStatus;

/** Client-facing projection of the lab aggregate. */
public record LabTestDTO(
        UUID testId,
        UUID recordId,
        UUID patientId,
        UUID requestingDepartmentId,
        String labType,
        LocalDate requestedDate,
        LocalDate performedDate,
        LabTestStatus status,
        String conclusion,
        boolean paid,
        List<LabResultDTO> results,
        Instant createdAt,
        Instant updatedAt
) {

    public LabTestDTO {
        results = results == null ? null : List.copyOf(results);
    }
}
