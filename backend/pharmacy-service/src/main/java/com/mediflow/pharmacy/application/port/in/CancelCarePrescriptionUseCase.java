package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;

/** V1 cancellation primitive; the gated outpatient facade is separate from the legacy writer. */
public interface CancelCarePrescriptionUseCase {
    CancelPrescriptionResult cancelCare(CancelPrescriptionCommand command);
}
