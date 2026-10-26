package com.mediflow.organization.web.controller;

import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.application.port.in.UpdateOwnStaffContactUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OwnStaffContactController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class OwnStaffContactControllerTest {
    @Autowired MockMvc mvc;
    @MockBean UpdateOwnStaffContactUseCase profiles;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH"})
    void ownUnitIsMinimalNoStoreAndDoesNotGrantForeignUnitAccess(String role) throws Exception {
        UUID own = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        var member = Staff.create("A", unit, JobTitle.NURSE, null, null, null, null, "001234567890");
        when(profiles.update(own, own, null, null, null)).thenReturn(member);
        String token = token(role, own, "access");
        mvc.perform(put("/api/v1/org/staff/{id}/contact", own).header("Authorization", "Bearer " + token).contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.fullName").value("A"))
                .andExpect(jsonPath("$.data.departmentId").value(unit.toString()))
                ;
        verify(profiles).update(own, own, null, null, null);
        mvc.perform(put("/api/v1/org/staff/{id}/contact", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token).header("X-Staff-Id", own.toString()).contentType("application/json").content("{}"))
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
        mvc.perform(put("/api/v1/org/staff/{id}/contact", id).header("Authorization", "Bearer " + access).contentType("application/json").content("{}"))
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
