package com.mediflow.pharmacy.application.dto.response;

import java.util.List;
import java.util.UUID;

/** Explicit Clinical source and deployment price policy; never inferred from related UUIDs. */
public record PrescribingContextDTO(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
        String careEpisodeType, UUID careEpisodeId, List<String> priceCodes) {
    public PrescribingContextDTO { priceCodes = List.copyOf(priceCodes); }
}
