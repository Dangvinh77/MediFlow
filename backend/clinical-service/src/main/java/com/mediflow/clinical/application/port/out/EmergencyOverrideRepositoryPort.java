package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.domain.model.EmergencyOverride;

public interface EmergencyOverrideRepositoryPort {
    EmergencyOverride save(EmergencyOverride override);
}
