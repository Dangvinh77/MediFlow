package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.dto.response.StaffDepartmentDTO;
import com.mediflow.organization.application.port.in.GetOwnStaffDepartmentUseCase;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OwnStaffDepartmentController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class OwnStaffDepartmentControllerTest {
    @Autowired MockMvc mvc;
    @MockBean GetOwnStaffDepartmentUseCase profiles;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH"})
    void ownUnitIsMinimalNoStoreAndDoesNotGrantForeignUnitAccess(String role) throws Exception {
        UUID own = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        when(profiles.read(own, own)).thenReturn(new StaffDepartmentDTO(unit, "Khoa Nội"));
        String token = token(role, own, "access");
        mvc.perform(get("/api/v1/org/staff/{id}/department", own).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.departmentName").value("Khoa Nội"))
                .andExpect(jsonPath("$.data.departmentId").value(unit.toString()))
                .andExpect(jsonPath("$.data.email").doesNotExist());
        verify(profiles).read(own, own);
        mvc.perform(get("/api/v1/org/staff/{id}/department", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token).header("X-Staff-Id", own.toString()))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(profiles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATIENT", "SYSTEM", "MISSING_IDENTITY", "REFRESH"})
    void rejectsNonStaffAndWrongTokenTypes(String scenario) throws Exception {
        UUID id = UUID.randomUUID();
        String role = scenario.equals("MISSING_IDENTITY") || scenario.equals("REFRESH") ? "NURSE" : scenario;
        String access = token(role, scenario.equals("MISSING_IDENTITY") ? null : id,
                scenario.equals("REFRESH") ? "refresh" : scenario.equals("SYSTEM") ? "service" : "access");
        mvc.perform(get("/api/v1/org/staff/{id}/department", id).header("Authorization", "Bearer " + access))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(profiles);
    }

    private String token(String role, UUID staff, String type) {
        var builder = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", role).claim("type", type)
                .expiration(Date.from(Instant.now().plusSeconds(120)));
        if (staff != null) builder.claim("staffId", staff.toString());
        return builder.signWith(Keys.hmacShaKeyFor("test-secret-must-have-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
}
