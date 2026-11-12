package com.mediflow.lab.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A released critical value waiting for a doctor of the requesting department to acknowledge it. */
public final class LabCriticalNotice {
    private final UUID noticeId;
    private final UUID testId;
    private final int resultVersion;
    private final String fieldCode;
    private final String label;
    private final String value;
    private final String unit;
    private final LabFlag flag;
    private final UUID patientId;
    private final UUID departmentId;
    private final Instant createdAt;
    private LabCriticalNoticeStatus status;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;

    private LabCriticalNotice(UUID noticeId, UUID testId, int resultVersion, String fieldCode, String label,
            String value, String unit, LabFlag flag, UUID patientId, UUID departmentId, Instant createdAt) {
        this.noticeId = Objects.requireNonNull(noticeId);
        this.testId = Objects.requireNonNull(testId);
        this.resultVersion = resultVersion;
        this.fieldCode = Objects.requireNonNull(fieldCode);
        this.label = Objects.requireNonNull(label);
        this.value = value;
        this.unit = unit;
        this.flag = Objects.requireNonNull(flag);
        this.patientId = Objects.requireNonNull(patientId);
        this.departmentId = Objects.requireNonNull(departmentId);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static LabCriticalNotice raise(UUID testId, int resultVersion, String fieldCode, String label, String value,
            String unit, LabFlag flag, UUID patientId, UUID departmentId, Instant at) {
        if (flag == null || !flag.critical()) {
            throw new IllegalArgumentException("Only a critical value raises a notice");
        }
        LabCriticalNotice notice = new LabCriticalNotice(UUID.randomUUID(), testId, resultVersion, fieldCode, label,
                value, unit, flag, patientId, departmentId, at);
        notice.status = LabCriticalNoticeStatus.OPEN;
        return notice;
    }

    public static LabCriticalNotice restore(UUID noticeId, UUID testId, int resultVersion, String fieldCode,
            String label, String value, String unit, LabFlag flag, UUID patientId, UUID departmentId,
            LabCriticalNoticeStatus status, UUID acknowledgedBy, Instant acknowledgedAt, Instant createdAt) {
        LabCriticalNotice notice = new LabCriticalNotice(noticeId, testId, resultVersion, fieldCode, label, value, unit,
                flag, patientId, departmentId, createdAt);
        notice.status = Objects.requireNonNull(status);
        notice.acknowledgedBy = acknowledgedBy;
        notice.acknowledgedAt = acknowledgedAt;
        return notice;
    }

    /** True when this call acknowledged it; false when it already was (the first acknowledgement stands). */
    public boolean acknowledge(UUID doctor, Instant at) {
        if (status == LabCriticalNoticeStatus.ACKNOWLEDGED) {
            return false;
        }
        this.status = LabCriticalNoticeStatus.ACKNOWLEDGED;
        this.acknowledgedBy = Objects.requireNonNull(doctor);
        this.acknowledgedAt = Objects.requireNonNull(at);
        return true;
    }

    public UUID getNoticeId() { return noticeId; }
    public UUID getTestId() { return testId; }
    public int getResultVersion() { return resultVersion; }
    public String getFieldCode() { return fieldCode; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public LabFlag getFlag() { return flag; }
    public UUID getPatientId() { return patientId; }
    public UUID getDepartmentId() { return departmentId; }
    public Instant getCreatedAt() { return createdAt; }
    public LabCriticalNoticeStatus getStatus() { return status; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
