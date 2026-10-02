package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;

/** Internal writer hook only; requires the same transaction as the future V1 lifecycle mutation. */
public interface CapturePrescriptionCareEventUseCase {
    void capture(UUID prescriptionId, UUID eventId, EventType type, String correlationId);
}
