package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase.Command;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.application.service.SurgeryCreationApplicationService;
import com.mediflow.surgery.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real owned DB/fences/effects. Authority is explicitly LOCAL; no upstream approval or API activation. */
@Testcontainers
@SpringBootTest(properties = {"mediflow.jwt.secret=creation-test-secret-with-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class SurgeryCreationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }
    @Autowired JdbcTemplate db;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryChecklistRepositoryPort checklists;
    @Autowired SurgeryCreationReceiptPort receipts;
    @Autowired SurgeryCareEventCapturePort events;
    @Autowired SurgeryUnitOfWorkPort unitOfWork;
    @Autowired SurgeryCommandReceiptPort commandReceipts;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryResourceReservationPort reservations;
    @Autowired SurgeryInboxPort inbox;
    @Autowired SurgeryFinancialClearanceRepositoryPort clearances;
    @Autowired SurgeryClearanceWirePort clearanceDecoder;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired org.springframework.context.ApplicationContext context;
    private final SurgeryCreationAuthorityPort authority = mock(SurgeryCreationAuthorityPort.class);
    private final AtomicReference<Instant> time = new AtomicReference<>();
    private SurgeryCreationApplicationService service;
    private SurgeryChecklistTemplate template;
    private Command command;

    @BeforeEach void prepare() {
        reset(authority);
        db.execute("TRUNCATE surgery_case, surgery_creation_receipt, surgery_inbox, surgery_inbox_semantic_mutex CASCADE");
        time.set(Instant.now().minusSeconds(1).plusNanos(123));
        template = new SurgeryChecklistTemplate(UUID.randomUUID(), "P_" + UUID.randomUUID(), 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "TEST_ONLY", true, 1)));
        unitOfWork.write(() -> { checklists.createTemplate(template, time.get()); return null; });
        command = new Command(UUID.randomUUID(), new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), null, UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                template.procedureCode(), "Local fixture indication", SurgeryPriority.ROUTINE, time.get(), 1,
                List.of(new SurgeryPlannedItem("ITEM", "PRICE", new BigDecimal("1.00"))),
                SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID()), "creation-local");
        doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return null; })
                .when(authority).authorize(any());
        when(authority.observe(any(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return approval(call.getArgument(1));
        });
        service = new SurgeryCreationApplicationService(cases, checklists, receipts, authority, events, time::get, unitOfWork);
    }

    @Test void create_outpatient_selectedAppointmentAndDistinctRecordRemainExact() {
        var outcome = service.create(command);
        var stored = cases.findById(outcome.surgeryCaseId()).orElseThrow();
        assertThat(stored.getCareEpisode()).isEqualTo(command.careEpisode());
        assertThat(stored.getRequestedBy()).isEqualTo(command.requestedBy()).isNotEqualTo(command.actor().accountId());
        assertThat(stored.getStatus()).isEqualTo(SurgeryStatus.REQUESTED);
        assertCompleteOnce(outcome);
        var snapshot = checklists.findSnapshotByCaseId(outcome.surgeryCaseId()).orElseThrow();
        assertThat(snapshot.templateId()).isEqualTo(template.templateId());
        assertThat(snapshot.items()).allMatch(item -> item.status() == SurgeryChecklistStatus.PENDING);
        assertThat(receipts.find(command.requestId(), SurgeryCreationApplicationService.fingerprint(command)).orElseThrow().createdAt())
                .isEqualTo(time.get());
        assertThat(new String(db.queryForObject("SELECT payload FROM surgery_care_event_outbox", byte[].class), java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"eventType\":\"surgery.case.created\"", "\"sourceRevision\":1", "\"quantity\":1")
                .doesNotContain("Local fixture indication", "totalAmount", "paidAmount");
    }

    @Test void create_admission_exactEpisodeWithoutInferringPatientAssociation() {
        UUID admission = UUID.randomUUID();
        var input = withEpisode(new CareEpisode(CareEpisodeType.ADMISSION, admission, admission, UUID.randomUUID()));
        var outcome = service.create(input);
        assertThat(cases.findById(outcome.surgeryCaseId()).orElseThrow().getCareEpisode()).isEqualTo(input.careEpisode());
        assertCompleteOnce(outcome);
    }

    @Test void create_walkIn_recordSelectedAndSnapshotPinnedAgainstNewTemplateRevision() {
        UUID record = UUID.randomUUID();
        var input = withEpisode(new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, record, null, record));
        var outcome = service.create(input);
        var next = new SurgeryChecklistTemplate(UUID.randomUUID(), template.procedureCode(), 2,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "OTHER_TEST_ONLY", true, 1)));
        unitOfWork.write(() -> { checklists.createTemplate(next, time.get()); return null; });
        assertThat(checklists.findSnapshotByCaseId(outcome.surgeryCaseId()).orElseThrow().templateRevision()).isEqualTo(1);
        assertThat(service.create(input).asReplay()).isEqualTo(outcome.asReplay());
        assertCompleteOnce(outcome);
    }

    @Test void create_sameIntentDifferentTrustedRecorder_replaysWithoutLookupOrNewWire() {
        var outcome = service.create(command);
        byte[] bytes = db.queryForObject("SELECT payload FROM surgery_care_event_outbox", byte[].class);
        var other = new Command(command.requestId(), command.careEpisode(), command.patientId(), command.departmentId(),
                command.requestedBy(), command.procedureCode(), command.indication(), command.priority(), command.requestedAt(), 1,
                List.of(new SurgeryPlannedItem("ITEM", "PRICE", BigDecimal.ONE)), SurgeryAuditActor.system("local-fixture-only"), "other-delivery");
        doThrow(new IllegalStateException("must not call external lookup on replay")).when(authority).observe(any(), anyString());
        assertThat(service.create(other)).isEqualTo(outcome.asReplay());
        verify(authority).authorize(other);
        assertThat(db.queryForObject("SELECT payload FROM surgery_care_event_outbox", byte[].class)).containsExactly(bytes);
        assertCompleteOnce(outcome);
    }

    @Test void create_forbiddenReplay_noAuthorityOrMutation() {
        service.create(command);
        doThrow(new IllegalStateException("forbidden")).when(authority).authorize(command);
        assertThatThrownBy(() -> service.create(command)).hasMessage("forbidden");
        assertThat(count("surgery_case")).isOne();
        verify(authority, times(1)).observe(any(), anyString());
    }

    @Test void create_twoWorkersSameRequest_oneCaseSnapshotReceiptAndCharge() throws Exception {
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS); return approval(call.getArgument(1));
        }).when(authority).observe(any(), anyString());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.create(command));
            var second = workers.submit(() -> service.create(command));
            var one = first.get(20, TimeUnit.SECONDS); var two = second.get(20, TimeUnit.SECONDS);
            assertThat(one.asReplay()).isEqualTo(two.asReplay());
            assertThat(List.of(one.replayed(), two.replayed())).containsExactlyInAnyOrder(false, true);
            assertCompleteOnce(one);
        }
    }

    @Test void create_twoWorkersChangedClinicalIntent_oneWinnerOneConflict() throws Exception {
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS); return approval(call.getArgument(1));
        }).when(authority).observe(any(), anyString());
        var other = new Command(command.requestId(), command.careEpisode(), command.patientId(), command.departmentId(),
                command.requestedBy(), command.procedureCode(), "Different intent", command.priority(), command.requestedAt(), 1,
                command.plannedItems(), command.actor(), "second");
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> attempt(command)); var second = workers.submit(() -> attempt(other));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "CONFLICT");
            assertThat(count("surgery_case")).isOne();
            assertThat(count("surgery_care_event_outbox")).isOne();
            assertThat(count("surgery_creation_receipt")).isOne();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"surgery_case", "preop_checklist_snapshot", "surgery_care_event_outbox", "surgery_creation_receipt"})
    void create_injectedWriteFailure_rollsBackAllEffectsAndSameRequestRecovers(String table) {
        db.execute("CREATE OR REPLACE FUNCTION fail_creation_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test write failure'; END $$");
        db.execute("CREATE TRIGGER fail_creation BEFORE " + (table.equals("surgery_creation_receipt") ? "UPDATE" : "INSERT")
                + " ON " + table + " FOR EACH ROW EXECUTE FUNCTION fail_creation_test()");
        try {
            assertThatThrownBy(() -> service.create(command)).isInstanceOf(RuntimeException.class);
            for (var target : List.of("surgery_case", "preop_checklist_snapshot", "preop_checklist_item", "surgery_status_history",
                    "surgery_revision_history", "surgery_care_event_outbox", "surgery_creation_receipt")) assertThat(count(target)).as(target).isZero();
        } finally { db.execute("DROP TRIGGER fail_creation ON " + table); }
        assertCompleteOnce(service.create(command));
    }

    @Test void create_callerTransactionSuspended_preflightOutsideAndCreationCommitsIndependently() {
        unitOfWork.write(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            service.create(command);
            return null;
        });
        assertThat(count("surgery_case")).isOne();
        verify(authority).authorize(command);
    }

    @Test void create_reconstructedServiceAfterRestart_returnsExactReceiptWithoutAuthorityLookup() {
        var original = service.create(command);
        var restarted = new SurgeryCreationApplicationService(cases, checklists, new SurgeryCreationReceiptAdapter(db), authority,
                events, () -> time.get().plusSeconds(86400), unitOfWork);
        doThrow(new IllegalStateException("no replay lookup")).when(authority).observe(any(), anyString());
        assertThat(restarted.create(command)).isEqualTo(original.asReplay());
        assertCompleteOnce(original);
    }

    @Test void create_approvalExpiresWhileWaiting_rechecksClockAfterRequestLock() throws Exception {
        var request = command.requestId(); var fingerprint = SurgeryCreationApplicationService.fingerprint(command);
        var locked = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> unitOfWork.write(() -> {
                receipts.claim(request, fingerprint); locked.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                // Deliberately rolled back placeholder, never a committed fake PENDING receipt.
                throw new IllegalStateException("holder rollback");
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            doAnswer(call -> {
                var proof = approval(call.getArgument(1)); time.set(proof.validUntil()); release.countDown(); return proof;
            }).when(authority).observe(any(), anyString());
            var creator = workers.submit(() -> service.create(command));
            assertThatThrownBy(() -> holder.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> creator.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.mediflow.surgery.domain.exception.SurgeryRuleException.class);
            assertThat(count("surgery_case")).isZero(); assertThat(count("surgery_creation_receipt")).isZero();
        }
    }

    @Test void create_preopThenCancel_creationReplayNeverReopensTerminalCaseOrRepeatsCharge() {
        var original = service.create(command);
        byte[] createdBytes = db.queryForObject("SELECT payload FROM surgery_care_event_outbox", byte[].class);
        var actor = new com.mediflow.surgery.application.dto.SurgeryActorIdentity(command.actor().accountId(), command.actor().verifiedStaffId());
        var preop = new com.mediflow.surgery.application.service.SurgeryPreopApplicationService(cases, commandReceipts, time::get);
        time.set(time.get().plusSeconds(1));
        unitOfWork.write(() -> preop.begin(new com.mediflow.surgery.application.port.in.BeginPreopUseCase.Command(
                original.surgeryCaseId(), 0, "begin", actor, "creation-vertical")));
        var cancel = new com.mediflow.surgery.application.service.SurgeryCancellationApplicationService(cases, schedules,
                reservations, commandReceipts, time::get, events);
        time.set(time.get().plusSeconds(1));
        var cancellation = unitOfWork.write(() -> cancel.cancel(new com.mediflow.surgery.application.port.in.CancelSurgeryUseCase.Command(
                original.surgeryCaseId(), 1, "Test-only pre-start cancellation", "cancel", actor, "creation-vertical")));
        assertThat(cancellation.state()).isEqualTo("CANCELLED");
        doThrow(new IllegalStateException("must not reopen or reobserve")).when(authority).observe(any(), anyString());
        assertThat(service.create(command)).isEqualTo(original.asReplay());
        assertThat(cases.findById(original.surgeryCaseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(count("surgery_case")).isOne();
        assertThat(count("surgery_creation_receipt")).isOne();
        assertThat(count("surgery_status_history")).isEqualTo(3);
        assertThat(count("surgery_revision_history")).isEqualTo(3);
        assertThat(count("surgery_resource_reservation")).isZero();
        assertThat(db.queryForList("SELECT event_type FROM surgery_care_event_outbox", String.class))
                .containsExactlyInAnyOrder("surgery.case.created", "surgery.cancelled");
        assertThat(db.queryForObject("SELECT payload FROM surgery_care_event_outbox WHERE event_type='surgery.case.created'", byte[].class))
                .containsExactly(createdBytes);
        assertThat(db.queryForList("SELECT delivery_status FROM surgery_care_event_outbox", String.class)).containsOnly("HELD");
    }

    @Test void create_productionContext_hasNoAuthorityOrCreateUseCaseFallback() {
        assertThat(context.getBeansOfType(SurgeryCreationAuthorityPort.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("mediflow.features.surgery.enabled", Boolean.class)).isFalse();
    }

    @Test void create_damagedCommittedPendingReceipt_failsClosedWithoutNewCaseOrRemoteLookup() {
        unitOfWork.write(() -> receipts.claim(command.requestId(), SurgeryCreationApplicationService.fingerprint(command)));
        assertThatThrownBy(() -> service.create(command)).isInstanceOf(SurgeryRevisionConflictException.class);
        verify(authority, never()).observe(any(), anyString());
        assertThat(count("surgery_case")).isZero();
        assertThat(count("surgery_care_event_outbox")).isZero();
    }

    @Test void create_winnerCommitsDuringFailingPreflight_replaysOnlyCommittedExactIntent() {
        var winnerAuthority = new SurgeryCreationAuthorityPort() {
            @Override public void authorize(Command input) { }
            @Override public Approval observe(Command input, String fingerprint) { return approval(fingerprint); }
        };
        var winnerService = new SurgeryCreationApplicationService(cases, checklists, receipts, winnerAuthority, events, time::get, unitOfWork);
        var winner = new AtomicReference<SurgeryCreationOutcome>();
        doAnswer(call -> {
            winner.set(winnerService.create(command));
            throw new com.mediflow.surgery.application.exception.UpstreamUnavailableException("test preflight interrupted");
        }).when(authority).observe(any(), anyString());
        assertThat(service.create(command)).isEqualTo(winner.get().asReplay());
        assertCompleteOnce(winner.get());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void complete_foreignCaseOrChecklist_receiptCannotCertifyAnotherRequest(boolean foreignCase) {
        var original = service.create(command);
        var otherInput = new Command(UUID.randomUUID(), command.careEpisode(), command.patientId(), command.departmentId(), command.requestedBy(),
                command.procedureCode(), command.indication(), command.priority(), command.requestedAt(), 1,
                command.plannedItems(), command.actor(), "other-request");
        var other = service.create(otherInput);
        UUID thirdRequest = UUID.randomUUID();
        assertThatThrownBy(() -> unitOfWork.write(() -> {
            var claim = receipts.claim(thirdRequest, "a".repeat(64));
            var thirdCase = SurgeryCase.create(UUID.randomUUID(), thirdRequest, command.careEpisode(), command.patientId(),
                    command.departmentId(), command.requestedBy(), command.procedureCode(), command.indication(), command.priority(),
                    command.requestedAt(), command.actor(), "foreign-receipt-negative");
            cases.save(thirdCase, -1);
            checklists.createSnapshot(template.snapshotForCase(UUID.randomUUID(), thirdCase.getSurgeryCaseId()));
            receipts.complete(claim.receiptId(), new SurgeryCreationOutcome(
                    thirdRequest, foreignCase ? original.surgeryCaseId() : thirdCase.getSurgeryCaseId(),
                    foreignCase ? original.checklistSnapshotId() : other.checklistSnapshotId(), time.get(), false));
            return null;
        })).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(count("surgery_creation_receipt")).isEqualTo(2);
        assertThat(count("surgery_case")).isEqualTo(2);
        assertThat(db.queryForList("SELECT state FROM surgery_creation_receipt", String.class)).containsOnly("COMPLETED");
    }

    @Test void create_requestFenceTimeout_boundedWholeTransactionRetryThenTypedBusyAndRecovery() throws Exception {
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> unitOfWork.write(() -> {
                receipts.claim(command.requestId(), SurgeryCreationApplicationService.fingerprint(command)); locked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                throw new IllegalStateException("holder rollback");
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> service.create(command))
                        .isInstanceOf(com.mediflow.surgery.application.exception.SurgeryCommandBusyException.class);
                assertThat(count("surgery_case")).isZero();
                assertThat(count("surgery_creation_receipt")).isZero();
                assertThat(count("surgery_care_event_outbox")).isZero();
                verify(authority, times(1)).observe(any(), anyString());
            } finally { release.countDown(); }
            assertThatThrownBy(() -> holder.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
        }
        assertCompleteOnce(service.create(command));
    }

    @Test void create_uncommittedCaseInvisibleToEarlyGrant_durableWorkerRecoversAfterCommitWithoutCallback() throws Exception {
        UUID admission = UUID.randomUUID();
        var input = withEpisode(new CareEpisode(CareEpisodeType.ADMISSION, admission, admission, null));
        var receiver = new com.mediflow.surgery.application.service.SurgeryFinancialClearanceService(cases, clearances, inbox,
                schedules, reservations, time::get, events);
        var retainedBytes = new AtomicReference<byte[]>();
        // Test-only re-addressing of a Billing schema fixture, NOT new producer/consumer contract approval.
        SurgeryCareEventCapturePort capture = (event, revision) -> {
            events.hold(event, revision);
            var payload = (com.mediflow.surgery.application.event.SurgeryCareEvent.Created) event.payload();
            try {
                var fixture = java.nio.file.Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-surgery.json");
                var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(java.nio.file.Files.readAllBytes(fixture));
                root.put("occurredAt", time.get().toString());
                var grant = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload");
                grant.put("patientId", input.patientId().toString()); grant.put("admissionId", admission.toString());
                grant.put("careEpisodeId", admission.toString()); grant.put("surgeryCaseId", payload.surgeryCaseId().toString());
                byte[] bytes = mapper.writeValueAsBytes(root);
                var delivery = clearanceDecoder.decode("financial.clearance.granted", bytes, time.get());
                // Existing decoder canonicalizes field order; persist/retry its accepted bytes, not original formatting.
                assertThat(mapper.readTree(delivery.incoming().payload())).isEqualTo(mapper.readTree(bytes));
                retainedBytes.set(delivery.incoming().payload());
                var state = unitOfWork.write(() -> receiver.receive(delivery));
                assertThat(state).isEqualTo(com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase.Outcome.DEFERRED);
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        };
        var creation = new SurgeryCreationApplicationService(cases, checklists, receipts, authority, capture, time::get, unitOfWork);
        var outcome = creation.create(input);
        assertThat(db.queryForObject("SELECT status FROM surgery_inbox", String.class)).isEqualTo("PENDING");
        assertThat(count("surgery_financial_clearance")).isZero();
        assertThat(db.queryForObject("SELECT payload FROM surgery_inbox", byte[].class)).containsExactly(retainedBytes.get());
        time.set(time.get().plusSeconds(61));
        var query = new com.mediflow.surgery.application.service.PendingSurgeryClearanceQueryService(inbox, time::get);
        var retry = new com.mediflow.surgery.application.service.SurgeryClearanceRetryService(inbox, time::get);
        var restartedWorker = new com.mediflow.surgery.messaging.consumer.PendingSurgeryClearanceWorker(
                limit -> unitOfWork.read(() -> query.due(limit)),
                value -> unitOfWork.write(() -> receiver.receive(value)), clearanceDecoder,
                value -> unitOfWork.write(() -> { retry.deferFailure(value); return null; }));
        restartedWorker.poll(); restartedWorker.poll();
        assertThat(db.queryForObject("SELECT status FROM surgery_inbox", String.class)).isEqualTo("APPLIED");
        assertThat(db.queryForObject("SELECT payload FROM surgery_inbox", byte[].class)).containsExactly(retainedBytes.get());
        assertThat(count("surgery_financial_clearance")).isOne();
        assertThat(cases.findById(outcome.surgeryCaseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(count("surgery_care_event_outbox")).isOne();
        assertThat(creation.create(input)).isEqualTo(outcome.asReplay());
    }

    private SurgeryCreationAuthorityPort.Approval approval(String fingerprint) {
        return new SurgeryCreationAuthorityPort.Approval(fingerprint, template.templateId(), 1, time.get(), time.get().plusSeconds(30));
    }
    private String attempt(Command input) {
        try { service.create(input); return "CREATED"; } catch (SurgeryRevisionConflictException conflict) { return "CONFLICT"; }
    }
    private Command withEpisode(CareEpisode episode) {
        return new Command(command.requestId(), episode, command.patientId(), command.departmentId(), command.requestedBy(),
                command.procedureCode(), command.indication(), command.priority(), command.requestedAt(), 1,
                command.plannedItems(), command.actor(), command.correlationId());
    }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void assertCompleteOnce(SurgeryCreationOutcome outcome) {
        for (var table : List.of("surgery_case", "preop_checklist_snapshot", "preop_checklist_item", "surgery_status_history",
                "surgery_revision_history", "surgery_care_event_outbox", "surgery_creation_receipt")) assertThat(count(table)).as(table).isOne();
        assertThat(db.queryForList("SELECT delivery_status FROM surgery_care_event_outbox", String.class)).containsExactly("HELD");
        assertThat(db.queryForList("SELECT state FROM surgery_creation_receipt", String.class)).containsExactly("COMPLETED");
        assertThat(db.queryForObject("SELECT surgery_case_id FROM surgery_creation_receipt WHERE surgery_request_id=?", UUID.class,
                outcome.requestId())).isEqualTo(outcome.surgeryCaseId());
    }
}
