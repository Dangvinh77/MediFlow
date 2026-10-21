package com.mediflow.pharmacy.application.port.out;

import java.util.List;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.PrescriptionClearance;

/** All methods join the caller's transaction. Mutations lock the prescription before the target fence. */
public interface PrescriptionClearancePort {
    boolean claim(UUID eventId, String eventFingerprint, PrescriptionClearance clearance);
    void lockTarget(UUID prescriptionId);
    void store(PrescriptionClearance clearance, boolean targetVerified);
    List<PrescriptionClearance> findByTargetForUpdate(UUID prescriptionId);
    /** Read-only confirmed projection; does not lock, verify a target or authorize dispensing. */
    List<PrescriptionClearance> findVerifiedByTarget(UUID prescriptionId);
    void markVerified(UUID clearanceId);
}
