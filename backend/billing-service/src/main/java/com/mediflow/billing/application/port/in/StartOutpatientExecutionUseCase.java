package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand;
import com.mediflow.billing.application.dto.response.OutpatientExecutionStartDTO;
import java.util.UUID;

public interface StartOutpatientExecutionUseCase {
    OutpatientExecutionStartDTO start(UUID clearanceId, StartOutpatientExecutionCommand command, String correlationId);
}
