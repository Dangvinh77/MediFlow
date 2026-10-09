package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;
import java.util.UUID;

public interface IssueSurgeryChargeUseCase {
    UUID issue(SurgeryChargeCommand command);
}
