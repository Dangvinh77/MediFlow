package com.mediflow.lab.application.port.in;

import com.mediflow.lab.application.dto.response.LabOrderLifecycleDTO;
import java.util.UUID;

public interface LookupLabOrderLifecycleUseCase {
    LabOrderLifecycleDTO lookup(UUID sourceOrderId);
}
