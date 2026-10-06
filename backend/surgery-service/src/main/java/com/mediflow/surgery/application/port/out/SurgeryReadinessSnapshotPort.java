package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface SurgeryReadinessSnapshotPort {
    void store(ReadinessSnapshot snapshot);
    Optional<ReadinessSnapshot> findSnapshot(UUID snapshotId);
}
