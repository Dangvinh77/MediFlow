package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.PrepareSurgeryScheduleUseCase;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.mediflow.surgery.application.port.out.AdmissionLookupPort;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/** Local draft-schedule orchestration. It never reserves room or staff resources. */
public class SurgeryScheduleApplicationService implements PrepareSurgeryScheduleUseCase {

    private static final String COMMAND_CODE = "PREPARE_SCHEDULE";

    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryCommandReceiptPort receipts;
    private final OrganizationLookupPort organization;
    private final AdmissionLookupPort admissions;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryClockPort clock;
    private final SurgeryCareEventCapturePort events;

    public SurgeryScheduleApplicationService(SurgeryCaseRepositoryPort cases,
                                             SurgeryScheduleRepositoryPort schedules,
                                             SurgeryCommandReceiptPort receipts,
                                             OrganizationLookupPort organization,
                                             AdmissionLookupPort admissions,
                                             SurgeryResourceReservationPort reservations,
                                             SurgeryClockPort clock, SurgeryCareEventCapturePort events) {
        this.events = java.util.Objects.requireNonNull(events);
        this.cases = cases;
        this.schedules = schedules;
        this.receipts = receipts;
        this.organization = organization;
        this.admissions = admissions;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SurgeryCommandOutcome prepare(Command command) {
        requireHuman(command == null ? null : command.actor());
        String operation = COMMAND_CODE + ":" + command.surgeryCaseId();
        String fingerprint = fingerprint(command);
        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(receipts,
                new SurgeryCommandReceiptPort.Key(command.actor().accountId().toString(),
                        operation, command.idempotencyKey()), fingerprint, COMMAND_CODE,
                command.surgeryCaseId());
        if (claim.isReplay()) return claim.replay();

        SurgeryCase observed = cases.findById(command.surgeryCaseId())
                .orElseThrow(() -> new com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException(command.surgeryCaseId()));
        if (observed.getRevision() != command.expectedCaseRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        requirePrestartScheduling(observed);
        java.util.List<Instant> observations = new java.util.ArrayList<>();
        if (observed.getCareEpisode().type() == CareEpisodeType.ADMISSION) {
            UUID admissionId = observed.getCareEpisode().admissionId();
            var admission = admissions.findAdmission(admissionId, command.correlationId());
            if (admission == null || !admissionId.equals(admission.admissionId())) {
                throw new UpstreamUnavailableException("Admission authority does not match requested identity");
            }
            if (admission.state() == OrganizationLookupPort.ReferenceState.NOT_FOUND) {
                throw new SurgeryRuleException("SURGERY_ADMISSION_NOT_FOUND", "Admission reference not found");
            }
            if (admission.state() == OrganizationLookupPort.ReferenceState.UNKNOWN || admission.state() == null) {
                throw new UpstreamUnavailableException("Admission authority is unknown");
            }
            if (!observed.getPatientId().equals(admission.patientId())
                    || !observed.getDepartmentId().equals(admission.departmentId())) {
                throw new SurgeryRuleException("SURGERY_ADMISSION_RELATIONSHIP_MISMATCH", "Admission patient/department does not match case");
            }
            if (admission.state() != OrganizationLookupPort.ReferenceState.ACTIVE) {
                throw new SurgeryRuleException("SURGERY_ADMISSION_INELIGIBLE", "Admission is not in the active medical-care window");
            }
            observations.add(admission.observedAt());
        }
        var department = organization.findDepartment(observed.getDepartmentId(), command.correlationId());
        var room = organization.findRoom(command.roomId(), command.correlationId());
        verifyReference(department,
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, observed.getDepartmentId(), "DEPARTMENT");
        verifyReference(room,
                OrganizationLookupPort.ReferenceKind.ROOM, command.roomId(), "ROOM");
        requireCaseDepartment(room.roomDepartmentId(), observed.getDepartmentId(), "ROOM");
        observations.add(department.observedAt());
        observations.add(room.observedAt());
        for (PrepareSurgeryScheduleUseCase.TeamMember member : command.team().stream()
                .sorted(Comparator.comparing(value -> value.staffId().toString())).toList()) {
            var eligibility = organization.findSurgicalEligibility(member.staffId(), member.role(),
                    command.startsAt(), command.endsAt(), command.correlationId());
            if (eligibility == null || !member.staffId().equals(eligibility.staffId())
                    || member.role() != eligibility.teamRole()
                    || !command.startsAt().equals(eligibility.startsAt())
                    || !command.endsAt().equals(eligibility.endsAt())) {
                throw new UpstreamUnavailableException("Organization eligibility does not match requested staff/role/interval");
            }
            verifyReference(new OrganizationLookupPort.OrganizationLookupSnapshot(
                    OrganizationLookupPort.ReferenceKind.STAFF, eligibility.staffId(), eligibility.state(),
                    eligibility.observedAt(), eligibility.sourceRevision(), null, eligibility.departmentId()),
                    OrganizationLookupPort.ReferenceKind.STAFF, member.staffId(), "STAFF");
            requireCaseDepartment(eligibility.departmentId(), observed.getDepartmentId(), "STAFF");
            observations.add(eligibility.observedAt());
        }

        SurgeryCase surgeryCase = cases.lockById(command.surgeryCaseId())
                .orElseThrow(() -> new com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException(command.surgeryCaseId()));
        if (surgeryCase.getRevision() != command.expectedCaseRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        requirePrestartScheduling(surgeryCase);

        SurgerySchedule current = schedules.findByCaseId(command.surgeryCaseId()).orElse(null);
        long currentRevision = current == null ? 0 : current.revision();
        if (currentRevision != command.expectedScheduleRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        UUID scheduleId = current == null ? UUID.randomUUID() : current.scheduleId();
        SurgerySchedule draft = new SurgerySchedule(scheduleId, command.surgeryCaseId(),
                currentRevision + 1, command.roomId(), command.startsAt(), command.endsAt(),
                command.team().stream().map(member -> new SurgeryTeamAssignment(
                        member.staffId(), member.role())).toList());
        Instant at = clock.now();
        // Observations may expire while waiting for the case lock; they are not distributed leases.
        if (observations.stream().anyMatch(value -> value == null || value.isBefore(at.minusSeconds(30))
                || value.isAfter(at.plusSeconds(5)))) {
            throw new UpstreamUnavailableException("Schedule authority became stale while waiting for mutation");
        }
        long revisionBeforeDraft = command.expectedCaseRevision();
        var pendingEvent = SurgeryReadinessInvalidation.Pending.NONE;
        if (surgeryCase.getStatus() == SurgeryStatus.READY || surgeryCase.getStatus() == SurgeryStatus.SCHEDULED) {
            if (current == null || surgeryCase.getReadinessSnapshot() == null
                    || surgeryCase.getReadinessSnapshot().dependencyRevisions().stream().noneMatch(value ->
                    value.dependencyType() == com.mediflow.surgery.domain.model.SurgeryDependencyType.SCHEDULE
                            && current.scheduleId().equals(value.sourceId()) && current.revision() == value.revision())) {
                throw new SurgeryRevisionConflictException();
            }
            pendingEvent = SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, command.actor(), command.correlationId(),
                    at, "SCHEDULE_REPLACED", schedules, reservations);
            // Draft adapter requires persisted PREOP. Both saves and old-release roll back together.
            cases.save(surgeryCase, revisionBeforeDraft);
            revisionBeforeDraft = surgeryCase.getRevision();
        }
        schedules.saveDraft(draft, currentRevision, at);
        surgeryCase.recordBusinessMutation(command.actor(), command.correlationId(), at,
                "SCHEDULE_DRAFT_PREPARED");
        cases.save(surgeryCase, revisionBeforeDraft);

        pendingEvent.capture(surgeryCase, events);
        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(COMMAND_CODE,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(), scheduleId,
                draft.revision(), "DRAFT", at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
    }

    private static void requireCaseDepartment(UUID authorityDepartment, UUID caseDepartment, String reference) {
        if (authorityDepartment == null) {
            throw new UpstreamUnavailableException("Organization " + reference + " department is missing");
        }
        if (!caseDepartment.equals(authorityDepartment)) {
            throw new SurgeryRuleException("SURGERY_" + reference + "_DEPARTMENT_MISMATCH",
                    "Surgery " + reference + " department does not match the case");
        }
    }

    private static String fingerprint(Command command) {
        String team = command.team().stream()
                .sorted(Comparator.comparing((PrepareSurgeryScheduleUseCase.TeamMember member) ->
                                member.staffId().toString())
                        .thenComparing(member -> member.role().name()))
                .map(member -> member.staffId() + ":" + member.role().name())
                .collect(java.util.stream.Collectors.joining(","));
        return SurgeryCommandReceipts.fingerprint(COMMAND_CODE,
                command.surgeryCaseId().toString(), Long.toString(command.expectedCaseRevision()),
                Long.toString(command.expectedScheduleRevision()), command.roomId().toString(),
                command.startsAt().toString(), command.endsAt().toString(), team);
    }

    private static void requireHuman(com.mediflow.surgery.domain.model.SurgeryAuditActor actor) {
        if (actor == null || actor.actorType() != SurgeryActorType.HUMAN) {
            throw new IllegalArgumentException("A human actor is required to prepare a surgery schedule");
        }
    }

    private static void requirePrestartScheduling(SurgeryCase surgeryCase) {
        if (surgeryCase.getStatus() != SurgeryStatus.PREOP_IN_PROGRESS
                && surgeryCase.getStatus() != SurgeryStatus.READY && surgeryCase.getStatus() != SurgeryStatus.SCHEDULED) {
            throw new SurgeryRuleException("SURGERY_SCHEDULE_DRAFT_NOT_ALLOWED",
                    "Schedule edits require a pre-operative case and invalidate prior readiness");
        }
    }

    private static void verifyReference(OrganizationLookupPort.OrganizationLookupSnapshot snapshot,
                                        OrganizationLookupPort.ReferenceKind expectedKind,
                                        UUID expectedId, String referenceName) {
        if (snapshot == null || snapshot.kind() != expectedKind
                || !expectedId.equals(snapshot.referenceId())) {
            throw new UpstreamUnavailableException("Organization lookup không khớp identity được yêu cầu");
        }
        switch (snapshot.state()) {
            case ACTIVE -> { }
            case UNKNOWN -> throw new UpstreamUnavailableException(
                    "Organization chưa xác định trạng thái " + referenceName);
            case NOT_FOUND -> throw new SurgeryRuleException("SURGERY_" + referenceName + "_NOT_FOUND",
                    "Không tìm thấy tham chiếu " + referenceName + " trong Organization");
            case INACTIVE -> {
                String code = "STAFF".equals(referenceName)
                        ? "SURGERY_STAFF_INELIGIBLE" : "SURGERY_" + referenceName + "_INACTIVE";
                throw new SurgeryRuleException(code,
                        "Tham chiếu " + referenceName + " không ở trạng thái hoạt động");
            }
        }
    }
}
