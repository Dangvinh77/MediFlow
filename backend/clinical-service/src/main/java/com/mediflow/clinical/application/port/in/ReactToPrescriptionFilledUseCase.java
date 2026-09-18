package com.mediflow.clinical.application.port.in;

import com.mediflow.clinical.application.dto.command.PrescriptionFilledCommand;

/** Handles Pharmacy confirmation that a prescription was dispensed. */
public interface ReactToPrescriptionFilledUseCase {

    void onPrescriptionFilled(PrescriptionFilledCommand command);
}
