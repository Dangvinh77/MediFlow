package com.mediflow.clinical.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.RecordHandover;

public interface RecordHandoverRepositoryPort {
    void insert(RecordHandover handover);

    Optional<RecordHandover> findPending(UUID recordId);

    Optional<RecordHandover> findByIdForUpdate(UUID handoverId);

    void accept(RecordHandover handover);

    /** Oldest first. */
    List<RecordHandover> findByRecord(UUID recordId);
}
