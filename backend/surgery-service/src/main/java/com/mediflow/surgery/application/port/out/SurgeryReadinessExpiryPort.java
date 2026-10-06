package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import java.time.Instant;
import java.util.List;

/** Persisted expiry candidates and retry metadata, never an alternate readiness authority. */
public interface SurgeryReadinessExpiryPort {
    List<Candidate> findDue(Instant now, int limit);
    void deferIfStillDue(Candidate candidate, Instant now, String failureCode);
}
