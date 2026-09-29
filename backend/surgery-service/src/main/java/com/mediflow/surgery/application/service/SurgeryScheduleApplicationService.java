package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.PrepareSurgeryScheduleUseCase;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
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
    private final SurgeryClockPort clock;

    public SurgeryScheduleApplicationService(SurgeryCaseRepositoryPort cases,
                                             SurgeryScheduleRepositoryPort schedules,
                                             SurgeryCommandReceiptPort receipts,
                                             OrganizationLookupPort organization,
                                             SurgeryClockPort clock) {
        this.cases = cases;
        this.schedules = schedules;
        this.receipts = receipts;
        this.organization = organization;
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
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (observed.getRevision() != command.expectedCaseRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        requirePreop(observed);
        verifyReference(organization.findDepartment(observed.getDepartmentId()),
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, observed.getDepartmentId(), "DEPARTMENT");
        verifyReference(organization.findRoom(command.roomId()),
                OrganizationLookupPort.ReferenceKind.ROOM, command.roomId(), "ROOM");
        for (PrepareSurgeryScheduleUseCase.TeamMember member : command.team().stream()
                .sorted(Comparator.comparing(value -> value.staffId().toString())).toList()) {
            verifyReference(organization.findStaff(member.staffId()),
                    OrganizationLookupPort.ReferenceKind.STAFF, member.staffId(), "STAFF");
        }

        SurgeryCase surgeryCase = cases.lockById(command.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.getRevision() != command.expectedCaseRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        requirePreop(surgeryCase);

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
        schedules.saveDraft(draft, currentRevision, at);
        surgeryCase.recordBusinessMutation(command.actor(), command.correlationId(), at,
                "SCHEDULE_DRAFT_PREPARED");
        cases.save(surgeryCase, command.expectedCaseRevision());

        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(COMMAND_CODE,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(), scheduleId,
                draft.revision(), "DRAFT", at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
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

    private static void requirePreop(SurgeryCase surgeryCase) {
        if (surgeryCase.getStatus() != SurgeryStatus.PREOP_IN_PROGRESS) {
            throw new SurgeryRuleException("SURGERY_SCHEDULE_DRAFT_NOT_ALLOWED",
                    "Chỉ được chuẩn bị lịch nháp khi ca đang ở giai đoạn tiền phẫu");
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
