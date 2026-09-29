package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public class SurgeryConsentApplicationService implements ManageSurgeryConsentUseCase {

    private static final String SIGN_COMMAND = "SIGN_CONSENT";
    private static final String REVOKE_COMMAND = "REVOKE_CONSENT";

    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryConsentRepositoryPort consents;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryCommandReceiptPort receipts;
    private final SurgeryClockPort clock;

    public SurgeryConsentApplicationService(SurgeryCaseRepositoryPort cases,
                                           SurgeryConsentRepositoryPort consents,
                                           SurgeryScheduleRepositoryPort schedules,
                                           SurgeryResourceReservationPort reservations,
                                           SurgeryCommandReceiptPort receipts,
                                           SurgeryClockPort surgeryClock) {
        this.cases = cases;
        this.consents = consents;
        this.schedules = schedules;
        this.reservations = reservations;
        this.receipts = receipts;
        this.clock = surgeryClock;
    }

    @Override
    @Transactional
    public SurgeryCommandOutcome sign(SignCommand command) {
        requireHuman(command == null ? null : command.recordedBy());
        String fingerprint = SurgeryCommandReceipts.fingerprint(SIGN_COMMAND,
                command.surgeryCaseId().toString(), Long.toString(command.expectedCaseRevision()),
                command.consentType().name(), command.signerId().toString(), command.signerType().name(),
                command.evidenceDocumentId() == null ? null : command.evidenceDocumentId().toString());
        String operation = SIGN_COMMAND + ":" + command.surgeryCaseId();
        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(receipts,
                new SurgeryCommandReceiptPort.Key(command.recordedBy().accountId().toString(),
                        operation, command.idempotencyKey()), fingerprint, SIGN_COMMAND, command.surgeryCaseId());
        if (claim.isReplay()) return claim.replay();

        SurgeryCase surgeryCase = lockCase(command.surgeryCaseId(), command.expectedCaseRevision());
        requireConsentMutable(surgeryCase);
        boolean activeTypeExists = consents.findByCaseId(command.surgeryCaseId()).stream()
                .anyMatch(existing -> existing.consentType() == command.consentType() && existing.isActive());
        if (activeTypeExists) throw new SurgeryRevisionConflictException();
        Instant at = clock.now();
        SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, command.recordedBy(),
                command.correlationId(), at, "CONSENT_CHANGED", schedules, reservations);
        SurgeryConsentRecord consent = SurgeryConsentRecord.sign(UUID.randomUUID(),
                command.surgeryCaseId(), command.consentType(), command.signerId(),
                command.signerType(), command.evidenceDocumentId(), command.recordedBy(),
                at, command.correlationId());
        consents.save(consent);
        surgeryCase.recordBusinessMutation(command.recordedBy(), command.correlationId(), at,
                "CONSENT_SIGNED");
        cases.save(surgeryCase, command.expectedCaseRevision());
        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(SIGN_COMMAND,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(), consent.consentId(),
                1, "ACTIVE:" + command.consentType().name(), at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
    }

    @Override
    @Transactional
    public SurgeryCommandOutcome revoke(RevokeCommand command) {
        requireHuman(command == null ? null : command.recordedBy());
        String fingerprint = SurgeryCommandReceipts.fingerprint(REVOKE_COMMAND,
                command.surgeryCaseId().toString(), command.consentId().toString(),
                Long.toString(command.expectedCaseRevision()), command.reason().trim());
        String operation = REVOKE_COMMAND + ":" + command.surgeryCaseId();
        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(receipts,
                new SurgeryCommandReceiptPort.Key(command.recordedBy().accountId().toString(),
                        operation, command.idempotencyKey()), fingerprint, REVOKE_COMMAND, command.surgeryCaseId());
        if (claim.isReplay()) return claim.replay();

        SurgeryCase surgeryCase = lockCase(command.surgeryCaseId(), command.expectedCaseRevision());
        requireConsentMutable(surgeryCase);
        SurgeryConsentRecord current = consents.findById(command.consentId())
                .filter(value -> value.surgeryCaseId().equals(command.surgeryCaseId()))
                .orElseThrow(SurgeryRevisionConflictException::new);
        Instant at = clock.now();
        SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, command.recordedBy(),
                command.correlationId(), at, "CONSENT_REVOKED", schedules, reservations);
        SurgeryConsentRecord revoked = current.revoke(command.recordedBy(), at,
                command.correlationId(), command.reason().trim());
        consents.save(revoked);
        surgeryCase.recordBusinessMutation(command.recordedBy(), command.correlationId(), at,
                "CONSENT_REVOKED");
        cases.save(surgeryCase, command.expectedCaseRevision());
        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(REVOKE_COMMAND,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(), revoked.consentId(),
                2, "REVOKED:" + revoked.consentType().name(), at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
    }

    private SurgeryCase lockCase(UUID caseId, long expectedRevision) {
        SurgeryCase surgeryCase = cases.lockById(caseId).orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.getRevision() != expectedRevision) throw new SurgeryRevisionConflictException();
        return surgeryCase;
    }

    private static void requireConsentMutable(SurgeryCase surgeryCase) {
        if (surgeryCase.getStatus() == SurgeryStatus.IN_PROGRESS
                || surgeryCase.getStatus() == SurgeryStatus.COMPLETED
                || surgeryCase.getStatus() == SurgeryStatus.CANCELLED) {
            throw new SurgeryRevisionConflictException();
        }
    }

    private static void requireHuman(com.mediflow.surgery.domain.model.SurgeryAuditActor actor) {
        if (actor == null || actor.actorType() != SurgeryActorType.HUMAN) {
            throw new IllegalArgumentException("A human recorder is required for consent commands");
        }
    }
}
