package com.mediflow.clinical.application.dto.response;

import java.util.UUID;

public record RecordCapabilitiesDTO(
        UUID recordId,
        UUID patientId,
        boolean canRead,
        String readBasis,
        boolean canWriteContent,
        boolean canComplete,
        boolean canRequestAmendment,
        String writeBlockedReason) {
}
