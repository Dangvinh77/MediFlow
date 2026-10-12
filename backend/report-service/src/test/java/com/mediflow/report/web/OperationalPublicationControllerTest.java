package com.mediflow.report.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import com.mediflow.report.application.port.in.PublishOperationalSnapshotsUseCase;
import com.mediflow.report.infrastructure.config.ReportOperationalPublicationJsonConfiguration;
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

@WebMvcTest(OperationalPublicationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ReportOperationalPublicationJsonConfiguration.class})
@TestPropertySource(properties = {
        "mediflow.features.care-finance-v2=true",
        "mediflow.report.operational-publication.enabled=true",
        "mediflow.jwt.secret=operational-publication-test-secret-32-bytes"
})
class OperationalPublicationControllerTest {

    private static final String PATH = "/api/v1/reports/operations/publications";
    private static final String SECRET = "operational-publication-test-secret-32-bytes";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID COMMAND = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID PREVIOUS = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @Autowired
    MockMvc mvc;

    @MockBean
    PublishOperationalSnapshotsUseCase publications;

    private static String publishJson() {
        return """
                {
                  "commandId":"%s",
                  "generationId":"%s",
                  "manifestFingerprint":"%s",
                  "coveredFrom":"2026-10-10",
                  "coveredTo":"2026-10-11",
                  "acceptanceReference":"approval/2026-10-11",
                  "expectedCurrentGenerationId":"%s"
                }
                """.formatted(COMMAND, GENERATION, "a".repeat(64), PREVIOUS);
    }

    private static String rollbackJson() {
        return """
                {
                  "commandId":"%s",
                  "generationId":"%s",
                  "acceptanceReference":"rollback/2026-10-11",
                  "expectedCurrentGenerationId":"%s"
                }
                """.formatted(COMMAND, GENERATION, PREVIOUS);
    }

