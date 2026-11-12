package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.UpdatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;

/** Public edit contract for an exact outpatient V1 prescription before payment begins. */
public interface UpdateOutpatientPrescriptionUseCase {
    PrescriptionDTO update(UpdatePrescriptionCommand command);
}
