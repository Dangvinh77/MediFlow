package com.mediflow.report.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import com.mediflow.report.application.port.in.PublishFinancialSnapshotsUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialPublicationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        com.mediflow.report.infrastructure.config.ReportFinancialPublicationJsonConfiguration.class})
@TestPropertySource(properties = {
        "mediflow.features.care-finance-v2=true",
        "mediflow.report.financial-publication.enabled=true",
        "mediflow.jwt.secret=publication-test-secret-at-least-32-bytes"
})
class FinancialPublicationControllerTest {

    private static final String PATH = "/api/v1/reports/financial/publications";
    private static final String SECRET = "publication-test-secret-at-least-32-bytes";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID COMMAND = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID PREVIOUS = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @Autowired
    MockMvc mvc;

    @MockBean
    PublishFinancialSnapshotsUseCase publications;

    private static String publishJson() {
        return """
                {
                  "commandId":"%s",
                  "generationId":"%s",
                  "manifestFingerprint":"%s",
                  "acceptanceReference":"approval-2026-10-11",
                  "historyPolicy":"AVAILABLE_EVIDENCE_ONLY"
                }
                """.formatted(COMMAND, GENERATION, "a".repeat(64));
    }

    private static String rollbackJson() {
        return """
                {
                  "commandId":"%s",
                  "generationId":"%s",
                  "expectedCurrentGenerationId":"%s",
                  "acceptanceReference":"rollback-2026-10-11"
                }
                """.formatted(COMMAND, GENERATION, PREVIOUS);
    }

    @Test
    void publish_adminWithValidRequest_returns201AndAuthenticatedActor() throws Exception {
        var result = new FinancialPublicationDTO(
                COMMAND, GENERATION, null,
                Instant.parse("2026-10-11T00:00:00Z"), "PUBLISH");
        when(publications.publish(org.mockito.ArgumentMatchers.any(), eq(ACTOR))).thenReturn(result);

        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson())
                        .with(user(ACTOR.toString()).roles("ADMIN"))
                        .header("X-Correlation-Id", "publication-check"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.action").value("PUBLISH"))
                .andExpect(jsonPath("$.data.generationId").value(GENERATION.toString()));

        verify(publications).publish(org.mockito.ArgumentMatchers.any(), eq(ACTOR));
    }

    @Test
    void rollback_adminWithExpectedGeneration_returns201AndPreviousGeneration() throws Exception {
        var result = new FinancialPublicationDTO(
                COMMAND, GENERATION, PREVIOUS,
                Instant.parse("2026-10-11T00:00:00Z"), "ROLLBACK");
        when(publications.rollback(org.mockito.ArgumentMatchers.any(), eq(ACTOR))).thenReturn(result);

        mvc.perform(post(PATH + "/rollbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rollbackJson())
                        .with(user(ACTOR.toString()).roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.action").value("ROLLBACK"))
                .andExpect(jsonPath("$.data.previousGenerationId").value(PREVIOUS.toString()));

        verify(publications).rollback(org.mockito.ArgumentMatchers.any(), eq(ACTOR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"})
    void publication_nonAdmin_cannotPublishOrRollback(String role) throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson())
                        .with(user(ACTOR.toString()).roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/rollbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rollbackJson())
                        .with(user(ACTOR.toString()).roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publications);
    }

    @Test
    void publication_anonymous_returns401BeforeUseCase() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publications);
    }

    @Test
    void publish_missingRequiredEvidence_returns400BeforeUseCase() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generationId\":\"" + GENERATION + "\"}")
                        .with(user(ACTOR.toString()).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());

        verifyNoInteractions(publications);
    }

    @Test
    void publish_malformedJson_returns400BeforeUseCase() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json")
                        .with(user(ACTOR.toString()).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(publications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh", "service", "missing-type", "missing-expiry", "expired", "wrong-subject"})
    void publication_invalidSignedAccessToken_returns401(String kind) throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson())
                        .header("Authorization", "Bearer " + token(kind)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publications);
    }

    private static String token(String kind) {
        String subject = kind.equals("wrong-subject") ? "not-a-uuid" : ACTOR.toString();
        var builder = Jwts.builder().subject(subject).claim("role", "ADMIN");
        if (!kind.equals("missing-type")) {
            builder.claim("type", kind.equals("refresh") ? "refresh" : kind.equals("service") ? "service" : "access");
        }
        if (!kind.equals("missing-expiry")) {
            builder.expiration(Date.from(Instant.now().plusSeconds(kind.equals("expired") ? -60 : 120)));
        }
        String secret = kind.equals("wrong-signature")
                ? "wrong-secret-must-have-at-least-32-bytes" : SECRET;
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @ParameterizedTest
    @ValueSource(strings={"numeric-policy", "duplicate-key", "trailing-json", "unknown-actor", "unknown-policy"})
    void publish_ambiguousEvidenceJson_rejectsBeforeOperatorUseCase(String variant) throws Exception {
        String body = publishJson();
        body = switch (variant) {
            case "numeric-policy" -> body.replace("\"AVAILABLE_EVIDENCE_ONLY\"", "0");
            case "duplicate-key" -> body.replace("\"commandId\":", "\"commandId\":\"" + UUID.randomUUID() + "\",\"commandId\":");
            case "trailing-json" -> body + " {}";
            case "unknown-actor" -> body.replace("\"commandId\":", "\"actorAccountId\":\"" + UUID.randomUUID() + "\",\"commandId\":");
            default -> body.replace("AVAILABLE_EVIDENCE_ONLY", "ALL_HISTORY_ASSUMED_COMPLETE");
        };
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user(ACTOR.toString()).roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(publications);
    }
    @Test void publish_validCommand_preservesCorrelationAndDisablesCaching() throws Exception {
        when(publications.publish(org.mockito.ArgumentMatchers.any(), eq(ACTOR))).thenReturn(
                new FinancialPublicationDTO(COMMAND, GENERATION, null, Instant.parse("2026-10-11T00:00:00Z"), "PUBLISH"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(publishJson())
                        .header("X-Correlation-Id", "publication-proof-check").with(user(ACTOR.toString()).roles("ADMIN")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.correlationId").value("publication-proof-check"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
    }
}
