package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;

import java.util.UUID;

public interface ManageSurgeryConsentUseCase {

    SurgeryCommandOutcome sign(SignCommand command);

    SurgeryCommandOutcome revoke(RevokeCommand command);

    record SignCommand(UUID surgeryCaseId, long expectedCaseRevision,
                       SurgeryConsentType consentType, UUID signerId,
                       SurgeryConsentSignerType signerType, UUID evidenceDocumentId,
                       String idempotencyKey, SurgeryAuditActor recordedBy, String correlationId) {
        public SignCommand {
            if (surgeryCaseId == null || expectedCaseRevision < 0 || consentType == null
                    || signerId == null || signerType == null || idempotencyKey == null
                    || idempotencyKey.isBlank() || recordedBy == null
                    || correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("Consent sign command is invalid");
            }
        }
    }

    record RevokeCommand(UUID surgeryCaseId, UUID consentId, long expectedCaseRevision,
                         String reason, String idempotencyKey,
                         SurgeryAuditActor recordedBy, String correlationId) {
        public RevokeCommand {
            if (surgeryCaseId == null || consentId == null || expectedCaseRevision < 0
                    || reason == null || reason.isBlank() || idempotencyKey == null
                    || idempotencyKey.isBlank() || recordedBy == null
                    || correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("Consent revoke command is invalid");
            }
        }
    }
}
