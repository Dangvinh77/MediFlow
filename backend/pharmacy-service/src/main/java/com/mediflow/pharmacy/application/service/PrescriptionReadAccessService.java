package com.mediflow.pharmacy.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.SearchPrescriptionsUseCase;
import com.mediflow.pharmacy.application.port.out.ClinicalReadDecisionPort;
import java.util.UUID;

/**
 * Applies Clinical's read decision to prescription reads. Deliberately not transactional: the
 * Clinical call must not run while a Pharmacy database transaction is held open.
 */
public class PrescriptionReadAccessService {
    private static final String DENIED = "RECORD_ACCESS_DENIED";
    private static final String PHARMACIST = "PHARMACIST";
    private static final String DOCTOR = "DOCTOR";

    private final GetPrescriptionUseCase prescriptions;
    private final SearchPrescriptionsUseCase searches;
    private final ClinicalReadDecisionPort decisions;
    private final boolean enabled;

    public PrescriptionReadAccessService(GetPrescriptionUseCase prescriptions, SearchPrescriptionsUseCase searches,
            ClinicalReadDecisionPort decisions, boolean enabled) {
        this.prescriptions = prescriptions;
        this.searches = searches;
        this.decisions = decisions;
        this.enabled = enabled;
    }

    public PrescriptionDTO getById(UUID prescriptionId, ActorIdentity actor, String correlationId) {
        PrescriptionDTO prescription = prescriptions.getPrescriptionById(prescriptionId);
        if (!enabled) {
            return prescription;
        }
        requireActor(actor);
        if (!PHARMACIST.equals(actor.role())) {
            requireAllowed(prescription.patientId(), actor, correlationId);
        }
        return prescription;
    }

    public PageResult<PrescriptionDTO> search(String keyword, UUID patientId, String status, PageQuery page,
            ActorIdentity actor, String correlationId) {
        if (!enabled) {
            return searches.search(keyword, patientId, status, page);
        }
        requireActor(actor);
        if (PHARMACIST.equals(actor.role())) {
            return searches.search(keyword, patientId, status, page);
        }
        if (patientId != null) {
            requireAllowed(patientId, actor, correlationId);
            return searches.search(keyword, patientId, status, page);
        }
        if (DOCTOR.equals(actor.role())) {
            if (actor.staffId() == null) {
                throw new ForbiddenOperationException(DENIED, "A staff identity is required to list prescriptions");
            }
            return searches.search(keyword, null, actor.staffId(), status, page);
        }
        return searches.search(keyword, null, status, page);
    }

    private void requireActor(ActorIdentity actor) {
        if (actor == null) {
            throw new ForbiddenOperationException(DENIED, "An authenticated staff identity is required");
        }
    }

    private void requireAllowed(UUID patientId, ActorIdentity actor, String correlationId) {
        var decision = decisions.decide(patientId, actor, correlationId);
        if (!decision.allowed()) {
            throw new ForbiddenOperationException(decision.denialCode() == null ? DENIED : decision.denialCode(),
                    "Reading these prescriptions is not permitted");
        }
    }
}
