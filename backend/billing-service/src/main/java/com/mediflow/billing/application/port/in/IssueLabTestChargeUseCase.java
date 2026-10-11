package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import java.util.UUID;

public interface IssueLabTestChargeUseCase {
    UUID issue(LabTestChargeCommand command);
}
