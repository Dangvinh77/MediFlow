package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabSpecimen;

public record LabSpecimenDTO(UUID specimenId, String accession, UUID patientId, String specimenType, List<UUID> testIds,
        UUID replacesSpecimenId, UUID collectedBy, Instant collectedAt, UUID receivedBy, Instant receivedAt,
        String decision, String rejectionReason) {

    public static LabSpecimenDTO of(LabSpecimen s) {
        return new LabSpecimenDTO(s.getSpecimenId(), s.getAccession(), s.getPatientId(), s.getSpecimenType(),
                s.getTestIds(), s.getReplacesSpecimenId(), s.getCollectedBy(), s.getCollectedAt(), s.getReceivedBy(),
                s.getReceivedAt(), s.getDecision() == null ? null : s.getDecision().name(), s.getRejectionReason());
    }
}
