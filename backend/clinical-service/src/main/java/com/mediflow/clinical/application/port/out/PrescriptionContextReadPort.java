package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.dto.response.PrescriptionContextDTO;
import java.util.UUID;

public interface PrescriptionContextReadPort {
    PrescriptionContextDTO find(UUID recordId);
}
