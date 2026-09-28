package com.mediflow.pharmacy.domain.model;

import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

/** Exact source-owned episode identity carried by a V1 prescription. */
public record CareEpisode(CareEpisodeType type, UUID id) {

    public CareEpisode {
        if (type == null || id == null) {
            throw new PrescriptionRuleException(
                    "PHARMACY_CARE_CONTEXT_INVALID", "Care episode type and ID are required");
        }
    }
}
