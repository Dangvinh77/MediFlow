package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

/** Retries a durable update after a process restart or an uncertain upstream response. */
public interface RecoverPrescriptionRevisionsUseCase {
    void recover(UUID operationId);
}
