package com.mediflow.report.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.report.application.dto.response.ReportCapabilitiesDTO;
import com.mediflow.report.application.dto.response.ReportCoverageDTO;
import com.mediflow.report.application.port.in.InspectReportCapabilitiesUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportCapabilityController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.features.care-finance-v2=true",
        "mediflow.report.financial-publication.enabled=true",
        "mediflow.report.zone-id=Asia/Bangkok",
        "mediflow.jwt.secret=capability-test-secret-at-least-32-bytes"
})
class ReportCapabilityControllerTest {

    private static final String PATH = "/api/v1/reports/financial";
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Autowired
    MockMvc mvc;

    @MockBean
    InspectReportCapabilitiesUseCase capabilities;

    @Test
    void capabilities_admin_returnsVersionedContract() throws Exception {
        when(capabilities.capabilities()).thenReturn(new ReportCapabilitiesDTO(
                1, "AVAILABLE_EVIDENCE_ONLY", List.of(
                        new ReportCapabilitiesDTO.SourceCapability("payment.completed", "billing-service", 1,
                                "transactionId", "IMMUTABLE_SINGLETON", "REJECT_UNSUPPORTED_CORRECTION")),
                List.of("PRE_V2_HISTORY_WITHOUT_SOURCE")));

        mvc.perform(get(PATH + "/capabilities").with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilityVersion").value(1))
                .andExpect(jsonPath("$.data.historyPolicy").value("AVAILABLE_EVIDENCE_ONLY"))
                .andExpect(jsonPath("$.data.sources[0].sourceIdField").value("transactionId"));

        verify(capabilities).capabilities();
    }

    @Test
    void coverage_manager_returnsExplicitAvailableSnapshot() throws Exception {
        when(capabilities.coverage(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5),
                "VND", "Asia/Bangkok")).thenReturn(new ReportCoverageDTO(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), ReportCoverageDTO.Status.AVAILABLE_SNAPSHOT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), GENERATION,
                Instant.parse("2026-10-11T00:00:00Z"), true));

        mvc.perform(get(PATH + "/coverage")
                        .param("from", "2026-10-03")
                        .param("to", "2026-10-05")
                        .with(user("00000000-0000-4000-8000-000000000001").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE_SNAPSHOT"))
                .andExpect(jsonPath("$.data.snapshotOnly").value(true))
                .andExpect(jsonPath("$.data.generationId").value(GENERATION.toString()));

        verify(capabilities).coverage(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5),
                "VND", "Asia/Bangkok");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"})
    void capabilityEndpoints_nonFinanceRole_returns403(String role) throws Exception {
        mvc.perform(get(PATH + "/capabilities")
                        .with(user("00000000-0000-4000-8000-000000000001").roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/coverage").param("from", "2026-10-03").param("to", "2026-10-05")
                        .with(user("00000000-0000-4000-8000-000000000001").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(capabilities);
    }

    @Test
    void capabilityEndpoints_anonymous_returns401() throws Exception {
        mvc.perform(get(PATH + "/capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/coverage").param("from", "2026-10-03").param("to", "2026-10-05"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(capabilities);
    }

    @Test
    void coverage_malformedDate_returns400BeforeUseCase() throws Exception {
        mvc.perform(get(PATH + "/coverage").param("from", "invalid").param("to", "2026-10-05")
                        .with(user("00000000-0000-4000-8000-000000000001").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));

        verifyNoInteractions(capabilities);
    }
}
