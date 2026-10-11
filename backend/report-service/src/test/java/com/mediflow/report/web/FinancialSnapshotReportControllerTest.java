package com.mediflow.report.web;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.mediflow.report.application.dto.response.FinancialSnapshotReportDTO;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadFinancialSnapshotUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(FinancialSnapshotReportController.class)
@Import({SecurityConfig.class,GlobalExceptionHandler.class})
@TestPropertySource(properties={"mediflow.features.care-finance-v2=true","mediflow.report.financial-read.enabled=true",
        "mediflow.jwt.secret=financial-test-secret-at-least-32-bytes"})
class FinancialSnapshotReportControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ReadFinancialSnapshotUseCase reader;
    private final LocalDate date=LocalDate.of(2026,10,10);
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String route) {
        var request=get("/api/v1/reports/financial/"+route);
        return route.equals("daily")?request.param("from",date.toString()).param("to",date.toString()):request.param("year","2026");
    }
    private FinancialSnapshotReportDTO response(UUID department) {
        var instant=Instant.parse("2026-10-11T00:00:00Z");
        return new FinancialSnapshotReportDTO(date,date,department,"VND","Asia/Bangkok",UUID.randomUUID(),instant,instant,instant,true,
                List.of(new FinancialSnapshotReportDTO.Period(date,date,new BigDecimal("60.00"),new BigDecimal("60.00"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("40.00"))));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER"})
    void financial_eachAuthorizedRole_bothRoutesExposeOnlyFiveMetricsAndAsOfEvidence(String role) throws Exception {
        UUID department=UUID.randomUUID(); var result=response(department);
        when(reader.daily(date,date,department)).thenReturn(result); when(reader.monthly(2026,department)).thenReturn(result);
        for(String route:List.of("daily","monthly")) {
            mvc.perform(request(route).param("departmentId",department.toString()).with(user("reader").roles(role)).header("X-Correlation-Id","finance-check"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.currency").value("VND"))
                    .andExpect(jsonPath("$.data.snapshotOnly").value(true)).andExpect(jsonPath("$.correlationId").value("finance-check"))
                    .andExpect(jsonPath("$.data.periods[0].cashReceived").value(60))
                    .andExpect(jsonPath("$.data.periods[0].earnedRevenue").value(0))
                    .andExpect(jsonPath("$.data.patientId").doesNotExist()).andExpect(jsonPath("$.data.acceptanceReference").doesNotExist());
        }
    }
    @ParameterizedTest @ValueSource(strings={"DOCTOR","NURSE","PHARMACIST","CASHIER","LAB_TECH","PATIENT","SYSTEM"})
    void financial_otherRoles_bothRoutesDeniedBeforeReader(String role) throws Exception {
        for(String route:List.of("daily","monthly")) mvc.perform(request(route).with(user("reader").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }
    @ParameterizedTest @ValueSource(strings={"daily","monthly"})
    void financial_anonymous_returns401(String route) throws Exception {
        mvc.perform(request(route)).andExpect(status().isUnauthorized()); verifyNoInteractions(reader);
    }
    @ParameterizedTest @ValueSource(strings={"daily","monthly"})
    void financial_missingAcceptedSource_returns404NotZero(String route) throws Exception {
        when(reader.daily(date,date,null)).thenThrow(new ReportProjectionUnavailableException());
        when(reader.monthly(2026,null)).thenThrow(new ReportProjectionUnavailableException());
        mvc.perform(request(route).with(user("reader").roles("ADMIN"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND")).andExpect(jsonPath("$.data").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"daily","monthly"})
    void financial_invalidPeriod_returns400StableCode(String route) throws Exception {
        when(reader.daily(date,date,null)).thenThrow(new ReportPeriodValidationException());
        when(reader.monthly(2026,null)).thenThrow(new ReportPeriodValidationException());
        mvc.perform(request(route).with(user("reader").roles("MANAGER"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR"));
    }
    @ParameterizedTest @ValueSource(strings={"bad-date","bad-year","bad-department","missing-year","missing-from"})
    void financial_malformedParameters_failBeforeReader(String kind) throws Exception {
        var bad=switch(kind) {
            case "bad-date" -> get("/api/v1/reports/financial/daily").param("from","bad").param("to",date.toString());
            case "bad-year" -> get("/api/v1/reports/financial/monthly").param("year","bad");
            case "bad-department" -> request("daily").param("departmentId","bad");
            case "missing-year" -> get("/api/v1/reports/financial/monthly");
            default -> get("/api/v1/reports/financial/daily").param("to",date.toString());
        };
        mvc.perform(bad.with(user("reader").roles("ADMIN"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_VALIDATION_ERROR")); verifyNoInteractions(reader);
    }
    private String token(String kind) {
        var builder=Jwts.builder().subject(kind.equals("bad-subject")?"invalid":UUID.randomUUID().toString()).claim("role","ADMIN");
        if (!kind.equals("missing-type")) builder.claim("type",kind.equals("refresh")?"refresh":kind.equals("service")?"service":"access");
        if (!kind.equals("missing-expiry")) builder.expiration(Date.from(Instant.now().plusSeconds(kind.equals("expired")?-60:120)));
        var secret=(kind.equals("wrong-signature")?"wrong-secret-must-have-at-least-32-bytes":"financial-test-secret-at-least-32-bytes").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return builder.signWith(Keys.hmacShaKeyFor(secret)).compact();
    }
    @ParameterizedTest @ValueSource(strings={"refresh","service","missing-type","missing-expiry","expired","wrong-signature","bad-subject"})
    void financial_invalidSignedToken_isUnauthorized(String kind) throws Exception {
        mvc.perform(request("daily").header("Authorization","Bearer "+token(kind))).andExpect(status().isUnauthorized()); verifyNoInteractions(reader);
    }
    @Test void financial_signedAccessToken_readsWithoutMockIdentity() throws Exception {
        when(reader.daily(date,date,null)).thenReturn(response(null));
        mvc.perform(request("daily").header("Authorization","Bearer "+token("valid"))).andExpect(status().isOk());
        verify(reader).daily(date,date,null);
    }
}
