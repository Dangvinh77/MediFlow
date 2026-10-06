package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.ApplySurgeryAuthorityInvalidationUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class SurgeryAuthorityInvalidationService implements ApplySurgeryAuthorityInvalidationUseCase {
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryAuthorityInvalidationPort invalidations;
    private final SurgeryClockPort clock;
    public SurgeryAuthorityInvalidationService(SurgeryCaseRepositoryPort cases, SurgeryScheduleRepositoryPort schedules,
            SurgeryResourceReservationPort reservations, SurgeryAuthorityInvalidationPort invalidations, SurgeryClockPort clock) {
        this.cases = cases; this.schedules = schedules; this.reservations = reservations;
        this.invalidations = invalidations; this.clock = clock;
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean apply(Candidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("Authority candidate required");
        var found = cases.lockById(candidate.surgeryCaseId());
        if (found.isEmpty()) return false;
        var now = clock.now();
        var pending = invalidations.lockPending(candidate, now);
        if (pending.isEmpty()) return false;
        var value = found.orElseThrow();
        if ((value.getStatus() != SurgeryStatus.READY && value.getStatus() != SurgeryStatus.SCHEDULED)
                || value.getReadinessSnapshot() == null
                || !candidate.readinessSnapshotId().equals(value.getReadinessSnapshot().snapshotId())) {
            invalidations.finish(candidate, SurgeryAuthorityInvalidationPort.Completion.SUPERSEDED, now);
            return false;
        }
        var schedule = schedules.findByCaseId(candidate.surgeryCaseId()).orElseThrow(SurgeryRevisionConflictException::new);
        if (!candidate.scheduleId().equals(schedule.scheduleId()) || candidate.scheduleRevision() != schedule.revision()
                || !pending.orElseThrow().change().affects(schedule)) throw new SurgeryRevisionConflictException();
        long revision = value.getRevision();
        SurgeryReadinessInvalidation.invalidateIfRequired(value, SurgeryAuditActor.system("organization-service"),
                pending.orElseThrow().correlationId(), now, "ORGANIZATION_AUTHORITY_CHANGED", schedules, reservations);
        cases.save(value, revision);
        invalidations.finish(candidate, SurgeryAuthorityInvalidationPort.Completion.APPLIED, now);
        return true;
    }
}
