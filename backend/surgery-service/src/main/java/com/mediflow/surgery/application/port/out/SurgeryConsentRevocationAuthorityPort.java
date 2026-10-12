package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;

/**
 * Verify the requesting recorder's right to withdraw this exact consent/document in the current
 * care context, on every attempt including replay. Login roles and signature recording authority
 * are not withdrawal authority. Providers must distinguish denial from source unavailability.
 * Invoked outside transactions; no positive production provider or universal legal policy exists.
 */
public interface SurgeryConsentRevocationAuthorityPort {
    SurgeryPreopAuthorityPort.Approval approve(SurgeryPreopAuthorityPort.Context context,
            SurgeryConsentRecord currentConsent, ManageSurgeryConsentUseCase.RevokeCommand command,
            String fingerprint);
}
