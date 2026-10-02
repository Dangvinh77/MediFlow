package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public class SurgeryPreopApplicationService implements BeginPreopUseCase {

    private static final String COMMAND_CODE = "BEGIN_PREOP";

    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryCommandReceiptPort receipts;
    private final SurgeryClockPort clock;

    public SurgeryPreopApplicationService(SurgeryCaseRepositoryPort cases,
                                          SurgeryCommandReceiptPort receipts,
                                          SurgeryClockPort surgeryClock) {
        this.cases = cases;
        this.receipts = receipts;
        this.clock = surgeryClock;
    }

    @Override
    @Transactional
    public SurgeryCommandOutcome begin(Command command) {
        if (command == null || command.actor() == null) {
            throw new IllegalArgumentException("A human actor is required for begin-preop");
        }
        SurgeryAuditActor actor = SurgeryAuditActor.human(
                command.actor().accountId(), command.actor().verifiedStaffId());
        String operation = COMMAND_CODE + ":" + command.surgeryCaseId();
        SurgeryCommandReceiptPort.Key key = new SurgeryCommandReceiptPort.Key(
                actor.accountId().toString(), operation, command.idempotencyKey());
        String fingerprint = SurgeryCommandReceipts.fingerprint(COMMAND_CODE,
                command.surgeryCaseId().toString(), Long.toString(command.expectedCaseRevision()),
                actor.verifiedStaffId() == null ? null : actor.verifiedStaffId().toString());
        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(
                receipts, key, fingerprint, COMMAND_CODE, command.surgeryCaseId());
        if (claim.isReplay()) return claim.replay();

        SurgeryCase surgeryCase = cases.lockById(command.surgeryCaseId())
                .orElseThrow(() -> new SurgeryCaseNotFoundException(command.surgeryCaseId()));
        if (surgeryCase.getRevision() != command.expectedCaseRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        Instant at = clock.now();
        surgeryCase.beginPreop(actor, command.correlationId(), at);
        cases.save(surgeryCase, command.expectedCaseRevision());
        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(COMMAND_CODE,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(), null, 0,
                SurgeryStatus.PREOP_IN_PROGRESS.name(), at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
    }
}
