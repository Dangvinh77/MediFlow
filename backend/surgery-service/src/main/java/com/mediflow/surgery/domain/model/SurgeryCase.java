package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Surgery aggregate root. Persistence and cross-service checks are performed outside the domain. */
public final class SurgeryCase {

    private static final int MAX_PROCEDURE_CODE_LENGTH = 64;
    private static final int MAX_INDICATION_LENGTH = 4_000;
    private static final int MAX_REASON_LENGTH = 1_000;

    private final UUID surgeryCaseId;
    private final UUID surgeryRequestId;
    private final CareEpisode careEpisode;
    private final UUID patientId;
    private final UUID departmentId;
    private final UUID requestedBy;
    private final String procedureCode;
    private final String indication;
    private final SurgeryPriority priority;
    private final Instant requestedAt;
    private final List<SurgeryStateChange> statusHistory = new ArrayList<>();
    private final List<SurgeryCaseAuditEntry> revisionHistory = new ArrayList<>();

    private SurgeryStatus status;
    private ReadinessSnapshot readinessSnapshot;
    private Instant readyAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private SurgeryAuditActor cancelledBy;
    private String cancellationReason;
    private long revision;

    private SurgeryCase(
            UUID surgeryCaseId,
            UUID surgeryRequestId,
            CareEpisode careEpisode,
            UUID patientId,
            UUID departmentId,
            UUID requestedBy,
            String procedureCode,
            String indication,
            SurgeryPriority priority,
            Instant requestedAt,
            SurgeryAuditActor createdBy,
            String correlationId) {
        this.surgeryCaseId = required(surgeryCaseId, "SURGERY_CASE_ID_REQUIRED");
        this.surgeryRequestId = required(surgeryRequestId, "SURGERY_REQUEST_ID_REQUIRED");
        this.careEpisode = required(careEpisode, "SURGERY_EPISODE_REQUIRED");
        this.patientId = required(patientId, "SURGERY_PATIENT_ID_REQUIRED");
        this.departmentId = required(departmentId, "SURGERY_DEPARTMENT_ID_REQUIRED");
        this.requestedBy = required(requestedBy, "SURGERY_REQUESTER_REQUIRED");
        this.procedureCode = requiredText(
                procedureCode, MAX_PROCEDURE_CODE_LENGTH, "SURGERY_PROCEDURE_CODE_INVALID");
        this.indication = requiredText(indication, MAX_INDICATION_LENGTH, "SURGERY_INDICATION_INVALID");
        this.priority = required(priority, "SURGERY_PRIORITY_REQUIRED");
        this.requestedAt = required(requestedAt, "SURGERY_REQUEST_TIME_REQUIRED");
        this.status = SurgeryStatus.REQUESTED;
        this.statusHistory.add(new SurgeryStateChange(
                null, SurgeryStatus.REQUESTED, required(createdBy, "SURGERY_ACTOR_REQUIRED"),
                "CASE_CREATED", requestedAt, correlationId));
        this.revisionHistory.add(new SurgeryCaseAuditEntry(0, "CASE_CREATED", null,
                SurgeryStatus.REQUESTED, createdBy, requestedAt, correlationId));
    }

    public static SurgeryCase create(
            UUID surgeryCaseId,
            UUID surgeryRequestId,
            CareEpisode careEpisode,
            UUID patientId,
            UUID departmentId,
            UUID requestedBy,
            String procedureCode,
            String indication,
            SurgeryPriority priority,
            Instant requestedAt,
            SurgeryAuditActor createdBy,
            String correlationId) {
        return new SurgeryCase(
                surgeryCaseId,
                surgeryRequestId,
                careEpisode,
                patientId,
                departmentId,
                requestedBy,
                procedureCode,
                indication,
                priority,
                requestedAt,
                createdBy,
                correlationId);
    }

    /** Rebuilds a persisted aggregate without replaying create or synthesizing initial history. */
    public static SurgeryCase restore(
            UUID caseId,
            UUID surgeryRequestId,
            CareEpisode careEpisode,
            UUID patientId,
            UUID departmentId,
            UUID requestedBy,
            String procedureCode,
            String indication,
            SurgeryPriority priority,
            Instant requestedAt,
            SurgeryStatus status,
            ReadinessSnapshot readinessSnapshot,
            Instant readyAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            SurgeryAuditActor cancelledBy,
            String cancellationReason,
            long businessRevision,
            List<SurgeryStateChange> history,
            List<SurgeryCaseAuditEntry> revisionHistory) {
        SurgeryCase restored = new SurgeryCase(caseId, surgeryRequestId, careEpisode, patientId,
                departmentId, requestedBy, procedureCode, indication, priority, requestedAt,
                status, readinessSnapshot, readyAt, startedAt, completedAt, cancelledAt,
                cancelledBy, cancellationReason, businessRevision, history, revisionHistory);
        restored.validateRestoredState();
        return restored;
    }

