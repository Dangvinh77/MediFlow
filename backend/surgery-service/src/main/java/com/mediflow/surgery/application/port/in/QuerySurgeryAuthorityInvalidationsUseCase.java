package com.mediflow.surgery.application.port.in;

import java.util.List;
import java.util.UUID;

public interface QuerySurgeryAuthorityInvalidationsUseCase {
    List<Candidate> findDue(int limit);
    record Candidate(UUID eventId, UUID surgeryCaseId, UUID readinessSnapshotId, UUID scheduleId, long scheduleRevision) {
        public Candidate {
            if (eventId == null || surgeryCaseId == null || readinessSnapshotId == null || scheduleId == null || scheduleRevision < 1) {
                throw new IllegalArgumentException("Invalid authority invalidation candidate");
            }
        }
    }
}
