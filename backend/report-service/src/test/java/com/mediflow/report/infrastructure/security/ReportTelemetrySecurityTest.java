package com.mediflow.report.infrastructure.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.report.infrastructure.config.SecurityConfig;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Tests filter policy against a test-only probe; does not claim deployed Actuator exposure. */
@WebMvcTest(ReportTelemetrySecurityTest.MetricsProbe.class)
@Import({SecurityConfig.class, ReportTelemetrySecurityTest.MetricsProbe.class})
@TestPropertySource(properties = {"mediflow.report.telemetry.enabled=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class ReportTelemetrySecurityTest {
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    @Autowired MockMvc mvc;

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER"})
    void optIn_onlyOperationsRolesCanReadMetrics(String role) throws Exception {
        mvc.perform(get("/actuator/metrics").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics/report.admission.pending").with(user("staff").roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"DOCTOR", "PATIENT", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "SYSTEM"})
    void optIn_otherRolesRemainDeniedForListAndDetail(String role) throws Exception {
        mvc.perform(get("/actuator/metrics").with(user("user").roles(role))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics/report.admission.pending").with(user("user").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_remainsUnauthenticated() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"refresh", "service"})
    void nonAccessToken_adminClaimStillDenied(String type) throws Exception {
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token(type)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedAccessToken_adminCanPassFilter() throws Exception {
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token("access")))
                .andExpect(status().isOk());
    }

    private static String token(String type) {
        return Jwts.builder().subject("telemetry-test").claim("role", "ADMIN").claim("type", type)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @RestController
    static class MetricsProbe {
        @GetMapping({"/actuator/metrics", "/actuator/metrics/{name}"})
        String read() { return "aggregate probe"; }
    }
}
