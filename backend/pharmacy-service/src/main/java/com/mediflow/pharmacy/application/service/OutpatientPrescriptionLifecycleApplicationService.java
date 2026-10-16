package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.*;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.DispenseActor;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** No outer transaction: a failed stock transaction must roll back before failure re-evaluation. */
public class OutpatientPrescriptionLifecycleApplicationService implements OutpatientPrescriptionLifecycleUseCase {
    private static final Set<String> STOCK_FAILURES = Set.of(
            "RESERVATION_EXPIRED", "DRUG_OUT_OF_STOCK", "DRUG_EXPIRED");
    private final PrescriptionRepositoryPort prescriptions;
    private final DispenseCarePrescriptionUseCase dispense;
    private final RecordCareStockFailureUseCase failures;
    private final CancelCarePrescriptionUseCase cancel;
    private final ExpireCarePrescriptionUseCase expiry;

    public OutpatientPrescriptionLifecycleApplicationService(PrescriptionRepositoryPort prescriptions,
            DispenseCarePrescriptionUseCase dispense, RecordCareStockFailureUseCase failures,
            CancelCarePrescriptionUseCase cancel, ExpireCarePrescriptionUseCase expiry) {
        this.prescriptions = prescriptions;
        this.dispense = dispense;
        this.failures = failures;
        this.cancel = cancel;
        this.expiry = expiry;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public DispenseDTO dispense(UUID prescriptionId, ActorIdentity actor, String correlationId) {
        DispenseActor audit = requireDispenseActor(actor);
        requireOutpatient(prescriptionId);
        String trace = normalizeTrace(correlationId);
        try {
            return dispense.execute(prescriptionId, audit, trace);
        } catch (BusinessRuleException exception) {
            if (!STOCK_FAILURES.contains(exception.getCode())) {
                throw exception; // Authorization, evidence and infrastructure errors are not stock failures.
            }
            // The proxied writer has exited/rolled back; this port starts its own REQUIRES_NEW transaction.
            var outcome = failures.recordStockFailure(prescriptionId, audit, trace);
            if (outcome.isPresent()) {
                return outcome.get();
            }
            throw exception; // Stock recovered or another terminal operation won; do not invent failure.
        }
    }

    @Override
    public CancelPrescriptionResult cancel(CancelPrescriptionCommand command) {
        if (command == null) throw new IllegalArgumentException("Cancellation command is required");
        var actor = command.actor();
        if (!actor.isAdministrator() && (!"DOCTOR".equals(actor.role()) || actor.staffId() == null)) {
            throw new com.mediflow.pharmacy.domain.exception.PrescriptionCancellationForbiddenException(
                    "Only an authenticated doctor or administrator may cancel");
        }
        requireOutpatient(command.prescriptionId());
        return cancel.cancelCare(command); // Kernel rechecks ownership/state under aggregate locks.
    }

    @Override
    public int expire(UUID prescriptionId, String correlationId) {
        requireOutpatient(prescriptionId);
        return expiry.expireCare(prescriptionId, normalizeTrace(correlationId));
    }

    private void requireOutpatient(UUID prescriptionId) {
        if (prescriptionId == null) throw new IllegalArgumentException("Prescription identity is required");
        var prescription = prescriptions.findById(prescriptionId)
                .orElseThrow(() -> new PrescriptionNotFoundException("Prescription not found"));
        if (prescription.getCareContext().contractVersion() != CareContractVersion.V1
                || prescription.getCareContext().careContext() != CareContext.OUTPATIENT) {
            throw new PrescriptionRuleException("PHARMACY_CARE_CONTEXT_INVALID",
                    "MVP lifecycle requires a persisted V1 outpatient prescription");
        }
        // Classification is not permission: each mutation kernel rechecks mode/state after locking.
    }

    private DispenseActor requireDispenseActor(ActorIdentity actor) {
        if (actor == null || (!actor.isAdministrator() && !"PHARMACIST".equals(actor.role()))) {
            throw new DispenseAuthorizationException("PHARMACY_DISPENSE_ACTOR_REQUIRED",
                    "An authenticated pharmacist or administrator is required");
        }
        try {
            return actor.dispenseAuditActor();
        } catch (IllegalStateException missingStaff) {
            throw new DispenseAuthorizationException("PHARMACY_DISPENSE_ACTOR_REQUIRED",
                    "A pharmacist requires a signed staff identity");
        }
    }

    private String normalizeTrace(String trace) {
        return trace == null || trace.isBlank() ? UUID.randomUUID().toString() : trace.trim();
    }
}
