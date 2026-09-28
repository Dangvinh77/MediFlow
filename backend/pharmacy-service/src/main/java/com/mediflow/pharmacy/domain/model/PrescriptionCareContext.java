package com.mediflow.pharmacy.domain.model;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

import java.util.UUID;

/**
 * Validated care metadata for a prescription. Version 0 deliberately carries no inferred episode;
 * version 1 must carry the exact source-provided episode and price code.
 */
public record PrescriptionCareContext(
        CareContractVersion contractVersion,
        CareContext careContext,
        CareEpisode episode,
        UUID admissionId,
        String priceCode) {

    public PrescriptionCareContext {
        if (contractVersion == null) {
            throw invalid("Care contract version is required");
        }
        if (contractVersion == CareContractVersion.LEGACY) {
            if (careContext != CareContext.OUTPATIENT
                    || episode != null
                    || admissionId != null
                    || priceCode != null) {
                throw invalid("Version 0 must remain outpatient without V1 episode fields");
            }
        } else {
            if (careContext == null || episode == null || priceCode == null
                    || priceCode.isBlank() || priceCode.length() > 64) {
                throw invalid("Version 1 requires care context, episode and price code");
            }

            if (careContext == CareContext.OUTPATIENT) {
                if (episode.type() != CareEpisodeType.OUTPATIENT_VISIT || admissionId != null) {
                    throw invalid("Outpatient context requires an outpatient visit and no admission ID");
                }
            } else if (careContext == CareContext.ADMISSION) {
                if (episode.type() != CareEpisodeType.ADMISSION
                        || admissionId == null
                        || !admissionId.equals(episode.id())) {
                    throw invalid("Admission ID must equal the admission care episode ID");
                }
            }
        }
    }

    public static PrescriptionCareContext legacy() {
        return new PrescriptionCareContext(
                CareContractVersion.LEGACY, CareContext.OUTPATIENT, null, null, null);
    }

    public static PrescriptionCareContext v1(
            CareContext careContext, CareEpisode episode, UUID admissionId, String priceCode) {
        return new PrescriptionCareContext(
                CareContractVersion.V1, careContext, episode, admissionId, priceCode);
    }

    private static PrescriptionRuleException invalid(String message) {
        return new PrescriptionRuleException("PHARMACY_CARE_CONTEXT_INVALID", message);
    }
}
