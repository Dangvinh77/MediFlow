package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;

/** Authority-backed boundary, separate from the internal persistence kernels. */
public interface RecordSurgeryPreopUseCase {
    SurgeryCommandOutcome updateChecklist(UpdateChecklistItemUseCase.Command command);
    SurgeryCommandOutcome recordConsent(ManageSurgeryConsentUseCase.SignCommand command);
}
