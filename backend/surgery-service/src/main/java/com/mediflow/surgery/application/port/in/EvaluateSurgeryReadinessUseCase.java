package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;

public interface EvaluateSurgeryReadinessUseCase {
    SurgeryCommandOutcome evaluate(SurgeryLifecycleCommand command);
}
