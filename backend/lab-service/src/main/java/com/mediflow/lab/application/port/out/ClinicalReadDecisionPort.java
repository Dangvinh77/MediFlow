package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** Asks Clinical whether a staff member may read a patient's results. Fails closed when Clinical cannot answer. */
public interface ClinicalReadDecisionPort {
    ClinicalReadDecision decide(UUID patientId, ReaderIdentity reader, String correlationId);

    record ClinicalReadDecision(boolean allowed, String denialCode, UUID departmentScope) {
        public ClinicalReadDecision(boolean allowed, String denialCode) {
            this(allowed, denialCode, null);
        }
    }
}
