package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.model.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual HTTP/security + owned creation kernel/database, with explicitly test-only source authority. */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {"mediflow.features.surgery.enabled=true", "mediflow.surgery.creation.api.enabled=true",
        "mediflow.jwt.secret=creation-http-pg-test-secret-at-least-32-bytes", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"})
class SurgeryCreationHttpPostgresTest {
    private static final String SECRET = "creation-http-pg-test-secret-at-least-32-bytes";
    private static final String PATH = "/api/v1/surgery/cases";
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired SurgeryChecklistRepositoryPort checklists;
    @Autowired SurgeryUnitOfWorkPort unitOfWork;
    @Autowired CreateSurgeryCaseUseCase creation;
    @MockBean SurgeryCreationAuthorityPort authority;
    @MockBean SurgeryClockPort clock;
    private Instant now;
    private SurgeryChecklistTemplate template;
    private UUID requestId;
    private UUID patient;
    private UUID episode;
    private UUID record;
    private UUID department;
    private UUID requester;
    private UUID account;
    private UUID staff;

    @BeforeEach void prepare() {
        db.execute("TRUNCATE surgery_case, surgery_creation_receipt CASCADE");
        now = Instant.now().minusSeconds(1).plusNanos(123);
        when(clock.now()).thenReturn(now);
        requestId = UUID.randomUUID(); patient = UUID.randomUUID(); episode = UUID.randomUUID();
        record = UUID.randomUUID(); department = UUID.randomUUID(); requester = UUID.randomUUID();
        account = UUID.randomUUID(); staff = UUID.randomUUID();
        template = new SurgeryChecklistTemplate(UUID.randomUUID(), "P_" + UUID.randomUUID(), 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "TEST_ONLY", true, 1)));
        unitOfWork.write(() -> { checklists.createTemplate(template, now); return null; });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(authority).authorize(any());
        when(authority.observe(any(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SurgeryCreationAuthorityPort.Approval(call.getArgument(1), template.templateId(), 1, now, now.plusSeconds(30));
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void createHttp_exactOutpatientOrAdmission_commitsThenReplaysOriginalReceipt(boolean admission) throws Exception {
        String body = body(admission);
        var first = send(body, token()).andExpect(status().isCreated()).andReturn();
        var original = mapper.readTree(first.getResponse().getContentAsByteArray()).path("data");
        String location = first.getResponse().getHeader("Location");
        assertThat(location).isEqualTo(PATH + "/" + original.path("surgeryCaseId").asText());
        assertThat(original.path("createdAt").asText()).isEqualTo(now.toString());
        // A replay may still authorize, but may not need a now-unavailable preflight source.
        doThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("test source outage"))
                .when(authority).observe(any(), anyString());
        var replay = send(body, token()).andExpect(status().isOk()).andExpect(header().string("Location", location)).andReturn();
        var repeated = mapper.readTree(replay.getResponse().getContentAsByteArray()).path("data");
        assertThat(repeated.path("surgeryCaseId")).isEqualTo(original.path("surgeryCaseId"));
        assertThat(repeated.path("checklistSnapshotId")).isEqualTo(original.path("checklistSnapshotId"));
        assertThat(repeated.path("createdAt")).isEqualTo(original.path("createdAt"));
        assertThat(repeated.path("replayed").asBoolean()).isTrue();
        verify(authority, times(2)).authorize(argThat(input -> input.actor().accountId().equals(account)
                && input.actor().verifiedStaffId().equals(staff) && input.requestedBy().equals(requester)
                && input.careEpisode().episodeId().equals(episode) && input.careEpisode().recordId().equals(record)));
        verify(authority, times(1)).observe(any(), anyString());
        assertCreatedOnce();
    }

    @Test void createHttp_changedIntent_conflictsWithoutAnotherCharge() throws Exception {
        send(body(false), token()).andExpect(status().isCreated());
        send(body(false).replace("Test-only indication", "Changed clinical intent"), token())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SURGERY_REVISION_CONFLICT"));
        assertCreatedOnce();
    }

    @Test void createHttp_samePatientDistinctEpisodes_hasSeparateCasesAndCharges() throws Exception {
        UUID firstEpisode = episode;
        String firstBody = body(false);
        send(firstBody, token()).andExpect(status().isCreated());
        requestId = UUID.randomUUID(); episode = UUID.randomUUID();
        send(body(false), token()).andExpect(status().isCreated());
        assertThat(db.queryForList("SELECT patient_id FROM surgery_case", UUID.class)).containsOnly(patient);
        assertThat(db.queryForList("SELECT episode_id FROM surgery_case", UUID.class)).containsExactlyInAnyOrder(firstEpisode, episode);
        assertThat(count("surgery_case")).isEqualTo(2);
        assertThat(count("surgery_creation_receipt")).isEqualTo(2);
        assertThat(count("surgery_care_event_outbox")).isEqualTo(2);
    }

    @Test void createHttp_mismatchedTemplateApproval_failsClosedAndRollsBackRequestClaim() throws Exception {
        doAnswer(call -> new SurgeryCreationAuthorityPort.Approval(call.getArgument(1), UUID.randomUUID(), 1, now, now.plusSeconds(30)))
                .when(authority).observe(any(), anyString());
        send(body(false), token()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SURGERY_TEMPLATE_MISMATCH"));
        assertNoEffects();
    }

    @Test void createHttp_revokedCaller_cannotReplayEvenCommittedReceipt() throws Exception {
        send(body(false), token()).andExpect(status().isCreated());
        doThrow(new org.springframework.security.access.AccessDeniedException("test-only revoked authority"))
                .when(authority).authorize(any());
        send(body(false), token()).andExpect(status().isForbidden());
        verify(authority, times(1)).observe(any(), anyString());
        assertCreatedOnce();
    }

    @Test void createHttp_sourceOutage_hasNoReceiptCaseChecklistOrCharge() throws Exception {
        doThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("private-source-detail"))
                .when(authority).observe(any(), anyString());
        var response = send(body(false), token()).andExpect(status().isServiceUnavailable()).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("private-source-detail", "Test-only indication");
        assertNoEffects();
    }

    @Test void createHttp_writeFailure_rollsBackChargeAndReceiptThenSameKeyRecovers() throws Exception {
        db.execute("CREATE OR REPLACE FUNCTION fail_http_creation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private SQL detail'; END $$");
        db.execute("CREATE TRIGGER fail_http_creation BEFORE UPDATE ON surgery_creation_receipt FOR EACH ROW EXECUTE FUNCTION fail_http_creation()");
        try {
            var result = send(body(false), token()).andExpect(status().isInternalServerError()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("private SQL detail", "Test-only indication");
            assertNoEffects();
        } finally { db.execute("DROP TRIGGER fail_http_creation ON surgery_creation_receipt"); }
        send(body(false), token()).andExpect(status().isCreated());
        assertCreatedOnce();
    }

    @Test void createHttp_thenPreopCancelAndRetry_cannotReopenOrRepeatCharge() throws Exception {
        var initial = send(body(false), token()).andExpect(status().isCreated()).andReturn();
        String caseId = mapper.readTree(initial.getResponse().getContentAsByteArray()).path("data").path("surgeryCaseId").asText();
        // Clinical transitions happen later; PostgreSQL may round requestedAt up to its microsecond.
        // Do not weaken the production monotonic-time invariant for a frozen nanosecond test Clock.
        when(clock.now()).thenReturn(now.plusSeconds(1));
        mvc.perform(post(PATH + "/" + caseId + "/preop").header("Authorization", "Bearer " + token())
                .header("Idempotency-Key", "http-begin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCaseRevision\":0}"))
                .andDo(result -> {
                    if (result.getResolvedException() instanceof TypeNotPresentException failure) {
                        throw new AssertionError("Missing runtime type: " + failure.typeName(), failure);
                    }
                }).andExpect(status().isOk());
        when(clock.now()).thenReturn(now.plusSeconds(2));
        mvc.perform(post(PATH + "/" + caseId + "/cancel").header("Authorization", "Bearer " + token())
                .header("Idempotency-Key", "http-cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCaseRevision\":1,\"reason\":\"Test-only cancellation\"}"))
                .andExpect(status().isOk());
        send(body(false), token()).andExpect(status().isOk()).andExpect(jsonPath("$.data.surgeryCaseId").value(caseId));
        assertThat(db.queryForObject("SELECT status FROM surgery_case", String.class)).isEqualTo("CANCELLED");
        assertThat(count("surgery_creation_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isEqualTo(2);
        assertThat(db.queryForList("SELECT delivery_status FROM surgery_care_event_outbox", String.class)).containsOnly("HELD");
    }

    @Test void createHttp_andInternalSystemRequestRace_shareOneGlobalWinner() throws Exception {
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new SurgeryCreationAuthorityPort.Approval(call.getArgument(1), template.templateId(), 1, now, now.plusSeconds(30));
        }).when(authority).observe(any(), anyString());
        String content = body(false); String jwt = token();
        var internal = new CreateSurgeryCaseUseCase.Command(requestId,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, episode, null, record), patient, department,
                requester, template.procedureCode(), "Test-only indication", SurgeryPriority.ROUTINE, now, 1,
                List.of(new SurgeryPlannedItem("ITEM", "PRICE", java.math.BigDecimal.ONE)),
                SurgeryAuditActor.system("test-only-referral"), "test-only-system-correlation");
        try (var workers = Executors.newFixedThreadPool(2)) {
            var http = workers.submit(() -> send(content, jwt).andReturn());
            var system = workers.submit(() -> creation.create(internal));
            var response = http.get(20, TimeUnit.SECONDS).getResponse();
            var result = system.get(20, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isIn(200, 201);
            var httpReceipt = mapper.readTree(response.getContentAsByteArray()).path("data");
            assertThat(httpReceipt.path("surgeryCaseId").asText()).isEqualTo(result.surgeryCaseId().toString());
            assertThat(httpReceipt.path("replayed").asBoolean()).isNotEqualTo(result.replayed());
            assertCreatedOnce();
        }
    }

    private String body(boolean admission) throws Exception {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("surgeryRequestId", requestId); fields.put("careEpisodeType", admission ? "ADMISSION" : "OUTPATIENT_VISIT");
        fields.put("careEpisodeId", episode); fields.put("admissionId", admission ? episode : null);
        fields.put("recordId", record); fields.put("patientId", patient); fields.put("departmentId", department);
        fields.put("requestedBy", requester); fields.put("procedureCode", template.procedureCode());
        fields.put("indication", "Test-only indication"); fields.put("priority", "ROUTINE");
        fields.put("requestedAt", now); fields.put("templateRevision", 1);
        fields.put("plannedItems", List.of(Map.of("itemCode", "ITEM", "priceCode", "PRICE", "quantity", 1)));
        return mapper.writeValueAsString(fields);
    }

    private String token() {
        return Jwts.builder().subject(account.toString()).claim("type", "access").claim("role", "DOCTOR")
                .claim("staffId", staff.toString()).expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private org.springframework.test.web.servlet.ResultActions send(String content, String jwt) throws Exception {
        return mvc.perform(post(PATH).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", requestId.toString()).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void assertCreatedOnce() {
        for (var table : List.of("surgery_case", "preop_checklist_snapshot", "preop_checklist_item",
                "surgery_creation_receipt", "surgery_care_event_outbox")) assertThat(count(table)).as(table).isOne();
        assertThat(db.queryForList("SELECT delivery_status FROM surgery_care_event_outbox", String.class)).containsOnly("HELD");
    }
    private void assertNoEffects() {
        for (var table : List.of("surgery_case", "preop_checklist_snapshot", "preop_checklist_item",
                "surgery_status_history", "surgery_revision_history", "surgery_creation_receipt", "surgery_care_event_outbox"))
            assertThat(count(table)).as(table).isZero();
    }
}
