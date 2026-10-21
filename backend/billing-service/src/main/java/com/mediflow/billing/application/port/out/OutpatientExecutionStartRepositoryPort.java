package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand;
import com.mediflow.billing.application.dto.response.OutpatientExecutionStartDTO;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface OutpatientExecutionStartRepositoryPort {
    Optional<UUID> lockAccount(UUID clearanceId);
    Optional<OutpatientExecutionStartDTO> find(UUID operationId);
    OutpatientExecutionStartDTO save(UUID clearanceId, UUID accountId, StartOutpatientExecutionCommand command, Instant startedAt);
}
