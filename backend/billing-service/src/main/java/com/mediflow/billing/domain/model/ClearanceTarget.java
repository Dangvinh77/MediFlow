package com.mediflow.billing.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

/** Exact operational target; recordId on LAB_TEST is context, never a fallback permission. */
public record ClearanceTarget(UUID appointmentId, UUID recordId, List<UUID> labTestIds,
                              UUID prescriptionId, UUID admissionId, UUID surgeryCaseId) {
    public ClearanceTarget {
        labTestIds = labTestIds == null ? List.of() : List.copyOf(labTestIds);
    }

    public void validate(ClearancePurpose purpose, CareEpisodeType episodeType, UUID episodeId) {
        boolean admissionMatches = episodeType == CareEpisodeType.ADMISSION
                && episodeId != null && episodeId.equals(admissionId);
        boolean distinctLabs = new HashSet<>(labTestIds).size() == labTestIds.size();
        boolean valid = purpose != null && episodeType != null && episodeId != null && switch (purpose) {
            case EXAM -> (appointmentId != null || recordId != null) && labTestIds.isEmpty()
                    && prescriptionId == null && admissionId == null && surgeryCaseId == null;
            case LAB_TEST -> !labTestIds.isEmpty() && distinctLabs && appointmentId == null
                    && prescriptionId == null && admissionId == null && surgeryCaseId == null;
            case PRESCRIPTION -> prescriptionId != null && appointmentId == null && recordId == null
                    && labTestIds.isEmpty() && admissionId == null && surgeryCaseId == null;
            case ADMISSION_DEPOSIT -> admissionMatches && appointmentId == null && recordId == null
                    && labTestIds.isEmpty() && prescriptionId == null && surgeryCaseId == null;
            case SURGERY -> surgeryCaseId != null && appointmentId == null && recordId == null
                    && labTestIds.isEmpty() && prescriptionId == null
                    && (episodeType == CareEpisodeType.ADMISSION ? admissionMatches : admissionId == null);
        };
        if (!valid) {
            throw new BillingRuleException("BILLING_CLEARANCE_TARGET_MISMATCH",
                    "Target không khớp với purpose hoặc care episode");
        }
        if (purpose == ClearancePurpose.EXAM && episodeType == CareEpisodeType.OUTPATIENT_VISIT
                && !episodeId.equals(appointmentId == null ? recordId : appointmentId)) {
            throw new BillingRuleException("BILLING_CLEARANCE_EPISODE_MISMATCH", "EXAM khác care episode");
        }
    }
}
