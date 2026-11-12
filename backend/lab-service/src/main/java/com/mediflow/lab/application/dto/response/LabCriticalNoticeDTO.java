package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabCriticalNotice;

public record LabCriticalNoticeDTO(UUID noticeId, UUID testId, int resultVersion, String fieldCode, String label,
        String value, String unit, String flag, UUID patientId, UUID departmentId, String status, UUID acknowledgedBy,
        Instant acknowledgedAt, Instant createdAt) {

    public static LabCriticalNoticeDTO of(LabCriticalNotice n) {
        return new LabCriticalNoticeDTO(n.getNoticeId(), n.getTestId(), n.getResultVersion(), n.getFieldCode(),
                n.getLabel(), n.getValue(), n.getUnit(), n.getFlag().name(), n.getPatientId(), n.getDepartmentId(),
                n.getStatus().name(), n.getAcknowledgedBy(), n.getAcknowledgedAt(), n.getCreatedAt());
    }
}
