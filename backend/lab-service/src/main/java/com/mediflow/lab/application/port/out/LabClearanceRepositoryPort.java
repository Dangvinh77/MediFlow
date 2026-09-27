package com.mediflow.lab.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabFinancialClearance;

/** Durable exact-target clearance projection and event inbox boundary. */
public interface LabClearanceRepositoryPort {

    /** Claims the event and inserts every target projection in the caller's transaction. */
    boolean claimAndSave(UUID eventId, List<LabFinancialClearance> clearances);

    Optional<LabFinancialClearance> findValidByTestId(UUID testId, Instant at);

    Optional<LabFinancialClearance> findLatestByTestId(UUID testId);
}
