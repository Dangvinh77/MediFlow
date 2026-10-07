package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.SurgeryScheduleConflictException;
import com.mediflow.surgery.application.port.in.CancelSurgeryUseCase;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.application.port.in.ExpireSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.PrepareSurgeryScheduleUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.port.out.AdmissionLookupPort;
import com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.service.SurgeryLifecycleApplicationService;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Real PostgreSQL race matrix for Surgery command boundaries.
 *
 * <p>The authority stubs below are local test policy ports only.  They do not
 * claim a Clinical/Organization/Billing contract or seed data in another
 * service.  Every worker enters through the application service and therefore
 * exercises the production UoW transaction boundaries and row/resource locks.
 */
@Testcontainers
@SpringBootTest(properties = {
        "mediflow.jwt.secret=surgery-race-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "mediflow.features.surgery.enabled=true"
})
@Import(SurgeryLifecycleIntegrationTest.LocalOnlyWiring.class)
class SurgeryLifecycleRacePostgresTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private final Map<UUID, Fixture> fixtures = new java.util.concurrent.ConcurrentHashMap<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired JdbcTemplate db;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryChecklistRepositoryPort checklists;
    @Autowired SurgeryConsentRepositoryPort consents;
    @Autowired SurgeryFinancialClearanceRepositoryPort clearances;
    @Autowired SurgeryLifecycleApplicationService lifecycle;
    @Autowired CancelSurgeryUseCase cancellation;
    @Autowired PrepareSurgeryScheduleUseCase schedulePreparation;
    @Autowired UpdateChecklistItemUseCase checklistUpdates;
    @Autowired ManageSurgeryConsentUseCase consentManagement;
    @Autowired ExpireSurgeryReadinessUseCase readinessExpiry;
    @Autowired AtomicReference<Instant> lifecycleTestTime;

    @MockBean SurgeryReadinessAuthorityPort authority;
    @MockBean FinancialClearanceLookupPort financialAuthority;
    @MockBean OrganizationLookupPort organization;
    @MockBean AdmissionLookupPort admissions;

    @BeforeEach
    void resetDatabaseAndLocalAuthorities() {
        db.execute("TRUNCATE surgery_case CASCADE");
        fixtures.clear();
        lifecycleTestTime.set(NOW);
        reset(authority, financialAuthority, organization, admissions);

        when(authority.observe(any(), any(), anyString()))
                .thenAnswer(invocation -> readinessEvidence(invocation.getArgument(0), invocation.getArgument(1)));
        when(financialAuthority.observe(any(), anyString()))
                .thenAnswer(invocation -> new FinancialClearanceLookupPort.Observation(
                        true, lifecycleTestTime.get(), NOW.plusSeconds(600)));
        when(organization.findDepartment(any(), anyString())).thenAnswer(invocation ->
                new OrganizationLookupPort.OrganizationLookupSnapshot(
                        OrganizationLookupPort.ReferenceKind.DEPARTMENT, invocation.getArgument(0),
                        OrganizationLookupPort.ReferenceState.ACTIVE, lifecycleTestTime.get(), "department-v1", null));
        when(organization.findRoom(any(), anyString())).thenAnswer(invocation ->
                new OrganizationLookupPort.OrganizationLookupSnapshot(
                        OrganizationLookupPort.ReferenceKind.ROOM, invocation.getArgument(0),
                        OrganizationLookupPort.ReferenceState.ACTIVE, lifecycleTestTime.get(), "room-v1", null,
                        null, fixtures.values().stream().findFirst().map(Fixture::departmentId).orElse(null)));
        when(organization.findSurgicalEligibility(any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> new OrganizationLookupPort.SurgicalEligibilitySnapshot(
                        invocation.getArgument(0), invocation.getArgument(1), OrganizationLookupPort.ReferenceState.ACTIVE,
                        fixtures.values().stream().findFirst().map(Fixture::departmentId).orElse(null),
                        lifecycleTestTime.get(), "staff-v1", invocation.getArgument(2), invocation.getArgument(3)));
    }

    @Test
    void finalizeAndCancel_sameRevision_onlyOneCommitsAndLoserReceiptRollsBack() throws Exception {
        Fixture fixture = ready(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand finalize = lifecycleCommand(fixture, expectedRevision, 1, "finalize-race");
        CancelSurgeryUseCase.Command cancel = cancelCommand(fixture, expectedRevision, "cancel-race");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.finalizeSchedule(finalize), () -> cancellation.cancel(cancel));

        assertExactlyOneSuccess(attempts);
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isIn(SurgeryStatus.SCHEDULED, SurgeryStatus.CANCELLED);
        assertThat(count("surgery_command_receipt")).isEqualTo(2);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        if (after.getStatus() == SurgeryStatus.SCHEDULED) {
            assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RESERVED", "RESERVED");
            assertThat(eventCount("surgery.cancelled")).isZero();
        } else {
            assertThat(reservationStatuses(fixture)).isEmpty();
            assertThat(eventCount("surgery.cancelled")).isOne();
        }
        assertThat(count("surgery_lifecycle_intent")).isOne();
        assertCaseHistory(after);
    }

    @Test
    void finalizeAndReschedule_sameRevision_onlyOneCommitsAndInvalidationIsAtomic() throws Exception {
        Fixture fixture = ready(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand finalize = lifecycleCommand(fixture, expectedRevision, 1, "finalize-reschedule-race");
        PrepareSurgeryScheduleUseCase.Command reschedule = prepareCommand(fixture, expectedRevision, 1,
                NOW.plusSeconds(360), NOW.plusSeconds(480), "reschedule-race");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.finalizeSchedule(finalize), () -> schedulePreparation.prepare(reschedule));

        assertExactlyOneSuccess(attempts);
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isIn(SurgeryStatus.SCHEDULED, SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(count("surgery_command_receipt")).isEqualTo(2);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        if (after.getStatus() == SurgeryStatus.SCHEDULED) {
            assertThat(schedules.findByCaseId(fixture.caseId()).orElseThrow().revision()).isEqualTo(1);
            assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RESERVED", "RESERVED");
            assertThat(eventCount("surgery.readiness.invalidated")).isZero();
        } else {
            assertThat(schedules.findByCaseId(fixture.caseId()).orElseThrow().revision()).isEqualTo(2);
            assertThat(reservationStatuses(fixture)).isEmpty();
            assertThat(eventCount("surgery.readiness.invalidated")).isOne();
        }
        assertThat(count("surgery_lifecycle_intent")).isOne();
        assertCaseHistory(after);
    }

    @Test
    void evaluateAndReschedule_sameRevision_onlyOneCommitsAndDraftOrReadinessIsWhole() throws Exception {
        Fixture fixture = seed(true, NOW.plusSeconds(600));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand evaluate = lifecycleCommand(fixture, expectedRevision, 1, "evaluate-reschedule-race");
        PrepareSurgeryScheduleUseCase.Command reschedule = prepareCommand(fixture, expectedRevision, 1,
                NOW.plusSeconds(420), NOW.plusSeconds(540), "evaluate-reschedule-draft");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.evaluate(evaluate), () -> schedulePreparation.prepare(reschedule));

        assertExactlyOneSuccess(attempts);
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isIn(SurgeryStatus.READY, SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(count("surgery_command_receipt")).isEqualTo(1);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertThat(reservationStatuses(fixture)).isEmpty();
        if (after.getStatus() == SurgeryStatus.READY) {
            assertThat(schedules.findByCaseId(fixture.caseId()).orElseThrow().revision()).isEqualTo(1);
            assertThat(count("surgery_lifecycle_intent")).isOne();
        } else {
            assertThat(schedules.findByCaseId(fixture.caseId()).orElseThrow().revision()).isEqualTo(2);
            assertThat(count("surgery_lifecycle_intent")).isZero();
        }
        assertCaseHistory(after);
    }

    @Test
    void evaluateAndChecklistChange_sameRevision_onlyOneCommitsAndNoPartialReadiness() throws Exception {
        Fixture fixture = seed(true, NOW.plusSeconds(600));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand evaluate = lifecycleCommand(fixture, expectedRevision, 1, "evaluate-checklist-race");
        UpdateChecklistItemUseCase.Command checklist = failedChecklistCommand(fixture, expectedRevision, "checklist-race");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.evaluate(evaluate), () -> checklistUpdates.update(checklist));

        assertExactlyOneSuccess(attempts);
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isIn(SurgeryStatus.READY, SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(count("surgery_command_receipt")).isEqualTo(1);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        if (after.getStatus() == SurgeryStatus.READY) {
            assertThat(checklists.findSnapshotByCaseId(fixture.caseId()).orElseThrow().mandatoryChecklistComplete()).isTrue();
            assertThat(count("surgery_lifecycle_intent")).isOne();
        } else {
            assertThat(checklists.findSnapshotByCaseId(fixture.caseId()).orElseThrow().mandatoryChecklistComplete()).isFalse();
            assertThat(count("surgery_lifecycle_intent")).isZero();
            assertThat(eventCount("surgery.readiness.invalidated")).isZero();
        }
        assertCaseHistory(after);
    }

    @Test
    void evaluateAndConsentRevoke_sameRevision_onlyOneCommitsAndConsentOrReadinessWins() throws Exception {
        Fixture fixture = seed(true, NOW.plusSeconds(600));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand evaluate = lifecycleCommand(fixture, expectedRevision, 1, "evaluate-consent-race");
        ManageSurgeryConsentUseCase.RevokeCommand revoke = revokeCommand(fixture, expectedRevision,
                fixture.consents().get(SurgeryConsentType.SURGERY), "consent-race");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.evaluate(evaluate), () -> consentManagement.revoke(revoke));

        assertExactlyOneSuccess(attempts);
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isIn(SurgeryStatus.READY, SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(count("surgery_command_receipt")).isEqualTo(1);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        boolean active = consents.findById(fixture.consents().get(SurgeryConsentType.SURGERY)).orElseThrow().isActive();
        if (after.getStatus() == SurgeryStatus.READY) {
            assertThat(active).isTrue();
            assertThat(count("surgery_lifecycle_intent")).isOne();
        } else {
            assertThat(active).isFalse();
            assertThat(count("surgery_lifecycle_intent")).isZero();
        }
        assertCaseHistory(after);
    }

    @Test
    void startAndConsentRevoke_sameRevision_onlyOneCommitsAndResourceSetMatchesStatus() throws Exception {
        Fixture fixture = scheduled(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand start = lifecycleCommand(fixture, expectedRevision, 1, "start-consent-race");
        ManageSurgeryConsentUseCase.RevokeCommand revoke = revokeCommand(fixture, expectedRevision,
                fixture.consents().get(SurgeryConsentType.ANESTHESIA), "start-consent-revoke-race");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.start(start), () -> consentManagement.revoke(revoke));

        assertExactlyOneSuccess(attempts);
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isIn(SurgeryStatus.IN_PROGRESS, SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(count("surgery_command_receipt")).isEqualTo(3);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        if (after.getStatus() == SurgeryStatus.IN_PROGRESS) {
            assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("IN_USE", "IN_USE");
            assertThat(eventCount("surgery.readiness.invalidated")).isZero();
        } else {
            assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RELEASED", "RELEASED");
            assertThat(eventCount("surgery.readiness.invalidated")).isOne();
        }
        assertThat(count("surgery_lifecycle_intent")).isOne();
        assertCaseHistory(after);
    }

    @Test
    void startAndExpiry_sameRevision_onlyOneCommitsAndExpiredReadinessCannotUseResources() throws Exception {
        Fixture fixture = scheduled(seed(true, NOW.plusSeconds(5)));
        long expectedRevision = currentCase(fixture).getRevision();
        lifecycleTestTime.set(NOW.plusSeconds(10));
        SurgeryLifecycleCommand start = lifecycleCommand(fixture, expectedRevision, 1, "start-expiry-race");
        UUID readinessSnapshotId = currentCase(fixture).getReadinessSnapshot().readinessSnapshotId();
        QueryExpiredSurgeryReadinessUseCase.Candidate candidate =
                new QueryExpiredSurgeryReadinessUseCase.Candidate(fixture.caseId(), readinessSnapshotId);
        List<Attempt<Object>> attempts = race(() -> (Object) lifecycle.start(start),
                () -> (Object) readinessExpiry.expire(candidate, "expiry-race"));

        assertThat(attempts).hasSize(2);
        Attempt<Object> startAttempt = attempts.getFirst();
        Attempt<Object> expiryAttempt = attempts.get(1);
        boolean startMutated = startAttempt.value() instanceof SurgeryCommandOutcome outcome
                && "READINESS_EXPIRED".equals(outcome.state());
        boolean expiryMutated = Boolean.TRUE.equals(expiryAttempt.value());
        assertThat(startMutated).isNotEqualTo(expiryMutated);
        if (startMutated) {
            assertThat(expiryAttempt.error()).isNull();
            assertThat(expiryMutated).isFalse();
        } else {
            assertThat(startAttempt.error()).isInstanceOfAny(SurgeryRevisionConflictException.class,
                    SurgeryScheduleConflictException.class, SurgeryRuleException.class);
            assertThat(expiryMutated).isTrue();
        }
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(after.getReadinessSnapshot()).isNull();
        assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RELEASED", "RELEASED");
        assertThat(eventCount("surgery.readiness.invalidated")).isOne();
        assertThat(count("surgery_command_receipt")).isIn(2L, 3L);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertThat(count("surgery_lifecycle_intent")).isOne();
        assertThat(countForCase("surgery_readiness_snapshot", fixture.caseId())).isBetween(1L, 2L);
        assertThat(latestRevisionTimestamp(fixture)).isEqualTo(NOW.plusSeconds(10));
        assertCaseHistory(after);
    }

    @Test
    void completeAndComplete_distinctIdempotencyKeys_onlyOneResultAndOneHeldIntent() throws Exception {
        Fixture fixture = inProgress(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        CompleteSurgeryUseCase.Command first = completionCommand(fixture, expectedRevision, "complete-worker-a");
        CompleteSurgeryUseCase.Command second = completionCommand(fixture, expectedRevision, "complete-worker-b");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.complete(first), () -> lifecycle.complete(second));

        assertExactlyOneSuccess(attempts);
        assertThat(currentCase(fixture).getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(count("surgery_result")).isOne();
        assertThat(count("surgery_performed_item")).isOne();
        assertThat(count("surgery_lifecycle_intent")).isEqualTo(2);
        assertThat(countWhere("surgery_lifecycle_intent", "delivery_status <> 'HELD'")).isZero();
        assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RELEASED", "RELEASED");
        assertThat(count("surgery_command_receipt")).isEqualTo(4);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertCaseHistory(currentCase(fixture));
    }

    @Test
    void complete_sameIdempotencyKey_replaysCommittedResultWithoutDuplicateHeldIntent() {
        Fixture fixture = inProgress(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        CompleteSurgeryUseCase.Command command = completionCommand(fixture, expectedRevision, "complete-replay");

        SurgeryCommandOutcome first = lifecycle.complete(command);
        SurgeryCommandOutcome replay = lifecycle.complete(command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.state()).isEqualTo("COMPLETED");
        SurgeryCase after = currentCase(fixture);
        assertThat(after.getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(count("surgery_result")).isOne();
        assertThat(count("surgery_performed_item")).isOne();
        assertThat(count("surgery_lifecycle_intent")).isEqualTo(2);
        assertThat(count("surgery_command_receipt")).isEqualTo(4);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RELEASED", "RELEASED");
        assertCaseHistory(after);
    }

    @Test
    void completeAndCancel_sameRevision_completionWinsAndCancelCannotLeavePartialResult() throws Exception {
        Fixture fixture = inProgress(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        CompleteSurgeryUseCase.Command complete = completionCommand(fixture, expectedRevision, "complete-cancel-race");
        CancelSurgeryUseCase.Command cancel = cancelCommand(fixture, expectedRevision, "cancel-after-start-race");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.complete(complete), () -> cancellation.cancel(cancel));

        assertExactlyOneSuccess(attempts);
        assertThat(currentCase(fixture).getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(count("surgery_result")).isOne();
        assertThat(count("surgery_performed_item")).isOne();
        assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RELEASED", "RELEASED");
        assertThat(count("surgery_lifecycle_intent")).isEqualTo(2);
        assertThat(eventCount("surgery.cancelled")).isZero();
        assertThat(count("surgery_command_receipt")).isEqualTo(4);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertCaseHistory(currentCase(fixture));
    }

    @Test
    void startAndStart_distinctIdempotencyKeys_oneInUseSetAndOneRolledBackReceipt() throws Exception {
        Fixture fixture = scheduled(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand first = lifecycleCommand(fixture, expectedRevision, 1, "start-worker-a");
        SurgeryLifecycleCommand second = lifecycleCommand(fixture, expectedRevision, 1, "start-worker-b");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.start(first), () -> lifecycle.start(second));

        assertExactlyOneSuccess(attempts);
        assertThat(currentCase(fixture).getStatus()).isEqualTo(SurgeryStatus.IN_PROGRESS);
        assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("IN_USE", "IN_USE");
        assertThat(count("surgery_command_receipt")).isEqualTo(3);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertThat(count("surgery_lifecycle_intent")).isEqualTo(1);
        assertCaseHistory(currentCase(fixture));
    }

    @Test
    void finalizeAndFinalize_distinctIdempotencyKeys_oneReservationSetAndNoDuplicateMutation() throws Exception {
        Fixture fixture = ready(seed(true, NOW.plusSeconds(600)));
        long expectedRevision = currentCase(fixture).getRevision();
        SurgeryLifecycleCommand first = lifecycleCommand(fixture, expectedRevision, 1, "finalize-worker-a");
        SurgeryLifecycleCommand second = lifecycleCommand(fixture, expectedRevision, 1, "finalize-worker-b");
        List<? extends Attempt<?>> attempts = race(() -> lifecycle.finalizeSchedule(first), () -> lifecycle.finalizeSchedule(second));

        assertExactlyOneSuccess(attempts);
        assertThat(currentCase(fixture).getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(reservationStatuses(fixture)).containsExactlyInAnyOrder("RESERVED", "RESERVED");
        assertThat(count("surgery_command_receipt")).isEqualTo(2);
        assertThat(countWhere("surgery_command_receipt", "status = 'PENDING'")).isZero();
        assertThat(count("surgery_resource_reservation")).isEqualTo(2);
        assertCaseHistory(currentCase(fixture));
    }

    private Fixture ready(Fixture fixture) {
        SurgeryCommandOutcome outcome = lifecycle.evaluate(lifecycleCommand(fixture,
                currentCase(fixture).getRevision(), 1, "evaluate-" + fixture.caseId()));
        assertThat(outcome.state()).isEqualTo("READY");
        return fixture;
    }

    private Fixture scheduled(Fixture fixture) {
        ready(fixture);
        SurgeryCase value = currentCase(fixture);
        SurgeryCommandOutcome outcome = lifecycle.finalizeSchedule(
                lifecycleCommand(fixture, value.getRevision(), 1, "finalize-" + fixture.caseId()));
        assertThat(outcome.state()).isEqualTo("SCHEDULED");
        return fixture;
    }

    private Fixture inProgress(Fixture fixture) {
        scheduled(fixture);
        SurgeryCase value = currentCase(fixture);
        SurgeryCommandOutcome outcome = lifecycle.start(
                lifecycleCommand(fixture, value.getRevision(), 1, "start-" + fixture.caseId()));
        assertThat(outcome.state()).isEqualTo("IN_PROGRESS");
        lifecycleTestTime.set(NOW.plusSeconds(20));
        return fixture;
    }

    private Fixture seed(boolean checklistSatisfied, Instant validUntil) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(ignored -> {
            UUID caseId = UUID.randomUUID();
            UUID departmentId = UUID.randomUUID();
            UUID roomId = UUID.randomUUID();
            UUID patientId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            String procedureCode = "TEST-" + caseId.toString().replace("-", "").substring(0, 20);
            UUID staff = STAFF;
            SurgeryAuditActor actor = SurgeryAuditActor.human(ACCOUNT, staff);
            SurgeryCase value = SurgeryCase.create(caseId, requestId,
                    new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                    patientId, departmentId, staff, procedureCode, "Local race prerequisite",
                    SurgeryPriority.ROUTINE, NOW.minusSeconds(120), actor, "race-seed");
            cases.save(value, -1);
            value.beginPreop(actor, "race-seed", NOW.minusSeconds(110));
            cases.save(value, 0);

            UUID templateId = UUID.randomUUID();
            SurgeryChecklistTemplate template = new SurgeryChecklistTemplate(templateId, procedureCode, 1,
                    List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "PREOP-CHECK", true, 1)));
            checklists.createTemplate(template, NOW.minusSeconds(100));
            SurgeryChecklistSnapshot snapshot = template.snapshotForCase(UUID.randomUUID(), caseId);
            checklists.createSnapshot(snapshot);
            SurgeryChecklistItem firstItem = snapshot.items().getFirst();
            long checklistRevision = 0;
            UUID checklistItemId = firstItem.checklistItemId();
            if (checklistSatisfied) {
                UUID evidence = UUID.randomUUID();
                SurgeryChecklistItem revisedItem = firstItem.revise(SurgeryChecklistStatus.SATISFIED, evidence, 1L);
                SurgeryChecklistSnapshot revised = snapshot.reviseItem(checklistItemId, 0, revisedItem);
                checklists.saveItemChange(revised, 0, new SurgeryChecklistItemChange(UUID.randomUUID(), checklistItemId,
                        1, SurgeryChecklistStatus.PENDING, SurgeryChecklistStatus.SATISFIED, evidence, 1L,
                        actor, NOW.minusSeconds(90), "race-seed"));
                checklistRevision = 1;
            }

            EnumMap<SurgeryConsentType, UUID> consentIds = new EnumMap<>(SurgeryConsentType.class);
            for (SurgeryConsentType type : SurgeryConsentType.values()) {
                SurgeryConsentRecord consent = SurgeryConsentRecord.sign(UUID.randomUUID(), caseId, type, patientId,
                        SurgeryConsentSignerType.PATIENT, UUID.randomUUID(), actor, NOW.minusSeconds(80), "race-seed");
                consents.save(consent);
                consentIds.put(type, consent.consentId());
            }
            UUID scheduleId = UUID.randomUUID();
            SurgerySchedule schedule = new SurgerySchedule(scheduleId, caseId, 1, roomId,
                    NOW.plusSeconds(300), NOW.plusSeconds(600),
                    List.of(new SurgeryTeamAssignment(staff, SurgeryTeamRole.PRIMARY_SURGEON)));
            schedules.saveDraft(schedule, 0, NOW.minusSeconds(70));

            UUID clearanceId = UUID.randomUUID();
            SurgeryFinancialClearance clearance = new SurgeryFinancialClearance(clearanceId, UUID.randomUUID(),
                    UUID.randomUUID(), patientId, caseId, CareEpisodeType.OUTPATIENT_VISIT,
                    value.getCareEpisode().episodeId(), null, BigDecimal.TEN, "VND", "CASH",
                    NOW.minusSeconds(60), NOW.plusSeconds(600), "a".repeat(64));
            clearances.saveIfAbsentAndMatching(clearance);
            Fixture fixture = new Fixture(caseId, requestId, patientId, departmentId, roomId, staff, scheduleId,
                    procedureCode, snapshot.checklistSnapshotId(), checklistItemId, checklistRevision,
                    Map.copyOf(consentIds), clearanceId, validUntil);
            fixtures.put(caseId, fixture);
            return fixture;
        });
    }

    private SurgeryReadinessEvidence readinessEvidence(SurgeryCase value, SurgerySchedule schedule) {
        Fixture fixture = fixtures.get(value.getSurgeryCaseId());
        Instant observed = lifecycleTestTime.get();
        List<SurgeryReadinessEvidence.Proof> proofs = Arrays.stream(SurgeryDependencyType.values()).map(type -> {
            UUID source = switch (type) {
                case INDICATION -> fixture.requestId();
                case CHECKLIST -> fixture.checklistSnapshotId();
                case SURGERY_CONSENT -> fixture.consents().get(SurgeryConsentType.SURGERY);
                case ANESTHESIA_CONSENT -> fixture.consents().get(SurgeryConsentType.ANESTHESIA);
                case TEAM_ELIGIBILITY -> UUID.fromString("30000000-0000-0000-0000-000000000001");
                case SCHEDULE -> schedule.scheduleId();
                case FINANCIAL_CLEARANCE -> fixture.clearanceId();
            };
            long revision = switch (type) {
                case CHECKLIST -> fixture.checklistRevision();
                case SCHEDULE -> schedule.revision();
                default -> 0;
            };
            return new SurgeryReadinessEvidence.Proof(type, source, revision,
                    SurgeryReadinessEvidence.Decision.SATISFIED, observed, NOW.minusSeconds(30), fixture.validUntil());
        }).toList();
        return new SurgeryReadinessEvidence(value.getSurgeryCaseId(), value.getPatientId(), value.getDepartmentId(),
                value.getCareEpisode(), value.getRevision(), schedule.scheduleId(), schedule.revision(), proofs);
    }

    private SurgeryLifecycleCommand lifecycleCommand(Fixture fixture, long expectedCaseRevision,
                                                     long expectedScheduleRevision, String key) {
        return new SurgeryLifecycleCommand(fixture.caseId(), expectedCaseRevision, expectedScheduleRevision,
                key, new SurgeryActorIdentity(ACCOUNT, fixture.staffId()), key);
    }

    private CancelSurgeryUseCase.Command cancelCommand(Fixture fixture, long expectedRevision, String key) {
        return new CancelSurgeryUseCase.Command(fixture.caseId(), expectedRevision, "race cancellation", key,
                new SurgeryActorIdentity(ACCOUNT, fixture.staffId()), key);
    }

    private PrepareSurgeryScheduleUseCase.Command prepareCommand(Fixture fixture, long expectedCaseRevision,
                                                                  long expectedScheduleRevision, Instant startsAt,
                                                                  Instant endsAt, String key) {
        return new PrepareSurgeryScheduleUseCase.Command(fixture.caseId(), expectedCaseRevision,
                expectedScheduleRevision, fixture.roomId(), startsAt, endsAt,
                List.of(new PrepareSurgeryScheduleUseCase.TeamMember(fixture.staffId(), SurgeryTeamRole.PRIMARY_SURGEON)),
                key, SurgeryAuditActor.human(ACCOUNT, fixture.staffId()), key);
    }

    private UpdateChecklistItemUseCase.Command failedChecklistCommand(Fixture fixture, long expectedRevision, String key) {
        return new UpdateChecklistItemUseCase.Command(fixture.caseId(), fixture.checklistItemId(), expectedRevision,
                fixture.checklistRevision(), fixture.checklistRevision(), SurgeryChecklistStatus.FAILED, null, null,
                key, SurgeryAuditActor.human(ACCOUNT, fixture.staffId()), key);
    }

    private ManageSurgeryConsentUseCase.RevokeCommand revokeCommand(Fixture fixture, long expectedRevision,
                                                                       UUID consentId, String key) {
        return new ManageSurgeryConsentUseCase.RevokeCommand(fixture.caseId(), consentId, expectedRevision,
                "race consent revocation", key, SurgeryAuditActor.human(ACCOUNT, fixture.staffId()), key);
    }

    private CompleteSurgeryUseCase.Command completionCommand(Fixture fixture, long expectedRevision, String key) {
        SurgeryLifecycleCommand identity = lifecycleCommand(fixture, expectedRevision, 1, key);
        return new CompleteSurgeryUseCase.Command(identity, fixture.procedureCode(), "TEST-METHOD", "TEST-OUTCOME", null,
                NOW.plusSeconds(10), NOW.plusSeconds(15),
                List.of(new SurgeryPerformedItem(UUID.randomUUID(), "ITEM-1", "PRICE-1", BigDecimal.ONE)));
    }

    private SurgeryCase currentCase(Fixture fixture) {
        return cases.findById(fixture.caseId()).orElseThrow();
    }

    private List<String> reservationStatuses(Fixture fixture) {
        return db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=? ORDER BY resource_type",
                String.class, fixture.caseId());
    }

    private long eventCount(String eventType) {
        Long count = db.queryForObject("SELECT count(*) FROM surgery_care_event_outbox WHERE event_type=?", Long.class, eventType);
        return count == null ? 0 : count;
    }

    private long count(String table) {
        Long count = db.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    private long countWhere(String table, String predicate) {
        Long count = db.queryForObject("SELECT count(*) FROM " + table + " WHERE " + predicate, Long.class);
        return count == null ? 0 : count;
    }

    private void assertCaseHistory(SurgeryCase value) {
        assertThat(countForCase("surgery_revision_history", value.getSurgeryCaseId()))
                .isEqualTo(value.getRevision() + 1);
        assertThat(countWhere("surgery_care_event_outbox", "delivery_status <> 'HELD'")).isZero();
        assertThat(countWhere("surgery_lifecycle_intent", "delivery_status <> 'HELD'")).isZero();
        List<Integer> revisions = db.queryForList(
                "SELECT revision FROM surgery_revision_history WHERE surgery_case_id=? ORDER BY revision",
                Integer.class, value.getSurgeryCaseId());
        assertThat(revisions).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(0,
                Math.toIntExact(value.getRevision())).boxed().toList());
    }

    private long countForCase(String table, UUID caseId) {
        Long count = db.queryForObject("SELECT count(*) FROM " + table + " WHERE surgery_case_id=?", Long.class, caseId);
        return count == null ? 0 : count;
    }

    private Instant latestRevisionTimestamp(Fixture fixture) {
        return db.queryForObject(
                "SELECT occurred_at FROM surgery_revision_history WHERE surgery_case_id=? ORDER BY revision DESC LIMIT 1",
                (rs, row) -> rs.getTimestamp(1).toInstant(), fixture.caseId());
    }

    private static void assertExactlyOneSuccess(List<? extends Attempt<?>> attempts) {
        assertThat(attempts).hasSize(2);
        assertThat(attempts.stream().filter(Attempt::succeeded)).hasSize(1);
        Attempt<?> loser = attempts.stream().filter(attempt -> !attempt.succeeded()).findFirst().orElseThrow();
        assertThat(loser.error()).isInstanceOfAny(SurgeryRevisionConflictException.class,
                SurgeryScheduleConflictException.class, SurgeryRuleException.class);
    }

    private static <T> List<Attempt<T>> race(Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt<T>> left = workers.submit(() -> invoke(start, first));
            Future<Attempt<T>> right = workers.submit(() -> invoke(start, second));
            return List.of(await(left), await(right));
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static <T> Attempt<T> invoke(CyclicBarrier start, Callable<T> operation) {
        try {
            start.await(10, TimeUnit.SECONDS);
            return new Attempt<>(operation.call(), null);
        } catch (Throwable failure) {
            return new Attempt<>(null, failure);
        }
    }

    private static <T> Attempt<T> await(Future<Attempt<T>> future) throws Exception {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            throw new AssertionError("Race worker crashed outside the captured operation", failure.getCause());
        } catch (TimeoutException failure) {
            throw new AssertionError("Race worker did not complete; possible lock leak", failure);
        }
    }

    private record Attempt<T>(T value, Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }

    private record Fixture(UUID caseId, UUID requestId, UUID patientId, UUID departmentId, UUID roomId,
                           UUID staffId, UUID scheduleId, String procedureCode, UUID checklistSnapshotId,
                           UUID checklistItemId, long checklistRevision, Map<SurgeryConsentType, UUID> consents,
                           UUID clearanceId, Instant validUntil) {
    }
}
