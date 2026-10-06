package com.mediflow.surgery.application.dto.response;

import com.mediflow.surgery.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only, deliberately redacted view; no signature, evidence document or financial amount. */
public record SurgeryCaseDetails(
        Core caseDetails, Checklist checklist, List<Consent> consents,
        Schedule plannedSchedule, Result actualResult, Readiness readiness,
        List<Transition> statusHistory) {
    public SurgeryCaseDetails {
        consents = List.copyOf(consents);
        statusHistory = List.copyOf(statusHistory);
    }
    public record Core(UUID surgeryCaseId, UUID surgeryRequestId, CareEpisodeType episodeType,
            UUID episodeId, UUID admissionId, UUID medicalRecordId, UUID patientId,
            UUID departmentId, UUID requestedBy, String procedureCode, SurgeryPriority priority,
            SurgeryStatus status, long revision, Instant requestedAt, Instant startedAt,
            Instant completedAt, Instant cancelledAt) {}
    public record Checklist(UUID checklistSnapshotId, long templateRevision, long revision,
            boolean mandatoryComplete, List<Item> items) {
        public Checklist { items = List.copyOf(items); }
    }
    public record Item(UUID checklistItemId, String itemCode, boolean mandatory,
            SurgeryChecklistStatus status, long revision) {}
    public record Consent(UUID consentId, SurgeryConsentType consentType, boolean active,
            Instant signedAt) {}
    public record Schedule(UUID scheduleId, long revision, UUID roomId, Instant startsAt,
            Instant endsAt, List<TeamMember> team) {
        public Schedule { team = List.copyOf(team); }
    }
    public record TeamMember(UUID staffId, SurgeryTeamRole role) {}
    public record Result(UUID resultId, String procedureCode, String methodCode,
            String treatmentOutcomeCode, String complicationGroupCode, Instant actualStartAt,
            Instant actualEndAt, Instant recordedAt, List<PerformedItem> performedItems) {
        public Result { performedItems = List.copyOf(performedItems); }
    }
    public record PerformedItem(UUID performedItemId, String itemCode, String priceCode,
            java.math.BigDecimal quantity) {}
    /** Cached evidence only: this read never re-authorizes READY, finalization or START. */
    public record Readiness(UUID readinessSnapshotId, boolean snapshotValidNow, Instant evaluatedAt,
            Instant validUntil, List<String> blockingReasons) {
        public Readiness { blockingReasons = List.copyOf(blockingReasons); }
    }
    public record Transition(SurgeryStatus previousStatus, SurgeryStatus newStatus,
            Instant occurredAt) {}
}
