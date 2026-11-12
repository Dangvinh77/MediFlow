package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import java.util.UUID;

/** Asks Clinical whether staff may read a patient's prescriptions. Fails closed when Clinical cannot answer. */
public interface ClinicalReadDecisionPort {
    ClinicalReadDecision decide(UUID patientId, ActorIdentity actor, String correlationId);

    record ClinicalReadDecision(boolean allowed, String denialCode) {}
}
