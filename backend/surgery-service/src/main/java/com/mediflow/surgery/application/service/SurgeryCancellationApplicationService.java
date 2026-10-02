package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.CancelSurgeryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Local transactional cancellation; wire publication stays gated by the cross-owner contract. */
public class SurgeryCancellationApplicationService implements CancelSurgeryUseCase {

    private static final String COMMAND_CODE = "CANCEL_SURGERY";

    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryCommandReceiptPort receipts;
    private final SurgeryClockPort clock;

    public SurgeryCancellationApplicationService(SurgeryCaseRepositoryPort cases,
                                                  SurgeryScheduleRepositoryPort schedules,
                                                  SurgeryResourceReservationPort reservations,
                                                  SurgeryCommandReceiptPort receipts,
                                                  SurgeryClockPort clock) {
        this.cases = cases;
        this.schedules = schedules;
        this.reservations = reservations;
        this.receipts = receipts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SurgeryCommandOutcome cancel(Command command) {
        if (command == null || command.actor() == null) {
            throw new IllegalArgumentException("A human actor is required to cancel a surgery case");
        }
        SurgeryAuditActor actor = SurgeryAuditActor.human(
                command.actor().accountId(), command.actor().verifiedStaffId());
        String fingerprint = SurgeryCommandReceipts.fingerprint(COMMAND_CODE,
                command.surgeryCaseId().toString(), Long.toString(command.expectedCaseRevision()),
                command.reason());
        String operation = COMMAND_CODE + ":" + command.surgeryCaseId();
        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(receipts,
                new SurgeryCommandReceiptPort.Key(actor.accountId().toString(),
                        operation, command.idempotencyKey()), fingerprint, COMMAND_CODE,
                command.surgeryCaseId());
        if (claim.isReplay()) return claim.replay();

        SurgeryCase surgeryCase = cases.lockById(command.surgeryCaseId())
                .orElseThrow(() -> new SurgeryCaseNotFoundException(command.surgeryCaseId()));
        if (surgeryCase.getRevision() != command.expectedCaseRevision()) {
            throw new SurgeryRevisionConflictException();
        }

        SurgeryStatus priorStatus = surgeryCase.getStatus();
        SurgerySchedule schedule = null;
        if (priorStatus == SurgeryStatus.SCHEDULED) {
            schedule = schedules.findByCaseId(surgeryCase.getSurgeryCaseId())
                    .orElseThrow(SurgeryRevisionConflictException::new);
            var scheduleDependency = surgeryCase.getReadinessSnapshot() == null ? null
                    : surgeryCase.getReadinessSnapshot().dependencyRevisions().stream()
                    .filter(dependency -> dependency.dependencyType() == SurgeryDependencyType.SCHEDULE)
                    .findFirst().orElse(null);
            if (scheduleDependency == null
                    || !schedule.scheduleId().equals(scheduleDependency.sourceId())
                    || schedule.revision() != scheduleDependency.revision()) {
                throw new SurgeryRevisionConflictException();
            }
        } else if (priorStatus != SurgeryStatus.REQUESTED
                && priorStatus != SurgeryStatus.PREOP_IN_PROGRESS
                && priorStatus != SurgeryStatus.READY) {
            throw new SurgeryRevisionConflictException();
        }

        Instant at = clock.now();
        if (schedule != null) {
            reservations.release(surgeryCase.getSurgeryCaseId(), schedule.scheduleId(),
                    schedule.revision(), at);
        }
        surgeryCase.cancel(actor, command.correlationId(), at, command.reason());
        cases.save(surgeryCase, command.expectedCaseRevision());

        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(COMMAND_CODE,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(),
                schedule == null ? null : schedule.scheduleId(),
                schedule == null ? 0 : schedule.revision(), SurgeryStatus.CANCELLED.name(), at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
    }
}
