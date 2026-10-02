package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;

/** Internal V1 cancellation; legacy/public adapters do not call this port before contract approval. */
public interface CancelCarePrescriptionUseCase {
    CancelPrescriptionResult cancelCare(CancelPrescriptionCommand command);
}
