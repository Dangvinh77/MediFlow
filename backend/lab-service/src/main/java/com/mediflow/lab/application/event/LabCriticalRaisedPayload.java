package com.mediflow.lab.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;

/** {@code lab.critical.raised} v1: addressed to the requesting department's doctors, never the patient. */
public record LabCriticalRaisedPayload(UUID noticeId, UUID labId, UUID patientId, UUID recordId, UUID departmentId,
        CareEpisodeType careEpisodeType, UUID careEpisodeId, String labType, int resultVersion, String fieldCode,
        String label, String value, String unit, String flag, Instant releasedAt) {
}
