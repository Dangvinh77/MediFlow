package com.mediflow.pharmacy.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand;
import com.mediflow.pharmacy.application.port.in.ProjectPrescriptionClearanceUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearancePort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;

/** Transactional authorization-only projection; opt-in intake never activates public V1 dispensing. */
@Service
public class PrescriptionClearanceApplicationService implements ProjectPrescriptionClearanceUseCase {
    private final PrescriptionRepositoryPort prescriptions;
    private final PrescriptionClearancePort clearances;

    public PrescriptionClearanceApplicationService(
            PrescriptionRepositoryPort prescriptions, PrescriptionClearancePort clearances) {
        this.prescriptions = prescriptions;
        this.clearances = clearances;
    }

    @Override
    @Transactional
    public void project(PrescriptionClearanceCommand command) {
        var grant = command.clearance();
        if (!clearances.claim(command.eventId(), command.eventFingerprint(), grant)) {
            return;
        }
        var prescription = prescriptions.findByIdForUpdate(grant.prescriptionId());
        prescription.ifPresent(grant::requireMatch);
        clearances.lockTarget(grant.prescriptionId());
        // Missing prescription is a durable pending fact, not an inferred target or an ACK loss.
        clearances.store(grant, prescription.isPresent());
    }
}
