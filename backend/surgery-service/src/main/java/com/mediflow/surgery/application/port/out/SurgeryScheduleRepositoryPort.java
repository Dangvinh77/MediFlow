package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgerySchedule;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persists a draft revision; only the reservation port finalizes the slot. */
public interface SurgeryScheduleRepositoryPort {

    Optional<SurgerySchedule> findByCaseId(UUID caseId);

    Optional<SurgerySchedule> findRevision(UUID caseId, long revision);

    void saveDraft(SurgerySchedule schedule, long expectedRevision, Instant at);
}
