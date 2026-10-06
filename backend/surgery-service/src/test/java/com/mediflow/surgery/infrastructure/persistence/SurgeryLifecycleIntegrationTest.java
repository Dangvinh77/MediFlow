package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryLifecycleIntentPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessSnapshotPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryResultRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.service.SurgeryLifecycleApplicationService;
import com.mediflow.surgery.application.service.SurgeryReadinessEngine;
import com.mediflow.surgery.application.exception.SurgeryScheduleConflictException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Real Surgery-owned DB/transactions. Authority double is NOT shared-contract or clinical approval. */
@Testcontainers
@SpringBootTest(properties = {"mediflow.jwt.secret=surgery-lifecycle-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false","spring.cloud.discovery.enabled=false","mediflow.features.surgery.enabled=true"})
class SurgeryLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789);
    private static final UUID ACCOUNT = UUID.randomUUID();
    private final Map<UUID,Fixture> fixtures = new ConcurrentHashMap<>();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",PG::getJdbcUrl); registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
    }
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryChecklistRepositoryPort checklists;
    @Autowired SurgeryConsentRepositoryPort consents;
    @Autowired SurgeryFinancialClearanceRepositoryPort clearances;
    @Autowired SurgeryLifecycleApplicationService lifecycle;
    @Autowired SurgeryReadinessSnapshotPort snapshots;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate db;
    @Autowired AtomicReference<Instant> lifecycleTestTime;
    @MockBean SurgeryReadinessAuthorityPort authority;
    @MockBean FinancialClearanceLookupPort financialAuthority;
    @SpyBean SurgeryLifecycleIntentAdapter intents;

    @BeforeEach void resetIsolatedPrerequisites() {
        db.execute("TRUNCATE surgery_case CASCADE");
        fixtures.clear(); lifecycleTestTime.set(NOW);
        when(authority.observe(any(),any(),anyString())).thenAnswer(call -> evidence(call.getArgument(0),call.getArgument(1)));
        when(financialAuthority.observe(any(),anyString())).thenAnswer(call -> new FinancialClearanceLookupPort.Observation(
                true,lifecycleTestTime.get(),null));
    }

    @Test void start_billingRevokedClearance_commitsDenialAuditAndReleasesSchedule() {
        var fixture=seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(fixture,"financial-ready"));
        lifecycle.finalizeSchedule(identity(fixture,"financial-finalize"));
        when(financialAuthority.observe(any(),anyString())).thenAnswer(call -> new FinancialClearanceLookupPort.Observation(
                false,lifecycleTestTime.get(),lifecycleTestTime.get().plusSeconds(30)));
        var command=identity(fixture,"financial-start");
        assertThat(lifecycle.start(command).state()).isEqualTo("NOT_READY");
        assertThat(cases.findById(fixture.caseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(bookings(fixture)).containsExactlyInAnyOrder("RELEASED","RELEASED");
        assertThat(lifecycle.start(command).replayed()).isTrue();
    }
    @Test void evaluate_billingUnavailable_rollsBackReceiptAndNoSnapshotOrIntent() {
        var fixture=seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        when(financialAuthority.observe(any(),anyString())).thenThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("test unavailable"));
        assertThatThrownBy(()->lifecycle.evaluate(identity(fixture,"financial-outage")))
                .isInstanceOf(com.mediflow.surgery.application.exception.UpstreamUnavailableException.class);
        assertThat(count("surgery_command_receipt")).isZero();
        assertThat(count("surgery_readiness_snapshot")).isZero();
        assertThat(count("surgery_lifecycle_intent")).isZero();
    }
    @Test void start_afterThirtySeconds_rechecksFreshAuthorityWithoutInventingScheduleExpiry() {
        var fixture=seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(fixture,"fresh-ready"));
        lifecycle.finalizeSchedule(identity(fixture,"fresh-finalize"));
        lifecycleTestTime.set(NOW.plusSeconds(35));
        assertThat(lifecycle.start(identity(fixture,"fresh-start")).state()).isEqualTo("IN_PROGRESS");
        assertThat(bookings(fixture)).containsExactlyInAnyOrder("IN_USE","IN_USE");
    }
    @Test void lifecycle_readyFinalizeStartComplete_commitsOwnedDataAndOnlyHeldIntentWithNanosecondEvidence() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        var readyCommand = identity(fixture,"ready");
        assertThat(lifecycle.evaluate(readyCommand).state()).isEqualTo("READY");
        var ready = cases.findById(fixture.caseId()).orElseThrow();
        assertThat(ready.getReadinessSnapshot().evaluatedAt()).isEqualTo(lifecycleTestTime.get());
        assertThat(ready.getReadinessSnapshot().validUntil()).isEqualTo(NOW.plusSeconds(120).plusNanos(1));
        assertThat(lifecycle.evaluate(readyCommand).replayed()).isTrue();
        assertThat(lifecycle.finalizeSchedule(identity(fixture,"finalize")).state()).isEqualTo("SCHEDULED");
        assertThat(bookings(fixture)).containsExactlyInAnyOrder("RESERVED","RESERVED");
        assertThat(lifecycle.start(identity(fixture,"start")).state()).isEqualTo("IN_PROGRESS");
        assertThat(bookings(fixture)).containsExactlyInAnyOrder("IN_USE","IN_USE");
        lifecycleTestTime.set(NOW.plusSeconds(10));
        var command = completion(fixture,"complete");
        assertThat(lifecycle.complete(command).state()).isEqualTo("COMPLETED");
        assertThat(lifecycle.complete(command).replayed()).isTrue();
        assertThat(bookings(fixture)).containsExactlyInAnyOrder("RELEASED","RELEASED");
        assertThat(count("surgery_result")).isEqualTo(1); assertThat(count("surgery_performed_item")).isEqualTo(2);
        assertThat(count("surgery_lifecycle_intent")).isEqualTo(2); assertThat(count("surgery_command_receipt")).isEqualTo(4);
        assertThat(count("surgery_outbox")).isZero();
        assertThat(db.queryForList("SELECT delivery_status FROM surgery_lifecycle_intent",String.class)).containsOnly("HELD");
    }

    @Test void evaluate_failureAfterHeldInsert_rollsBackSnapshotPrecisionStateHistoryAndReceipt() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        int history = count("surgery_revision_history");
        doAnswer(call -> { call.callRealMethod(); throw new TestIntentFailure(); })
                .when(intents).holdReady(any(),any(),any(),anyString());
        var command = identity(fixture,"ready");
        assertThatThrownBy(() -> lifecycle.evaluate(command)).isInstanceOf(TestIntentFailure.class);
        assertThat(cases.findById(fixture.caseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(count("surgery_readiness_snapshot")).isZero(); assertThat(count("surgery_readiness_precision")).isZero();
        assertThat(count("surgery_lifecycle_intent")).isZero(); assertThat(count("surgery_command_receipt")).isZero();
        assertThat(count("surgery_revision_history")).isEqualTo(history);
        reset(intents);
        assertThat(lifecycle.evaluate(command).state()).isEqualTo("READY");
    }

    @Test void complete_failureAfterHeldInsert_rollsBackResultItemsReleaseOutcomeAndReceipt() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        start(fixture); lifecycleTestTime.set(NOW.plusSeconds(10));
        var command = completion(fixture,"complete");
        doAnswer(call -> { call.callRealMethod(); throw new TestIntentFailure(); })
                .when(intents).holdCompleted(any(),any(),any(),anyString());
        assertThatThrownBy(() -> lifecycle.complete(command)).isInstanceOf(TestIntentFailure.class);
        assertThat(cases.findById(fixture.caseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.IN_PROGRESS);
        assertThat(bookings(fixture)).containsOnly("IN_USE"); assertThat(count("surgery_result")).isZero();
        assertThat(count("surgery_performed_item")).isZero(); assertThat(count("surgery_lifecycle_intent")).isEqualTo(1);
        assertThat(count("surgery_command_receipt")).isEqualTo(3);
        reset(intents); assertThat(lifecycle.complete(command).state()).isEqualTo("COMPLETED");
    }

    @Test void finalize_twoWorkersSameRoom_onlyOneCommitsFullResourcesAndReceipt() throws Exception {
        UUID room = UUID.randomUUID();
        var first = seed(room,NOW.plusSeconds(300),NOW.plusSeconds(600));
        var second = seed(room,NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(first,"ready")); lifecycle.evaluate(identity(second,"ready"));
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
            var result = evidence(call.getArgument(0),call.getArgument(1)); barrier.await(3,TimeUnit.SECONDS); return result;
        }).when(authority).observe(any(),any(),anyString());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var commands = List.of(identity(first,"finalize"),identity(second,"finalize"));
            var outcomes = workers.invokeAll(commands.stream().<java.util.concurrent.Callable<String>>map(command -> () -> {
                try { return lifecycle.finalizeSchedule(command).state(); }
                catch (SurgeryScheduleConflictException denied) { return "CONFLICT"; }
            }).toList());
            assertThat(List.of(outcomes.get(0).get(10,TimeUnit.SECONDS),outcomes.get(1).get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SCHEDULED","CONFLICT");
        }
        assertThat(count("surgery_resource_reservation")).isEqualTo(2);
        assertThat(count("surgery_command_receipt")).isEqualTo(3);
    }

    @Test void start_previousCaseOverrun_blocksAlreadyReservedAdjacentCaseWithoutPartialInUse() {
        UUID room = UUID.randomUUID();
        var first = seed(room,NOW.plusSeconds(300),NOW.plusSeconds(600));
        var next = seed(room,NOW.plusSeconds(600),NOW.plusSeconds(900));
        lifecycle.evaluate(identity(first,"ready")); lifecycle.finalizeSchedule(identity(first,"finalize"));
        lifecycle.evaluate(identity(next,"ready")); lifecycle.finalizeSchedule(identity(next,"finalize"));
        lifecycle.start(identity(first,"start"));
        assertThatThrownBy(() -> lifecycle.start(identity(next,"start"))).isInstanceOf(SurgeryScheduleConflictException.class);
        assertThat(bookings(first)).containsOnly("IN_USE"); assertThat(bookings(next)).containsOnly("RESERVED");
        assertThat(cases.findById(next.caseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
    }

    @Test void start_forgedResourceWithSameBookingCount_rejectsExactSetMismatch() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(fixture,"ready")); lifecycle.finalizeSchedule(identity(fixture,"finalize"));
        UUID foreign = UUID.randomUUID();
        db.update("INSERT INTO surgery_resource_mutex(resource_type,resource_id) VALUES ('ROOM',?)",foreign);
        db.update("UPDATE surgery_resource_reservation SET resource_id=? WHERE surgery_case_id=? AND resource_type='ROOM'",foreign,fixture.caseId());
        assertThatThrownBy(() -> lifecycle.start(identity(fixture,"start"))).isInstanceOf(SurgeryScheduleConflictException.class);
        assertThat(bookings(fixture)).containsOnly("RESERVED");
    }

    @Test void start_revokedAnesthesiaAfterObservation_commitsDenialInvalidationAndExactRelease() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(fixture,"ready")); lifecycle.finalizeSchedule(identity(fixture,"finalize"));
        var command = identity(fixture,"start-denied");
        doAnswer(call -> {
            var observed = evidence(call.getArgument(0),call.getArgument(1));
            var consent = consents.findByCaseId(fixture.caseId()).stream().filter(item -> item.consentType() == SurgeryConsentType.ANESTHESIA).findFirst().orElseThrow();
            consents.save(consent.revoke(SurgeryAuditActor.human(ACCOUNT,fixture.staff()),lifecycleTestTime.get(),"local-pg","withdrawn"));
            return observed;
        }).when(authority).observe(any(),any(),anyString());
        var denied = lifecycle.start(command);
        assertThat(denied.state()).isEqualTo("NOT_READY");
        assertThat(cases.findById(fixture.caseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(bookings(fixture)).containsOnly("RELEASED");
        assertThat(lifecycle.start(command).replayed()).isTrue();
        assertThat(count("surgery_command_receipt")).isEqualTo(3);
        assertThat(count("surgery_lifecycle_intent")).isEqualTo(1);
    }

    @Test void start_exactGrantExpiry_commitsDenialWithoutStartOrInUse() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(fixture,"ready")); lifecycle.finalizeSchedule(identity(fixture,"finalize"));
        var command = identity(fixture,"expired-start");
        lifecycleTestTime.set(NOW.plusSeconds(120).plusNanos(1));
        assertThat(lifecycle.start(command).state()).isEqualTo("READINESS_EXPIRED");
        var value = cases.findById(fixture.caseId()).orElseThrow();
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(bookings(fixture)).containsOnly("RELEASED");
        assertThat(count("surgery_result")).isZero();
        assertThat(lifecycle.start(command).replayed()).isTrue();
    }

    @Test void finalize_changedAuthorityRevision_commitsDenialAndDoesNotReserve() {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        lifecycle.evaluate(identity(fixture,"ready"));
        var command = identity(fixture,"changed-finalize");
        doAnswer(call -> {
            var observed = evidence(call.getArgument(0),call.getArgument(1));
            var changed = observed.proofs().stream().map(proof -> proof.type() == SurgeryDependencyType.TEAM_ELIGIBILITY
                    ? new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision()+1,proof.decision(),proof.observedAt(),proof.validFrom(),proof.validUntil())
                    : proof).toList();
            return new SurgeryReadinessEvidence(observed.surgeryCaseId(),observed.patientId(),observed.departmentId(),observed.episode(),
                    observed.caseRevision(),observed.scheduleId(),observed.scheduleRevision(),changed);
        }).when(authority).observe(any(),any(),anyString());
        assertThat(lifecycle.finalizeSchedule(command).state()).isEqualTo("READINESS_CHANGED");
        assertThat(cases.findById(fixture.caseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(bookings(fixture)).isEmpty();
        assertThat(lifecycle.finalizeSchedule(command).replayed()).isTrue();
    }

    @Test void complete_sameIntentConcurrentDelivery_hasOneResultOneHeldOutcomeAndNoBookings() throws Exception {
        var fixture = seed(UUID.randomUUID(),NOW.plusSeconds(300),NOW.plusSeconds(600));
        start(fixture); lifecycleTestTime.set(NOW.plusSeconds(10));
        var command = completion(fixture,"complete"); var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { barrier.await(); return lifecycle.complete(command); });
            var second = workers.submit(() -> { barrier.await(); return lifecycle.complete(command); });
            assertThat(List.of(first.get(10,TimeUnit.SECONDS).replayed(),second.get(10,TimeUnit.SECONDS).replayed())).containsExactlyInAnyOrder(false,true);
        }
        assertThat(count("surgery_result")).isEqualTo(1); assertThat(count("surgery_lifecycle_intent")).isEqualTo(2);
        assertThat(bookings(fixture)).containsOnly("RELEASED");
    }

    private void start(Fixture fixture) {
        lifecycle.evaluate(identity(fixture,"ready")); lifecycle.finalizeSchedule(identity(fixture,"finalize")); lifecycle.start(identity(fixture,"start"));
    }
    private Fixture seed(UUID room,Instant starts,Instant ends) {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            UUID staff = UUID.randomUUID(); var actor = SurgeryAuditActor.human(ACCOUNT,staff);
            String procedure = "TEST-"+UUID.randomUUID().toString().substring(0,8);
            var value = SurgeryCase.create(UUID.randomUUID(),UUID.randomUUID(),new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,UUID.randomUUID(),null,UUID.randomUUID()),
                    UUID.randomUUID(),UUID.randomUUID(),staff,procedure,"Isolated test-only clinical prerequisite",SurgeryPriority.ROUTINE,NOW.minusSeconds(100),actor,"local-pg");
            cases.save(value,-1); value.beginPreop(actor,"local-pg",NOW.minusSeconds(90)); cases.save(value,0);
            var template = new SurgeryChecklistTemplate(UUID.randomUUID(),procedure,1,List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(),"TEST-ONLY",true,1)));
            checklists.createTemplate(template,NOW.minusSeconds(95));
            var checklist = template.snapshotForCase(UUID.randomUUID(),value.getSurgeryCaseId()); checklists.createSnapshot(checklist);
            var before = checklist.items().getFirst(); var after = before.revise(SurgeryChecklistStatus.SATISFIED,UUID.randomUUID(),1L);
            checklist = checklist.reviseItem(before.checklistItemId(),0,after);
            checklists.saveItemChange(checklist,0,new SurgeryChecklistItemChange(UUID.randomUUID(),before.checklistItemId(),1,SurgeryChecklistStatus.PENDING,
                    SurgeryChecklistStatus.SATISFIED,after.evidenceReferenceId(),1L,actor,NOW.minusSeconds(80),"local-pg"));
            var consentIds = new java.util.EnumMap<SurgeryConsentType,UUID>(SurgeryConsentType.class);
            for (var type : SurgeryConsentType.values()) {
                var consent = SurgeryConsentRecord.sign(UUID.randomUUID(),value.getSurgeryCaseId(),type,value.getPatientId(),SurgeryConsentSignerType.PATIENT,
                        UUID.randomUUID(),actor,NOW.minusSeconds(70),"local-pg"); consents.save(consent); consentIds.put(type,consent.consentId());
            }
            var schedule = new SurgerySchedule(UUID.randomUUID(),value.getSurgeryCaseId(),1,room,starts,ends,List.of(new SurgeryTeamAssignment(staff,SurgeryTeamRole.PRIMARY_SURGEON)));
            schedules.saveDraft(schedule,0,NOW.minusSeconds(60));
            var grant = new SurgeryFinancialClearance(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),value.getPatientId(),value.getSurgeryCaseId(),value.getCareEpisode().type(),
                    value.getCareEpisode().episodeId(),null,BigDecimal.TEN,"VND","CASH",NOW.minusSeconds(50),NOW.plusSeconds(120).plusNanos(1),"a".repeat(64));
            clearances.saveIfAbsentAndMatching(grant);
            var fixture = new Fixture(value.getSurgeryCaseId(),staff,checklist.checklistSnapshotId(),grant.clearanceId(),Map.copyOf(consentIds),UUID.randomUUID());
            fixtures.put(fixture.caseId(),fixture); return fixture;
        });
    }
    private SurgeryReadinessEvidence evidence(SurgeryCase value,SurgerySchedule schedule) {
        var fixture = fixtures.get(value.getSurgeryCaseId()); Instant at = lifecycleTestTime.get();
        var proofs = Arrays.stream(SurgeryDependencyType.values()).map(type -> {
            UUID source = switch (type) {
                case INDICATION -> value.getSurgeryRequestId(); case CHECKLIST -> fixture.checklistId();
                case SURGERY_CONSENT -> fixture.consents().get(SurgeryConsentType.SURGERY);
                case ANESTHESIA_CONSENT -> fixture.consents().get(SurgeryConsentType.ANESTHESIA);
                case TEAM_ELIGIBILITY -> fixture.teamPolicyId(); case SCHEDULE -> schedule.scheduleId(); case FINANCIAL_CLEARANCE -> fixture.grantId();
            };
            return new SurgeryReadinessEvidence.Proof(type,source,type == SurgeryDependencyType.SCHEDULE || type == SurgeryDependencyType.CHECKLIST ? 1 : 0,
                    SurgeryReadinessEvidence.Decision.SATISFIED,at,NOW.minusSeconds(60),NOW.plusSeconds(200));
        }).toList();
        return new SurgeryReadinessEvidence(value.getSurgeryCaseId(),value.getPatientId(),value.getDepartmentId(),value.getCareEpisode(),value.getRevision(),schedule.scheduleId(),1,proofs);
    }
    private SurgeryLifecycleCommand identity(Fixture fixture,String key) {
        // Persisted history has microsecond precision; distinct accepted commands advance server time.
        lifecycleTestTime.updateAndGet(at -> at.plusMillis(1));
        return new SurgeryLifecycleCommand(fixture.caseId(),cases.findById(fixture.caseId()).orElseThrow().getRevision(),1,key,
                new SurgeryActorIdentity(ACCOUNT,fixture.staff()),"local-pg");
    }
    private CompleteSurgeryUseCase.Command completion(Fixture fixture,String key) {
        return new CompleteSurgeryUseCase.Command(identity(fixture,key),"TEST-ACTUAL","TEST-METHOD","TEST-OUTCOME",null,NOW,NOW.plusSeconds(5),
                List.of(new SurgeryPerformedItem(UUID.randomUUID(),"TEST-ITEM-1","TEST-PRICE",BigDecimal.ONE),
                        new SurgeryPerformedItem(UUID.randomUUID(),"TEST-ITEM-2","TEST-PRICE",BigDecimal.valueOf(2))));
    }
    private List<String> bookings(Fixture fixture) { return db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",String.class,fixture.caseId()); }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    private record Fixture(UUID caseId,UUID staff,UUID checklistId,UUID grantId,Map<SurgeryConsentType,UUID> consents,UUID teamPolicyId) {}
    private static final class TestIntentFailure extends RuntimeException {}

    @TestConfiguration(proxyBeanMethods = false) static class LocalOnlyWiring {
        @Bean AtomicReference<Instant> lifecycleTestTime() { return new AtomicReference<>(NOW); }
        @Bean @Primary SurgeryClockPort lifecycleClock(AtomicReference<Instant> lifecycleTestTime) { return lifecycleTestTime::get; }
        @Bean SurgeryLifecycleApplicationService lifecycle(SurgeryCaseRepositoryPort cases,SurgeryScheduleRepositoryPort schedules,
                SurgeryCommandReceiptPort receipts,SurgeryReadinessSnapshotPort snapshots,SurgeryChecklistRepositoryPort checklists,
                SurgeryConsentRepositoryPort consents,SurgeryFinancialClearanceRepositoryPort clearances,SurgeryResourceReservationPort resources,
                SurgeryResultRepositoryPort results,SurgeryReadinessAuthorityPort authority,SurgeryLifecycleIntentPort intents,SurgeryClockPort clock,
                FinancialClearanceLookupPort financialAuthority) {
            return new SurgeryLifecycleApplicationService(cases,schedules,receipts,snapshots,checklists,consents,clearances,resources,results,authority,intents,clock,
                    new SurgeryReadinessEngine(Duration.ofSeconds(30),Duration.ofSeconds(5)),financialAuthority);
        }
    }
}
