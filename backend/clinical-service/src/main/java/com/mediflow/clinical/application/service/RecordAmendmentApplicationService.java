package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.AmendmentAssignmentRequest;
import com.mediflow.clinical.application.dto.request.AmendmentDecisionRequest;
import com.mediflow.clinical.application.dto.request.RecordAmendmentRequest;
import com.mediflow.clinical.application.dto.response.AmendmentAssignmentDTO;
import com.mediflow.clinical.application.dto.response.RecordAmendmentDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordReviewAuthorityPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.AmendmentAssignment;
import com.mediflow.clinical.domain.model.AmendmentSection;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordAmendment;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/**
 * The responsible doctor may directly apply an audited correction to a finished visit. A doctor assigned to correct
 * another doctor's record still needs an independent head or hospital reviewer decision. Nothing here rewrites the
 * visit, publishes an event or touches attached results.
 */
@Service
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class RecordAmendmentApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RecordAmendmentRepositoryPort amendments;
    private final MedicalRecordRepositoryPort records;
    private final CurrentClinicalActorPort actors;
    private final StaffLookupPort staff;
    private final SchedulingAuthorityPort authority;
    private final RecordReviewAuthorityPort reviewAuthority;
    private final RecordReadGuard readGuard;
    private final boolean recordAccessPolicyEnabled;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;
    private final CorrelationIdProvider correlationIds;

    @Autowired
    public RecordAmendmentApplicationService(RecordAmendmentRepositoryPort amendments, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, StaffLookupPort staff, SchedulingAuthorityPort authority,
            RecordReviewAuthorityPort reviewAuthority, RecordReadGuard readGuard, RecordAccessPolicyMode mode,
            IdempotentCommandPort idempotency, CorrelationIdProvider correlationIds) {
        this(amendments, records, actors, staff, authority, reviewAuthority, readGuard, mode.enabled(), idempotency,
                Clock.system(CLINIC_ZONE), correlationIds);
    }

    public RecordAmendmentApplicationService(RecordAmendmentRepositoryPort amendments, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, StaffLookupPort staff, SchedulingAuthorityPort authority,
            RecordReviewAuthorityPort reviewAuthority, RecordReadGuard readGuard, boolean recordAccessPolicyEnabled,
            IdempotentCommandPort idempotency, Clock clock, CorrelationIdProvider correlationIds) {
        this.amendments = amendments;
        this.records = records;
        this.actors = actors;
        this.staff = staff;
        this.authority = authority;
        this.reviewAuthority = reviewAuthority;
        this.readGuard = readGuard;
        this.recordAccessPolicyEnabled = recordAccessPolicyEnabled;
        this.idempotency = idempotency;
        this.clock = clock;
        this.correlationIds = correlationIds;
    }

    public IdempotentCommandPort.Result<RecordAmendmentDTO> request(UUID recordId, RecordAmendmentRequest request, String key) {
        ActorIdentity actor = doctor();
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/records/" + recordId + "/amendments";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), RecordAmendmentDTO.class, 201,
                ignored -> null, () -> requestNow(actor, recordId, request));
    }

    public IdempotentCommandPort.Result<RecordAmendmentDTO> decide(UUID recordId, UUID amendmentId,
            AmendmentDecisionRequest request, String key) {
        ActorIdentity actor = doctor();
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/records/" + recordId + "/amendments/" + amendmentId + "/decision";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), RecordAmendmentDTO.class, 200,
                ignored -> null, () -> decideNow(actor, recordId, amendmentId, request));
    }

    public IdempotentCommandPort.Result<AmendmentAssignmentDTO> assign(UUID recordId, AmendmentAssignmentRequest request,
            String key) {
        ActorIdentity actor = doctor();
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/records/" + recordId + "/amendment-assignments";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), AmendmentAssignmentDTO.class, 201,
                ignored -> null, () -> assignNow(actor, recordId, request));
    }

    /** The department head ends an assignment; the assignee can no longer request amendments for the visit. */
    @Transactional
    public void revokeAssignment(UUID recordId, UUID assignmentId) {
        ActorIdentity actor = doctor();
        MedicalRecord record = records.findById(recordId).orElseThrow(() -> notFound(recordId));
        if (!isHead(actor, record.getDepartmentId())) {
            throw new ForbiddenOperationException("AMENDMENT_ASSIGNMENT_DENIED", "Only the department head may revoke");
        }
        if (!amendments.assignmentExists(recordId, assignmentId)) {
            throw new ResourceNotFoundException("AMENDMENT_ASSIGNMENT_NOT_FOUND", "Assignment not found: " + assignmentId);
        }
        amendments.revokeAssignment(recordId, assignmentId, actor.staffId(), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<RecordAmendmentDTO> list(UUID recordId) {
        MedicalRecord record = records.findById(recordId).orElseThrow(() -> notFound(recordId));
        if (recordAccessPolicyEnabled) {
            readGuard.requireRecord(record);
        }
        return amendments.findByRecord(recordId).stream().map(RecordAmendmentApplicationService::dto).toList();
    }

    /** Pending amendments the caller may decide, oldest first; never the caller's own. */
    @Transactional(readOnly = true)
    public List<RecordAmendmentDTO> pending() {
        ActorIdentity actor = doctor();
        var pending = amendments.findPending().stream()
                .filter(item -> !actor.staffId().equals(item.amendment().getAuthorStaffId()))
                .toList();
        if (pending.isEmpty()) {
            return List.of();
        }
        if (isReviewer(actor)) {
            return logged(pending);
        }
        Map<UUID, Boolean> heads = new HashMap<>();
        for (UUID department : pending.stream().map(RecordAmendmentRepositoryPort.PendingAmendment::departmentId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new))) {
            heads.put(department, isHead(actor, department));
        }
        return logged(pending.stream().filter(item -> heads.getOrDefault(item.departmentId(), false)).toList());
    }

    /** The queue shows original and amended text, so each listed record gets a read entry in the access log. */
    private List<RecordAmendmentDTO> logged(List<RecordAmendmentRepositoryPort.PendingAmendment> shown) {
        for (UUID recordId : shown.stream().map(item -> item.amendment().getRecordId())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new))) {
            records.findById(recordId).ifPresent(record -> readGuard.logReviewRead(record.getPatientId(), recordId));
        }
        return shown.stream().map(item -> dto(item.amendment())).toList();
    }

    private RecordAmendmentDTO requestNow(ActorIdentity actor, UUID recordId, RecordAmendmentRequest request) {
        MedicalRecord record = finished(recordId);
        UUID assignedBy = null;
        boolean responsible = actor.staffId().equals(record.responsibleDoctorId());
        if (responsible && inDepartment(actor.staffId(), record)) {
            assignedBy = null;
        } else {
            var assignment = amendments.findAssignment(recordId, actor.staffId());
            if (assignment.isPresent() && inDepartment(actor.staffId(), record)) {
                assignedBy = assignment.get().getAssignedByStaffId();
            } else if (responsible) {
                throw new ForbiddenOperationException("AMENDMENT_AUTHOR_NOT_ELIGIBLE",
                        "The responsible doctor no longer works in this department; the head must assign a doctor");
            } else {
                throw new ForbiddenOperationException("CLINICAL_DOCTOR_NOT_ASSIGNED",
                        "Only the responsible doctor or a doctor assigned by the head may amend this visit");
            }
        }
        AmendmentSection section = request == null ? null : request.section();
        List<RecordAmendment> history = amendments.findByRecord(recordId);
        String currentText = section == null ? null : effectiveText(record, section, history);
        if (request != null && request.originalText() != null
                && !request.originalText().equals(currentText)) {
            throw new InvalidClinicalDataException("AMENDMENT_STALE_SOURCE",
                    "The section changed after it was opened; reload the record and submit against its current value");
        }
        Instant requestedAt = clock.instant();
        RecordAmendment amendment = RecordAmendment.request(recordId, section, currentText,
                request == null ? null : request.amendedText(), request == null ? null : request.reason(), actor.staffId(),
                assignedBy, requestedAt);
        if (assignedBy == null && actor.staffId().equals(record.responsibleDoctorId())) {
            amendment.applyByResponsibleDoctor(actor.staffId(), requestedAt);
        }
        amendments.insert(amendment);
        return dto(amendment);
    }

    private RecordAmendmentDTO decideNow(ActorIdentity actor, UUID recordId, UUID amendmentId, AmendmentDecisionRequest request) {
        MedicalRecord record = records.findByIdForUpdate(recordId).orElseThrow(() -> notFound(recordId));
        RecordAmendment amendment = amendments.findByIdForUpdate(amendmentId)
                .filter(found -> found.getRecordId().equals(recordId))
                .orElseThrow(() -> new ResourceNotFoundException("AMENDMENT_NOT_FOUND", "Amendment not found: " + amendmentId));
        amendment.requireDecidableBy(actor.staffId());
        String decision = request == null ? null : request.decision();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new InvalidClinicalDataException("AMENDMENT_INVALID", "decision must be APPROVED or REJECTED");
        }
        if (!isHead(actor, record.getDepartmentId()) && !isReviewer(actor)) {
            throw new ForbiddenOperationException("AMENDMENT_DECISION_DENIED",
                    "Only the department head or a hospital record reviewer may decide amendments");
        }
        if ("APPROVED".equals(decision)) {
            String currentText = effectiveText(record, amendment.getSection(), amendments.findByRecord(recordId));
            if (!Objects.equals(amendment.getOriginalText(), currentText)) {
                throw new InvalidClinicalDataException("AMENDMENT_STALE_SOURCE",
                        "The section changed after this request; reject it and submit a new correction against the current value");
            }
            amendment.approve(actor.staffId(), request.note(), clock.instant());
        } else {
            amendment.reject(actor.staffId(), request.note(), clock.instant());
        }
        amendments.decide(amendment);
        return dto(amendment);
    }

    private AmendmentAssignmentDTO assignNow(ActorIdentity actor, UUID recordId, AmendmentAssignmentRequest request) {
        MedicalRecord record = finished(recordId);
        if (!isHead(actor, record.getDepartmentId())) {
            throw new ForbiddenOperationException("AMENDMENT_ASSIGNMENT_DENIED", "Only the department head may assign");
        }
        if (inDepartment(record.responsibleDoctorId(), record)) {
            throw new InvalidClinicalDataException("AMENDMENT_INVALID",
                    "The responsible doctor still works in this department and amends the visit themselves");
        }
        UUID assignee = request == null ? null : request.assigneeStaffId();
        if (assignee == null || !inDepartment(assignee, record)) {
            throw new InvalidClinicalDataException("AMENDMENT_INVALID", "The assignee must be a doctor of this department");
        }
        AmendmentAssignment assignment = AmendmentAssignment.create(recordId, assignee, actor.staffId(),
                request.reason(), clock.instant());
        amendments.insertAssignment(assignment);
        return new AmendmentAssignmentDTO(assignment.getAssignmentId(), recordId, assignee, actor.staffId(),
                assignment.getReason(), assignment.getCreatedAt());
    }

    private MedicalRecord finished(UUID recordId) {
        MedicalRecord record = records.findByIdForUpdate(recordId).orElseThrow(() -> notFound(recordId));
        if (record.getStatus() == MedicalRecordStatus.OPEN) {
            throw new InvalidClinicalDataException("AMENDMENT_RECORD_OPEN",
                    "An open visit is edited directly; amendments are for finished visits");
        }
        return record;
    }

    private boolean inDepartment(UUID staffId, MedicalRecord record) {
        return staffId != null && staff.departmentOf(staffId).map(record.getDepartmentId()::equals).orElse(false);
    }

    private boolean isHead(ActorIdentity actor, UUID departmentId) {
        SchedulingAuthoritySnapshot snapshot = authority.authority(actor.accountId(), departmentId,
                correlationIds.currentOrCreate().toString());
        return snapshot != null && Boolean.TRUE.equals(snapshot.canManageSchedules())
                && Boolean.TRUE.equals(snapshot.staffActive()) && actor.staffId().equals(snapshot.staffId())
                && departmentId.equals(snapshot.departmentId());
    }

    private boolean isReviewer(ActorIdentity actor) {
        return reviewAuthority.canReviewRecords(actor.accountId(), correlationIds.currentOrCreate());
    }

    static String original(MedicalRecord record, AmendmentSection section) {
        String text = switch (section) {
            case SYMPTOMS -> record.getSymptoms();
            case CHIEF_COMPLAINT -> record.getChiefComplaint();
            case PRESENT_ILLNESS -> record.getPresentIllness();
            case PAST_HISTORY -> record.getPastHistory();
            case ALLERGY_NOTE -> record.getAllergyNote();
            case PHYSICAL_EXAM -> record.getPhysicalExam();
            case ADVICE -> record.getAdvice();
            case DISPOSITION_NOTE -> record.getDispositionNote();
            case CLOSURE_NOTE -> record.getClosureNote();
            case DIAGNOSIS -> record.getDiagnoses().stream()
                    .map(d -> (d.getIcdCode() == null || d.getIcdCode().isBlank() ? "" : d.getIcdCode() + " ")
                            + d.getDiagnosisName()
                            + (d.getDescription() == null || d.getDescription().isBlank() ? "" : " — " + d.getDescription()))
                    .collect(Collectors.joining("\n"));
        };
        return Objects.requireNonNullElse(text, "");
    }

    private static String effectiveText(MedicalRecord record, AmendmentSection section, List<RecordAmendment> history) {
        RecordAmendment latest = history.stream()
                .filter(amendment -> amendment.getSection() == section)
                .filter(amendment -> amendment.getStatus() == com.mediflow.clinical.domain.model.RecordAmendmentStatus.APPROVED
                        || amendment.getStatus() == com.mediflow.clinical.domain.model.RecordAmendmentStatus.APPLIED)
                .max(Comparator.comparing(RecordAmendment::getEffectiveRevision,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(RecordAmendment::getDecidedAt)
                        .thenComparing(RecordAmendment::getRequestedAt)
                        .thenComparing(amendment -> amendment.getAmendmentId().toString()))
                .orElse(null);
        return latest == null ? original(record, section) : latest.getAmendedText();
    }

    private ActorIdentity doctor() {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !"DOCTOR".equals(actor.role())) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED", "A doctor identity is required");
        }
        return actor;
    }

    private static MedicalRecordNotFoundException notFound(UUID recordId) {
        return new MedicalRecordNotFoundException("Medical record not found: " + recordId);
    }

    static RecordAmendmentDTO dto(RecordAmendment a) {
        return new RecordAmendmentDTO(a.getAmendmentId(), a.getRecordId(), a.getSection().name(), a.getOriginalText(),
                a.getAmendedText(), a.getReason(), a.getAuthorStaffId(), a.getAssignedByStaffId(), a.getStatus().name(),
                a.getRequestedAt(), a.getDecidedByStaffId(), a.getDecisionNote(), a.getDecidedAt(), a.getContentHash());
    }
}
