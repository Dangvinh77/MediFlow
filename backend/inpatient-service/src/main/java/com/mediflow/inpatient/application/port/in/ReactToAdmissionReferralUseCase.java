package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;

public interface ReactToAdmissionReferralUseCase {
    void onAdmissionRequested(AdmissionRequestedCommand command);
}
