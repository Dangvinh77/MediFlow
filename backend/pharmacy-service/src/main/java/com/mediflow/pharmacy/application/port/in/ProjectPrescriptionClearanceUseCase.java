package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand;

/** Grants authorization only. It does not dispense, deduct stock or publish payment events. */
public interface ProjectPrescriptionClearanceUseCase {
    void project(PrescriptionClearanceCommand command);
}
