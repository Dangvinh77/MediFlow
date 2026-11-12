package com.mediflow.clinical.application.port.out;

import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.VitalSignObservation;

public interface VitalSignRepositoryPort {
    VitalSignObservation insert(VitalSignObservation observation);
    /** Newest first. */
    List<VitalSignObservation> listByRegistration(UUID registrationId);
}