    private SurgeryCase(
            UUID caseId,
            UUID surgeryRequestId,
            CareEpisode careEpisode,
            UUID patientId,
            UUID departmentId,
            UUID requestedBy,
            String procedureCode,
            String indication,
            SurgeryPriority priority,
            Instant requestedAt,
            SurgeryStatus status,
            ReadinessSnapshot readinessSnapshot,
            Instant readyAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            SurgeryAuditActor cancelledBy,
            String cancellationReason,
            long businessRevision,
            List<SurgeryStateChange> history,
            List<SurgeryCaseAuditEntry> revisionHistory) {
        this.surgeryCaseId = required(caseId, "SURGERY_CASE_ID_REQUIRED");
        this.surgeryRequestId = required(surgeryRequestId, "SURGERY_REQUEST_ID_REQUIRED");
        this.careEpisode = required(careEpisode, "SURGERY_EPISODE_REQUIRED");
        this.patientId = required(patientId, "SURGERY_PATIENT_ID_REQUIRED");
        this.departmentId = required(departmentId, "SURGERY_DEPARTMENT_ID_REQUIRED");
        this.requestedBy = required(requestedBy, "SURGERY_REQUESTER_REQUIRED");
        this.procedureCode = requiredText(procedureCode, MAX_PROCEDURE_CODE_LENGTH,
                "SURGERY_PROCEDURE_CODE_INVALID");
        this.indication = requiredText(indication, MAX_INDICATION_LENGTH, "SURGERY_INDICATION_INVALID");
        this.priority = required(priority, "SURGERY_PRIORITY_REQUIRED");
        this.requestedAt = required(requestedAt, "SURGERY_REQUEST_TIME_REQUIRED");
        this.status = required(status, "SURGERY_STATUS_REQUIRED");
        this.readinessSnapshot = readinessSnapshot;
        this.readyAt = readyAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = cancellationReason;
        if (businessRevision < 0 || history == null || history.isEmpty()
                || revisionHistory == null || revisionHistory.isEmpty()
                || history.stream().anyMatch(java.util.Objects::isNull)
                || revisionHistory.stream().anyMatch(java.util.Objects::isNull)) {
            throw rule("SURGERY_AGGREGATE_RESTORE_INVALID",
                    "Aggregate đã lưu phải có revision cùng lịch sử trạng thái và kiểm toán");
        }
        this.revision = businessRevision;
        this.statusHistory.addAll(List.copyOf(history));
        this.revisionHistory.addAll(List.copyOf(revisionHistory));
    }

    public void beginPreop(SurgeryAuditActor actor, String correlationId, Instant at) {
        transition(SurgeryStatus.REQUESTED, SurgeryStatus.PREOP_IN_PROGRESS,
                actor, "PREOP_STARTED", correlationId, at);
    }

    public void markReady(ReadinessSnapshot snapshot, SurgeryAuditActor actor, String correlationId) {
        requireStatus(SurgeryStatus.PREOP_IN_PROGRESS);
        requireSnapshotForThisCase(snapshot);
        if (!snapshot.isReady()) {
            throw rule("SURGERY_NOT_READY", "Ca phẫu thuật chưa đạt đầy đủ readiness guards");
        }
        transition(SurgeryStatus.PREOP_IN_PROGRESS, SurgeryStatus.READY,
                actor, "ALL_READINESS_GUARDS_SATISFIED", correlationId, snapshot.evaluatedAt());
        this.readinessSnapshot = snapshot;
        this.readyAt = snapshot.evaluatedAt();
    }

    /** Finalization occurs only after the application has atomically reserved room/team resources. */
    public void finalizeSchedule(SurgeryAuditActor actor, String correlationId, Instant at) {
        requireStatus(SurgeryStatus.READY);
        if (readinessSnapshot == null || !readinessSnapshot.isValidAt(at)) {
            throw rule("SURGERY_NOT_READY", "Không thể chốt lịch khi readiness snapshot không hợp lệ");
        }
        transition(SurgeryStatus.READY, SurgeryStatus.SCHEDULED,
                actor, "SCHEDULE_FINALIZED", correlationId, at);
    }

