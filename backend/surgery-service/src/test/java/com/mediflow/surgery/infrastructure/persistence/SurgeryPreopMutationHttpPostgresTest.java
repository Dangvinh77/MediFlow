package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryPreopAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.startsWith;

/**
 * Real HTTP/security plus PostgreSQL proof for the default-off pre-op mutation boundary.
 *
 * <p>The authority double is deliberately limited to this test: production has no provider until
 * the clinical/legal evidence contract is accepted. Kernels, repositories, transactions and the
 * held event writer remain real beans.</p>
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "mediflow.features.surgery.enabled=true",
        "mediflow.surgery.preop.api.enabled=true",
        "mediflow.jwt.secret=preop-http-pg-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SurgeryPreopMutationHttpPostgresTest {
    private static final String SECRET = "preop-http-pg-test-secret-at-least-32-bytes";
    private static final String CASES_PATH = "/api/v1/surgery/cases";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryChecklistRepositoryPort checklists;
    @Autowired SurgeryConsentRepositoryPort consents;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryResourceReservationPort reservations;
    @SpyBean SurgeryCommandReceiptPort receipts;
    @MockBean SurgeryPreopAuthorityPort authority;
    @MockBean SurgeryClockPort clock;

    private final AtomicReference<Instant> testNow = new AtomicReference<>();
    private UUID account;
    private UUID staff;

    @BeforeEach
    void setUp() {
        db.execute("TRUNCATE surgery_case CASCADE");
        db.execute("TRUNCATE preop_checklist_template CASCADE");
        account = UUID.randomUUID();
        staff = UUID.randomUUID();
        testNow.set(Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MICROS));
        reset(authority, clock, receipts);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String fingerprint = invocation.getArgument(2, String.class);
            Instant observed = testNow.get();
            return new SurgeryPreopAuthorityPort.Approval(fingerprint, observed, observed.plusSeconds(30));
        }).when(authority).approveChecklist(any(), any(), anyString());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String fingerprint = invocation.getArgument(2, String.class);
            Instant observed = testNow.get();
            return new SurgeryPreopAuthorityPort.Approval(fingerprint, observed, observed.plusSeconds(30));
        }).when(authority).approveConsent(any(), any(), anyString());
        doAnswer(invocation -> testNow.get()).when(clock).now();
    }

    @Test
    void checklist_put_persistsEvidenceAndAudit() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> body = checklistBody(fixture, "SATISFIED", UUID.randomUUID(), 7L);

        checklist(fixture, body, "checklist-evidence-1", "DOCTOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commandCode").value("UPDATE_CHECKLIST_ITEM"))
                .andExpect(jsonPath("$.data.state").value("SATISFIED"))
                .andExpect(jsonPath("$.data.replayed").value(false));

        assertThat(db.queryForObject("SELECT status FROM preop_checklist_item WHERE checklist_item_id=?",
                String.class, fixture.itemId())).isEqualTo("SATISFIED");
        assertThat(db.queryForObject("SELECT evidence_revision FROM preop_checklist_item WHERE checklist_item_id=?",
                Long.class, fixture.itemId())).isEqualTo(7L);
        assertThat(count("preop_checklist_item_history", fixture.caseId())).isOne();
        assertThat(count("surgery_command_receipt", fixture.caseId())).isOne();
        assertThat(count("surgery_care_event_outbox", fixture.caseId())).isZero();
    }

    @Test
    void checklist_put_sameKey_replaysWithoutSecondMutation() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> body = checklistBody(fixture, "FAILED", null, null);
        checklist(fixture, body, "checklist-replay-1", "DOCTOR").andExpect(status().isOk());
        checklist(fixture, body, "checklist-replay-1", "DOCTOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true));

        assertThat(count("preop_checklist_item_history", fixture.caseId())).isOne();
        assertThat(count("surgery_command_receipt", fixture.caseId())).isOne();
        assertThat(db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",
                Long.class, fixture.caseId())).isEqualTo(2L);
        verify(authority, times(2)).approveChecklist(any(), any(), anyString());
    }

    @Test
    void checklist_put_changedPayload_sameKey_conflictsWithoutMutation() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> first = checklistBody(fixture, "FAILED", null, null);
        Map<String, Object> changed = checklistBody(fixture, "SATISFIED", UUID.randomUUID(), 1L);
        checklist(fixture, first, "checklist-payload-1", "DOCTOR").andExpect(status().isOk());
        checklist(fixture, changed, "checklist-payload-1", "DOCTOR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SURGERY_REVISION_CONFLICT"));

        assertThat(count("preop_checklist_item_history", fixture.caseId())).isOne();
        assertThat(db.queryForObject("SELECT status FROM preop_checklist_item WHERE checklist_item_id=?",
                String.class, fixture.itemId())).isEqualTo("FAILED");
        assertThat(db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",
                Long.class, fixture.caseId())).isEqualTo(2L);
    }

    @Test
    void consent_post_twoTypedConsents_persistsEachAuditAndLocation() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> surgery = consentBody("SURGERY", fixture.patientId());
        MvcResult first = consent(fixture, surgery, "consent-surgery-1", "DOCTOR")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(
                        "/api/v1/surgery/cases/" + fixture.caseId() + "/consents/")))
                .andReturn();
        JsonNode firstData = data(first);
        assertThat(firstData.path("state").asText()).isEqualTo("ACTIVE:SURGERY");

        testNow.updateAndGet(value -> value.plusMillis(1));
        Map<String, Object> anesthesia = consentBody("ANESTHESIA", fixture.patientId());
        consent(fixture, anesthesia, "consent-anesthesia-1", "DOCTOR")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "/api/v1/surgery/cases/" + fixture.caseId() + "/consents/")));

        assertThat(db.queryForList("SELECT consent_type FROM surgery_consent WHERE surgery_case_id=? ORDER BY consent_type",
                String.class, fixture.caseId())).containsExactly("ANESTHESIA", "SURGERY");
        assertThat(count("surgery_consent_history", fixture.caseId())).isEqualTo(2);
        assertThat(count("surgery_command_receipt", fixture.caseId())).isEqualTo(2);
        assertThat(firstData.path("subjectId").asText()).isNotBlank();
    }

    @Test
    void consent_post_sameKey_replaysWithSameLocation() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> body = consentBody("SURGERY", fixture.patientId());
        MvcResult first = consent(fixture, body, "consent-replay-1", "DOCTOR")
                .andExpect(status().isCreated()).andReturn();
        String location = first.getResponse().getHeader("Location");
        MvcResult replay = consent(fixture, body, "consent-replay-1", "DOCTOR")
                .andExpect(status().isOk())
                .andExpect(header().string("Location", location))
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andReturn();

        assertThat(data(replay).path("subjectId").asText()).isEqualTo(data(first).path("subjectId").asText());
        assertThat(count("surgery_consent", fixture.caseId())).isOne();
        assertThat(count("surgery_consent_history", fixture.caseId())).isOne();
        verify(authority, times(2)).approveConsent(any(), any(), anyString());
    }

    @Test
    void consent_post_deniedAuthority_blocksFreshAndReplay() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> body = consentBody("SURGERY", fixture.patientId());
        doThrow(new AccessDeniedException("test-only denied authority"))
                .when(authority).approveConsent(any(), any(), anyString());

        consent(fixture, body, "consent-denied-1", "DOCTOR")
                .andExpect(status().isForbidden());
        assertNoConsentEffects(fixture);

        reset(authority);
        doAnswer(invocation -> {
            String fingerprint = invocation.getArgument(2, String.class);
            return new SurgeryPreopAuthorityPort.Approval(fingerprint, testNow.get(), testNow.get().plusSeconds(30));
        }).when(authority).approveConsent(any(), any(), anyString());
        consent(fixture, body, "consent-denied-1", "DOCTOR").andExpect(status().isCreated());
        doThrow(new AccessDeniedException("test-only revoked replay authority"))
                .when(authority).approveConsent(any(), any(), anyString());
        consent(fixture, body, "consent-denied-1", "DOCTOR").andExpect(status().isForbidden());
        assertThat(count("surgery_consent", fixture.caseId())).isOne();
        assertThat(count("surgery_consent_history", fixture.caseId())).isOne();
    }

    @Test
    void checklist_put_sourceOutage_hasNoReceiptAuditOrStateChange() throws Exception {
        CaseFixture fixture = seedPreopCase();
        doThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("private source detail"))
                .when(authority).approveChecklist(any(), any(), anyString());

        checklist(fixture, checklistBody(fixture, "FAILED", null, null), "checklist-outage-1", "DOCTOR")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SURGERY_UPSTREAM_UNAVAILABLE"));
        assertNoChecklistEffects(fixture);
    }

    @Test
    void checklist_put_invalidProof_hasNoReceiptAuditOrStateChange() throws Exception {
        CaseFixture fixture = seedPreopCase();
        doAnswer(invocation -> new SurgeryPreopAuthorityPort.Approval(
                "0".repeat(64), testNow.get(), testNow.get().plusSeconds(30)))
                .when(authority).approveChecklist(any(), any(), anyString());

        checklist(fixture, checklistBody(fixture, "FAILED", null, null), "checklist-invalid-proof-1", "DOCTOR")
                .andExpect(status().isServiceUnavailable());
        assertNoChecklistEffects(fixture);
    }

    @Test
    void checklist_put_notApplicableWithoutPolicy_is400AndHasNoEffects() throws Exception {
        CaseFixture fixture = seedPreopCase();
        Map<String, Object> body = checklistBody(fixture, "NOT_APPLICABLE", null, null);

        checklist(fixture, body, "checklist-na-1", "DOCTOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertNoChecklistEffects(fixture);
    }

    @Test
    void checklist_put_missingVerifiedStaff_isForbiddenBeforeAuthority() throws Exception {
        CaseFixture fixture = seedPreopCase();
        checklist(fixture, checklistBody(fixture, "FAILED", null, null), "checklist-staff-1", "DOCTOR", null)
                .andExpect(status().isForbidden());
        assertNoChecklistEffects(fixture);
    }

    @Test
    void checklist_put_pharmacistRole_isForbiddenBeforeAuthority() throws Exception {
        CaseFixture fixture = seedPreopCase();
        checklist(fixture, checklistBody(fixture, "FAILED", null, null), "checklist-role-1", "PHARMACIST")
                .andExpect(status().isForbidden());
        assertNoChecklistEffects(fixture);
    }

    @Test
    void checklist_put_scheduledCase_invalidatesAndReleasesExactReservation() throws Exception {
        ScheduledFixture fixture = seedScheduledCase();
        testNow.updateAndGet(value -> value.plusSeconds(5));
        checklist(fixture.base(), checklistBody(fixture.base(), "FAILED", null, null),
                "checklist-scheduled-1", "DOCTOR")
                .andExpect(status().isOk());

        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo("PREOP_IN_PROGRESS");
        assertThat(db.queryForObject("SELECT readiness_snapshot_id FROM surgery_case WHERE surgery_case_id=?",
                UUID.class, fixture.caseId())).isNull();
        assertThat(db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",
                String.class, fixture.caseId())).containsOnly("RELEASED");
        assertThat(db.queryForObject("SELECT event_type FROM surgery_care_event_outbox WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo("surgery.readiness.invalidated");
        byte[] payload = db.queryForObject("SELECT payload FROM surgery_care_event_outbox WHERE surgery_case_id=?",
                byte[].class, fixture.caseId());
        assertThat(new String(payload, StandardCharsets.UTF_8))
                .contains(fixture.scheduleId().toString())
                .contains("\"scheduleRevision\":1");
    }

    @Test
    void checklist_put_scheduledEventFailure_rollsBackReleaseAuditReceiptThenRetrySucceeds() throws Exception {
        ScheduledFixture fixture = seedScheduledCase();
        testNow.updateAndGet(value -> value.plusSeconds(5));
        db.execute("CREATE OR REPLACE FUNCTION fail_preop_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private preop event failure'; END $$");
        db.execute("CREATE TRIGGER fail_preop_event BEFORE INSERT ON surgery_care_event_outbox FOR EACH ROW EXECUTE FUNCTION fail_preop_event()");
        try {
            checklist(fixture.base(), checklistBody(fixture.base(), "FAILED", null, null),
                    "checklist-rollback-1", "DOCTOR")
                    .andExpect(status().isInternalServerError());
        } finally {
            db.execute("DROP TRIGGER fail_preop_event ON surgery_care_event_outbox");
        }

        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo("SCHEDULED");
        assertThat(db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",
                String.class, fixture.caseId())).containsOnly("RESERVED");
        assertThat(count("preop_checklist_item_history", fixture.caseId())).isOne();
        assertThat(count("surgery_command_receipt", fixture.caseId())).isZero();
        assertThat(count("surgery_care_event_outbox", fixture.caseId())).isZero();

        testNow.updateAndGet(value -> value.plusMillis(1));
        checklist(fixture.base(), checklistBody(fixture.base(), "FAILED", null, null),
                "checklist-rollback-1", "DOCTOR").andExpect(status().isOk());
        assertThat(db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",
                String.class, fixture.caseId())).containsOnly("RELEASED");
        assertThat(count("preop_checklist_item_history", fixture.caseId())).isEqualTo(2);
        assertThat(count("surgery_command_receipt", fixture.caseId())).isOne();
        assertThat(count("surgery_care_event_outbox", fixture.caseId())).isOne();
    }

    @Test
    void checklist_put_authorityExpiresDuringKernel_rollsBackThenSameKeyRetries() throws Exception {
        ScheduledFixture fixture = seedScheduledCase();
        testNow.updateAndGet(value -> value.plusSeconds(5));
        doAnswer(invocation -> {
            String fingerprint = invocation.getArgument(2, String.class);
            return new SurgeryPreopAuthorityPort.Approval(fingerprint, testNow.get(), testNow.get().plusSeconds(5));
        }).when(authority).approveChecklist(any(), any(), anyString());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            testNow.updateAndGet(value -> value.plusSeconds(6));
            return null;
        }).when(receipts).complete(any(), any(), anyString(), any(), any());

        checklist(fixture.base(), checklistBody(fixture.base(), "FAILED", null, null),
                "checklist-expiry-1", "DOCTOR").andExpect(status().isServiceUnavailable());
        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo("SCHEDULED");
        assertThat(db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",
                String.class, fixture.caseId())).containsOnly("RESERVED");
        assertThat(count("preop_checklist_item_history", fixture.caseId())).isOne();
        assertThat(count("surgery_command_receipt", fixture.caseId())).isZero();
        assertThat(count("surgery_care_event_outbox", fixture.caseId())).isZero();

        reset(receipts);
        testNow.updateAndGet(value -> value.plusSeconds(1));
        checklist(fixture.base(), checklistBody(fixture.base(), "FAILED", null, null),
                "checklist-expiry-1", "DOCTOR").andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo("PREOP_IN_PROGRESS");
        assertThat(count("surgery_command_receipt", fixture.caseId())).isOne();
        assertThat(count("preop_checklist_item_history", fixture.caseId())).isEqualTo(2);
    }

    @Test
    void consent_post_terminalCase_rejectsLateMutationWithoutSideEffects() throws Exception {
        ScheduledFixture fixture = seedScheduledCase();
        testNow.updateAndGet(value -> value.plusSeconds(5));
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            SurgeryCase value = cases.lockById(fixture.caseId()).orElseThrow();
            value.start(value.getReadinessSnapshot(), fixture.actor(), "terminal-fixture", testNow.get().plusSeconds(1));
            reservations.markInUse(fixture.caseId(), fixture.scheduleId(), 1, testNow.get().plusSeconds(1));
            cases.save(value, 6);
            value.complete(fixture.actor(), "terminal-fixture", testNow.get().plusSeconds(2));
            cases.save(value, 7);
        });
        long caseRevision = db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",
                Long.class, fixture.caseId());
        var consentBefore = db.queryForList(
                "SELECT * FROM surgery_consent WHERE surgery_case_id=? ORDER BY consent_id", fixture.caseId());
        assertThat(consentBefore).hasSize(2); // Scheduling required both typed consents.
        var caseBefore = db.queryForMap("SELECT * FROM surgery_case WHERE surgery_case_id=?", fixture.caseId());
        var reservationsBefore = db.queryForList(
                "SELECT * FROM surgery_resource_reservation WHERE surgery_case_id=? ORDER BY reservation_id",
                fixture.caseId());
        int consentHistoryBefore = count("surgery_consent_history", fixture.caseId());
        int outboxBefore = count("surgery_care_event_outbox", fixture.caseId());
        Map<String, Object> body = consentBody("SURGERY", fixture.patientId());
        body.put("expectedCaseRevision", caseRevision);
        consent(fixture.base(), body, "consent-terminal-1", "DOCTOR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SURGERY_REVISION_CONFLICT"));
        assertThat(db.queryForList("SELECT * FROM surgery_consent WHERE surgery_case_id=? ORDER BY consent_id",
                fixture.caseId())).isEqualTo(consentBefore);
        assertThat(count("surgery_consent_history", fixture.caseId())).isEqualTo(consentHistoryBefore);
        assertThat(db.queryForMap("SELECT * FROM surgery_case WHERE surgery_case_id=?", fixture.caseId()))
                .isEqualTo(caseBefore);
        assertThat(db.queryForList(
                "SELECT * FROM surgery_resource_reservation WHERE surgery_case_id=? ORDER BY reservation_id",
                fixture.caseId())).isEqualTo(reservationsBefore);
        assertThat(count("surgery_care_event_outbox", fixture.caseId())).isEqualTo(outboxBefore);
        assertThat(count("surgery_command_receipt", fixture.caseId())).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions checklist(
            CaseFixture fixture, Map<String, Object> body, String key, String role) throws Exception {
        return checklist(fixture, body, key, role, staff);
    }

    private org.springframework.test.web.servlet.ResultActions checklist(
            CaseFixture fixture, Map<String, Object> body, String key, String role, UUID tokenStaff) throws Exception {
        String authorization = token(role, tokenStaff);
        var request = put(CASES_PATH + "/" + fixture.caseId() + "/checklist")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
        request.header("Authorization", "Bearer " + authorization);
        return mvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions consent(
            CaseFixture fixture, Map<String, Object> body, String key, String role) throws Exception {
        return mvc.perform(post(CASES_PATH + "/" + fixture.caseId() + "/consents")
                .header("Authorization", "Bearer " + token(role, staff))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private Map<String, Object> checklistBody(CaseFixture fixture, String status,
                                               UUID evidenceId, Long evidenceRevision) {
        Map<String, Object> body = new HashMap<>();
        body.put("checklistItemId", fixture.itemId());
        body.put("expectedCaseRevision", currentCaseRevision(fixture.caseId()));
        body.put("expectedSnapshotRevision", currentSnapshotRevision(fixture.caseId()));
        body.put("expectedItemRevision", currentItemRevision(fixture.itemId()));
        body.put("status", status);
        if (evidenceId != null) body.put("evidenceReferenceId", evidenceId);
        if (evidenceRevision != null) body.put("evidenceRevision", evidenceRevision);
        return body;
    }

    private Map<String, Object> consentBody(String type, UUID signerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("expectedCaseRevision", currentCaseRevision(activeCaseId));
        body.put("consentType", type);
        body.put("signerId", signerId);
        body.put("signerType", "PATIENT");
        body.put("evidenceDocumentId", UUID.randomUUID());
        return body;
    }

    private UUID activeCaseId;

    private CaseFixture seedPreopCase() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Instant base = testNow.get();
            UUID caseId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            UUID patientId = UUID.randomUUID();
            UUID departmentId = UUID.randomUUID();
            UUID episodeId = UUID.randomUUID();
            UUID recordId = UUID.randomUUID();
            String procedure = "PROC-" + UUID.randomUUID().toString().substring(0, 8);
            SurgeryAuditActor actor = SurgeryAuditActor.human(account, staff);
            SurgeryCase surgeryCase = SurgeryCase.create(caseId, requestId,
                    new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, episodeId, null, recordId),
                    patientId, departmentId, staff, procedure, "Test-only pre-op indication",
                    SurgeryPriority.ROUTINE, base.minusSeconds(20), actor, "preop-fixture");
            cases.save(surgeryCase, -1);
            surgeryCase.beginPreop(actor, "preop-fixture", base.minusSeconds(19));
            cases.save(surgeryCase, 0);
            SurgeryChecklistItemDefinition definition = new SurgeryChecklistItemDefinition(
                    UUID.randomUUID(), "IDENTITY_CONFIRMED", true, 1);
            SurgeryChecklistTemplate template = new SurgeryChecklistTemplate(
                    UUID.randomUUID(), procedure, 1, List.of(definition));
            checklists.createTemplate(template, base.minusSeconds(18));
            var snapshot = template.snapshotForCase(UUID.randomUUID(), caseId);
            checklists.createSnapshot(snapshot);
            CaseFixture fixture = new CaseFixture(caseId, patientId, staff, snapshot.checklistSnapshotId(),
                    snapshot.items().getFirst().checklistItemId(), actor, procedure, null);
            activeCaseId = caseId;
            return fixture;
        });
    }

    private ScheduledFixture seedScheduledCase() {
        CaseFixture base = seedPreopCase();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Instant at = testNow.get();
            SurgeryCase value = cases.lockById(base.caseId()).orElseThrow();
            SurgeryChecklistItem item = checklists.findSnapshotByCaseId(base.caseId()).orElseThrow().items().getFirst();
            var snapshot = checklists.findSnapshotByCaseId(base.caseId()).orElseThrow();
            var satisfied = item.revise(SurgeryChecklistStatus.SATISFIED, UUID.randomUUID(), 1L);
            var revised = snapshot.reviseItem(item.checklistItemId(), 0, satisfied);
            checklists.saveItemChange(revised, 0, new SurgeryChecklistItemChange(UUID.randomUUID(), item.checklistItemId(),
                    1, SurgeryChecklistStatus.PENDING, SurgeryChecklistStatus.SATISFIED,
                    satisfied.evidenceReferenceId(), 1L, base.actor(), at.minusSeconds(10), "scheduled-fixture"));
            value.recordBusinessMutation(base.actor(), "scheduled-fixture", at.minusSeconds(9), "CHECKLIST_ITEM_CHANGED");
            cases.save(value, 1);
            Map<SurgeryConsentType, UUID> consentIds = new EnumMap<>(SurgeryConsentType.class);
            for (SurgeryConsentType type : SurgeryConsentType.values()) {
                var consent = com.mediflow.surgery.domain.model.SurgeryConsentRecord.sign(UUID.randomUUID(),
                        base.caseId(), type, base.patientId(), SurgeryConsentSignerType.PATIENT,
                        UUID.randomUUID(), base.actor(), at.minusSeconds(8), "scheduled-fixture");
                consents.save(consent);
                consentIds.put(type, consent.consentId());
                value.recordBusinessMutation(base.actor(), "scheduled-fixture", at.minusSeconds(7), "CONSENT_SIGNED");
                cases.save(value, 2 + type.ordinal());
            }
            UUID roomId = UUID.randomUUID();
            UUID teamStaff = UUID.randomUUID();
            SurgerySchedule schedule = new SurgerySchedule(UUID.randomUUID(), base.caseId(), 1, roomId,
                    at.plusSeconds(300), at.plusSeconds(900),
                    List.of(new SurgeryTeamAssignment(teamStaff, SurgeryTeamRole.PRIMARY_SURGEON)));
            schedules.saveDraft(schedule, 0, at.minusSeconds(5));
            List<SurgeryDependencyRevision> dependencies = EnumSet.allOf(SurgeryDependencyType.class).stream()
                    .map(type -> new SurgeryDependencyRevision(type,
                            switch (type) {
                                case INDICATION -> value.getSurgeryRequestId();
                                case CHECKLIST -> base.checklistSnapshotId();
                                case SURGERY_CONSENT -> consentIds.get(SurgeryConsentType.SURGERY);
                                case ANESTHESIA_CONSENT -> consentIds.get(SurgeryConsentType.ANESTHESIA);
                                case FINANCIAL_CLEARANCE -> UUID.randomUUID();
                                case TEAM_ELIGIBILITY -> teamStaff;
                                case SCHEDULE -> schedule.scheduleId();
                            }, type == SurgeryDependencyType.SCHEDULE ? 1 : 0)).toList();
            ReadinessSnapshot readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), base.caseId(),
                    true, true, true, true, true, true, true, at, dependencies, at.plusSeconds(120));
            value.markReady(readiness, base.actor(), "scheduled-fixture");
            cases.save(value, 4);
            reservations.reserve(schedule, at.plusSeconds(1));
            value.finalizeSchedule(base.actor(), "scheduled-fixture", at.plusSeconds(2));
            cases.save(value, 5);
            activeCaseId = base.caseId();
            return new ScheduledFixture(base, schedule.scheduleId());
        });
    }

    private long currentCaseRevision(UUID caseId) {
        return db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?", Long.class, caseId);
    }

    private long currentSnapshotRevision(UUID caseId) {
        return db.queryForObject("SELECT revision FROM preop_checklist_snapshot WHERE surgery_case_id=?", Long.class, caseId);
    }

    private long currentItemRevision(UUID itemId) {
        return db.queryForObject("SELECT revision FROM preop_checklist_item WHERE checklist_item_id=?", Long.class, itemId);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private void assertNoChecklistEffects(CaseFixture fixture) {
        assertThat(db.queryForObject("SELECT status FROM preop_checklist_item WHERE checklist_item_id=?",
                String.class, fixture.itemId())).isEqualTo("PENDING");
        assertThat(count("preop_checklist_item_history", fixture.caseId())).isZero();
        assertThat(count("surgery_command_receipt", fixture.caseId())).isZero();
        assertThat(db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",
                Long.class, fixture.caseId())).isEqualTo(1L);
        assertThat(count("surgery_care_event_outbox", fixture.caseId())).isZero();
    }

    private void assertNoConsentEffects(CaseFixture fixture) {
        assertThat(count("surgery_consent", fixture.caseId())).isZero();
        assertThat(count("surgery_consent_history", fixture.caseId())).isZero();
        assertThat(count("surgery_command_receipt", fixture.caseId())).isZero();
        assertThat(db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",
                Long.class, fixture.caseId())).isEqualTo(1L);
    }

    private int count(String table, UUID caseId) {
        if ("preop_checklist_item_history".equals(table)) {
            return db.queryForObject("""
                    SELECT count(*) FROM preop_checklist_item_history h
                    JOIN preop_checklist_item i ON i.checklist_item_id = h.checklist_item_id
                    WHERE i.surgery_case_id = ?
                    """, Integer.class, caseId);
        }
        if ("surgery_consent_history".equals(table)) {
            return db.queryForObject("""
                    SELECT count(*) FROM surgery_consent_history h
                    JOIN surgery_consent c ON c.consent_id = h.consent_id
                    WHERE c.surgery_case_id = ?
                    """, Integer.class, caseId);
        }
        return db.queryForObject("SELECT count(*) FROM " + table + " WHERE surgery_case_id=?", Integer.class, caseId);
    }

    private String token(String role, UUID tokenStaff) {
        var builder = Jwts.builder().subject(account.toString()).claim("type", "access").claim("role", role);
        if (tokenStaff != null) builder.claim("staffId", tokenStaff.toString());
        return builder.expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private record CaseFixture(UUID caseId, UUID patientId, UUID staffId, UUID checklistSnapshotId,
                               UUID itemId, SurgeryAuditActor actor, String procedureCode,
                               UUID scheduleId) {}

    private record ScheduledFixture(CaseFixture base, UUID scheduleId) {
        UUID caseId() { return base.caseId(); }
        UUID patientId() { return base.patientId(); }
        SurgeryAuditActor actor() { return base.actor(); }
    }
}
