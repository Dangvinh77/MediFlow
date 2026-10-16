package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import java.util.Optional;
import java.util.UUID;

/** Stores immutable V1 proof in the caller's transaction. Caller must lock the prescription. */
public interface PrescriptionCareEventWriterPort {
    /** Historical name retained: default HELD; explicitly enrolled new outpatient flows may deliver. */
    void storeHeld(PrescriptionCareEvent event);
    /** Returns original business proof whether HELD, pending, quarantined or already published. */
    Optional<PrescriptionCareEvent> findHeld(UUID prescriptionId, PrescriptionCareEvent.EventType type);
}
