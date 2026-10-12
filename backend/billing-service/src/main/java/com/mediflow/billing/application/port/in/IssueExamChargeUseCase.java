package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import java.util.UUID;

public interface IssueExamChargeUseCase {
    UUID issue(ExamChargeCommand command);
}
