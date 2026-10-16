package com.mediflow.report.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.OperationalPublicationCandidateDTO;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.application.dto.response.OperationalReplayProgress.Status;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.GetOperationalPublicationCandidateUseCase;
import com.mediflow.report.application.port.in.ReplayOperationalProjectionUseCase;
import com.mediflow.report.application.port.out.OperationalPublicationStorePort;
import com.mediflow.report.application.service.OperationalPublicationApplicationService;
import com.mediflow.report.infrastructure.config.ReportOperationalPublicationConfiguration;
import com.mediflow.report.infrastructure.config.ReportOperationalPublicationJsonConfiguration;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import java.time.ZoneId;
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationalRebuildController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ReportOperationalPublicationJsonConfiguration.class})
@TestPropertySource(properties = {
        "mediflow.features.care-finance-v2=true",
        "mediflow.report.operational-publication.enabled=true",
        "mediflow.jwt.secret=operational-rebuild-test-secret-32-bytes"
})
class OperationalRebuildControllerTest {

    private static final String PATH = "/api/v1/reports/operations/rebuilds";
    private static final String SECRET = "operational-rebuild-test-secret-32-bytes";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired
    MockMvc mvc;

    @MockBean
    ReplayOperationalProjectionUseCase replay;

    @MockBean
    GetOperationalPublicationCandidateUseCase candidates;

    @Test
    void start_admin_returns201LocationCorrelationAndNoStore() throws Exception {
        when(replay.start()).thenReturn(progress(Status.BUILDING, 3, 0));

        mvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + token())
                        .header(JwtClaims.HEADER_CORRELATION_ID, "rebuild-start-check"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.generationId").value(GENERATION.toString()))
                .andExpect(jsonPath("$.data.status").value("BUILDING"))
                .andExpect(jsonPath("$.correlationId").value("rebuild-start-check"))
                .andExpect(header().string("Location", PATH + "/" + GENERATION))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(replay).start();
    }

    @Test
    void advance_adminWithBoundedBatch_returnsProgressAndNoStore() throws Exception {
        when(replay.advance(GENERATION, 31)).thenReturn(progress(Status.BUILDING, 4, 2));

        mvc.perform(post(PATH + "/" + GENERATION + "/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchSize\":31}")
                        .header("Authorization", "Bearer " + token())
                        .header(JwtClaims.HEADER_CORRELATION_ID, "rebuild-advance-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BUILDING"))
                .andExpect(jsonPath("$.data.appliedEvents").value(2))
                .andExpect(jsonPath("$.correlationId").value("rebuild-advance-check"))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(replay).advance(GENERATION, 31);
    }

    @Test
    void candidate_admin_returnsCandidateWithFiniteManifestProof() throws Exception {
        when(candidates.candidate(GENERATION)).thenReturn(new OperationalPublicationCandidateDTO(
                progress(Status.VERIFIED, 4, 4), "a".repeat(64)));

        mvc.perform(get(PATH + "/" + GENERATION)
                        .header("Authorization", "Bearer " + token())
                        .header(JwtClaims.HEADER_CORRELATION_ID, "rebuild-candidate-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.generationId").value(GENERATION.toString()))
                .andExpect(jsonPath("$.data.progress.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.manifestFingerprint").value("a".repeat(64)))
                .andExpect(jsonPath("$.correlationId").value("rebuild-candidate-check"))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(candidates).candidate(GENERATION);
    }

    @Test
    void candidate_unknownGeneration_returns404WithoutInventingCandidate() throws Exception {
        when(candidates.candidate(GENERATION)).thenThrow(new ReportProjectionUnavailableException());

        mvc.perform(get(PATH + "/" + GENERATION)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));

