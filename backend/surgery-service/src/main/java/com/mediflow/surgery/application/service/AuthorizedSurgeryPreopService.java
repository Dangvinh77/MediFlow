package com.mediflow.surgery.application.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryPreopUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryPreopAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Fresh authority precedes locks; existing transactional kernels preserve receipts and atomic effects. */
public final class AuthorizedSurgeryPreopService implements RecordSurgeryPreopUseCase {
    private static final Duration MAX_OBSERVATION_AGE = Duration.ofSeconds(30);
    private final SurgeryCaseRepositoryPort cases;
    private final UpdateChecklistItemUseCase checklist;
    private final ManageSurgeryConsentUseCase consents;
    private final SurgeryPreopAuthorityPort authority;
    private final SurgeryClockPort clock;
    private final SurgeryUnitOfWorkPort transactions;

    public AuthorizedSurgeryPreopService(SurgeryCaseRepositoryPort cases, UpdateChecklistItemUseCase checklist,
            ManageSurgeryConsentUseCase consents, SurgeryPreopAuthorityPort authority,
            SurgeryClockPort clock, SurgeryUnitOfWorkPort transactions) {
        this.cases = Objects.requireNonNull(cases); this.checklist = Objects.requireNonNull(checklist);
        this.consents = Objects.requireNonNull(consents); this.authority = Objects.requireNonNull(authority);
        this.clock = Objects.requireNonNull(clock); this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public SurgeryCommandOutcome updateChecklist(UpdateChecklistItemUseCase.Command command) {
        Objects.requireNonNull(command); requireRecorder(command.actor());
        var context = context(command.surgeryCaseId());
        String fingerprint = fingerprint(context, command.actor(), "UPDATE_CHECKLIST_ITEM",
                command.checklistItemId().toString(), Long.toString(command.expectedCaseRevision()),
                Long.toString(command.expectedSnapshotRevision()), Long.toString(command.expectedItemRevision()),
                command.status().name(), text(command.evidenceReferenceId()), text(command.evidenceRevision()),
                command.idempotencyKey());
        var proof = transactions.outside(() -> authority.approveChecklist(context, command, fingerprint));
        return apply(context, proof, fingerprint, () -> checklist.update(command));
    }

    @Override
    public SurgeryCommandOutcome recordConsent(ManageSurgeryConsentUseCase.SignCommand command) {
        Objects.requireNonNull(command); requireRecorder(command.recordedBy());
        var context = context(command.surgeryCaseId());
        String fingerprint = fingerprint(context, command.recordedBy(), "SIGN_CONSENT",
                Long.toString(command.expectedCaseRevision()), command.consentType().name(),
                command.signerId().toString(), command.signerType().name(), text(command.evidenceDocumentId()),
                command.idempotencyKey());
        var proof = transactions.outside(() -> authority.approveConsent(context, command, fingerprint));
        return apply(context, proof, fingerprint, () -> consents.sign(command));
    }

    private SurgeryPreopAuthorityPort.Context context(UUID caseId) {
        return transactions.read(() -> SurgeryPreopAuthorityPort.Context.from(cases.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("SURGERY_CASE_NOT_FOUND", "Surgery case not found"))));
    }

    private SurgeryCommandOutcome apply(SurgeryPreopAuthorityPort.Context observed,
            SurgeryPreopAuthorityPort.Approval proof, String fingerprint, Supplier<SurgeryCommandOutcome> action) {
        // NOT_SUPPORTED also suspends an ambient caller transaction: preflight must never use its locks.
        return transactions.outside(() -> transactions.write(() -> {
            var locked = cases.lockById(observed.surgeryCaseId()).orElseThrow(SurgeryRevisionConflictException::new);
            if (!SurgeryPreopAuthorityPort.Context.from(locked).equals(observed))
                throw new SurgeryRevisionConflictException();
            requireFresh(proof, fingerprint);
            var outcome = action.get();
            // The kernel can also wait for a receipt or resource mutex. Failure here rolls back
            // its child/audit/receipt/release/HELD writes in this same outer transaction.
            requireFresh(proof, fingerprint);
            return outcome;
        }));
    }

    private void requireFresh(SurgeryPreopAuthorityPort.Approval proof, String fingerprint) {
        var now = clock.now();
        if (proof == null || !fingerprint.equals(proof.fingerprint()) || proof.observedAt().isAfter(now)
                || Duration.between(proof.observedAt(), now).compareTo(MAX_OBSERVATION_AGE) > 0
                || !now.isBefore(proof.validUntil()))
            throw new UpstreamUnavailableException("Pre-op authority proof is unavailable or stale");
    }

    private static void requireRecorder(SurgeryAuditActor actor) {
        if (actor.actorType() != SurgeryActorType.HUMAN || actor.accountId() == null || actor.verifiedStaffId() == null)
            throw new com.mediflow.surgery.domain.exception.SurgeryRuleException(
                    "SURGERY_VERIFIED_RECORDER_REQUIRED", "Verified human staff recorder required");
    }

    private static String fingerprint(SurgeryPreopAuthorityPort.Context c, SurgeryAuditActor actor,
            String action, String... values) {
        String context = SurgeryCommandReceipts.fingerprint(action, c.surgeryCaseId().toString(),
                c.surgeryRequestId().toString(), c.patientId().toString(), c.departmentId().toString(),
                c.careEpisode().type().name(), c.careEpisode().episodeId().toString(),
                text(c.careEpisode().admissionId()), text(c.careEpisode().recordId()), c.procedureCode(),
                Long.toString(c.caseRevision()), actor.accountId().toString(), actor.verifiedStaffId().toString());
        return SurgeryCommandReceipts.fingerprint(context, SurgeryCommandReceipts.fingerprint(values));
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }
}
