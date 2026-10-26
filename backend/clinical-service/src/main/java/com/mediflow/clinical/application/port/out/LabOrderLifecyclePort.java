package com.mediflow.clinical.application.port.out;

import java.util.UUID;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;

public interface LabOrderLifecyclePort {
    LabOrderLifecycleSnapshot lifecycle(UUID sourceOrderId, String correlationId);
}
