package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;
import com.mediflow.billing.application.dto.response.PrescriptionRevisionResult;
public interface RevisePrescriptionChargeUseCase {
    PrescriptionRevisionResult prepare(PrescriptionRevisionCommand command);
    PrescriptionRevisionResult commit(PrescriptionRevisionCommand command);
    PrescriptionRevisionResult abort(PrescriptionRevisionCommand command);
    PrescriptionRevisionResult applyRevisedEvent(PrescriptionRevisionCommand command);
}
