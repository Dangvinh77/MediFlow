package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.application.service.SurgeryLifecycleApplicationService;
import com.mediflow.surgery.application.service.SurgeryReadinessEngine;
import com.mediflow.surgery.domain.model.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actual owned HTTP-to-PostgreSQL workflow. Only source authority/Clock are test doubles.
 * No upstream referral, approved clinical policy, Gateway or live outbound delivery is certified.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "mediflow.features.surgery.enabled=true", "mediflow.surgery.creation.api.enabled=true",
        "mediflow.surgery.preop.api.enabled=true", "mediflow.surgery.lifecycle.api.enabled=true",
        "mediflow.jwt.secret=workflow-http-pg-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SurgeryHttpWorkflowPostgresTest {
    private static final String SECRET = "workflow-http-pg-test-secret-at-least-32-bytes";
    private static final String PATH = "/api/v1/surgery/cases";
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", PG::getJdbcUrl);
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryChecklistRepositoryPort checklists;
    @Autowired SurgeryConsentRepositoryPort consents;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryFinancialClearanceRepositoryPort clearances;
    @Autowired SurgeryUnitOfWorkPort transactions;
    @MockBean SurgeryCreationAuthorityPort creationAuthority;
    @MockBean SurgeryPreopAuthorityPort preopAuthority;
    @MockBean SurgeryReadinessAuthorityPort readinessAuthority;
    @MockBean FinancialClearanceLookupPort financialAuthority;
    @MockBean OrganizationLookupPort organization;
    @MockBean AdmissionLookupPort admissions;
    @MockBean SurgeryClockPort clock;

    private final AtomicReference<Instant> now = new AtomicReference<>();
    private final AtomicBoolean financiallyEligible = new AtomicBoolean(true);
    private final List<Step> accepted = new ArrayList<>();
    private final List<JsonNode> outcomes = new ArrayList<>();
    private final List<String> locations = new ArrayList<>();
    private Instant base;
    private UUID account;
    private UUID staff;
    private UUID request;
    private UUID patient;
    private UUID department;
    private UUID episode;
    private UUID record;
    private UUID caseId;
    private UUID grantId;
    private UUID room;
    private UUID teamPolicy;
    private SurgeryChecklistTemplate template;
    private EpisodeScenario scenario;

    @BeforeEach void prepare() {
        database.execute("TRUNCATE surgery_case, surgery_creation_receipt CASCADE");
        database.execute("TRUNCATE preop_checklist_template CASCADE");
        base = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        now.set(base); financiallyEligible.set(true); accepted.clear(); outcomes.clear(); locations.clear();
        account = UUID.randomUUID(); staff = UUID.randomUUID(); request = UUID.randomUUID();
        patient = UUID.randomUUID(); department = UUID.randomUUID(); episode = UUID.randomUUID();
        record = UUID.randomUUID(); room = UUID.randomUUID(); teamPolicy = UUID.randomUUID();
        caseId = null; grantId = null;
        template = new SurgeryChecklistTemplate(UUID.randomUUID(), "TEST_" + UUID.randomUUID(), 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "TEST_ONLY_CHECK", true, 1)));
        transactions.write(() -> { checklists.createTemplate(template, base.minusSeconds(1)); return null; });
        when(clock.now()).thenAnswer(ignored -> now.get());
        configureSourceDoubles();
    }

    @ParameterizedTest @EnumSource(EpisodeScenario.class)
    void workflow_createThroughComplete_preservesExactEpisodeAndActualItems(EpisodeScenario selected) throws Exception {
        create(selected); preop(); satisfyChecklist(); consent(SurgeryConsentType.SURGERY);
        consent(SurgeryConsentType.ANESTHESIA); financialPrerequisite(); draft(); ready(); finalizeSchedule(); start();
        complete();
        var stored = current();
        assertThat(stored.getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(stored.getCareEpisode().episodeId()).isEqualTo(episode);
        assertThat(stored.getCareEpisode().recordId()).isEqualTo(record);
        assertThat(stored.getCareEpisode().admissionId()).isEqualTo(selected == EpisodeScenario.ADMISSION ? episode : null);
        assertThat(count("surgery_result")).isOne();
        assertThat(count("surgery_performed_item")).isEqualTo(2);
        assertThat(bookings()).hasSize(2).containsOnly("RELEASED");
        assertThat(eventTypes()).containsExactlyInAnyOrder("surgery.case.created", "surgery.ready", "surgery.completed");
        JsonNode payload = eventPayload("surgery.completed");
        assertThat(payload.path("careEpisodeId").asText()).isEqualTo(episode.toString());
        assertThat(payload.path("recordId").asText()).isEqualTo(record.toString());
        assertThat(payload.path("performedItems")).hasSize(2);
        assertThat(payload.path("performedItems").findValuesAsText("priceCode")).containsOnly("TEST_PRICE");
        assertThat(payload.path("performedItems").findValuesAsText("performedItemId")).doesNotHaveDuplicates();
        assertHeldOnly();
        replayAll();
        assertThat(count("surgery_result")).isOne();
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
    }

    @ParameterizedTest @EnumSource(value = SurgeryStatus.class,
            names = {"REQUESTED", "PREOP_IN_PROGRESS", "READY", "SCHEDULED"})
    void cancel_eachPrestartState_derivesStagePreservesGrantAndReplaysWithoutReopen(SurgeryStatus stage) throws Exception {
        create(EpisodeScenario.ADMISSION);
        if (stage != SurgeryStatus.REQUESTED) preop();
        if (stage == SurgeryStatus.READY || stage == SurgeryStatus.SCHEDULED) {
            prepareEligible(); ready();
            if (stage == SurgeryStatus.SCHEDULED) finalizeSchedule();
        }
        long before = current().getRevision();
        execute(new Step("POST", "/cancel", "cancel", body(Map.of("expectedCaseRevision", before,
                "reason", "Test-only patient cancellation")), 200, 200));
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(current().getReadinessSnapshot()).isNull();
        assertThat(bookings()).allMatch("RELEASED"::equals);
        String expectedStage = stage == SurgeryStatus.REQUESTED ? "BEFORE_PREOP"
                : stage == SurgeryStatus.SCHEDULED ? "BEFORE_START" : "AFTER_PREOP";
        assertThat(eventPayload("surgery.cancelled").path("cancellationStage").asText()).isEqualTo(expectedStage);
        assertThat(eventPayload("surgery.cancelled").path("cancelledBy").asText()).isEqualTo(account.toString());
        assertThat(eventPayload("surgery.cancelled").path("cancelledByStaffId").asText()).isEqualTo(staff.toString());
        if (grantId != null) assertThat(clearances.findById(grantId)).isPresent();
        replayAll();
        assertThat(current().getRevision()).isEqualTo(before + 1);
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(countEvent("surgery.cancelled")).isOne();
        assertHeldOnly();
    }

    @Test void completion_changedPayloadSameKey_conflictsAndCannotCorrectTerminalResult() throws Exception {
        throughStart();
        Step command = completionCommand("complete-once");
        execute(command);
        JsonNode before = eventPayload("surgery.completed");
        send(new Step("POST", "/complete", command.key(), command.body().replace("TEST_METHOD", "CHANGED_METHOD"), 200, 200))
                .andExpect(status().isConflict());
        assertThat(count("surgery_result")).isOne();
        assertThat(count("surgery_performed_item")).isEqualTo(2);
        assertThat(eventPayload("surgery.completed")).isEqualTo(before);
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        send(new Step("POST", "/cancel", "cancel-after-complete", body(Map.of("expectedCaseRevision", current().getRevision(),
                "reason", "Must reject")), 200, 200)).andExpect(status().isConflict());
        assertThat(countEvent("surgery.cancelled")).isZero();
    }

    @Test void checklist_scheduledCorrection_releasesOldBookingAndRequiresFreshReadiness() throws Exception {
        create(EpisodeScenario.APPOINTMENT); preop(); prepareEligible(); ready(); finalizeSchedule();
        UUID oldSnapshot = current().getReadinessSnapshot().snapshotId();
        checklist(SurgeryChecklistStatus.FAILED, "checklist-correction");
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(current().getReadinessSnapshot()).isNull();
        assertThat(bookings()).hasSize(2).containsOnly("RELEASED");
        assertThat(eventPayload("surgery.readiness.invalidated").path("readinessSnapshotId").asText()).isEqualTo(oldSnapshot.toString());
        send(lifecycleCommand("/start", "stale-start")).andExpect(status().isUnprocessableEntity());
        checklist(SurgeryChecklistStatus.SATISFIED, "checklist-restored");
        draft(); // RELEASED history cannot be re-finalized: prepare a new revision first.
        ready();
        assertThat(current().getReadinessSnapshot().snapshotId()).isNotEqualTo(oldSnapshot);
        finalizeSchedule(); start(); complete();
        assertThat(countEvent("surgery.ready")).isEqualTo(2);
        assertThat(countEvent("surgery.readiness.invalidated")).isOne();
        assertThat(count("surgery_result")).isOne();
        assertHeldOnly();
    }

    @ParameterizedTest @ValueSource(strings={"0.00001","1000000000000000"})
    void complete_unrepresentableQuantity_returns400WithoutResultOrOutbox(String quantity) throws Exception {
        throughStart();
        Step command = completionCommand("quantity-rejected");
        long revision = current().getRevision();
        int receipts = count("surgery_command_receipt"), history = count("surgery_revision_history");
        Step invalid = new Step(command.method(), command.path(), command.key(),
                command.body().replace("\"quantity\":1", "\"quantity\":" + quantity), 200, 200);
        send(invalid).andExpect(status().isBadRequest());
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.IN_PROGRESS);
        assertThat(current().getRevision()).isEqualTo(revision);
        assertThat(count("surgery_result")).isZero();
        assertThat(countEvent("surgery.completed")).isZero();
        assertThat(count("surgery_command_receipt")).isEqualTo(receipts);
        assertThat(count("surgery_revision_history")).isEqualTo(history);
        assertThat(bookings()).hasSize(2).containsOnly("IN_USE");
        execute(command);
        assertThat(countEvent("surgery.completed")).isOne();
        assertHeldOnly();
    }

    @Test void start_currentBillingDenial_stableReplayAndFreshRecoveryCannotBypassFinance() throws Exception {
        create(EpisodeScenario.ADMISSION); preop(); prepareEligible(); ready(); finalizeSchedule();
        financiallyEligible.set(false);
        Step deniedCommand = lifecycleCommand("/start", "denied-start");
        JsonNode denied = execute(deniedCommand);
        assertThat(denied.path("state").asText()).isEqualTo("NOT_READY");
        assertThat(denied.path("blockingReasons").toString()).contains("FINANCIAL");
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(bookings()).containsOnly("RELEASED");
        financiallyEligible.set(true);
        JsonNode replay = response(send(deniedCommand).andExpect(status().isOk()));
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("state")).isEqualTo(denied.path("state"));
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        draft(); ready(); finalizeSchedule(); start(); complete();
        assertThat(countEvent("surgery.completed")).isOne();
        assertThat(countEvent("surgery.readiness.invalidated")).isOne();
        assertHeldOnly();
    }

    @Test void complete_heldCaptureFailure_rollsBackEverythingThenSameHttpKeyRecovers() throws Exception {
        throughStart();
        Step command = completionCommand("complete-fault");
        long revision = current().getRevision();
        int history = count("surgery_revision_history"), receipts = count("surgery_command_receipt");
        database.execute("""
                CREATE FUNCTION fail_workflow_completed() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.event_type='surgery.completed' THEN RAISE EXCEPTION 'private test fault'; END IF; RETURN NEW; END $$
                """);
        database.execute("CREATE TRIGGER fail_workflow_completed BEFORE INSERT ON surgery_care_event_outbox FOR EACH ROW EXECUTE FUNCTION fail_workflow_completed()");
        try {
            var failed = send(command).andExpect(status().isInternalServerError()).andReturn();
            assertThat(failed.getResponse().getContentAsString()).doesNotContain("private test fault", "Test-only indication");
            assertThat(current().getStatus()).isEqualTo(SurgeryStatus.IN_PROGRESS);
            assertThat(current().getRevision()).isEqualTo(revision);
            assertThat(bookings()).hasSize(2).containsOnly("IN_USE");
            assertThat(count("surgery_result")).isZero();
            assertThat(count("surgery_performed_item")).isZero();
            assertThat(count("surgery_revision_history")).isEqualTo(history);
            assertThat(count("surgery_command_receipt")).isEqualTo(receipts);
            assertThat(countEvent("surgery.completed")).isZero();
            assertNoPendingReceipts();
        } finally {
            database.execute("DROP TRIGGER fail_workflow_completed ON surgery_care_event_outbox");
            database.execute("DROP FUNCTION fail_workflow_completed()");
        }
        assertThat(execute(command).path("state").asText()).isEqualTo("COMPLETED");
        replayAll();
        assertThat(count("surgery_result")).isOne();
        assertThat(countEvent("surgery.completed")).isOne();
    }

    @Test void readiness_missingAnesthesiaConsent_denialReplayIsNotNewAuthorization() throws Exception {
        create(EpisodeScenario.WALK_IN); preop(); satisfyChecklist(); consent(SurgeryConsentType.SURGERY);
        financialPrerequisite(); draft();
        Step command = lifecycleCommand("/readiness/evaluate", "missing-consent");
        JsonNode denied = execute(command);
        assertThat(denied.path("state").asText()).isEqualTo("NOT_READY");
        assertThat(denied.path("blockingReasons").toString()).contains("ANESTHESIA_CONSENT_MISSING");
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(countEvent("surgery.ready")).isZero();
        consent(SurgeryConsentType.ANESTHESIA);
        JsonNode replay = response(send(command).andExpect(status().isOk()));
        assertThat(replay.path("state")).isEqualTo(denied.path("state"));
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        ready(); finalizeSchedule(); start(); complete();
        assertThat(countEvent("surgery.ready")).isOne();
        assertHeldOnly();
    }

    private void configureSourceDoubles() {
        doAnswer(call -> { outsideTransaction(); return null; }).when(creationAuthority).authorize(any());
        when(creationAuthority.observe(any(), anyString())).thenAnswer(call -> {
            outsideTransaction();
            return new SurgeryCreationAuthorityPort.Approval(call.getArgument(1), template.templateId(), 1, now.get(), now.get().plusSeconds(30));
        });
        doAnswer(call -> {
            outsideTransaction();
            SurgeryPreopAuthorityPort.Context context = call.getArgument(0);
            assertThat(context.surgeryCaseId()).isEqualTo(caseId);
            assertThat(context.patientId()).isEqualTo(patient);
            return new SurgeryPreopAuthorityPort.Approval(call.getArgument(2), now.get(), now.get().plusSeconds(30));
        }).when(preopAuthority).approveChecklist(any(), any(), anyString());
        doAnswer(call -> {
            outsideTransaction();
            SurgeryPreopAuthorityPort.Context context = call.getArgument(0);
            assertThat(context.surgeryCaseId()).isEqualTo(caseId);
            assertThat(context.patientId()).isEqualTo(patient);
            return new SurgeryPreopAuthorityPort.Approval(call.getArgument(2), now.get(), now.get().plusSeconds(30));
        }).when(preopAuthority).approveConsent(any(), any(), anyString());
        doAnswer(call -> {
            outsideTransaction();
            assertThat(call.getArgument(2, UUID.class)).isEqualTo(caseId);
            return null;
        }).when(readinessAuthority).authorize(any(), anyString(), any());
        when(readinessAuthority.observe(any(), any(), anyString())).thenAnswer(call -> {
            outsideTransaction(); return evidence(call.getArgument(0), call.getArgument(1));
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(readinessAuthority).reconcile(any(), any(), any());
        doAnswer(call -> { outsideTransaction(); return null; }).when(readinessAuthority).verifyResult(any(), any(), anyString());
        when(financialAuthority.observe(any(), anyString())).thenAnswer(call -> {
            outsideTransaction();
            SurgeryFinancialClearance grant = call.getArgument(0);
            assertThat(grant.clearanceId()).isEqualTo(grantId);
            assertThat(grant.matches(current())).isTrue();
            return new FinancialClearanceLookupPort.Observation(financiallyEligible.get(), now.get(), base.plusSeconds(3600));
        });
        when(organization.findDepartment(any(), anyString())).thenAnswer(call ->
                new OrganizationLookupPort.OrganizationLookupSnapshot(OrganizationLookupPort.ReferenceKind.DEPARTMENT,
                        call.getArgument(0), OrganizationLookupPort.ReferenceState.ACTIVE, now.get(), "1", null));
        when(organization.findRoom(any(), anyString())).thenAnswer(call ->
                new OrganizationLookupPort.OrganizationLookupSnapshot(OrganizationLookupPort.ReferenceKind.ROOM,
                        call.getArgument(0), OrganizationLookupPort.ReferenceState.ACTIVE, now.get(), "1", null, null, department));
        when(organization.findSurgicalEligibility(any(), any(), any(), any(), anyString())).thenAnswer(call ->
                new OrganizationLookupPort.SurgicalEligibilitySnapshot(call.getArgument(0), call.getArgument(1),
                        OrganizationLookupPort.ReferenceState.ACTIVE, department, now.get(), "1", call.getArgument(2), call.getArgument(3)));
        when(admissions.findAdmission(any(), anyString())).thenAnswer(call ->
                new AdmissionLookupPort.AdmissionSnapshot(call.getArgument(0), patient, department, record, "ADMITTED",
                        OrganizationLookupPort.ReferenceState.ACTIVE, "1", now.get()));
    }

    private SurgeryReadinessEvidence evidence(SurgeryCase value, SurgerySchedule schedule) {
        var checklist = checklists.findSnapshotByCaseId(caseId).orElseThrow();
        var recorded = consents.findByCaseId(caseId);
        var proofs = Arrays.stream(SurgeryDependencyType.values()).map(type -> {
            UUID source; long revision = 0; boolean satisfied = true;
            switch (type) {
                case INDICATION -> source = request;
                case CHECKLIST -> { source = checklist.checklistSnapshotId(); revision = checklist.revision(); satisfied = checklist.mandatoryChecklistComplete(); }
                case SURGERY_CONSENT, ANESTHESIA_CONSENT -> {
                    SurgeryConsentType consentType = type == SurgeryDependencyType.SURGERY_CONSENT ? SurgeryConsentType.SURGERY : SurgeryConsentType.ANESTHESIA;
                    var consent = recorded.stream().filter(item -> item.consentType() == consentType && item.isActive()).findFirst().orElse(null);
                    source = consent == null ? teamPolicy : consent.consentId();
                    revision = consent == null ? 0 : consent.auditHistory().size() - 1;
                    satisfied = consent != null;
                }
                case TEAM_ELIGIBILITY -> source = teamPolicy;
                case SCHEDULE -> { source = schedule.scheduleId(); revision = schedule.revision(); }
                case FINANCIAL_CLEARANCE -> source = grantId;
                default -> throw new IllegalStateException("Uncovered guard");
            }
            return new SurgeryReadinessEvidence.Proof(type, source, revision, satisfied ? SurgeryReadinessEvidence.Decision.SATISFIED
                    : SurgeryReadinessEvidence.Decision.UNSATISFIED, now.get(), base.minusSeconds(1), base.plusSeconds(3600));
        }).toList();
        return new SurgeryReadinessEvidence(caseId, patient, department, value.getCareEpisode(), value.getRevision(),
                schedule.scheduleId(), schedule.revision(), proofs);
    }

    private void create(EpisodeScenario selected) throws Exception {
        scenario = selected;
        if (selected == EpisodeScenario.WALK_IN) episode = record;
        var body = new java.util.HashMap<String, Object>();
        body.put("surgeryRequestId", request); body.put("careEpisodeType", selected == EpisodeScenario.ADMISSION ? "ADMISSION" : "OUTPATIENT_VISIT");
        body.put("careEpisodeId", episode); body.put("recordId", record);
        if (selected == EpisodeScenario.ADMISSION) body.put("admissionId", episode);
        body.put("patientId", patient); body.put("departmentId", department); body.put("requestedBy", staff);
        body.put("procedureCode", template.procedureCode()); body.put("indication", "Test-only indication");
        body.put("priority", "ROUTINE"); body.put("requestedAt", base.minusSeconds(1)); body.put("templateRevision", 1);
        body.put("plannedItems", List.of(Map.of("itemCode", "TEST_PLANNED", "priceCode", "TEST_PRICE", "quantity", BigDecimal.ONE)));
        JsonNode result = execute(new Step("POST", "", request.toString(), body(body), 201, 200));
        caseId = UUID.fromString(result.path("surgeryCaseId").asText());
        assertThat(count("surgery_case")).isOne();
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.REQUESTED);
    }

    private void preop() throws Exception {
        execute(new Step("POST", "/preop", "begin-preop", body(Map.of("expectedCaseRevision", current().getRevision())), 200, 200));
    }
    private void satisfyChecklist() throws Exception { checklist(SurgeryChecklistStatus.SATISFIED, "initial-checklist"); }
    private void checklist(SurgeryChecklistStatus state, String key) throws Exception {
        var snapshot = checklists.findSnapshotByCaseId(caseId).orElseThrow();
        var item = snapshot.items().getFirst();
        execute(new Step("PUT", "/checklist", key, body(Map.of("checklistItemId", item.checklistItemId(),
                "expectedCaseRevision", current().getRevision(), "expectedSnapshotRevision", snapshot.revision(),
                "expectedItemRevision", item.revision(), "status", state.name(), "evidenceReferenceId", UUID.randomUUID(),
                "evidenceRevision", 1)), 200, 200));
    }
    private void consent(SurgeryConsentType type) throws Exception {
        execute(new Step("POST", "/consents", "consent-" + type, body(Map.of("expectedCaseRevision", current().getRevision(),
                "consentType", type.name(), "signerId", patient, "signerType", "PATIENT", "evidenceDocumentId", UUID.randomUUID())), 201, 200));
    }
    private void financialPrerequisite() {
        grantId = UUID.randomUUID();
        var grant = new SurgeryFinancialClearance(grantId, UUID.randomUUID(), UUID.randomUUID(), patient, caseId,
                current().getCareEpisode().type(), episode, scenario == EpisodeScenario.ADMISSION ? episode : null,
                BigDecimal.TEN, "VND", "CASH", base.minusSeconds(1), base.plusSeconds(3600), "a".repeat(64));
        transactions.write(() -> { assertThat(clearances.saveIfAbsentAndMatching(grant)).isEqualTo(SurgeryFinancialClearanceRepositoryPort.SaveDecision.CREATED); return null; });
    }
    private void draft() throws Exception {
        long revision = schedules.findByCaseId(caseId).map(SurgerySchedule::revision).orElse(0L);
        execute(new Step("PUT", "/schedule", "draft-" + current().getRevision(), body(Map.of("expectedCaseRevision", current().getRevision(),
                "expectedScheduleRevision", revision, "roomId", room, "startsAt", base.plusSeconds(300), "endsAt", base.plusSeconds(600),
                "team", List.of(Map.of("staffId", staff, "role", "PRIMARY_SURGEON")))), 200, 200));
        assertThat(bookings().stream().filter(value -> !value.equals("RELEASED"))).isEmpty();
        assertThat(current().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
    }
    private void prepareEligible() throws Exception {
        satisfyChecklist(); consent(SurgeryConsentType.SURGERY); consent(SurgeryConsentType.ANESTHESIA); financialPrerequisite(); draft();
    }
    private void ready() throws Exception {
        assertThat(execute(lifecycleCommand("/readiness/evaluate", "ready-" + current().getRevision())).path("state").asText()).isEqualTo("READY");
    }
    private void finalizeSchedule() throws Exception {
        assertThat(execute(lifecycleCommand("/schedule/finalize", "finalize-" + current().getRevision())).path("state").asText()).isEqualTo("SCHEDULED");
        assertThat(bookings().stream().filter("RESERVED"::equals)).hasSize(2);
    }
    private void start() throws Exception {
        assertThat(execute(lifecycleCommand("/start", "start-" + current().getRevision())).path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(bookings().stream().filter("IN_USE"::equals)).hasSize(2);
    }
    private void throughStart() throws Exception {
        create(EpisodeScenario.APPOINTMENT); preop(); prepareEligible(); ready(); finalizeSchedule(); start();
    }
    private void complete() throws Exception { execute(completionCommand("complete")); }
    private Step completionCommand(String key) throws Exception {
        Instant startedAt = current().getStartedAt();
        now.updateAndGet(value -> value.isAfter(startedAt.plusSeconds(5)) ? value : startedAt.plusSeconds(6));
        return new Step("POST", "/complete", key, body(Map.of("expectedCaseRevision", current().getRevision(),
                "expectedScheduleRevision", schedules.findByCaseId(caseId).orElseThrow().revision(),
                "procedureCode", "TEST_ACTUAL", "methodCode", "TEST_METHOD", "outcomeCode", "TEST_OUTCOME",
                "actualStartAt", startedAt, "actualEndAt", startedAt.plusSeconds(5),
                "performedItems", List.of(Map.of("performedItemId", UUID.randomUUID(), "itemCode", "TEST_ACTUAL_A", "priceCode", "TEST_PRICE", "quantity", 1),
                        Map.of("performedItemId", UUID.randomUUID(), "itemCode", "TEST_ACTUAL_B", "priceCode", "TEST_PRICE", "quantity", 2)))), 200, 200);
    }
    private Step lifecycleCommand(String path, String key) throws Exception {
        return new Step("POST", path, key, body(Map.of("expectedCaseRevision", current().getRevision(),
                "expectedScheduleRevision", schedules.findByCaseId(caseId).orElseThrow().revision())), 200, 200);
    }
    private JsonNode execute(Step command) throws Exception {
        ResultActions result = send(command).andExpect(status().is(command.freshStatus())).andExpect(jsonPath("$.success").value(true));
        JsonNode outcome = response(result);
        locations.add(result.andReturn().getResponse().getHeader("Location"));
        accepted.add(command); outcomes.add(outcome); return outcome;
    }
    private void replayAll() throws Exception {
        int history = count("surgery_revision_history"), receipts = count("surgery_command_receipt"), events = count("surgery_care_event_outbox");
        for (int i = 0; i < accepted.size(); i++) {
            Step step = accepted.get(i);
            ResultActions result = send(step).andExpect(status().is(step.replayStatus()));
            JsonNode repeated = response(result);
            assertThat(result.andReturn().getResponse().getHeader("Location")).isEqualTo(locations.get(i));
            ObjectNode original = outcomes.get(i).deepCopy(); original.put("replayed", true);
            assertThat(repeated).isEqualTo(original);
        }
        assertThat(count("surgery_revision_history")).isEqualTo(history);
        assertThat(count("surgery_command_receipt")).isEqualTo(receipts);
        assertThat(count("surgery_care_event_outbox")).isEqualTo(events);
        assertNoPendingReceipts();
    }
    private ResultActions send(Step step) throws Exception {
        now.updateAndGet(value -> value.plusMillis(1));
        String path = step.path().isEmpty() ? PATH : PATH + "/" + caseId + step.path();
        MockHttpServletRequestBuilder requestBuilder = step.method().equals("PUT") ? put(path) : post(path);
        return http.perform(requestBuilder.contentType(MediaType.APPLICATION_JSON).content(step.body())
                .header("Idempotency-Key", step.key()).header("X-Correlation-Id", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token()));
    }
    private String token() {
        Instant time = Instant.now();
        return Jwts.builder().subject(account.toString()).claim("role", "DOCTOR").claim("type", "access")
                .claim("staffId", staff.toString()).issuedAt(Date.from(time.minusSeconds(1))).expiration(Date.from(time.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }
    private JsonNode response(ResultActions response) throws Exception { return json.readTree(response.andReturn().getResponse().getContentAsByteArray()).path("data"); }
    private SurgeryCase current() { return cases.findById(caseId).orElseThrow(); }
    private int count(String table) { return database.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private int countEvent(String type) { return database.queryForObject("SELECT count(*) FROM surgery_care_event_outbox WHERE event_type=?", Integer.class, type); }
    private List<String> bookings() { return database.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?", String.class, caseId); }
    private List<String> eventTypes() { return database.queryForList("SELECT event_type FROM surgery_care_event_outbox WHERE surgery_case_id=?", String.class, caseId); }
    private JsonNode eventPayload(String type) throws Exception {
        return json.readTree(database.queryForObject("SELECT payload FROM surgery_care_event_outbox WHERE surgery_case_id=? AND event_type=? ORDER BY occurred_at DESC LIMIT 1",
                byte[].class, caseId, type)).path("payload");
    }
    private void assertNoPendingReceipts() { assertThat(database.queryForObject("SELECT count(*) FROM surgery_command_receipt WHERE status='PENDING'", Integer.class)).isZero(); }
    private void assertHeldOnly() {
        assertThat(database.queryForList("SELECT delivery_status FROM surgery_care_event_outbox", String.class)).isNotEmpty().containsOnly("HELD");
        assertThat(count("surgery_outbox")).isZero(); assertNoPendingReceipts();
    }
    private static void outsideTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    private record Step(String method, String path, String key, String body, int freshStatus, int replayStatus) {}
    private enum EpisodeScenario { APPOINTMENT, ADMISSION, WALK_IN }

    @TestConfiguration(proxyBeanMethods = false) static class LocalLifecycle {
        @Bean SurgeryLifecycleApplicationService lifecycle(SurgeryCaseRepositoryPort cases, SurgeryScheduleRepositoryPort schedules,
                SurgeryCommandReceiptPort receipts, SurgeryReadinessSnapshotPort snapshots, SurgeryChecklistRepositoryPort checklists,
                SurgeryConsentRepositoryPort consents, SurgeryFinancialClearanceRepositoryPort clearances, SurgeryResourceReservationPort resources,
                SurgeryResultRepositoryPort results, SurgeryReadinessAuthorityPort authority, SurgeryLifecycleIntentPort intents,
                SurgeryClockPort clock, FinancialClearanceLookupPort financialAuthority, SurgeryCareEventCapturePort events,
                SurgeryUnitOfWorkPort transactions) {
            return new SurgeryLifecycleApplicationService(cases, schedules, receipts, snapshots, checklists, consents, clearances,
                    resources, results, authority, intents, clock, new SurgeryReadinessEngine(Duration.ofSeconds(30), Duration.ofSeconds(5)),
                    financialAuthority, events, transactions);
        }
    }
}
