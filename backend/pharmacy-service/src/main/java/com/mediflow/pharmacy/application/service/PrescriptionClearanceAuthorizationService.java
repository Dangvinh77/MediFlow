package com.mediflow.pharmacy.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.port.out.PrescriptionClearancePort;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionClearance;

/**
 * Local transaction primitive, NOT a reusable permission token or a public authorization API.
 * The internal V1 writer must already hold the prescription and stock locks, invoke this check
 * immediately before effects, and commit those effects in this same transaction.
 */
@Service
public class PrescriptionClearanceAuthorizationService {
    private final PrescriptionClearancePort clearances;
    private final Clock clock;

    public PrescriptionClearanceAuthorizationService(PrescriptionClearancePort clearances, Clock clock) {
        this.clearances = clearances;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PrescriptionClearance requireValidAfterLocks(Prescription prescription) {
        if (prescription == null || prescription.getPrescriptionId() == null) {
            throw new IllegalArgumentException("A persisted, locked prescription is required");
        }
        if (!prescription.isActive()) {
            throw new DispenseAuthorizationException("PRESCRIPTION_NOT_ACTIVE",
                    "A terminal prescription cannot receive dispense authorization");
        }
        clearances.lockTarget(prescription.getPrescriptionId());
        var candidates = clearances.findByTargetForUpdate(prescription.getPrescriptionId());
        // Capture time AFTER all authorization lock waits; an earlier timestamp is stale proof.
        Instant now = clock.instant();
        var grant = candidates.stream().filter(candidate -> candidate.matches(prescription)
                        && candidate.isValidAt(now)).findFirst()
                .orElseThrow(() -> new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED",
                        "A non-expired matching V1 prescription clearance is required"));
        clearances.markVerified(grant.clearanceId());
        return grant;
    }
}
