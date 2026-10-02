package com.mediflow.report.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.mediflow.report.application.dto.response.OperationalSnapshotReportDTO;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadOperationalSnapshotUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;

@WebMvcTest(OperationalSnapshotReportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {"mediflow.features.care-finance-v2=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes", "mediflow.security.swagger-permit=false"})
class OperationalSnapshotReportControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ReadOperationalSnapshotUseCase reader;
    private final LocalDate date = LocalDate.of(2026, 10, 2);
    @Test @WithMockUser(roles = "DOCTOR")
    void daily_doctor_getsAggregateSnapshotAndCorrelationOnly() throws Exception {
        when(reader.daily(date, date, null)).thenReturn(new OperationalSnapshotReportDTO(date, date, null,
                UUID.randomUUID(), Instant.parse("2026-10-02T00:00:00Z"), true, List.of()));
        mvc.perform(get("/api/v1/reports/operations/daily").param("from", date.toString()).param("to", date.toString())
                .header("X-Correlation-Id", "report-snapshot"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshotOnly").value(true))
                .andExpect(jsonPath("$.correlationId").value("report-snapshot"))
                .andExpect(jsonPath("$.data.patientId").doesNotExist());
    }
    @Test @WithMockUser(roles = "MANAGER")
    void surgery_missingAcceptedCoverage_returns404NotSuccessfulZero() throws Exception {
        when(reader.surgery(date, date, null)).thenThrow(new ReportProjectionUnavailableException());
        mvc.perform(get("/api/v1/reports/operations/surgery").param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
    }
    @Test @WithMockUser(roles = "ADMIN")
    void daily_invalidPeriod_returns400() throws Exception {
        when(reader.daily(date, date, null)).thenThrow(new ReportPeriodValidationException());
        mvc.perform(get("/api/v1/reports/operations/daily").param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));
    }
    @Test @WithMockUser(roles = "PATIENT")
    void daily_patient_deniedBeforeReader() throws Exception {
        mvc.perform(get("/api/v1/reports/operations/daily").param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }
    @Test void daily_unauthenticated_deniedBeforeReader() throws Exception {
        mvc.perform(get("/api/v1/reports/operations/daily").param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reader);
    }
    @Test @WithMockUser(roles = "DOCTOR")
    void daily_badDateFormat_validatesBeforeReader() throws Exception {
        mvc.perform(get("/api/v1/reports/operations/daily").param("from", "invalid").param("to", date.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));
        verifyNoInteractions(reader);
    }
    @Test void operations_allOtherRoles_deniedForBothRoutesBeforeReader() throws Exception {
        for (String role : List.of("SYSTEM", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT")) {
            for (String route : List.of("daily", "surgery")) {
                mvc.perform(get("/api/v1/reports/operations/" + route).param("from", date.toString()).param("to", date.toString())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-user").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(reader);
    }
}