        verify(candidates).candidate(GENERATION);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 501})
    void advance_batchOutsideOneTo500_returns400BeforeReplay(int batchSize) throws Exception {
        mvc.perform(post(PATH + "/" + GENERATION + "/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchSize\":" + batchSize + "}")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));

        verifyNoInteractions(replay, candidates);
    }

    @Test
    void advance_missingBatchSize_returns400BeforeReplay() throws Exception {
        mvc.perform(post(PATH + "/" + GENERATION + "/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));

        verifyNoInteractions(replay, candidates);
    }

    @Test
    void advance_malformedGeneration_returns400BeforeReplay() throws Exception {
        mvc.perform(post(PATH + "/not-a-uuid/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchSize\":31}")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(replay, candidates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"})
    void rebuild_nonAdminRole_returns403BeforeUseCases(String role) throws Exception {
        String bearer = "Bearer " + token(role);
        mvc.perform(post(PATH).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/" + GENERATION + "/batches")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"batchSize\":31}")
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/" + GENERATION).header("Authorization", bearer))
                .andExpect(status().isForbidden());

        verifyNoInteractions(replay, candidates);
    }

    @Test
    void rebuild_anonymous_returns401BeforeUseCases() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/" + GENERATION + "/batches")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"batchSize\":31}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/" + GENERATION)).andExpect(status().isUnauthorized());

        verifyNoInteractions(replay, candidates);
    }

    private static OperationalReplayProgress progress(Status status, long sourceEvents, long appliedEvents) {
        return new OperationalReplayProgress(GENERATION, status, sourceEvents, appliedEvents);
    }

    private static String token() {
        return token("ADMIN");
    }

    private static String token(String role) {
        return Jwts.builder()
                .subject(ACTOR.toString())
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}

@WebMvcTest(OperationalRebuildController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.features.care-finance-v2=true",
        "mediflow.jwt.secret=operational-rebuild-missing-publication-flag-32-bytes"
})
class OperationalRebuildPublicationFlagDisabledControllerTest {

    private static final String PATH = "/api/v1/reports/operations/rebuilds";
    private static final String SECRET = "operational-rebuild-missing-publication-flag-32-bytes";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockBean
    ReplayOperationalProjectionUseCase replay;

    @MockBean
    GetOperationalPublicationCandidateUseCase candidates;

    @Test
    void rebuild_publicationFlagMissing_doesNotExposeEndpoint() throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(replay, candidates);
    }

    private static String token() {
        return Jwts.builder().subject(ACTOR.toString()).claim(JwtClaims.ROLE, "ADMIN")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

@WebMvcTest(OperationalRebuildController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.report.operational-publication.enabled=true",
        "mediflow.jwt.secret=operational-rebuild-missing-care-finance-flag-32-bytes"
})
class OperationalRebuildCareFinanceFlagDisabledControllerTest {

    private static final String PATH = "/api/v1/reports/operations/rebuilds";
    private static final String SECRET = "operational-rebuild-missing-care-finance-flag-32-bytes";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockBean
    ReplayOperationalProjectionUseCase replay;

    @MockBean
    GetOperationalPublicationCandidateUseCase candidates;

    @Test
    void rebuild_careFinanceFlagMissing_doesNotExposeEndpoint() throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(replay, candidates);
    }

    private static String token() {
        return Jwts.builder().subject(ACTOR.toString()).claim(JwtClaims.ROLE, "ADMIN")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

/** The operational publication application wiring has one explicit two-flag gate. */
class OperationalPublicationConfigurationGateTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ReportOperationalPublicationConfiguration.class,
                    ReportOperationalPublicationJsonConfiguration.class,
                    OperationalPublicationController.class, OperationalRebuildController.class)
            .withBean(OperationalPublicationStorePort.class,
                    () -> mock(OperationalPublicationStorePort.class))
            .withBean(ReplayOperationalProjectionUseCase.class,
                    () -> mock(ReplayOperationalProjectionUseCase.class))
            .withBean("reportZoneId", ZoneId.class, () -> ZoneId.of("Asia/Bangkok"));

    @ParameterizedTest
    @ValueSource(strings = {"default", "care-only", "publication-only", "both"})
    void operationalPublication_requiresBothExplicitGates(String mode) {
        var configured = switch (mode) {
            case "care-only" -> context.withPropertyValues("mediflow.features.care-finance-v2=true");
            case "publication-only" -> context.withPropertyValues(
                    "mediflow.report.operational-publication.enabled=true");
            case "both" -> context.withPropertyValues(
                    "mediflow.features.care-finance-v2=true",
                    "mediflow.report.operational-publication.enabled=true");
            default -> context;
        };

        configured.run(application -> {
            assertThat(application).hasNotFailed();
            if (mode.equals("both")) {
                assertThat(application).hasSingleBean(OperationalPublicationApplicationService.class)
                        .hasSingleBean(OperationalPublicationController.class)
                        .hasSingleBean(OperationalRebuildController.class)
                        .hasSingleBean(ReportOperationalPublicationJsonConfiguration.class);
            } else {
                assertThat(application).doesNotHaveBean(OperationalPublicationApplicationService.class)
                        .doesNotHaveBean(OperationalPublicationController.class)
                        .doesNotHaveBean(OperationalRebuildController.class)
                        .doesNotHaveBean(ReportOperationalPublicationJsonConfiguration.class);
            }
        });
    }

    @Test
    void operationalPublication_enabledWithoutPlatformClock_usesFallbackWithoutRegisteringGlobalClock() {
        context.withPropertyValues("mediflow.features.care-finance-v2=true",
                        "mediflow.report.operational-publication.enabled=true")
                .run(application -> assertThat(application)
                        .hasSingleBean(OperationalPublicationApplicationService.class)
                        .doesNotHaveBean(java.time.Clock.class));
    }
}
