package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;

/** Exact Inpatient producer bytes; no patient-based target discovery. */
public interface AdmissionLifecycleWirePort {
    AdmissionLifecycleCommand decode(String routingKey, byte[] body);
}
