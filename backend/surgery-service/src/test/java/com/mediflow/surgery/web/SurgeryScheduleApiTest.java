package com.mediflow.surgery.web;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.port.in.PrepareSurgeryScheduleUseCase;
import com.mediflow.surgery.infrastructure.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={SurgeryScheduleController.class, SurgeryPreopController.class, SurgeryCancellationController.class},properties={"mediflow.features.surgery.enabled=true",
        "mediflow.jwt.secret=schedule-test-secret-with-at-least-32-bytes"})
@Import(SecurityConfig.class)
class SurgeryScheduleApiTest {
    private static final String SECRET="schedule-test-secret-with-at-least-32-bytes";
    private static final UUID CASE=UUID.randomUUID();
    private static final String PATH="/api/v1/surgery/cases/" + CASE + "/schedule";
    private static final String BODY="""
            {"expectedCaseRevision":1,"expectedScheduleRevision":0,
             "roomId":"00000000-0000-0000-0000-000000000003",
             "startsAt":"2026-10-06T08:00:00Z","endsAt":"2026-10-06T09:00:00Z",
             "team":[{"staffId":"00000000-0000-0000-0000-000000000004","role":"PRIMARY_SURGEON"}]}
            """;
    @Autowired MockMvc mvc;
    @MockBean PrepareSurgeryScheduleUseCase scheduling;
    @MockBean com.mediflow.surgery.application.port.in.BeginPreopUseCase preop;
    @MockBean com.mediflow.surgery.application.port.in.CancelSurgeryUseCase cancel;

    @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER","DOCTOR"})
    void allowedSchedulersReceiveOnlyDraftOutcome(String role) throws Exception {
        when(scheduling.prepare(any())).thenReturn(new SurgeryCommandOutcome("PREPARE_SCHEDULE",CASE,2,
                UUID.randomUUID(),1,"DRAFT",Instant.parse("2026-10-05T08:00:00Z"),false));
        mvc.perform(put(PATH).header("Authorization","Bearer " + token(role)).header("Idempotency-Key","draft-api")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scheduleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.caseRevision").value(2));
        verify(scheduling).prepare(argThat(command -> command.actor().accountId().equals(
                UUID.fromString("00000000-0000-0000-0000-000000000001"))));
    }
    @ParameterizedTest @ValueSource(strings={"NURSE","PATIENT","PHARMACIST","CASHIER","LAB_TECH"})
    void forbiddenRoleNeverChangesDraft(String role) throws Exception {
        mvc.perform(put(PATH).header("Authorization","Bearer " + token(role)).header("Idempotency-Key","draft-api")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(scheduling);
    }
    @Test void unauthenticatedCommandHasNoEffect() throws Exception {
        mvc.perform(put(PATH).header("Idempotency-Key","draft-api").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(scheduling);
    }
    @Test void missingKeyOrRevisionOrReversedIntervalIs400BeforeUseCase() throws Exception {
        mvc.perform(put(PATH).header("Authorization","Bearer " + token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
        for (String malformed : new String[]{BODY.replace("\"expectedCaseRevision\":1,",""),
                BODY.replace("2026-10-06T09:00:00Z","2026-10-06T07:00:00Z"),
                BODY.replace("PRIMARY_SURGEON","UNKNOWN")}) {
            mvc.perform(put(PATH).header("Authorization","Bearer " + token("ADMIN")).header("Idempotency-Key","draft-api")
                    .contentType(MediaType.APPLICATION_JSON).content(malformed)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(scheduling);
    }
    @Test void revisionConflictIs409AndUpstreamFailureIs503() throws Exception {
        when(scheduling.prepare(any())).thenThrow(new com.mediflow.surgery.application.exception.SurgeryRevisionConflictException());
        mvc.perform(put(PATH).header("Authorization","Bearer " + token("DOCTOR")).header("Idempotency-Key","draft-api")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
        doThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("Test unavailable"))
                .when(scheduling).prepare(any());
        mvc.perform(put(PATH).header("Authorization","Bearer " + token("DOCTOR")).header("Idempotency-Key","draft-api")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isServiceUnavailable());
    }
    @Test void unexpectedAuthorityFieldsAreRejectedByEveryCurrentCommand() throws Exception {
        mvc.perform(put(PATH).header("Authorization", "Bearer " + token("ADMIN")).header("Idempotency-Key", "strict-api")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"expectedCaseRevision\":1,",
                        "\"ready\":true,\"expectedCaseRevision\":1,")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        for (String command : new String[]{"preop", "cancel"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                            "/api/v1/surgery/cases/" + CASE + "/" + command)
                    .header("Authorization", "Bearer " + token("ADMIN")).header("Idempotency-Key", "strict-api")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(command.equals("cancel")
                            ? "{\"expectedCaseRevision\":1,\"reason\":\"Cancellation\",\"actorId\":\"forged\"}"
                            : "{\"expectedCaseRevision\":1,\"actorId\":\"forged\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(scheduling, preop, cancel);
    }
    @Test void invalidTransitionIs422ButUnexpectedFailureIsSafe500WithCorrelation() throws Exception {
        when(scheduling.prepare(any())).thenThrow(new com.mediflow.common.exception.BusinessRuleException(
                "SURGERY_SCHEDULE_DRAFT_NOT_ALLOWED", "Schedule requires pre-operative case"));
        mvc.perform(put(PATH).header("Authorization", "Bearer " + token("ADMIN")).header("Idempotency-Key", "errors-api")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SURGERY_SCHEDULE_DRAFT_NOT_ALLOWED"));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Private SQL, connection or patient data"))
                .when(scheduling).prepare(any());
        String correlation = UUID.randomUUID().toString();
        mvc.perform(put(PATH).header("Authorization", "Bearer " + token("ADMIN")).header("Idempotency-Key", "errors-api")
                .header("X-Correlation-Id", correlation).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("The operation could not be completed"))
                .andExpect(jsonPath("$.correlationId").value(correlation));
    }
    private String token(String role) {
        return Jwts.builder().subject("00000000-0000-0000-0000-000000000001")
                .claim("staffId","00000000-0000-0000-0000-000000000002").claim("type","access").claim("role",role)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
