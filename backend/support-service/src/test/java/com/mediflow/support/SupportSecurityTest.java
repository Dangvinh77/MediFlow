package com.mediflow.support;

import com.mediflow.notification.application.dto.command.CallerIdentity;
import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;
import com.mediflow.notification.web.NotificationController;
import com.mediflow.report.application.port.in.ReadReportUseCase;
import com.mediflow.report.web.ReportController;
import com.mediflow.support.infrastructure.config.SupportSecurityConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Both real module JWT filters and method-security policies in the same servlet application. */
@WebMvcTest(properties = "mediflow.jwt.secret=" + SupportSecurityTest.SECRET)
@ContextConfiguration(classes = SupportSecurityTest.WebSlice.class)
@ActiveProfiles({"report-module", "notification-module", "outpatient-mvp"})
class SupportSecurityTest {
    static final String SECRET = "support-security-test-secret-at-least-thirty-two-bytes";
    static final UUID PATIENT = UUID.fromString("10000000-0000-4000-8000-000000000002");
    @Autowired MockMvc mvc;
    @MockBean ReadReportUseCase reports;
    @MockBean ReadNotificationUseCase notifications;
    @MockBean SendNotificationUseCase sender;

    @Test void managerReachesReportButCannotBorrowNotificationRoles() throws Exception {
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-17").header("Authorization", token("MANAGER", "access")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/" + UUID.randomUUID()).header("Authorization", token("MANAGER", "access")))
                .andExpect(status().isForbidden());
        verify(reports).daily(java.time.LocalDate.of(2026, 10, 17), null);
        verifyNoInteractions(notifications);
    }

    @Test void patientIdentitySurvivesNotificationFilterAndCannotReadReports() throws Exception {
        UUID id = UUID.randomUUID();
        when(notifications.getById(id, PATIENT, false)).thenReturn(new NotificationDTO(id, PATIENT, "title", "content",
                NotificationChannel.IN_APP, NotificationStatus.SENT, null, Instant.now(), Instant.now()));
        mvc.perform(get("/api/v1/notifications/" + id).header("Authorization", token("PATIENT", "access")))
                .andExpect(status().isOk()).andExpect(jsonPath("data.patientId").value(PATIENT.toString()));
        verify(notifications).getById(id, PATIENT, false);
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-17").header("Authorization", token("PATIENT", "access")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @Test void anonymousAndRefreshTokensNeverReachEitherUseCase() throws Exception {
        for (String path : new String[]{"/api/v1/reports/daily?date=2026-10-17", "/api/v1/notifications/" + UUID.randomUUID()}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", token("ADMIN", "refresh"))).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(reports, notifications);
    }

    @Test void serviceCredentialCannotReadHumanEndpoints() throws Exception {
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-17").header("Authorization", token("SYSTEM", "service")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/" + UUID.randomUUID()).header("Authorization", token("SYSTEM", "service")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports, notifications);
    }

    @Test void hostDeniesUnknownAndManagementPathsEvenForAdmin() throws Exception {
        for (String path : new String[]{"/api/v1/unknown", "/actuator/env", "/swagger-ui.html"}) {
            mvc.perform(get(path).header("Authorization", token("ADMIN", "access"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports, notifications);
    }

    @Test void eachControllerKeepsItsOwnExceptionAdvice() throws Exception {
        when(notifications.getById(any(), any(), anyBoolean()))
                .thenThrow(new com.mediflow.notification.domain.exception.NotificationNotFoundException("missing"));
        mvc.perform(get("/api/v1/notifications/" + UUID.randomUUID()).header("Authorization", token("ADMIN", "access")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("error.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-17").header("Authorization", token("ADMIN", "access")))
                .andExpect(status().isOk());
    }

    static String token(String role, String type) {
        return token(role, type, PATIENT);
    }

    static String token(String role, String type, UUID patientId) {
        return "Bearer " + Jwts.builder().subject(role.equals("SYSTEM") ? "pharmacy-service" : UUID.randomUUID().toString())
                .claim("role", role).claim("type", type).claim("patientId", patientId.toString())
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ReportController.class, NotificationController.class,
            com.mediflow.report.infrastructure.config.SecurityConfig.class,
            com.mediflow.notification.infrastructure.config.SecurityConfig.class,
            com.mediflow.report.web.GlobalExceptionHandler.class,
            com.mediflow.notification.web.GlobalExceptionHandler.class,
            SupportSecurityConfiguration.class})
    static class WebSlice { }
}
