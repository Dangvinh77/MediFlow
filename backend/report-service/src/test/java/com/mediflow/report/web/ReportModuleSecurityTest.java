package com.mediflow.report.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutpatientReportController.class)
@ActiveProfiles({"outpatient-mvp", "report-module"})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ReportModuleSecurityTest.HostDenyFence.class})
@org.springframework.test.context.TestPropertySource(properties = {
        "mediflow.jwt.secret=isolated-report-module-test-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.report.outpatient-read.enabled=true"})
class ReportModuleSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ReadOutpatientReportUseCase reader;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void missingOrExpiredAccessToken_neverReachesOutpatientReads(boolean expired) throws Exception {
        var builder = io.jsonwebtoken.Jwts.builder().subject(java.util.UUID.randomUUID().toString())
                .claim("type", "access").claim("role", "MANAGER");
        if (expired) builder.expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(60)));
        String token = builder.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "isolated-report-module-test-secret-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        for (String route : new String[]{"daily?date=2026-10-19", "monthly?year=2026"}) {
            mvc.perform(get("/api/v1/reports/outpatient/" + route).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(reader);
    }

    @Test void reportChainDoesNotClaimAnotherContextsUrl() throws Exception {
        mvc.perform(get("/api/v1/notifications/probe").with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }

    @Test void reportAuthorizationStillDeniesPatientAndAnonymous() throws Exception {
        mvc.perform(get("/api/v1/reports/outpatient/monthly").param("year", "2025")
                        .with(user("patient").roles("PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/outpatient/monthly").param("year", "2025"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reader);
    }

    @Test void reportManagerCanReachItsOwnControllerOnly() throws Exception {
        mvc.perform(get("/api/v1/reports/outpatient/monthly").param("year", "2025")
                        .with(user("manager").roles("MANAGER"))).andExpect(status().isOk());
    }

    /** Local host boundary probe, not Notification implementation/acceptance. */
    @TestConfiguration(proxyBeanMethods = false)
    static class HostDenyFence {
        @Bean @Order(100)
        SecurityFilterChain hostDefaultDeny(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().denyAll()).build();
        }
    }
}
