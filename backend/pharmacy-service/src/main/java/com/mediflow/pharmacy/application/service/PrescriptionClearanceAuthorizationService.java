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
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceAuthorityPort;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.util.Optional;

/**
 * Local transaction primitive, NOT a reusable permission token or a public authorization API.
 * The internal V1 writer must already hold the prescription and stock locks, invoke this check
 * immediately before effects, and commit those effects in this same transaction.
 */
@Service
public class PrescriptionClearanceAuthorizationService {
    private final PrescriptionClearancePort clearances;
    private final Clock clock;
    private final Optional<PrescriptionClearanceAuthorityPort> currentAuthority;
    private final boolean authorityRequired;

    public PrescriptionClearanceAuthorizationService(PrescriptionClearancePort clearances, Clock clock) {
        this(clearances,clock,Optional.empty(),false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PrescriptionClearanceAuthorizationService(PrescriptionClearancePort clearances, Clock clock,
            Optional<PrescriptionClearanceAuthorityPort> currentAuthority,
            @org.springframework.beans.factory.annotation.Value("${mediflow.pharmacy.clearance-authority.required:false}") boolean authorityRequired) {
        this.clearances = clearances;
        this.clock = clock;
        this.currentAuthority=currentAuthority;
        this.authorityRequired=authorityRequired;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PrescriptionClearance requireValidAfterLocks(Prescription prescription) {
        return requireValidAfterLocks(prescription,java.util.UUID.randomUUID().toString());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PrescriptionClearance requireValidAfterLocks(Prescription prescription,String correlationId) {
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
        if(currentAuthority.isPresent()) {
            var observation=currentAuthority.get().lookup(grant.clearanceId(),correlationId);
            if(observation==null) throw new PharmacyUpstreamUnavailableException();
            observation.requireCurrent(grant,clock.instant());
        } else if(authorityRequired) throw new PharmacyUpstreamUnavailableException();
        clearances.markVerified(grant.clearanceId());
        return grant;
    }
}
