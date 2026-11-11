package com.mediflow.clinical.application.dto.response;

import com.mediflow.clinical.domain.model.CareEpisodeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Stable source-order fields consumed by Lab after a Clinical write. */
public record ClinicalLabOrderDTO(
        UUID sourceOrderId,
        UUID recordId,
        UUID patientId,
        UUID requestingDoctorId,
        UUID requestingDepartmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String labType,
        LocalDate requestedDate,
        String priceCode,
        Instant orderedAt,
        String serviceCode) {

    public ClinicalLabOrderDTO(UUID sourceOrderId, UUID recordId, UUID patientId, UUID requestingDoctorId,
                               UUID requestingDepartmentId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
                               String labType, LocalDate requestedDate, String priceCode, Instant orderedAt) {
        this(sourceOrderId, recordId, patientId, requestingDoctorId, requestingDepartmentId, careEpisodeType,
                careEpisodeId, labType, requestedDate, priceCode, orderedAt, null);
    }
}
