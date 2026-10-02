package com.mediflow.pharmacy.application.port.out;

import java.util.UUID;

import com.mediflow.pharmacy.domain.model.AdmissionMedicationContext;

public interface AdmissionMedicationContextPort {
    /** Insert-if-absent; same event with different bytes must throw, never silently skip. */
    boolean claim(UUID eventId, String eventFingerprint);

    /** Seed if absent then lock, in the caller's transaction (also used for future dispense fencing). */
    AdmissionMedicationContext lockOrCreate(UUID admissionId, UUID patientId);

    void save(AdmissionMedicationContext context);
}
