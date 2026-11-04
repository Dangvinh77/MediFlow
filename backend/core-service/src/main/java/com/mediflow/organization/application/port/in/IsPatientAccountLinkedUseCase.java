package com.mediflow.organization.application.port.in;

import java.util.UUID;

/** Public account-link query for Core's co-hosted Patient context. */
public interface IsPatientAccountLinkedUseCase {
    boolean isLinked(UUID patientId);
}
