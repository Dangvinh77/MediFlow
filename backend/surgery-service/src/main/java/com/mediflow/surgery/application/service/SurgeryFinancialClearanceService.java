package com.mediflow.surgery.application.service;

import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryStatus;

/** Only records financial evidence. No automatic READY, START or resource reservation. */
public class SurgeryFinancialClearanceService implements ReactToSurgeryClearanceUseCase {
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryFinancialClearanceRepositoryPort clearances;
    private final SurgeryInboxPort inbox;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryClockPort clock;
    public SurgeryFinancialClearanceService(SurgeryCaseRepositoryPort cases, SurgeryFinancialClearanceRepositoryPort clearances,
            SurgeryInboxPort inbox, SurgeryScheduleRepositoryPort schedules, SurgeryResourceReservationPort reservations, SurgeryClockPort clock) {
        this.cases = cases; this.clearances = clearances; this.inbox = inbox;
        this.schedules = schedules; this.reservations = reservations; this.clock = clock;
    }
    @Override @Transactional
    public Outcome receive(Command command) {
        var decision = inbox.begin(command.incoming());
        if (decision == SurgeryInboxPort.Decision.ALREADY_APPLIED) return Outcome.REPLAYED;
        if (decision == SurgeryInboxPort.Decision.CONFLICT) return Outcome.CONFLICT;
        if (decision == SurgeryInboxPort.Decision.QUARANTINED) return Outcome.QUARANTINED;
        var found = cases.lockById(command.clearance().surgeryCaseId());
        Instant now = clock.now();
        if (found.isEmpty()) {
            inbox.defer(command.incoming().eventId(), "SURGERY_CASE_NOT_YET_PRESENT", now.plusSeconds(60));
            return Outcome.DEFERRED;
        }
        var surgeryCase = found.get();
        if (!command.clearance().matches(surgeryCase) || command.clearance().grantedAt().isAfter(now.plusSeconds(5))) {
            inbox.quarantine(command.incoming().eventId(), "SURGERY_CLEARANCE_TARGET_OR_TIME_MISMATCH");
            return Outcome.QUARANTINED;
        }
        var saved = clearances.saveIfAbsentAndMatching(command.clearance());
        if (saved == SurgeryFinancialClearanceRepositoryPort.SaveDecision.CONFLICT) {
            inbox.quarantine(command.incoming().eventId(), "SURGERY_CLEARANCE_ID_CONTENT_MISMATCH");
            return Outcome.CONFLICT;
        }
        if (saved == SurgeryFinancialClearanceRepositoryPort.SaveDecision.CREATED
                && (surgeryCase.getStatus() == SurgeryStatus.READY || surgeryCase.getStatus() == SurgeryStatus.SCHEDULED)) {
            long previousRevision = surgeryCase.getRevision();
            SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, SurgeryAuditActor.system("billing-service"),
                    command.correlationId(), now, "FINANCIAL_CLEARANCE_CHANGED", schedules, reservations);
            cases.save(surgeryCase, previousRevision);
        }
        inbox.markApplied(command.incoming().eventId(), now);
        return Outcome.APPLIED;
    }
}
