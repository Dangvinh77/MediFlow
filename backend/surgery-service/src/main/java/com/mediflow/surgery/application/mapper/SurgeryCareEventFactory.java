package com.mediflow.surgery.application.mapper;

import com.mediflow.surgery.application.event.SurgeryCareEvent;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Pure mapping from committed domain evidence. Retry never changes source IDs or business time. */
public final class SurgeryCareEventFactory {
    private SurgeryCareEventFactory() { }

    public static SurgeryCareEvent created(SurgeryCase value, List<SurgeryPlannedItem> items) {
        if (value.getStatus() != SurgeryStatus.REQUESTED || value.getRevision() != 0 || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Case-created charge intent requires the exact newly created case and explicit planned items");
        }
        var keys = new HashSet<String>();
        for (var item : items) {
            if (item == null || !keys.add(item.itemCode())) throw new IllegalArgumentException("Duplicate planned item code");
        }
        var episode = value.getCareEpisode();
        var lines = items.stream().sorted(Comparator.comparing(SurgeryPlannedItem::itemCode))
                .map(item -> new SurgeryCareEvent.PlannedItem(item.itemCode(), item.priceCode(), item.quantity())).toList();
        var payload=new SurgeryCareEvent.Created(value.getSurgeryCaseId(),value.getSurgeryRequestId(),value.getPatientId(),
                value.getDepartmentId(),episode.type().name(),episode.episodeId(),episode.admissionId(),episode.recordId(),0,1,
                "SURGERY",value.getSurgeryCaseId(),value.getProcedureCode(),value.getPriority().name(),value.getRequestedBy(),lines,value.getRequestedAt());
        return envelope(SurgeryCareEvent.CASE_CREATED,value.getSurgeryCaseId(),value.getRequestedAt(),value,payload);
    }

    public static SurgeryCareEvent ready(SurgeryCase value, SurgerySchedule schedule, ReadinessSnapshot snapshot) {
        if (value.getStatus() != SurgeryStatus.READY || !snapshot.equals(value.getReadinessSnapshot())
                || !snapshot.isReady() || !value.getSurgeryCaseId().equals(schedule.surgeryCaseId())
                || snapshot.dependencyRevisions().stream().noneMatch(proof ->
                    proof.dependencyType() == SurgeryDependencyType.SCHEDULE
                    && proof.sourceId().equals(schedule.scheduleId()) && proof.revision() == schedule.revision())) {
            throw new IllegalArgumentException("READY requires exact stored readiness/schedule evidence");
        }
        var episode = value.getCareEpisode();
        var payload = new SurgeryCareEvent.Ready(value.getSurgeryCaseId(), value.getSurgeryRequestId(),
                value.getPatientId(), value.getDepartmentId(), episode.type().name(), episode.episodeId(),
                episode.admissionId(), episode.recordId(), value.getRevision(), schedule.scheduleId(),
                schedule.revision(), schedule.roomId(), schedule.startsAt(), schedule.endsAt(),
                snapshot.snapshotId(), snapshot.evaluatedAt(), false, false);
        return envelope(SurgeryCareEvent.READY, snapshot.snapshotId(), snapshot.evaluatedAt(), value, payload);
    }

    public static SurgeryCareEvent completed(SurgeryCase value, SurgeryResult result) {
        if (value.getStatus() != SurgeryStatus.COMPLETED || !value.getSurgeryCaseId().equals(result.surgeryCaseId())
                || !value.getCompletedAt().equals(result.recordedAt())) {
            throw new IllegalArgumentException("COMPLETED requires the exact immutable stored result");
        }
        var episode = value.getCareEpisode();
        var items = result.performedItems().stream().map(item -> new SurgeryCareEvent.PerformedItem(
                item.performedItemId(), item.itemCode(), item.priceCode(), item.quantity())).toList();
        var payload = new SurgeryCareEvent.Completed(value.getSurgeryCaseId(), value.getSurgeryRequestId(),
                value.getPatientId(), value.getDepartmentId(), episode.type().name(), episode.episodeId(),
                episode.admissionId(), episode.recordId(), value.getRevision(), result.resultId(), 1,
                result.procedureCode(), result.methodCode(), result.treatmentOutcomeCode(),
                result.complicationGroupCode(), null, items, result.actualStartAt(), result.actualEndAt(), result.recordedAt());
        return envelope(SurgeryCareEvent.COMPLETED, result.resultId(), result.recordedAt(), value, payload);
    }

