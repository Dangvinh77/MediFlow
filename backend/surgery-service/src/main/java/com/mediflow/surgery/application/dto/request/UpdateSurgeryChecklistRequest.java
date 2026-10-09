package com.mediflow.surgery.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/** Exact pinned item; references are inputs to authority verification, never approvals. */
public record UpdateSurgeryChecklistRequest(
        @NotNull UUID checklistItemId,
        @NotNull @PositiveOrZero Long expectedCaseRevision,
        @NotNull @PositiveOrZero Long expectedSnapshotRevision,
        @NotNull @PositiveOrZero Long expectedItemRevision,
        @NotNull SurgeryChecklistStatus status,
        UUID evidenceReferenceId, @PositiveOrZero Long evidenceRevision) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected checklist field");
    }

    @AssertTrue(message = "Evidence does not match the checklist status")
    public boolean isEvidenceConsistent() {
        if (status == null) return true;
        if (status == SurgeryChecklistStatus.NOT_APPLICABLE) return false;
        if (status == SurgeryChecklistStatus.SATISFIED
                && (evidenceReferenceId == null || evidenceRevision == null)) return false;
        if (status == SurgeryChecklistStatus.PENDING
                && (evidenceReferenceId != null || evidenceRevision != null)) return false;
        return evidenceRevision == null || evidenceReferenceId != null;
    }

    public UpdateChecklistItemUseCase.Command toCommand(UUID caseId, String key,
            SurgeryActorIdentity identity, String correlation) {
        return new UpdateChecklistItemUseCase.Command(caseId, checklistItemId, expectedCaseRevision,
                expectedSnapshotRevision, expectedItemRevision, status, evidenceReferenceId, evidenceRevision,
                key, SurgeryAuditActor.human(identity.accountId(), identity.verifiedStaffId()), correlation);
    }
}
