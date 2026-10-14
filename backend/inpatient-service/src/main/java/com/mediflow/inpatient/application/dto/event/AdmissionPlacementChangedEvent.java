package com.mediflow.inpatient.application.dto.event;

import com.mediflow.inpatient.domain.model.enums.PlacementChangeType;
import java.time.Instant;
import java.util.UUID;

public record AdmissionPlacementChangedEvent(
        UUID admissionId,
        UUID patientId,
        long placementRevision,
        PlacementChangeType changeType,
        Instant changedAt,
        UUID actorId,
        String reason,
        PlacementFact previousPlacement,
        PlacementFact currentPlacement) {

    public record PlacementFact(UUID assignmentId, UUID bedId, UUID departmentId,
                                String wardCode, Instant assignedAt, Instant releasedAt) {}
}