    @Test
    void publish_adminWithSignedAccessToken_returns201AndAuthenticatedActor() throws Exception {
        when(publications.publish(any(), eq(ACTOR))).thenReturn(new OperationalPublicationDTO(
                COMMAND, GENERATION, PREVIOUS, Instant.parse("2026-10-11T04:00:00Z"), "PUBLISH"));

        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson())
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true))
                        .header(JwtClaims.HEADER_CORRELATION_ID, "operational-publication-check"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.action").value("PUBLISH"))
                .andExpect(jsonPath("$.data.generationId").value(GENERATION.toString()))
                .andExpect(jsonPath("$.correlationId").value("operational-publication-check"))
                .andExpect(header().string("Location", "/api/v1/reports/operations/rebuilds/" + GENERATION))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(publications).publish(any(), eq(ACTOR));
    }

    @Test
    void rollback_adminWithSignedAccessToken_returns201AndPreviousGeneration() throws Exception {
        when(publications.rollback(any(), eq(ACTOR))).thenReturn(new OperationalPublicationDTO(
                COMMAND, GENERATION, PREVIOUS, Instant.parse("2026-10-11T04:00:00Z"), "ROLLBACK"));

        mvc.perform(post(PATH + "/rollbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rollbackJson())
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.action").value("ROLLBACK"))
                .andExpect(jsonPath("$.data.previousGenerationId").value(PREVIOUS.toString()))
                .andExpect(header().string("Location", "/api/v1/reports/operations/rebuilds/" + GENERATION))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(publications).rollback(any(), eq(ACTOR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"})
    void publication_nonAdminRole_returns403BeforeUseCase(String role) throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson())
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true,
                                role)))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/rollbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rollbackJson())
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true,
                                role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publications);
    }

    @Test
    void publication_anonymous_returns401BeforeUseCase() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(publishJson()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/rollbacks").contentType(MediaType.APPLICATION_JSON).content(rollbackJson()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh", "service", "missing-type", "missing-expiry", "expired",
            "bad-canonical-subject", "wrong-signature"})
    void publication_invalidSignedToken_returns401BeforeUseCase(String kind) throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson())
                        .header("Authorization", "Bearer " + token(kind, ACTOR.toString(), true)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post(PATH + "/rollbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rollbackJson())
                        .header("Authorization", "Bearer " + token(kind, ACTOR.toString(), true)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publications);
    }

    @Test
    void publish_missingRequiredField_returns400BeforeUseCase() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + COMMAND + "\"}")
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());

        verifyNoInteractions(publications);
    }

    @Test
    void publish_invalidSha256OrReference_returns400BeforeUseCase() throws Exception {
        String invalid = publishJson()
                .replace("a".repeat(64), "A".repeat(64))
                .replace("approval/2026-10-11", "approval with spaces");

        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid)
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));

        verifyNoInteractions(publications);
    }

    @Test
    void rollback_missingExpectedCurrentGenerationId_returns400BeforeUseCase() throws Exception {
        String invalid = rollbackJson().replace(",\n  \"expectedCurrentGenerationId\":\"" + PREVIOUS + "\"", "");

        mvc.perform(post(PATH + "/rollbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid)
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));

        verifyNoInteractions(publications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate-key", "trailing-json", "unknown-actor", "unknown-field"})
    void publication_ambiguousOrClientSuppliedActorJson_returns400BeforeUseCase(String variant) throws Exception {
        String body = switch (variant) {
            case "duplicate-key" -> publishJson().replace("\"commandId\":",
                    "\"commandId\":\"" + UUID.randomUUID() + "\",\"commandId\":");
            case "trailing-json" -> publishJson() + " {}";
            case "unknown-actor" -> publishJson().replace("\"commandId\":",
                    "\"actorAccountId\":\"" + UUID.randomUUID() + "\",\"commandId\":");
            default -> publishJson().replace("\"commandId\":", "\"unexpected\":true,\"commandId\":");
        };

        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token("access", ACTOR.toString(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(publications);
    }

    private static String token(String kind, String subject, boolean expires) {
        return token(kind, subject, expires, "ADMIN");
    }

    private static String token(String kind, String subject, boolean expires, String role) {
        String actualSubject = kind.equals("bad-canonical-subject")
                ? "ABCDEF00-0000-4000-8000-000000000001" : subject;
        String secret = kind.equals("wrong-signature")
                ? "wrong-operational-publication-secret-32-bytes" : SECRET;
        var builder = Jwts.builder().subject(actualSubject).claim(JwtClaims.ROLE, role);
        if (!kind.equals("missing-type")) {
            builder.claim(JwtClaims.TYPE, kind.equals("refresh") ? JwtClaims.REFRESH_TOKEN_TYPE
                    : kind.equals("service") ? JwtClaims.SERVICE_TOKEN_TYPE : JwtClaims.ACCESS_TOKEN_TYPE);
        }
        if (!kind.equals("missing-expiry")) {
            builder.expiration(Date.from(Instant.now().plusSeconds(kind.equals("expired") ? -60 : 300)));
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

/** The operational publication gate must be closed unless both flags are explicitly enabled. */
@WebMvcTest(OperationalPublicationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.jwt.secret=operational-publication-disabled-test-secret-32-bytes"
})
class OperationalPublicationFeatureDisabledControllerTest {

    private static final String PATH = "/api/v1/reports/operations/publications";
    private static final String SECRET = "operational-publication-disabled-test-secret-32-bytes";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockBean
    PublishOperationalSnapshotsUseCase publications;

    @Test
    void publication_defaultFlagsDisabled_doesNotExposeCommandEndpoint() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + disabledToken()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(publications);
    }

    @Test
    void unknownReportRoute_returnsRedacted404Envelope() throws Exception {
        mvc.perform(get("/api/v1/reports/operations/not-a-route")
                        .header("Authorization", "Bearer " + disabledToken())
                        .header(JwtClaims.HEADER_CORRELATION_ID, "unknown-route-check"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Report endpoint is unavailable"))
                .andExpect(jsonPath("$.correlationId").value("unknown-route-check"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(publications);
    }

    private static String disabledToken() {
        return Jwts.builder()
                .subject(ACTOR.toString())
                .claim(JwtClaims.ROLE, "ADMIN")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
