package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;

/** Offline/local V1 boundary; no live subscription or dispense authorization is activated. */
public interface ProjectAdmissionLifecycleUseCase {
    void project(AdmissionLifecycleCommand command);
}
