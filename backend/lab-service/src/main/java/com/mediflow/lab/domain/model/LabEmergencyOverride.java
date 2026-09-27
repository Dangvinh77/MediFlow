package com.mediflow.lab.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.exception.LabRuleException;

/** Auditable emergency authorization for one test and one care episode. */
public record LabEmergencyOverride(
        UUID overrideId,
        UUID testId,
        UUID patientId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        UUID approvedBy,
        String approverRole,
        String reason,
        Instant approvedAt
) {

    public static LabEmergencyOverride approve(
            UUID overrideId, UUID testId, UUID patientId, CareEpisodeType careEpisodeType,
            UUID careEpisodeId, UUID approvedBy, String approverRole, String reason, Instant approvedAt) {
        if (overrideId == null || testId == null || patientId == null || careEpisodeType == null
                || careEpisodeId == null || approvedBy == null || approverRole == null
                || approverRole.isBlank() || approverRole.length() > 32 || reason == null
                || reason.isBlank() || reason.length() > 1000 || approvedAt == null) {
            throw new LabRuleException("LAB_OVERRIDE_INVALID", "Thông tin duyệt cấp cứu không hợp lệ");
        }
        return new LabEmergencyOverride(overrideId, testId, patientId, careEpisodeType, careEpisodeId,
                approvedBy, approverRole, reason, approvedAt);
    }
}
