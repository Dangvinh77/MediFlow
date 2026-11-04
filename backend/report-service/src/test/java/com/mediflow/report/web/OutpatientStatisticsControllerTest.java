package com.mediflow.report.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mediflow.report.application.dto.response.OutpatientStatisticsDTO;
import com.mediflow.report.application.port.in.ReadOutpatientStatisticsUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import java.math.BigDecimal;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutpatientStatisticsController.class) @ActiveProfiles("mvp-outpatient")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {"mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.report.outpatient-read.enabled=true"})
class OutpatientStatisticsControllerTest {
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 4);
    @MockBean com.mediflow.report.application.port.out.CurrentReportAuthorityPort authority;
    @MockBean private ReadOutpatientStatisticsUseCase reader;
    @Autowired private MockMvc mvc;

    @Test
    void adminReadsProvisionalStatisticsByServiceLine() throws Exception {
        var zero = new BigDecimal("0.00");
        var department = UUID.randomUUID();
        when(reader.statistics(DAY, DAY, null)).thenReturn(new OutpatientStatisticsDTO(DAY, DAY, null, "VND",
                "Asia/Bangkok", false,
                new OutpatientStatisticsDTO.Totals(1, new BigDecimal("50000.00"), new BigDecimal("36000.00"), zero, zero, zero),
                List.of(new OutpatientStatisticsDTO.Day(DAY, 1, new BigDecimal("50000.00"), new BigDecimal("36000.00"), zero, zero, zero)),
                List.of(new OutpatientStatisticsDTO.Department(department, 1, new BigDecimal("50000.00"),
                        new BigDecimal("36000.00"), zero, zero, zero))));
        mvc.perform(get("/api/v1/reports/outpatient/statistics").param("fromDate", DAY.toString())
                        .param("toDate", DAY.toString()).with(user("test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(false))
                .andExpect(jsonPath("$.data.totals.examRevenue").value(50000.00))
                .andExpect(jsonPath("$.data.days[0].labRevenue").value(36000.00))
                .andExpect(jsonPath("$.data.departments[0].departmentId").value(department.toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"DOCTOR", "CASHIER", "PATIENT", "SYSTEM"})
    void otherRolesCannotReadStatistics(String role) throws Exception {
        mvc.perform(get("/api/v1/reports/outpatient/statistics").param("fromDate", DAY.toString())
                .param("toDate", DAY.toString()).with(user("test").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }

    @Test
    void managerNeedsCurrentAssignedDepartment() throws Exception {
        UUID department = UUID.randomUUID();
        String token = ReportScopeTestSupport.bearer("MANAGER", SECRET);
        String path = "/api/v1/reports/outpatient/statistics?fromDate=2026-11-04&toDate=2026-11-04";
        mvc.perform(get(path).header("Authorization", token)).andExpect(status().isForbidden());
        when(authority.permits(eq(ReportScopeTestSupport.ACCOUNT), eq("MANAGER"), eq(department), anyString(), any()))
                .thenReturn(true);
        mvc.perform(get(path + "&departmentId=" + department).header("Authorization", token)).andExpect(status().isOk());
        verify(reader).statistics(DAY, DAY, department);
    }
}
