package com.mediflow.surgery.application.port.in;

import java.util.List;
import java.util.UUID;

/** Bounded, non-mutating discovery; candidates never grant mutation authority. */
public interface QueryExpiredSurgeryReadinessUseCase {
    List<Candidate> findDue(int limit);

    record Candidate(UUID surgeryCaseId, UUID readinessSnapshotId) {
        public Candidate {
            if (surgeryCaseId == null || readinessSnapshotId == null) {
                throw new IllegalArgumentException("Readiness expiry identity is required");
            }
        }
    }
}
