package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.mapper.SurgeryCareEventFactory;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;

import java.time.Instant;

/** Shared pre-start invalidation protocol for case-owned readiness dependencies. */
final class SurgeryReadinessInvalidation {

    private SurgeryReadinessInvalidation() { }

    static Pending invalidateIfRequired(SurgeryCase surgeryCase, SurgeryAuditActor actor,
                                     String correlationId, Instant at, String reason,
                                     SurgeryScheduleRepositoryPort schedules,
                                     SurgeryResourceReservationPort reservations) {
        SurgeryStatus status = surgeryCase.getStatus();
        if (status != SurgeryStatus.READY && status != SurgeryStatus.SCHEDULED) return Pending.NONE;
        SurgerySchedule schedule = schedules.findByCaseId(surgeryCase.getSurgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (!surgeryCase.getSurgeryCaseId().equals(schedule.surgeryCaseId())
                || surgeryCase.getReadinessSnapshot() == null
                || surgeryCase.getReadinessSnapshot().dependencyRevisions().stream().noneMatch(dependency ->
                dependency.dependencyType() == com.mediflow.surgery.domain.model.SurgeryDependencyType.SCHEDULE
                        && schedule.scheduleId().equals(dependency.sourceId())
                        && schedule.revision() == dependency.revision())) {
            throw new SurgeryRevisionConflictException();
        }
        if (status == SurgeryStatus.SCHEDULED) {
            reservations.release(surgeryCase.getSurgeryCaseId(), schedule.scheduleId(),
                    schedule.revision(), at);
        }
        var prior = surgeryCase.getReadinessSnapshot();
        surgeryCase.invalidateReadiness(actor, correlationId, at, reason);
        return new Pending(prior, schedule, at, reason, correlationId);
    }

    /** Capture after final revision is persisted; child mutations can increment it again. */
    record Pending(ReadinessSnapshot snapshot, SurgerySchedule schedule,
            Instant at, String reasonCode, String correlationId) {
        static final Pending NONE = new Pending(null, null, null, null, null);
        void capture(SurgeryCase value, SurgeryCareEventCapturePort events) {
            if (snapshot != null) events.hold(
                    SurgeryCareEventFactory.invalidated(
                        value, snapshot, schedule, at, reasonCode, correlationId), value.getRevision());
        }
    }
}
