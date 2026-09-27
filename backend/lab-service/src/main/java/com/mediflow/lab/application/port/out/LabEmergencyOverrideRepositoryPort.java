package com.mediflow.lab.application.port.out;

import com.mediflow.lab.domain.model.LabEmergencyOverride;

/** Persistence boundary for the emergency approval audit record. */
public interface LabEmergencyOverrideRepositoryPort {

    void save(LabEmergencyOverride override);
}
