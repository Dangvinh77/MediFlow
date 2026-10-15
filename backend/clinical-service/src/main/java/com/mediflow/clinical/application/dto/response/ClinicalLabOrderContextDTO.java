package com.mediflow.clinical.application.dto.response;

import com.mediflow.clinical.domain.model.CareEpisodeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Exact persisted order identity returned only to the authenticated Lab service. */
public record ClinicalLabOrderContextDTO(
        boolean exists,
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
        Instant observedAt) {

    public static ClinicalLabOrderContextDTO absent(UUID id, Instant observedAt) {
        return new ClinicalLabOrderContextDTO(false, id, null, null, null, null, null, null,
                null, null, null, null, observedAt);
    }
}
