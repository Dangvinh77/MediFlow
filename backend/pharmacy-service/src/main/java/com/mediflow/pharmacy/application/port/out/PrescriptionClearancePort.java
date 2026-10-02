package com.mediflow.pharmacy.application.port.out;

import java.util.List;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.PrescriptionClearance;

/** All methods join the caller's transaction. Lock prescription before this target fence. */
public interface PrescriptionClearancePort {
    boolean claim(UUID eventId, String eventFingerprint, PrescriptionClearance clearance);
    void lockTarget(UUID prescriptionId);
    void store(PrescriptionClearance clearance, boolean targetVerified);
    List<PrescriptionClearance> findByTargetForUpdate(UUID prescriptionId);
    void markVerified(UUID clearanceId);
}
