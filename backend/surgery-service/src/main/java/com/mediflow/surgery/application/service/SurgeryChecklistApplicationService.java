package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public class SurgeryChecklistApplicationService implements UpdateChecklistItemUseCase {

    private static final String COMMAND_CODE = "UPDATE_CHECKLIST_ITEM";

    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryChecklistRepositoryPort checklists;
    private final SurgeryCommandReceiptPort receipts;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResourceReservationPort reservations;
    private final SurgeryClockPort clock;

    public SurgeryChecklistApplicationService(SurgeryCaseRepositoryPort cases,
                                              SurgeryChecklistRepositoryPort checklists,
                                              SurgeryCommandReceiptPort receipts,
                                              SurgeryScheduleRepositoryPort schedules,
                                              SurgeryResourceReservationPort reservations,
                                              SurgeryClockPort surgeryClock) {
        this.cases = cases;
        this.checklists = checklists;
        this.receipts = receipts;
        this.schedules = schedules;
        this.reservations = reservations;
        this.clock = surgeryClock;
    }

    @Override
    @Transactional
    public SurgeryCommandOutcome update(Command command) {
        if (command == null || command.actor().actorType() != SurgeryActorType.HUMAN) {
            throw new IllegalArgumentException("A human actor is required for checklist changes");
        }
        validateEvidenceInput(command);
        String operation = COMMAND_CODE + ":" + command.surgeryCaseId();
        SurgeryCommandReceiptPort.Key key = new SurgeryCommandReceiptPort.Key(
                command.actor().accountId().toString(), operation, command.idempotencyKey());
        String fingerprint = SurgeryCommandReceipts.fingerprint(COMMAND_CODE,
                command.surgeryCaseId().toString(), command.checklistItemId().toString(),
                Long.toString(command.expectedCaseRevision()),
                Long.toString(command.expectedSnapshotRevision()),
                Long.toString(command.expectedItemRevision()), command.status().name(),
                command.evidenceReferenceId() == null ? null : command.evidenceReferenceId().toString(),
                command.evidenceRevision() == null ? null : command.evidenceRevision().toString());
        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(
                receipts, key, fingerprint, COMMAND_CODE, command.surgeryCaseId());
        if (claim.isReplay()) return claim.replay();

        SurgeryCase surgeryCase = cases.lockById(command.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.getRevision() != command.expectedCaseRevision()
                || surgeryCase.getStatus() == SurgeryStatus.IN_PROGRESS
                || surgeryCase.getStatus() == SurgeryStatus.COMPLETED
                || surgeryCase.getStatus() == SurgeryStatus.CANCELLED) {
            throw new SurgeryRevisionConflictException();
        }
        SurgeryChecklistSnapshot current = checklists.findSnapshotByCaseId(command.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (current.revision() != command.expectedSnapshotRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        SurgeryChecklistItem before = current.items().stream()
                .filter(item -> item.checklistItemId().equals(command.checklistItemId()))
                .findFirst().orElseThrow(SurgeryRevisionConflictException::new);
        if (before.revision() != command.expectedItemRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        Instant at = clock.now();
        SurgeryChecklistItem after = before.revise(command.status(),
                command.evidenceReferenceId(), command.evidenceRevision());
        SurgeryChecklistSnapshot revised = current.reviseItem(before.checklistItemId(),
                command.expectedSnapshotRevision(), after);
        SurgeryChecklistItemChange change = new SurgeryChecklistItemChange(
                UUID.randomUUID(), before.checklistItemId(), after.revision(),
                before.status(), after.status(), after.evidenceReferenceId(), after.evidenceRevision(),
                command.actor(), at, command.correlationId());
        SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, command.actor(),
                command.correlationId(), at, "CHECKLIST_CHANGED", schedules, reservations);
        surgeryCase.recordBusinessMutation(command.actor(), command.correlationId(), at,
                "CHECKLIST_ITEM_CHANGED");
        cases.save(surgeryCase, command.expectedCaseRevision());
        checklists.saveItemChange(revised, command.expectedSnapshotRevision(), change);
        SurgeryCommandOutcome outcome = new SurgeryCommandOutcome(COMMAND_CODE,
                surgeryCase.getSurgeryCaseId(), surgeryCase.getRevision(), after.checklistItemId(),
                after.revision(), after.status().name(), at, false);
        SurgeryCommandReceipts.complete(receipts, claim.receiptId(), outcome);
        return outcome;
    }

    private static void validateEvidenceInput(UpdateChecklistItemUseCase.Command command) {
        if (command.status() == SurgeryChecklistStatus.NOT_APPLICABLE) {
            throw new SurgeryRuleException("SURGERY_NOT_APPLICABLE_POLICY_UNCONFIRMED",
                    "Chưa có chính sách lâm sàng cho phép đánh dấu mục không áp dụng");
        }
        if (command.status() == SurgeryChecklistStatus.SATISFIED
                && command.evidenceReferenceId() == null) {
            throw new SurgeryRuleException("SURGERY_CHECKLIST_EVIDENCE_REQUIRED",
                    "Mục được xác nhận cần tham chiếu bằng chứng");
        }
        if (command.status() != SurgeryChecklistStatus.SATISFIED
                && command.status() != SurgeryChecklistStatus.FAILED
                && (command.evidenceReferenceId() != null || command.evidenceRevision() != null)) {
            throw new SurgeryRuleException("SURGERY_CHECKLIST_EVIDENCE_STATUS_MISMATCH",
                    "Trạng thái checklist này không được mang bằng chứng xác nhận");
        }
        if (command.evidenceRevision() != null && command.evidenceReferenceId() == null) {
            throw new SurgeryRuleException("SURGERY_CHECKLIST_EVIDENCE_REFERENCE_REQUIRED",
                    "Phiên bản bằng chứng cần có tham chiếu bằng chứng");
        }
        if (command.evidenceRevision() != null && command.evidenceRevision() < 0) {
            throw new SurgeryRuleException("SURGERY_CHECKLIST_EVIDENCE_REVISION_INVALID",
                    "Phiên bản bằng chứng không hợp lệ");
        }
    }
}
