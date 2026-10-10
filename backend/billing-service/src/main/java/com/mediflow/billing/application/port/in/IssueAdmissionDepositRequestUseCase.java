package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import java.util.UUID;

public interface IssueAdmissionDepositRequestUseCase {
    UUID issue(AdmissionDepositRequestCommand command);
}
