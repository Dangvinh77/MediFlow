package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.in.CancelSurgeryUseCase;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryLifecycleIntentPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessSnapshotPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryResultRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import com.mediflow.surgery.application.event.SurgeryCareEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Framework-free cross-command tests for the Surgery application workflow.
 *
 * <p>The ports below are deterministic in-memory ports. They deliberately do not claim to model
 * a database transaction; their purpose is to let one test drive the real application kernels
 * through a sequence of commands and inspect the resulting aggregate, receipts and held facts.</p>
 */
class SurgeryWorkflowApplicationTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-07T08:00:00Z");
    private static final Instant PREOP_AT = REQUESTED_AT.plusSeconds(1);
    private static final Instant READY_AT = REQUESTED_AT.plusSeconds(10);
    private static final Instant COMPLETE_AT = REQUESTED_AT.plusSeconds(20);

    @Test
    void workflow_readinessFinalizeStartComplete_replaysCompletionWithoutDuplicateResult() {
        Workflow workflow = Workflow.startedInPreop();

        SurgeryCommandOutcome ready = workflow.lifecycle.evaluate(workflow.lifecycleCommand("ready-1"));
        SurgeryCommandOutcome scheduled = workflow.lifecycle.finalizeSchedule(
                workflow.lifecycleCommand("finalize-1"));
        SurgeryCommandOutcome started = workflow.lifecycle.start(
                workflow.lifecycleCommand("start-1"));
        CompleteSurgeryUseCase.Command completion = workflow.completionCommand("complete-1");
        SurgeryCommandOutcome completed = workflow.lifecycle.complete(completion);
        SurgeryCommandOutcome replay = workflow.lifecycle.complete(completion);

        assertThat(ready.state()).isEqualTo(SurgeryStatus.READY.name());
        assertThat(scheduled.state()).isEqualTo(SurgeryStatus.SCHEDULED.name());
        assertThat(started.state()).isEqualTo(SurgeryStatus.IN_PROGRESS.name());
        assertThat(completed.state()).isEqualTo(SurgeryStatus.COMPLETED.name());
        assertThat(replay).isEqualTo(completed.asReplay());
        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(workflow.caseValue.getRevision()).isEqualTo(5);
        assertThat(workflow.results.findByCaseId(workflow.caseValue.getSurgeryCaseId()))
                .isPresent().get().satisfies(result -> {
                    assertThat(result.actualEndAt()).isEqualTo(COMPLETE_AT);
                    assertThat(result.performedItems()).hasSize(1);
                });
        assertThat(workflow.results.created).hasSize(1);
        assertThat(workflow.resources.inUseCalls).hasSize(1);
        assertThat(workflow.resources.releaseCalls).hasSize(1);
        assertThat(workflow.intents.completed).hasSize(1);
        assertThat(workflow.intents.completed.getFirst().resultId()).isEqualTo(completed.subjectId());
    }

    @Test
    void workflow_checklistMutationInvalidatesReadiness_thenSatisfiedMutationAllowsReEvaluation() {
        Workflow workflow = Workflow.ready();
        UUID itemId = workflow.checklist.items().getFirst().checklistItemId();

        SurgeryCommandOutcome failed = workflow.checklistService.update(new UpdateChecklistItemUseCase.Command(
                workflow.caseValue.getSurgeryCaseId(), itemId, workflow.caseValue.getRevision(),
                workflow.checklists.value.revision(), 0, SurgeryChecklistStatus.FAILED, null, null,
                "check-fail-1", workflow.actor, "workflow-checklist"));

        SurgeryCommandOutcome denied = workflow.lifecycle.evaluate(
                workflow.lifecycleCommand("ready-after-failure"));

        SurgeryCommandOutcome satisfied = workflow.checklistService.update(new UpdateChecklistItemUseCase.Command(
                workflow.caseValue.getSurgeryCaseId(), itemId, workflow.caseValue.getRevision(),
                workflow.checklists.value.revision(), 1, SurgeryChecklistStatus.SATISFIED,
                UUID.randomUUID(), 2L, "check-satisfy-1", workflow.actor, "workflow-checklist"));
        SurgeryCommandOutcome readyAgain = workflow.lifecycle.evaluate(
                workflow.lifecycleCommand("ready-again-1"));

        assertThat(failed.state()).isEqualTo(SurgeryChecklistStatus.FAILED.name());
        assertThat(denied.state()).isEqualTo("NOT_READY");
        assertThat(denied.blockingReasons()).contains("CHECKLIST_INCOMPLETE");
        assertThat(satisfied.state()).isEqualTo(SurgeryChecklistStatus.SATISFIED.name());
        assertThat(readyAgain.state()).isEqualTo(SurgeryStatus.READY.name());
        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.READY);
        assertThat(workflow.checklists.value.mandatoryChecklistComplete()).isTrue();
        assertThat(workflow.snapshots.stored).hasSize(3);
        assertThat(workflow.events.held).extracting(SurgeryCareEvent::eventType)
                .contains(SurgeryCareEvent.READINESS_INVALIDATED);
    }

    @Test
    void workflow_consentRevocationInvalidatesReadiness_thenNewConsentReEstablishesIt() {
        Workflow workflow = Workflow.ready();
        SurgeryConsentRecord surgeryConsent = workflow.consent(SurgeryConsentType.SURGERY);

        SurgeryCommandOutcome revoked = workflow.consentService.revoke(new ManageSurgeryConsentUseCase.RevokeCommand(
                workflow.caseValue.getSurgeryCaseId(), surgeryConsent.consentId(), workflow.caseValue.getRevision(),
                "patient withdrew consent", "revoke-consent-1", workflow.actor, "workflow-consent"));
        SurgeryCommandOutcome denied = workflow.lifecycle.evaluate(
                workflow.lifecycleCommand("ready-after-revoke"));

        SurgeryCommandOutcome signed = workflow.consentService.sign(new ManageSurgeryConsentUseCase.SignCommand(
                workflow.caseValue.getSurgeryCaseId(), workflow.caseValue.getRevision(), SurgeryConsentType.SURGERY,
                workflow.caseValue.getPatientId(), SurgeryConsentSignerType.PATIENT, UUID.randomUUID(),
                "sign-consent-2", workflow.actor, "workflow-consent"));
        SurgeryCommandOutcome readyAgain = workflow.lifecycle.evaluate(
                workflow.lifecycleCommand("ready-after-reconsent"));

        assertThat(revoked.state()).isEqualTo("REVOKED:SURGERY");
        assertThat(workflow.consentHistory(SurgeryConsentType.SURGERY))
                .filteredOn(consent -> consent.consentId().equals(surgeryConsent.consentId())).singleElement()
                .satisfies(consent -> {
                    assertThat(consent.isActive()).isFalse();
                    assertThat(consent.auditHistory()).hasSize(2);
                });
        assertThat(denied.state()).isEqualTo("NOT_READY");
        assertThat(denied.blockingReasons()).contains("SURGERY_CONSENT_MISSING");
        assertThat(signed.state()).isEqualTo("ACTIVE:SURGERY");
        assertThat(readyAgain.state()).isEqualTo(SurgeryStatus.READY.name());
        assertThat(workflow.activeConsent(SurgeryConsentType.SURGERY)).isNotEqualTo(surgeryConsent);
        assertThat(workflow.activeConsent(SurgeryConsentType.SURGERY).auditHistory()).hasSize(1);
        assertThat(workflow.consentHistory(SurgeryConsentType.SURGERY)).hasSize(2)
                .filteredOn(SurgeryConsentRecord::isActive).hasSize(1);
        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.READY);
    }

    @Test
    void workflow_financialDenial_cannotStart_andExactDenialReceiptReplays() {
        Workflow workflow = Workflow.scheduled();
        workflow.financial.eligible = false;
        SurgeryLifecycleCommand command = workflow.lifecycleCommand("start-denied-1");

        SurgeryCommandOutcome denied = workflow.lifecycle.start(command);
        SurgeryCommandOutcome replay = workflow.lifecycle.start(command);

        assertThat(denied.state()).isEqualTo("NOT_READY");
        assertThat(denied.blockingReasons()).contains("FINANCIAL_CLEARANCE_INVALID");
        assertThat(replay).isEqualTo(denied.asReplay());
        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(workflow.resources.inUseCalls).isEmpty();
        assertThat(workflow.resources.releaseCalls).singleElement().satisfies(call -> {
            assertThat(call.scheduleId()).isEqualTo(workflow.schedule.scheduleId());
            assertThat(call.scheduleRevision()).isEqualTo(workflow.schedule.revision());
        });
        assertThat(workflow.receipts.completedFor("START_SURGERY")).isEqualTo(1);
    }

    @Test
    void workflow_cancelRequestedCase_persistsCancellationAndReplayDoesNotReopenIt() {
        Workflow workflow = Workflow.newCase();
        CancelSurgeryUseCase.Command command = workflow.cancelCommand("cancel-requested-1", 0);

        SurgeryCommandOutcome cancelled = workflow.cancellation.cancel(command);
        SurgeryCommandOutcome replay = workflow.cancellation.cancel(command);

        assertThat(cancelled.state()).isEqualTo(SurgeryStatus.CANCELLED.name());
        assertThat(replay).isEqualTo(cancelled.asReplay());
        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(workflow.caseValue.getCancelledAt()).isEqualTo(READY_AT);
        assertThat(workflow.resources.releaseCalls).isEmpty();
        assertThat(workflow.events.held).extracting(SurgeryCareEvent::eventType)
                .containsExactly(SurgeryCareEvent.CANCELLED);
        assertThat(workflow.receipts.completedFor("CANCEL_SURGERY")).isEqualTo(1);
    }

    @Test
    void workflow_cancelCompletedCase_rejectsWithoutReopeningTerminalAggregate() {
        Workflow workflow = Workflow.completed();
        long revisionBefore = workflow.caseValue.getRevision();
        int eventsBefore = workflow.events.held.size();

        assertThatThrownBy(() -> workflow.cancellation.cancel(
                workflow.cancelCommand("cancel-terminal-1", revisionBefore)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(workflow.caseValue.getRevision()).isEqualTo(revisionBefore);
        assertThat(workflow.events.held).hasSize(eventsBefore);
        assertThat(workflow.resources.releaseCalls).hasSize(1);
    }

    @Test
    void workflow_staleCaseRevision_failsFinalizeBeforeResourceMutation() {
        Workflow workflow = Workflow.ready();
        long staleRevision = workflow.caseValue.getRevision();
        int claimsBefore = workflow.receipts.claimedCount();
        workflow.caseValue.recordBusinessMutation(workflow.actor, "workflow-change", READY_AT, "UNRELATED_CASE_CHANGE");

        assertThatThrownBy(() -> workflow.lifecycle.finalizeSchedule(new SurgeryLifecycleCommand(
                workflow.caseValue.getSurgeryCaseId(), staleRevision, workflow.schedule.revision(),
                "finalize-stale-1", workflow.actorIdentity, "workflow-stale")))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        assertThat(workflow.caseValue.getStatus()).isEqualTo(SurgeryStatus.READY);
        assertThat(workflow.resources.reserveCalls).isEmpty();
        assertThat(workflow.receipts.claimedCount()).isEqualTo(claimsBefore);
    }

    @Test
    void workflow_completionReceipt_keepsImmutableResultWhenCallerReusesSameCommand() {
        Workflow workflow = Workflow.completed();
        SurgeryResult stored = workflow.results.created.getFirst();
        List<SurgeryPerformedItem> items = stored.performedItems();

        SurgeryCommandOutcome replay = workflow.lifecycle.complete(workflow.lastCompletionCommand);

        assertThat(replay.replayed()).isTrue();
        assertThat(workflow.results.created).containsExactly(stored);
        assertThat(workflow.results.created.getFirst().performedItems()).containsExactlyElementsOf(items);
        assertThat(workflow.caseValue.getCompletedAt()).isEqualTo(COMPLETE_AT);
    }

    private static final class Workflow {
        private final UUID accountId = UUID.randomUUID();
        private final UUID staffId = UUID.randomUUID();
        private final SurgeryAuditActor actor = SurgeryAuditActor.human(accountId, staffId);
        private final SurgeryActorIdentity actorIdentity = new SurgeryActorIdentity(accountId, staffId);
        private final MutableClock clock = new MutableClock(READY_AT);
        private final InMemoryCaseRepository cases = new InMemoryCaseRepository();
        private final InMemoryScheduleRepository schedules = new InMemoryScheduleRepository();
        private final InMemoryReceiptPort receipts = new InMemoryReceiptPort();
        private final InMemoryChecklistRepository checklists = new InMemoryChecklistRepository();
        private final InMemoryConsentRepository consents = new InMemoryConsentRepository();
        private final InMemoryClearanceRepository clearances = new InMemoryClearanceRepository();
        private final FinancialAuthority financial = new FinancialAuthority(clock);
        private final InMemorySnapshots snapshots = new InMemorySnapshots();
        private final RecordingResources resources = new RecordingResources();
        private final InMemoryResults results = new InMemoryResults();
        private final RecordingAuthority authority = new RecordingAuthority(this);
        private final RecordingIntents intents = new RecordingIntents();
        private final RecordingEvents events = new RecordingEvents();
        private final SurgeryReadinessEngine engine = new SurgeryReadinessEngine(
                Duration.ofSeconds(30), Duration.ofSeconds(5));
        private final SurgeryUnitOfWorkPort unitOfWork = new DirectUnitOfWork();
        private final SurgeryPreopApplicationService preop;
        private final SurgeryChecklistApplicationService checklistService;
        private final SurgeryConsentApplicationService consentService;
        private final SurgeryLifecycleApplicationService lifecycle;
        private final SurgeryCancellationApplicationService cancellation;
        private final SurgeryCase caseValue;
        private final SurgerySchedule schedule;
        private final SurgeryChecklistSnapshot checklist;
        private CompleteSurgeryUseCase.Command lastCompletionCommand;

        private Workflow() {
            caseValue = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                    new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                    UUID.randomUUID(), UUID.randomUUID(), staffId, "PROC-001", "Clinical indication",
                    SurgeryPriority.ROUTINE, REQUESTED_AT, actor, "workflow-create");
            cases.value = caseValue;
            schedule = new SurgerySchedule(UUID.randomUUID(), caseValue.getSurgeryCaseId(), 1, UUID.randomUUID(),
                    REQUESTED_AT.plusSeconds(100), REQUESTED_AT.plusSeconds(200),
                    List.of(new SurgeryTeamAssignment(staffId, SurgeryTeamRole.PRIMARY_SURGEON)));
            schedules.value = schedule;
            UUID itemId = UUID.randomUUID();
            checklist = new SurgeryChecklistSnapshot(UUID.randomUUID(), caseValue.getSurgeryCaseId(), UUID.randomUUID(),
                    1, 0, List.of(new SurgeryChecklistItem(itemId, caseValue.getSurgeryCaseId(), UUID.randomUUID(),
                            "IDENTITY_CONFIRMED", true, 1, SurgeryChecklistStatus.SATISFIED, UUID.randomUUID(), 1L, 0)));
            checklists.value = checklist;
            SurgeryConsentRecord surgeryConsent = SurgeryConsentRecord.sign(UUID.randomUUID(),
                    caseValue.getSurgeryCaseId(), SurgeryConsentType.SURGERY, caseValue.getPatientId(),
                    SurgeryConsentSignerType.PATIENT, UUID.randomUUID(), actor, REQUESTED_AT, "workflow-create");
            SurgeryConsentRecord anesthesiaConsent = SurgeryConsentRecord.sign(UUID.randomUUID(),
                    caseValue.getSurgeryCaseId(), SurgeryConsentType.ANESTHESIA, caseValue.getPatientId(),
                    SurgeryConsentSignerType.PATIENT, UUID.randomUUID(), actor, REQUESTED_AT, "workflow-create");
            consents.values.put(surgeryConsent.consentId(), surgeryConsent);
            consents.values.put(anesthesiaConsent.consentId(), anesthesiaConsent);
            clearances.value = new SurgeryFinancialClearance(UUID.randomUUID(), UUID.randomUUID(), accountId,
                    caseValue.getPatientId(), caseValue.getSurgeryCaseId(), CareEpisodeType.OUTPATIENT_VISIT,
                    caseValue.getCareEpisode().episodeId(), null, BigDecimal.ONE, "VND", "CASH",
                    REQUESTED_AT, REQUESTED_AT.plusSeconds(600), "a".repeat(64));
            preop = new SurgeryPreopApplicationService(cases, receipts, clock);
            checklistService = new SurgeryChecklistApplicationService(cases, checklists, receipts, schedules,
                    resources, clock, events);
            consentService = new SurgeryConsentApplicationService(cases, consents, schedules, resources, receipts,
                    clock, events);
            lifecycle = new SurgeryLifecycleApplicationService(cases, schedules, receipts, snapshots, checklists,
                    consents, clearances, resources, results, authority, intents, clock, engine, financial,
                    events, unitOfWork);
            cancellation = new SurgeryCancellationApplicationService(cases, schedules, resources, receipts, clock, events);
        }

        static Workflow newCase() { return new Workflow(); }

        static Workflow startedInPreop() {
            Workflow value = new Workflow();
            value.beginPreop();
            return value;
        }

        static Workflow ready() {
            Workflow value = startedInPreop();
            value.lifecycle.evaluate(value.lifecycleCommand("ready-1"));
            return value;
        }

        static Workflow scheduled() {
            Workflow value = ready();
            value.lifecycle.finalizeSchedule(value.lifecycleCommand("finalize-1"));
            return value;
        }

        static Workflow completed() {
            Workflow value = scheduled();
            value.lifecycle.start(value.lifecycleCommand("start-1"));
            value.lastCompletionCommand = value.completionCommand("complete-1");
            value.lifecycle.complete(value.lastCompletionCommand);
            return value;
        }

        private void beginPreop() {
            clock.now = PREOP_AT;
            preop.begin(new BeginPreopUseCase.Command(caseValue.getSurgeryCaseId(), 0, "begin-preop-1",
                    actorIdentity, "workflow-preop"));
            clock.now = READY_AT;
        }

        private SurgeryLifecycleCommand lifecycleCommand(String idempotencyKey) {
            return new SurgeryLifecycleCommand(caseValue.getSurgeryCaseId(), caseValue.getRevision(),
                    schedule.revision(), idempotencyKey, actorIdentity, "workflow-lifecycle");
        }

        private CompleteSurgeryUseCase.Command completionCommand(String idempotencyKey) {
            clock.now = COMPLETE_AT;
            return new CompleteSurgeryUseCase.Command(
                    new SurgeryLifecycleCommand(caseValue.getSurgeryCaseId(), caseValue.getRevision(),
                            schedule.revision(), idempotencyKey, actorIdentity, "workflow-complete"),
                    "PROC-001", "METHOD-001", "SUCCESS", null,
                    READY_AT, COMPLETE_AT,
                    List.of(new SurgeryPerformedItem(UUID.randomUUID(), "ITEM-001", "PRICE-001", BigDecimal.ONE)));
        }

        private CancelSurgeryUseCase.Command cancelCommand(String idempotencyKey, long expectedRevision) {
            return new CancelSurgeryUseCase.Command(caseValue.getSurgeryCaseId(), expectedRevision,
                    "Patient request", idempotencyKey, actorIdentity, "workflow-cancel");
        }

        private SurgeryConsentRecord consent(SurgeryConsentType type) {
            return consents.values.values().stream().filter(value -> value.consentType() == type && value.isActive())
                    .findFirst().orElseThrow();
        }

        private SurgeryConsentRecord consentForEvidence(SurgeryConsentType type) {
            return consents.values.values().stream()
                    .filter(value -> value.consentType() == type && value.isActive())
                    .findFirst()
                    .orElseGet(() -> consents.values.values().stream()
                            .filter(value -> value.consentType() == type)
                            .reduce((first, latest) -> latest)
                            .orElseThrow());
        }

        private SurgeryConsentRecord activeConsent(SurgeryConsentType type) {
            return consent(type);
        }

        private List<SurgeryConsentRecord> consentHistory(SurgeryConsentType type) {
            return consents.values.values().stream()
                    .filter(value -> value.consentType() == type).toList();
        }
    }

    private static final class DirectUnitOfWork implements SurgeryUnitOfWorkPort {
        @Override public <T> T read(Supplier<T> action) { return action.get(); }
        @Override public <T> T write(Supplier<T> action) { return action.get(); }
        @Override public <T> T outside(Supplier<T> action) { return action.get(); }
    }

    private static final class MutableClock implements SurgeryClockPort {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public Instant now() { return now; }
    }

    private static final class InMemoryCaseRepository implements SurgeryCaseRepositoryPort {
        private SurgeryCase value;
        @Override public Optional<SurgeryCase> findById(UUID caseId) {
            return value != null && value.getSurgeryCaseId().equals(caseId) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<SurgeryCase> findByRequestId(UUID requestId) {
            return value != null && value.getSurgeryRequestId().equals(requestId) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<SurgeryCase> lockById(UUID caseId) { return findById(caseId); }
        @Override public SurgeryCase save(SurgeryCase surgeryCase, long expectedRevision) {
            value = surgeryCase;
            return surgeryCase;
        }
    }

    private static final class InMemoryScheduleRepository implements SurgeryScheduleRepositoryPort {
        private SurgerySchedule value;
        @Override public Optional<SurgerySchedule> findByCaseId(UUID caseId) {
            return value != null && value.surgeryCaseId().equals(caseId) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<SurgerySchedule> findRevision(UUID caseId, long revision) {
            return findByCaseId(caseId).filter(schedule -> schedule.revision() == revision);
        }
        @Override public void saveDraft(SurgerySchedule schedule, long expectedRevision, Instant at) { value = schedule; }
    }

    private static final class InMemoryReceiptPort implements SurgeryCommandReceiptPort {
        private final Map<Key, Entry> entries = new LinkedHashMap<>();
        private int claimCount;

        @Override public Optional<Claim> find(Key key, String fingerprint) {
            Entry entry = entries.get(key);
            if (entry == null || !entry.fingerprint.equals(fingerprint) || entry.response == null) return Optional.empty();
            return Optional.of(new Claim(State.REPLAY, entry.receiptId, entry.responseCode, entry.response));
        }

        @Override public Claim claim(Key key, String fingerprint) {
            claimCount++;
            Entry entry = entries.get(key);
            if (entry == null) {
                entry = new Entry(UUID.randomUUID(), fingerprint);
                entries.put(key, entry);
                return new Claim(State.NEW, entry.receiptId, null, null);
            }
            if (!entry.fingerprint.equals(fingerprint)) return new Claim(State.CONFLICT, entry.receiptId, null, null);
            if (entry.response == null) return new Claim(State.IN_PROGRESS, entry.receiptId, null, null);
            return new Claim(State.REPLAY, entry.receiptId, entry.responseCode, entry.response);
        }

        @Override public void complete(UUID receiptId, UUID caseId, String responseCode, byte[] response, Instant at) {
            entries.values().stream().filter(entry -> entry.receiptId.equals(receiptId)).findFirst()
                    .orElseThrow().complete(responseCode, response);
        }

        int claimedCount() { return claimCount; }
        int completedFor(String responseCode) {
            return (int) entries.values().stream().filter(entry -> responseCode.equals(entry.responseCode)).count();
        }

        private static final class Entry {
            private final UUID receiptId;
            private final String fingerprint;
            private String responseCode;
            private byte[] response;
            private Entry(UUID receiptId, String fingerprint) { this.receiptId = receiptId; this.fingerprint = fingerprint; }
            private void complete(String responseCode, byte[] response) {
                this.responseCode = responseCode;
                this.response = response.clone();
            }
        }
    }

    private static final class InMemoryChecklistRepository implements SurgeryChecklistRepositoryPort {
        private SurgeryChecklistSnapshot value;
        @Override public void createTemplate(com.mediflow.surgery.domain.model.SurgeryChecklistTemplate template, Instant createdAt) { }
        @Override public Optional<com.mediflow.surgery.domain.model.SurgeryChecklistTemplate> findTemplate(String procedureCode, long revision) { return Optional.empty(); }
        @Override public void createSnapshot(SurgeryChecklistSnapshot snapshot) { value = snapshot; }
        @Override public Optional<SurgeryChecklistSnapshot> findSnapshotByCaseId(UUID caseId) {
            return value != null && value.surgeryCaseId().equals(caseId) ? Optional.of(value) : Optional.empty();
        }
        @Override public SurgeryChecklistSnapshot saveItemChange(SurgeryChecklistSnapshot snapshot,
                long expectedSnapshotRevision, SurgeryChecklistItemChange change) {
            value = snapshot;
            return snapshot;
        }
    }

    private static final class InMemoryConsentRepository implements SurgeryConsentRepositoryPort {
        private final Map<UUID, SurgeryConsentRecord> values = new LinkedHashMap<>();
        @Override public List<SurgeryConsentRecord> findByCaseId(UUID caseId) {
            return values.values().stream().filter(value -> value.surgeryCaseId().equals(caseId)).toList();
        }
        @Override public Optional<SurgeryConsentRecord> findById(UUID consentId) {
            return values.values().stream().filter(value -> value.consentId().equals(consentId)).findFirst();
        }
        @Override public SurgeryConsentRecord save(SurgeryConsentRecord consent) {
            values.put(consent.consentId(), consent);
            return consent;
        }
    }

    private static final class InMemoryClearanceRepository implements SurgeryFinancialClearanceRepositoryPort {
        private SurgeryFinancialClearance value;
        @Override public SaveDecision saveIfAbsentAndMatching(SurgeryFinancialClearance clearance) {
            if (value == null) { value = clearance; return SaveDecision.CREATED; }
            return value.equals(clearance) ? SaveDecision.MATCHING : SaveDecision.CONFLICT;
        }
        @Override public Optional<SurgeryFinancialClearance> lockById(UUID clearanceId) {
            return value != null && value.clearanceId().equals(clearanceId) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<SurgeryFinancialClearance> findById(UUID clearanceId) { return lockById(clearanceId); }
    }

    private static final class FinancialAuthority implements FinancialClearanceLookupPort {
        private final MutableClock clock;
        private boolean eligible = true;
        private FinancialAuthority(MutableClock clock) { this.clock = clock; }
        @Override public Observation observe(SurgeryFinancialClearance expected, String correlationId) {
            return new Observation(eligible, clock.now(), expected.expiresAt());
        }
    }

    private static final class InMemorySnapshots implements SurgeryReadinessSnapshotPort {
        private final List<ReadinessSnapshot> stored = new ArrayList<>();
        @Override public void store(ReadinessSnapshot snapshot) { stored.add(snapshot); }
        @Override public Optional<ReadinessSnapshot> findSnapshot(UUID snapshotId) {
            return stored.stream().filter(value -> value.snapshotId().equals(snapshotId)).findFirst();
        }
    }

    private static final class RecordingResources implements SurgeryResourceReservationPort {
        private final List<SurgerySchedule> lockCalls = new ArrayList<>();
        private final List<SurgerySchedule> reserveCalls = new ArrayList<>();
        private final List<InUseCall> inUseCalls = new ArrayList<>();
        private final List<ReleaseCall> releaseCalls = new ArrayList<>();
        @Override public void lockForMutation(SurgerySchedule schedule) { lockCalls.add(schedule); }
        @Override public void reserve(SurgerySchedule schedule, Instant at) { reserveCalls.add(schedule); }
        @Override public void markInUse(UUID caseId, UUID scheduleId, long scheduleRevision, Instant at) {
            inUseCalls.add(new InUseCall(caseId, scheduleId, scheduleRevision));
        }
        @Override public void release(UUID caseId, UUID scheduleId, long scheduleRevision, Instant at) {
            releaseCalls.add(new ReleaseCall(caseId, scheduleId, scheduleRevision));
        }
        private record InUseCall(UUID caseId, UUID scheduleId, long scheduleRevision) { }
        private record ReleaseCall(UUID caseId, UUID scheduleId, long scheduleRevision) { }
    }

    private static final class InMemoryResults implements SurgeryResultRepositoryPort {
        private final List<SurgeryResult> created = new ArrayList<>();
        @Override public Optional<SurgeryResult> findByCaseId(UUID caseId) {
            return created.stream().filter(value -> value.surgeryCaseId().equals(caseId)).findFirst();
        }
        @Override public SurgeryResult create(SurgeryResult result) { created.add(result); return result; }
    }

    private static final class RecordingAuthority implements SurgeryReadinessAuthorityPort {
        private final Workflow workflow;
        private RecordingAuthority(Workflow workflow) { this.workflow = workflow; }
        @Override public void authorize(SurgeryActorIdentity actor, String operation, UUID caseId) { }
        @Override public SurgeryReadinessEvidence observe(SurgeryCase value, SurgerySchedule schedule, String correlationId) {
            Instant now = workflow.clock.now();
            List<SurgeryDependencyRevision> dependencies = Arrays.stream(SurgeryDependencyType.values()).map(type -> {
                UUID source = switch (type) {
                    case INDICATION -> value.getSurgeryRequestId();
                    case CHECKLIST -> workflow.checklist.checklistSnapshotId();
                    case SURGERY_CONSENT -> workflow.consentForEvidence(SurgeryConsentType.SURGERY).consentId();
                    case ANESTHESIA_CONSENT -> workflow.consentForEvidence(SurgeryConsentType.ANESTHESIA).consentId();
                    case TEAM_ELIGIBILITY -> workflow.staffId;
                    case SCHEDULE -> schedule.scheduleId();
                    case FINANCIAL_CLEARANCE -> workflow.clearances.value.clearanceId();
                };
                long revision = switch (type) {
                    case CHECKLIST -> workflow.checklists.value.revision();
                    case SCHEDULE -> schedule.revision();
                    case SURGERY_CONSENT, ANESTHESIA_CONSENT -> workflow.consentForEvidence(
                            type == SurgeryDependencyType.SURGERY_CONSENT
                                    ? SurgeryConsentType.SURGERY : SurgeryConsentType.ANESTHESIA).auditHistory().size() - 1;
                    default -> 1;
                };
                return new SurgeryDependencyRevision(type, source, revision);
            }).toList();
            List<SurgeryReadinessEvidence.Proof> proofs = dependencies.stream().map(dependency ->
                    new SurgeryReadinessEvidence.Proof(dependency.dependencyType(), dependency.sourceId(), dependency.revision(),
                            SurgeryReadinessEvidence.Decision.SATISFIED, now.minusSeconds(1),
                            REQUESTED_AT.minusSeconds(1), REQUESTED_AT.plusSeconds(600))).toList();
            return new SurgeryReadinessEvidence(value.getSurgeryCaseId(), value.getPatientId(), value.getDepartmentId(),
                    value.getCareEpisode(), value.getRevision(), schedule.scheduleId(), schedule.revision(), proofs);
        }
        @Override public void reconcile(SurgeryCase value, SurgerySchedule schedule, SurgeryReadinessEvidence evidence) { }
        @Override public void verifyResult(SurgeryCase value, SurgeryResult result, String correlationId) { }
    }

    private static final class RecordingIntents implements SurgeryLifecycleIntentPort {
        private final List<ReadinessSnapshot> ready = new ArrayList<>();
        private final List<SurgeryResult> completed = new ArrayList<>();
        @Override public void holdReady(SurgeryCase value, SurgerySchedule schedule, ReadinessSnapshot snapshot, String correlationId) {
            ready.add(snapshot);
        }
        @Override public void holdCompleted(SurgeryCase value, SurgerySchedule schedule, SurgeryResult result, String correlationId) {
            completed.add(result);
        }
    }

    private static final class RecordingEvents implements SurgeryCareEventCapturePort {
        private final List<SurgeryCareEvent> held = new ArrayList<>();
        @Override public void hold(SurgeryCareEvent event, long caseRevision) { held.add(event); }
    }
}
