package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import java.util.Optional;
import java.util.UUID;

/** Stores held V1 bytes atomically with the caller's transaction. Caller must lock the prescription. */
public interface PrescriptionCareEventWriterPort {
    void storeHeld(PrescriptionCareEvent event);
    Optional<PrescriptionCareEvent> findHeld(UUID prescriptionId, PrescriptionCareEvent.EventType type);
}
