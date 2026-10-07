package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.mapper.SurgeryCareEventFactory;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Internal LOCAL core only; no @Service or permissive production authority fallback. */
public final class SurgeryCreationApplicationService implements CreateSurgeryCaseUseCase {
    private static final long MAX_OBSERVATION_AGE_SECONDS = 30;
    private static final long MAX_FUTURE_SKEW_SECONDS = 5;
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryChecklistRepositoryPort checklists;
    private final SurgeryCreationReceiptPort receipts;
    private final SurgeryCreationAuthorityPort authority;
    private final SurgeryCareEventCapturePort events;
    private final SurgeryClockPort clock;
    private final SurgeryUnitOfWorkPort unitOfWork;

    public SurgeryCreationApplicationService(SurgeryCaseRepositoryPort cases, SurgeryChecklistRepositoryPort checklists,
            SurgeryCreationReceiptPort receipts, SurgeryCreationAuthorityPort authority, SurgeryCareEventCapturePort events,
            SurgeryClockPort clock, SurgeryUnitOfWorkPort unitOfWork) {
        this.cases = Objects.requireNonNull(cases);
        this.checklists = Objects.requireNonNull(checklists);
        this.receipts = Objects.requireNonNull(receipts);
        this.authority = Objects.requireNonNull(authority);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    @Override public SurgeryCreationOutcome create(Command command) {
        Objects.requireNonNull(command);
        // Authorization cannot be bypassed through the committed-receipt shortcut.
        unitOfWork.outside(() -> { authority.authorize(command); return null; });
        String fingerprint = fingerprint(command);
        var replay = unitOfWork.read(() -> receipts.find(command.requestId(), fingerprint));
        if (replay.isPresent()) return replay.get().asReplay();
        final SurgeryCreationAuthorityPort.Approval approval;
        try {
            approval = unitOfWork.outside(() -> authority.observe(command, fingerprint));
        } catch (RuntimeException unavailable) {
            // Another replica may have committed this intent during preflight. Only a committed exact receipt can replay.
            var winner = unitOfWork.read(() -> receipts.find(command.requestId(), fingerprint));
            if (winner.isPresent()) return winner.get().asReplay();
            throw unavailable;
        }
        return unitOfWork.write(() -> {
            var claim = receipts.claim(command.requestId(), fingerprint);
            if (claim.outcome() != null) return claim.outcome().asReplay();
            var now = clock.now(); // AFTER waiting on the global request fence.
            if (approval == null || !fingerprint.equals(approval.intentFingerprint())
                    || approval.templateRevision() != command.templateRevision()
                    || approval.observedAt().isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))
                    || approval.observedAt().isBefore(now.minusSeconds(MAX_OBSERVATION_AGE_SECONDS))
                    || !now.isBefore(approval.validUntil()) || command.requestedAt().isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
                throw new SurgeryRuleException("SURGERY_CREATION_AUTHORITY_INVALID", "Creation authority is missing, stale or mismatched");
            }
            var template = checklists.findTemplate(command.procedureCode(), command.templateRevision())
                    .orElseThrow(() -> new SurgeryRuleException("SURGERY_TEMPLATE_UNAVAILABLE", "Approved template is unavailable"));
            if (!template.templateId().equals(approval.templateId())
                    || !template.procedureCode().equals(command.procedureCode())
                    || template.revision() != command.templateRevision()) {
                throw new SurgeryRuleException("SURGERY_TEMPLATE_MISMATCH", "Approved template identity does not match");
            }
            // Legacy cases without a creation proof are not silently adopted or charged again.
            if (cases.findByRequestId(command.requestId()).isPresent()) throw new SurgeryRevisionConflictException();
            var value = SurgeryCase.create(UUID.randomUUID(), command.requestId(), command.careEpisode(), command.patientId(),
                    command.departmentId(), command.requestedBy(), command.procedureCode(), command.indication(),
                    command.priority(), command.requestedAt(), command.actor(), command.correlationId());
            var snapshot = template.snapshotForCase(UUID.randomUUID(), value.getSurgeryCaseId());
            cases.save(value, -1);
            checklists.createSnapshot(snapshot);
            // Canonical V7 fact is mandatory. An append failure rolls back case, children, audit and receipt.
            events.hold(SurgeryCareEventFactory.created(value, command.plannedItems()), value.getRevision());
            var outcome = new SurgeryCreationOutcome(command.requestId(), value.getSurgeryCaseId(), snapshot.checklistSnapshotId(), now, false);
            receipts.complete(claim.receiptId(), outcome);
            return outcome;
        });
    }

    public static String fingerprint(Command command) {
        var fields = new ArrayList<String>();
        fields.add("SURGERY_CREATE_INTENT_V1");
        fields.add(command.requestId().toString());
        fields.add(command.careEpisode().type().name());
        fields.add(command.careEpisode().episodeId().toString());
        fields.add(command.careEpisode().admissionId() == null ? null : command.careEpisode().admissionId().toString());
        fields.add(command.careEpisode().recordId() == null ? null : command.careEpisode().recordId().toString());
        fields.add(command.patientId().toString());
        fields.add(command.departmentId().toString());
        fields.add(command.requestedBy().toString());
        fields.add(command.procedureCode());
        fields.add(command.indication());
        fields.add(command.priority().name());
        fields.add(command.requestedAt().toString());
        fields.add(Long.toString(command.templateRevision()));
        command.plannedItems().stream().sorted(Comparator.comparing(SurgeryPlannedItem::itemCode)).forEach(item -> {
            fields.add(item.itemCode()); fields.add(item.priceCode()); fields.add(item.quantity().toPlainString());
        });
        // No delivery/channel/correlation/recorder in the clinical intent. Authorization is a separate mandatory step.
        return SurgeryCommandReceipts.fingerprint(fields.toArray(String[]::new));
    }
}