    public static SurgeryCareEvent cancelled(SurgeryCase value) {
        if (value.getStatus() != SurgeryStatus.CANCELLED
                || value.getCancelledBy().actorType() != SurgeryActorType.HUMAN) {
            throw new IllegalArgumentException("V1 cancellation requires persisted human cancellation evidence");
        }
        var transition = value.getStatusHistory().getLast();
        String stage = switch (transition.previousStatus()) {
            case REQUESTED -> "BEFORE_PREOP";
            case PREOP_IN_PROGRESS, READY -> "AFTER_PREOP";
            case SCHEDULED -> "BEFORE_START";
            default -> throw new IllegalArgumentException("Post-start cancellation is unsupported in V1");
        };
        var episode = value.getCareEpisode();
        UUID cancellationId = operationId(SurgeryCareEvent.CANCELLED, value.getSurgeryCaseId());
        var payload = new SurgeryCareEvent.Cancelled(value.getSurgeryCaseId(), value.getSurgeryRequestId(),
                value.getPatientId(), value.getDepartmentId(), episode.type().name(), episode.episodeId(),
                episode.admissionId(), episode.recordId(), value.getRevision(), cancellationId, 1, stage,
                value.getCancellationReason(), "PRE_START_CANCELLATION", value.getCancelledByAccountId(),
                value.getCancelledBy().verifiedStaffId(), value.getCancelledAt());
        return envelope(SurgeryCareEvent.CANCELLED, cancellationId, value.getCancelledAt(), value, payload);
    }

    private static SurgeryCareEvent envelope(String type, UUID sourceId, Instant at,
            SurgeryCase value, SurgeryCareEvent.Payload payload) {
        return new SurgeryCareEvent(operationId(type, sourceId), type, SurgeryCareEvent.VERSION, at,
                value.getStatusHistory().getLast().correlationId(), SurgeryCareEvent.PRODUCER, payload);
    }

    public static SurgeryCareEvent invalidated(SurgeryCase value, ReadinessSnapshot prior,
            SurgerySchedule schedule, Instant at, String reasonCode, String correlationId) {
        if (value.getStatus() != SurgeryStatus.PREOP_IN_PROGRESS || value.getReadinessSnapshot() != null
                || !value.getSurgeryCaseId().equals(schedule.surgeryCaseId())
                || !value.getSurgeryCaseId().equals(prior.surgeryCaseId())
                || prior.dependencyRevisions().stream().noneMatch(proof ->
                    proof.dependencyType() == SurgeryDependencyType.SCHEDULE
                    && proof.sourceId().equals(schedule.scheduleId()) && proof.revision() == schedule.revision())
                || value.getStatusHistory().stream().noneMatch(transition ->
                    transition.occurredAt().equals(at) && transition.correlationId().equals(correlationId)
                    && transition.reason().equals(reasonCode)
                    && (transition.previousStatus() == SurgeryStatus.READY
                        || transition.previousStatus() == SurgeryStatus.SCHEDULED))) {
            throw new IllegalArgumentException("Invalidation requires the exact cleared readiness and committed transition");
        }
        var episode = value.getCareEpisode();
        var payload = new SurgeryCareEvent.Invalidated(value.getSurgeryCaseId(), value.getSurgeryRequestId(),
                value.getPatientId(), value.getDepartmentId(), episode.type().name(), episode.episodeId(),
                episode.admissionId(), episode.recordId(), value.getRevision(), prior.snapshotId(),
                schedule.scheduleId(), schedule.revision(), reasonCode, at);
        return new SurgeryCareEvent(operationId(SurgeryCareEvent.READINESS_INVALIDATED, prior.snapshotId()),
                SurgeryCareEvent.READINESS_INVALIDATED, SurgeryCareEvent.VERSION, at, correlationId,
                SurgeryCareEvent.PRODUCER, payload);
    }

    private static UUID operationId(String type, UUID sourceId) {
        return UUID.nameUUIDFromBytes((type + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
    }
}
