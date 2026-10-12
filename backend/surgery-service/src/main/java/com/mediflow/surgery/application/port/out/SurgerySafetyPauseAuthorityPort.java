package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgerySafetyPause;

/** Verify approved policy, actual observation, item/applicability sources and each participant.
 * No production provider is installed; returning the caller's IDs is not source verification.
 */
public interface SurgerySafetyPauseAuthorityPort {
    SurgeryPreopAuthorityPort.Approval approve(SurgeryPreopAuthorityPort.Context context,
            SurgerySafetyPause proposal, String contextFingerprint);
}
