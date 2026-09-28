package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgeryConsentRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only consent audit storage; replacing an existing signer or signature is not supported. */
public interface SurgeryConsentRepositoryPort {

    List<SurgeryConsentRecord> findByCaseId(UUID caseId);

    Optional<SurgeryConsentRecord> findById(UUID consentId);

    SurgeryConsentRecord save(SurgeryConsentRecord consent);
}
