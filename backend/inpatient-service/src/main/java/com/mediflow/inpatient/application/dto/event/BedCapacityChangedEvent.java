package com.mediflow.inpatient.application.dto.event;

import com.mediflow.inpatient.domain.model.BedCapacitySnapshot;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import java.time.Instant;
import java.util.UUID;

public record BedCapacityChangedEvent(
        UUID bedId,
        long capacityRevision,
        UUID departmentId,
        String wardCode,
        String bedType,
        boolean active,
        BedStatus operationalStatus,
        boolean capacityEligible,
        Instant effectiveAt,
        UUID actorId) {

    public static BedCapacityChangedEvent from(BedCapacitySnapshot snapshot) {
        return new BedCapacityChangedEvent(snapshot.bedId(), snapshot.capacityRevision(),
                snapshot.departmentId(), snapshot.wardCode(), snapshot.bedType(), snapshot.active(),
                snapshot.operationalStatus(), snapshot.capacityEligible(), snapshot.effectiveAt(),
                snapshot.actorId());
    }
}
