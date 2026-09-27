package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmergencyOverride {
    private final UUID overrideId;
    private final UUID appointmentId;
    private final UUID recordId;
    private final UUID patientId;
    private final UUID careEpisodeId;
    private final UUID approvedBy;
    private final String approverRole;
    private final String reason;
    private final Instant approvedAt;

    public static EmergencyOverride create(UUID overrideId, UUID appointmentId, UUID recordId,
                                            UUID patientId, UUID careEpisodeId, UUID approvedBy,
                                            String approverRole, String reason, Instant approvedAt) {
        boolean appointmentTarget = appointmentId != null && recordId == null;
        boolean recordTarget = appointmentId == null && recordId != null;
        UUID targetId = appointmentTarget ? appointmentId : recordId;
        if (overrideId == null || (!appointmentTarget && !recordTarget) || patientId == null
                || careEpisodeId == null || approvedBy == null || approvedAt == null
                || targetId == null || !targetId.equals(careEpisodeId)
                || approverRole == null || (!"ADMIN".equals(approverRole) && !"DOCTOR".equals(approverRole))
                || reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new InvalidClinicalDataException("CLINICAL_OVERRIDE_INVALID",
                    "Emergency override requires an exact care target and complete approval audit");
        }
        return new EmergencyOverride(overrideId, appointmentId, recordId, patientId, careEpisodeId,
                approvedBy, approverRole, reason, approvedAt);
    }
}
