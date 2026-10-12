package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.BedStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BedCapacitySnapshot(
        UUID capacityHistoryId,
        UUID eventId,
        UUID bedId,
        long capacityRevision,
        UUID departmentId,
        String wardCode,
        String bedType,
        boolean active,
        BedStatus operationalStatus,
        boolean capacityEligible,
        Instant effectiveAt,
        UUID actorId,
        String correlationId) {

    public BedCapacitySnapshot {
        Objects.requireNonNull(capacityHistoryId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(bedId);
        Objects.requireNonNull(departmentId);
        Objects.requireNonNull(wardCode);
        Objects.requireNonNull(bedType);
        Objects.requireNonNull(operationalStatus);
        Objects.requireNonNull(effectiveAt);
        Objects.requireNonNull(correlationId);
        if (capacityRevision < 1) {
            throw new IllegalArgumentException("capacityRevision must be positive");
        }
    }

    public static BedCapacitySnapshot capture(Bed bed, UUID actorId, String correlationId,
                                              Instant effectiveAt, UUID eventId) {
        return new BedCapacitySnapshot(UUID.randomUUID(), eventId, bed.bedId(),
                bed.capacityRevision(), bed.departmentId(), bed.wardCode(), bed.bedType(),
                bed.active(), bed.status(), isCapacityEligible(bed), effectiveAt, actorId, correlationId);
    }

    private static boolean isCapacityEligible(Bed bed) {
        return bed.active() && bed.status() != BedStatus.OUT_OF_SERVICE;
    }
}
