package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.OutpatientCancellationCommand;
import com.mediflow.billing.application.dto.response.OutpatientAdjustmentDTO;
import java.util.UUID;

public interface OutpatientCancellationRepositoryPort {
    void receive(OutpatientCancellationCommand command);
    void recoverDuringIssuance(String sourceType, UUID sourceId);
    OutpatientAdjustmentDTO get(String sourceType, UUID sourceId);
}
