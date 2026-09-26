package com.mediflow.report.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;
import com.mediflow.report.application.exception.ReportDateRangeException;
import com.mediflow.report.application.port.in.ReadReportUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;

import org.springframework.security.test.context.support.WithMockUser;

/** Web-slice coverage for the T09 endpoint, envelope and RBAC contract. */
@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.security.swagger-permit=false"
})
class ReportControllerTest {

    private static final String BASE_PATH = "/api/v1/reports";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadReportUseCase readReportUseCase;

    @Test
    @WithMockUser(roles = "ADMIN")
    void daily_admin_returnsEnvelopeAndCorrelationId() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(readReportUseCase.daily(LocalDate.of(2026, 9, 15), departmentId))
                .thenReturn(new DailyReportDTO(LocalDate.of(2026, 9, 15), departmentId,
                        4, 2, 1, new BigDecimal("125.50")));

        mockMvc.perform(get(BASE_PATH + "/daily")
                        .param("date", "2026-09-15")
                        .param("departmentId", departmentId.toString())
                        .header("X-Correlation-Id", "corr-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visitCount").value(4))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.correlationId").value("corr-123"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void monthly_manager_allowsHospitalScope() throws Exception {
        MonthlyReportDTO response = new MonthlyReportDTO(2, 2028, null,
                BigDecimal.ZERO, 0, List.of());
        when(readReportUseCase.monthly(2, 2028, null)).thenReturn(response);

        mockMvc.perform(get(BASE_PATH + "/monthly")
                        .param("month", "2")
                        .param("year", "2028"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.month").value(2))
                .andExpect(jsonPath("$.data.departmentId").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void topMedicines_usesDefaultLimitTen() throws Exception {
        when(readReportUseCase.topMedicines(LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), null, 10))
                .thenReturn(List.of(new TopMedicineDTO(UUID.randomUUID(), "A", 3)));

        mockMvc.perform(get(BASE_PATH + "/top-medicines")
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].totalQuantity").value(3));

        verify(readReportUseCase).topMedicines(
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)), eq(null), eq(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidMonth_returns400Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/monthly")
                        .param("month", "13")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("month"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidDateRange_returns422BusinessError() throws Exception {
        when(readReportUseCase.topMedicines(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 9, 1), null, 10))
                .thenThrow(new ReportDateRangeException("fromDate không được sau toDate"));

        mockMvc.perform(get(BASE_PATH + "/top-medicines")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-09-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REPORT_DATE_RANGE_INVALID"));
    }

    @Test
    void missingAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/daily").param("date", "2026-09-15"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void everyLegacyQuery_requiresAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : reportRequests()) {
            mockMvc.perform(request.header("X-Correlation-Id", "unauthenticated-report"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.correlationId").value("unauthenticated-report"));
        }
        verifyNoInteractions(readReportUseCase);
    }

    @Test
    void everyLegacyQuery_acceptsAdminAndManager_butRejectsDoctor() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER")) {
            for (MockHttpServletRequestBuilder request : reportRequests()) {
                mockMvc.perform(request.with(user("report-reader").roles(role)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true));
            }
        }
        for (MockHttpServletRequestBuilder request : reportRequests()) {
            mockMvc.perform(request.with(user("clinical-reader").roles("DOCTOR")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void dailyResponse_exposesOnlyAggregateFields_notClinicalPayload() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(readReportUseCase.daily(date, null)).thenReturn(
                new DailyReportDTO(date, null, 1, 0, 0, BigDecimal.ZERO));

        String response = mockMvc.perform(get(BASE_PATH + "/daily").param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("visitCount")
                .doesNotContain("patientId", "recordId", "diagnosis", "clinicalNote");
    }

    @Test
    void swagger_requiresAuthenticationByDefault() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void swagger_isDeniedForAuthenticatedUsersWhenOptInIsDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void disallowedRole_returns403Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/daily").param("date", "2026-09-15"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedDate_returns400Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/daily").param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("date"));
    }

    private static List<MockHttpServletRequestBuilder> reportRequests() {
        return List.of(
                get(BASE_PATH + "/daily").param("date", "2026-09-15"),
                get(BASE_PATH + "/monthly").param("month", "9").param("year", "2026"),
                get(BASE_PATH + "/top-medicines")
                        .param("fromDate", "2026-09-01").param("toDate", "2026-09-15"));
    }
}
