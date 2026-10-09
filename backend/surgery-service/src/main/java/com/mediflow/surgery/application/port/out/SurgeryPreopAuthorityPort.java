package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.SurgeryCase;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** No provider is installed until approved evidence, care relationship and legal policy exist.
 * Implementations must reauthorize every attempt (including receipt replay), independently verify
 * exact evidence/order revisions or signer/guardian/witness/document/recorder policy, and distinguish
 * confirmed denial from unavailable sources. Methods are invoked only outside transactions.
 */
public interface SurgeryPreopAuthorityPort {
    Approval approveChecklist(Context context, UpdateChecklistItemUseCase.Command command, String fingerprint);
    Approval approveConsent(Context context, ManageSurgeryConsentUseCase.SignCommand command, String fingerprint);

    record Context(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
                   CareEpisode careEpisode, String procedureCode, long caseRevision) {
        public Context {
            Objects.requireNonNull(surgeryCaseId); Objects.requireNonNull(surgeryRequestId);
            Objects.requireNonNull(patientId); Objects.requireNonNull(departmentId);
            Objects.requireNonNull(careEpisode); Objects.requireNonNull(procedureCode);
            if (caseRevision < 0) throw new IllegalArgumentException("Invalid authority context revision");
        }
        public static Context from(SurgeryCase surgeryCase) {
            return new Context(surgeryCase.getSurgeryCaseId(), surgeryCase.getSurgeryRequestId(),
                    surgeryCase.getPatientId(), surgeryCase.getDepartmentId(), surgeryCase.getCareEpisode(),
                    surgeryCase.getProcedureCode(), surgeryCase.getRevision());
        }
    }

    record Approval(String fingerprint, Instant observedAt, Instant validUntil) {
        public Approval {
            if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid pre-op authority fingerprint");
            Objects.requireNonNull(observedAt); Objects.requireNonNull(validUntil);
            if (!validUntil.isAfter(observedAt)) throw new IllegalArgumentException("Invalid authority interval");
        }
    }
}
