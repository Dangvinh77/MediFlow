package com.mediflow.surgery.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/** Recorder is trusted JWT identity; signer category/document must be verified independently. */
public record RecordSurgeryConsentRequest(
        @NotNull @PositiveOrZero Long expectedCaseRevision,
        @NotNull SurgeryConsentType consentType, @NotNull UUID signerId,
        @NotNull SurgeryConsentSignerType signerType, @NotNull UUID evidenceDocumentId) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected consent field");
    }

    public ManageSurgeryConsentUseCase.SignCommand toCommand(UUID caseId, String key,
            SurgeryActorIdentity identity, String correlation) {
        return new ManageSurgeryConsentUseCase.SignCommand(caseId, expectedCaseRevision, consentType,
                signerId, signerType, evidenceDocumentId, key,
                SurgeryAuditActor.human(identity.accountId(), identity.verifiedStaffId()), correlation);
    }
}
