package com.mediflow.organization.web.controller;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.request.ScheduleManagementScopeCommand;
import com.mediflow.organization.application.dto.response.ConsultationEligibility;
import com.mediflow.organization.application.dto.response.CurrentDoctorAuthority;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeCommandResult;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.application.dto.response.SchedulingAuthority;
import com.mediflow.organization.application.port.in.ManageScheduleManagementScopeUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.security.JwtAuthFilter;
import com.mediflow.organization.infrastructure.security.JwtProperties;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import com.mediflow.organization.web.handler.OrganizationGlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleManagementScopeController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class,
        CorrelationIdFilter.class, OrganizationGlobalExceptionHandler.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class ScheduleManagementScopeControllerTest {
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

    @Autowired MockMvc mvc;
    @MockBean ManageScheduleManagementScopeUseCase authority;

    @Test
    void adminScopeUpdate_echoesCorrelationAndReplayMetadata() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        String correlation = "trace-c01-admin-grant";
        var snapshot = new ScheduleManagementScopeSnapshot(managerId, 1,
                List.of(departmentId), Instant.parse("2026-10-26T03:00:00Z"), adminId);
        when(authority.replaceScope(eq(adminId), eq(Role.ADMIN), eq(managerId), any(),
                eq("scope-01"), eq(correlation)))
                .thenReturn(new ScheduleManagementScopeCommandResult(snapshot, true, 200, null));

        mvc.perform(put("/api/v2/org/accounts/{id}/schedule-management-scope", managerId)
                        .header("Authorization", token("access", "ADMIN", adminId.toString(), 30))
                        .header("Idempotency-Key", "scope-01")
                        .header("X-Correlation-Id", correlation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"departmentIds\":[\"" + departmentId
                                + "\"],\"reason\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().string("X-Correlation-Id", correlation))
                .andExpect(jsonPath("$.data.accountId").value(managerId.toString()))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.departmentIds[0]").value(departmentId.toString()))
                .andExpect(jsonPath("$.correlationId").value(correlation));

        verify(authority).replaceScope(eq(adminId), eq(Role.ADMIN), eq(managerId), any(),
                eq("scope-01"), eq(correlation));
    }

    @Test
    void unknownScopeCommandField_isRejectedBeforeUseCase() throws Exception {
        UUID adminId = UUID.randomUUID();
        mvc.perform(put("/api/v2/org/accounts/{id}/schedule-management-scope", UUID.randomUUID())
                        .header("Authorization", token("access", "ADMIN", adminId.toString(), 30))
                        .header("Idempotency-Key", "scope-01")
                        .header("X-Correlation-Id", "trace-c01-unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"departmentIds\":[],\"reason\":\"Revoke\",\"extra\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authority);
    }

    @Test
    void internalAuthorityRequiresClinicalServiceTokenAndExactWireProjection() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        String correlation = "trace-clinical-auth-01";
        when(authority.getSchedulingAuthority(actorId, departmentId)).thenReturn(
                new SchedulingAuthority(actorId, true, true, "MANAGER", UUID.randomUUID(), true,
                        departmentId, true, true, 3, Instant.parse("2026-10-26T03:00:00Z")));

        mvc.perform(get("/api/v2/org/scheduling-authority")
                        .param("actorAccountId", actorId.toString())
                        .param("departmentId", departmentId.toString())
                        .header("Authorization", token("service", "SYSTEM", "clinical-service", 50))
                        .header("X-Correlation-Id", correlation))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", correlation))
                .andExpect(jsonPath("$.data.actorAccountId").value(actorId.toString()))
                .andExpect(jsonPath("$.data.currentRole").value("MANAGER"))
                .andExpect(jsonPath("$.data.departmentActive").value(true))
                .andExpect(jsonPath("$.data.canManageSchedules").value(true))
                .andExpect(jsonPath("$.data.scopeRevision").value(3))
                .andExpect(jsonPath("$.correlationId").value(correlation));

        mvc.perform(get("/api/v2/org/scheduling-authority")
                        .param("actorAccountId", actorId.toString())
                        .param("departmentId", departmentId.toString())
                        .header("Authorization", token("service", "SYSTEM", "billing-service", 50)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/org/scheduling-authority")
                        .param("actorAccountId", actorId.toString())
                        .param("departmentId", departmentId.toString())
                        .header("Authorization", token("service", "SYSTEM", "clinical-service", 70)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/org/scheduling-authority")
                        .param("actorAccountId", actorId.toString())
                        .param("departmentId", departmentId.toString())
                        .header("Authorization", token("access", "ADMIN", UUID.randomUUID().toString(), 30)))
                .andExpect(status().isForbidden());
        verify(authority).getSchedulingAuthority(actorId, departmentId);
    }

    @Test
    void consultationEligibilityIsInternalOnlyAndKeepsRequestedIds() throws Exception {
        UUID doctor = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID room = UUID.randomUUID();
        when(authority.getConsultationEligibility(doctor, department, room)).thenReturn(
                new ConsultationEligibility(doctor, true, true, true, department,
                        department, true, true, room, true, true, department,
                        "CONSULTATION", true, Instant.parse("2026-10-26T03:00:00Z")));

        mvc.perform(get("/api/v2/org/consultation-eligibility")
                        .param("doctorId", doctor.toString())
                        .param("departmentId", department.toString())
                        .param("roomId", room.toString())
                        .header("X-Correlation-Id", "trace-clinical-eligibility-01")
                        .header("Authorization", token("service", "SYSTEM", "clinical-service", 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(doctor.toString()))
                .andExpect(jsonPath("$.data.departmentId").value(department.toString()))
                .andExpect(jsonPath("$.data.roomId").value(room.toString()))
                .andExpect(jsonPath("$.data.roomType").value("CONSULTATION"))
                .andExpect(jsonPath("$.data.eligible").value(true));

        mvc.perform(get("/api/v2/org/consultation-eligibility")
                        .param("doctorId", doctor.toString())
                        .param("departmentId", department.toString())
                        .param("roomId", room.toString())
                        .header("X-Correlation-Id", "trace-clinical-eligibility-denied")
                        .header("Authorization", token("access", "ADMIN", UUID.randomUUID().toString(), 30)))
                .andExpect(status().isForbidden());
        verify(authority).getConsultationEligibility(doctor, department, room);
    }

    @Test
    void currentDoctorAuthorityIsInternalAndReturnsLiveAccountStaffAndDepartment() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        String correlation = "trace-clinical-current-doctor-authority";
        when(authority.getCurrentDoctorAuthority(actorId)).thenReturn(
                new CurrentDoctorAuthority(actorId, true, true, "DOCTOR", staffId,
                        true, departmentId, true, Instant.parse("2026-10-26T03:00:00Z")));

        mvc.perform(get("/api/v2/org/current-doctor-authority")
                        .param("actorAccountId", actorId.toString())
                        .header("Authorization", token("service", "SYSTEM", "clinical-service", 50))
                        .header("X-Correlation-Id", correlation))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", correlation))
                .andExpect(jsonPath("$.data.actorAccountId").value(actorId.toString()))
                .andExpect(jsonPath("$.data.accountExists").value(true))
                .andExpect(jsonPath("$.data.currentRole").value("DOCTOR"))
                .andExpect(jsonPath("$.data.staffId").value(staffId.toString()))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.data.departmentActive").value(true));

        mvc.perform(get("/api/v2/org/current-doctor-authority")
                        .param("actorAccountId", actorId.toString())
                        .header("Authorization", token("service", "SYSTEM", "billing-service", 50)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/org/current-doctor-authority")
                        .param("actorAccountId", actorId.toString())
                        .header("Authorization", token("access", "DOCTOR", actorId.toString(), 30)))
                .andExpect(status().isForbidden());
        verify(authority).getCurrentDoctorAuthority(actorId);
    }

    private static String token(String type, String role, String subject, long lifetimeSeconds) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject(subject)
                .claim("type", type).claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(lifetimeSeconds)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
