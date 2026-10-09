package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import java.util.UUID;

/** Internal V1 caller with real Clinical context preflight; public activation is separate. */
public interface CreateContextCheckedPrescriptionUseCase {
    UUID createWithContext(UUID commandId, CreatePrescriptionCommand command);
}
