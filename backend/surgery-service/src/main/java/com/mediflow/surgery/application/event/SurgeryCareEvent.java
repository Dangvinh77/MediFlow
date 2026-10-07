package com.mediflow.surgery.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Surgery-owned V1 wire. It never contains authoritative prices or clinical narrative. */
public record SurgeryCareEvent(UUID eventId, String eventType, int version, Instant occurredAt,
        String correlationId, String producer, Payload payload) {
    public static final int VERSION = 1;
    public static final String PRODUCER = "surgery-service";
    public static final String CASE_CREATED = "surgery.case.created";
    public static final String READY = "surgery.ready";
    public static final String READINESS_INVALIDATED = "surgery.readiness.invalidated";
    public static final String COMPLETED = "surgery.completed";
    public static final String CANCELLED = "surgery.cancelled";

    public SurgeryCareEvent {
        if (eventId == null || version != VERSION || occurredAt == null || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 128
                || !PRODUCER.equals(producer) || payload == null
                || !(READY.equals(eventType) && payload instanceof Ready
                    || CASE_CREATED.equals(eventType) && payload instanceof Created
                    || READINESS_INVALIDATED.equals(eventType) && payload instanceof Invalidated
                    || COMPLETED.equals(eventType) && payload instanceof Completed
                    || CANCELLED.equals(eventType) && payload instanceof Cancelled)) {
            throw new IllegalArgumentException("Invalid Surgery V1 envelope");
        }
        if (payload.surgeryCaseId() == null || payload.surgeryRequestId() == null || payload.patientId() == null
                || payload.departmentId() == null || payload.careEpisodeId() == null || payload.caseRevision() < 0
                || !("ADMISSION".equals(payload.careEpisodeType())
                        && payload.careEpisodeId().equals(payload.admissionId())
                    || "OUTPATIENT_VISIT".equals(payload.careEpisodeType()) && payload.admissionId() == null)) {
            throw new IllegalArgumentException("Invalid exact Surgery care identity");
        }
        Instant businessAt = switch (payload) {
            case Created value -> value.requestedAt();
            case Ready value -> value.readyAt();
            case Invalidated value -> value.invalidatedAt();
            case Completed value -> value.recordedAt();
            case Cancelled value -> value.cancelledAt();
        };
        if (!occurredAt.equals(businessAt)) throw new IllegalArgumentException("Envelope time differs from the committed Surgery fact");
    }

    public sealed interface Payload permits Created, Ready, Invalidated, Completed, Cancelled {
        UUID surgeryCaseId(); UUID surgeryRequestId(); UUID patientId(); UUID departmentId();
        String careEpisodeType(); UUID careEpisodeId(); UUID admissionId(); UUID recordId(); long caseRevision();
    }

    public record Invalidated(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId, long caseRevision,
            UUID readinessSnapshotId, UUID scheduleId, long scheduleRevision, String reasonCode,
            Instant invalidatedAt) implements Payload {
        public Invalidated {
            if (caseRevision < 1 || readinessSnapshotId == null || scheduleId == null || scheduleRevision < 1
                    || invalidatedAt == null || reasonCode == null
                    || !java.util.Set.of("READINESS_EXPIRED", "ORGANIZATION_AUTHORITY_CHANGED",
                        "FINANCIAL_CLEARANCE_CHANGED", "CONSENT_CHANGED", "CONSENT_REVOKED", "CHECKLIST_CHANGED",
                        "SCHEDULE_REPLACED", "READINESS_RECHECK_FAILED").contains(reasonCode)) {
                throw new IllegalArgumentException("Invalid Surgery readiness invalidation");
            }
        }
    }

    public record Created(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId, long caseRevision,
            int sourceRevision, String sourceType, UUID sourceId, String procedureCode, String priority,
            UUID requestedBy, List<PlannedItem> plannedItems, Instant requestedAt) implements Payload {
        public Created {
            if (caseRevision != 0 || sourceRevision != 1 || !"SURGERY".equals(sourceType)
                    || surgeryCaseId == null || !surgeryCaseId.equals(sourceId) || requestedBy == null
                    || requestedAt == null || !code(procedureCode) || priority == null
                    || !java.util.Set.of("ROUTINE", "URGENT", "EMERGENCY").contains(priority) || plannedItems == null
                    || plannedItems.isEmpty()) throw new IllegalArgumentException("Invalid case-created fact");
            plannedItems = List.copyOf(plannedItems);
            if (plannedItems.stream().map(PlannedItem::itemCode).distinct().count() != plannedItems.size())
                throw new IllegalArgumentException("Duplicate planned item");
        }
    }
    public record PlannedItem(String itemCode,String priceCode,BigDecimal quantity) {
        public PlannedItem {
            var value = new com.mediflow.surgery.domain.model.SurgeryPlannedItem(itemCode, priceCode, quantity);
            quantity = value.quantity();
        }
    }

    public record Ready(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId, long caseRevision,
            UUID scheduleId, long scheduleRevision, UUID roomId, Instant plannedStartAt, Instant plannedEndAt,
            UUID readinessSnapshotId, Instant readyAt, boolean emergencyOverrideUsed,
            boolean reservationConfirmed) implements Payload {
        public Ready {
            if (caseRevision < 1 || scheduleId == null || scheduleRevision < 1 || roomId == null
                    || plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)
                    || readinessSnapshotId == null || readyAt == null || reservationConfirmed || emergencyOverrideUsed)
                throw new IllegalArgumentException("READY is provisional, not reservation/override authority");
        }
    }

    public record Completed(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId, long caseRevision,
            UUID resultId, int sourceRevision, String procedureCode, String methodCode, String outcomeCode,
            String complicationsCategory, String complicationsSummary, List<PerformedItem> performedItems,
            Instant startedAt, Instant completedAt, Instant recordedAt) implements Payload {
        public Completed {
            if (caseRevision < 1 || resultId == null || sourceRevision != 1 || !code(procedureCode)
                    || !code(methodCode) || !code(outcomeCode) || complicationsSummary != null
                    || (complicationsCategory != null && !complicationsCategory.matches("[A-Za-z0-9._-]{1,64}"))
                    || startedAt == null || completedAt == null || !completedAt.isAfter(startedAt)
                    || recordedAt == null || recordedAt.isBefore(completedAt) || performedItems == null)
                throw new IllegalArgumentException("Invalid immutable Surgery completion");
            performedItems = List.copyOf(performedItems);
            if (performedItems.stream().map(PerformedItem::performedItemId).distinct().count() != performedItems.size())
                throw new IllegalArgumentException("Duplicate performed item");
        }
    }

    public record Cancelled(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId, long caseRevision,
            UUID cancellationId, int sourceRevision, String cancellationStage, String reason,
            String reasonCode, UUID cancelledBy, UUID cancelledByStaffId, Instant cancelledAt) implements Payload {
        public Cancelled {
            if (caseRevision < 1 || cancellationId == null || sourceRevision != 1 || cancellationStage == null
                    || !java.util.Set.of("BEFORE_PREOP", "AFTER_PREOP", "BEFORE_START").contains(cancellationStage)
                    || reason == null || reason.isBlank() || reason.length() > 1000
                    || !"PRE_START_CANCELLATION".equals(reasonCode) || cancelledBy == null || cancelledAt == null)
                throw new IllegalArgumentException("Invalid pre-start cancellation");
        }
    }

    public record PerformedItem(UUID performedItemId, String itemCode, String priceCode, BigDecimal quantity) {
        public PerformedItem {
            var value = new com.mediflow.surgery.domain.model.SurgeryPerformedItem(performedItemId, itemCode, priceCode, quantity);
            itemCode = value.itemCode(); priceCode = value.priceCode(); quantity = value.quantity();
        }
    }
    private static boolean code(String value) { return value != null && !value.isBlank() && value.length() <= 64; }
}
