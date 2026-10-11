package com.mediflow.report.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress.Status;
import com.mediflow.report.application.port.in.RebuildFinancialSourcesUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(FinancialRebuildController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.features.care-finance-v2=true",
        "mediflow.report.financial-publication.enabled=true",
        "mediflow.report.zone-id=Asia/Bangkok",
        "mediflow.jwt.secret=rebuild-test-secret-at-least-32-bytes"
})
class FinancialRebuildControllerTest {

    private static final String PATH = "/api/v1/reports/financial/rebuilds";
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Autowired
    MockMvc mvc;

    @MockBean
    RebuildFinancialSourcesUseCase rebuild;

    private static FinancialRebuildProgress progress(Status status) {
        return new FinancialRebuildProgress(GENERATION, status,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), "Asia/Bangkok",
                Instant.parse("2026-10-11T00:00:00Z"), 2, 0, 0, "a".repeat(64), null);
    }

    @Test
    void start_admin_returns201AndUsesConfiguredZone() throws Exception {
        when(rebuild.start(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), "Asia/Bangkok"))
                .thenReturn(progress(Status.BUILDING));

        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-10-01\",\"to\":\"2026-10-10\"}")
                        .with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.generationId").value(GENERATION.toString()))
                .andExpect(jsonPath("$.data.status").value("BUILDING"));

        verify(rebuild).start(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), "Asia/Bangkok");
    }

    @Test
    void advance_admin_defaultsToBounded31DayBatch() throws Exception {
        when(rebuild.advance(GENERATION, 31)).thenReturn(progress(Status.BUILDING));

        mvc.perform(post(PATH + "/" + GENERATION + "/batches")
                        .with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BUILDING"));

        verify(rebuild).advance(GENERATION, 31);
    }

    @Test
    void progress_admin_returnsCurrentGenerationState() throws Exception {
        when(rebuild.progress(GENERATION)).thenReturn(progress(Status.VERIFIED));

        mvc.perform(get(PATH + "/" + GENERATION)
                        .with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.snapshotOnly").doesNotExist());

        verify(rebuild).progress(GENERATION);
    }

    @Test
    void start_missingDates_returns400BeforeUseCase() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-10-01\"}")
                        .with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));

        verifyNoInteractions(rebuild);
    }

    @Test
    void advance_malformedGeneration_returns400BeforeUseCase() throws Exception {
        mvc.perform(post(PATH + "/not-a-uuid/batches")
                        .param("days", "31")
                        .with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rebuild);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"})
    void rebuild_nonAdmin_returns403(String role) throws Exception {
        mvc.perform(get(PATH + "/" + GENERATION)
                        .with(user("00000000-0000-4000-8000-000000000001").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(rebuild);
    }
}
