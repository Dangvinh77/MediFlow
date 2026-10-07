package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.application.event.SurgeryCareEvent;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit coverage for the channel-neutral Surgery creation transaction protocol. */
@ExtendWith(MockitoExtension.class)
class SurgeryCreationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEPARTMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID REQUESTER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID STAFF_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID TEMPLATE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID RECEIPT_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final String PROCEDURE_CODE = "APPENDECTOMY";
    private static final String CORRELATION_ID = "creation-correlation";

    @Mock SurgeryCaseRepositoryPort cases;
    @Mock SurgeryChecklistRepositoryPort checklists;
    @Mock SurgeryCreationReceiptPort receipts;
    @Mock SurgeryCreationAuthorityPort authority;
    @Mock SurgeryCareEventCapturePort events;
    @Mock SurgeryClockPort clock;
    @Mock SurgeryUnitOfWorkPort unitOfWork;

    private SurgeryCreationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SurgeryCreationApplicationService(cases, checklists, receipts, authority, events, clock, unitOfWork);
        lenient().doAnswer(invocation -> runSupplier(invocation.getArgument(0))).when(unitOfWork).outside(any());
        lenient().doAnswer(invocation -> runSupplier(invocation.getArgument(0))).when(unitOfWork).read(any());
        lenient().doAnswer(invocation -> runSupplier(invocation.getArgument(0))).when(unitOfWork).write(any());
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(receipts.find(any(), anyString())).thenReturn(Optional.empty());
        lenient().when(receipts.claim(any(), anyString()))
                .thenReturn(new SurgeryCreationReceiptPort.Claim(RECEIPT_ID, null));
        lenient().when(cases.findByRequestId(any())).thenReturn(Optional.empty());
        lenient().when(cases.save(any(), anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(checklists.findTemplate(anyString(), anyLong())).thenReturn(Optional.of(template()));
    }

    @Test
    void create_validCommand_persistsInitialCasePinnedChecklistAndHeldCreatedEvent() {
        CreateSurgeryCaseUseCase.Command command = command();
        stubValidApproval(command);

        SurgeryCreationOutcome outcome = service.create(command);

        assertThat(outcome.requestId()).isEqualTo(command.requestId());
        assertThat(outcome.surgeryCaseId()).isNotNull();
        assertThat(outcome.checklistSnapshotId()).isNotNull();
        assertThat(outcome.createdAt()).isEqualTo(NOW);
        assertThat(outcome.replayed()).isFalse();
        verify(authority).authorize(command);
        verify(authority).observe(command, SurgeryCreationApplicationService.fingerprint(command));
        verify(receipts).claim(command.requestId(), SurgeryCreationApplicationService.fingerprint(command));

        ArgumentCaptor<SurgeryCase> caseCaptor = ArgumentCaptor.forClass(SurgeryCase.class);
        verify(cases).save(caseCaptor.capture(), eq(-1L));
        SurgeryCase savedCase = caseCaptor.getValue();
        assertThat(savedCase.getSurgeryCaseId()).isEqualTo(outcome.surgeryCaseId());
        assertThat(savedCase.getSurgeryRequestId()).isEqualTo(command.requestId());
        assertThat(savedCase.getStatus().name()).isEqualTo("REQUESTED");
        assertThat(savedCase.getRevision()).isZero();
        assertThat(savedCase.getProcedureCode()).isEqualTo(command.procedureCode());

        ArgumentCaptor<SurgeryChecklistSnapshot> snapshotCaptor = ArgumentCaptor.forClass(SurgeryChecklistSnapshot.class);
        verify(checklists).createSnapshot(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().surgeryCaseId()).isEqualTo(outcome.surgeryCaseId());
        assertThat(snapshotCaptor.getValue().checklistSnapshotId()).isEqualTo(outcome.checklistSnapshotId());
        assertThat(snapshotCaptor.getValue().templateId()).isEqualTo(TEMPLATE_ID);
        assertThat(snapshotCaptor.getValue().templateRevision()).isEqualTo(command.templateRevision());
        assertThat(snapshotCaptor.getValue().revision()).isZero();

        ArgumentCaptor<SurgeryCareEvent> eventCaptor = ArgumentCaptor.forClass(SurgeryCareEvent.class);
        verify(events).hold(eventCaptor.capture(), eq(0L));
        SurgeryCareEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(SurgeryCareEvent.CASE_CREATED);
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.payload()).isInstanceOf(SurgeryCareEvent.Created.class);
        SurgeryCareEvent.Created created = (SurgeryCareEvent.Created) event.payload();
        assertThat(created.surgeryCaseId()).isEqualTo(outcome.surgeryCaseId());
        assertThat(created.surgeryRequestId()).isEqualTo(command.requestId());
        assertThat(created.requestedAt()).isEqualTo(command.requestedAt());
        assertThat(created.plannedItems()).extracting(SurgeryCareEvent.PlannedItem::itemCode)
                .containsExactly("ITEM-A", "ITEM-B");
        verify(receipts).complete(eq(RECEIPT_ID), eq(outcome));
    }

    @Test
    void create_committedReceipt_reauthorizesAndReplaysWithoutObserveOrWrites() {
        CreateSurgeryCaseUseCase.Command command = command();
        SurgeryCreationOutcome committed = new SurgeryCreationOutcome(command.requestId(),
                UUID.fromString("90000000-0000-0000-0000-000000000001"),
                UUID.fromString("90000000-0000-0000-0000-000000000002"), NOW.minusSeconds(2), false);
        String fingerprint = SurgeryCreationApplicationService.fingerprint(command);
        when(receipts.find(command.requestId(), fingerprint)).thenReturn(Optional.of(committed));

        SurgeryCreationOutcome replay = service.create(command);

        assertThat(replay).isEqualTo(committed.asReplay());
        verify(authority).authorize(command);
        verify(authority, never()).observe(any(), anyString());
        verify(receipts, never()).claim(any(), anyString());
        verifyNoInteractions(cases, checklists, events, clock);
    }

    @Test
    void create_replayAuthorizationFails_rejectsBeforeReceiptLookup() {
        CreateSurgeryCaseUseCase.Command command = command();
        SecurityException denied = new SecurityException("policy denied");
        doThrow(denied).when(authority).authorize(command);

        assertThatThrownBy(() -> service.create(command)).isSameAs(denied);

        verify(receipts, never()).find(any(), anyString());
        verifyNoInteractions(cases, checklists, events, clock);
    }

    @Test
    void create_racingCommittedClaim_returnsReplayWithoutDuplicatingCase() {
        CreateSurgeryCaseUseCase.Command command = command();
        stubValidApproval(command);
        SurgeryCreationOutcome committed = new SurgeryCreationOutcome(command.requestId(),
                UUID.fromString("90000000-0000-0000-0000-000000000003"),
                UUID.fromString("90000000-0000-0000-0000-000000000004"), NOW, false);
        when(receipts.claim(command.requestId(), SurgeryCreationApplicationService.fingerprint(command)))
                .thenReturn(new SurgeryCreationReceiptPort.Claim(RECEIPT_ID, committed));

        SurgeryCreationOutcome replay = service.create(command);

        assertThat(replay).isEqualTo(committed.asReplay());
        verify(receipts, never()).complete(any(), any());
        verifyNoInteractions(cases, checklists, events);
    }

    @Test
    void create_missingApproval_rejectsBeforeTemplateOrCaseMutation() {
        CreateSurgeryCaseUseCase.Command command = command();
        when(authority.observe(command, SurgeryCreationApplicationService.fingerprint(command))).thenReturn(null);

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(receipts).claim(command.requestId(), SurgeryCreationApplicationService.fingerprint(command));
        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
        verify(events, never()).hold(any(), anyLong());
        verify(receipts, never()).complete(any(), any());
    }

    @Test
    void create_authorityFingerprintMismatch_rejectsWithoutApplyingEffects() {
        CreateSurgeryCaseUseCase.Command command = command();
        when(authority.observe(command, SurgeryCreationApplicationService.fingerprint(command)))
                .thenReturn(new SurgeryCreationAuthorityPort.Approval("a".repeat(64), TEMPLATE_ID, 1,
                        NOW.minusSeconds(1), NOW.plusSeconds(60)));

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
        verify(events, never()).hold(any(), anyLong());
    }

    @Test
    void create_authorityTemplateRevisionMismatch_rejectsWithoutApplyingEffects() {
        CreateSurgeryCaseUseCase.Command command = command();
        when(authority.observe(command, SurgeryCreationApplicationService.fingerprint(command)))
                .thenReturn(new SurgeryCreationAuthorityPort.Approval(templateFingerprint(command), TEMPLATE_ID, 2,
                        NOW.minusSeconds(1), NOW.plusSeconds(60)));

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
    }

    @Test
    void create_authorityObservationTooOld_rejectsAtWriteBoundary() {
        CreateSurgeryCaseUseCase.Command command = command();
        String fingerprint = SurgeryCreationApplicationService.fingerprint(command);
        when(authority.observe(command, fingerprint)).thenReturn(new SurgeryCreationAuthorityPort.Approval(
                fingerprint, TEMPLATE_ID, 1, NOW.minusSeconds(31), NOW.plusSeconds(60)));

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
    }

    @Test
    void create_authorityObservationTooFarInFuture_rejectsAtWriteBoundary() {
        CreateSurgeryCaseUseCase.Command command = command();
        String fingerprint = SurgeryCreationApplicationService.fingerprint(command);
        when(authority.observe(command, fingerprint)).thenReturn(new SurgeryCreationAuthorityPort.Approval(
                fingerprint, TEMPLATE_ID, 1, NOW.plusSeconds(6), NOW.plusSeconds(60)));

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
    }

    @Test
    void create_authorityProofExpired_rejectsAtWriteBoundary() {
        CreateSurgeryCaseUseCase.Command command = command();
        String fingerprint = SurgeryCreationApplicationService.fingerprint(command);
        when(authority.observe(command, fingerprint)).thenReturn(new SurgeryCreationAuthorityPort.Approval(
                fingerprint, TEMPLATE_ID, 1, NOW.minusSeconds(1), NOW.plusSeconds(0)));

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
    }

    @Test
    void create_requestedAtTooFarInFuture_rejectsBeforeTemplateRead() {
        CreateSurgeryCaseUseCase.Command command = commandWithRequestedAt(NOW.plusSeconds(6));
        stubValidApproval(command);

        assertRule(() -> service.create(command), "SURGERY_CREATION_AUTHORITY_INVALID");

        verify(checklists, never()).findTemplate(anyString(), anyLong());
        verify(cases, never()).save(any(), anyLong());
    }

    @Test
    void create_approvedTemplateMissing_rejectsWithoutCaseOrEvent() {
        CreateSurgeryCaseUseCase.Command command = command();
        stubValidApproval(command);
        when(checklists.findTemplate(command.procedureCode(), command.templateRevision())).thenReturn(Optional.empty());

        assertRule(() -> service.create(command), "SURGERY_TEMPLATE_UNAVAILABLE");

        verify(cases, never()).save(any(), anyLong());
        verify(events, never()).hold(any(), anyLong());
        verify(receipts, never()).complete(any(), any());
    }

    @Test
    void create_approvedTemplateIdentityMismatch_rejectsWithoutCaseOrEvent() {
        CreateSurgeryCaseUseCase.Command command = command();
        stubValidApproval(command);
        SurgeryChecklistTemplate wrongTemplate = new SurgeryChecklistTemplate(
                UUID.fromString("71000000-0000-0000-0000-000000000001"), command.procedureCode(), 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "PREOP", true, 1)));
        when(checklists.findTemplate(command.procedureCode(), command.templateRevision())).thenReturn(Optional.of(wrongTemplate));

        assertRule(() -> service.create(command), "SURGERY_TEMPLATE_MISMATCH");

        verify(cases, never()).save(any(), anyLong());
        verify(events, never()).hold(any(), anyLong());
        verify(receipts, never()).complete(any(), any());
    }

    @Test
    void create_existingRequestIdentity_rejectsChangedIntentWithoutDuplicateCase() {
        CreateSurgeryCaseUseCase.Command command = command();
        stubValidApproval(command);
        when(cases.findByRequestId(command.requestId())).thenReturn(Optional.of(org.mockito.Mockito.mock(SurgeryCase.class)));

        assertThatThrownBy(() -> service.create(command)).isInstanceOf(com.mediflow.surgery.application.exception.SurgeryRevisionConflictException.class);

        verify(cases, never()).save(any(), anyLong());
        verify(checklists, never()).createSnapshot(any());
        verify(events, never()).hold(any(), anyLong());
        verify(receipts, never()).complete(any(), any());
    }

    @Test
    void create_eventCaptureFails_doesNotCompleteCreationReceipt() {
        CreateSurgeryCaseUseCase.Command command = command();
        stubValidApproval(command);
        doThrow(new IllegalStateException("event capture failed")).when(events).hold(any(), anyLong());

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("event capture failed");

        verify(cases).save(any(), eq(-1L));
        verify(checklists).createSnapshot(any());
        verify(receipts, never()).complete(any(), any());
    }

    @Test
    void fingerprint_sameClinicalIntentDifferentActorAndCorrelation_isStable() {
        CreateSurgeryCaseUseCase.Command original = command();
        CreateSurgeryCaseUseCase.Command deliveryVariant = new CreateSurgeryCaseUseCase.Command(
                original.requestId(), original.careEpisode(), original.patientId(), original.departmentId(),
                original.requestedBy(), original.procedureCode(), original.indication(), original.priority(),
                original.requestedAt(), original.templateRevision(), original.plannedItems(),
                SurgeryAuditActor.human(UUID.fromString("51000000-0000-0000-0000-000000000001"),
                        UUID.fromString("61000000-0000-0000-0000-000000000001")), "another-correlation");

        assertThat(SurgeryCreationApplicationService.fingerprint(original))
                .isEqualTo(SurgeryCreationApplicationService.fingerprint(deliveryVariant));
    }

    @Test
    void fingerprint_plannedLinesSortedAndNormalized_isStableAcrossInputOrderAndScale() {
        CreateSurgeryCaseUseCase.Command original = command();
        List<SurgeryPlannedItem> reordered = List.of(
                new SurgeryPlannedItem("ITEM-B", "PRICE-B", new BigDecimal("2.000")),
                new SurgeryPlannedItem("ITEM-A", "PRICE-A", new BigDecimal("1.00")));
        CreateSurgeryCaseUseCase.Command variant = new CreateSurgeryCaseUseCase.Command(
                original.requestId(), original.careEpisode(), original.patientId(), original.departmentId(),
                original.requestedBy(), original.procedureCode(), original.indication(), original.priority(),
                original.requestedAt(), original.templateRevision(), reordered, original.actor(), original.correlationId());

        assertThat(SurgeryCreationApplicationService.fingerprint(original))
                .isEqualTo(SurgeryCreationApplicationService.fingerprint(variant));
    }

    @Test
    void fingerprint_clinicalIntentChange_changesGlobalRequestFence() {
        CreateSurgeryCaseUseCase.Command original = command();
        CreateSurgeryCaseUseCase.Command changed = new CreateSurgeryCaseUseCase.Command(
                original.requestId(), original.careEpisode(), original.patientId(), original.departmentId(),
                original.requestedBy(), original.procedureCode(), "Changed indication", original.priority(),
                original.requestedAt(), original.templateRevision(), original.plannedItems(), original.actor(),
                original.correlationId());

        assertThat(SurgeryCreationApplicationService.fingerprint(original))
                .isNotEqualTo(SurgeryCreationApplicationService.fingerprint(changed));
    }

    @Test
    void command_duplicatePlannedItemCode_rejectsBeforeUseCase() {
        assertThatThrownBy(() -> new CreateSurgeryCaseUseCase.Command(
                REQUEST_ID, episode(), PATIENT_ID, DEPARTMENT_ID, REQUESTER_ID, PROCEDURE_CODE,
                "Appendix inflammation", SurgeryPriority.ROUTINE, NOW.minusSeconds(30), 1,
                List.of(new SurgeryPlannedItem("ITEM-A", "PRICE-A", BigDecimal.ONE),
                        new SurgeryPlannedItem("ITEM-A", "PRICE-B", BigDecimal.ONE)),
                actor(), CORRELATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate planned item code");
    }

    private void stubValidApproval(CreateSurgeryCaseUseCase.Command command) {
        String fingerprint = SurgeryCreationApplicationService.fingerprint(command);
        when(authority.observe(command, fingerprint)).thenReturn(
                new SurgeryCreationAuthorityPort.Approval(fingerprint, TEMPLATE_ID, command.templateRevision(),
                        NOW.minusSeconds(1), NOW.plusSeconds(60)));
    }

    private static String templateFingerprint(CreateSurgeryCaseUseCase.Command command) {
        return SurgeryCreationApplicationService.fingerprint(command);
    }

    private CreateSurgeryCaseUseCase.Command command() {
        return commandWithRequestedAt(NOW.minusSeconds(30));
    }

    private CreateSurgeryCaseUseCase.Command commandWithRequestedAt(Instant requestedAt) {
        return new CreateSurgeryCaseUseCase.Command(REQUEST_ID, episode(), PATIENT_ID, DEPARTMENT_ID,
                REQUESTER_ID, PROCEDURE_CODE, "Appendix inflammation", SurgeryPriority.ROUTINE, requestedAt, 1,
                List.of(new SurgeryPlannedItem("ITEM-B", "PRICE-B", new BigDecimal("2.00")),
                        new SurgeryPlannedItem("ITEM-A", "PRICE-A", new BigDecimal("1.0"))), actor(), CORRELATION_ID);
    }

    private static CareEpisode episode() {
        return new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.fromString("a0000000-0000-0000-0000-000000000001"),
                null, UUID.fromString("a0000000-0000-0000-0000-000000000002"));
    }

    private static SurgeryAuditActor actor() {
        return SurgeryAuditActor.human(ACCOUNT_ID, STAFF_ID);
    }

    private static SurgeryChecklistTemplate template() {
        return new SurgeryChecklistTemplate(TEMPLATE_ID, PROCEDURE_CODE, 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.fromString("71000000-0000-0000-0000-000000000001"),
                        "PREOP-CHECK", true, 1)));
    }

    private static Object runSupplier(Supplier<?> supplier) {
        return supplier.get();
    }

    private static void assertRule(ThrowingCall call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(SurgeryRuleException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
