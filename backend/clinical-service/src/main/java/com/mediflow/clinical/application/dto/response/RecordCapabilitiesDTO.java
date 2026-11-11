package com.mediflow.clinical.application.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Collaboration flags are absent while {@code mediflow.v2.record-amendment} is off. */
public record RecordCapabilitiesDTO(
        UUID recordId,
        UUID patientId,
        boolean canRead,
        String readBasis,
        boolean canWriteContent,
        boolean canComplete,
        boolean canRequestAmendment,
        String writeBlockedReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canHandover,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canAcceptHandover,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canAddConsultationNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canAmend,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canDecideAmendments) {

    public RecordCapabilitiesDTO(UUID recordId, UUID patientId, boolean canRead, String readBasis,
            boolean canWriteContent, boolean canComplete, boolean canRequestAmendment, String writeBlockedReason) {
        this(recordId, patientId, canRead, readBasis, canWriteContent, canComplete, canRequestAmendment,
                writeBlockedReason, null, null, null, null, null);
    }
}
