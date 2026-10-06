package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgerySchedule;

import java.time.Instant;
import java.util.UUID;

/** The caller persists a verified draft and performs case transition in this same transaction. */
public interface SurgeryResourceReservationPort {

    /** Case first, sorted union of existing/current resource mutexes. Recheck guards/Clock after this returns. */
    void lockForMutation(SurgerySchedule schedule);

    void reserve(SurgerySchedule schedule, Instant at);

    void markInUse(UUID caseId, UUID scheduleId, long scheduleRevision, Instant at);

    void release(UUID caseId, UUID scheduleId, long scheduleRevision, Instant at);
}
