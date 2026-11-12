package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.pharmacy.application.dto.command.UpdatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.model.PrescriptionRevisionOperation;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.RecoverPrescriptionRevisionsUseCase;
import com.mediflow.pharmacy.application.port.in.UpdateOutpatientPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRevisionBillingPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates Clinical proof, Billing's payment fence and durable Pharmacy revision receipts. */
public final class PublicOutpatientPrescriptionRevisionService
        implements UpdateOutpatientPrescriptionUseCase, RecoverPrescriptionRevisionsUseCase {
    private static final Logger log = LoggerFactory.getLogger(PublicOutpatientPrescriptionRevisionService.class);

    private final OutpatientPrescriptionContextPort contexts;
    private final PrescriptionIdentityPort identities;
    private final PrescriptionRepositoryPort prescriptions;
    private final CarePrescriptionRevisionTransactionService transactions;
    private final PrescriptionRevisionBillingPort billing;
    private final GetPrescriptionUseCase reader;
    private final Clock clock;

    public PublicOutpatientPrescriptionRevisionService(OutpatientPrescriptionContextPort contexts,
            PrescriptionIdentityPort identities, PrescriptionRepositoryPort prescriptions,
            CarePrescriptionRevisionTransactionService transactions, PrescriptionRevisionBillingPort billing,
            GetPrescriptionUseCase reader, Clock clock) {
        this.contexts = contexts;
        this.identities = identities;
        this.prescriptions = prescriptions;
        this.transactions = transactions;
        this.billing = billing;
        this.reader = reader;
        this.clock = clock;
    }

    @Override
    public PrescriptionDTO update(UpdatePrescriptionCommand command) {
        var existing = transactions.find(command.operationId());
        PrescriptionRevisionOperation operation;
        if (existing.isPresent()) {
            operation = existing.get();
            transactions.verifyIntent(operation, command);
        } else {
            var proof = authorizeAndObserve(command.prescriptionId(), command.actor(), command.correlationId());
            operation = transactions.begin(command, proof.context(), proof.identity());
        }
        return process(operation.operationId());
    }

    @Override
    public void recover(UUID operationId) {
        process(operationId);
    }

    private PrescriptionDTO process(UUID operationId) {
        var operation = transactions.get(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.COMMITTED) {
            return reader.getPrescriptionById(operation.prescriptionId());
        }
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTED) {
            throw replayFailure(operation);
        }
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTING) {
            return finishAbort(operation);
        }
        if (operation.status() == PrescriptionRevisionOperation.Status.PENDING) {
            try {
                billing.prepare(operation.operationId(), operation.billingPayload(), operation.correlationId());
                operation = transactions.markPrepared(operationId);
            } catch (BusinessRuleException rejected) {
                var aborting = transactions.markAborting(operationId, rejected.getCode(), safeMessage(rejected));
                if (aborting.status() == PrescriptionRevisionOperation.Status.ABORTING) {
                    return finishAbort(aborting);
                }
                if (aborting.status() == PrescriptionRevisionOperation.Status.APPLIED
                        || aborting.status() == PrescriptionRevisionOperation.Status.COMMITTED) {
                    return finishCommit(aborting);
                }
                throw rejected;
            }
        }
        operation = transactions.get(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTING) return finishAbort(operation);
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTED) throw replayFailure(operation);
        if (operation.status() == PrescriptionRevisionOperation.Status.PREPARED) {
            try {
                var proof = authorizeAndObserve(operation.prescriptionId(), operation.actor(), operation.correlationId());
                transactions.apply(operationId, proof.context(), proof.identity());
            } catch (RuntimeException failure) {
                if (isDefinitiveBusinessFailure(failure)) {
                    var aborting = transactions.markAborting(operationId, code(failure), safeMessage(failure));
                    if (aborting.status() == PrescriptionRevisionOperation.Status.ABORTING) return finishAbort(aborting);
                    if (aborting.status() == PrescriptionRevisionOperation.Status.APPLIED
                            || aborting.status() == PrescriptionRevisionOperation.Status.COMMITTED) return finishCommit(aborting);
                    if (aborting.status() == PrescriptionRevisionOperation.Status.ABORTED) throw replayFailure(aborting);
                } else {
                    // A lost local commit response is resolved from the durable row, never by aborting Billing blindly.
                    var durable = transactions.get(operationId);
                    if (durable.status() == PrescriptionRevisionOperation.Status.APPLIED
                            || durable.status() == PrescriptionRevisionOperation.Status.COMMITTED) return finishCommit(durable);
                    throw failure instanceof PharmacyUpstreamUnavailableException
                            ? failure : new PharmacyUpstreamUnavailableException();
                }
            }
        }
        operation = transactions.get(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTING) return finishAbort(operation);
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTED) throw replayFailure(operation);
        return finishCommit(operation);
    }

    private PrescriptionDTO finishAbort(PrescriptionRevisionOperation operation) {
        try {
            billing.abort(operation.operationId(), operation.billingPayload(), operation.correlationId());
        } catch (RuntimeException unavailable) {
            throw unavailable instanceof PharmacyUpstreamUnavailableException
                    ? unavailable : new PharmacyUpstreamUnavailableException();
        }
        var aborted = transactions.markAborted(operation.operationId());
        if (aborted.status() == PrescriptionRevisionOperation.Status.APPLIED
                || aborted.status() == PrescriptionRevisionOperation.Status.COMMITTED) return finishCommit(aborted);
        throw replayFailure(aborted);
    }

    private PrescriptionDTO finishCommit(PrescriptionRevisionOperation operation) {
        if (operation.status() == PrescriptionRevisionOperation.Status.APPLIED) {
            try {
                billing.commit(operation.operationId(), operation.billingPayload(), operation.correlationId());
                operation = transactions.markCommitted(operation.operationId());
            } catch (RuntimeException pending) {
                // Pharmacy already committed the snapshot and outbox event; the scheduled reconciler retries commit.
                log.warn("Billing revision commit is pending for operation {}", operation.operationId());
            }
        }
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTING) return finishAbort(operation);
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTED) throw replayFailure(operation);
        return reader.getPrescriptionById(operation.prescriptionId());
    }

    private Proof authorizeAndObserve(UUID prescriptionId,
            com.mediflow.pharmacy.application.dto.command.ActorIdentity actor, String correlationId) {
        Prescription prescription = prescriptions.findById(prescriptionId)
                .orElseThrow(() -> new PrescriptionNotFoundException("Prescription not found: " + prescriptionId));
        requireSourceAndActor(prescription, actor);
        var startedAt = clock.instant();
        var context = contexts.findRecord(prescription.getRecordId(), correlationId);
        context.requireExact(prescription.getRecordId(), prescription.getPatientId(), prescription.getDoctorId(),
                prescription.getDepartmentId(), prescription.getCareContext().episode().id(), startedAt);
        var identity = identities.lookup(prescription.getPatientId(), prescription.getDoctorId(),
                prescription.getDepartmentId(), correlationId);
        identity.requireExact(prescription.getPatientId(), prescription.getDoctorId(), prescription.getDepartmentId(), startedAt);
        context.requireExact(prescription.getRecordId(), prescription.getPatientId(), prescription.getDoctorId(),
                prescription.getDepartmentId(), prescription.getCareContext().episode().id(), clock.instant());
        return new Proof(context, identity);
    }

    private static void requireSourceAndActor(Prescription prescription,
            com.mediflow.pharmacy.application.dto.command.ActorIdentity actor) {
        var care = prescription.getCareContext();
        if (care.contractVersion() != CareContractVersion.V1 || care.careContext() != CareContext.OUTPATIENT
                || care.episode() == null || care.episode().type() != CareEpisodeType.OUTPATIENT_VISIT
                || care.admissionId() != null || prescription.getRecordId() == null || care.priceCode() == null) {
            throw new com.mediflow.pharmacy.domain.exception.PrescriptionRuleException(
                    "PHARMACY_OUTPATIENT_V1_REQUIRED", "Only an exact outpatient V1 prescription can be edited");
        }
        if (prescription.getStatus() != PrescriptionStatus.ACTIVE) {
            throw new com.mediflow.pharmacy.domain.exception.PrescriptionRuleException(
                    "PRESCRIPTION_NOT_EDITABLE", "Only an active prescription can be edited");
        }
        if (actor == null || (!actor.isAdministrator() && !"DOCTOR".equals(actor.role()))) {
            throw new ForbiddenOperationException("PRESCRIPTION_EDIT_FORBIDDEN",
                    "Only the prescribing doctor or an authorized administrator may edit");
        }
        if (!actor.isAdministrator() && (actor.staffId() == null || !actor.staffId().equals(prescription.getDoctorId()))) {
            throw new ForbiddenOperationException("PRESCRIPTION_EDIT_FORBIDDEN",
                    "Doctors may only edit prescriptions they authored");
        }
        try {
            actor.auditActorId();
        } catch (IllegalStateException denied) {
            throw new ForbiddenOperationException("PRESCRIPTION_EDIT_FORBIDDEN", "A signed staff identity is required");
        }
    }

    private static BusinessRuleException replayFailure(PrescriptionRevisionOperation operation) {
        return new BusinessRuleException(operation.failureCode() == null ? "PRESCRIPTION_REVISION_ABORTED" : operation.failureCode(),
                operation.failureMessage() == null ? "Prescription revision was not applied" : operation.failureMessage());
    }

    private static boolean isDefinitiveBusinessFailure(RuntimeException failure) {
        return failure instanceof BusinessRuleException || failure instanceof DuplicateResourceException
                || failure instanceof ForbiddenOperationException || failure instanceof ResourceNotFoundException;
    }

    private static String code(RuntimeException failure) {
        if (failure instanceof BusinessRuleException business) return business.getCode();
        if (failure instanceof DuplicateResourceException duplicate) return duplicate.getCode();
        if (failure instanceof ForbiddenOperationException forbidden) return forbidden.getCode();
        return "PRESCRIPTION_REVISION_ABORTED";
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return "Prescription revision was not applied";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record Proof(OutpatientPrescriptionContextPort.Observation context,
            PrescriptionIdentityPort.Observation identity) { }
}
