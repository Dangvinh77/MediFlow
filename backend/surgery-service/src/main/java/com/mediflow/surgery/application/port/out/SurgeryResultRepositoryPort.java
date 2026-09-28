package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgeryResult;

import java.util.Optional;
import java.util.UUID;

/** Immutable result storage; V1 does not support correction or replacement. */
public interface SurgeryResultRepositoryPort {

    Optional<SurgeryResult> findByCaseId(UUID caseId);

    SurgeryResult create(SurgeryResult result);
}
