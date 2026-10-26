package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrationCreateRequest(
        @NotNull UUID patientId,
        @NotNull UUID sessionId,
        @Positive int expectedSessionRevision,
        @NotNull OutpatientReceptionMode receptionMode,
        @NotNull OutpatientVisitType visitType,
        UUID previousRegistrationId,
        @Size(max = 1000) String reason,
        UUID followUpRecommendationId,
        @Size(max = 1000) String replacementReason,
        Boolean patientAgreedToDoctorReplacement) {
    public RegistrationCreateRequest(UUID patientId, UUID sessionId, int expectedSessionRevision,
            OutpatientReceptionMode receptionMode, OutpatientVisitType visitType,
            UUID previousRegistrationId, String reason) {
        this(patientId, sessionId, expectedSessionRevision, receptionMode, visitType, previousRegistrationId,
                reason, null, null, null);
    }
}
