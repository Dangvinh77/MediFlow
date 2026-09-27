package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;
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
        Instant updatedAt,
        int careContractVersion,
        UUID sourceOrderId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String priceCode,
        UUID clearanceId,
        UUID emergencyOverrideId,
        int resultVersion
) {

    public LabTestDTO {
        results = results == null ? null : List.copyOf(results);
    }

    /** Compatibility constructor for callers that build version-0 projections directly. */
    public LabTestDTO(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                      String labType, LocalDate requestedDate, LocalDate performedDate,
                      LabTestStatus status, String conclusion, boolean paid, List<LabResultDTO> results,
                      Instant createdAt, Instant updatedAt) {
        this(testId, recordId, patientId, requestingDepartmentId, labType, requestedDate, performedDate,
                status, conclusion, paid, results, createdAt, updatedAt, 0, null, null, null,
                null, null, null, 0);
    }
}
