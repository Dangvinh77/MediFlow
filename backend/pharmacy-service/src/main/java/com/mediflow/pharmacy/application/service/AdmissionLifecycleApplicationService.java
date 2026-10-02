package com.mediflow.pharmacy.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;
import com.mediflow.pharmacy.application.port.in.ProjectAdmissionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.AdmissionMedicationContextPort;

@Service
public class AdmissionLifecycleApplicationService implements ProjectAdmissionLifecycleUseCase {
    private final AdmissionMedicationContextPort contexts;

    public AdmissionLifecycleApplicationService(AdmissionMedicationContextPort contexts) {
        this.contexts = contexts;
    }

    @Override
    @Transactional
    public void project(AdmissionLifecycleCommand command) {
        if (!contexts.claim(command.eventId(), command.eventFingerprint())) {
            return;
        }
        var fact = command.fact();
        var current = contexts.lockOrCreate(fact.admissionId(), fact.patientId());
        var updated = current.apply(fact);
        if (updated != current) {
            contexts.save(updated);
        }
    }
}
