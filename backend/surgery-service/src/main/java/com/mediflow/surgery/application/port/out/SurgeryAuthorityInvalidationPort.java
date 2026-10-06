package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase.Candidate;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgeryAuthorityInvalidationPort {
    Capture capture(UUID eventId, SurgeryAuthorityChange change, String correlationId, Instant now);
    List<Candidate> findDue(Instant now, int limit);
    /** Caller holds the case lock first; the returned work is fenced by exact snapshot/schedule pins. */
    Optional<Work> lockPending(Candidate candidate, Instant now);
    void finish(Candidate candidate, Completion completion, Instant now);
    void defer(Candidate candidate, String failureCode, Instant now);
    enum Capture { CREATED, MATCHING, CONFLICT }
    enum Completion { APPLIED, SUPERSEDED }
    record Work(SurgeryAuthorityChange change, String correlationId) {}
}
