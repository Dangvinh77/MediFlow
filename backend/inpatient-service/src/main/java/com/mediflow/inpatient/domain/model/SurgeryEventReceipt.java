package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SurgeryEventReceipt {
    private final UUID receiptId;
    private final UUID eventId;
    private final String eventType;
    private final UUID operationId;
    private final String payloadFingerprint;
    private final UUID surgeryCaseId;
    private final UUID surgeryRequestId;
    private final UUID admissionId;
    private final UUID patientId;
    private final UUID departmentId;
    private final int caseRevision;
    private final Integer sourceRevision;
    private final ExternalOrderStatus targetStatus;
    private final String summary;
    private final String timelineContent;
    private final Instant timelineAt;
    private final Instant receivedAt;
    private Instant appliedAt;

    private SurgeryEventReceipt(UUID receiptId, UUID eventId, String eventType, UUID operationId,
                                String payloadFingerprint, UUID surgeryCaseId, UUID surgeryRequestId,
                                UUID admissionId, UUID patientId, UUID departmentId, int caseRevision,
                                Integer sourceRevision, ExternalOrderStatus targetStatus,
                                String summary, String timelineContent, Instant timelineAt,
                                Instant receivedAt, Instant appliedAt) {
        this.receiptId = Objects.requireNonNull(receiptId);
        this.eventId = Objects.requireNonNull(eventId);
        this.eventType = Objects.requireNonNull(eventType);
        this.operationId = Objects.requireNonNull(operationId);
        this.payloadFingerprint = Objects.requireNonNull(payloadFingerprint);
        this.surgeryCaseId = Objects.requireNonNull(surgeryCaseId);
        this.surgeryRequestId = Objects.requireNonNull(surgeryRequestId);
        this.admissionId = Objects.requireNonNull(admissionId);
        this.patientId = Objects.requireNonNull(patientId);
        this.departmentId = Objects.requireNonNull(departmentId);
        this.caseRevision = caseRevision;
        this.sourceRevision = sourceRevision;
        this.targetStatus = Objects.requireNonNull(targetStatus);
        this.summary = summary;
        this.timelineContent = timelineContent;
        this.timelineAt = timelineAt;
        this.receivedAt = Objects.requireNonNull(receivedAt);
        this.appliedAt = appliedAt;
    }

    public static SurgeryEventReceipt pending(UUID eventId, String eventType, UUID operationId,
                                               String payloadFingerprint, UUID surgeryCaseId,
                                               UUID surgeryRequestId, UUID admissionId, UUID patientId,
                                               UUID departmentId,
                                               int caseRevision, Integer sourceRevision,
                                               ExternalOrderStatus targetStatus, String summary,
                                               String timelineContent, Instant timelineAt,
                                               Instant receivedAt) {
        return new SurgeryEventReceipt(UUID.randomUUID(), eventId, eventType, operationId,
                payloadFingerprint, surgeryCaseId, surgeryRequestId, admissionId, patientId, departmentId,
                caseRevision, sourceRevision, targetStatus, summary, timelineContent,
                timelineAt, receivedAt, null);
    }

    public static SurgeryEventReceipt restore(UUID receiptId, UUID eventId, String eventType,
                                               UUID operationId, String payloadFingerprint,
                                               UUID surgeryCaseId, UUID surgeryRequestId,
                                               UUID admissionId, UUID patientId, UUID departmentId, int caseRevision,
                                               Integer sourceRevision, ExternalOrderStatus targetStatus,
                                               String summary, String timelineContent, Instant timelineAt,
                                               Instant receivedAt, Instant appliedAt) {
        return new SurgeryEventReceipt(receiptId, eventId, eventType, operationId,
                payloadFingerprint, surgeryCaseId, surgeryRequestId, admissionId, patientId, departmentId,
                caseRevision, sourceRevision, targetStatus, summary, timelineContent,
                timelineAt, receivedAt, appliedAt);
    }

    public boolean hasFingerprint(String fingerprint) {
        return payloadFingerprint.equals(fingerprint);
    }

    public void markApplied(Instant at) {
        if (appliedAt == null) {
            appliedAt = Objects.requireNonNull(at);
        }
    }

    public UUID receiptId() { return receiptId; }
    public UUID eventId() { return eventId; }
    public String eventType() { return eventType; }
    public UUID operationId() { return operationId; }
    public String payloadFingerprint() { return payloadFingerprint; }
    public UUID surgeryCaseId() { return surgeryCaseId; }
    public UUID surgeryRequestId() { return surgeryRequestId; }
    public UUID admissionId() { return admissionId; }
    public UUID patientId() { return patientId; }
    public UUID departmentId() { return departmentId; }
    public int caseRevision() { return caseRevision; }
    public Integer sourceRevision() { return sourceRevision; }
    public ExternalOrderStatus targetStatus() { return targetStatus; }
    public String summary() { return summary; }
    public String timelineContent() { return timelineContent; }
    public Instant timelineAt() { return timelineAt; }
    public Instant receivedAt() { return receivedAt; }
    public Instant appliedAt() { return appliedAt; }
    public boolean applied() { return appliedAt != null; }
}
