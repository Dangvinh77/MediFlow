package com.mediflow.lab.domain.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.domain.exception.LabRuleException;

/** A collected specimen and the laboratory's single decision to accept or reject it. */
public final class LabSpecimen {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private final UUID specimenId;
    private final String accession;
    private final UUID patientId;
    private final String specimenType;
    private final List<UUID> testIds;
    private final UUID replacesSpecimenId;
    private final UUID collectedBy;
    private final Instant collectedAt;
    private UUID receivedBy;
    private Instant receivedAt;
    private LabSpecimenDecision decision;
    private String rejectionReason;

    private LabSpecimen(UUID specimenId, String accession, UUID patientId, String specimenType, List<UUID> testIds,
            UUID replacesSpecimenId, UUID collectedBy, Instant collectedAt) {
        this.specimenId = Objects.requireNonNull(specimenId);
        this.accession = Objects.requireNonNull(accession);
        this.patientId = Objects.requireNonNull(patientId);
        this.specimenType = specimenType;
        this.testIds = List.copyOf(testIds);
        this.replacesSpecimenId = replacesSpecimenId;
        this.collectedBy = Objects.requireNonNull(collectedBy);
        this.collectedAt = Objects.requireNonNull(collectedAt);
    }

    public static LabSpecimen collect(UUID patientId, String specimenType, List<UUID> testIds,
            UUID replacesSpecimenId, UUID collectedBy, Instant at) {
        if (specimenType == null || specimenType.isBlank() || specimenType.strip().length() > 40) {
            throw new LabRuleException("LAB_SPECIMEN_TYPE_REQUIRED", "Loại mẫu bệnh phẩm là bắt buộc (tối đa 40 ký tự)");
        }
        if (testIds == null || testIds.isEmpty() || testIds.stream().anyMatch(Objects::isNull)
                || testIds.stream().distinct().count() != testIds.size()) {
            throw new LabRuleException("LAB_SPECIMEN_TESTS_REQUIRED", "Mẫu phải gắn với ít nhất một xét nghiệm, không trùng");
        }
        UUID id = UUID.randomUUID();
        String accession = "SP-" + DAY.format(at) + "-"
                + id.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return new LabSpecimen(id, accession, patientId, specimenType.strip().toUpperCase(Locale.ROOT), testIds,
                replacesSpecimenId, collectedBy, at);
    }

    public static LabSpecimen restore(UUID specimenId, String accession, UUID patientId, String specimenType,
            List<UUID> testIds, UUID replacesSpecimenId, UUID collectedBy, Instant collectedAt, UUID receivedBy,
            Instant receivedAt, LabSpecimenDecision decision, String rejectionReason) {
        LabSpecimen specimen = new LabSpecimen(specimenId, accession, patientId, specimenType, testIds,
                replacesSpecimenId, collectedBy, collectedAt);
        specimen.receivedBy = receivedBy;
        specimen.receivedAt = receivedAt;
        specimen.decision = decision;
        specimen.rejectionReason = rejectionReason;
        return specimen;
    }

    public void decide(LabSpecimenDecision decision, String reason, UUID actor, Instant at) {
        if (this.decision != null) {
            throw new DuplicateResourceException("LAB_SPECIMEN_DECIDED", "Mẫu bệnh phẩm đã được tiếp nhận hoặc từ chối");
        }
        Objects.requireNonNull(decision);
        String trimmed = reason == null ? null : reason.strip();
        if (decision == LabSpecimenDecision.REJECTED && (trimmed == null || trimmed.isEmpty())) {
            throw new LabRuleException("LAB_SPECIMEN_REJECTION_REASON_REQUIRED", "Cần nêu lý do từ chối mẫu");
        }
        if (trimmed != null && trimmed.length() > 500) {
            throw new LabRuleException("LAB_SPECIMEN_REJECTION_REASON_TOO_LONG", "Lý do từ chối tối đa 500 ký tự");
        }
        this.decision = decision;
        this.rejectionReason = decision == LabSpecimenDecision.REJECTED ? trimmed : null;
        this.receivedBy = Objects.requireNonNull(actor);
        this.receivedAt = Objects.requireNonNull(at);
    }

    public UUID getSpecimenId() { return specimenId; }
    public String getAccession() { return accession; }
    public UUID getPatientId() { return patientId; }
    public String getSpecimenType() { return specimenType; }
    public List<UUID> getTestIds() { return testIds; }
    public UUID getReplacesSpecimenId() { return replacesSpecimenId; }
    public UUID getCollectedBy() { return collectedBy; }
    public Instant getCollectedAt() { return collectedAt; }
    public UUID getReceivedBy() { return receivedBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public LabSpecimenDecision getDecision() { return decision; }
    public String getRejectionReason() { return rejectionReason; }
}
