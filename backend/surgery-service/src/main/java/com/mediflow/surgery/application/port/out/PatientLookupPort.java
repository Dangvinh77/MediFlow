package com.mediflow.surgery.application.port.out;

import java.util.UUID;

/** Authoritative Patient existence check for a specific command correlation. */
public interface PatientLookupPort {

    boolean exists(UUID patientId, UUID correlationId);
}
