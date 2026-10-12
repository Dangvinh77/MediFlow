package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;

/** Authorized internal boundary; the persistence kernel alone does not grant revocation rights. */
public interface RevokeSurgeryConsentUseCase {
    SurgeryCommandOutcome revoke(ManageSurgeryConsentUseCase.RevokeCommand command);
}
