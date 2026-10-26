package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.PrescribingContextDTO;
import java.util.UUID;

public interface GetPrescribingContextUseCase {
    PrescribingContextDTO get(UUID recordId, ActorIdentity actor, String correlationId);
}
