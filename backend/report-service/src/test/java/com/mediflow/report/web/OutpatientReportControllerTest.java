package com.mediflow.report.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.application.dto.response.OutpatientReportDTO;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
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

@WebMvcTest(OutpatientReportController.class) @ActiveProfiles("mvp-outpatient")
@Import({SecurityConfig.class,GlobalExceptionHandler.class})
@TestPropertySource(properties={"mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true","mediflow.report.outpatient-read.enabled=true"})
class OutpatientReportControllerTest {
    @Autowired private MockMvc mvc;@MockBean private ReadOutpatientReportUseCase reader;
    private final LocalDate date=LocalDate.of(2026,10,1);
    @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER"})
    void permittedRoleGetsCashNotEarnedOrLiability(String role)throws Exception{
        var period=new OutpatientReportDTO.Period(date,date,1,2,3,4,new BigDecimal("100.00"),new BigDecimal("20.00"),new BigDecimal("80.00"));
        when(reader.daily(date,date,null)).thenReturn(new OutpatientReportDTO(date,date,null,"VND","Asia/Bangkok",
                UUID.randomUUID(),Instant.parse("2026-10-02T00:00:00Z"),Instant.parse("2026-10-02T00:00:00Z"),true,List.of(period)));
        mvc.perform(get("/api/v1/reports/outpatient/daily").param("fromDate",date.toString()).param("toDate",date.toString())
                        .with(user("test-owner").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.covered").value(true))
                .andExpect(jsonPath("$.data.periods[0].netCash").value(80.00))
                .andExpect(jsonPath("$.data.periods[0].earnedRevenue").doesNotExist())
                .andExpect(jsonPath("$.data.periods[0].depositLiability").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"DOCTOR","PATIENT","PHARMACIST","SYSTEM"})
    void otherRolesCannotReadAggregates(String role)throws Exception{
        mvc.perform(get("/api/v1/reports/outpatient/daily").param("fromDate",date.toString()).param("toDate",date.toString())
                .with(user("test").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }
    @Test void missingCoverageDoesNotExposeZeroReport()throws Exception{
        when(reader.daily(date,date,null)).thenThrow(new ReportProjectionUnavailableException());
        mvc.perform(get("/api/v1/reports/outpatient/daily").param("fromDate",date.toString()).param("toDate",date.toString())
                        .with(user("test").roles("MANAGER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
    @Test void invalidYearFailsBeforeQuery()throws Exception{
        mvc.perform(get("/api/v1/reports/outpatient/monthly").param("year","1999").with(user("test").roles("MANAGER")))
                .andExpect(status().isBadRequest());verifyNoInteractions(reader);
    }
    @Test void absentAuthenticationIsDenied()throws Exception{
        mvc.perform(get("/api/v1/reports/outpatient/monthly").param("year","2026")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reader);
    }
}