    /** START rechecks the complete guard set; a prior READY snapshot alone is never sufficient. */
    public void start(ReadinessSnapshot currentSnapshot, SurgeryAuditActor actor,
                      String correlationId, Instant at) {
        requireStatus(SurgeryStatus.SCHEDULED);
        requireSnapshotForThisCase(currentSnapshot);
        if (!currentSnapshot.isValidAt(at)) {
            throw rule("SURGERY_NOT_READY", "Readiness đã thay đổi trước thời điểm bắt đầu");
        }
        if (at == null || at.isBefore(readyAt)) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm bắt đầu không hợp lệ");
        }
        if (currentSnapshot.evaluatedAt().isAfter(at)) {
            throw rule("SURGERY_INVALID_TIME", "Readiness phải được đánh giá trước khi bắt đầu");
        }
        transition(SurgeryStatus.SCHEDULED, SurgeryStatus.IN_PROGRESS,
                actor, "SURGERY_STARTED", correlationId, at);
        this.readinessSnapshot = currentSnapshot;
        this.startedAt = at;
    }

    public void complete(SurgeryAuditActor actor, String correlationId, Instant at) {
        requireStatus(SurgeryStatus.IN_PROGRESS);
        if (at == null || startedAt == null || !at.isAfter(startedAt)) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm hoàn tất phải sau thời điểm bắt đầu");
        }
        transition(SurgeryStatus.IN_PROGRESS, SurgeryStatus.COMPLETED,
                actor, "SURGERY_COMPLETED", correlationId, at);
        this.completedAt = at;
    }

    /**
     * Guard-changing mutations invalidate READY/SCHEDULED and require a fresh evaluation.
     * The application transaction must release any finalized resource reservation with this change.
     */
    public void invalidateReadiness(SurgeryAuditActor actor, String correlationId,
                                    Instant at, String reason) {
        if (status != SurgeryStatus.READY && status != SurgeryStatus.SCHEDULED) {
            throw invalidTransition("Chỉ có thể vô hiệu hóa readiness trước khi bắt đầu ca mổ");
        }
        String validReason = requiredText(reason, MAX_REASON_LENGTH, "SURGERY_INVALIDATION_REASON_REQUIRED");
        Instant invalidatedAt = required(at, "SURGERY_TRANSITION_TIME_REQUIRED");
        transition(status, SurgeryStatus.PREOP_IN_PROGRESS,
                actor, validReason, correlationId, invalidatedAt);
        this.readinessSnapshot = null;
        this.readyAt = null;
    }

    /** V1 cancellation is allowed only before IN_PROGRESS; the stage is derived from persisted state. */
    public void cancel(SurgeryAuditActor actor, String correlationId, Instant at, String reason) {
        if (status == SurgeryStatus.IN_PROGRESS
                || status == SurgeryStatus.COMPLETED
                || status == SurgeryStatus.CANCELLED) {
            throw invalidTransition("V1 chỉ cho phép hủy ca trước khi bắt đầu phẫu thuật");
        }
        String validReason = requiredText(reason, MAX_REASON_LENGTH, "SURGERY_CANCELLATION_REASON_REQUIRED");
        Instant cancelledAt = required(at, "SURGERY_CANCELLATION_TIME_REQUIRED");
        transition(status, SurgeryStatus.CANCELLED, actor, validReason, correlationId, cancelledAt);
        // Keep the immutable snapshot row/history, but remove the case's active readiness pointer.
        this.readinessSnapshot = null;
        this.readyAt = null;
        this.cancelledBy = actor;
        this.cancellationReason = validReason;
        this.cancelledAt = cancelledAt;
    }

    /**
     * Advances the case business revision for an atomic child mutation.
     * Call in the same application transaction after changing a case-owned child.
     */
    public void recordBusinessMutation(SurgeryAuditActor actor, String correlationId,
                                       Instant at, String changeCode) {
        SurgeryAuditActor validActor = required(actor, "SURGERY_ACTOR_REQUIRED");
        Instant changedAt = required(at, "SURGERY_TRANSITION_TIME_REQUIRED");
        if (status == SurgeryStatus.COMPLETED || status == SurgeryStatus.CANCELLED) {
            throw invalidTransition("Không thể thay đổi dữ liệu của ca đã kết thúc");
        }
        Instant lastChangeAt = revisionHistory.getLast().occurredAt();
        if (changedAt.isBefore(lastChangeAt)) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm thay đổi dữ liệu không được đi lùi");
        }
        SurgeryCaseAuditEntry audit = new SurgeryCaseAuditEntry(
                revision + 1, changeCode, status, status, validActor, changedAt, correlationId);
        revision++;
        revisionHistory.add(audit);
    }

    private void transition(
            SurgeryStatus expected,
            SurgeryStatus next,
            SurgeryAuditActor actor,
            String reason,
            String correlationId,
            Instant at) {
        requireStatus(expected);
        SurgeryAuditActor validActor = required(actor, "SURGERY_ACTOR_REQUIRED");
        Instant changedAt = required(at, "SURGERY_TRANSITION_TIME_REQUIRED");
        if (!revisionHistory.isEmpty()
                && changedAt.isBefore(revisionHistory.getLast().occurredAt())) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm thay đổi dữ liệu không được đi lùi");
        }
        SurgeryStatus previous = status;
        SurgeryStateChange change = new SurgeryStateChange(
                previous, next, validActor, reason, changedAt, correlationId);
        SurgeryCaseAuditEntry audit = new SurgeryCaseAuditEntry(
                revision + 1, reason, previous, next, validActor, changedAt, correlationId);
        status = next;
        revision++;
        statusHistory.add(change);
        revisionHistory.add(audit);
    }

    private void validateRestoredState() {
        if (statusHistory.getFirst() == null
                || statusHistory.getFirst().previousStatus() != null
                || statusHistory.getFirst().newStatus() != SurgeryStatus.REQUESTED
                || !statusHistory.getFirst().occurredAt().equals(requestedAt)
                || revision + 1 != revisionHistory.size()) {
            throw invalidRestore();
        }
        SurgeryStatus prior = SurgeryStatus.REQUESTED;
        Instant priorTime = requestedAt;
        boolean hasStarted = false;
        for (int index = 1; index < statusHistory.size(); index++) {
            SurgeryStateChange change = statusHistory.get(index);
            if (change == null || change.previousStatus() != prior
                    || !isAllowedTransition(change.previousStatus(), change.newStatus())
                    || change.occurredAt().isBefore(priorTime)) {
                throw invalidRestore();
            }
            hasStarted |= change.newStatus() == SurgeryStatus.IN_PROGRESS;
            prior = change.newStatus();
            priorTime = change.occurredAt();
        }
        List<SurgeryStateChange> transitionsFromAudit = new ArrayList<>();
        for (int index = 0; index < revisionHistory.size(); index++) {
            SurgeryCaseAuditEntry audit = revisionHistory.get(index);
            if (audit == null || audit.revision() != index
                    || index == 0 && (!"CASE_CREATED".equals(audit.changeCode())
                    || audit.previousStatus() != null || audit.newStatus() != SurgeryStatus.REQUESTED
                    || !audit.occurredAt().equals(requestedAt))) {
                throw invalidRestore();
            }
            if (index > 0 && audit.occurredAt().isBefore(revisionHistory.get(index - 1).occurredAt())) {
                throw invalidRestore();
            }
            if (index == 0 || audit.changesStatus()) {
                transitionsFromAudit.add(new SurgeryStateChange(audit.previousStatus(), audit.newStatus(),
                        audit.actor(), audit.changeCode(), audit.occurredAt(), audit.correlationId()));
            } else if (index > 0 && (audit.previousStatus() != audit.newStatus()
                    || audit.newStatus() != stateAtRevision(audit.revision()))) {
                throw invalidRestore();
            }
        }
        if (!transitionsFromAudit.equals(statusHistory)) {
            throw invalidRestore();
        }
        SurgeryStateChange lastTransition = statusHistory.getLast();
        if ((status == SurgeryStatus.CANCELLED
                && (!lastTransition.occurredAt().equals(cancelledAt)
                || !lastTransition.actor().equals(cancelledBy)
                || !lastTransition.reason().equals(cancellationReason)))
                || (status == SurgeryStatus.COMPLETED
                && !lastTransition.occurredAt().equals(completedAt))) {
            throw invalidRestore();
        }
        if (prior != status || (status == SurgeryStatus.READY || status == SurgeryStatus.SCHEDULED)
                && (readinessSnapshot == null || readyAt == null
                || !readinessSnapshot.isValidAt(readinessSnapshot.evaluatedAt()))
                || (readinessSnapshot == null) != (readyAt == null)
                || (readinessSnapshot != null && !surgeryCaseId.equals(readinessSnapshot.surgeryCaseId()))
                || hasStarted != (startedAt != null)
                || (status == SurgeryStatus.COMPLETED) != (completedAt != null)
                || (status == SurgeryStatus.CANCELLED)
                != (cancelledAt != null && cancelledBy != null && cancellationReason != null && !cancellationReason.isBlank())
                || (status != SurgeryStatus.CANCELLED
                && (cancelledAt != null || cancelledBy != null || cancellationReason != null))) {
            throw invalidRestore();
        }
        if ((startedAt != null && (startedAt.isBefore(requestedAt)
                || completedAt != null && !completedAt.isAfter(startedAt)))
                || (cancelledAt != null && cancelledAt.isBefore(requestedAt))) {
            throw invalidRestore();
        }
    }

    private static boolean isAllowedTransition(SurgeryStatus from, SurgeryStatus to) {
        return switch (from) {
            case REQUESTED -> to == SurgeryStatus.PREOP_IN_PROGRESS || to == SurgeryStatus.CANCELLED;
            case PREOP_IN_PROGRESS -> to == SurgeryStatus.READY || to == SurgeryStatus.CANCELLED;
            case READY -> to == SurgeryStatus.SCHEDULED || to == SurgeryStatus.PREOP_IN_PROGRESS
                    || to == SurgeryStatus.CANCELLED;
            case SCHEDULED -> to == SurgeryStatus.IN_PROGRESS || to == SurgeryStatus.PREOP_IN_PROGRESS
                    || to == SurgeryStatus.CANCELLED;
            case IN_PROGRESS -> to == SurgeryStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private SurgeryStatus stateAtRevision(long revision) {
        SurgeryStatus state = SurgeryStatus.REQUESTED;
        for (SurgeryCaseAuditEntry audit : revisionHistory) {
            if (audit.revision() > revision) {
                break;
            }
            state = audit.newStatus();
        }
        return state;
    }

    private static SurgeryRuleException invalidRestore() {
        return rule("SURGERY_AGGREGATE_RESTORE_INVALID",
                "Trạng thái đã lưu không khớp lịch sử và các mốc thời gian");
    }

    private void requireSnapshotForThisCase(ReadinessSnapshot snapshot) {
        if (snapshot == null || !surgeryCaseId.equals(snapshot.surgeryCaseId())) {
            throw rule("SURGERY_READINESS_SNAPSHOT_MISMATCH",
                    "Readiness snapshot không thuộc ca phẫu thuật này");
        }
    }

    private void requireStatus(SurgeryStatus expected) {
        if (status != expected) {
            throw invalidTransition("Trạng thái hiện tại không cho phép thao tác này");
        }
    }

    private static <T> T required(T value, String code) {
        if (value == null) {
            throw rule(code, "Dữ liệu bắt buộc của ca phẫu thuật bị thiếu");
        }
        return value;
    }

    private static String requiredText(String value, int maxLength, String code) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw rule(code, "Giá trị bắt buộc bị trống hoặc vượt độ dài cho phép");
        }
        return value.trim();
    }

    private static SurgeryRuleException invalidTransition(String message) {
        return rule("SURGERY_INVALID_TRANSITION", message);
    }

    private static SurgeryRuleException rule(String code, String message) {
        return new SurgeryRuleException(code, message);
    }

    public UUID getSurgeryCaseId() { return surgeryCaseId; }
    public UUID getSurgeryRequestId() { return surgeryRequestId; }
    public CareEpisode getCareEpisode() { return careEpisode; }
    public UUID getPatientId() { return patientId; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getProcedureCode() { return procedureCode; }
    public String getIndication() { return indication; }
    public SurgeryPriority getPriority() { return priority; }
    public SurgeryStatus getStatus() { return status; }
    public ReadinessSnapshot getReadinessSnapshot() { return readinessSnapshot; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getCancelledByAccountId() {
        return cancelledBy != null && cancelledBy.actorType() == SurgeryActorType.HUMAN
                ? cancelledBy.accountId() : null;
    }
    public SurgeryAuditActor getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
    public long getRevision() { return revision; }
    public List<SurgeryStateChange> getStatusHistory() { return List.copyOf(statusHistory); }
    public List<SurgeryCaseAuditEntry> getRevisionHistory() { return List.copyOf(revisionHistory); }
}
