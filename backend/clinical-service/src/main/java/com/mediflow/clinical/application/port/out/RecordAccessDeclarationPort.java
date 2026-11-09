package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.RecordAccessDeclaration;

public interface RecordAccessDeclarationPort {
    /** Returns a declaration still valid at {@code at}; one expiring exactly at {@code at} is not. */
    Optional<RecordAccessDeclaration> findActive(UUID accountId, UUID patientId, Instant at);

    void save(RecordAccessDeclaration declaration);
}
