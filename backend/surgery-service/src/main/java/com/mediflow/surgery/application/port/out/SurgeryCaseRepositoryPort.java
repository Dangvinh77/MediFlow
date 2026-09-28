package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgeryCase;

import java.util.Optional;
import java.util.UUID;

/** Case aggregate storage; expectedRevision is the value observed before this command. */
public interface SurgeryCaseRepositoryPort {

    Optional<SurgeryCase> findById(UUID caseId);

    Optional<SurgeryCase> findByRequestId(UUID requestId);

    Optional<SurgeryCase> lockById(UUID caseId);

    SurgeryCase save(SurgeryCase surgeryCase, long expectedRevision);
}
