package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;

import com.mediflow.surgery.application.port.in.ExpireSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One expiry per transaction; lock-time evidence, not a stale batch timestamp, decides. */
public class SurgeryReadinessExpiryService implements ExpireSurgeryReadinessUseCase {
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryClockPort clock;
    private final SurgeryCareEventCapturePort events;

    public SurgeryReadinessExpiryService(SurgeryCaseRepositoryPort cases, SurgeryScheduleRepositoryPort schedules,
            SurgeryResourceReservationPort reservations, SurgeryClockPort clock, SurgeryCareEventCapturePort events) {
        this.events = java.util.Objects.requireNonNull(events);
        this.cases = cases; this.schedules = schedules; this.reservations = reservations; this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean expire(Candidate candidate, String correlationId) {
        if (candidate == null || correlationId == null || correlationId.isBlank() || correlationId.length() > 128) {
            throw new IllegalArgumentException("Readiness expiry candidate and correlation are required");
        }
        var found = cases.lockById(candidate.surgeryCaseId());
        if (found.isEmpty()) return false;
        var surgeryCase = found.orElseThrow();
        if (surgeryCase.getStatus() != SurgeryStatus.READY && surgeryCase.getStatus() != SurgeryStatus.SCHEDULED) return false;
        var snapshot = surgeryCase.getReadinessSnapshot();
        if (snapshot == null || !candidate.readinessSnapshotId().equals(snapshot.readinessSnapshotId())) return false;
        var now = clock.now();
        if (snapshot.validUntil() == null || now.isBefore(snapshot.validUntil())) return false;
        long previousRevision = surgeryCase.getRevision();
        var pendingEvent = SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, SurgeryAuditActor.system("surgery-service"),
                correlationId, now, "READINESS_EXPIRED", schedules, reservations);
        cases.save(surgeryCase, previousRevision);
        pendingEvent.capture(surgeryCase, events);
        return true;
    }
}
