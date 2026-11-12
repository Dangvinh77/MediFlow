package com.mediflow.pharmacy.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.SearchPrescriptionsUseCase;
import com.mediflow.pharmacy.application.port.out.ClinicalReadDecisionPort;
import java.util.List;
import java.util.UUID;

/**
 * Applies Clinical's read decision to prescription reads. Deliberately not transactional: the
 * Clinical call must not run while a Pharmacy database transaction is held open.
 */
public class PrescriptionReadAccessService {
    private static final String DENIED = "RECORD_ACCESS_DENIED";
    private static final String PURPOSE_REQUIRED = "RECORD_ACCESS_PURPOSE_REQUIRED";
    private static final String DECLARE_PURPOSE =
            "Bạn chưa có căn cứ xem đơn thuốc này. Hãy mở hồ sơ khám của người bệnh và khai báo mục đích đọc.";
    private static final String NOT_PERMITTED = "Bạn không có quyền xem đơn thuốc này.";
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
        return searches.search(keyword, null, status, page).map(PrescriptionReadAccessService::withoutMedicationLines);
    }

    /** An unfiltered list has no patient to declare against, so it carries work-queue fields only. */
    private static PrescriptionDTO withoutMedicationLines(PrescriptionDTO p) {
        return new PrescriptionDTO(p.prescriptionId(), p.recordId(), p.patientId(), p.doctorId(), p.departmentId(),
                p.prescribedDate(), p.totalAmount(), List.of(), p.status(), p.dispenseStatus(), p.paymentConfirmed(),
                p.cancelledAt(), p.cancelledBy(), p.cancellationReason(), p.createdAt(), p.updatedAt(), p.revision());
    }

    private void requireActor(ActorIdentity actor) {
        if (actor == null) {
            throw new ForbiddenOperationException(DENIED, "An authenticated staff identity is required");
        }
    }

    private void requireAllowed(UUID patientId, ActorIdentity actor, String correlationId) {
        var decision = decisions.decide(patientId, actor, correlationId);
        if (!decision.allowed()) {
            throw PURPOSE_REQUIRED.equals(decision.denialCode())
                    ? new ForbiddenOperationException(PURPOSE_REQUIRED, DECLARE_PURPOSE)
                    : new ForbiddenOperationException(decision.denialCode() == null ? DENIED : decision.denialCode(), NOT_PERMITTED);
        }
    }
}
